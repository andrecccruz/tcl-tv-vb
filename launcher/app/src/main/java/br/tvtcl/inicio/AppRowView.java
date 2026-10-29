package br.tvtcl.inicio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Fileira única de apps, desenhada inteira aqui (sem uma View por app). O app em foco fica sempre no centro da tela:
 * as setas fazem a fileira deslizar, e não o foco. O app do centro é 1,6x maior, com borda clara e brilho; os outros
 * ficam um pouco transparentes. Embaixo do app do centro, só o nome dele.
 * <p>
 * As teclas chegam pela MainActivity ({@link #move}); esta View não recebe foco do Android.
 */
public class AppRowView extends View {

    // Medidas em dp de uma tela de 1280x720.
    private static final float TILE_DP = 116;
    private static final float FOCUS_SCALE = 1.6f;
    private static final float GAP_DP = 30;
    private static final float CENTER_Y_DP = 418;
    private static final float LABEL_GAP_DP = 34;
    private static final float EDGE_FADE_DP = 120;
    private static final float UNFOCUSED_ALPHA = 0.55f;
    private static final float DIMMED = 0.3f;
    private static final long SLIDE_MS = 450;
    private static final float GLOW_SCALE = 128f / 72f;

    /** Lado, em pixels, dos blocos já desenhados: o tamanho do app em foco, para ele ficar nítido. */
    static int tilePx(Context context) {
        return Math.round(Ui.dp(context, TILE_DP * FOCUS_SCALE));
    }

    private final List<Apps.Entry> apps = new ArrayList<>();
    private final float tile;
    private final float step;
    private final float centerYOffset;
    private final float labelGap;
    private final float edgeFade;
    private final float radius;
    private final float glowPad;
    private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final Bitmap glow;

    private int index;
    private float position;     // índice "contínuo" que está no centro; anima até index
    private float dim = 1f;     // 1 = normal, DIMMED = com o menu em foco
    private final ValueAnimator slide = new ValueAnimator();
    private final ValueAnimator dimmer = new ValueAnimator();

    public AppRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        tile = Ui.dp(context, TILE_DP);
        step = tile + Ui.dp(context, GAP_DP);
        centerYOffset = Ui.dp(context, CENTER_Y_DP);
        labelGap = Ui.dp(context, LABEL_GAP_DP);
        edgeFade = Ui.dp(context, EDGE_FADE_DP);
        radius = tile * 0.15f;
        glowPad = Ui.dp(context, 22);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Ui.dp(context, 2.5f));
        borderPaint.setColor(0xFFFFFFFF);
        glowPaint.setColor(0xFFFFF4DE);
        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTextSize(Ui.dp(context, 22));
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setShadowLayer(Ui.dp(context, 6), 0, 0, 0x99000000);
        glow = makeGlow();

        DecelerateInterpolator decelerate = new DecelerateInterpolator(1.8f);
        slide.setDuration(SLIDE_MS);
        slide.setInterpolator(decelerate);
        slide.addUpdateListener(a -> {
            position = (float) a.getAnimatedValue();
            invalidate();
        });
        dimmer.setDuration(SLIDE_MS);
        dimmer.setInterpolator(decelerate);
        dimmer.addUpdateListener(a -> {
            dim = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    /** Brilho suave: um quadrado arredondado desfocado, desenhado uma vez e esticado em volta do app em foco. */
    private static Bitmap makeGlow() {
        int size = 128;
        int pad = 28;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setMaskFilter(new BlurMaskFilter(14, BlurMaskFilter.Blur.NORMAL));
        new Canvas(bitmap).drawRoundRect(pad, pad, size - pad, size - pad, 14, 14, paint);
        return bitmap;
    }

    /** Troca a lista mantendo no centro o app que estava lá (se ele ainda existir). */
    void setApps(List<Apps.Entry> list) {
        String current = apps.isEmpty() ? null : apps.get(index).component.flattenToShortString();
        apps.clear();
        apps.addAll(list);
        int keep = 0;
        if (current != null) {
            for (int i = 0; i < apps.size(); i++) {
                if (apps.get(i).component.flattenToShortString().equals(current)) {
                    keep = i;
                    break;
                }
            }
        }
        slide.cancel();
        index = keep;
        position = keep;
        invalidate();
    }

    Apps.Entry selected() {
        return apps.isEmpty() ? null : apps.get(index);
    }

    void move(int delta) {
        select(index + delta, true);
    }

    void select(int target, boolean animate) {
        if (apps.isEmpty()) {
            return;
        }
        target = Math.max(0, Math.min(apps.size() - 1, target));
        if (target == index && !slide.isRunning() && position == target) {
            return;
        }
        index = target;
        slide.cancel();
        if (animate) {
            // Parte de onde a fileira está agora: apertar várias vezes seguidas não dá solavanco.
            slide.setFloatValues(position, target);
            slide.start();
        } else {
            position = target;
            invalidate();
        }
    }

    void setDimmed(boolean dimmed) {
        dimmer.cancel();
        dimmer.setFloatValues(dim, dimmed ? DIMMED : 1f);
        dimmer.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (apps.isEmpty()) {
            return;
        }
        float width = getWidth();
        float cx = width / 2f;
        float cy = centerYOffset;
        float extra = tile * (FOCUS_SCALE - 1f) / 2f;   // quanto o app em foco "empurra" os vizinhos

        int first = Math.max(0, (int) Math.floor(position - cx / step) - 1);
        int last = Math.min(apps.size() - 1, (int) Math.ceil(position + cx / step) + 1);
        for (int i = first; i <= last; i++) {
            float d = i - position;
            float ad = Math.abs(d);
            float focus = Math.max(0f, 1f - ad);
            float size = tile * (1f + (FOCUS_SCALE - 1f) * focus);
            float x = cx + Math.signum(d) * (ad * step + Math.min(ad, 1f) * extra);
            float half = size / 2f;
            if (x + half < 0 || x - half > width) {
                continue;
            }
            float edge = Math.min(1f, Math.max(0f, Math.min(x, width - x) / edgeFade));
            float alpha = (UNFOCUSED_ALPHA + (1f - UNFOCUSED_ALPHA) * focus) * edge * dim;
            dst.set(x - half, cy - half, x + half, cy + half);

            if (focus > 0f) {
                // No bitmap do brilho, o quadrado ocupa 72 de 128 px; o resto é o desfoque em volta.
                float g = (half + glowPad * 0.2f) * GLOW_SCALE;
                glowPaint.setAlpha(Math.round(140 * focus * dim));
                canvas.drawBitmap(glow, null, rect(x - g, cy - g, x + g, cy + g), glowPaint);
                dst.set(x - half, cy - half, x + half, cy + half);
            }

            tilePaint.setAlpha(Math.round(255 * alpha));
            canvas.drawBitmap(apps.get(i).tile, null, dst, tilePaint);

            if (focus > 0f) {
                float r = radius * size / tile;
                float inset = borderPaint.getStrokeWidth() / 2f;
                dst.inset(inset, inset);
                borderPaint.setAlpha(Math.round(235 * focus * dim));
                canvas.drawRoundRect(dst, r, r, borderPaint);
            }
        }

        // Nome do app do centro (some e volta durante o deslize).
        int nearest = Math.round(position);
        float focus = Math.max(0f, 1f - Math.abs(nearest - position) * 2f);
        if (focus > 0f && nearest >= 0 && nearest < apps.size()) {
            labelPaint.setAlpha(Math.round(255 * focus * dim));
            CharSequence label = TextUtils.ellipsize(apps.get(nearest).label, labelPaint, width * 0.5f,
                    TextUtils.TruncateAt.END);
            canvas.drawText(label, 0, label.length(), cx, cy + tile * FOCUS_SCALE / 2f + labelGap, labelPaint);
        }
    }

    private RectF rect(float l, float t, float r, float b) {
        dst.set(l, t, r, b);
        return dst;
    }
}
