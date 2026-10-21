package com.dealflash.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class HotDealStore {
    private static final String PREF="dealflash_hot_v6", KEY="hot";
    public static class Hot {
        public String key,title,url,source,imageUrl="";
        public double current,reference,marketPrice;
        public int dropPct;
        public long foundAt,lastVerifiedAt;
        public boolean verified;
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("key",key);o.put("title",title);o.put("url",url);o.put("source",source);o.put("imageUrl",imageUrl);o.put("current",current);o.put("reference",reference);o.put("marketPrice",marketPrice);o.put("dropPct",dropPct);o.put("foundAt",foundAt);o.put("lastVerifiedAt",lastVerifiedAt);o.put("verified",verified);}catch(Exception ignored){}return o;}
        static Hot from(JSONObject o){Hot h=new Hot();h.key=o.optString("key");h.title=o.optString("title","상품");h.url=o.optString("url");h.source=o.optString("source","COUPANG");h.imageUrl=o.optString("imageUrl","");h.current=o.optDouble("current");h.reference=o.optDouble("reference");h.marketPrice=o.optDouble("marketPrice");h.dropPct=o.optInt("dropPct");h.foundAt=o.optLong("foundAt");h.lastVerifiedAt=o.optLong("lastVerifiedAt");h.verified=o.optBoolean("verified");return h;}
    }
    /** 저장된 목록. key 가 같은 항목은 1개로 합친다 (최초 발견시각은 가장 이른 값 유지). */
    public static synchronized List<Hot> all(Context c){
        ArrayList<Hot> raw=new ArrayList<>();
        try{JSONArray a=new JSONArray(c.getSharedPreferences(PREF,0).getString(KEY,"[]"));for(int i=0;i<a.length();i++)raw.add(Hot.from(a.getJSONObject(i)));}catch(Exception ignored){}
        ArrayList<Hot> out=new ArrayList<>(ListingIntegrity.mergeByKey(raw,h->h.key,HotDealStore::mergeSameKey).values());
        out.sort((a,b)->Long.compare(b.lastVerifiedAt,a.lastVerifiedAt));
        return out;
    }
    /** 같은 key 두 건: 더 최근에 검증된 쪽 내용 + 더 이른 foundAt. */
    static Hot mergeSameKey(Hot x,Hot y){Hot newer=y.lastVerifiedAt>=x.lastVerifiedAt?y:x;Hot older=newer==y?x:y;
        if(newer.imageUrl==null||newer.imageUrl.isEmpty())newer.imageUrl=older.imageUrl;
        long f=Math.min(x.foundAt>0?x.foundAt:Long.MAX_VALUE,y.foundAt>0?y.foundAt:Long.MAX_VALUE);newer.foundAt=f==Long.MAX_VALUE?newer.foundAt:f;return newer;}
    public static synchronized List<Hot> recent(Context c){long cut=System.currentTimeMillis()-24L*60*60*1000;ArrayList<Hot> out=new ArrayList<>();for(Hot h:all(c))if(h.lastVerifiedAt>=cut && DataQuality.acceptable(h.key,h.source,h.url,h.title,h.current,h.dropPct))out.add(h);return out;}

    public static synchronized void put(Context c,String key,String title,String url,String source,double current,double reference,int pct){put(c,key,title,url,source,current,reference,pct,null);}
    /**
     * 상품 identity 는 key(폴센트 상품 id / 쿠팡 productId[+vendorItemId]) 하나뿐이다.
     * 제목·가격 유사도, URL 문자열 비교로 다른 상품에 덮어쓰지 않는다.
     * foundAt(최초 발견)은 새 항목을 만들 때 한 번만 정하고, 재검증은 lastVerifiedAt 만 갱신한다.
     */
    public static synchronized void put(Context c,String key,String title,String url,String source,double current,double reference,int pct,String imageUrl){
        put(c,key,title,url,source,current,reference,pct,imageUrl,0);
    }
    public static synchronized void put(Context c,String key,String title,String url,String source,double current,double reference,int pct,String imageUrl,long firstDiscoveredAt){
        if(!DataQuality.acceptable(key,source,url,title,current,pct))return;
        List<Hot> a=all(c);Hot found=null;
        for(Hot h:a)if(key.equals(h.key)){found=h;break;}
        long now=System.currentTimeMillis();
        if(found==null){found=new Hot();found.key=key;found.foundAt=firstDiscoveredAt>0?Math.min(firstDiscoveredAt,now):now;found.title="상품";a.add(found);}
        if(title!=null&&!title.isEmpty())found.title=title;
        if(url!=null&&!url.isEmpty())found.url=url;
        found.source=source;found.current=current;found.reference=reference;found.dropPct=pct;found.lastVerifiedAt=now;found.verified=true;
        if(imageUrl!=null&&!imageUrl.isEmpty())found.imageUrl=imageUrl;
        else if(found.imageUrl==null||found.imageUrl.isEmpty()){HistoryStore.H hi=HistoryStore.load(c).get(key);if(hi!=null&&hi.imageUrl!=null)found.imageUrl=hi.imageUrl;}
        save(c,a);
    }
    /** 마이그레이션용: 목록 전체 교체 (key 중복은 저장 시 제거). */
    public static synchronized void replaceAll(Context c,List<Hot> list){save(c,list);}
    private static void save(Context c,List<Hot> list){JSONArray a=new JSONArray();long keep=System.currentTimeMillis()-90L*24*60*60*1000;int n=0;java.util.HashSet<String> seen=new java.util.HashSet<>();for(Hot h:list){if(h.lastVerifiedAt<keep||n>=1000)continue;if(h.key==null||h.key.isEmpty()||!seen.add(h.key))continue;a.put(h.json());n++;}c.getSharedPreferences(PREF,0).edit().putString(KEY,a.toString()).apply();}
    public static synchronized void remove(Context c,String key){List<Hot> a=all(c);a.removeIf(h->key.equals(h.key));save(c,a);}
    public static void clear(Context c){c.getSharedPreferences(PREF,0).edit().clear().apply();}
}
