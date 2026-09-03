package tv.cjump.jni;

/**
 * Stub implementation for TV devices. Problem device detection is not needed
 * for Jellyfin Android TV as we target modern hardware.
 */
public class DeviceUtils {

    public static boolean isProblemBoxDevice() {
        return false;
    }

    public static boolean isMiBox2Device() {
        return false;
    }

    public static boolean isMagicBoxDevice() {
        return false;
    }
}
