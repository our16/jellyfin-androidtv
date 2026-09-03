package tv.cjump.jni;

import android.graphics.Bitmap;

/**
 * Stub implementation for API 23+. Native bitmap allocation is not needed
 * on modern Android as the runtime handles bitmap memory natively.
 */
public class NativeBitmapFactory {

    public static boolean isInNativeAlloc() {
        return false;
    }

    public static void loadLibs() {
        // No-op on API 23+
    }

    public static synchronized void releaseLibs() {
        // No-op on API 23+
    }

    public static Bitmap createBitmap(int width, int height, Bitmap.Config config) {
        return Bitmap.createBitmap(width, height, config);
    }

    public static void recycle(Bitmap bitmap) {
        bitmap.recycle();
    }
}
