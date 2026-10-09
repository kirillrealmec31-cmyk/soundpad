package com.soundpad.overlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
    TextView s1, s2;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    TextView txt(String s, int sp, int c) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(c);
        return t;
    }

    Button btn(String t, int c1, int c2, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(dp(16));
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
        r.setPadding(dp(24), dp(56), dp(24), dp(24));
        r.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF02030A, 0xFF0A1E5C}));
        TextView t = txt("SOUNDPAD", 34, 0xFFFFFFFF);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.12f);
        r.addView(t);
        r.addView(txt("оверлей-пэд для звуков", 14, 0xFF26D9FF));
        s1 = txt("", 15, 0xFFD2DEFB);
        s2 = txt("", 15, 0xFFD2DEFB);
        s1.setPadding(0, dp(28), 0, dp(4));
        r.addView(s1);
        r.addView(s2);
        final String pkg = getPackageName();
        r.addView(btn("1. Разрешить поверх окон", 0xFF2F7BFF, 0xFF26D9FF, v ->
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkg)))));
        r.addView(btn("2. Доступ ко всем файлам", 0xFF5B2BFF, 0xFF2F7BFF, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + pkg)));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        }));
        r.addView(btn("Выдать оба права через root", 0xFF0B1F5C, 0xFF2F7BFF, v -> grantRoot(pkg)));
        r.addView(btn("Запустить оверлей", 0xFF00B8FF, 0xFF26FFD0, v -> {
            if (!overlayOk() || !filesOk()) {
                toast("Сначала выдай оба права");
                return;
            }
            startService(new Intent(this, OverlayService.class));
            moveTaskToBack(true);
        }));
        r.addView(btn("Остановить", 0xFF3A1030, 0xFFFF4D7E, v -> stopService(new Intent(this, OverlayService.class))));
        TextView h = txt("Звуки кладём в /sdcard/Soundpad (mp3, ogg, wav, m4a, flac)", 12, 0xFF7F93C4);
        h.setPadding(0, dp(18), 0, 0);
        r.addView(h);
        setContentView(r);
    }

    void grantRoot(final String pkg) {
        new Thread(() -> {
            String msg;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow; appops set " + pkg + " MANAGE_EXTERNAL_STORAGE allow"});
                msg = p.waitFor() == 0 ? "Права выданы" : "Root отказал";
            } catch (Exception e) {
                msg = "Root недоступен";
            }
            final String m = msg;
            runOnUiThread(() -> {
                toast(m);
                refresh();
            });
        }).start();
    }

    void refresh() {
        s1.setText((overlayOk() ? "OK    " : "нет   ") + "поверх других окон");
        s2.setText((filesOk() ? "OK    " : "нет   ") + "доступ ко всем файлам");
        s1.setTextColor(overlayOk() ? 0xFF26FFD0 : 0xFFFF4D7E);
        s2.setTextColor(filesOk() ? 0xFF26FFD0 : 0xFFFF4D7E);
    }

    protected void onResume() {
        super.onResume();
        refresh();
    }
}
