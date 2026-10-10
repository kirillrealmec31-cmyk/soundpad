package com.soundpad.overlay;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Подаёт звук в микрофон: PCM идёт в петлю AFE-PROXY (hw:0,5), которая подключена к записи. */
public class MicEngine {
    static final int RATE = 48000, CHUNK = 960;
    static final String PRE = "export LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib; B=/data/data/com.termux/files/usr/bin; ";
    static final String ROUTE = "$B/amixer -c 0 cset name='MultiMedia1 Mixer AFE_PCM_TX' %1$s >/dev/null 2>&1; "
        + "$B/amixer -c 0 cset name='Voip_Tx Mixer AFE_PCM_TX_Voip' %1$s >/dev/null 2>&1; ";
    static final String PLAY = "exec $B/aplay -q -D hw:0,5 -t raw -f S16_LE -r 48000 -c 2 --buffer-time=100000";

    interface Cb { void done(boolean ok, String msg); }

    static class Voice {
        volatile short[] pcm;
        volatile int pos;
        volatile boolean done;

        float progress() {
            short[] p = pcm;
            return p == null || p.length == 0 ? 0f : Math.min(1f, pos / (float) p.length);
        }

        void stop() { done = true; }
    }

    Context ctx;
    final Handler ui = new Handler(Looper.getMainLooper());
    Process proc;
    OutputStream out;
    volatile boolean running;
    final CopyOnWriteArrayList<Voice> voices = new CopyOnWriteArrayList<>();
    final Map<String, short[]> cache = new ConcurrentHashMap<>();

    void post(Cb cb, boolean ok, String m) { ui.post(() -> cb.done(ok, m)); }

    String lastLine(String s) {
        String[] l = s.trim().split("\n");
        return l[l.length - 1];
    }

    void start(Context c, Cb cb) {
        ctx = c.getApplicationContext();
        if (running) { post(cb, true, ""); return; }
        new Thread(() -> {
            try {
                final Process p = new ProcessBuilder("su", "-c", PRE + String.format(ROUTE, "1") + PLAY)
                    .redirectErrorStream(true).start();
                final StringBuilder log = new StringBuilder();
                Thread rd = new Thread(() -> {
                    try {
                        InputStream in = p.getInputStream();
                        byte[] b = new byte[512];
                        int n;
                        while ((n = in.read(b)) > 0) {
                            synchronized (log) {
                                log.append(new String(b, 0, n));
                                if (log.length() > 400) log.delete(0, log.length() - 400);
                            }
                        }
                    } catch (Exception e) { }
                });
                rd.setDaemon(true);
                rd.start();
                Thread.sleep(900);
                boolean alive;
                try { p.exitValue(); alive = false; } catch (IllegalThreadStateException e) { alive = true; }
                if (!alive) {
                    String m;
                    synchronized (log) { m = log.toString().trim(); }
                    post(cb, false, m.isEmpty() ? "Нет root или не найден aplay (Termux: alsa-utils)" : lastLine(m));
                    return;
                }
                proc = p;
                out = p.getOutputStream();
                running = true;
                Thread w = new Thread(this::loop);
                w.setDaemon(true);
                w.start();
                post(cb, true, "");
            } catch (Exception e) {
                post(cb, false, "Нет root: разреши доступ в KernelSU");
            }
        }).start();
    }

    void loop() {
        byte[] buf = new byte[CHUNK * 4];
        int[] acc = new int[CHUNK * 2];
        long t0 = System.nanoTime(), n = 0;
        try {
            while (running) {
                Arrays.fill(acc, 0);
                for (Voice v : voices) {
                    short[] p = v.pcm;
                    if (v.done) { voices.remove(v); continue; }
                    if (p == null) continue;
                    int pos = v.pos;
                    for (int i = 0; i < acc.length && pos < p.length; i++) acc[i] += p[pos++];
                    v.pos = pos;
                    if (pos >= p.length) { v.done = true; voices.remove(v); }
                }
                for (int i = 0; i < acc.length; i++) {
                    int s = (int) (acc[i] * 0.9f);
                    if (s > 32767) s = 32767;
                    else if (s < -32768) s = -32768;
                    buf[i * 2] = (byte) s;
                    buf[i * 2 + 1] = (byte) (s >> 8);
                }
                out.write(buf);
                out.flush();
                n++;
                long wait = t0 + n * 20000000L - System.nanoTime();
                if (wait > 0) Thread.sleep(wait / 1000000L, (int) (wait % 1000000L));
            }
        } catch (Exception e) { }
        running = false;
    }

    Voice play(final File f) {
        final Voice v = new Voice();
        new Thread(() -> {
            try {
                String k = f.getAbsolutePath();
                short[] pcm = cache.get(k);
                if (pcm == null) {
                    pcm = decode(f);
                    cache.put(k, pcm);
                }
                if (v.done) return;
                v.pcm = pcm;
                voices.add(v);
            } catch (Throwable e) {
                v.done = true;
                ui.post(() -> Toast.makeText(ctx, "Не удалось декодировать " + f.getName(), Toast.LENGTH_SHORT).show());
            }
        }).start();
        return v;
    }

    void preload(final List<File> fs) {
        new Thread(() -> {
            for (File f : fs) {
                try {
                    String k = f.getAbsolutePath();
                    if (!cache.containsKey(k)) cache.put(k, decode(f));
                } catch (Throwable e) { }
            }
        }).start();
    }

    void stop() {
        running = false;
        voices.clear();
        try { if (out != null) out.close(); } catch (Exception e) { }
        if (proc != null) proc.destroy();
        proc = null;
        out = null;
        new Thread(() -> {
            try { new ProcessBuilder("su", "-c", PRE + String.format(ROUTE, "0")).start().waitFor(); } catch (Exception e) { }
        }).start();
    }

    short[] decode(File f) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(f.getAbsolutePath());
        int tr = -1;
        MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat t = ex.getTrackFormat(i);
            String m = t.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) { tr = i; fmt = t; break; }
        }
        if (tr < 0) throw new Exception("no audio");
        ex.selectTrack(tr);
        MediaCodec dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
        dec.configure(fmt, null, null, 0);
        dec.start();
        int sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE), ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        short[] buf = new short[1 << 16];
        int len = 0;
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        boolean inDone = false, outDone = false;
        while (!outDone) {
            if (!inDone) {
                int ii = dec.dequeueInputBuffer(10000);
                if (ii >= 0) {
                    ByteBuffer ib = dec.getInputBuffer(ii);
                    int sz = ex.readSampleData(ib, 0);
                    if (sz < 0) {
                        dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inDone = true;
                    } else {
                        dec.queueInputBuffer(ii, 0, sz, ex.getSampleTime(), 0);
                        ex.advance();
                    }
                }
            }
            int oi = dec.dequeueOutputBuffer(bi, 10000);
            if (oi >= 0) {
                ByteBuffer ob = dec.getOutputBuffer(oi);
                ob.position(bi.offset);
                ob.limit(bi.offset + bi.size);
                int cnt = bi.size / 2;
                if (len + cnt > buf.length) buf = Arrays.copyOf(buf, Math.max(buf.length * 2, len + cnt));
                ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(buf, len, cnt);
                len += cnt;
                dec.releaseOutputBuffer(oi, false);
                if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || len > 90 * sr * ch) outDone = true;
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat of = dec.getOutputFormat();
                sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }
        }
        dec.stop();
        dec.release();
        ex.release();
        int inF = len / ch, outF = (int) ((long) inF * RATE / sr);
        short[] res = new short[outF * 2];
        for (int i = 0; i < outF; i++) {
            double src = (double) i * sr / RATE;
            int i0 = (int) src, i1 = Math.min(i0 + 1, inF - 1);
            float fr = (float) (src - i0);
            for (int c = 0; c < 2; c++) {
                int cc = Math.min(c, ch - 1);
                float a = buf[i0 * ch + cc], b = buf[i1 * ch + cc];
                res[i * 2 + c] = (short) (a + (b - a) * fr);
            }
        }
        return res;
    }
}
