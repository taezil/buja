package com.dealflash.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * Single write path into the feed store (HistoryStore) for BOTH server sync and FCM.
 * Everything here is Handler/Activity free so it can run in JVM tests.
 *
 * Identity (v2.9.6): the server key is THE identity and is stored as given. The only exception is an ephemeral share-link key
 * ({@code fc:l:}, {@code fcshort:}): the token changed on every scan, so the same product arrived under a new key each time. Those
 * are folded onto the key the server now derives from the title ({@link ServerIdentity}, a tested port of the Worker algorithm), never onto the app's own
 * older fingerprint. Option differences (size / quantity are part of the title) keep different products apart.
 */
public final class DealsRepository {
    public static final long FRESH_MS=24L*60*60*1000;
    private DealsRepository(){}

    public static final class Result { public int server,parsed,distinct,saved,rejected,persisted,visible; public String error=""; }

    /** Server key -> key the store uses. Identity for every stable server key; ephemeral keys fold onto the server's title key. Idempotent. */
    public static String canonicalKey(String key,String source,String title){
        if(key!=null&&"FALLCENT".equals(source)&&ServerIdentity.isEphemeralKey(key)){
            String sk=ServerIdentity.keyFor(title);
            if(!sk.isEmpty())return sk;
        }
        return key;
    }

    /** Applies one /deals payload. Server data is authoritative; the whole pipeline is counted in SyncDiag. */
    public static Result applyServerPayload(Context c,Object root,String endpoint,long now){
        Result res=new Result();
        ServerDealsParser.Counters k=new ServerDealsParser.Counters();
        JSONArray a=ServerDealsParser.findItems(root,k);
        if(a==null){
            res.error="items 배열 없음 (root keys: "+k.rootKeys+")";
            SyncDiag.record(c,endpoint,k,0,0,0,res.error,"");
            SyncDiag.error(c,"parse",new IllegalStateException(res.error));
            return res;
        }
        k.serverItems=a.length();
        String sample=a.length()>0&&a.optJSONObject(0)!=null?cut(a.optJSONObject(0).toString(),700):"";
        ArrayList<ServerDealsParser.Item> parsed=new ArrayList<>();
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);
            if(o==null){k.invalid++;k.reasons.merge("NOT_OBJECT",1,Integer::sum);continue;}
            ServerDealsParser.Item it=ServerDealsParser.parse(o,now,k);
            if(it==null)continue;
            it.key=canonicalKey(it.key,it.source,it.title);
            if(now-it.lastSeen>FRESH_MS)k.stale++;
            parsed.add(it);
        }
        // Oldest first: repeated sightings of one product (different scans) build a real chronological price history,
        // and the newest sighting - one atomic title/price/url/image tuple - ends up as the stored state.
        parsed.sort((x,y)->Long.compare(x.lastSeen,y.lastSeen));
        HashSet<String> distinct=new HashSet<>();
        synchronized(HistoryStore.class){
            Map<String,HistoryStore.H> map=HistoryStore.load(c);
            for(ServerDealsParser.Item it:parsed){
                distinct.add(it.key);
                HistoryStore.H z=map.get(it.key);
                if(z==null){z=new HistoryStore.H();z.key=it.key;z.firstSeen=it.firstSeen;}
                z.signals=mergeSignals(z.signals,it.signals);
                if(it.lastSeen>=z.serverSeen||z.last<=0){
                    double oldLast=z.last;long oldSeen=z.lastSeen;
                    z.title=it.title;z.url=it.url;z.source=it.source;z.last=it.price;z.declaredDiscountPct=it.pct;
                    if(it.image!=null&&!it.image.isEmpty())z.imageUrl=it.image;
                    z.lastSeen=Math.max(z.lastSeen,it.lastSeen);z.serverSeen=it.lastSeen;z.rocket=it.rocket;z.trusted=it.trusted;z.origin="server";
                    if(oldLast>0&&oldLast!=it.price){z.prevPrice=oldLast;z.prevSeen=oldSeen;}
                    DealScore.observe(z,it.price,it.lastSeen);
                }else if((z.imageUrl==null||z.imageUrl.isEmpty())&&it.image!=null)z.imageUrl=it.image;
                if(z.firstSeen<=0||(it.firstSeen>0&&it.firstSeen<z.firstSeen))z.firstSeen=it.firstSeen;
                if(z.imageUrl==null)z.imageUrl="";
                map.put(it.key,z);res.saved++;
            }
            HistoryStore.SaveResult sr=HistoryStore.save(c,map);
            // A product whose newest sighting is below the 급락 minimum is no longer a drop, whatever an earlier push pinned in the hot list.
            HashMap<String,Long> ended=new HashMap<>();
            for(String dk:distinct){HistoryStore.H z=map.get(dk);if(z!=null&&z.declaredDiscountPct<FeedFilter.DROP_TAB_MIN)ended.put(dk,z.serverSeen>0?z.serverSeen:z.lastSeen);}   // judged by the NEWEST sighting stored for the key
            HotDealStore.expire(c,ended);
            Map<String,HistoryStore.H> back=HistoryStore.load(c);
            res.persisted=back.size();
            for(HistoryStore.H h:back.values())if(DataQuality.visible(h)&&now-h.lastSeen<=FRESH_MS)res.visible++;
            res.server=k.serverItems;res.parsed=k.parsed;res.rejected=k.invalid;res.distinct=distinct.size();
            String saveErr=sr.failed>0?sr.failed+" serialize failures, first="+sr.firstError:"";
            SyncDiag.record(c,endpoint,k,res.saved,res.visible,res.persisted,saveErr,sample);
        }
        return res;
    }

    public static boolean applyPush(Context c,String key,String name,String url,String rawSource,double price,int drop,String imageUrl,long now){
        return applyPushKey(c,key,name,url,rawSource,price,drop,imageUrl,now)!=null;
    }

    /**
     * FCM / event-poll observation -> HistoryStore + HotDealStore, so "push arrived" always implies "feed has it".
     * @return the key the product is stored under (use it for notification deep links), or null if rejected.
     */
    public static String applyPushKey(Context c,String key,String name,String url,String rawSource,double price,int drop,String imageUrl,long now){
        String source=DataQuality.normalizeSource(rawSource,key,url);
        String title=name==null||name.trim().length()<2?"상품":name.trim();
        DataQuality.Reason r=DataQuality.checkServer(key,source,url,title,price,drop);
        if(r!=DataQuality.Reason.OK){
            SyncDiag.push(c,key,"REJECTED",r+" url="+url+" price="+price+" drop="+drop);
            android.util.Log.w("DealFlashSync","push rejected "+r+" key="+key+" url="+url);
            return null;
        }
        String ck=canonicalKey(key,source,title);
        synchronized(HistoryStore.class){
            Map<String,HistoryStore.H> map=HistoryStore.load(c);
            HistoryStore.H h=map.get(ck);
            if(h==null){h=new HistoryStore.H();h.key=ck;h.firstSeen=now;}
            if(h.last>0&&h.last!=price){h.prevPrice=h.last;h.prevSeen=h.lastSeen;}
            h.title=title;h.url=DataQuality.acceptUrl(url);h.source=source;h.last=price;h.declaredDiscountPct=drop;
            if(imageUrl!=null&&!imageUrl.isEmpty())h.imageUrl=imageUrl;if(h.imageUrl==null)h.imageUrl="";
            h.lastSeen=now;h.trusted=true;h.origin="fcm";DealScore.observe(h,price,now);
            map.put(ck,h);HistoryStore.save(c,map);
        }
        HotDealStore.put(c,ck,title,url,source,price,0,drop,imageUrl);
        SyncDiag.push(c,ck,"STORED","server key="+key+" drop="+drop+" price="+price);
        return ck;
    }

    static String mergeSignals(String a,String b){
        LinkedHashSet<String> set=new LinkedHashSet<>();
        for(String s:new String[]{a,b})if(s!=null&&!s.isEmpty())for(String x:s.split("\\|")){x=x.trim();if(!x.isEmpty()&&!"null".equals(x))set.add(x);}
        return String.join("|",set);
    }
    private static String cut(String s,int n){return s.length()>n?s.substring(0,n)+"…":s;}
}
