package br.tvtcl.inicio;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

/**
 * As telas são desenhadas em dp de uma TV de 1280x720. Aqui a densidade é ajustada para que 1280 dp ocupem a
 * largura real da tela (na TV de 1920x1080, 1 dp = 1,5 px), seja qual for a densidade que a TCL configurou.
 */
final class Ui {

    private static final float DESIGN_WIDTH_DP = 1280f;

    private Ui() {
    }

    /** Use em attachBaseContext de cada Activity. */
    static Context scaled(Context base) {
        DisplayMetrics metrics = base.getResources().getDisplayMetrics();
        int longSide = Math.max(metrics.widthPixels, metrics.heightPixels);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.densityDpi = Math.round(DisplayMetrics.DENSITY_DEFAULT * longSide / DESIGN_WIDTH_DP);
        config.fontScale = 1f;
        return base.createConfigurationContext(config);
    }

    static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
