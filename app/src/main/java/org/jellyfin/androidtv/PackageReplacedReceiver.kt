package org.jellyfin.androidtv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Relaunches the app after an overwrite install completes: the system delivers
 * ACTION_MY_PACKAGE_REPLACED to the freshly installed (closed) app, which lets us
 * bring the new version right back to the foreground.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

		Timber.i("Package replaced: relaunching app after update")
		try {
			val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
			if (launch != null) {
				launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
				context.startActivity(launch)
				Timber.i("Update relaunch started")
			}
		} catch (e: Exception) {
			Timber.e(e, "Failed to relaunch app after package replacement")
		}
	}
}
