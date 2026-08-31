package org.jellyfin.androidtv.util.profile

import android.os.Build

/**
 * List of device models with known HEVC DoVi/HDR10+ playback issues.
 */
private val modelsWithDoViHdr10PlusBug = listOf(
	"AFTKRT", // Amazon Fire TV 4K Max (2nd Gen)
	"AFTKA", // Amazon Fire TV 4K Max (1st Gen)
	"AFTKM", // Amazon Fire TV 4K (2nd Gen)
	"AFTMM", // Amazon Fire TV 4K (1st Gen)
)

/**
 * List of device models with unreported Dolby Vision Profile 7 support.
 */
private val modelsWithUnreportedDoviProfile7Support = listOf(
	"SHIELD Android TV", // NVIDIA Shield TV Pro 2019 (mdarcy)
)

/**
 * XGIMI projector models - 4K LED projectors with MTK9669 chip.
 * These devices have 4GB RAM and support 4K HDR playback.
 */
private val xgimiProjectorModels = listOf(
	"H6",      // XGIMI H6 High Brightness Fixed Focus
	"H6 Pro",  // XGIMI H6 Pro
	"H3",      // XGIMI H3
	"H3 Pro",  // XGIMI H3 Pro
	"Horizon", // XGIMI Horizon series
	"MoGo",    // XGIMI MoGo series
)

object KnownDefects {
	val hevcDoviHdr10PlusBug = Build.MODEL in modelsWithDoViHdr10PlusBug
	val unreportedDoviProfile7Support = Build.MODEL in modelsWithUnreportedDoviProfile7Support

	/**
	 * Check if current device is an XGIMI projector.
	 * These devices benefit from 4K-optimized settings.
	 */
	val isXgimiProjector: Boolean by lazy {
		val model = Build.MODEL
		val manufacturer = Build.MANUFACTURER
		manufacturer.contains("XGIMI", ignoreCase = true) ||
			xgimiProjectorModels.any { model.contains(it, ignoreCase = true) }
	}

	/**
	 * Check if current device is a 4K projector with limited RAM (4GB).
	 * These devices need memory-conscious optimizations.
	 */
	val is4KProjector: Boolean by lazy {
		isXgimiProjector && Runtime.getRuntime().maxMemory() < 5L * 1024 * 1024 * 1024 // < 5GB heap
	}
}
