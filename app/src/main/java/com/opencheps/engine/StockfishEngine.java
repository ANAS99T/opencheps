package com.opencheps.engine;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class StockfishEngine {

    public static class Pv {
        public final String uci;
        public final int cp;
        public final boolean mate;
        public Pv(String uci, int cp, boolean mate) {
            this.uci = uci; this.cp = cp; this.mate = mate;
        }
    }

    private static final String TAG = "OC/Engine";

    private Process proc;
    private BufferedWriter in;
    private BufferedReader out;
    private volatile boolean ready;
    private volatile int currentMultiPv = 0;

    public synchronized void start(Context ctx) throws Exception {
        if (ready) return;
        ApplicationInfo ai = ctx.getApplicationInfo();
        File bin = new File(ai.nativeLibraryDir, "libstockfish.so");
        if (!bin.exists()) {
            throw new RuntimeException("Stockfish native lib missing at " + bin);
        }

        ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.directory(ctx.getFilesDir());
        proc = pb.start();

        in = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
        out = new BufferedReader(new InputStreamReader(proc.getInputStream()));

        send("uci");
        waitFor("uciok", 6000);
        send("setoption name Threads value 2");
        send("setoption name Hash value 48");
        send("setoption name Move Overhead value 100");
        send("setoption name UCI_LimitStrength value false");
        send("setoption name Ponder value false");
        setMultiPv(3);
        send("isready");
        waitFor("readyok", 6000);
        ready = true;
        Log.i(TAG, "Stockfish 18 ready: " + bin.getAbsolutePath());
    }

    private void setMultiPv(int n) throws Exception {
        if (n != currentMultiPv) {
            send("setoption name MultiPV value " + n);
            currentMultiPv = n;
        }
    }

    public synchronized List<Pv> topMoves(String fen, int movetimeMs, int multiPv) throws Exception {
        if (!ready) throw new IllegalStateException("engine not started");
        setMultiPv(multiPv);
        send("ucinewgame");
        send("position fen " + fen);
        send("go movetime " + movetimeMs);

        String[] bestByIdx = new String[multiPv + 1];
        int[] cpByIdx = new int[multiPv + 1];
        boolean[] mateByIdx = new boolean[multiPv + 1];

        String line;
        while ((line = out.readLine()) != null) {
            if (line.startsWith("bestmove")) break;
            if (!line.startsWith("info ") || !line.contains(" pv ")) continue;
            int idx = 1; int cp = 0; boolean mate = false;
            String[] tok = line.split("\\s+");
            String pvMove = null;
            for (int i = 0; i < tok.length - 1; i++) {
                switch (tok[i]) {
                    case "multipv": idx = parseSafe(tok[i + 1], 1); break;
                    case "cp":      cp  = parseSafe(tok[i + 1], 0); break;
                    case "mate":    int m = parseSafe(tok[i + 1], 0);
                                    mate = true;
                                    cp = m > 0 ? 100000 - m : -100000 - m;
                                    break;
                    case "pv":      pvMove = tok[i + 1]; break;
                    default: break;
                }
            }
            if (pvMove != null && idx >= 1 && idx <= multiPv) {
                bestByIdx[idx] = pvMove;
                cpByIdx[idx] = cp;
                mateByIdx[idx] = mate;
            }
        }
        List<Pv> result = new ArrayList<>();
        for (int i = 1; i <= multiPv; i++) {
            if (bestByIdx[i] != null) result.add(new Pv(bestByIdx[i], cpByIdx[i], mateByIdx[i]));
        }
        return result;
    }

    private int parseSafe(String s, int dflt) {
        try { return Integer.parseInt(s); } catch (Exception e) { return dflt; }
    }

    public synchronized void stop() {
        ready = false;
        try { if (in != null) { send("stop"); send("quit"); in.close(); } } catch (Exception ignored) {}
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
}
