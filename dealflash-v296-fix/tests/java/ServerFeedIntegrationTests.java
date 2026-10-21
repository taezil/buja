package com.dealflash.app;

import android.content.Context;
import org.json.*;
import java.nio.file.*;
import java.util.*;

/** API JSON -> ServerDealsParser -> DealsRepository -> HistoryStore (serialize/reload) -> FeedFilter. */
public class ServerFeedIntegrationTests {
  static int pass=0,fail=0;
  static void check(boolean v,String l){System.out.println((v?"PASS ":"FAIL ")+l);if(v)pass++;else fail++;}
  static FeedFilter.Params params(String screen,String signal){FeedFilter.Params p=new FeedFilter.Params();p.screen=screen;p.signal=signal;return p;}
  static int count(Context c,String screen,String signal,long now){return FeedFilter.cards(HistoryStore.load(c).values(),params(screen,signal),now,null).size();}

  public static void main(String[] a)throws Exception{
    String dir=a.length>0?a[0]:"tests/fixtures";
    long now=System.currentTimeMillis();
    // refresh timestamps so the fixture is "live" whenever it is run
    Context c=new Context();
    String raw=new String(Files.readAllBytes(Paths.get(dir,"deals_sample.json")),"UTF-8");
    JSONObject root=new JSONObject(raw);
    // Fixture times are stored as "minutes ago" so the test never rots; ts_unit=s reproduces a seconds-based server.
    JSONArray fx=root.optJSONArray("items");
    for(int i=0;i<fx.length();i++){JSONObject o=fx.optJSONObject(i);boolean sec="s".equals(o.optString("ts_unit"));
      for(String f:new String[]{"first_seen","last_seen","observed_at"}){String ak=f+"_age_min";if(!o.has(ak))continue;long t=now-(long)(o.optDouble(ak)*60000);o.put(f,sec?(Object)Long.valueOf(t/1000):(Object)Long.valueOf(t));o.remove(ak);}
      o.remove("ts_unit");}
    DealsRepository.Result r=DealsRepository.applyServerPayload(c,root,"/api/v1/deals?limit=300",now);
    check(r.error.isEmpty(),"payload accepted (items array found)");
    check(r.server==12,"serverItems=12 (got "+r.server+")");
    check(r.parsed==10,"parsed=10: valid items incl. duplicate/stale rows (got "+r.parsed+")");
    check(r.rejected==2,"rejected=2 = null price + foreign domain (got "+r.rejected+")");
    check(r.persisted==9,"persisted=9 distinct keys after dedupe (got "+r.persisted+")");
    check(r.visible==8,"visible=8 (9 stored minus 3-day-old) (got "+r.visible+")");
    SharedPrefsCheck(c);
    Map<String,HistoryStore.H> m=HistoryStore.load(c);
    check(m.get("cp:111:v:9001")!=null&&m.get("cp:111:v:9001").signals.contains("실시간 인기")&&m.get("cp:111:v:9001").signals.contains("오늘 인기"),"tracking-URL duplicate merged into ONE record, signals unioned");
    check(m.get("cp:111:v:9001").url.contains("coupang.com")&&m.get("cp:111:v:9001").last==3900,"record keeps one atomic tuple (url+price)");
    check(m.get("fcurl:abc123def456").signals.equals("실시간 인기|카테고리 급락"),"signals JSON array parsed");
    check(m.get("fc:Plain0001").signals.isEmpty()&&m.get("fc:Plain0001").imageUrl.isEmpty(),"JSON null signals/image do not become the string \"null\"");
    check(m.containsKey("fcfp:aaaaaaaaaaaaaaaaaaaa")&&m.get("fcfp:aaaaaaaaaaaaaaaaaaaa").source.equals("FALLCENT"),"lowercase source normalised; seconds timestamp -> ms");
    check(now-m.get("fcfp:aaaaaaaaaaaaaaaaaaaa").lastSeen<10*60_000L,"seconds timestamp is not treated as 1970");
    check(m.get("fcid:StableId77").declaredDiscountPct==31,"fractional last_drop_rate 0.31 -> 31%");
    check(m.containsKey("fcx:1234567890abcdef1234")&&m.containsKey("fcfp:0123456789abcdef0123")&&m.containsKey("fcurl:abc123def456")&&m.containsKey("fcid:StableId77"),"fcx/fcfp/fcurl/unknown fc* prefixes all accepted (server owns identity)");
    check(!m.containsKey("fc:NoPrice01")&&!m.containsKey("fc:Evil0001"),"missing price / foreign domain rejected");
    // feeds
    int drop=count(c,"급락","전체",now), disc=count(c,"발견","전체",now);
    check(drop==7,"급락 tab = all visible items with drop>=10 regardless of alert threshold (got "+drop+")");
    check(disc==8,"발견 전체 = every visible item (got "+disc+")");
    check(count(c,"발견","실시간 인기",now)==4,"발견/실시간 인기 = 4 (신라면, 버즈, 생수500ml, 생수1L: option variants not merged)");
    check(count(c,"발견","카테고리 급락",now)==2,"발견/카테고리 급락 = 2");
    check(count(c,"발견","지금이 타이밍",now)==3,"발견/지금이 타이밍 = 3");
    check(count(c,"발견","오늘 인기",now)==2,"발견/오늘 인기 = 2 (stale row excluded)");
    // option variants stay distinct
    HistoryStore.H w1=m.get("fcfp:aaaaaaaaaaaaaaaaaaaa"),w2=m.get("fcfp:bbbbbbbbbbbbbbbbbbbb");
    check(w1!=null&&w2!=null&&ProductLinks.group(Arrays.asList(w1,w2)).size()==2,"500ml x24 and 1L x12 remain separate cards");
    // idempotent re-sync (no duplicates / no growth)
    DealsRepository.Result r2=DealsRepository.applyServerPayload(c,root,"/api/deals",now+1000);
    check(r2.persisted==r.persisted,"second sync does not duplicate records");
    // alert threshold must not hide anything
    ScannerPrefsLike(c);
    check(count(c,"급락","전체",now)==drop,"changing alert threshold does not change the 급락 list (feed filter has no threshold)");
    // FCM-first product, then server sync with tracking-URL variant of same product
    Context c2=new Context();
    boolean ok=DealsRepository.applyPush(c2,"cp:777:v:8","LG 올레드 TV 65","https://www.coupang.com/vp/products/777?vendorItemId=8&utm_x=1","COUPANG",1490000,52,null,now);
    check(ok&&count(c2,"급락","전체",now)==1,"FCM-first product is immediately visible in 급락 (no server sync needed)");
    check(count(c2,"발견","전체",now)==1,"FCM-first product is also in 발견 전체");
    JSONObject one=new JSONObject("{\"items\":[{\"product_key\":\"cp:777:v:8\",\"product_name\":\"LG 올레드 TV 65\",\"product_url\":\"https://m.coupang.com/vp/products/777?vendorItemId=8&ref=z\",\"source\":\"COUPANG\",\"current_price\":1490000,\"last_drop_rate\":52,\"last_seen\":"+(now-1000)+",\"first_seen\":"+(now-9000)+",\"image_url\":\"https://img.test/tv.jpg\",\"signals\":\"오늘 인기\"}]}");
    DealsRepository.applyServerPayload(c2,one,"/api/deals",now);
    HistoryStore.H tv=HistoryStore.load(c2).get("cp:777:v:8");
    check(HistoryStore.load(c2).size()==1&&tv.imageUrl.equals("https://img.test/tv.jpg")&&tv.signals.equals("오늘 인기"),"server sync enriches the FCM record (image/signals) instead of duplicating");
    check(!DealsRepository.applyPush(c2,"fc:X1","x","https://evil.test/x","FALLCENT",5000,50,null,now)&&c2.getSharedPreferences("dealflash_sync_diag",0).getString("push_last_result","").equals("REJECTED"),"rejected push is recorded in diagnostics, not swallowed");
    // payload shapes
    Context c3=new Context();
    check(DealsRepository.applyServerPayload(c3,new JSONObject("{\"ok\":true,\"deals\":[]}"),"x",now).error.isEmpty(),"alt root key 'deals' recognised");
    check(!DealsRepository.applyServerPayload(c3,new JSONObject("{\"ok\":false,\"error\":\"boom\"}"),"x",now).error.isEmpty()&&c3.getSharedPreferences("dealflash_sync_diag",0).getString("deals_sync_error","").contains("items"),"payload without items surfaces an explicit error");
    check(DealsRepository.applyServerPayload(new Context(),new JSONArray("[]"),"x",now).error.isEmpty(),"bare array root accepted");
    // strict gate unchanged for local scans + superset property
    check(!DataQuality.valid("cp:1:v:2","FALLCENT","https://fcnt.link/AbC","t1x",5000,30),"strict valid() (local scan identity) still rejects mismatched identity");
    check(DataQuality.checkServer("cp:1:v:2","FALLCENT","https://fcnt.link/AbC","t1x",5000,30)==DataQuality.Reason.OK,"...while server-authoritative gate accepts server-owned identity");
    String[] ks={"fc:Stable001","cp:123:v:456","fc:l:0123456789abcdef","fcfp:abcdef0123456789abcd","fcx:abcdef0123456789abcd","fcurl:abc"};
    boolean sup=true;
    for(String k:ks){String src=k.startsWith("cp:")?"COUPANG":"FALLCENT";String url=k.startsWith("cp:")?"https://www.coupang.com/vp/products/123?itemId=4&vendorItemId=456":k.startsWith("fc:l:")?"https://fcnt.link/AbC":"https://fallcent.com/product/Stable001";String kk=ProductUrl.key(src,url);
      if(DataQuality.valid(kk,src,url,"상품 이름",5000,30)&&!DataQuality.acceptable(kk,src,url,"상품 이름",5000,30))sup=false;}
    check(sup,"everything valid() accepts, checkServer() also accepts");
    check(!DataQuality.acceptable("fc:A1","FALLCENT","https://fallcent.com/product/recommend","t1x",5000,30),"feed/category URLs still rejected");
    check(!DataQuality.acceptable("fc:A1","FALLCENT","https://fallcent.com/product/A1","OO 3,050원 ▼40%",9000,80),"embedded title/price contamination still rejected");
    // Same products re-listed in 3 consecutive scans under NEW fc:l: keys (share tokens are per scan), badge words vary.
    {
      Context q=new Context();long t0=now-20*60_000L;
      String[][] prod={{"청정원 호밍스 고칼슘 한우 어린이 곰국 6개입 (냉동), 1.8kg, 1개","9120","27"},{"맑은물에 국산콩 100% 촌두부, 550g, 1개","2830","13"},{"맑은물에 국산콩 100% 촌두부, 550g, 2개","5300","14"}};
      JSONArray arr=new JSONArray();
      for(int scan=0;scan<3;scan++)for(int i=0;i<prod.length;i++){
        String prefix=scan==1?"":(i==0?"역대급최저가 구매타이밍 로켓프레시 ":"구매타이밍 ");
        long seen=t0+scan*5*60_000L+i*200L;
        int price=Integer.parseInt(prod[i][1])-(scan==2&&i==0?500:0); // gram-soup gets cheaper in the last scan
        JSONObject o=new JSONObject();
        o.put("product_key","fc:l:"+String.format("%020x",(scan+1)*1000003L*(i+7)));
        o.put("product_name",prefix+prod[i][0]);o.put("product_url","https://fcnt.link/Tok"+scan+"x"+i+"abcdefghijklmnopqrstuvwxyz");
        o.put("source","FALLCENT");o.put("current_price",price);o.put("last_drop_rate",Integer.parseInt(prod[i][2]));
        o.put("first_seen",seen);o.put("last_seen",seen);o.put("observed_at",seen);o.put("image_url","https://img.test/"+i+".jpg");
        o.put("signals",scan==0?"실시간 인기":scan==1?"추천":"지금이 타이밍");
        arr.put(o);
      }
      JSONObject payload=new JSONObject();payload.put("items",arr);
      DealsRepository.Result y=DealsRepository.applyServerPayload(q,payload,"/api/v1/deals",now);
      check(y.parsed==9&&y.distinct==3&&y.persisted==3,"IDENTITY: 9 server rows (3 scans x 3 products, all-new keys) -> 3 stored products (got parsed="+y.parsed+" distinct="+y.distinct+" persisted="+y.persisted+")");
      Map<String,HistoryStore.H> stored=HistoryStore.load(q);
      HistoryStore.H soup=null;for(HistoryStore.H h:stored.values())if(h.title.contains("곰국"))soup=h;
      check(soup!=null&&soup.last==8620&&DealScore.points(soup.observations,now).size()==2,"IDENTITY: newest sighting wins (price 8620); real price history accumulated across scans ("+(soup==null?"?":DealScore.points(soup.observations,now).size())+" points)");
      check(soup!=null&&soup.signals.equals("실시간 인기|추천|지금이 타이밍"),"IDENTITY: signals from all sightings are stacked on one card");
      check(!soup.title.startsWith("역대급")||ProductUX.clean(soup.title).startsWith("청정원"),"IDENTITY: badge words do not leak into display title ("+ProductUX.clean(soup.title).substring(0,8)+"...)");
      int cards=FeedFilter.cards(stored.values(),params("발견","전체"),now,null).size();
      check(cards==3,"IDENTITY: 550g x1 and 550g x2 stay separate options; feed shows 3 cards (got "+cards+")");
      // The push for the NEWEST sighting uses a key the store has never seen -> must land on the same record
      String pk=DealsRepository.applyPushKey(q,"fc:l:ffffffffffffffffffff","역대급최저가 구매타이밍 로켓프레시 맑은물에 국산콩 100% 촌두부, 550g, 1개","https://fcnt.link/TokPushabcdefghijklmnopqrst","FALLCENT",2500,60,null,now);
      HistoryStore.H tofu=HistoryStore.load(q).get(pk);
      check(pk!=null&&HistoryStore.load(q).size()==3&&tofu.last==2500&&tofu.declaredDiscountPct==60,"IDENTITY: FCM with unseen fc:l: key updates the existing product (no duplicate), returns deep-link key "+pk);
    }
    // REAL production payload (verbatim /api/v1/deals items captured from the live Worker): fc:l:<20 hex> keys + fcnt.link URLs.
    Path prod=Paths.get(dir,"prod_deals.json");
    Context p=new Context();JSONObject pr=new JSONObject(new String(Files.readAllBytes(prod),"UTF-8"));
    JSONObject first=pr.optJSONArray("items").optJSONObject(0);
    long pnow=first.optLong("last_seen")+60_000L; // evaluate "one minute after capture"
    DealsRepository.Result x=DealsRepository.applyServerPayload(p,pr,"prod",pnow);
    check(x.server==5&&x.parsed==5&&x.rejected==0,"PRODUCTION: server=5 parsed=5 rejected=0 (got "+x.server+"/"+x.parsed+"/"+x.rejected+")");
    check(x.persisted==5&&x.visible==5,"PRODUCTION: persisted=5 visible=5 after save+reload (got "+x.persisted+"/"+x.visible+")");
    FeedFilter.Params pp=params("발견","전체");
    check(FeedFilter.cards(HistoryStore.load(p).values(),pp,pnow,null).size()==5,"PRODUCTION: 발견/전체 shows 5 cards");
    int dropCards=FeedFilter.cards(HistoryStore.load(p).values(),params("급락","전체"),pnow,null).size();
    check(dropCards==4,"PRODUCTION: 급락 shows the 4 items with drop>=10 (7% item is discovery-only) got "+dropCards);
    String ck0=DealsRepository.canonicalKey("fc:l:f98d93dc8536a3ec495b","FALLCENT",first.optString("product_name"));
    check(ck0.startsWith("fcfp:"),"PRODUCTION: ephemeral share-link key maps to stable fcfp: identity ("+ck0+")");
    HistoryStore.H f0=HistoryStore.load(p).get(ck0);
    check(f0!=null&&f0.last==9120&&f0.declaredDiscountPct==27&&f0.url.startsWith("https://fcnt.link/")&&f0.imageUrl.contains("coupangcdn.com")&&f0.title.contains("곰국"),"PRODUCTION: title/price/url/image of one record stay together");
    // FCM payload for the same key (FCM carries the same server key)
    Context pf=new Context();
    check(DealsRepository.applyPush(pf,"fc:l:279113c765738368f0d2",first.optString("product_name").replace("청정원 호밍스 고칼슘 한우 어린이 곰국","지엔씨푸드 타코야끼"),"https://fcnt.link/AzhTNBjuK3MFn5rOFAq2vsmfANCtXTAuqJQ74krxjADdS1aY8jdoPSJwIdlY8Jw4dXRtfpG9zZcbWeisHR4GK-JnRgtnrrJXkt55l2wSvRfF8_zUW9PZ1tVyN2d_1Hx7MiJEcfNA5GbTZjBaMTmsHImhzYFr_J7S8E4u0suCEH5A","FALLCENT",7660,55,null,pnow)
        &&FeedFilter.cards(HistoryStore.load(pf).values(),params("급락","전체"),pnow,null).size()==1,"PRODUCTION: FCM push with fc:l: key is visible in 급락 immediately");
    System.out.println("Integration: "+pass+" passed, "+fail+" failed");if(fail>0)System.exit(1);
  }
  static void SharedPrefsCheck(Context c){String s=c.getSharedPreferences("dealflash_history_v6",0).getString("items","");check(s.length()>100&&HistoryStore.lastSave().failed==0,"serialization: 0 failures, JSON written ("+s.length()+" chars)");}
  static void ScannerPrefsLike(Context c){c.getSharedPreferences("dealflash_auto_v7",0).edit().putInt("threshold",70).apply();}
}
