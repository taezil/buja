package com.dealflash.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Pure parser for the Worker's deals payload. Tolerant to field aliases; never throws; counts every rejection. */
public final class ServerDealsParser {
    private ServerDealsParser() {}

    public static final class Item {
        public String key="",title="",url="",source="",image="",signals="";
        public double price; public int pct;
        public long firstSeen,lastSeen; public boolean trusted=true,rocket;
    }

    public static final class Counters {
        public int serverItems, parsed, invalid, stale, noTimestamp, secondsTimestamps, fractionPct, arraySignals, pipeSignals;
        public String rootKeys="", itemsKey="";
        public final Map<String,Integer> reasons=new LinkedHashMap<>();
        public final Map<String,Integer> keyPrefixes=new LinkedHashMap<>();
        public final List<String> samples=new ArrayList<>();
        void reject(String reason,String sample){invalid++;reasons.merge(reason,1,Integer::sum);if(samples.size()<5)samples.add(reason+" | "+sample);}
    }

    private static final String[] ITEM_KEYS={"items","deals","results","products","data"};

    public static JSONArray findItems(Object root,Counters c){
        if(root instanceof JSONArray){c.itemsKey="<root array>";return (JSONArray)root;}
        if(!(root instanceof JSONObject))return null;
        JSONObject o=(JSONObject)root;
        StringBuilder k=new StringBuilder();Iterator<String> it=o.keys();while(it.hasNext()){if(k.length()>0)k.append(',');k.append(it.next());}
        c.rootKeys=k.toString();
        for(String name:ITEM_KEYS){
            JSONArray a=o.optJSONArray(name);if(a!=null){c.itemsKey=name;return a;}
            JSONObject nested=o.optJSONObject(name);
            if(nested!=null){JSONArray n=nested.optJSONArray("items");if(n!=null){c.itemsKey=name+".items";return n;}}
        }
        return null;
    }

    public static String str(JSONObject o,String... names){
        for(String n:names){if(!o.has(n)||o.isNull(n))continue;Object v=o.opt(n);if(v instanceof JSONArray||v instanceof JSONObject)continue;String s=String.valueOf(v).trim();if(!s.isEmpty()&&!"null".equals(s))return s;}
        return "";
    }
    static double num(JSONObject o,String... names){
        for(String n:names){if(!o.has(n)||o.isNull(n))continue;Object v=o.opt(n);double d=Double.NaN;
            if(v instanceof Number)d=((Number)v).doubleValue();else if(v instanceof String){try{d=Double.parseDouble(((String)v).replace(",","").replace("원","").trim());}catch(Exception ignored){}}
            if(Double.isFinite(d))return d;}
        return 0;
    }

    /** Seconds → ms, numeric strings, ISO-8601. Returns 0 if unknown. */
    public static long time(JSONObject o,Counters c,String... names){
        long best=0;
        for(String n:names){
            if(!o.has(n)||o.isNull(n))continue;Object v=o.opt(n);long t=0;
            if(v instanceof Number)t=((Number)v).longValue();
            else if(v instanceof String){String s=((String)v).trim();try{t=Long.parseLong(s);}catch(Exception e){try{t=java.time.Instant.parse(s).toEpochMilli();}catch(Exception e2){try{t=java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();}catch(Exception e3){try{t=java.time.LocalDateTime.parse(s.replace(' ','T')).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();}catch(Exception ignored){}}}}}
            if(t>0&&t<100_000_000_000L){t*=1000L;if(c!=null)c.secondsTimestamps++;}
            best=Math.max(best,t);
        }
        return best;
    }

    public static int pct(JSONObject o,Counters c){
        double d=0;for(String n:new String[]{"last_drop_rate","drop_rate","drop_pct","discount_rate","discount_pct","drop"})d=Math.max(d,num(o,n));
        if(d>0&&d<1){d*=100;if(c!=null)c.fractionPct++;}
        return (int)Math.round(d);
    }

    public static String signals(JSONObject o,Counters c){
        LinkedHashSet<String> set=new LinkedHashSet<>();
        for(String n:new String[]{"signals","signal","signal_names","signal_name","signal_type"}){
            if(!o.has(n)||o.isNull(n))continue;Object v=o.opt(n);
            if(v instanceof JSONArray){JSONArray a=(JSONArray)v;if(c!=null)c.arraySignals++;for(int i=0;i<a.length();i++){Object e=a.opt(i);if(e instanceof JSONObject)add(set,str((JSONObject)e,"signal","name","label"));else if(e!=null&&!JSONObject.NULL.equals(e))add(set,String.valueOf(e));}}
            else{String s=String.valueOf(v);if(s.contains("|")&&c!=null)c.pipeSignals++;for(String x:s.split("[|,]"))add(set,x);}
        }
        return String.join("|",set);
    }
    private static void add(Set<String> set,String s){if(s==null)return;s=s.trim();if(!s.isEmpty()&&!"null".equals(s))set.add(s);}

    /** @return parsed item, or null if rejected (counter + sample recorded). */
    public static Item parse(JSONObject o,long now,Counters c){
        String key=str(o,"product_key","key","id");
        String url=str(o,"product_url","canonical_url","url","link");
        String title=str(o,"product_name","title","name");
        double price=num(o,"current_price","price","total_price","last_price");
        int pct=pct(o,c);
        String source=DataQuality.normalizeSource(str(o,"source","market"),key,url);
        String prefix=key.contains(":")?key.substring(0,key.indexOf(':')+1):"(none)";c.keyPrefixes.merge(prefix,1,Integer::sum);
        DataQuality.Reason r=DataQuality.checkServer(key,source,url,title,price,pct);
        if(r!=DataQuality.Reason.OK){c.reject(r.name(),"key="+key+" src="+str(o,"source")+" url="+url+" price="+price+" pct="+pct+" title="+(title.length()>30?title.substring(0,30):title));return null;}
        Item it=new Item();
        it.key=key;it.title=title;it.url=DataQuality.acceptUrl(url);it.source=source;it.price=price;it.pct=pct;
        it.image=str(o,"image_url","image");it.signals=signals(o,c);
        long seen=time(o,c,"last_seen","observed_at","seen_at","checked_at","updated_at");
        if(seen<=0){c.noTimestamp++;seen=now;}
        it.lastSeen=Math.min(seen,now);
        long first=time(o,c,"first_seen","created_at");it.firstSeen=first>0?Math.min(first,it.lastSeen):it.lastSeen;
        it.rocket="ROCKET".equals(str(o,"shipping_type"));
        if(o.has("trusted")&&!o.isNull("trusted"))it.trusted=o.optBoolean("trusted",true);
        c.parsed++;
        return it;
    }
}
