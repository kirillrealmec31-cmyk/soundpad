package com.soundpad.overlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final int LINE = 0xFF2B3449, TXT = 0xFFE6EAF2, SUB = 0xFF8791A8, ACC = 0xFF93ABEB, ACCD = 0xFF3D4F85, SURF = 0xFF1B2232;
    TextView s1, s2, s3;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    TextView txt(String s, int sp, int c) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(c);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    Button btn(String t, int fill, int stroke, int tc, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(tc);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(18));
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54));
        p.topMargin = dp(10);
        b.setLayoutParams(p);
        return b;
    }

    boolean overlayOk() { return Settings.canDrawOverlays(this); }

    boolean filesOk() { return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(); }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(24), dp(64), dp(24), dp(24));
        r.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0C0F16, 0xFF161C2B}));
        TextView t = txt("Soundpad", 32, TXT);
        t.setLetterSpacing(0.04f);
        r.addView(t);
        r.addView(txt("оверлей-пэд для звуков", 14, SUB));
        s1 = txt("", 15, TXT);
        s2 = txt("", 15, TXT);
        s1.setPadding(0, dp(30), 0, dp(4));
        r.addView(s1);
        r.addView(s2);
        s3 = txt("root: проверяю…", 15, SUB);
        r.addView(s3);
        final String pkg = getPackageName();
        r.addView(btn("Разрешить поверх окон", SURF, LINE, TXT, v ->
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkg)))));
        r.addView(btn("Доступ ко всем файлам", SURF, LINE, TXT, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + pkg)));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        }));
        r.addView(btn("Выдать оба права через root", SURF, ACCD, ACC, v -> grantRoot(pkg)));
        r.addView(btn("Запустить оверлей", 0xFF3D4F85, ACC, TXT, v -> {
            if (!overlayOk() || !filesOk()) {
                toast("Сначала выдай оба права");
                return;
            }
            startService(new Intent(this, OverlayService.class));
            moveTaskToBack(true);
        }));
        r.addView(btn("Остановить", SURF, LINE, 0xFFC98A98, v -> stopService(new Intent(this, OverlayService.class))));
        TextView h = txt("Звуки выбираются прямо в оверлее: плюс, затем нужные файлы. Переключатель в панели: динамик или микрофон. Микрофону нужен root у приложения или запущенный мост в Termux (soundpad-root.sh).", 12, SUB);
        h.setPadding(0, dp(20), 0, 0);
        r.addView(h);
        setContentView(r);
    }

    void grantRoot(final String pkg) {
        new Thread(() -> {
            String msg;
            String r = Su.check();
            if (r.equals("OK")) {
                try {
                    Process p = Su.run("appops set " + pkg + " SYSTEM_ALERT_WINDOW allow; appops set " + pkg + " MANAGE_EXTERNAL_STORAGE allow");
                    msg = p.waitFor() == 0 ? "Права выданы" : "Права не выданы";
                } catch (Exception e) {
                    msg = "Ошибка: " + e.getMessage();
                }
            } else {
                msg = "root: " + r;
            }
            final String m = msg;
            runOnUiThread(() -> {
                toast(m);
                refresh();
            });
        }).start();
    }

    void checkRoot() {
        new Thread(() -> {
            final String r = Su.check();
            runOnUiThread(() -> {
                s3.setText(r.equals("OK") ? "OK    root для приложения" : "нет   root: " + r);
                s3.setTextColor(r.equals("OK") ? ACC : 0xFFC98A98);
            });
        }).start();
    }

    void refresh() {
        s1.setText((overlayOk() ? "OK    " : "нет   ") + "поверх других окон");
        s2.setText((filesOk() ? "OK    " : "нет   ") + "доступ ко всем файлам");
        s1.setTextColor(overlayOk() ? ACC : 0xFFC98A98);
        s2.setTextColor(filesOk() ? ACC : 0xFFC98A98);
        checkRoot();
    }

    protected void onResume() {
        super.onResume();
        refresh();
    }
}
