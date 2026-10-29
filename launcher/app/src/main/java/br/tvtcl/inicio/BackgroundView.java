package br.tvtcl.inicio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Fundo animado da tela inicial, desenhado em Canvas por hardware (sem bibliotecas):
 * gradiente quase preto azulado/arroxeado, dois raios de luz balançando a partir do canto superior esquerdo,
 * partículas bokeh douradas e brancas numa faixa no meio, estrelas atravessando da direita para a esquerda e duas
 * fitas de onda atrás da fileira de apps.
 * <p>
 * Desempenho (a TV tem GPU fraca e 2 GB de RAM): no máximo 30 quadros por segundo; tudo o que é "macio" (raios,
 * bokeh, estrelas, ondas) é um bitmap ALPHA_8 pequeno feito uma vez e só esticado/movido por quadro, então a GPU
 * desenha retângulos com textura e nada de caminhos ou desfoque. Posições ficam em arrays: nada é alocado por quadro.
 * Controle com {@link #start()} (onResume) e {@link #stop()} (onPause).
 */
public class BackgroundView extends View implements Choreographer.FrameCallback {

    private static final long FRAME_NS = 1_000_000_000L / 30;
    private static final long FRAME_SLACK_NS = 4_000_000L;

    /** Partículas por nível (Prefs.PARTICLES_FEW, NORMAL, MANY). */
    private static final int[] BOKEH_COUNT = {35, 70, 80};
    private static final int[] STAR_COUNT = {12, 25, 30};
    private static final int MAX_BOKEH = 80;
    private static final int MAX_STARS = 30;
    private static final int[] BOKEH_COLORS = {0xFFFFC266, 0xFFFFD58E, 0xFFFFE9C4, 0xFFFFFFFF};

    private final Choreographer choreographer = Choreographer.getInstance();
    private final Random random = new Random();
    private final Paint background = new Paint();
    private final Paint sprite = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint rayPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint wavePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix rayMatrix = new Matrix();
    private final RectF dst = new RectF();
    private final Bitmap softDot = makeDot(true);
    private final Bitmap sharpDot = makeDot(false);
    private final Bitmap ray = makeRay();

    // Bokeh.
    private final float[] bx = new float[MAX_BOKEH];
    private final float[] by = new float[MAX_BOKEH];
    private final float[] bvx = new float[MAX_BOKEH];
    private final float[] bvy = new float[MAX_BOKEH];
    private final float[] bSize = new float[MAX_BOKEH];
    private final float[] bAge = new float[MAX_BOKEH];
    private final float[] bLife = new float[MAX_BOKEH];
    private final float[] bPeak = new float[MAX_BOKEH];
    private final float[] bPhase = new float[MAX_BOKEH];
    private final int[] bColor = new int[MAX_BOKEH];
    private final boolean[] bSoft = new boolean[MAX_BOKEH];

    // Estrelas.
    private final float[] sx = new float[MAX_STARS];
    private final float[] sy = new float[MAX_STARS];
    private final float[] sSpeed = new float[MAX_STARS];
    private final float[] sSize = new float[MAX_STARS];
    private final float[] sPeak = new float[MAX_STARS];

    // Ondas: um período de cada fita em bitmap, repetido lado a lado e deslizado em pixels inteiros (sem emenda).
    private final Bitmap[] waveBitmap = new Bitmap[2];
    private final float[] waveTop = new float[2];
    private final float[] waveOffset = new float[2];
    private static final float[] WAVE_SPEED = {0.018f, 0.032f};      // fração da largura por segundo
    private static final int[] WAVE_COLOR = {0xFFD8E2FF, 0xFFEADCFF};

    private int width;
    private int height;
    private int bokehCount = BOKEH_COUNT[Prefs.PARTICLES_NORMAL];
    private int starCount = STAR_COUNT[Prefs.PARTICLES_NORMAL];
    private boolean animated = true;
    private boolean running;
    private long lastFrameNs;
    private float time;

    public BackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Liga/desliga a animação e escolhe a quantidade de partículas (Prefs.PARTICLES_*). */
    void configure(boolean animated, int level) {
        int bokeh = BOKEH_COUNT[level];
        int stars = STAR_COUNT[level];
        if (bokeh != bokehCount || stars != starCount) {
            bokehCount = bokeh;
            starCount = stars;
            if (width > 0) {
                seedParticles();
            }
        }
        this.animated = animated;
        if (!animated) {
            stop();
        }
        invalidate();   // desligado, fica o último quadro parado
    }

    void start() {
        if (animated && !running) {
            running = true;
            lastFrameNs = 0;
            choreographer.postFrameCallback(this);
        }
    }

    void stop() {
        running = false;
        choreographer.removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        // A tela pode ir a 60 Hz; só desenha quando já passou ~1/30 s.
        if (lastFrameNs == 0 || frameTimeNanos - lastFrameNs >= FRAME_NS - FRAME_SLACK_NS) {
            float dt = lastFrameNs == 0 ? 0f : Math.min(0.1f, (frameTimeNanos - lastFrameNs) / 1e9f);
            lastFrameNs = frameTimeNanos;
            step(dt);
            invalidate();
        }
        choreographer.postFrameCallback(this);
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        if (w == 0 || h == 0) {
            return;
        }
        background.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xFF0D1226, 0xFF07080F, 0xFF140B24}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        float density = getResources().getDisplayMetrics().density;
        // Atrás da fileira de apps (centro em 418 de 720).
        waveBitmap[0] = makeWave(Math.round(w * 0.62f), h * 0.028f, h * 0.05f, h * 0.018f, density);
        waveBitmap[1] = makeWave(Math.round(w * 0.86f), h * 0.022f, h * 0.075f, h * 0.026f, density);
        waveTop[0] = h * 0.555f - waveBitmap[0].getHeight() / 2f;
        waveTop[1] = h * 0.625f - waveBitmap[1].getHeight() / 2f;
        seedParticles();
    }

    private void seedParticles() {
        for (int i = 0; i < MAX_BOKEH; i++) {
            spawnBokeh(i, true);
        }
        for (int i = 0; i < MAX_STARS; i++) {
            spawnStar(i, true);
        }
    }

    private float rand(float from, float to) {
        return from + random.nextFloat() * (to - from);
    }

    private void spawnBokeh(int i, boolean anywhere) {
        float h = height;
        boolean soft = random.nextFloat() < 0.18f;
        bSoft[i] = soft;
        bSize[i] = soft ? h * rand(0.05f, 0.11f) : h * rand(0.006f, 0.02f);
        bPeak[i] = soft ? rand(0.06f, 0.15f) : rand(0.35f, 0.85f);
        bColor[i] = BOKEH_COLORS[random.nextInt(BOKEH_COLORS.length)];
        bx[i] = rand(0f, width * 1.05f);
        // Faixa no meio da tela: média de 3 sorteios concentra perto do centro.
        float band = (random.nextFloat() + random.nextFloat() + random.nextFloat()) / 3f;
        by[i] = h * (0.24f + 0.52f * band);
        bvx[i] = -h * rand(0.008f, 0.03f) * (soft ? 0.6f : 1f);
        bvy[i] = h * rand(-0.004f, 0.004f);
        bLife[i] = rand(5f, 12f);
        bAge[i] = anywhere ? random.nextFloat() * bLife[i] : 0f;
        bPhase[i] = rand(0f, 6.283f);
    }

    private void spawnStar(int i, boolean anywhere) {
        sx[i] = anywhere ? rand(0f, width) : width + rand(0f, width * 0.1f);
        sy[i] = height * rand(0.05f, 0.95f);
        sSpeed[i] = width * rand(0.04f, 0.1f);
        sSize[i] = height * rand(0.0025f, 0.005f);
        sPeak[i] = rand(0.45f, 0.9f);
    }

    private void step(float dt) {
        if (width == 0) {
            return;   // ainda sem tamanho (o primeiro quadro pode vir antes do layout)
        }
        time += dt;
        for (int i = 0; i < bokehCount; i++) {
            bAge[i] += dt;
            bx[i] += bvx[i] * dt;
            by[i] += bvy[i] * dt;
            if (bAge[i] >= bLife[i] || bx[i] < -bSize[i]) {
                spawnBokeh(i, false);
            }
        }
        for (int i = 0; i < starCount; i++) {
            float progress = 1f - sx[i] / width;      // 0 na direita, 1 na esquerda
            sx[i] -= sSpeed[i] * (0.6f + 1.2f * progress) * dt;
            if (sx[i] < -sSize[i] * 4f) {
                spawnStar(i, false);
            }
        }
        for (int k = 0; k < 2; k++) {
            waveOffset[k] = (waveOffset[k] + WAVE_SPEED[k] * width * dt) % waveBitmap[k].getWidth();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (width == 0) {
            return;
        }
        float w = width;
        float h = height;
        canvas.drawRect(0, 0, w, h, background);

        // Raios de luz do canto superior esquerdo, balançando devagar.
        drawRay(canvas, -58f + 3f * (float) Math.sin(time * 0.48f), w * 0.17f, w * 1.25f,
                0xFFBFD0FF, 0.13f + 0.02f * (float) Math.sin(time * 0.31f));
        drawRay(canvas, -40f + 2.5f * (float) Math.sin(time * 0.37f + 1f), w * 0.09f, w * 1.0f,
                0xFFE6D8FF, 0.10f + 0.02f * (float) Math.sin(time * 0.43f + 2f));

        // Estrelas: crescem e aceleram enquanto atravessam (sensação de sobrevoo).
        sprite.setColor(0xFFFFFFFF);
        for (int i = 0; i < starCount; i++) {
            float progress = Math.max(0f, 1f - sx[i] / w);
            float size = sSize[i] * (1f + 1.6f * progress);
            float fadeIn = Math.min(1f, (w - sx[i]) / (w * 0.08f));
            float fadeOut = Math.min(1f, Math.max(0f, sx[i] / (w * 0.1f)));
            sprite.setAlpha(Math.round(255 * sPeak[i] * Math.max(0f, fadeIn) * fadeOut));
            dst.set(sx[i] - size, sy[i] - size, sx[i] + size, sy[i] + size);
            canvas.drawBitmap(sharpDot, null, dst, sprite);
        }

        // Fitas de onda: cada uma é um período repetido lado a lado, em x inteiro.
        for (int k = 0; k < 2; k++) {
            Bitmap wave = waveBitmap[k];
            int period = wave.getWidth();
            float top = waveTop[k] + h * 0.006f * (float) Math.sin(time * 0.35f + k * 2f);
            wavePaint.setColor(WAVE_COLOR[k]);
            for (int x = -Math.round(waveOffset[k]); x < w; x += period) {
                canvas.drawBitmap(wave, x, top, wavePaint);
            }
        }

        // Bokeh: aparece, flutua para a esquerda e some.
        for (int i = 0; i < bokehCount; i++) {
            float t = bAge[i] / bLife[i];
            float envelope = (float) Math.sin(Math.PI * Math.min(1f, Math.max(0f, t)));
            float twinkle = 0.85f + 0.15f * (float) Math.sin(time * 1.7f + bPhase[i]);
            sprite.setColor(bColor[i]);
            sprite.setAlpha(Math.round(255 * bPeak[i] * envelope * twinkle));
            float r = bSize[i] / 2f;
            float y = by[i] + r * 0.3f * (float) Math.sin(time * 0.8f + bPhase[i]);
            dst.set(bx[i] - r, y - r, bx[i] + r, y + r);
            canvas.drawBitmap(bSoft[i] ? softDot : sharpDot, null, dst, sprite);
        }
    }

    /** Raio: o bitmap aponta para baixo; gira em volta do canto superior esquerdo (ângulo negativo = para a direita). */
    private void drawRay(Canvas canvas, float degrees, float rayWidth, float length, int color, float alpha) {
        rayMatrix.setScale(rayWidth / ray.getWidth(), length / ray.getHeight());
        rayMatrix.postTranslate(-rayWidth / 2f, 0f);
        rayMatrix.postRotate(degrees);
        rayMatrix.postTranslate(-width * 0.02f, -height * 0.06f);
        rayPaint.setColor(color);
        rayPaint.setAlpha(Math.round(255 * alpha));
        canvas.drawBitmap(ray, rayMatrix, rayPaint);
    }

    // ---- Bitmaps feitos uma vez -------------------------------------------------------------------------------

    /** Ponto de luz: desfocado (bokeh grande) ou disco de borda macia (bokeh pequeno e estrelas). */
    private static Bitmap makeDot(boolean soft) {
        int size = 64;
        byte[] pixels = new byte[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f) / size * 2f - 1f;
                float dy = (y + 0.5f) / size * 2f - 1f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a;
                if (soft) {
                    // Disco desfocado com a borda um pouco mais clara, como bokeh de lente.
                    float disc = clamp((1f - d) / 0.45f);
                    a = disc * (0.75f + 0.25f * clamp((d - 0.5f) / 0.35f));
                } else {
                    a = (float) Math.pow(clamp((1f - d) / 0.6f), 1.6f);
                }
                pixels[y * size + x] = (byte) Math.round(255 * a);
            }
        }
        return alphaBitmap(pixels, size, size);
    }

    /** Raio de luz: perfil gaussiano na largura, nasce rápido e some devagar no comprimento. */
    private static Bitmap makeRay() {
        int w = 64;
        int h = 256;
        byte[] pixels = new byte[w * h];
        for (int y = 0; y < h; y++) {
            float along = (y + 0.5f) / h;
            float fade = clamp(along / 0.06f) * (float) Math.pow(1f - along, 1.7f);
            for (int x = 0; x < w; x++) {
                float across = (x + 0.5f) / w * 2f - 1f;
                float a = (float) Math.exp(-across * across * 4.5f) * fade;
                pixels[y * w + x] = (byte) Math.round(255 * a);
            }
        }
        return alphaBitmap(pixels, w, h);
    }

    /**
     * Um período de uma fita de onda: preenchimento translúcido entre duas senoides, borda fina clara em cima e
     * outra mais fraca embaixo. Como as duas senoides repetem a cada "period" px, o bitmap emenda consigo mesmo.
     */
    private static Bitmap makeWave(int period, float amplitude, float thickness, float amplitude2, float density) {
        float margin = 4f * density;
        int bitmapHeight = Math.round(margin * 2 + amplitude * 2 + thickness + amplitude2 * 2);
        Bitmap bitmap = Bitmap.createBitmap(period, bitmapHeight, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(bitmap);

        Path fill = new Path();
        Path topEdge = new Path();
        Path bottomEdge = new Path();
        float stepPx = 6f;
        float base = margin + amplitude;
        // Passa um pouco das bordas para o traço não ter ponta visível na emenda.
        for (float x = -stepPx * 2; x <= period + stepPx * 2; x += stepPx) {
            double phase = 2 * Math.PI * x / period;
            float top = base + amplitude * (float) Math.sin(phase);
            if (x == -stepPx * 2) {
                fill.moveTo(x, top);
                topEdge.moveTo(x, top);
            } else {
                fill.lineTo(x, top);
                topEdge.lineTo(x, top);
            }
        }
        for (float x = period + stepPx * 2; x >= -stepPx * 2; x -= stepPx) {
            double phase = 2 * Math.PI * x / period;
            float bottom = base + amplitude + thickness + amplitude2 * (float) Math.sin(2 * phase + 1.3);
            fill.lineTo(x, bottom);
            if (x == period + stepPx * 2) {
                bottomEdge.moveTo(x, bottom);
            } else {
                bottomEdge.lineTo(x, bottom);
            }
        }
        fill.close();

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, margin, 0, bitmapHeight - margin,
                0x2EFFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(fill, paint);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        paint.setColor(0x80FFFFFF);
        canvas.drawPath(topEdge, paint);
        paint.setStrokeWidth(0.8f * density);
        paint.setColor(0x26FFFFFF);
        canvas.drawPath(bottomEdge, paint);
        return bitmap;
    }

    private static Bitmap alphaBitmap(byte[] pixels, int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
        return bitmap;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
