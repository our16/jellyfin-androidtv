package org.jellyfin.androidtv.data.repository

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

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

	suspend fun checkForUpdate(): AppUpdateInfo?
	suspend fun downloadApk(context: Context, updateInfo: AppUpdateInfo)
}

class AppUpdateRepositoryImpl(
	private val api: ApiClient,
) : AppUpdateRepository {

	companion object {
		private const val NOTIFICATION_CHANNEL_ID = "app_update"
		private const val DOWNLOAD_ID_KEY = "app_update_download_id"
	}

	override val updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
	override val isChecking = MutableStateFlow(false)
	override val downloadProgress = MutableStateFlow(-1)

	override suspend fun checkForUpdate(): AppUpdateInfo? {
		isChecking.value = true
		return try {
			val baseUrl = api.baseUrl ?: run {
				isChecking.value = false
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
			val connection = java.net.URL(uri.toString()).openConnection() as java.net.HttpURLConnection
			connection.requestMethod = "GET"
			connection.setRequestProperty(
				"Authorization",
				api.accessToken?.let { "MediaBrowser Token=\"$it\"" } ?: ""
			)
			connection.setRequestProperty("X-Emby-Authorization", "MediaBrowser Client=\"Jellyfin for Android TV\", Device=\"androidtv\", Version=\"$versionName\"")
			connection.setRequestProperty("Client", "Jellyfin for Android TV")
			connection.setRequestProperty("Version", versionName)
			connection.connectTimeout = 10000
			connection.readTimeout = 10000

			if (connection.responseCode == 200) {
				val body = connection.inputStream.bufferedReader().readText()
				// Prepend base URL to relative download URLs
				val fixedBody = body.replace(
					"\"/AppUpdate/Download",
					"\"$baseUrl/AppUpdate/Download"
				)
				parseUpdateInfo(fixedBody)
			} else {
				Timber.w("Update check failed with code %d", connection.responseCode)
				null
			}
		}
			updateInfo.value = response
			isChecking.value = false
			response
		} catch (e: Exception) {
			Timber.e(e, "Failed to check for app update")
			isChecking.value = false
			null
		}
	}

	private fun parseUpdateInfo(json: String): AppUpdateInfo {
		val updateAvailable = json.contains("\"updateAvailable\":true")
		if (!updateAvailable) return AppUpdateInfo(updateAvailable = false)

		return AppUpdateInfo(
			updateAvailable = true,
			appVersion = extractString(json, "appVersion"),
			appVersionCode = extractInt(json, "appVersionCode"),
			downloadUrl = extractString(json, "downloadUrl"),
			downloadSize = extractLong(json, "downloadSize"),
			checksum = extractString(json, "checksum"),
			mandatory = json.contains("\"mandatory\":true"),
		)
	}

	private fun extractString(json: String, key: String): String =
		Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""

	private fun extractInt(json: String, key: String): Int =
		Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

	private fun extractLong(json: String, key: String): Long =
		Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

	override suspend fun downloadApk(context: Context, updateInfo: AppUpdateInfo) {
		if (updateInfo.downloadUrl.isEmpty()) return

		withContext(Dispatchers.IO) {
			try {
				val channel = NotificationChannel(
					NOTIFICATION_CHANNEL_ID,
					"App Update",
					NotificationManager.IMPORTANCE_LOW
				)
				NotificationManagerCompat.from(context).createNotificationChannel(channel)

				val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
				val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
					.setTitle("Jellyfin ${updateInfo.appVersion}")
					.setDescription("Downloading update...")
					.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
					.setDestinationInExternalPublicDir(
						Environment.DIRECTORY_DOWNLOADS,
						"jellyfin-${updateInfo.appVersion}.apk"
					)
					.setAllowedOverMetered(true)
					// Add auth headers for server authentication
					.addRequestHeader("Authorization", api.accessToken?.let { "MediaBrowser Token=\"$it\"" } ?: "")
					.addRequestHeader("X-Emby-Authorization", "MediaBrowser Client=\"Jellyfin for Android TV\", Device=\"androidtv\", Version=\"${BuildConfig.VERSION_NAME}\"")

				val downloadId = dm.enqueue(request)
				Timber.i("Download started: $downloadId")
				downloadProgress.value = 0

				// Monitor until done
				var cursor: Cursor? = null
				try {
					val query = DownloadManager.Query().setFilterById(downloadId)
					while (true) {
						cursor = dm.query(query)
						if (cursor != null && cursor.moveToFirst()) {
							val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
							val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
							val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
							if (total > 0) downloadProgress.value = ((downloaded * 100) / total).toInt()

							if (status == DownloadManager.STATUS_SUCCESSFUL) {
								downloadProgress.value = 100
								val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
								val file = File(Uri.parse(uri).path ?: return@withContext)
								launchInstaller(context, file)
								downloadProgress.value = -1
								return@withContext
							}
							if (status == DownloadManager.STATUS_FAILED) {
								downloadProgress.value = -1
								return@withContext
							}
						}
						cursor?.close()
						delay(500)
					}
				} finally {
					cursor?.close()
				}
			} catch (e: Exception) {
				Timber.e(e, "Download failed")
				downloadProgress.value = -1
			}
		}
	}

	private fun launchInstaller(context: Context, apkFile: File) {
		val uri = androidx.core.content.FileProvider.getUriForFile(
			context,
			"${BuildConfig.APPLICATION_ID}.fileprovider",
			apkFile
		)
		val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
			addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		context.startActivity(intent)
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
