package br.tvtcl.inicio;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes do launcher, guardados no próprio app. */
final class Prefs {

    static final int PARTICLES_FEW = 0;
    static final int PARTICLES_NORMAL = 1;
    static final int PARTICLES_MANY = 2;

    private static final String FILE = "ajustes";
    private static final String KEY_ANIMATED_BACKGROUND = "fundo_animado";
    private static final String KEY_PARTICLES = "particulas";

    private Prefs() {
    }

    static boolean animatedBackground(Context context) {
        return prefs(context).getBoolean(KEY_ANIMATED_BACKGROUND, true);
    }

    static void setAnimatedBackground(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ANIMATED_BACKGROUND, enabled).apply();
    }

    static int particles(Context context) {
        return prefs(context).getInt(KEY_PARTICLES, PARTICLES_NORMAL);
    }

    static void setParticles(Context context, int level) {
        prefs(context).edit().putInt(KEY_PARTICLES, level).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
