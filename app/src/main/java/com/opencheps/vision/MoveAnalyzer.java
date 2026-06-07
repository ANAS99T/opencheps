package com.opencheps.vision;

import com.opencheps.util.Side;

import java.util.ArrayList;
import java.util.List;

public class MoveAnalyzer {

    public enum Kind { NONE, QUIET, CAPTURE, CASTLE, EN_PASSANT, PROMOTION, UNKNOWN }

    public static class Result {
        public final Kind kind;
        public final Side mover;
        public final int changeCount;
        public Result(Kind kind, Side mover, int changeCount) {
            this.kind = kind; this.mover = mover; this.changeCount = changeCount;
        }
    }

    public static Result analyze(char[][] prev, char[][] curr) {
        if (prev == null || curr == null) return new Result(Kind.UNKNOWN, null, 0);

        List<int[]> emptied = new ArrayList<>();
        List<int[]> filled = new ArrayList<>();
        List<int[]> replaced = new ArrayList<>();
        int changes = 0;

        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                char p = prev[r][f] == 0 ? '.' : prev[r][f];
                char c = curr[r][f] == 0 ? '.' : curr[r][f];
                if (p == c) continue;
                changes++;
                if (p == '.' && c != '.') filled.add(new int[]{r, f, c});
                else if (p != '.' && c == '.') emptied.add(new int[]{r, f, p});
                else replaced.add(new int[]{r, f, p, c});
            }
        }

        if (changes == 0) return new Result(Kind.NONE, null, 0);

        if (emptied.size() == 1 && filled.size() == 1 && replaced.isEmpty()) {
            char piece = (char) emptied.get(0)[2];
            char dest  = (char) filled.get(0)[2];
            Side mover = Character.isUpperCase(piece) ? Side.WHITE : Side.BLACK;
            if (Character.isUpperCase(piece) != Character.isUpperCase(dest)) {
                return new Result(Kind.UNKNOWN, null, changes);
            }
            boolean isPromotion = (Character.toLowerCase(piece) == 'p'
                    && Character.toLowerCase(dest) != 'p');
            return new Result(isPromotion ? Kind.PROMOTION : Kind.QUIET, mover, changes);
        }

        if (emptied.size() == 1 && filled.isEmpty() && replaced.size() == 1) {
            char attacker = (char) emptied.get(0)[2];
            int[] rep = replaced.get(0);
            char victim   = (char) rep[2];
            char newPiece = (char) rep[3];
            Side mover = Character.isUpperCase(attacker) ? Side.WHITE : Side.BLACK;
            boolean attackerIsWhite = Character.isUpperCase(attacker);
            boolean victimIsWhite   = Character.isUpperCase(victim);
            boolean newIsWhite      = Character.isUpperCase(newPiece);
            if (attackerIsWhite == victimIsWhite) return new Result(Kind.UNKNOWN, null, changes);
            if (newIsWhite != attackerIsWhite)    return new Result(Kind.UNKNOWN, null, changes);
            boolean isPromotion = (Character.toLowerCase(attacker) == 'p'
                    && Character.toLowerCase(newPiece) != 'p');
            return new Result(isPromotion ? Kind.PROMOTION : Kind.CAPTURE, mover, changes);
        }

        if (emptied.size() == 2 && filled.size() == 1 && replaced.isEmpty()) {
            char p1 = (char) emptied.get(0)[2];
            char p2 = (char) emptied.get(1)[2];
            char dest = (char) filled.get(0)[2];
            if (Character.toLowerCase(p1) == 'p' && Character.toLowerCase(p2) == 'p'
                    && Character.toLowerCase(dest) == 'p'
                    && Character.isUpperCase(p1) != Character.isUpperCase(p2)) {
                Side mover = Character.isUpperCase(dest) ? Side.WHITE : Side.BLACK;
                return new Result(Kind.EN_PASSANT, mover, changes);
            }
            return new Result(Kind.UNKNOWN, null, changes);
        }

        if (emptied.size() == 2 && filled.size() == 2 && replaced.isEmpty()) {
            char a = (char) emptied.get(0)[2];
            char b = (char) emptied.get(1)[2];
            char c = (char) filled.get(0)[2];
            char d = (char) filled.get(1)[2];
            if (Character.isUpperCase(a) == Character.isUpperCase(b)
                    && Character.isUpperCase(c) == Character.isUpperCase(d)
                    && Character.isUpperCase(a) == Character.isUpperCase(c)
                    && hasPiece(a, b, 'k') && hasPiece(a, b, 'r')
                    && hasPiece(c, d, 'k') && hasPiece(c, d, 'r')) {
                Side mover = Character.isUpperCase(a) ? Side.WHITE : Side.BLACK;
                return new Result(Kind.CASTLE, mover, changes);
            }
        }

        return new Result(Kind.UNKNOWN, null, changes);
    }

    private static boolean hasPiece(char a, char b, char piece) {
        char lp = Character.toLowerCase(piece);
        return Character.toLowerCase(a) == lp || Character.toLowerCase(b) == lp;
    }
}
