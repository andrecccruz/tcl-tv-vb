package br.tvtcl.inicio;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lista de apps instalados, com o bloco quadrado de cada um já desenhado, e os atalhos para abri-los. */
final class Apps {

    /**
     * Favoritos (nomes de pacote), na ordem em que aparecem no começo da fileira. Os que não estiverem instalados são
     * pulados; vazio, a fileira fica só em ordem alfabética. Exemplo: {"com.netflix.ninja", "com.google.android.youtube.tv"}.
     */
    static final String[] FAVORITES = {
    };

    private static final int TILE_FILL = 0xFF1B2233;

    static final class Entry {
        final ComponentName component;
        final String label;
        final Bitmap tile;

        Entry(ComponentName component, String label, Bitmap tile) {
            this.component = component;
            this.label = label;
            this.tile = tile;
        }
    }

    /** Blocos já desenhados, por componente + versão do app + tamanho (ficam enquanto o processo viver). */
    private static Map<String, Bitmap> cache = new HashMap<>();

    private Apps() {
    }

    /**
     * Apps com ícone na grade da TV, em ordem alfabética. Com favoritesFirst, os favoritos vêm antes, na ordem de
     * {@link #FAVORITES}. Os blocos têm tilePx de lado e cantos arredondados.
     */
    static List<Entry> load(Context context, int tilePx, boolean favoritesFirst) {
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);

        Map<String, Bitmap> newCache = new HashMap<>();
        List<Entry> apps = new ArrayList<>();
        for (ResolveInfo info : found) {
            ActivityInfo activity = info.activityInfo;
            if (activity.packageName.equals(context.getPackageName())) {
                continue;
            }
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            long updated = 0;
            try {
                updated = pm.getPackageInfo(activity.packageName, 0).lastUpdateTime;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            String key = component.flattenToShortString() + '@' + updated + '#' + tilePx;
            Bitmap tile = cache.get(key);
            if (tile == null) {
                tile = renderTile(pm, activity, tilePx);
            }
            newCache.put(key, tile);
            apps.add(new Entry(component, String.valueOf(info.loadLabel(pm)), tile));
        }
        // Apps removidos saem do cache. Sem recycle(): a outra tela pode ainda estar mostrando o bloco antigo.
        cache = newCache;

        Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> collator.compare(a.label, b.label));
        if (favoritesFirst) {
            List<Entry> ordered = new ArrayList<>(apps.size());
            for (String pkg : FAVORITES) {
                for (Entry entry : apps) {
                    if (entry.component.getPackageName().equals(pkg)) {
                        ordered.add(entry);
                        break;
                    }
                }
            }
            for (Entry entry : apps) {
                if (!ordered.contains(entry)) {
                    ordered.add(entry);
                }
            }
            apps = ordered;
        }
        return apps;
    }

    /**
     * Bloco quadrado de cantos arredondados. Ordem de preferência: ícone adaptativo (ocupa o bloco todo), banner da TV
     * centralizado sobre a cor da borda dele, e por último o ícone comum no centro.
     */
    private static Bitmap renderTile(PackageManager pm, ActivityInfo activity, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float radius = size * 0.15f;
        Path clip = new Path();
        clip.addRoundRect(0, 0, size, size, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawColor(TILE_FILL);

        Drawable icon = sharpIcon(pm, activity);
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) icon;
            // As camadas têm 25% de sobra de cada lado em volta da área visível.
            int extra = Math.round(size * AdaptiveIconDrawable.getExtraInsetFraction());
            for (Drawable layer : new Drawable[]{adaptive.getBackground(), adaptive.getForeground()}) {
                if (layer != null) {
                    layer.setBounds(-extra, -extra, size + extra, size + extra);
                    layer.draw(canvas);
                }
            }
            return bitmap;
        }

        Drawable banner = activity.loadBanner(pm);
        if (banner == null) {
            banner = activity.applicationInfo.loadBanner(pm);
        }
        if (banner != null) {
            canvas.drawColor(edgeColor(banner));
            int width = Math.round(size * 0.94f);
            int height = width * 9 / 16;
            int left = (size - width) / 2;
            int top = (size - height) / 2;
            banner.setBounds(left, top, left + width, top + height);
            banner.draw(canvas);
            return bitmap;
        }

        if (icon != null) {
            int inset = Math.round(size * 0.16f);
            icon.setBounds(inset, inset, size - inset, size - inset);
            icon.draw(canvas);
        }
        return bitmap;
    }

    /** O ícone na maior densidade que o app tiver, para não ficar borrado no bloco grande. */
    private static Drawable sharpIcon(PackageManager pm, ActivityInfo activity) {
        int res = activity.getIconResource();
        if (res != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(activity.applicationInfo);
                Drawable drawable = resources.getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
                if (drawable != null) {
                    return drawable;
                }
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        return activity.loadIcon(pm);
    }

    /** Cor do canto do banner, para o espaço em volta dele no bloco quadrado não destoar. */
    private static int edgeColor(Drawable banner) {
        Bitmap probe = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        banner.setBounds(0, 0, 32, 18);
        banner.draw(new Canvas(probe));
        int color = probe.getPixel(1, 1);
        probe.recycle();
        return (color >>> 24) < 0xF0 ? TILE_FILL : color;
    }

    static void open(Context context, ComponentName component) {
        start(context, new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    /** Tela do app nas Configurações, onde fica o "Forçar parada". */
    static void openDetails(Context context, ComponentName component) {
        start(context, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", component.getPackageName(), null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static boolean start(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
