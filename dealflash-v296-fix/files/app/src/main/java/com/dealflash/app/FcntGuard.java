package com.dealflash.app;

import android.content.Context;

/**
 * v2.10.5: Fallcent's share links (fcnt.link) sit behind Cloudflare rate limiting (Error 1015). Several taps - and the up-to-4 repeated
 * clicks of the hidden page - used to hit it in a burst and get this phone blocked for a while. This guard (1) remembers a block and
 * stops touching fcnt.link for a cooldown, and (2) remembers the Coupang address a successful hidden-page run produced for a product
 * so the next tap (also after the Fallcent page is gone) goes straight to Coupang without any Fallcent request.
 * Handler/Activity free so it runs in JVM tests.
 */
public final class FcntGuard {
    private FcntGuard() {}
    private static final String PREF = "fcnt_guard";
    public static final long BLOCK_MS = 10L * 60 * 1000;
    public static final long CACHE_MS = 24L * 60 * 60 * 1000;

    public static boolean blocked(Context c, long now) { return blockedUntil(c) > now; }
    public static long blockedUntil(Context c) { try { return c.getSharedPreferences(PREF, 0).getLong("blocked_until", 0); } catch (Throwable t) { return 0; } }
    public static void markBlocked(Context c, long now) { try { c.getSharedPreferences(PREF, 0).edit().putLong("blocked_until", now + BLOCK_MS).apply(); } catch (Throwable ignored) {} }
    public static String blockedMessage(Context c, long now) {
        long left = Math.max(1, (blockedUntil(c) - now + 59_999L) / 60_000L);
        return "폴센트(fcnt.link)가 잠시 접속을 제한했어요 (Cloudflare 1015). 약 " + left + "분 뒤에 다시 눌러 주세요.";
    }

    /** Cloudflare block / challenge / rate-limit pages (title or first text). */
    public static boolean looksBlocked(String title, String body) {
        String t = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT), b = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
        return t.contains("access denied") || t.contains("just a moment") || t.contains("attention required")
            || b.contains("error 1015") || b.contains("you are being rate limited") || b.contains("verifying you are human");
    }

    /** Stable key of a Fallcent product page (fallcent.com/product/&lt;id&gt;); null for ephemeral share-link tokens, which are never cached. */
    public static String cacheKey(String input) {
        if (input == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^https?://(?:www\\.)?fallcent\\.com/product/([A-Za-z0-9_-]{16,})").matcher(input.trim());
        return m.find() ? "fp:" + m.group(1) : null;
    }
    public static void remember(Context c, String input, String coupangUrl, long now) {
        String k = cacheKey(input);
        if (k == null || coupangUrl == null || !coupangUrl.startsWith("http")) return;
        try { c.getSharedPreferences(PREF, 0).edit().putString(k, now + "|" + coupangUrl).apply(); } catch (Throwable ignored) {}
    }
    public static String recall(Context c, String input, long now) {
        String k = cacheKey(input);
        if (k == null) return null;
        try {
            String v = c.getSharedPreferences(PREF, 0).getString(k, null);
            if (v == null) return null;
            int i = v.indexOf('|');
            if (i <= 0 || now - Long.parseLong(v.substring(0, i)) > CACHE_MS) return null;
            String u = v.substring(i + 1);
            return u.startsWith("http") ? u : null;
        } catch (Throwable t) { return null; }
    }
}
