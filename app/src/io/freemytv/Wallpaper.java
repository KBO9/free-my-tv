package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen's wallpaper (Home Settings > Wallpaper): one of the gallery's, drawn here from a few colors (no
 * image files in the APK), or one of My Photos, kept until changed. While one is chosen it is the background whatever
 * the top of the screen shows, and the look is the dark one (Prefs.theme).
 */
final class Wallpaper {
    private Wallpaper() {}

    /** The gallery: a name (the same in every language, like a coin's) and its colors: the base, then three blobs of light. */
    static final String[] NAMES = {"Aurora", "Ocean", "Mirage", "Jungle", "Magma", "Cosmos", "Sahara", "Iris"};
    private static final int[][] COLORS = {
            {0xFF0B1E3A, 0xFF1FD6A6, 0xFF3A7BFF, 0xFF9B5CFF},
            {0xFF04263D, 0xFF0FA3B1, 0xFF1E6BD6, 0xFF7FE0E8},
            {0xFF3A1238, 0xFFFF7A3D, 0xFFE8436E, 0xFFFFC46B},
            {0xFF0C2A1C, 0xFF2E9E5B, 0xFF0F6B5C, 0xFFB7D96A},
            {0xFF2A0D0B, 0xFFE0492B, 0xFF8E1F3B, 0xFFFF9A4A},
            {0xFF05070F, 0xFF27336E, 0xFF10183A, 0xFF5B4BAF},
            {0xFF3B2C20, 0xFFD9A56B, 0xFFA8643F, 0xFFF1D9A8},
            {0xFF1F1740, 0xFF8E7BFF, 0xFFD071E0, 0xFF5AA8FF},
    };

    /** Every choice, in the order the setting steps through them: none, the gallery, then My Photos. */
    static List<String> choices(Context c) {
        List<String> out = new ArrayList<>();
        out.add("");
        for (int i = 0; i < NAMES.length; i++) out.add("g:" + i);
        for (File f : Photos.files(c)) out.add("p:" + f.getName());
        return out;
    }

    /** What the setting shows for a choice (already translated). */
    static String name(Context c, String choice) {
        int at = choices(c).indexOf(choice);
        if (at <= 0) return Ui.tr("Off");
        return at <= NAMES.length ? NAMES[at - 1] : Ui.tr("Photo") + " " + (at - NAMES.length);
    }

    /** The gallery's i-th wallpaper: soft blobs of light on a dark base. */
    private static Bitmap gallery(int i, int w, int h) {
        int[] col = COLORS[i];
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(col[0]);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        float[][] at = {{0.78f, 0.22f, 0.62f}, {0.30f, 0.88f, 0.58f}, {0.98f, 0.92f, 0.42f}};   // x, y, radius (of the width)
        for (int n = 0; n < at.length; n++) {
            float x = at[n][0] * w, y = at[n][1] * h, r = at[n][2] * w;
            p.setShader(new RadialGradient(x, y, r, new int[] {col[n + 1], col[n + 1] & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }
        return b;
    }

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String built, building;
    private static Backdrop backdrop;

    /**
     * The chosen wallpaper ready to draw, or null: none chosen, its photo gone (the choice is then forgotten), or
     * still being built (`ready` runs when it is).
     */
    static synchronized Backdrop backdrop(Context c, Runnable ready) {
        String want = Prefs.wallpaper();
        if (want.isEmpty()) {
            backdrop = null;
            built = null;
            return null;
        }
        if (want.equals(built)) return backdrop;
        if (!want.equals(building)) {
            if (!choices(c).contains(want)) {   // a photo removed since: back to no wallpaper
                Prefs.setWallpaper("");
                return null;
            }
            building = want;
            File dir = Photos.dir(c);
            worker.execute(() -> {
                Backdrop b = null;
                try {
                    Bitmap src = want.startsWith("g:") ? gallery(Integer.parseInt(want.substring(2)), Backdrop.W, Backdrop.H)
                            : BitmapFactory.decodeFile(new File(dir, want.substring(2)).getPath());
                    if (src != null) b = Backdrop.make(src, true, true, true);
                } catch (RuntimeException | OutOfMemoryError e) {
                    AppLog.w("wallpaper: " + want + ": " + e.getMessage());
                }
                synchronized (Wallpaper.class) {
                    building = null;
                    backdrop = b;   // null when it failed: the usual background, and no retry until the choice changes
                    built = want;
                }
                main.post(ready);
            });
        }
        return null;   // the previous one belongs to another choice: the usual background until this one is ready
    }
}
