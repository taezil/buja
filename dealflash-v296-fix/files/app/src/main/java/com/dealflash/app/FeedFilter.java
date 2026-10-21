package com.dealflash.app;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure feed selection used by MainActivity. NOTE: the user's alert threshold is deliberately NOT a parameter —
 * it only decides whether a push is sent, never what the feeds list.
 */
public final class FeedFilter {
    private FeedFilter() {}
    /** Minimum declared drop for an item to count as a 급락 card (independent from any alert threshold). */
    public static final int DROP_TAB_MIN=30;
    /** How long a sighting stays listed per tab: 급락 = what is worth acting on now, 발견 = recent discoveries, 알림 = what the user's own threshold would have caught since yesterday. */
    public static long windowMs(String screen){
        if("급락".equals(screen))return 2L*60*60*1000;
        if("발견".equals(screen))return 6L*60*60*1000;
        return DealsRepository.FRESH_MS;
    }
    /** A price last confirmed longer ago than this may already have ended (Fallcent drops a deal from its list when the price rises, so the server just stops refreshing it). */
    public static final long PRICE_STALE_MS=30L*60*1000;
    public static boolean isPriceStale(HistoryStore.H h,long now){return h!=null&&h.lastSeen>0&&now-h.lastSeen>PRICE_STALE_MS;}
    /** Comparator prefix for the deal tabs: freshly confirmed prices first, possibly-ended ones after, regardless of the chosen sort. */
    public static int freshFirst(HistoryStore.H a,HistoryStore.H b,long now){return Boolean.compare(isPriceStale(a,now),isPriceStale(b,now));}
    /** Same, but also demotes rows a live Coupang re-check found already ended (key set built once per sort). */
    public static int freshFirst(HistoryStore.H a,HistoryStore.H b,long now,java.util.Set<String> endedKeys){return Boolean.compare(isPriceStale(a,now)||endedKeys.contains(a.key),isPriceStale(b,now)||endedKeys.contains(b.key));}

    public static final class Params {
        public String screen="급락",category="전체",signal="전체",query="";
        public int minDrop=0,minSaving=0; public boolean evidenceOnly=false,favoritesOnly=false;
        public Set<String> hotKeys=Collections.emptySet();
        public Predicate<String> favorite=k->false;
        public Function<HistoryStore.H,String> categoryOf=h->ProductUX.category(h.title);
    }

    public static boolean hasSignal(HistoryStore.H x,String signal){
        if(x==null||x.signals==null||x.signals.isEmpty())return false;
        for(String s:x.signals.split("\\|"))if(signal.equals(s.trim()))return true;return false;
    }

    /** Stage 1: per-record eligibility (before family grouping). */
    public static List<HistoryStore.H> eligible(Collection<HistoryStore.H> history,Params p,long now){
        List<HistoryStore.H> out=new ArrayList<>();String q=p.query==null?"":p.query.toLowerCase(Locale.KOREA);
        // A search looks through everything seen in the last 24h on any tab: the 급락/발견 limits are for browsing, not for finding a named product.
        final boolean searching=!q.isEmpty();final long window=searching?DealsRepository.FRESH_MS:windowMs(p.screen);
        for(HistoryStore.H h:history){
            if(!DataQuality.visible(h)||now-h.lastSeen>window)continue;
            boolean isDrop=h.declaredDiscountPct>=DROP_TAB_MIN||p.hotKeys.contains(h.key);
            if("급락".equals(p.screen)&&!isDrop&&!searching)continue;
            if("발견".equals(p.screen)&&!searching&&!"전체".equals(p.signal)&&!hasSignal(h,p.signal))continue;
            if("보관".equals(p.screen)&&!p.favorite.test(h.key))continue;
            if(!"전체".equals(p.category)&&!p.category.equals(p.categoryOf.apply(h)))continue;
            if(searching&&!ProductUX.clean(h.title).toLowerCase(Locale.KOREA).contains(q))continue;
            out.add(h);
        }
        out.sort((a,b)->Long.compare(b.lastSeen,a.lastSeen));
        return out.size()>400?new ArrayList<>(out.subList(0,400)):out;
    }

    /** Stage 2: family grouping + user filters. Returns the cards to render (unsorted). */
    public static List<HistoryStore.H> cards(Collection<HistoryStore.H> history,Params p,long now,Map<String,DealScore.Evidence> evidenceOut){
        List<HistoryStore.H> shown=new ArrayList<>();
        for(HistoryStore.H x:ProductLinks.group(eligible(history,p,now))){
            DealScore.Evidence e=evidenceOf(x);if(evidenceOut!=null)evidenceOut.put(x.key,e);
            if(x.declaredDiscountPct<p.minDrop||(p.minSaving>0&&(e.count<3||e.saving<p.minSaving)))continue;
            if(p.evidenceOnly&&e.count<3)continue;if(p.favoritesOnly&&!p.favorite.test(x.key))continue;
            shown.add(x);
        }
        return shown;
    }
    public static DealScore.Evidence evidenceOf(HistoryStore.H x){
        HistoryStore.H prior=new HistoryStore.H();prior.observations=x.observations;prior.last=x.prevPrice;prior.lastSeen=x.prevSeen;
        return DealScore.evaluate(prior,x.last,x.declaredDiscountPct,x.lastSeen);
    }
}
