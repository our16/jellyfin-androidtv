package org.jellyfin.androidtv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import timber.log.Timber

/**
 * Receives PackageInstaller session status broadcasts.
 *
 * MUST be statically registered in the manifest: the app process gets killed right
 * after commit() during an overwrite install, so a dynamically registered receiver
 * would die with it and STATUS_PENDING_USER_ACTION would never be handled (no
 * install confirmation dialog would ever appear). The static receiver gets the
 * process resurrected by the system even when the app is closed.
 */
class InstallStatusReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != INSTALL_STATUS_ACTION) return

		val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
		Timber.i("Install status broadcast: %s", status)

		when (status) {
			PackageInstaller.STATUS_PENDING_USER_ACTION -> {
				val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
				if (confirm != null) {
					try {
						confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						context.startActivity(confirm)
						Timber.i("Install confirmation dialog launched")
					} catch (e: Exception) {
						Timber.e(e, "Failed to launch install confirmation")
						Toast.makeText(context, "无法显示安装确认: ${e.message}", Toast.LENGTH_LONG).show()
					}
				} else {
					Toast.makeText(context, "安装确认不可用", Toast.LENGTH_LONG).show()
				}
			}
			PackageInstaller.STATUS_SUCCESS -> {
				Toast.makeText(context, "安装成功，正在重启…", Toast.LENGTH_LONG).show()
			}
			else -> {
				Timber.e("Install failed with status %s", status)
				Toast.makeText(context, "安装失败 (status=$status)", Toast.LENGTH_LONG).show()
			}
		}
	}

	companion object {
		const val INSTALL_STATUS_ACTION = "org.jellyfin.androidtv.action.INSTALL_STATUS"
	}
}
