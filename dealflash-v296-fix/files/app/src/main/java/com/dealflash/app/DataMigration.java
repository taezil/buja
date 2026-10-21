package com.dealflash.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Storage migrations. Schema 11 re-keys rows stored under the app's old title fingerprint or an ephemeral share-link key onto the server's identity. */
public final class DataMigration {
    public static final int SCHEMA = 11;
    private static final String PREF = "dealflash_schema";
    private DataMigration() {}

    public static synchronized void run(Context c) {
        SharedPreferences sp=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        int v=sp.getInt("v",0);
        if(v>=SCHEMA)return;
        try{
            if(v<8){
                sp.edit().putString("history_backup_v8",c.getSharedPreferences("dealflash_history_v6",0).getString("items","[]"))
                  .putString("hot_backup_v8",c.getSharedPreferences("dealflash_hot_v6",0).getString("hot","[]")).apply();
                migrateLegacyHistory(c);migrateLegacyHot(c);v=8;sp.edit().putInt("v",8).apply();
            }
            if(v<9){
                sp.edit().putString("history_backup_v9",c.getSharedPreferences("dealflash_history_v6",0).getString("items","[]"))
                  .putString("hot_backup_v9",c.getSharedPreferences("dealflash_hot_v6",0).getString("hot","[]")).apply();
                migrateStableIdentity(c);
                v=9;sp.edit().putInt("v",9).apply();
            }
            if(v<10){sp.edit().putInt("v",10).apply();v=10;}
            if(v<11){
                sp.edit().putString("history_backup_v11",c.getSharedPreferences("dealflash_history_v6",0).getString("items","[]"))
                  .putString("hot_backup_v11",c.getSharedPreferences("dealflash_hot_v6",0).getString("hot","[]")).apply();
                String report=migrateServerIdentity(c);
                sp.edit().putString("report_v11",report).putInt("v",11).apply();
            }
        }catch(Throwable ignored){/* retry next launch */}
    }

    private static String transportKey(String source,String url,String oldKey){
        if(("FALLCENT".equals(source)||"COUPANG".equals(source))&&url!=null&&!url.isEmpty()){
            String k=ProductUrl.key(source,url);if(k!=null&&!k.isEmpty())return k;
        }
        return oldKey;
    }

    private static ListingIntegrity.Rec rec(String key,String title,String image,double price,int pct){
        double[] e=ListingIntegrity.embedded(title);
        return new ListingIntegrity.Rec(key,title,image,price,pct,e==null?0:e[0],e==null?0:(int)e[1],false);
    }
    private static boolean polluted(String source,String title,double price,int pct){return "FALLCENT".equals(source)&&ListingIntegrity.mismatch(rec("",title,"",price,pct));}

    /** Old v2.2/v2.3 cleanup, retained for fresh installs or very old upgrades. */
    private static void migrateLegacyHistory(Context c){
        List<HistoryStore.H> raw=new ArrayList<>(HistoryStore.load(c).values());
        for(HistoryStore.H h:raw)h.key=transportKey(h.source,h.url,h.key);
        Map<String,HistoryStore.H> merged=ListingIntegrity.mergeByKey(raw,h->h.key,(x,y)->mergeHistory(x,y));
        List<ListingIntegrity.Rec> recs=new ArrayList<>();for(HistoryStore.H h:merged.values())if("FALLCENT".equals(h.source))recs.add(rec(h.key,h.title,h.imageUrl,h.last,h.declaredDiscountPct));
        ListingIntegrity.Sanitized s=ListingIntegrity.sanitize(recs);for(String k:s.remove)merged.remove(k);for(String k:s.clearImage){HistoryStore.H h=merged.get(k);if(h!=null)h.imageUrl="";}
        merged.values().removeIf(h->!DataQuality.valid(h.key,h.source,h.url,h.title,h.last,h.declaredDiscountPct));
        for(HistoryStore.H h:merged.values()){h.trusted=false;h.observations="";h.dealScore=0;}
        HistoryStore.save(c,merged);
    }

    private static void migrateLegacyHot(Context c){
        List<HotDealStore.Hot> raw=HotDealStore.all(c);for(HotDealStore.Hot h:raw)h.key=transportKey(h.source,h.url,h.key);
        Map<String,HotDealStore.Hot> merged=ListingIntegrity.mergeByKey(raw,h->h.key,(x,y)->HotDealStore.mergeSameKey(x,y));
        List<HotDealStore.Hot> keep=new ArrayList<>();for(HotDealStore.Hot h:merged.values())if(DataQuality.valid(h.key,h.source,h.url,h.title,h.current,h.dropPct))keep.add(h);
        HotDealStore.replaceAll(c,keep);
    }

    /** Convert fcnt.link-derived identities to a stable product identity and collapse existing duplicates. */
    private static void migrateStableIdentity(Context c){
        Map<String,HistoryStore.H> old=HistoryStore.load(c);
        ArrayList<HistoryStore.H> rows=new ArrayList<>(old.values());
        for(HistoryStore.H h:rows){
            String raw=transportKey(h.source,h.url,h.key);
            if("FALLCENT".equals(h.source)){
                if(h.linkedCoupangKey!=null&&h.linkedCoupangKey.matches("cp:[0-9]+:v:[0-9]+"))h.key="fcx:"+sha(h.linkedCoupangKey);
                else h.key=ProductIdentity.fallbackKey(h.title,raw);
            }else h.key=raw;
        }
        Map<String,HistoryStore.H> merged=ListingIntegrity.mergeByKey(rows,h->h.key,DataMigration::mergeHistory);
        merged.values().removeIf(h->!DataQuality.valid(h.key,h.source,h.url,h.title,h.last,h.declaredDiscountPct));
        HistoryStore.save(c,merged);

        List<HotDealStore.Hot> hotRows=HotDealStore.all(c);
        for(HotDealStore.Hot h:hotRows){
            String raw=transportKey(h.source,h.url,h.key);
            h.key="FALLCENT".equals(h.source)?ProductIdentity.fallbackKey(h.title,raw):raw;
        }
        Map<String,HotDealStore.Hot> hotMerged=ListingIntegrity.mergeByKey(hotRows,h->h.key,HotDealStore::mergeSameKey);
        ArrayList<HotDealStore.Hot> keep=new ArrayList<>();
        for(HotDealStore.Hot h:hotMerged.values())if(DataQuality.valid(h.key,h.source,h.url,h.title,h.current,h.dropPct))keep.add(h);
        HotDealStore.replaceAll(c,keep);
    }


    /** The key a stored row should have under the server's identity, or the unchanged key when it must not move. */
    static String serverKeyFor(String source,String key,String title){
        if(!"FALLCENT".equals(source)||key==null)return key;
        if(!(key.startsWith("fcfp:")||ServerIdentity.isEphemeralKey(key)))return key;   // fcx:/fcurl:/fc:<id>/cp: are exact ids: never touched
        String k=ServerIdentity.keyFor(title);
        return k.isEmpty()?key:k;                                                       // weak title: keep what we have
    }

    /**
     * v2.9.6: rows written by older builds sit under the app's own title fingerprint (or an ephemeral fc:l: key) while the server
     * now serves the same product under its own fingerprint, so one product showed up twice. Re-key every such row, merge the
     * collisions (newest sighting wins, earliest firstSeen / lowest price kept), and carry favorites and category overrides over.
     * Different options have different titles -> different keys -> never merged. Returns a one-line report.
     */
    static String migrateServerIdentity(Context c){
        Map<String,String> remap=new HashMap<>();
        List<HistoryStore.H> rows=new ArrayList<>(HistoryStore.load(c).values());
        int before=rows.size();
        for(HistoryStore.H h:rows){String nk=serverKeyFor(h.source,h.key,h.title);if(!nk.equals(h.key)){remap.put(h.key,nk);h.key=nk;}}
        Map<String,HistoryStore.H> merged=ListingIntegrity.mergeByKey(rows,h->h.key,DataMigration::mergeHistory);
        HistoryStore.save(c,merged);

        List<HotDealStore.Hot> hot=HotDealStore.all(c);int hotBefore=hot.size();
        for(HotDealStore.Hot h:hot){String nk=serverKeyFor(h.source,h.key,h.title);if(!nk.equals(h.key)){remap.put(h.key,nk);h.key=nk;}}
        Map<String,HotDealStore.Hot> hotMerged=ListingIntegrity.mergeByKey(hot,h->h.key,HotDealStore::mergeSameKey);
        HotDealStore.replaceAll(c,new ArrayList<>(hotMerged.values()));

        int fav=0;SharedPreferences favs=c.getSharedPreferences("favorites",0),cats=c.getSharedPreferences("category_overrides",0);
        SharedPreferences.Editor fe=favs.edit(),ce=cats.edit();
        for(Map.Entry<String,String> e:remap.entrySet()){
            String o=e.getKey(),n=e.getValue();
            if(favs.getBoolean(o,false)){fe.putBoolean(n,true).remove(o);fav++;}
            String cat=cats.getString(o,null);if(cat!=null){if(cats.getString(n,null)==null)ce.putString(n,cat);ce.remove(o);}
        }
        fe.apply();ce.apply();
        return "history "+before+"->"+merged.size()+", hot "+hotBefore+"->"+hotMerged.size()+", rekeyed="+remap.size()+", favorites moved="+fav;
    }

    private static HistoryStore.H mergeHistory(HistoryStore.H x,HistoryStore.H y){
        boolean px=polluted(x.source,x.title,x.last,x.declaredDiscountPct),py=polluted(y.source,y.title,y.last,y.declaredDiscountPct);
        HistoryStore.H n=(px!=py)?(px?y:x):(y.lastSeen>=x.lastSeen?y:x),o=n==y?x:y;
        long f=Math.min(x.firstSeen>0?x.firstSeen:Long.MAX_VALUE,y.firstSeen>0?y.firstSeen:Long.MAX_VALUE);if(f!=Long.MAX_VALUE)n.firstSeen=f;
        if(!polluted(o.source,o.title,o.last,o.declaredDiscountPct)&&o.low>0&&(n.low<=0||o.low<n.low))n.low=o.low;
        if((n.imageUrl==null||n.imageUrl.isEmpty())&&o.imageUrl!=null)n.imageUrl=o.imageUrl;
        String no=n.observations==null?"":n.observations, oo=o.observations==null?"":o.observations;
        if(no.length()<oo.length())n.observations=oo;
        if((n.linkedCoupangKey==null||n.linkedCoupangKey.isEmpty())&&o.linkedCoupangKey!=null)n.linkedCoupangKey=o.linkedCoupangKey;
        n.trusted=n.trusted||o.trusted;return n;
    }

    private static String sha(String s){
        try{byte[] d=java.security.MessageDigest.getInstance("SHA-1").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(java.util.Locale.ROOT,"%02x",x));return b.substring(0,20);}catch(Exception e){return Integer.toHexString(s.hashCode());}
    }
}
