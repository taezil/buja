package com.dealflash.app;

import android.content.Context;
import org.json.*;
import java.nio.file.*;
import java.util.*;

/** v2.9.6: server-identity port (cross-language vectors), legacy key folding, local re-key migration, option separation. */
public class IdentityMigrationTests {
  static int pass=0,fail=0;
  static void check(boolean v,String l){System.out.println((v?"PASS ":"FAIL ")+l);if(v)pass++;else fail++;}

  static JSONObject item(String key,String title,double price,int drop,String sig,long seen,String url){
    try{JSONObject o=new JSONObject();o.put("product_key",key);o.put("product_name",title);o.put("product_url",url);o.put("image_url","https://img.example/"+Math.abs(title.hashCode())+".jpg");
      o.put("category","식품");o.put("source","FALLCENT");o.put("first_seen",seen-3600_000L);o.put("last_seen",seen);o.put("current_price",price);o.put("observed_at",seen);o.put("last_drop_rate",drop);o.put("signals",sig);return o;}catch(Exception e){throw new RuntimeException(e);}
  }
  static final String TOK="https://fcnt.link/AzhTNBjuK3MFn5rOFAq2vsmfANCtXTAuqJQ74krxjADdS1aY8jdoPSJwIdlY8Jw4dXRtfpG9zZcbWeisHR4GK-JnRgtnrrJXkt55l2wSvRfF8_zUW9PZ1tVyN2d_1Hx7MiJEcfNA5GbTZjBaMTmsHImhzYFr_J7S8E4u0suCEH5A";

  public static void main(String[] a)throws Exception{
    String dir=a.length>0?a[0]:"tests/fixtures";
    long now=System.currentTimeMillis();

    // ---- 1. cross-language identity vectors produced by the Worker's own code
    JSONArray v=new JSONArray(new String(Files.readAllBytes(Paths.get(dir,"identity-vectors.json")),"UTF-8"));
    int bad=0;
    for(int i=0;i<v.length();i++){
      JSONObject o=v.optJSONObject(i);String t=o.optString("title");
      String c=ServerIdentity.cleanTitle(t),fp=ServerIdentity.fingerprint(t),k=ServerIdentity.keyFor(t),d=ServerIdentity.displayTitle(t);
      boolean ok=c.equals(o.optString("clean"))&&fp.equals(o.optString("fp"))&&k.equals(o.optString("key"))&&d.equals(o.optString("display"))&&ServerIdentity.displayTitle(d).equals(d);
      if(!ok){bad++;System.out.println("  MISMATCH "+t+"\n    clean "+c+" | "+o.optString("clean")+"\n    fp    "+fp+" | "+o.optString("fp")+"\n    key   "+k+" | "+o.optString("key")+"\n    disp  "+d+" | "+o.optString("display"));}
    }
    check(bad==0,"IDENTITY PORT: "+v.length()+" server vectors reproduce clean/fingerprint/key/display byte-for-byte (mismatches="+bad+")");
    check(ServerIdentity.keyFor("삼다수 500ml × 1개").length()>0&&!ServerIdentity.keyFor("삼다수 500ml × 1개").equals(ServerIdentity.keyFor("삼다수 500ml × 24개")),"option variants (qty) keep different keys");
    check(ServerIdentity.keyFor("삼성전자 갤럭시 Z Fold8 자급제, 그라파이트, 256GB").equals(ServerIdentity.keyFor("삼성전자 갤럭시 Z Fold8 자급제, 그라파이트, 256GB"))&&!ServerIdentity.keyFor("삼성전자 갤럭시 Z Fold8 자급제, 그라파이트, 256GB").equals(ServerIdentity.keyFor("삼성전자 갤럭시 Z Fold8 자급제, 그라파이트, 512GB"))&&!ServerIdentity.keyFor("삼성전자 갤럭시 Z Fold8 자급제, 그라파이트, 256GB").equals(ServerIdentity.keyFor("삼성전자 갤럭시 Z Fold8 자급제, 크림, 256GB")),"storage / colour options keep different keys");
    check(ServerIdentity.keyFor("짧음").isEmpty(),"weak title -> no derived key");
    check(ServerIdentity.displayTitle("상품 1,000매 x 2, 1,000매 x 2").equals("상품 1,000매 x 2")||ServerIdentity.displayTitle("상품 1,000매 x 2, 1,000매 x 2").startsWith("상품 1,000매"),"thousands comma is not a segment separator");
    check(ServerIdentity.displayTitle("그냥 이름, 하나").equals("그냥 이름, 하나"),"nothing redundant -> unchanged");
    check(ProductUX.display("칠성사이다, 190ml × 30개, 190ml × 30개").equals("칠성사이다, 190ml × 30개"),"UI title shows the option once");

    // ---- 2. canonicalKey
    String sida2="칠성사이다, 190ml × 30개, 190ml × 30개",sida1="칠성사이다, 190ml × 30개";
    String serverKey="fcfp:157b24de0d28e7a40f2e";
    check(DealsRepository.canonicalKey(serverKey,"FALLCENT",sida2).equals(serverKey),"stable server key is used as-is");
    check(DealsRepository.canonicalKey("fc:l:d49dc9af1657e1f546ab","FALLCENT",sida1).equals(serverKey),"ephemeral fc:l: folds onto the SERVER key (title without repeated spec)");
    check(DealsRepository.canonicalKey("fc:l:48f5ad2f86445bde55e8","FALLCENT",sida2).equals(serverKey),"ephemeral fc:l: folds onto the SERVER key (title with repeated spec)");
    check(DealsRepository.canonicalKey("fcx:1234567890abcdef1234","FALLCENT","아무거나 긴 제목 입니다").equals("fcx:1234567890abcdef1234")&&DealsRepository.canonicalKey("fc:Plain0001","FALLCENT","제목").equals("fc:Plain0001"),"exact-id keys are never rewritten");
    check(DealsRepository.canonicalKey("cp:111:v:9001","COUPANG","x").equals("cp:111:v:9001"),"other sources untouched");

    // ---- 3. production-shaped payload: 1 stable row + 4 ephemeral rows per product (real titles, real prod keys)
    Context c=new Context();
    JSONArray items=new JSONArray();
    String[][] prods={{"fcfp:157b24de0d28e7a40f2e",sida2,sida1,"12700"},{"fcfp:552441592fbc62dea13e","파스퇴르 무항생제 인증 바른목장 우유, 190ml × 24개, 190ml × 24개","파스퇴르 무항생제 인증 바른목장 우유, 190ml × 24개","17640"},{"fcfp:0d27fa3654d5780da3e0","삼성전자 Galaxy Z Fold8 자급제 SM-F971N, 그라파이트, 256GB","삼성전자 Galaxy Z Fold8 자급제 SM-F971N, 그라파이트, 256GB","2073000"},{"fcfp:bd314e74d6257f8d9662","삼성전자 갤럭시 Z Fold8 자급제, 그라파이트, 512GB","삼성전자 갤럭시 Z Fold8 자급제, 그라파이트, 512GB","2303300"}};
    int n=0;
    for(String[] p:prods){
      items.put(item(p[0],p[1],Double.parseDouble(p[3]),p[0].endsWith("e")?20:2,"오늘 인기",now-60_000L,TOK));
      for(int j=0;j<4;j++)items.put(item("fc:l:"+String.format("%020x",(long)(++n*7919)*104729L*(j+1)),p[2],Double.parseDouble(p[3]),20,"오늘 인기",now-(10+j*5)*60_000L,TOK+j));
    }
    JSONObject root=new JSONObject();root.put("ok",true);root.put("items",items);
    DealsRepository.Result r=DealsRepository.applyServerPayload(c,root,"prod-shaped",now);
    check(r.error.isEmpty()&&r.parsed==20,"20 rows parsed (got "+r.parsed+")");
    check(r.persisted==4,"20 rows (4 products x 5 keys) -> 4 stored products (got "+r.persisted+")");
    HistoryStore.H sida=HistoryStore.load(c).get(serverKey);
    check(sida!=null&&sida.last==12700&&sida.lastSeen>=now-61_000L,"newest sighting of the product is the stored state");
    int cards=FeedFilter.cards(HistoryStore.load(c).values(),filter("발견","전체"),now,null).size();
    check(cards==4,"발견 shows 4 cards, not 20 (got "+cards+")");

    // ---- 4. local migration: what v2.9.5 left on a phone
    Context m=new Context();
    Map<String,HistoryStore.H> old=new LinkedHashMap<>();
    String appKeyOld="fcfp:"+"ab12cd34ef56ab12cd34";            // key produced by the OLD app fingerprint (differs from the server's)
    old.put(appKeyOld,h(appKeyOld,sida2,12700,20,now-5*60_000L,now-9*3600_000L));
    old.put("fc:l:aaaaaaaaaaaaaaaaaaaa",h("fc:l:aaaaaaaaaaaaaaaaaaaa",sida1,13100,18,now-3*3600_000L,now-3*3600_000L));
    old.put(serverKey,h(serverKey,sida2,12700,20,now-2*60_000L,now-8*3600_000L));
    String q24="fcfp:cd34ef56ab12cd34ef56";
    old.put(q24,h(q24,"칠성사이다, 190ml × 24개",9900,15,now-4*60_000L,now-2*3600_000L));
    old.put("fcx:1234567890abcdef1234",h("fcx:1234567890abcdef1234","정확 옵션 상품 테스트 제목",5000,30,now-60_000L,now-3600_000L));
    old.put("fcurl:abc123def456",h("fcurl:abc123def456","링크 기반 식별 상품 테스트",5000,30,now-60_000L,now-3600_000L));
    old.put("fcfp:eeeeeeeeeeeeeeeeeeee",h("fcfp:eeeeeeeeeeeeeeeeeeee","짧음",5000,30,now-60_000L,now-3600_000L));
    HistoryStore.save(m,old);
    m.getSharedPreferences("favorites",0).edit().putBoolean("fc:l:aaaaaaaaaaaaaaaaaaaa",true).putBoolean(q24,true).apply();
    m.getSharedPreferences("category_overrides",0).edit().putString("fc:l:aaaaaaaaaaaaaaaaaaaa","생활/주방").apply();
    HotDealStore.put(m,"fc:l:aaaaaaaaaaaaaaaaaaaa",sida1,"https://fcnt.link/x","FALLCENT",13100,0,18,null);
    String rep=DataMigration.migrateServerIdentity(m);
    Map<String,HistoryStore.H> after=HistoryStore.load(m);
    check(after.size()==5,"7 stored rows -> 5 (3 spellings of 30개 merged; 24개, exact-id, fcurl and weak-title rows kept) got "+after.size()+" :: "+rep);
    check(after.containsKey(serverKey)&&after.get(serverKey).firstSeen==now-9*3600_000L,"merged row lives under the server key and keeps the earliest firstSeen");
    check(after.get(serverKey).last==12700,"newest sighting wins (price 12700, not the 3h-old 13100)");
    check(after.containsKey(ServerIdentity.keyFor("칠성사이다, 190ml × 24개"))&&after.get(ServerIdentity.keyFor("칠성사이다, 190ml × 24개")).last==9900,"24개 option stays a separate product");
    check(after.containsKey("fcx:1234567890abcdef1234")&&after.containsKey("fcurl:abc123def456")&&after.containsKey("fcfp:eeeeeeeeeeeeeeeeeeee"),"exact-id, fcurl and weak-title keys untouched");
    check(m.getSharedPreferences("favorites",0).getBoolean(serverKey,false)&&!m.getSharedPreferences("favorites",0).getBoolean("fc:l:aaaaaaaaaaaaaaaaaaaa",false),"favorite moved from legacy key to the surviving key");
    check(m.getSharedPreferences("favorites",0).getBoolean(ServerIdentity.keyFor("칠성사이다, 190ml × 24개"),false),"favorite on the 24개 option moved to its own server key");
    check("생활/주방".equals(m.getSharedPreferences("category_overrides",0).getString(serverKey,null)),"category override moved");
    boolean hotOk=false;for(HotDealStore.Hot x:HotDealStore.all(m))if(x.key.equals(serverKey))hotOk=true;
    check(hotOk&&HotDealStore.all(m).size()==1,"hot-deal row re-keyed");
    String rep2=DataMigration.migrateServerIdentity(m);
    check(HistoryStore.load(m).size()==5&&rep2.contains("rekeyed=0"),"migration is idempotent ("+rep2+")");
    // a server sync after migration lands on the same rows (no new duplicates)
    JSONArray one=new JSONArray();one.put(item(serverKey,sida2,12400,22,"실시간 인기",now,TOK));JSONObject r1=new JSONObject();r1.put("items",one);
    DealsRepository.applyServerPayload(m,r1,"sync",now);
    check(HistoryStore.load(m).size()==5&&HistoryStore.load(m).get(serverKey).last==12400,"next sync updates the migrated row instead of adding one");
    {List<HistoryStore.H> cs=FeedFilter.cards(HistoryStore.load(m).values(),filter("발견","전체"),now,null);StringBuilder sb=new StringBuilder();for(HistoryStore.H z:cs)sb.append(z.key).append(" ");for(HistoryStore.H z:HistoryStore.load(m).values())sb.append("| vis=").append(DataQuality.visible(z)).append(":").append(z.key);check(cs.size()==5,"feed shows each stored product once ("+sb+")");}

    // ---- image memory: thumbnails are decoded near the displayed size, never below it
    check(ImageSampling.sampleSize(1000,1000,253)==2&&ImageSampling.sampleSize(500,500,253)==1&&ImageSampling.sampleSize(4000,3000,253)==8&&ImageSampling.sampleSize(300,2000,253)==1,"thumbnail sample size keeps both sides >= target");
    check(ImageSampling.bytes(1000,1000,ImageSampling.sampleSize(1000,1000,253),true)*8<ImageSampling.bytes(1000,1000,1,false)*2,"thumbnail decode is far smaller than a full ARGB_8888 decode ("+ImageSampling.bytes(1000,1000,2,true)+" vs "+ImageSampling.bytes(600,600,1,false)+" bytes before)");
    System.out.println("Identity/migration: "+pass+" passed, "+fail+" failed");if(fail>0)System.exit(1);
  }
  static FeedFilter.Params filter(String screen,String signal){FeedFilter.Params p=new FeedFilter.Params();p.screen=screen;p.signal=signal;return p;}
  static HistoryStore.H h(String key,String title,double price,int pct,long lastSeen,long firstSeen){
    HistoryStore.H x=new HistoryStore.H();x.key=key;x.title=title;x.url="https://fcnt.link/AzhTNBjuK3MFn5rOFAq2vsmfANCtXTAuqJQ74krxjADdS1aY8jdoPSJwIdlY8Jw4dXRtfpG9zZcbWeisHR4GK-JnRgtnrrJXkt55l2wSvRfF8_zUW9PZ1tVyN2d_1Hx7MiJEcfNA5GbTZjBaMTmsHImhzYFr_J7S8E4u0suCEH5A";
    x.source="FALLCENT";x.imageUrl="https://img.example/a.jpg";x.last=price;x.low=price;x.declaredDiscountPct=pct;x.lastSeen=lastSeen;x.firstSeen=firstSeen;x.signals="오늘 인기";x.origin="server";x.trusted=true;x.serverSeen=lastSeen;return x;}
}
