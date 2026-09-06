package org.jellyfin.androidtv.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
	val updateAvailable: Boolean,
	val appVersion: String = "",
	val appVersionCode: Int = 0,
	val downloadUrl: String = "",
	val downloadSize: Long = 0,
	val checksum: String = "",
	val mandatory: Boolean = false,
	val changelog: Map<String, String> = emptyMap(),
)

interface AppUpdateRepository {
	val updateInfo: StateFlow<AppUpdateInfo?>
	val isChecking: StateFlow<Boolean>
	val downloadProgress: StateFlow<Int> // -1 = idle, 0-100 = progress
	val downloadMessage: StateFlow<String>
	val downloadedApk: StateFlow<File?> // non-null = download finished, waiting for user confirmation
	val isInstalling: StateFlow<Boolean> // true after the user confirmed, app may be killed at any moment

	suspend fun checkForUpdate(): AppUpdateInfo?
	suspend fun downloadApk(context: Context, updateInfo: AppUpdateInfo)
	fun installDownloadedApk(context: Context)
}

class AppUpdateRepositoryImpl(
	private val api: ApiClient,
) : AppUpdateRepository {

	companion object {
		private const val NOTIFICATION_CHANNEL_ID = "app_update"
		private const val TAG = "AppUpdate"
		private const val INSTALL_STATUS_ACTION = "org.jellyfin.androidtv.action.INSTALL_STATUS"
	}

	override val updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
	override val isChecking = MutableStateFlow(false)
	override val downloadProgress = MutableStateFlow(-1)
	override val downloadMessage = MutableStateFlow("")
	override val downloadedApk = MutableStateFlow<File?>(null)
	override val isInstalling = MutableStateFlow(false)

	private val httpClient = OkHttpClient.Builder()
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(60, TimeUnit.SECONDS)
		.build()

	override suspend fun checkForUpdate(): AppUpdateInfo? {
		isChecking.value = true
		downloadMessage.value = ""
		return try {
			val baseUrl = api.baseUrl ?: run {
				isChecking.value = false
				downloadMessage.value = "服务器未连接"
				return null
			}
			val response = withContext(Dispatchers.IO) {
				val versionName = BuildConfig.VERSION_NAME
				val versionCode = calcVersionCode(versionName)
				val uri = Uri.parse("$baseUrl/AppUpdate/Check").buildUpon()
					.appendQueryParameter("currentVersionCode", versionCode.toString())
					.appendQueryParameter("currentVersion", versionName)
					.appendQueryParameter("channel", "stable")
					.build()
				Timber.i(TAG, "Checking update: $uri")
				val connection = java.net.URL(uri.toString()).openConnection() as java.net.HttpURLConnection
				connection.requestMethod = "GET"
				connection.setRequestProperty(
					"Authorization",
					api.accessToken?.let { "MediaBrowser Token=\"$it\"" } ?: ""
				)
				connection.setRequestProperty("X-Emby-Authorization", "MediaBrowser Client=\"Jellyfin for Android TV\", Device=\"androidtv\", Version=\"$versionName\"")
				connection.setRequestProperty("Accept", "application/json; profile=\"CamelCase\"")
				connection.connectTimeout = 10000
				connection.readTimeout = 10000

				Timber.i(TAG, "Response code: ${connection.responseCode}")
				if (connection.responseCode == 200) {
					val body = connection.inputStream.bufferedReader().readText()
					Timber.i(TAG, "Response body: $body")
					val fixedBody = body.replace(
						"\"/AppUpdate/Download",
						"\"$baseUrl/AppUpdate/Download"
					)
					parseUpdateInfo(fixedBody)
				} else {
					Timber.w(TAG, "Update check failed with code %d", connection.responseCode)
					downloadMessage.value = "检查更新失败: ${connection.responseCode}"
					null
				}
			}
			updateInfo.value = response
			isChecking.value = false
			if (response?.updateAvailable == true) {
				downloadMessage.value = "发现新版本 ${response.appVersion}"
			} else {
				downloadMessage.value = "当前已是最新版本"
			}
			response
		} catch (e: Exception) {
			Timber.e(TAG, e, "Failed to check for app update")
			downloadMessage.value = "检查更新失败: ${e.message}"
			isChecking.value = false
			null
		}
	}

	private fun parseUpdateInfo(json: String): AppUpdateInfo {
		// Server may return PascalCase or camelCase, handle both
		val jsonLower = json.lowercase()
		val updateAvailable = jsonLower.contains("\"updateavailable\":true")
		if (!updateAvailable) return AppUpdateInfo(updateAvailable = false)

		return AppUpdateInfo(
			updateAvailable = true,
			appVersion = extractString(json, "appVersion") ?: extractString(json, "AppVersion") ?: "",
			appVersionCode = extractInt(json, "appVersionCode") ?: extractInt(json, "AppVersionCode") ?: 0,
			downloadUrl = extractString(json, "downloadUrl") ?: extractString(json, "DownloadUrl") ?: "",
			downloadSize = extractLong(json, "downloadSize") ?: extractLong(json, "DownloadSize") ?: 0,
			checksum = extractString(json, "checksum") ?: extractString(json, "Checksum") ?: "",
			mandatory = jsonLower.contains("\"mandatory\":true"),
			changelog = extractChangelog(json),
		)
	}

	/**
	 * Parses the changelog dictionary from the Check response, e.g.
	 * "changelog":{"zh-CN":"...","en-US":"..."}.
	 * Values are JSON-escaped (\uXXXX for CJK, \n, \") and must be unescaped for display.
	 */
	private fun extractChangelog(json: String): Map<String, String> {
		val match = Regex("\"[Cc]hangelog\"\\s*:\\s*\\{([^{}]*)\\}").find(json) ?: return emptyMap()
		val result = mutableMapOf<String, String>()
		Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(match.groupValues[1]).forEach {
			result[it.groupValues[1]] = unescapeJson(it.groupValues[2])
		}
		return result
	}

	/** Decodes JSON string escapes: \uXXXX, \n, \r, \t, \", \\, \/ */
	private fun unescapeJson(s: String): String {
		if ('\\' !in s) return s
		val sb = StringBuilder(s.length)
		var i = 0
		while (i < s.length) {
			val c = s[i]
			if (c == '\\' && i + 1 < s.length) {
				when (val n = s[i + 1]) {
					'n' -> { sb.append('\n'); i += 2 }
					't' -> { sb.append('\t'); i += 2 }
					'r' -> { sb.append('\r'); i += 2 }
					'"' -> { sb.append('"'); i += 2 }
					'\\' -> { sb.append('\\'); i += 2 }
					'/' -> { sb.append('/'); i += 2 }
					'u' -> {
						if (i + 5 < s.length) {
							val code = s.substring(i + 2, i + 6).toIntOrNull(16)
							if (code != null) {
								sb.append(code.toChar())
								i += 6
							} else {
								sb.append(c); i++
							}
						} else {
							sb.append(c); i++
						}
					}
					else -> { sb.append(c); i++ }
				}
			} else {
				sb.append(c); i++
			}
		}
		return sb.toString()
	}

	private fun extractString(json: String, key: String): String? =
		unescapeJson(Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return null)

	private fun extractInt(json: String, key: String): Int? =
		Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

	private fun extractLong(json: String, key: String): Long? =
		Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull()

	override suspend fun downloadApk(context: Context, updateInfo: AppUpdateInfo) {
		if (updateInfo.downloadUrl.isEmpty()) {
			downloadMessage.value = "下载地址为空"
			Timber.w(TAG, "Download URL is empty")
			return
		}

		withContext(Dispatchers.IO) {
			try {
				downloadProgress.value = 0
				downloadMessage.value = "正在下载..."
				Timber.i(TAG, "Starting download: ${updateInfo.downloadUrl}")

				val apkFile = File(context.cacheDir, "jellyfin-${updateInfo.appVersion}.apk")

				val request = Request.Builder()
					.url(updateInfo.downloadUrl)
					.addHeader("Authorization", api.accessToken?.let { "MediaBrowser Token=\"$it\"" } ?: "")
					.addHeader("X-Emby-Authorization", "MediaBrowser Client=\"Jellyfin for Android TV\", Device=\"androidtv\", Version=\"${BuildConfig.VERSION_NAME}\"")
					.addHeader("Accept", "application/json; profile=\"CamelCase\"")
					.build()

				val response = httpClient.newCall(request).execute()
				Timber.i(TAG, "Download response code: ${response.code}")

				if (!response.isSuccessful) {
					downloadMessage.value = "下载失败: ${response.code}"
					downloadProgress.value = -1
					Timber.e(TAG, "Download failed with code: ${response.code}")
					return@withContext
				}

				val body = response.body ?: run {
					downloadMessage.value = "下载失败: 响应体为空"
					downloadProgress.value = -1
					Timber.e(TAG, "Response body is null")
					return@withContext
				}

				val contentLength = body.contentLength()
				Timber.i(TAG, "Content length: $contentLength bytes")

				val inputStream = body.byteStream()
				val outputStream = FileOutputStream(apkFile)

				val buffer = ByteArray(8192)
				var bytesRead: Int
				var totalRead = 0L

				while (inputStream.read(buffer).also { bytesRead = it } != -1) {
					outputStream.write(buffer, 0, bytesRead)
					totalRead += bytesRead
					if (contentLength > 0) {
						downloadProgress.value = ((totalRead * 100) / contentLength).toInt()
					}
				}

				outputStream.flush()
				outputStream.close()
				inputStream.close()

				Timber.i(TAG, "Download complete: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
				downloadProgress.value = 100
				downloadMessage.value = "下载完成，正在安装..."

				// Verify checksum if provided
				if (updateInfo.checksum.isNotEmpty()) {
					val fileHash = calculateSha256(apkFile)
					val expectedHash = updateInfo.checksum.removePrefix("sha256:")
					if (!fileHash.equals(expectedHash, ignoreCase = true)) {
						Timber.e(TAG, "Checksum mismatch: expected=$expectedHash, actual=$fileHash")
						downloadMessage.value = "文件校验失败"
						downloadProgress.value = -1
						apkFile.delete()
						return@withContext
					}
					Timber.i(TAG, "Checksum verified")
				}

				// Download finished: wait for explicit user confirmation before installing
				downloadedApk.value = apkFile
				downloadProgress.value = -1
				downloadMessage.value = "下载完成"

			} catch (e: Exception) {
				Timber.e(TAG, e, "Download failed")
				downloadMessage.value = "下载失败: ${e.message}"
				downloadProgress.value = -1
			}
		}
	}

	override fun installDownloadedApk(context: Context) {
		val apk = downloadedApk.value ?: return
		isInstalling.value = true
		downloadMessage.value = "正在安装…完成后应用将自动重启"
		launchInstaller(context, apk)
	}

	private fun launchInstaller(context: Context, apkFile: File) {
		// Preferred: in-app PackageInstaller session (no file manager involved,
		// system install confirmation is shown directly after the download)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && installViaPackageSession(context, apkFile)) {
			return
		}

		// Fallback: ACTION_VIEW via FileProvider
		try {
			val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				androidx.core.content.FileProvider.getUriForFile(
					context,
					"${BuildConfig.APPLICATION_ID}.fileprovider",
					apkFile
				)
			} else {
				Uri.fromFile(apkFile)
			}

			val intent = Intent(Intent.ACTION_VIEW).apply {
				setDataAndType(uri, "application/vnd.android.package-archive")
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
			Timber.i(TAG, "Launching installer via ACTION_VIEW for: $apkFile")
			context.startActivity(intent)
		} catch (e: Exception) {
			Timber.e(TAG, e, "Failed to launch installer")
			downloadMessage.value = "无法启动安装程序"
		}
	}

	/**
	 * Streams the APK into a PackageInstaller session and commits it.
	 * After commit the system sends a status broadcast with STATUS_PENDING_USER_ACTION
	 * carrying the confirmation dialog intent - the app must launch that intent itself,
	 * otherwise no install confirmation is ever shown.
	 * Returns false when the session could not be created.
	 */
	private fun installViaPackageSession(context: Context, apkFile: File): Boolean {
		var receiver: BroadcastReceiver? = null
		val appContext = context.applicationContext
		return try {
			val packageInstaller = appContext.packageManager.packageInstaller
			val sessionParams = android.content.pm.PackageInstaller.SessionParams(
				android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
			)
			val sessionId = packageInstaller.createSession(sessionParams)
			val session = packageInstaller.openSession(sessionId)

			try {
				session.openWrite("jellyfin_update.apk", 0, apkFile.length()).use { out ->
					apkFile.inputStream().use { input -> input.copyTo(out) }
					session.fsync(out)
				}

				val statusIntent = Intent(INSTALL_STATUS_ACTION).setPackage(appContext.packageName)
				val flags = PendingIntent.FLAG_UPDATE_CURRENT or
					(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
				val statusReceiver = PendingIntent.getBroadcast(appContext, sessionId, statusIntent, flags)

				receiver = object : BroadcastReceiver() {
					override fun onReceive(ctx: Context, intent: Intent) {
						val status = intent.getIntExtra(
							android.content.pm.PackageInstaller.EXTRA_STATUS,
							android.content.pm.PackageInstaller.STATUS_FAILURE
						)
						Timber.i(TAG, "Install status broadcast: %s", status)
						when (status) {
							android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {
								val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
								if (confirm != null) {
									try {
										confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
										appContext.startActivity(confirm)
										downloadMessage.value = "请在系统弹窗中确认安装，安装过程中应用会关闭"
									} catch (e: Exception) {
										Timber.e(TAG, e, "Failed to launch install confirmation")
										downloadMessage.value = "无法显示安装确认: ${e.message}"
										isInstalling.value = false
									}
								} else {
									downloadMessage.value = "安装确认不可用"
									isInstalling.value = false
								}
							}
							android.content.pm.PackageInstaller.STATUS_SUCCESS -> {
								downloadMessage.value = "安装完成，正在重启应用…"
							}
							else -> {
								downloadMessage.value = "安装失败 (status=$status)"
								isInstalling.value = false
							}
						}
						runCatching { appContext.unregisterReceiver(this) }
					}
				}
				val filter = IntentFilter(INSTALL_STATUS_ACTION)
				ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

				session.commit(statusReceiver.intentSender)
				Timber.i(TAG, "PackageInstaller session committed (id=$sessionId) for: $apkFile")
			} catch (e: Exception) {
				runCatching { receiver?.let { appContext.unregisterReceiver(it) } }
				throw e
			} finally {
				session.close()
			}
			true
		} catch (e: Exception) {
			Timber.e(TAG, e, "PackageInstaller session failed, falling back to ACTION_VIEW")
			downloadMessage.value = "应用内安装不可用，尝试其他方式…"
			false
		}
	}

	private fun calculateSha256(file: File): String {
		val digest = MessageDigest.getInstance("SHA-256")
		val inputStream = file.inputStream()
		val buffer = ByteArray(8192)
		var bytesRead: Int
		while (inputStream.read(buffer).also { bytesRead = it } != -1) {
			digest.update(buffer, 0, bytesRead)
		}
		inputStream.close()
		return digest.digest().joinToString("") { "%02x".format(it) }
	}

	private fun calcVersionCode(versionName: String): Int {
		val (core, preRelease) = when (val idx = versionName.indexOf('-')) {
			-1 -> versionName to null
			else -> versionName.substring(0, idx) to versionName.substring(idx + 1)
		}
		val parts = core.split('.').mapNotNull { it.toIntOrNull() }.take(3)
		val major = parts.getOrElse(0) { 0 }
		val minor = parts.getOrElse(1) { 0 }
		val patch = parts.getOrElse(2) { 0 }
		val build = preRelease?.substringAfter('.')?.toIntOrNull() ?: 99
		return major * 1000000 + minor * 10000 + patch * 100 + build
	}
}
