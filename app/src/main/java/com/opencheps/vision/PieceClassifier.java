package com.opencheps.vision;

import android.graphics.Bitmap;
import android.util.Log;

import com.opencheps.util.Side;

public class PieceClassifier {

    private static final String TAG = "OC/Classifier";

    private static final char[] START_WHITE_POV = {
        'r','n','b','q','k','b','n','r',
        'p','p','p','p','p','p','p','p',
        '.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.',
        'P','P','P','P','P','P','P','P',
        'R','N','B','Q','K','B','N','R'
    };

    private float[][] templates;
    private char[] labels;
    private float emptyLightLum, emptyDarkLum;
    private boolean trained;
    private Side bootstrapSide;

    public boolean isTrained() { return trained; }
    public Side bootstrapSide() { return bootstrapSide; }

    public synchronized boolean tryBootstrap(Bitmap bmp, BoardDetector.Board board) {
        if (board == null) return false;
        float[][] feats = new float[64][];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int cx = (int) (board.left + (f + 0.5f) * board.square);
                int cy = (int) (board.top  + (r + 0.5f) * board.square);
                int rad = (int) (board.square * 0.40f);
                feats[r * 8 + f] = SquareSampler.sample(bmp, cx, cy, rad);
            }
        }
        Side side = guessSideFromBootstrap(feats);
        if (side == null) {
            Log.i(TAG, "bootstrap: not a starting position");
            return false;
        }

        char[] map = side == Side.WHITE
            ? START_WHITE_POV
            : flipLabels(START_WHITE_POV);

        float lightSum = 0, darkSum = 0; int lightN = 0, darkN = 0;
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                if (!isEmptyRow(r, side)) continue;
                float lum = SquareSampler.meanLum(feats[r * 8 + f]);
                boolean light = ((r + f) & 1) == 0;
                if (light) { lightSum += lum; lightN++; }
                else       { darkSum  += lum; darkN++;  }
            }
        }
        emptyLightLum = lightN > 0 ? lightSum / lightN : 0.7f;
        emptyDarkLum  = darkN  > 0 ? darkSum  / darkN  : 0.3f;

        templates = feats;
        labels = map;
        bootstrapSide = side;
        trained = true;
        Log.i(TAG, "Bootstrapped templates for side " + side
                + " emptyLight=" + emptyLightLum + " emptyDark=" + emptyDarkLum);
        return true;
    }

    private boolean isEmptyRow(int r, Side side) {
        return r >= 2 && r <= 5;
    }

    private char[] flipLabels(char[] src) {
        char[] o = new char[64];
        for (int r = 0; r < 8; r++)
            for (int f = 0; f < 8; f++)
                o[r * 8 + f] = src[(7 - r) * 8 + (7 - f)];
        return o;
    }

    private Side guessSideFromBootstrap(float[][] feats) {
        float[] lum = new float[64];
        for (int i = 0; i < 64; i++) lum[i] = SquareSampler.meanLum(feats[i]);
        float bgLight = 0, bgDark = 0; int nl = 0, nd = 0;
        for (int r = 2; r <= 5; r++) for (int f = 0; f < 8; f++) {
            if (((r + f) & 1) == 0) { bgLight += lum[r * 8 + f]; nl++; }
            else                    { bgDark  += lum[r * 8 + f]; nd++; }
        }
        if (nl == 0 || nd == 0) return null;
        bgLight /= nl; bgDark /= nd;

        int filledTop = 0, filledBot = 0;
        for (int f = 0; f < 8; f++) {
            if (filled(lum, 0, f, bgLight, bgDark)) filledTop++;
            if (filled(lum, 1, f, bgLight, bgDark)) filledTop++;
            if (filled(lum, 6, f, bgLight, bgDark)) filledBot++;
            if (filled(lum, 7, f, bgLight, bgDark)) filledBot++;
        }
        int emptyMid = 0;
        for (int r = 2; r <= 5; r++) for (int f = 0; f < 8; f++) {
            if (!filled(lum, r, f, bgLight, bgDark)) emptyMid++;
        }
        if (filledTop < 13 || filledBot < 13 || emptyMid < 26) return null;

        float topAvg = 0, botAvg = 0; int tn = 0, bn = 0;
        for (int f = 0; f < 8; f++) {
            if (filled(lum, 0, f, bgLight, bgDark)) { topAvg += lum[0 * 8 + f]; tn++; }
            if (filled(lum, 1, f, bgLight, bgDark)) { topAvg += lum[1 * 8 + f]; tn++; }
            if (filled(lum, 6, f, bgLight, bgDark)) { botAvg += lum[6 * 8 + f]; bn++; }
            if (filled(lum, 7, f, bgLight, bgDark)) { botAvg += lum[7 * 8 + f]; bn++; }
        }
        if (tn == 0 || bn == 0) return null;
        topAvg /= tn; botAvg /= bn;
        return botAvg > topAvg ? Side.WHITE : Side.BLACK;
    }

    private boolean filled(float[] lum, int r, int f, float bgLight, float bgDark) {
        float v = lum[r * 8 + f];
        float bg = ((r + f) & 1) == 0 ? bgLight : bgDark;
        return Math.abs(v - bg) > 0.10f;
    }

    public synchronized char[][] classifyBoard(Bitmap bmp, BoardDetector.Board board) {
        if (!trained) return null;
        char[][] grid = new char[8][8];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int cx = (int) (board.left + (f + 0.5f) * board.square);
                int cy = (int) (board.top  + (r + 0.5f) * board.square);
                int rad = (int) (board.square * 0.40f);
                float[] feat = SquareSampler.sample(bmp, cx, cy, rad);
                grid[r][f] = nearest(feat, r, f);
            }
        }
        return grid;
    }

    private char nearest(float[] feat, int r, int f) {
        float lum = SquareSampler.meanLum(feat);
        boolean light = ((r + f) & 1) == 0;
        float bg = light ? emptyLightLum : emptyDarkLum;
        if (Math.abs(lum - bg) < 0.07f) return '.';

        float best = Float.MAX_VALUE;
        char bestC = '.';
        for (int i = 0; i < templates.length; i++) {
            if (labels[i] == '.') continue;
            boolean tplLight = (((i / 8) + (i % 8)) & 1) == 0;
            if (tplLight != light) continue;
            float d = SquareSampler.distance(feat, templates[i]);
            if (d < best) { best = d; bestC = labels[i]; }
        }
        if (best > 30f) {
            return guessByColor(feat) ? Character.toLowerCase(bestC) : Character.toUpperCase(bestC);
        }
        return bestC;
    }

    private boolean guessByColor(float[] feat) {
        int dark = 0, total = 0;
        for (int i = 0; i < feat.length; i += 3) {
            float lum = 0.299f * feat[i] + 0.587f * feat[i + 1] + 0.114f * feat[i + 2];
            if (lum < 0.35f) dark++;
            total++;
        }
        return dark * 2 > total;
    }

    public synchronized void reset() {
        templates = null;
        labels = null;
        trained = false;
        bootstrapSide = null;
    }
}
