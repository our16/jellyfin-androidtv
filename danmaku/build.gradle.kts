plugins {
	alias(libs.plugins.android.library)
}

android {
	namespace = "org.jellyfin.danmaku"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
	}
}

dependencies {
	// Kotlin
	implementation(libs.kotlinx.coroutines)

	// Android(x)
	implementation(libs.androidx.core)
	implementation(libs.androidx.appcompat)
	implementation(libs.timber)

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)
}
