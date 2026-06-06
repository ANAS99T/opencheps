package com.opencheps.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String FILE = "opencheps";
    public  static final String KEY_USER_SIDE = "user_side";
    public  static final String KEY_THINK_MS  = "think_ms";
    public  static final String KEY_MULTIPV   = "multipv";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String userSide(Context c) {
        return get(c).getString(KEY_USER_SIDE, "w");
    }

    public static void setUserSide(Context c, String side) {
        get(c).edit().putString(KEY_USER_SIDE, side).apply();
    }

    public static int thinkMs(Context c) { return get(c).getInt(KEY_THINK_MS, 1500); }
    public static int multiPv(Context c) { return get(c).getInt(KEY_MULTIPV, 3); }
}
