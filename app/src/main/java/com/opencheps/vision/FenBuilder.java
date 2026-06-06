package com.opencheps.vision;

import com.opencheps.util.Side;

public class FenBuilder {

    public static String build(char[][] grid, Side bootstrapSide, Side stm) {
        char[][] g = bootstrapSide == Side.BLACK ? flip(grid) : grid;

        StringBuilder sb = new StringBuilder();
        boolean haveWK = false, haveBK = false;
        for (int r = 0; r < 8; r++) {
            int run = 0;
            for (int f = 0; f < 8; f++) {
                char c = g[r][f];
                if (c == 'K') haveWK = true;
                if (c == 'k') haveBK = true;
                if (c == '.' || c == 0) { run++; continue; }
                if (run > 0) { sb.append(run); run = 0; }
                sb.append(c);
            }
            if (run > 0) sb.append(run);
            if (r < 7) sb.append('/');
        }
        if (!haveWK || !haveBK) return null;
        return sb.toString() + " " + (stm == Side.WHITE ? "w" : "b") + " - - 0 1";
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
