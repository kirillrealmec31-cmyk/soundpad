package com.soundpad.overlay;

import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.IBinder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OverlayService extends Service {
    static final int BG1 = 0xF2171D2D, BG2 = 0xF20D111B, SURF = 0xFF1B2232, LINE = 0xFF2B3449,
        TXT = 0xFFE6EAF2, SUB = 0xFF8791A8, ACC = 0xFF93ABEB, ACCD = 0xFF3D4F85;
    final File root = Environment.getExternalStorageDirectory();
    WindowManager wm;
    Bubble bubble;
    LinearLayout panel, listBox;
    ScrollView sv;
    TextView title, doneBtn;
    Icon backI, stopI, plusI;
    WindowManager.LayoutParams bp, pp;
    boolean open, busy, moved;
    float d, tx, ty;
    int sw, sh, sx, sy;
    File cur = root;
    SharedPreferences prefs;
    final List<Pad> pads = new ArrayList<>();
    final MicEngine mic = new MicEngine();
    boolean micMode, micBusy;
    TextView sub;
    Toggle tog;

    public IBinder onBind(Intent i) { return null; }

    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("sp", MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;
        metrics();
        bubble = new Bubble(this);
        bp = lp(dp(64), dp(64));
        bp.x = sw - dp(76);
        bp.y = (int) (sh * 0.35f);
        bubbleTouch();
        buildPanel();
        wm.addView(bubble, bp);
    }

    public void onDestroy() {
        stopAll();
        mic.stop();
        try { wm.removeView(panel); } catch (Exception e) { }
        try { wm.removeView(bubble); } catch (Exception e) { }
        super.onDestroy();
    }

    int dp(float v) { return (int) (v * d); }

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
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    void squish(View v) {
        v.setOnTouchListener((x, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                x.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2.4f)).start();
            return false;
        });
    }

    void note(Canvas c, float cx, float cy, float s, Paint p, int col) {
        p.setColor(col);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx - s * 0.25f, cy + s * 0.35f, s * 0.22f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(cx - s * 0.03f, cy + s * 0.35f, cx - s * 0.03f, cy - s * 0.45f, p);
        c.drawLine(cx - s * 0.03f, cy - s * 0.45f, cx + s * 0.4f, cy - s * 0.25f, p);
    }

    void bars(Canvas c, float cx, float cy, float s, boolean on, Paint p, int col) {
        float t = System.nanoTime() / 1e9f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2.4f * d);
        p.setColor(col);
        float[] base = {0.55f, 1f, 0.7f};
        for (int i = 0; i < 3; i++) {
            float k = on ? 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (5f + i * 1.7f) + i * 1.9f)) : base[i] * 0.8f;
            float hh = s * k;
            float x = cx + (i - 1) * 5.5f * d;
            c.drawLine(x, cy - hh / 2, x, cy + hh / 2, p);
        }
    }

    // ---------- векторные иконки: 0 плюс, 1 стоп, 2 назад, 3 папка, 4 нота, 5 галочка ----------
    class Icon extends View {
        final int type;
        int col = SUB;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(Context c, int type) {
            super(c);
            this.type = type;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void tint(int c) { col = c; invalidate(); }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(col);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f * d);
            Path q = new Path();
            switch (type) {
                case 0:
                    c.drawLine(w * 0.28f, h * 0.5f, w * 0.72f, h * 0.5f, p);
                    c.drawLine(w * 0.5f, h * 0.28f, w * 0.5f, h * 0.72f, p);
                    break;
                case 1:
                    p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(new RectF(w * 0.3f, h * 0.3f, w * 0.7f, h * 0.7f), 3 * d, 3 * d, p);
                    break;
                case 2:
                    q.moveTo(w * 0.58f, h * 0.26f);
                    q.lineTo(w * 0.38f, h * 0.5f);
                    q.lineTo(w * 0.58f, h * 0.74f);
                    c.drawPath(q, p);
                    break;
                case 3:
                    q.moveTo(w * 0.2f, h * 0.3f);
                    q.lineTo(w * 0.42f, h * 0.3f);
                    q.lineTo(w * 0.5f, h * 0.38f);
                    q.lineTo(w * 0.8f, h * 0.38f);
                    q.lineTo(w * 0.8f, h * 0.72f);
                    q.lineTo(w * 0.2f, h * 0.72f);
                    q.close();
                    c.drawPath(q, p);
                    break;
                case 4:
                    note(c, w / 2f, h / 2f, Math.min(w, h) * 0.5f, p, col);
                    break;
                default:
                    q.moveTo(w * 0.26f, h * 0.52f);
                    q.lineTo(w * 0.43f, h * 0.68f);
                    q.lineTo(w * 0.75f, h * 0.32f);
                    c.drawPath(q, p);
            }
        }
    }

    // ---------- пузырь ----------
    class Bubble extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bubble(Context c) { super(c); }

        boolean playing() {
            for (Pad x : pads) if (x.on()) return true;
            return false;
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), r = w / 2f, t = System.nanoTime() / 1e9f;
            boolean on = playing();
            float br = 0.5f + 0.5f * (float) Math.sin(t * 1.6f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? 0x3393ABEB : 0x1A93ABEB);
            c.drawCircle(r, r, r - 5 * d + 3 * d * br, p);
            p.setColor(0xF2171D2D);
            c.drawCircle(r, r, r - 8 * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f * d);
            p.setColor(on ? ACC : LINE);
            c.drawCircle(r, r, r - 8 * d, p);
            bars(c, r, r, 18 * d, on, p, ACC);
            if (micMode) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(ACC);
                c.drawCircle(w - 14 * d, 14 * d, 3.5f * d + d * br, p);
            }
            postInvalidateOnAnimation();
        }
    }

    void bubbleTouch() {
        bubble.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    tx = e.getRawX();
                    ty = e.getRawY();
                    sx = bp.x;
                    sy = bp.y;
                    moved = false;
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start();
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
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                    if (e.getAction() == MotionEvent.ACTION_UP && !moved) openPanel();
                    return true;
            }
            return false;
        });
    }

    class Toggle extends View {
        boolean on;
        float k;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Toggle(Context c) {
            super(c);
            squish(this);
        }

        void set(boolean b) {
            on = b;
            ValueAnimator a = ValueAnimator.ofFloat(k, b ? 1f : 0f);
            a.setDuration(200);
            a.addUpdateListener(x -> {
                k = (float) x.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            RectF rf = new RectF(d, d, w - d, h - d);
            p.setStyle(Paint.Style.FILL);
            p.setColor(k > 0.5f ? 0xFF2A3A66 : SURF);
            c.drawRoundRect(rf, h / 2f, h / 2f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d);
            p.setColor(k > 0.5f ? ACC : LINE);
            c.drawRoundRect(rf, h / 2f, h / 2f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(k > 0.5f ? ACC : SUB);
            c.drawCircle(h / 2f + k * (w - h), h / 2f, h / 2f - 5 * d, p);
        }
    }

    void toggleMic() {
        if (micBusy) return;
        stopAll();
        if (micMode) {
            micMode = false;
            mic.stop();
            tog.set(false);
            sub.setText("Выход: динамик");
            return;
        }
        micBusy = true;
        tog.set(true);
        sub.setText("Подключаю микрофон…");
        mic.start(this, (ok, msg) -> {
            micBusy = false;
            if (ok) {
                micMode = true;
                sub.setText("Выход: микрофон");
                List<File> fl = new ArrayList<>();
                for (Pad p : pads) fl.add(p.f);
                mic.preload(fl);
            } else {
                tog.set(false);
                sub.setText("Выход: динамик");
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- пад ----------
    class Pad extends View {
        final File f;
        final String name;
        MediaPlayer mp;
        MicEngine.Voice voice;
        boolean ready;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        Pad(Context c, File f) {
            super(c);
            this.f = f;
            String n = f.getName();
            int k = n.lastIndexOf('.');
            name = k > 0 ? n.substring(0, k) : n;
            tp.setTextSize(12.5f * d);
            tp.setColor(TXT);
            tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            squish(this);
            setOnClickListener(x -> { if (on()) stop(); else start(); });
        }

        boolean on() {
            if (voice != null && voice.done) voice = null;
            return micMode ? voice != null : mp != null;
        }

        void start() {
            if (micMode) {
                voice = mic.play(f);
                invalidate();
                return;
            }
            try {
                MediaPlayer m = new MediaPlayer();
                m.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                m.setDataSource(f.getAbsolutePath());
                m.setOnPreparedListener(x -> { ready = true; x.start(); invalidate(); });
                m.setOnCompletionListener(x -> stop());
                m.setOnErrorListener((x, w, e) -> { stop(); return true; });
                mp = m;
                m.prepareAsync();
                invalidate();
            } catch (Exception e) {
                stop();
                Toast.makeText(OverlayService.this, "Не открывается: " + f.getName(), Toast.LENGTH_SHORT).show();
            }
        }

        void stop() {
            if (voice != null) {
                voice.stop();
                voice = null;
            }
            MediaPlayer m = mp;
            mp = null;
            ready = false;
            if (m != null) {
                try { m.stop(); } catch (Exception e) { }
                try { m.release(); } catch (Exception e) { }
            }
            invalidate();
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = 16 * d;
            RectF rf = new RectF(d, d, w - d, h - d);
            boolean on = on();
            p.setStyle(Paint.Style.FILL);
            p.setColor(SURF);
            c.drawRoundRect(rf, r, r, p);
            if (on && (ready || voice != null)) {
                float prog = 0;
                try {
                    if (voice != null) prog = voice.progress();
                    else {
                        int du = mp.getDuration();
                        if (du > 0) prog = mp.getCurrentPosition() / (float) du;
                    }
                } catch (Exception e) { }
                c.save();
                Path clip = new Path();
                clip.addRoundRect(rf, r, r, Path.Direction.CW);
                c.clipPath(clip);
                p.setColor(0x3893ABEB);
                c.drawRect(0, 0, w * prog, h, p);
                p.setColor(ACC);
                c.drawRect(Math.max(0, w * prog - d), 0, w * prog, h, p);
                c.restore();
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d);
            p.setColor(on ? ACC : LINE);
            c.drawRoundRect(rf, r, r, p);
            if (on) bars(c, 20 * d, 20 * d, 13 * d, true, p, ACC);
            else note(c, 20 * d, 20 * d, 13 * d, p, SUB);
            String s = TextUtils.ellipsize(name, tp, w - 24 * d, TextUtils.TruncateAt.END).toString();
            c.drawText(s, 12 * d, h - 12 * d, tp);
            if (on) postInvalidateOnAnimation();
        }
    }

    // ---------- панель ----------
    void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(6), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG1, BG2});
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), LINE);
        panel.setBackground(bg);

        FrameLayout hw = new FrameLayout(this);
        View handle = new View(this);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(LINE);
        hg.setCornerRadius(dp(2));
        handle.setBackground(hg);
        hw.addView(handle, new FrameLayout.LayoutParams(dp(38), dp(4), Gravity.CENTER));
        hw.setOnClickListener(v -> closePanel());
        panel.addView(hw, new LinearLayout.LayoutParams(-1, dp(22)));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        backI = new Icon(this, 2);
        backI.setVisibility(View.GONE);
        backI.tint(TXT);
        backI.setOnClickListener(v -> browseUp());
        squish(backI);
        head.addView(backI, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout tb = new LinearLayout(this);
        tb.setOrientation(LinearLayout.VERTICAL);
        title = txt("Soundpad", 17, TXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        tb.addView(title);
        sub = txt("Выход: динамик", 11, SUB);
        tb.addView(sub);
        head.addView(tb, new LinearLayout.LayoutParams(0, -2, 1f));
        tog = new Toggle(this);
        tog.setOnClickListener(v -> toggleMic());
        LinearLayout.LayoutParams tgl = new LinearLayout.LayoutParams(dp(48), dp(28));
        tgl.rightMargin = dp(6);
        head.addView(tog, tgl);
        stopI = new Icon(this, 1);
        stopI.setOnClickListener(v -> {
            stopAll();
            stopI.tint(ACC);
            stopI.postDelayed(() -> stopI.tint(SUB), 240);
        });
        squish(stopI);
        plusI = new Icon(this, 0);
        plusI.tint(ACC);
        plusI.setOnClickListener(v -> { stopAll(); cur = root; swap(() -> showBrowser()); });
        squish(plusI);
        doneBtn = txt("Готово", 13, ACC);
        doneBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable dg = new GradientDrawable();
        dg.setColor(0x333D4F85);
        dg.setStroke(dp(1), ACCD);
        dg.setCornerRadius(dp(16));
        doneBtn.setBackground(dg);
        doneBtn.setVisibility(View.GONE);
        doneBtn.setOnClickListener(v -> swap(() -> showGrid()));
        squish(doneBtn);
        head.addView(stopI, new LinearLayout.LayoutParams(dp(40), dp(40)));
        head.addView(plusI, new LinearLayout.LayoutParams(dp(40), dp(40)));
        head.addView(doneBtn, new LinearLayout.LayoutParams(-2, -2));
        panel.addView(head, new LinearLayout.LayoutParams(-1, dp(44)));

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(listBox);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, (int) (sh * 0.46f));
        sp.topMargin = dp(6);
        panel.addView(sv, sp);
        pp = lp(dp(340), WindowManager.LayoutParams.WRAP_CONTENT);
    }

    void swap(Runnable r) {
        sv.animate().alpha(0f).setDuration(90).withEndAction(() -> {
            r.run();
            sv.scrollTo(0, 0);
            sv.animate().alpha(1f).setDuration(160).start();
        }).start();
    }

    void openPanel() {
        if (open || busy) return;
        metrics();
        int pw = (int) Math.min(sw * 0.92f, dp(360));
        pp.width = pw;
        pp.x = Math.max(0, Math.min(bp.x, sw - pw));
        pp.y = Math.max(dp(24), Math.min(bp.y, (int) (sh * 0.18f)));
        sv.setLayoutParams(sizeLp());
        showGrid();
        panel.setAlpha(0f);
        panel.setScaleX(0.9f);
        panel.setScaleY(0.9f);
        panel.setTranslationY(dp(14));
        panel.setPivotX(pw / 2f);
        panel.setPivotY(0f);
        wm.removeView(bubble);
        wm.addView(panel, pp);
        open = true;
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(260)
            .setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    LinearLayout.LayoutParams sizeLp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, (int) (sh * 0.46f));
        l.topMargin = dp(6);
        return l;
    }

    void closePanel() {
        if (!open || busy) return;
        busy = true;
        stopAll();
        panel.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).translationY(dp(10)).setDuration(170).withEndAction(() -> {
            try { wm.removeView(panel); } catch (Exception e) { }
            wm.addView(bubble, bp);
            bubble.setScaleX(0.5f);
            bubble.setScaleY(0.5f);
            bubble.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(new OvershootInterpolator(2.2f)).start();
            open = false;
            busy = false;
        }).start();
    }

    // ---------- сетка падов ----------
    List<String> loadList() {
        List<String> l = new ArrayList<>();
        for (String s : prefs.getString("files", "").split("\n")) if (!s.isEmpty()) l.add(s);
        return l;
    }

    void saveList(List<String> l) { prefs.edit().putString("files", TextUtils.join("\n", l)).apply(); }

    void showGrid() {
        pads.clear();
        listBox.removeAllViews();
        backI.setVisibility(View.GONE);
        doneBtn.setVisibility(View.GONE);
        stopI.setVisibility(View.VISIBLE);
        plusI.setVisibility(View.VISIBLE);
        title.setText("Soundpad");
        sub.setVisibility(View.VISIBLE);
        tog.setVisibility(View.VISIBLE);
        List<String> l = loadList();
        List<File> fl = new ArrayList<>();
        for (String s : l) { File f = new File(s); if (f.isFile()) fl.add(f); }
        if (fl.isEmpty()) {
            LinearLayout e = new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL);
            e.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(20));
            g.setStroke(dp(1), ACCD, dp(6), dp(5));
            e.setBackground(g);
            Icon pl = new Icon(this, 0);
            pl.tint(ACC);
            e.addView(pl, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView t = txt("Добавить звуки", 14, SUB);
            t.setGravity(Gravity.CENTER);
            e.addView(t);
            e.setOnClickListener(v -> { cur = root; swap(() -> showBrowser()); });
            squish(e);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(130));
            lp.topMargin = dp(8);
            listBox.addView(e, lp);
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < fl.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                listBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            Pad pd = new Pad(this, fl.get(i));
            pads.add(pd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(68), 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(pd, lp);
            pd.setAlpha(0f);
            pd.setTranslationY(dp(10));
            pd.animate().alpha(1f).translationY(0f).setStartDelay(i * 35L).setDuration(260)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        for (int k = fl.size() % 3 == 0 ? 0 : 3 - fl.size() % 3; k > 0; k--)
            row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(68), 1f));
    }

    void stopAll() { for (Pad p : pads) p.stop(); }

    // ---------- выбор файлов прямо в оверлее ----------
    void browseUp() {
        if (cur.equals(root) || cur.getParentFile() == null) swap(() -> showGrid());
        else {
            cur = cur.getParentFile();
            swap(() -> showBrowser());
        }
    }

    void showBrowser() {
        backI.setVisibility(View.VISIBLE);
        doneBtn.setVisibility(View.VISIBLE);
        stopI.setVisibility(View.GONE);
        plusI.setVisibility(View.GONE);
        title.setText(cur.equals(root) ? "Память" : cur.getName());
        sub.setVisibility(View.GONE);
        tog.setVisibility(View.GONE);
        listBox.removeAllViews();
        File[] fs = cur.listFiles();
        List<File> items = new ArrayList<>();
        if (fs != null) {
            Arrays.sort(fs, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : fs) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory() || f.getName().toLowerCase().matches(".*\\.(mp3|ogg|wav|m4a|aac|flac|opus)$")) items.add(f);
            }
        }
        if (items.isEmpty()) {
            TextView t = txt("Здесь нет аудио и папок", 13, SUB);
            t.setPadding(dp(8), dp(16), 0, 0);
            listBox.addView(t);
            return;
        }
        for (File f : items) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(6), 0, dp(6), 0);
            Icon ic = new Icon(this, f.isDirectory() ? 3 : 4);
            ic.tint(f.isDirectory() ? ACC : SUB);
            r.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView t = txt(f.getName(), 14, TXT);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.leftMargin = dp(12);
            r.addView(t, tl);
            final Icon ck = new Icon(this, 5);
            ck.tint(ACC);
            ck.setVisibility(!f.isDirectory() && loadList().contains(f.getAbsolutePath()) ? View.VISIBLE : View.INVISIBLE);
            r.addView(ck, new LinearLayout.LayoutParams(dp(24), dp(24)));
            squish(r);
            final File ff = f;
            r.setOnClickListener(v -> {
                if (ff.isDirectory()) {
                    cur = ff;
                    swap(() -> showBrowser());
                } else {
                    List<String> l = loadList();
                    String path = ff.getAbsolutePath();
                    if (l.contains(path)) {
                        l.remove(path);
                        ck.setVisibility(View.INVISIBLE);
                    } else {
                        l.add(path);
                        ck.setVisibility(View.VISIBLE);
                        ck.setScaleX(0f);
                        ck.setScaleY(0f);
                        ck.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
                    }
                    saveList(l);
                }
            });
            listBox.addView(r, new LinearLayout.LayoutParams(-1, dp(50)));
        }
    }
}
