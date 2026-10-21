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
    public static final int DROP_TAB_MIN=10;

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
        for(HistoryStore.H h:history){
            if(!DataQuality.visible(h)||now-h.lastSeen>DealsRepository.FRESH_MS)continue;
            boolean isDrop=h.declaredDiscountPct>=DROP_TAB_MIN||p.hotKeys.contains(h.key);
            if("급락".equals(p.screen)&&!isDrop)continue;
            if("발견".equals(p.screen)&&!"전체".equals(p.signal)&&!hasSignal(h,p.signal))continue;
            if("보관".equals(p.screen)&&!p.favorite.test(h.key))continue;
            if(!"전체".equals(p.category)&&!p.category.equals(p.categoryOf.apply(h)))continue;
            if(!q.isEmpty()&&!ProductUX.clean(h.title).toLowerCase(Locale.KOREA).contains(q))continue;
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
