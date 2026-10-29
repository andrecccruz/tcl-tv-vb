package br.tvtcl.inicio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Menu do rodapé: 4 botões redondos, pequenos e discretos. Com o foco no menu, ele sobe um pouco e os botões viram
 * pílulas com ícone e texto; o item em foco fica branco com texto escuro.
 * <p>
 * As teclas chegam pela MainActivity ({@link #move}, {@link #setExpanded}); esta View não recebe foco do Android.
 */
public class MenuBarView extends View {

    static final int SEARCH = 0;
    static final int ALL_APPS = 1;
    static final int LAUNCHER_SETTINGS = 2;
    static final int SETTINGS = 3;

    private static final int[] ICONS = {R.drawable.ic_search, R.drawable.ic_apps, R.drawable.ic_tune,
            R.drawable.ic_settings};
    private static final int[] LABELS = {R.string.search, R.string.all_apps, R.string.launcher_settings,
            R.string.settings};
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF121726;
    private static final long ANIM_MS = 350;

    // Medidas em dp de uma tela de 1280x720.
    private final float circle;       // diâmetro recolhido
    private final float pillHeight;
    private final float baseY;        // centro vertical recolhido
    private final float rise;         // quanto sobe ao expandir
    private final float iconSize;
    private final float padStart;
    private final float iconTextGap;
    private final float padEnd;
    private final float gapCollapsed;
    private final float gapExpanded;

    private final String[] labels = new String[ICONS.length];
    private final float[] textWidths = new float[ICONS.length];
    private final Drawable[] iconsLight = new Drawable[ICONS.length];
    private final Drawable[] iconsDark = new Drawable[ICONS.length];
    private final float[] highlight = new float[ICONS.length];       // 0..1, quanto o item está "branco"
    private final float[] highlightFrom = new float[ICONS.length];
    private final float[] widths = new float[ICONS.length];
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();

    private int focused;
    private boolean expandedTarget;
    private float expand;            // 0 = bolinhas, 1 = pílulas
    private float expandFrom;
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);

    public MenuBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        circle = Ui.dp(context, 40);
        pillHeight = Ui.dp(context, 46);
        baseY = Ui.dp(context, 664);
        rise = Ui.dp(context, 22);
        iconSize = Ui.dp(context, 22);
        padStart = Ui.dp(context, 18);
        iconTextGap = Ui.dp(context, 10);
        padEnd = Ui.dp(context, 22);
        gapCollapsed = Ui.dp(context, 18);
        gapExpanded = Ui.dp(context, 14);

        text.setTextSize(Ui.dp(context, 17));
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        for (int i = 0; i < ICONS.length; i++) {
            labels[i] = context.getString(LABELS[i]);
            textWidths[i] = text.measureText(labels[i]);
            iconsLight[i] = tinted(context, ICONS[i], LIGHT);
            iconsDark[i] = tinted(context, ICONS[i], DARK);
        }

        anim.setDuration(ANIM_MS);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            float target = expandedTarget ? 1f : 0f;
            expand = expandFrom + (target - expandFrom) * t;
            for (int i = 0; i < highlight.length; i++) {
                float goal = expandedTarget && i == focused ? 1f : 0f;
                highlight[i] = highlightFrom[i] + (goal - highlightFrom[i]) * t;
            }
            invalidate();
        });
    }

    private static Drawable tinted(Context context, int res, int color) {
        Drawable drawable = context.getDrawable(res).mutate();
        drawable.setTint(color);
        return drawable;
    }

    int focused() {
        return focused;
    }

    void setExpanded(boolean expanded) {
        expandedTarget = expanded;
        restart();
    }

    void move(int delta) {
        int target = Math.max(0, Math.min(ICONS.length - 1, focused + delta));
        if (target != focused) {
            focused = target;
            restart();
        }
    }

    private void restart() {
        anim.cancel();
        expandFrom = expand;
        System.arraycopy(highlight, 0, highlightFrom, 0, highlight.length);
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float height = circle + (pillHeight - circle) * expand;
        float gap = gapCollapsed + (gapExpanded - gapCollapsed) * expand;
        float total = gap * (ICONS.length - 1);
        for (int i = 0; i < ICONS.length; i++) {
            float full = padStart + iconSize + iconTextGap + textWidths[i] + padEnd;
            widths[i] = circle + (full - circle) * expand;
            total += widths[i];
        }
        float cy = baseY - rise * expand;
        float x = (getWidth() - total) / 2f;
        float r = height / 2f;

        for (int i = 0; i < ICONS.length; i++) {
            float w = widths[i];
            float h = highlight[i];
            pill.set(x, cy - r, x + w, cy + r);

            // Fundo: discreto recolhido, um pouco mais claro expandido, branco no item em foco.
            int base = Math.round(255 * (0.10f + 0.06f * expand));
            int alpha = Math.round(base + (255 - base) * h);
            fill.setColor(LIGHT);
            fill.setAlpha(alpha);
            canvas.drawRoundRect(pill, r, r, fill);

            // Ícone: centralizado na bolinha; na pílula, à esquerda.
            float iconCx = x + (w / 2f) * (1f - expand) + (padStart + iconSize / 2f) * expand;
            int il = Math.round(iconCx - iconSize / 2f);
            int it = Math.round(cy - iconSize / 2f);
            int contentAlpha = Math.round(255 * (0.65f + 0.3f * expand));
            Drawable light = iconsLight[i];
            Drawable dark = iconsDark[i];
            light.setBounds(il, it, il + Math.round(iconSize), it + Math.round(iconSize));
            dark.setBounds(light.getBounds());
            light.setAlpha(Math.round(contentAlpha * (1f - h)));
            light.draw(canvas);
            if (h > 0f) {
                dark.setAlpha(Math.round(255 * h));
                dark.draw(canvas);
            }

            // Texto: aparece só com a pílula aberta, recortado nela durante a animação.
            if (expand > 0.05f) {
                canvas.save();
                canvas.clipRect(pill);
                text.setColor(blend(LIGHT, DARK, h));
                text.setAlpha(Math.round(255 * Math.min(1f, expand * 1.4f) * (0.9f + 0.1f * h)));
                float tx = x + padStart + iconSize + iconTextGap;
                float ty = cy - (text.descent() + text.ascent()) / 2f;
                canvas.drawText(labels[i], tx, ty, text);
                canvas.restore();
            }
            x += w + gap;
        }
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
