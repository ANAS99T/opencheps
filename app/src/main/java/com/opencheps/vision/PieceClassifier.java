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

    private SquareSampler.Sample[] templates;
    private char[] labels;
    private float[] emptyLightRGB, emptyDarkRGB;
    private float emptyColorTolerance;
    private float whitePieceLumAvg;
    private float blackPieceLumAvg;
    private boolean trained;
    private Side bootstrapSide;

    public boolean isTrained() { return trained; }
    public Side bootstrapSide() { return bootstrapSide; }

    public synchronized boolean tryBootstrap(Bitmap bmp, BoardDetector.Board board) {
        if (board == null) return false;
        SquareSampler.Sample[] samples = sampleAll(bmp, board);
        if (samples == null) return false;

        float[] lightRGB = new float[3];
        float[] darkRGB  = new float[3];
        int lc = 0, dc = 0;
        for (int r = 2; r <= 5; r++) for (int f = 0; f < 8; f++) {
            SquareSampler.Sample s = samples[r * 8 + f];
            if (((r + f) & 1) == 0) {
                lightRGB[0] += s.meanR; lightRGB[1] += s.meanG; lightRGB[2] += s.meanB; lc++;
            } else {
                darkRGB[0]  += s.meanR; darkRGB[1]  += s.meanG; darkRGB[2]  += s.meanB; dc++;
            }
        }
        if (lc == 0 || dc == 0) return false;
        for (int i = 0; i < 3; i++) { lightRGB[i] /= lc; darkRGB[i] /= dc; }

        float maxEmptyDist = 0;
        for (int r = 2; r <= 5; r++) for (int f = 0; f < 8; f++) {
            float[] ref = ((r + f) & 1) == 0 ? lightRGB : darkRGB;
            float dist = sqDist(samples[r * 8 + f], ref);
            maxEmptyDist = Math.max(maxEmptyDist, dist);
        }
        float tolerance = Math.max(0.0015f, maxEmptyDist * 3.5f + 0.001f);

        int filledTop = 0, filledBot = 0, emptyMid = 0;
        float topLum = 0, botLum = 0; int tn = 0, bn = 0;
        for (int f = 0; f < 8; f++) {
            for (int r : new int[]{0, 1}) {
                float[] ref = ((r + f) & 1) == 0 ? lightRGB : darkRGB;
                if (sqDist(samples[r * 8 + f], ref) > tolerance) {
                    filledTop++;
                    topLum += samples[r * 8 + f].meanLum; tn++;
                }
            }
            for (int r : new int[]{6, 7}) {
                float[] ref = ((r + f) & 1) == 0 ? lightRGB : darkRGB;
                if (sqDist(samples[r * 8 + f], ref) > tolerance) {
                    filledBot++;
                    botLum += samples[r * 8 + f].meanLum; bn++;
                }
            }
        }
        for (int r = 2; r <= 5; r++) for (int f = 0; f < 8; f++) {
            float[] ref = ((r + f) & 1) == 0 ? lightRGB : darkRGB;
            if (sqDist(samples[r * 8 + f], ref) <= tolerance) emptyMid++;
        }

        Log.i(TAG, "bootstrap: filledTop=" + filledTop + " filledBot=" + filledBot
                + " emptyMid=" + emptyMid + " maxEmptyDist=" + maxEmptyDist
                + " tol=" + tolerance);

        if (filledTop < 12 || filledBot < 12 || emptyMid < 24) return false;
        if (tn == 0 || bn == 0) return false;

        topLum /= tn; botLum /= bn;
        Side side = botLum > topLum ? Side.WHITE : Side.BLACK;

        char[] map = side == Side.WHITE ? START_WHITE_POV : flipLabels(START_WHITE_POV);

        templates = samples;
        labels = map;
        emptyLightRGB = lightRGB;
        emptyDarkRGB  = darkRGB;
        emptyColorTolerance = tolerance;
        bootstrapSide = side;

        float whiteSum = 0; int whiteN = 0;
        float blackSum = 0; int blackN = 0;
        for (int i = 0; i < 64; i++) {
            char c = labels[i];
            if (c == '.') continue;
            float pieceLum = pieceLuminanceDelta(samples[i], i);
            if (Character.isUpperCase(c)) { whiteSum += pieceLum; whiteN++; }
            else                          { blackSum += pieceLum; blackN++; }
        }
        whitePieceLumAvg = whiteN > 0 ? whiteSum / whiteN : 0.3f;
        blackPieceLumAvg = blackN > 0 ? blackSum / blackN : -0.3f;

        trained = true;
        Log.i(TAG, "BOOTSTRAPPED side=" + side
                + " whiteDelta=" + whitePieceLumAvg
                + " blackDelta=" + blackPieceLumAvg
                + " tol=" + tolerance);
        return true;
    }

    public synchronized char[][] classifyBoard(Bitmap bmp, BoardDetector.Board board) {
        if (!trained) return null;
        SquareSampler.Sample[] samples = sampleAll(bmp, board);
        if (samples == null) return null;

        char[][] grid = new char[8][8];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                grid[r][f] = nearest(samples[r * 8 + f], r, f);
            }
        }
        return grid;
    }

    private SquareSampler.Sample[] sampleAll(Bitmap bmp, BoardDetector.Board board) {
        if (board.square < 8) return null;
        int rad = Math.max(4, (int) (board.square * 0.28f));
        SquareSampler.Sample[] samples = new SquareSampler.Sample[64];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int cx = Math.round(board.left + (f + 0.5f) * board.square);
                int cy = Math.round(board.top  + (r + 0.5f) * board.square);
                samples[r * 8 + f] = SquareSampler.sample(bmp, cx, cy, rad);
            }
        }
        return samples;
    }

    private char[] flipLabels(char[] src) {
        char[] o = new char[64];
        for (int r = 0; r < 8; r++)
            for (int f = 0; f < 8; f++)
                o[r * 8 + f] = src[(7 - r) * 8 + (7 - f)];
        return o;
    }

    private float sqDist(SquareSampler.Sample s, float[] ref) {
        float dr = s.meanR - ref[0];
        float dg = s.meanG - ref[1];
        float db = s.meanB - ref[2];
        return dr * dr + dg * dg + db * db;
    }

    private float pieceLuminanceDelta(SquareSampler.Sample s, int squareIdx) {
        boolean light = (((squareIdx / 8) + (squareIdx % 8)) & 1) == 0;
        float bgLum = light
                ? 0.299f * emptyLightRGB[0] + 0.587f * emptyLightRGB[1] + 0.114f * emptyLightRGB[2]
                : 0.299f * emptyDarkRGB[0]  + 0.587f * emptyDarkRGB[1]  + 0.114f * emptyDarkRGB[2];
        return s.meanLum - bgLum;
    }

    private char nearest(SquareSampler.Sample s, int r, int f) {
        boolean light = ((r + f) & 1) == 0;
        float[] ref = light ? emptyLightRGB : emptyDarkRGB;
        if (sqDist(s, ref) <= emptyColorTolerance) return '.';

        float delta = pieceLuminanceDelta(s, r * 8 + f);
        float distToWhite = Math.abs(delta - whitePieceLumAvg);
        float distToBlack = Math.abs(delta - blackPieceLumAvg);
        boolean isWhitePiece = distToWhite < distToBlack;

        float best = Float.MAX_VALUE;
        char bestC = isWhitePiece ? 'P' : 'p';
        for (int i = 0; i < templates.length; i++) {
            char tc = labels[i];
            if (tc == '.') continue;
            if (Character.isUpperCase(tc) != isWhitePiece) continue;
            boolean tplLight = (((i / 8) + (i % 8)) & 1) == 0;
            if (tplLight != light) continue;
            float d = SquareSampler.distance(s.feature, templates[i].feature);
            if (d < best) { best = d; bestC = tc; }
        }
        return bestC;
    }

    public synchronized void reset() {
        templates = null;
        labels = null;
        trained = false;
        bootstrapSide = null;
    }
}
