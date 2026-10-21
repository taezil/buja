package com.dealflash.app;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quality gates.
 *  - valid():       STRICT. Identity must be re-derivable from the URL. Used for data the phone scraped itself.
 *  - checkServer(): SERVER-AUTHORITATIVE. The Worker owns identity (cp:/fc:/fcx:/fcfp:/fcurl:/...), so the key is
 *                   only shape-checked; URL host/path, price, drop and title/price integrity are still enforced.
 *                   Returns a Reason so every rejection is countable.
 */
public final class DataQuality {
    private DataQuality() {}

    public enum Reason { OK, BAD_SOURCE, BAD_KEY, BAD_URL, BAD_PRICE, BAD_DROP, BAD_TITLE, EMBEDDED_MISMATCH }

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9]{1,12}:[^\\s]{1,280}$");
    private static final Pattern HOST = Pattern.compile("(?i)^https?://([^/?#:@]+)(?::\\d+)?([/?#].*)?$");
    private static final java.util.Set<String> FC_RESERVED = new java.util.HashSet<>(
            java.util.Arrays.asList("recommend", "ranking", "list", "search", "category", "home"));

    public static boolean valid(String key,String source,String url,String title,double price,int pct) {
        if (!("FALLCENT".equals(source)||"COUPANG".equals(source))) return false;
        String safe=ProductUrl.canonical(source,url);
        if(safe==null||key==null)return false;
        String transportKey=ProductUrl.key(source,safe);
        boolean identityOk=key.equals(transportKey)
                || ("FALLCENT".equals(source)&&ProductIdentity.isInternalFallcentKey(key));
        if(!identityOk)return false;
        if(!Double.isFinite(price)||price<100||price>1_000_000_000||pct<0||pct>99)return false;
        if(title==null||title.trim().length()<2)return false;
        double[] embedded=ListingIntegrity.embedded(title);
        return embedded==null || (Math.abs(embedded[0]-price)<=1 && Math.abs(embedded[1]-pct)<=1);
    }

    /** Server/FCM-authoritative gate (superset of valid()). */
    public static Reason checkServer(String key,String source,String url,String title,double price,int pct) {
        if (!("FALLCENT".equals(source)||"COUPANG".equals(source))) return Reason.BAD_SOURCE;
        if (key==null||key.startsWith("http")||!KEY.matcher(key).matches()) return Reason.BAD_KEY;
        if (acceptUrl(url)==null) return Reason.BAD_URL;
        if (!Double.isFinite(price)||price<100||price>1_000_000_000) return Reason.BAD_PRICE;
        if (pct<0||pct>99) return Reason.BAD_DROP;
        if (title==null||title.trim().length()<2) return Reason.BAD_TITLE;
        double[] e=ListingIntegrity.embedded(title);
        if (e!=null && !(Math.abs(e[0]-price)<=1 && Math.abs(e[1]-pct)<=1)) return Reason.EMBEDDED_MISMATCH;
        return Reason.OK;
    }
    public static boolean acceptable(String key,String source,String url,String title,double price,int pct){
        return checkServer(key,source,url,title,price,pct)==Reason.OK;
    }

    /** Returns the cleaned URL if its origin is a known product origin, else null. Never throws. */
    public static String acceptUrl(String raw) {
        if (raw==null) return null;
        String s=raw.trim().replace("\\u002F","/").replace("\\/","/").replace("&amp;","&").replace("&#38;","&");
        if (s.startsWith("//")) s="https:"+s;
        Matcher m=HOST.matcher(s);
        if (!m.matches()) return null;
        String host=m.group(1).toLowerCase(Locale.ROOT), rest=m.group(2)==null?"":m.group(2);
        String path=rest; int q=path.indexOf('?'); if(q>=0)path=path.substring(0,q); q=path.indexOf('#'); if(q>=0)path=path.substring(0,q);
        if (host.equals("coupang.com")||host.endsWith(".coupang.com")) {
            if (host.equals("link.coupang.com")) return path.length()>1?s:null;
            return path.matches("(?i)^/(?:vp|np|vm)/products/\\d+(?:/.*)?$")?s:null;
        }
        if (host.equals("coupa.ng")) return path.length()>1?s:null;
        if (host.equals("fcnt.link")) return path.replaceAll("^/+|/+$","").isEmpty()?null:s;
        if (host.equals("fallcent.com")||host.endsWith(".fallcent.com")) {
            String p=path.replaceAll("^/+|/+$","");
            if (p.isEmpty()) return null;
            String[] seg=p.split("/");
            String first=seg[0].toLowerCase(Locale.ROOT);
            if (first.equals("product")) {
                if (seg.length<2||FC_RESERVED.contains(seg[1].toLowerCase(Locale.ROOT))) return null;
                return s;
            }
            return FC_RESERVED.contains(first)?null:s;
        }
        return null;
    }

    /** Normalizes a server-provided source label. Blank/unknown labels are derived from key/url. */
    public static String normalizeSource(String raw,String key,String url) {
        String s=raw==null?"":raw.trim().toUpperCase(Locale.ROOT);
        if (s.equals("FALLCENT")||s.equals("FC")) return "FALLCENT";
        if (s.equals("COUPANG")||s.equals("CP")) return "COUPANG";
        String u=url==null?"":url.toLowerCase(Locale.ROOT);
        if (key!=null&&key.startsWith("cp:")) return "COUPANG";
        if (u.contains("coupang.com/")&&!u.contains("fallcent")) return "COUPANG";
        return "FALLCENT";
    }

    public static boolean visible(HistoryStore.H h){
        return h!=null&&h.trusted&&checkServer(h.key,h.source,h.url,h.title,h.last,h.declaredDiscountPct)==Reason.OK;
    }
}
