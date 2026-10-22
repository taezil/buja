package com.dealflash.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Predicate;

/**
 * v2.10.2: the server (Worker 2.9.8) marks a deal "ended" once it has been missing from Fallcent for 3 normal scans of the section
 * it lived in. The app asks about the keys it holds locally (exact, complete lookup, no recency window or per-call cap) and hides
 * the ended ones everywhere except 보관 - favourites are never deleted. Handler/Activity free so it runs in JVM tests.
 */
public final class EndedSync {
    private EndedSync(){}
    /** Keys per request (the server accepts up to 2000). */
    public static final int BATCH=400;
    /** An ended, non-favourite row is dropped from the local store after this long (it only crowds the 500-row cache). */
    public static final long PRUNE_MS=24L*60*60*1000;

    public static final class Ended { public final String key; public final long at; public Ended(String k,long a){key=k;at=a;} }

    /** Local rows worth asking about: live rows (endedAt==0) the payload did not just confirm as live, newest first, split into requests. */
    public static List<List<String>> batches(Map<String,HistoryStore.H> local,Set<String> confirmedLive){
        ArrayList<HistoryStore.H> rows=new ArrayList<>();
        for(HistoryStore.H h:local.values())if(h!=null&&h.key!=null&&!h.key.isEmpty()&&h.endedAt<=0&&(confirmedLive==null||!confirmedLive.contains(h.key)))rows.add(h);
        rows.sort((a,b)->Long.compare(b.lastSeen,a.lastSeen));
        ArrayList<List<String>> out=new ArrayList<>();ArrayList<String> cur=new ArrayList<>();
        for(HistoryStore.H h:rows){cur.add(h.key);if(cur.size()>=BATCH){out.add(cur);cur=new ArrayList<>();}}
        if(!cur.isEmpty())out.add(cur);
        return out;
    }

    public static List<Ended> parse(JSONObject resp){
        ArrayList<Ended> out=new ArrayList<>();
        JSONArray a=resp==null?null:resp.optJSONArray("ended");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String k=o.optString("product_key","");if(!k.isEmpty())out.add(new Ended(k,o.optLong("ended_at",0)));}
        return out;
    }

    /** Marks the ended keys that exist locally, expires their hot-list pins, prunes old ended non-favourites. Returns how many rows were newly marked. */
    public static int apply(Context c,Collection<Ended> ended,Predicate<String> favorite,long now){
        synchronized(HistoryStore.class){
            Map<String,HistoryStore.H> map=HistoryStore.load(c);int marked=0;boolean dirty=false;HashMap<String,Long> pins=new HashMap<>();
            if(ended!=null)for(Ended e:ended){
                HistoryStore.H h=map.get(e.key);if(h==null)continue;
                long at=e.at>0?e.at:now;
                if(h.endedAt<=0){h.endedAt=at;marked++;dirty=true;}
                pins.put(e.key,at);
            }
            Iterator<Map.Entry<String,HistoryStore.H>> it=map.entrySet().iterator();
            while(it.hasNext()){HistoryStore.H h=it.next().getValue();if(h.endedAt>0&&now-h.endedAt>PRUNE_MS&&!(favorite!=null&&favorite.test(h.key))){it.remove();dirty=true;}}
            if(dirty)HistoryStore.save(c,map);
            if(!pins.isEmpty())HotDealStore.expire(c,pins);
            return marked;
        }
    }
}
