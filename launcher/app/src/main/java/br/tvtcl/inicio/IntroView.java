package br.tvtcl.inicio;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.nio.ByteBuffer;

/** Abertura: tela preta e uma faixa de luz que varre da esquerda para a direita. Depois, chama onDone. */
public class IntroView extends View {

    private static final long SWEEP_MS = 2000;

    private final Paint bandPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint corePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final Bitmap band;
    private final ValueAnimator sweep = ValueAnimator.ofFloat(0f, 1f);
    private float progress;
    private Runnable onDone;

    public IntroView(Context context, AttributeSet attrs) {
        super(context, attrs);
        band = makeBand();
        bandPaint.setColor(0xFFE9D9B8);
        corePaint.setColor(0xFFFFFFFF);
        sweep.setDuration(SWEEP_MS);
        sweep.setInterpolator(new AccelerateDecelerateInterpolator());
        sweep.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        sweep.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finish();
            }
        });
    }

    /** Perfil horizontal da faixa (forte no meio, sumindo nas bordas), mais fraco no alto e embaixo. */
    private static Bitmap makeBand() {
        int w = 128;
        int h = 64;
        byte[] pixels = new byte[w * h];
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h * 2f - 1f;
            float vertical = 1f - 0.65f * v * v;
            for (int x = 0; x < w; x++) {
                float u = (x + 0.5f) / w * 2f - 1f;
                float a = (float) Math.exp(-u * u * 5f) * vertical;
                pixels[y * w + x] = (byte) Math.round(255 * a);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
        return bitmap;
    }

    void play(Runnable done) {
        onDone = done;
        setVisibility(VISIBLE);
        setAlpha(1f);
        progress = 0f;
        sweep.start();
    }

    boolean isPlaying() {
        return sweep.isRunning();
    }

    /** Termina já (por exemplo, se o usuário apertar uma tecla). */
    void finish() {
        if (onDone == null) {
            return;
        }
        Runnable done = onDone;
        onDone = null;
        sweep.cancel();
        animate().alpha(0f).setDuration(500).withEndAction(() -> setVisibility(GONE)).start();
        done.run();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        canvas.drawColor(0xFF000000);
        float bandW = w * 0.32f;
        float x = -bandW + (w + bandW * 2f) * progress;
        // Some devagar no começo e no fim da varredura.
        float fade = (float) Math.sin(Math.PI * Math.min(1f, Math.max(0f, progress)));
        bandPaint.setAlpha(Math.round(110 * fade));
        dst.set(x - bandW / 2f, 0, x + bandW / 2f, h);
        canvas.drawBitmap(band, null, dst, bandPaint);
        corePaint.setAlpha(Math.round(200 * fade));
        float core = w * 0.012f;
        dst.set(x - core, h * 0.1f, x + core, h * 0.9f);
        canvas.drawBitmap(band, null, dst, corePaint);
    }
}
