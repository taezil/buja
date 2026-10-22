package com.dealflash.app;

/** Pure logic for the "re-check on Coupang" feature (no Android types, unit-tested on the JVM). */
public final class PriceVerdict {
    private PriceVerdict() {}
    public static final String SAME = "SAME", UP = "UP", DOWN = "DOWN", UNKNOWN = "UNKNOWN";

    /** Compare what the feed showed with the price read from the live Coupang page. Same tolerance as BrowserScanner.parseDetail. */
    public static String classify(double expected, double found) {
        if (!(found > 0) || !(expected > 0)) return UNKNOWN;
        double tol = Math.max(300.0, expected * 0.02);
        if (Math.abs(found - expected) <= tol) return SAME;
        return found > expected ? UP : DOWN;
    }

    /** The candidate within tolerance of the expected price (closest), or 0 when none is. */
    public static double matchAmong(double expected, java.util.List<Double> candidates) {
        double best = 0, bestD = Double.MAX_VALUE;
        if (candidates == null || !(expected > 0)) return 0;
        for (double c : candidates) { if (SAME.equals(classify(expected, c))) { double d = Math.abs(c - expected); if (d < bestD) { bestD = d; best = c; } } }
        return best;
    }

    public static String listPrices(java.util.List<Double> candidates) {
        StringBuilder b = new StringBuilder(); int n = 0;
        for (double c : candidates) { if (n++ >= 4) { b.append(" 외"); break; } if (b.length() > 0) b.append(" / "); b.append(String.format(java.util.Locale.KOREA, "%,d", Math.round(c))).append("원"); }
        return b.toString();
    }

    public static String encode(String status, double price, long at, String note) {
        return status + "|" + Math.round(price) + "|" + at + "|" + (note == null ? "" : note.replace('|', '/'));
    }

    /** @return {status, price, at, note} or null when the stored value is malformed. */
    public static String[] decode(String s) {
        if (s == null) return null;
        String[] p = s.split("\\|", 4);
        if (p.length < 3) return null;
        try { Double.parseDouble(p[1]); Long.parseLong(p[2]); } catch (NumberFormatException e) { return null; }
        return new String[]{p[0], p[1], p[2], p.length > 3 ? p[3] : ""};
    }

    /** A check that found the price UP, made after the feed row was last refreshed, means the deal has ended. */
    public static boolean endedByCheck(String[] v, long feedLastSeen) {
        if (v == null || !UP.equals(v[0])) return false;
        try { return Long.parseLong(v[2]) >= feedLastSeen; } catch (NumberFormatException e) { return false; }
    }
}
