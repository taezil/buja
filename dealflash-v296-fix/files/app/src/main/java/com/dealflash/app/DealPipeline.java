package com.dealflash.app;

import android.content.Context;
import java.util.*;

/** Testable collection -> identity gate -> history -> hot list transaction boundary. */
public final class DealPipeline {
    public static class Result { public boolean accepted,notify;public String key="";public DealScore.Evidence evidence; }
    public static Result accept(Context c,Map<String,HistoryStore.H> history,PageScanner.Listing x,
                                String source,int threshold,int minSaving,long now){
        Result r=new Result();
        String safe=ProductUrl.canonical(source,x.url);
        String transportKey=ProductUrl.key(source,safe);
        if(x.key!=null&&!x.key.isEmpty()&&!transportKey.equals(x.key))return r;
        if(!DataQuality.valid(transportKey,source,safe,x.title,x.price,x.declaredDiscountPct))return r;
        String key=ProductIdentity.stableKey(source,x.title,x.imageUrl,x.linkedCoupangUrl,transportKey);
        if(key==null||key.isEmpty()||!DataQuality.valid(key,source,safe,x.title,x.price,x.declaredDiscountPct))return r;
        r.key=key;
        HistoryStore.H h=history.get(key);boolean fresh=h==null;
        if(fresh){h=new HistoryStore.H();h.key=key;h.firstSeen=now;}
        if(h.firstSeen<=0)h.firstSeen=now;
        r.evidence=DealScore.evaluate(h,x.price,x.declaredDiscountPct,now);
        boolean changed=fresh||h.last!=x.price; // A badge-only change is not a price movement.
        h.prevPrice=h.last;h.prevSeen=h.lastSeen;h.prevDiscountPct=h.declaredDiscountPct;
        h.title=x.title;h.url=safe;h.source=source;h.sources=source;h.trusted=true;
        if("FALLCENT".equals(source)) {
            String link=ProductLinks.exactOptionKey(x.linkedCoupangUrl);
            if(!link.isEmpty())h.linkedCoupangKey=link;
        }
        h.last=x.price;h.lastSeen=now;h.rocket=x.rocket;h.declaredDiscountPct=x.declaredDiscountPct;
        h.imageUrl=x.imageUrl==null?"":x.imageUrl;
        h.dealScore=r.evidence.score;h.reference=r.evidence.reference;
        h.low=r.evidence.low>0?Math.min(r.evidence.low,x.price):x.price;
        DealScore.observe(h,x.price,now);
        history.put(key,h);r.accepted=true;
        // Every valid 급락 stays visible regardless of the user's alert threshold; discoveries below the 급락 minimum live in HistoryStore only.
        if(x.declaredDiscountPct>=FeedFilter.DROP_TAB_MIN)HotDealStore.put(c,key,x.title,safe,source,x.price,r.evidence.reference,x.declaredDiscountPct,h.imageUrl,h.firstSeen);
        else HotDealStore.expire(c,java.util.Collections.singletonMap(key,now));   // the discount ended: stop pinning it in 급락
        boolean alertPass=(r.evidence.drop>=threshold || x.declaredDiscountPct>=threshold)
            && (minSaving==0 || (r.evidence.count>=3&&r.evidence.saving>=minSaving));
        if(alertPass){
            String sig=key+"|"+Math.round(x.price);
            r.notify=changed&&!sig.equals(h.lastAlertSig);
            if(r.notify)h.lastAlertSig=sig;
        }
        // Visibility and collection continue even when the alert condition is not met.
        return r;
    }
}
