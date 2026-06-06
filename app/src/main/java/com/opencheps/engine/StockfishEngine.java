package com.opencheps.engine;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class StockfishEngine {

    public static class Pv {
        public final String uci;
        public final int cp;
        public Pv(String uci, int cp) { this.uci = uci; this.cp = cp; }
    }

    private static final String TAG = "OC/Engine";
    private static final String ASSET_NAME = "stockfish";

    private Process proc;
    private BufferedWriter in;
    private BufferedReader out;
    private volatile boolean ready;

    public synchronized void start(Context ctx) throws Exception {
        if (ready) return;
        File bin = extractBinary(ctx);
        bin.setExecutable(true, true);

        ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.directory(bin.getParentFile());
        proc = pb.start();

        in = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
        out = new BufferedReader(new InputStreamReader(proc.getInputStream()));

        send("uci");
        waitFor("uciok", 4000);
        send("setoption name Threads value 2");
        send("setoption name Hash value 32");
        send("setoption name Use NNUE value true");
        send("setoption name UCI_LimitStrength value false");
        send("setoption name Ponder value false");
        send("setoption name MultiPV value 3");
        send("isready");
        waitFor("readyok", 4000);
        ready = true;
        Log.i(TAG, "Stockfish ready: " + bin.getAbsolutePath());
    }

    public synchronized List<Pv> topMoves(String fen, int movetimeMs, int multiPv) throws Exception {
        if (!ready) throw new IllegalStateException("engine not started");
        send("setoption name MultiPV value " + multiPv);
        send("ucinewgame");
        send("position fen " + fen);
        send("go movetime " + movetimeMs);

        String[] bestByIdx = new String[multiPv + 1];
        int[] cpByIdx = new int[multiPv + 1];
        for (int i = 0; i < cpByIdx.length; i++) cpByIdx[i] = Integer.MIN_VALUE;

        String line;
        while ((line = out.readLine()) != null) {
            if (line.startsWith("bestmove")) break;
            if (!line.startsWith("info ") || !line.contains(" pv ")) continue;
            int idx = 1;
            int cp = 0;
            String[] tok = line.split("\\s+");
            String pvMove = null;
            for (int i = 0; i < tok.length; i++) {
                switch (tok[i]) {
                    case "multipv": idx = Integer.parseInt(tok[i + 1]); break;
                    case "cp":      cp  = Integer.parseInt(tok[i + 1]); break;
                    case "mate":    cp  = Integer.parseInt(tok[i + 1]) > 0 ? 100000 : -100000; break;
                    case "pv":      if (i + 1 < tok.length) pvMove = tok[i + 1]; break;
                    default: break;
                }
            }
            if (pvMove != null && idx >= 1 && idx <= multiPv) {
                bestByIdx[idx] = pvMove;
                cpByIdx[idx] = cp;
            }
        }
        List<Pv> result = new ArrayList<>();
        for (int i = 1; i <= multiPv; i++) {
            if (bestByIdx[i] != null) result.add(new Pv(bestByIdx[i], cpByIdx[i]));
        }
        return result;
    }

    public synchronized void stop() {
        ready = false;
        try { if (in != null) { send("quit"); in.close(); } } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        if (proc != null) proc.destroy();
        proc = null;
    }

    private void send(String cmd) throws Exception {
        in.write(cmd);
        in.write("\n");
        in.flush();
    }

    private void waitFor(String token, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String line;
        while (System.currentTimeMillis() < deadline && (line = out.readLine()) != null) {
            if (line.contains(token)) return;
        }
        throw new RuntimeException("Timed out waiting for: " + token);
    }

    private File extractBinary(Context ctx) throws Exception {
        File dst = new File(ctx.getFilesDir(), "stockfish");
        if (dst.exists() && dst.length() > 0) return dst;
        try (InputStream is = ctx.getAssets().open(ASSET_NAME);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
        }
        return dst;
    }
}
