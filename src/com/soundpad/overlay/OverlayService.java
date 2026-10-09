package com.soundpad.overlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OverlayService extends Service {
    WindowManager wm;
    View bubble;
    LinearLayout panel, grid;
    ScrollView sv;
    WindowManager.LayoutParams bp, pp;
    boolean open = false, moved = false;
    float d, tx, ty;
    int sw, sh, sx, sy;
    final List<MediaPlayer> players = new ArrayList<>();
    final int[][] pal = {{0xFF2F7BFF, 0xFF26D9FF}, {0xFF5B2BFF, 0xFF2F7BFF}, {0xFF0092E6, 0xFF26FFD0}, {0xFF3A3DFF, 0xFF8A5BFF}};

    public IBinder onBind(Intent i) { return null; }

    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        d = getResources().getDisplayMetrics().density;
        metrics();
        buildBubble();
        buildPanel();
        wm.addView(bubble, bp);
    }

    public void onDestroy() {
        stopAll();
        try { wm.removeView(open ? panel : bubble); } catch (Exception e) { }
        super.onDestroy();
    }

    void metrics() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        sw = m.widthPixels;
        sh = m.heightPixels;
    }

    WindowManager.LayoutParams lp(int w, int h) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    TextView txt(String s, int sp, int c) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(c);
        return t;
    }

    TextView hb(String s, View.OnClickListener l) {
        TextView t = txt(s, 18, 0xFF26D9FF);
        t.setPadding((int) (10 * d), (int) (4 * d), (int) (10 * d), (int) (4 * d));
        t.setOnClickListener(l);
        return t;
    }

    class Bubble extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float t = 0;

        Bubble(Context c) { super(c); }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = w / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, w, h, 0xFF0B1F5C, 0xFF02030A, Shader.TileMode.CLAMP));
            c.drawCircle(r, r, r - 2 * d, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(0xFF26D9FF);
            c.drawCircle(r, r, r - 2 * d, p);
            p.setStyle(Paint.Style.FILL);
            boolean on = !players.isEmpty();
            t += 0.18f;
            float[] base = {0.34f, 0.60f, 0.45f};
            for (int i = 0; i < 3; i++) {
                float k = on ? (0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(t * (1.5f + i * 0.7f) + i * 2))) : 1f;
                float bh = h * base[i] * k;
                float x = w * (0.32f + i * 0.18f);
                p.setColor(i == 1 ? 0xFF26D9FF : 0xFF2F7BFF);
                c.drawRoundRect(new RectF(x - 3 * d, h / 2f - bh / 2f, x + 3 * d, h / 2f + bh / 2f), 3 * d, 3 * d, p);
            }
            if (on) postInvalidateOnAnimation();
        }
    }

    void buildBubble() {
        bubble = new Bubble(this);
        bp = lp((int) (58 * d), (int) (58 * d));
        bp.x = (int) (sw - 70 * d);
        bp.y = (int) (sh * 0.35f);
        bubble.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    tx = e.getRawX();
                    ty = e.getRawY();
                    sx = bp.x;
                    sy = bp.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float mx = e.getRawX() - tx, my = e.getRawY() - ty;
                    if (Math.abs(mx) > 8 * d || Math.abs(my) > 8 * d) moved = true;
                    if (moved) {
                        bp.x = (int) (sx + mx);
                        bp.y = (int) (sy + my);
                        wm.updateViewLayout(bubble, bp);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (!moved) openPanel();
                    return true;
            }
            return false;
        });
    }

    void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pd = (int) (12 * d);
        panel.setPadding(pd, pd, pd, pd);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF20B1840, 0xF2040712});
        bg.setCornerRadius(24 * d);
        bg.setStroke((int) (1.2f * d), 0xAA2F7BFF);
        panel.setBackground(bg);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = txt("SOUNDPAD", 15, 0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.18f);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(hb("\u21BB", v -> fill()));
        head.addView(hb("\u25A0", v -> stopAll()));
        head.addView(hb("\u2014", v -> closePanel()));
        head.addView(hb("\u00D7", v -> stopSelf()));
        panel.addView(head, new LinearLayout.LayoutParams(-1, -2));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(grid);
        panel.addView(sv, new LinearLayout.LayoutParams(-1, (int) (sh * 0.42f)));
        pp = lp((int) (Math.min(sw * 0.92f, 360 * d)), WindowManager.LayoutParams.WRAP_CONTENT);
    }

    void openPanel() {
        if (open) return;
        metrics();
        fill();
        int pw = (int) Math.min(sw * 0.92f, 360 * d);
        pp.width = pw;
        pp.x = Math.max(0, Math.min(bp.x, sw - pw));
        pp.y = Math.max((int) (24 * d), Math.min(bp.y, (int) (sh * 0.2f)));
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, (int) (sh * 0.42f)));
        wm.removeView(bubble);
        wm.addView(panel, pp);
        open = true;
    }

    void closePanel() {
        if (!open) return;
        wm.removeView(panel);
        wm.addView(bubble, bp);
        open = false;
    }

    void fill() {
        grid.removeAllViews();
        File dir = new File(Environment.getExternalStorageDirectory(), "Soundpad");
        dir.mkdirs();
        File[] fs = dir.listFiles();
        List<File> l = new ArrayList<>();
        if (fs != null) {
            for (File f : fs) {
                if (f.getName().toLowerCase().matches(".*\\.(mp3|ogg|wav|m4a|aac|flac|opus)$")) l.add(f);
            }
        }
        Collections.sort(l);
        if (l.isEmpty()) {
            TextView t = txt("Положи звуки в /sdcard/Soundpad и нажми \u21BB", 13, 0xFF9FB3E6);
            t.setPadding(0, (int) (14 * d), 0, (int) (6 * d));
            grid.addView(t);
            return;
        }
        int n = Math.min(l.size(), 60);
        LinearLayout row = null;
        for (int i = 0; i < n; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            row.addView(pad(l.get(i), i));
        }
        for (int k = n % 3 == 0 ? 0 : 3 - n % 3; k > 0; k--) {
            View sp = new View(this);
            row.addView(sp, new LinearLayout.LayoutParams(0, (int) (64 * d), 1f));
        }
    }

    View pad(final File f, int i) {
        TextView t = new TextView(this);
        String n = f.getName();
        int k = n.lastIndexOf('.');
        if (k > 0) n = n.substring(0, k);
        t.setText(n);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        int pd = (int) (6 * d);
        t.setPadding(pd, pd, pd, pd);
        int[] c = pal[i % pal.length];
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c[0], c[1]});
        g.setCornerRadius(14 * d);
        g.setStroke((int) d, 0x55FFFFFF);
        t.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, (int) (64 * d), 1f);
        int m = (int) (4 * d);
        p.setMargins(m, m, m, m);
        t.setLayoutParams(p);
        t.setOnTouchListener((v, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(70).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            return false;
        });
        t.setOnClickListener(v -> play(f));
        return t;
    }

    void play(File f) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnPreparedListener(m -> m.start());
            mp.setOnCompletionListener(m -> {
                players.remove(m);
                m.release();
            });
            mp.setOnErrorListener((m, w, x) -> {
                players.remove(m);
                m.release();
                return true;
            });
            players.add(mp);
            mp.prepareAsync();
            if (bubble != null) bubble.postInvalidateOnAnimation();
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось: " + f.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    void stopAll() {
        for (MediaPlayer m : new ArrayList<>(players)) {
            try { m.stop(); } catch (Exception e) { }
            m.release();
        }
        players.clear();
    }
}
