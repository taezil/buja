package com.dealflash.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;

/** Stable identity used inside DealFlash. Short/share URLs are transport, not product identity. */
public final class ProductIdentity {
    private ProductIdentity() {}

    public static boolean isInternalFallcentKey(String key) {
        return key != null && (key.startsWith("fcfp:") || key.startsWith("fcx:") || key.startsWith("fcurl:"));
    }

    public static String stableKey(String source, PageScanner.Listing x) {
        if (x == null) return "";
        String raw = ProductUrl.key(source, ProductUrl.canonical(source, x.url));
        return stableKey(source, x.title, x.imageUrl, x.linkedCoupangUrl, raw);
    }

    public static String stableKey(HistoryStore.H h) {
        if (h == null) return "";
        if (!"FALLCENT".equals(h.source)) return h.key == null ? "" : h.key;
        if (isInternalFallcentKey(h.key)) return h.key;
        if (h.linkedCoupangKey != null && h.linkedCoupangKey.matches("cp:[0-9]+:v:[0-9]+")) {
            return "fcx:" + sha1(h.linkedCoupangKey, 20);
        }
        return fallbackKey(h.title, h.key);
    }

    /**
     * For Fallcent, fcnt.link tokens are ephemeral/share identifiers. Prefer exact Coupang option
     * linkage when available; otherwise use the complete normalized product title. We deliberately
     * do not include price, so one product keeps its history when the price changes.
     */
    public static String stableKey(String source, String title, String imageUrl, String linkedCoupangUrl, String rawKey) {
        if (!"FALLCENT".equals(source)) return rawKey == null ? "" : rawKey;
        String exact = ProductLinks.exactOptionKey(linkedCoupangUrl);
        if (!exact.isEmpty()) return "fcx:" + sha1(exact, 20);
        return fallbackKey(title, rawKey);
    }

    public static String groupKey(HistoryStore.H h) {
        if (h == null) return "";
        // An exact Coupang option row and the Fallcent row linked to it share one group key, otherwise the explicit link could never merge them.
        if ("COUPANG".equals(h.source) && h.key != null && h.key.matches("cp:[0-9]+:v:[0-9]+")) {
            return "fcx:" + sha1(h.key, 20);
        }
        if ("FALLCENT".equals(h.source) && h.linkedCoupangKey != null && h.linkedCoupangKey.matches("cp:[0-9]+:v:[0-9]+")) {
            return "fcx:" + sha1(h.linkedCoupangKey, 20);
        }
        return stableKey(h);
    }

    /**
     * v2.9.6: ONE identity for Fallcent products everywhere (server rows, FCM pushes, on-device scans): the Worker's title key.
     * The previous private fingerprint produced a different hash than the server for ~half of the live titles, which is what
     * showed the same product twice. Falls back to the transport key when the title is too weak to identify anything.
     */
    public static String fallbackKey(String title, String rawKey) {
        String k = ServerIdentity.keyFor(title);
        return k.isEmpty() ? (rawKey == null ? "" : rawKey) : k;
    }

    public static String normalizedTitle(String title) {
        String x = ProductUX.clean(title == null ? "" : title);
        x = Normalizer.normalize(x, Normalizer.Form.NFKC).toLowerCase(Locale.KOREA);
        x = x.replace('×', 'x').replace('＊', 'x');
        x = x.replaceAll("([0-9])\\s+(ml|l|g|kg|cm|mm|인치|개|매|팩|봉|병|캔)(?=$|[^a-z])", "$1$2");
        x = x.replaceAll("[^가-힣a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
        return x;
    }

    private static String sha1(String s, int chars) {
        try {
            byte[] d=MessageDigest.getInstance("SHA-1").digest((s==null?"":s).getBytes(StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();
            for(byte x:d)b.append(String.format(Locale.ROOT,"%02x",x));
            return b.substring(0,Math.min(chars,b.length()));
        } catch(Exception e) { return Integer.toHexString((s==null?"":s).hashCode()); }
    }
}
