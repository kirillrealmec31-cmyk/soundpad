package com.soundpad.overlay;

import java.io.IOException;
import java.io.InputStream;

/** Запуск команд от root: пробует несколько путей к su и объясняет, почему не вышло. */
public class Su {
    static final String[] PATHS = {"su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su"};

    static String err(IOException e) {
        String m = String.valueOf(e.getMessage());
        int i = m.indexOf("error=");
        if (i < 0) return m;
        String t = m.substring(i);
        int c = t.indexOf(',');
        return c > 0 ? t.substring(0, c) : t;
    }

    static Process run(String script) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String p : PATHS) {
            try {
                return new ProcessBuilder(p, "-c", script).redirectErrorStream(true).start();
            } catch (IOException e) {
                sb.append(p).append(": ").append(err(e)).append("; ");
            }
        }
        throw new IOException(sb.toString().trim());
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
        } catch (IOException e) {
            return e.getMessage();
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
