package com.opencheps.vision;

import com.opencheps.util.Side;

public class FenBuilder {

    public static class Validation {
        public final boolean ok;
        public final String reason;
        Validation(boolean ok, String reason) { this.ok = ok; this.reason = reason; }
    }

    public static String build(char[][] grid, Side bootstrapSide, Side stm) {
        char[][] g = bootstrapSide == Side.BLACK ? flip(grid) : grid;

        Validation v = validate(g);
        if (!v.ok) return null;

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int run = 0;
            for (int f = 0; f < 8; f++) {
                char c = g[r][f];
                if (c == '.' || c == 0) { run++; continue; }
                if (run > 0) { sb.append(run); run = 0; }
                sb.append(c);
            }
            if (run > 0) sb.append(run);
            if (r < 7) sb.append('/');
        }
        return sb.toString() + " " + (stm == Side.WHITE ? "w" : "b") + " - - 0 1";
    }

    public static Validation validate(char[][] g) {
        int wK = 0, bK = 0, wP = 0, bP = 0;
        int wN = 0, bN = 0, wB = 0, bB = 0, wR = 0, bR = 0, wQ = 0, bQ = 0;
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                char c = g[r][f];
                if (c == 'K') wK++; else if (c == 'k') bK++;
                else if (c == 'P') { wP++; if (r == 0 || r == 7) return new Validation(false, "P on back rank"); }
                else if (c == 'p') { bP++; if (r == 0 || r == 7) return new Validation(false, "p on back rank"); }
                else if (c == 'N') wN++; else if (c == 'n') bN++;
                else if (c == 'B') wB++; else if (c == 'b') bB++;
                else if (c == 'R') wR++; else if (c == 'r') bR++;
                else if (c == 'Q') wQ++; else if (c == 'q') bQ++;
            }
        }
        if (wK != 1) return new Validation(false, "wK=" + wK);
        if (bK != 1) return new Validation(false, "bK=" + bK);
        if (wP > 8 || bP > 8) return new Validation(false, "too many pawns");
        if (wN + wB + wR + wQ + wP > 15) return new Validation(false, "white over 15 non-king");
        if (bN + bB + bR + bQ + bP > 15) return new Validation(false, "black over 15 non-king");
        if (kingsAdjacent(g))            return new Validation(false, "kings adjacent");
        return new Validation(true, null);
    }

    private static boolean kingsAdjacent(char[][] g) {
        int wr=-1,wf=-1,br=-1,bf=-1;
        for (int r=0;r<8;r++) for (int f=0;f<8;f++) {
            if (g[r][f]=='K') { wr=r; wf=f; }
            else if (g[r][f]=='k') { br=r; bf=f; }
        }
        if (wr<0||br<0) return false;
        return Math.abs(wr-br) <= 1 && Math.abs(wf-bf) <= 1;
    }

    public static char[][] flip(char[][] g) {
        char[][] o = new char[8][8];
        for (int r = 0; r < 8; r++)
            for (int f = 0; f < 8; f++) o[r][f] = g[7 - r][7 - f];
        return o;
    }

    public static int materialDiff(char[][] g) {
        int w = 0, b = 0;
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) {
            int v = value(g[r][f]);
            if (Character.isUpperCase(g[r][f])) w += v; else b += v;
        }
        return w - b;
    }

    private static int value(char c) {
        switch (Character.toLowerCase(c)) {
            case 'p': return 1;
            case 'n': case 'b': return 3;
            case 'r': return 5;
            case 'q': return 9;
            case 'k': return 0;
            default: return 0;
        }
    }
}
