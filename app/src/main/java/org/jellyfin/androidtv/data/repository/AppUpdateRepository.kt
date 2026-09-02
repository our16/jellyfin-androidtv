package org.jellyfin.androidtv.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
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

	suspend fun checkForUpdate(): AppUpdateInfo?
	suspend fun downloadApk(context: Context, updateInfo: AppUpdateInfo)
}

class AppUpdateRepositoryImpl(
	private val api: ApiClient,
) : AppUpdateRepository {

	companion object {
		private const val NOTIFICATION_CHANNEL_ID = "app_update"
		private const val TAG = "AppUpdate"
	}

	override val updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
	override val isChecking = MutableStateFlow(false)
	override val downloadProgress = MutableStateFlow(-1)
	override val downloadMessage = MutableStateFlow("")

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
		)
	}

	private fun extractString(json: String, key: String): String? =
		Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)

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

				// Launch installer
				launchInstaller(context, apkFile)
				downloadMessage.value = "安装包已准备，请确认安装"
				downloadProgress.value = -1

			} catch (e: Exception) {
				Timber.e(TAG, e, "Download failed")
				downloadMessage.value = "下载失败: ${e.message}"
				downloadProgress.value = -1
			}
		}
	}

	private fun launchInstaller(context: Context, apkFile: File) {
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
			Timber.i(TAG, "Launching installer for: $apkFile")
			context.startActivity(intent)
		} catch (e: Exception) {
			Timber.e(TAG, e, "Failed to launch installer")
			downloadMessage.value = "无法启动安装程序"
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
