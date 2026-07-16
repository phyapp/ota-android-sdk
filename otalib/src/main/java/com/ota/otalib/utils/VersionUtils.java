package com.ota.otalib.utils;

import android.os.Build;

public class VersionUtils {

    /**
     * 当前是否在Android6.0及以上
     */
    public static boolean isAndroid6() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /**
     * 当前是否在Android8.0及以上
     */
    public static boolean isAndroid8() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    /**
     * 当前是否在Android10.0及以上
     */
    public static boolean isAndroid10() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /**
     * 当前是否在Android11.0及以上
     */
    public static boolean isAndroid11() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /**
     * 当前是否在Android12.0及以上
     */
    public static boolean isAndroid12() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /**
     * 当前是否在Android13.0及以上
     */
    public static boolean isAndroid13() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }
}
