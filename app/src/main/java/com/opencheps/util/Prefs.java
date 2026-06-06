package com.opencheps.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String FILE = "opencheps";
    public  static final String KEY_AUTO_SIDE    = "auto_side";
    public  static final String KEY_FORCED_SIDE  = "forced_side";
    public  static final String KEY_THINK_MS     = "think_ms";
    public  static final String KEY_MULTIPV      = "multipv";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean autoSide(Context c)     { return get(c).getBoolean(KEY_AUTO_SIDE, true); }
    public static String  forcedSide(Context c)   { return get(c).getString(KEY_FORCED_SIDE, "w"); }
    public static int     thinkMs(Context c)      { return get(c).getInt(KEY_THINK_MS, 700); }
    public static int     multiPv(Context c)      { return get(c).getInt(KEY_MULTIPV, 3); }
}
