package com.dealflash.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class HistoryStore {
    private static final String PREF = "dealflash_history_v6";
    private static final String KEY = "items";
    public static final int MAX = 500;

    public static class H {
        public String key, title, url, source, imageUrl;
        public boolean rocket;
        public int declaredDiscountPct, prevDiscountPct, dealScore;
        public String observations="", sources="", linkedCoupangKey="", signals="";
        /** "server" | "fcm" | "local" — where the freshest price/drop came from. */
        public String origin="local";
        public boolean trusted;
        public double prevPrice; public long prevSeen;
        public double reference, last, low;
        public long firstSeen, lastSeen;
        /** Newest server-side timestamp already applied (server data only overwrites when it is not older). */
        public long serverSeen;
        public String lastAlertSig = "";
        /** Server-side 30-day price-history stats (see PriceJudge). */
        public double histMin, histAvg; public int histN; public long histFirst;
        public H copy(){
            H n=new H();n.key=key;n.title=title;n.url=url;n.source=source;n.imageUrl=imageUrl;n.rocket=rocket;
            n.declaredDiscountPct=declaredDiscountPct;n.prevDiscountPct=prevDiscountPct;n.dealScore=dealScore;
            n.observations=observations;n.sources=sources;n.linkedCoupangKey=linkedCoupangKey;n.signals=signals;
            n.origin=origin;n.trusted=trusted;n.prevPrice=prevPrice;n.prevSeen=prevSeen;n.reference=reference;
            n.last=last;n.low=low;n.firstSeen=firstSeen;n.lastSeen=lastSeen;n.serverSeen=serverSeen;n.lastAlertSig=lastAlertSig;n.histMin=histMin;n.histAvg=histAvg;n.histN=histN;n.histFirst=histFirst;
            return n;
        }
    }

    public static final class SaveResult { public int requested, written, failed; public String firstError=""; }
    private static volatile SaveResult lastSave=new SaveResult();
    public static SaveResult lastSave(){return lastSave;}

    // Parse cache: avoids re-parsing the (multi-MB) JSON on every tab switch / keystroke.
    private static String cachedRaw; private static Map<String,H> cachedMap;

    public static synchronized Map<String,H> load(Context c) {
        String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]");
        if (cachedRaw!=null && cachedMap!=null && cachedRaw.equals(raw)) return copyOf(cachedMap);
        HashMap<String,H> map = new HashMap<>();
        try {
            JSONArray a = new JSONArray(raw);
            int limit=Math.min(a.length(),MAX);
            for (int i=0;i<limit;i++) {
                JSONObject o=a.optJSONObject(i); if(o==null)continue; H h=new H();
                h.linkedCoupangKey=o.optString("linkedCoupangKey","");h.observations=o.optString("observations", "");h.sources=o.optString("sources", "");h.signals=o.optString("signals", "");h.trusted=o.optBoolean("trusted",false);
                h.key=o.optString("key"); h.title=o.optString("title"); h.url=o.optString("url"); h.source=o.optString("source"); h.imageUrl=o.optString("imageUrl","");
                h.rocket=o.optBoolean("rocket",false); h.declaredDiscountPct=o.optInt("declaredDiscountPct",0); h.prevDiscountPct=o.optInt("prevDiscountPct",0); h.dealScore=o.optInt("dealScore",0);
                h.prevPrice=o.optDouble("prevPrice",0); h.prevSeen=o.optLong("prevSeen",0);
                h.histMin=o.optDouble("histMin",0);h.histAvg=o.optDouble("histAvg",0);h.histN=o.optInt("histN",0);h.histFirst=o.optLong("histFirst",0);
                h.reference=o.optDouble("reference",0); h.last=o.optDouble("last",0); h.low=o.optDouble("low",0);
                h.firstSeen=o.optLong("firstSeen",0); h.lastSeen=o.optLong("lastSeen",0); h.lastAlertSig=o.optString("lastAlertSig","");
                h.origin=o.optString("origin","local"); h.serverSeen=o.optLong("serverSeen",0);
                if (!h.key.isEmpty()) map.put(h.key,h);
            }
        } catch(Exception e){ android.util.Log.e("DealFlashStore","load failed: "+e); }
        cachedRaw=raw; cachedMap=map;
        return copyOf(map);
    }

    private static Map<String,H> copyOf(Map<String,H> m){HashMap<String,H> out=new HashMap<>();for(Map.Entry<String,H> e:m.entrySet())out.put(e.getKey(),e.getValue().copy());return out;}
    private static double fin(double v){return Double.isFinite(v)?v:0;}

    public static synchronized SaveResult save(Context c, Map<String,H> map) {
        ArrayList<H> list=new ArrayList<>(map.values());
        list.sort((a,b)->Long.compare(b.lastSeen,a.lastSeen));
        JSONArray a=new JSONArray();
        SaveResult r=new SaveResult(); r.requested=Math.min(MAX,list.size());
        long now=System.currentTimeMillis();
        for(int i=0;i<r.requested;i++) {
            H h=list.get(i); JSONObject o=new JSONObject();
            try {
                h.observations=DealScore.compact(h.observations,now);
                o.put("linkedCoupangKey",h.linkedCoupangKey==null?"":h.linkedCoupangKey);o.put("observations",h.observations);o.put("sources",h.sources==null?"":h.sources);
                o.put("signals",h.signals==null?"":h.signals);o.put("trusted",h.trusted);o.put("key",h.key);o.put("title",h.title==null?"":h.title);o.put("url",h.url==null?"":h.url);
                o.put("source",h.source==null?"":h.source);o.put("imageUrl",h.imageUrl==null?"":h.imageUrl);o.put("rocket",h.rocket);
                o.put("declaredDiscountPct",h.declaredDiscountPct);o.put("prevDiscountPct",h.prevDiscountPct);o.put("dealScore",h.dealScore);
                o.put("prevPrice",fin(h.prevPrice));o.put("prevSeen",h.prevSeen);o.put("reference",fin(h.reference));o.put("last",fin(h.last));o.put("low",fin(h.low));
                o.put("firstSeen",h.firstSeen);o.put("lastSeen",h.lastSeen);o.put("lastAlertSig",h.lastAlertSig==null?"":h.lastAlertSig);
                o.put("origin",h.origin==null?"local":h.origin);o.put("serverSeen",h.serverSeen);
                o.put("histMin",fin(h.histMin));o.put("histAvg",fin(h.histAvg));o.put("histN",h.histN);o.put("histFirst",h.histFirst);
                a.put(o); r.written++;
            } catch(Exception e){ r.failed++; if(r.firstError.isEmpty())r.firstError=h.key+": "+e; android.util.Log.e("DealFlashStore","serialize failed "+h.key+": "+e); }
        }
        String raw=a.toString();
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,raw).apply();
        cachedRaw=null; cachedMap=null; // next load re-parses exactly what was persisted
        lastSave=r;
        return r;
    }

    public static synchronized void clear(Context c) {
        cachedRaw=null;cachedMap=null;
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }
}
