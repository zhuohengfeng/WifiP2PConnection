package com.ryan.wifip2pconnection.utils;

import android.util.Log;

public class Logger {
    public static String TAG = "zhf_p2p";
    public static boolean DEBUG = true;

    public static void d(String message, Object... args) {
        //f (DEBUG) {
            Log.d(TAG, String.format(message, args));
        //}
    }

    public static void w(String message, Object... args) {
        Log.w(TAG, String.format(message, args));
    }

    public static void e(String message, Object... args) {
        Log.e(TAG, String.format(message, args));
    }

    public static void e(Throwable t, String message, Object... args) {
        Log.e(TAG, String.format(message, args), t);
    }
}
