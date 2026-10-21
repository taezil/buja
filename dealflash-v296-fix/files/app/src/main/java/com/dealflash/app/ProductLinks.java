package com.dealflash.app;

import java.util.*;

/** Cross-source grouping. Exact variants stay distinct in storage; the feed may collapse cosmetic variants into one family card. */
public final class ProductLinks {
    public static String exactOptionKey(String url){
        String canonical=ProductUrl.canonicalCoupang(url);
        String key=ProductUrl.key("COUPANG",canonical);
        // Item-only / product-only references cannot prove the same seller offer.
        return key.contains(":v:")?key:"";
    }
    public static String displayFamilyKey(HistoryStore.H h){
        if(h==null)return "";
        String exact=ProductIdentity.groupKey(h);
        if(!"FALLCENT".equals(h.source)||(h.linkedCoupangKey!=null&&!h.linkedCoupangKey.isEmpty()))return exact;
        String n=ProductIdentity.normalizedTitle(h.title);
        // (Boundaries are explicit look-arounds: \\b means different things on ICU/Android and recent JDKs for Hangul.)
        // Never family-collapse count/size variants: those can have materially different unit economics.
        if(n.matches(".*(?<![가-힣a-z0-9])[0-9]+(?:\\.[0-9]+)?(?:ml|l|g|kg|개|매|팩|봉|병|캔|입|세트)(?![가-힣a-z0-9]).*"))return exact;
        // Cosmetic colour words are commonly emitted as separate Fallcent offers. Keep them in DB,
        // but collapse them on the feed so users do not see the same base product repeatedly.
        String family=n.replaceAll("(?<![가-힣a-z0-9])(?:라이트|다크|연|진)?\\s*(?:그레이|grey|gray|블랙|black|화이트|white|베이지|beige|브라운|brown|네이비|navy|블루|blue|레드|red|핑크|pink|그린|green|옐로우|yellow|퍼플|purple|오렌지|orange)(?![가-힣a-z0-9])"," ")
                .replaceAll("\\s+"," ").trim();
        if(family.length()<8)return exact;
        return "family:"+Integer.toHexString(family.hashCode());
    }
    public static List<HistoryStore.H> group(List<HistoryStore.H> items){
        Map<String,List<HistoryStore.H>> groups=new LinkedHashMap<>();
        for(HistoryStore.H h:items){
            String key=displayFamilyKey(h);
            if(key==null||key.isEmpty())key=h.key;
            groups.computeIfAbsent(key,k->new ArrayList<>()).add(h);
        }
        List<HistoryStore.H> result=new ArrayList<>();
        for(List<HistoryStore.H> g:groups.values()){
            HistoryStore.H selected=g.get(0);long earliest=selected.firstSeen;
            Set<String> sources=new TreeSet<>();
            for(HistoryStore.H h:g){if(betterRepresentative(h,selected))selected=h;if(h.firstSeen>0)earliest=earliest<=0?h.firstSeen:Math.min(earliest,h.firstSeen);sources.add(h.source);}
            // One whole record represents the family (best discount, then lowest price, then newest). Never assemble title/image/price from different records.
            selected.firstSeen=earliest;selected.sources=String.join(" + ",sources);result.add(selected);
        }
        return result;
    }
    private static boolean betterRepresentative(HistoryStore.H a,HistoryStore.H b){
        if(a.declaredDiscountPct!=b.declaredDiscountPct)return a.declaredDiscountPct>b.declaredDiscountPct;
        if(a.last>0&&b.last>0&&a.last!=b.last)return a.last<b.last;
        return a.lastSeen>b.lastSeen;
    }
}
