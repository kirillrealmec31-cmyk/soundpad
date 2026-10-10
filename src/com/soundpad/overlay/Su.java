package com.soundpad.overlay;

import java.io.IOException;
import java.io.InputStream;

/** Запуск команд от root: пробует несколько путей к su и умеет объяснить, почему не вышло. */
public class Su {
    static final String[] PATHS = {"su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su"};

    static Process run(String script) throws IOException {
        IOException last = null;
        for (String p : PATHS) {
            try {
                return new ProcessBuilder(p, "-c", script).redirectErrorStream(true).start();
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    /** "OK" если root работает, иначе причина. */
    static String check() {
        try {
            Process p = run("id");
            StringBuilder sb = new StringBuilder();
            InputStream in = p.getInputStream();
            byte[] b = new byte[256];
            int n;
            while ((n = in.read(b)) > 0) sb.append(new String(b, 0, n));
            int rc = p.waitFor();
            String o = sb.toString().trim();
            if (rc == 0 && o.contains("uid=0")) return "OK";
            return "код " + rc + ": " + (o.isEmpty() ? "пустой ответ" : o);
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
