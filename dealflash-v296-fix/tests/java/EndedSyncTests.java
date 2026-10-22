package com.dealflash.app;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.*;
import java.util.*;
public class EndedSyncTests {
 static int pass=0,fail=0;
 static void check(boolean v,String l){System.out.println((v?"PASS ":"FAIL ")+l);if(v)pass++;else fail++;}
 static HistoryStore.H row(String key,long seen,int pct){HistoryStore.H h=new HistoryStore.H();h.key=key;h.title="테스트상품 "+key+" 500ml × 2개";h.url="https://fcnt.link/x";h.source="FALLCENT";h.last=10000;h.declaredDiscountPct=pct;h.firstSeen=seen;h.lastSeen=seen;h.serverSeen=seen;h.imageUrl="";h.trusted=true;return h;}
 static FeedFilter.Params params(String screen){FeedFilter.Params p=new FeedFilter.Params();p.screen=screen;return p;}
 static String k(int i){return String.format("fcfp:%020d",i);}
 public static void main(String[] a)throws Exception{
  long now=System.currentTimeMillis();
  // ---- request batching: only live rows the payload did not just confirm, newest first, 400 per request
  Map<String,HistoryStore.H> local=new HashMap<>();
  for(int i=1;i<=500;i++)local.put(k(i),row(k(i),now-i*1000L,40));
  local.get(k(7)).endedAt=now-5000;                       // already ended -> not asked again
  Set<String> live=new HashSet<>(Arrays.asList(k(1),k(2)));// confirmed by this payload -> not asked
  List<List<String>> bs=EndedSync.batches(local,live);
  int total=0;for(List<String> b:bs)total+=b.size();
  check(bs.size()==2&&bs.get(0).size()==EndedSync.BATCH&&total==497,"500 local rows -> 2 requests (400+97), skipping ended and confirmed-live rows (got "+bs.size()+" batches, "+total+" keys)");
  check(bs.get(0).get(0).equals(k(3)),"newest rows are asked first");
  check(EndedSync.batches(new HashMap<>(),null).isEmpty(),"empty store -> no request");
  // ---- parsing
  JSONObject resp=new JSONObject("{\"ok\":true,\"ended\":[{\"product_key\":\"fcfp:00000000000000000009\",\"ended_at\":123},{\"product_key\":\"\"},{\"x\":1}]}");
  List<EndedSync.Ended> pe=EndedSync.parse(resp);
  check(pe.size()==1&&pe.get(0).key.endsWith("9")&&pe.get(0).at==123,"parse keeps valid entries only");
  check(EndedSync.parse(new JSONObject("{\"ok\":false}")).isEmpty()&&EndedSync.parse(null).isEmpty(),"old server / bad body -> nothing ended");
  // ---- mass ending: the server reports 3,700 ended products, the app holds 500 of them locally -> every local one is marked, foreign keys ignored
  Context c=new Context();Map<String,HistoryStore.H> m=new HashMap<>();
  for(int i=1;i<=500;i++)m.put(k(i),row(k(i),now-i*1000L,40));
  HistoryStore.save(c,m);
  ArrayList<EndedSync.Ended> mass=new ArrayList<>();for(int i=1;i<=3700;i++)mass.add(new EndedSync.Ended(k(i),now-1000));
  int marked=EndedSync.apply(c,mass,key->false,now);
  Map<String,HistoryStore.H> back=HistoryStore.load(c);int ended=0;for(HistoryStore.H h:back.values())if(h.endedAt>0)ended++;
  check(marked==500&&ended==500&&back.size()==500,"3,700 server-side endings: all 500 local rows marked, none lost, nothing foreign added (marked="+marked+" ended="+ended+" size="+back.size()+")");
  check(EndedSync.apply(c,mass,key->false,now)==0,"idempotent: a repeat marks nothing new");
  // ---- visibility: hidden everywhere but 보관
  Map<String,HistoryStore.H> v=new HashMap<>();HistoryStore.H live1=row(k(1),now-1000,60),dead=row(k(2),now-1000,60);dead.endedAt=now-500;v.put(live1.key,live1);v.put(dead.key,dead);
  for(String screen:new String[]{"급락","발견","알림"})check(FeedFilter.eligible(v.values(),params(screen),now).size()==1,"ended row hidden on "+screen);
  FeedFilter.Params sp=params("발견");sp.query="테스트상품";check(FeedFilter.eligible(v.values(),sp,now).size()==1,"ended row hidden from search");
  FeedFilter.Params kp=params("보관");kp.favorite=key->true;check(FeedFilter.eligible(v.values(),kp,now).size()==2,"favourites stay in 보관 even when ended");
  kp.favorite=key->key.equals(k(2));List<HistoryStore.H> kept=FeedFilter.eligible(v.values(),kp,now);check(kept.size()==1&&kept.get(0).key.equals(k(2)),"보관 shows only the favourite (the ended one)");
  // ---- prune: ended non-favourites go after a day, favourites are never deleted
  Context c2=new Context();Map<String,HistoryStore.H> pm=new HashMap<>();
  for(int i=1;i<=4;i++)pm.put(k(i),row(k(i),now-3*3600000L,40));
  pm.get(k(1)).endedAt=now-2*EndedSync.PRUNE_MS;pm.get(k(2)).endedAt=now-2*EndedSync.PRUNE_MS;pm.get(k(3)).endedAt=now-3600000L;
  HistoryStore.save(c2,pm);
  EndedSync.apply(c2,new ArrayList<>(),key->key.equals(k(2)),now);
  Map<String,HistoryStore.H> after=HistoryStore.load(c2);
  check(!after.containsKey(k(1))&&after.containsKey(k(2))&&after.containsKey(k(3))&&after.containsKey(k(4)),"old ended non-favourite pruned; favourite, recently ended and live rows kept");
  // ---- reactivation: the product shows up in the server feed again -> live again, same row
  Path prod=Paths.get(a[0],"prod_deals.json");
  Context c3=new Context();JSONObject pr=new JSONObject(new String(Files.readAllBytes(prod),"UTF-8"));
  long pnow=pr.optJSONArray("items").optJSONObject(0).optLong("last_seen")+60_000L;
  DealsRepository.Result r1=DealsRepository.applyServerPayload(c3,pr,"prod",pnow);
  String key=r1.activeKeys.iterator().next();
  check(r1.activeKeys.size()==5,"payload result exposes the 5 live keys");
  EndedSync.apply(c3,Arrays.asList(new EndedSync.Ended(key,pnow)),key2->false,pnow);
  check(HistoryStore.load(c3).get(key).endedAt>0,"marked ended");
  int size=HistoryStore.load(c3).size();
  DealsRepository.applyServerPayload(c3,pr,"prod",pnow+60000);
  check(HistoryStore.load(c3).get(key).endedAt==0&&HistoryStore.load(c3).size()==size,"a product that is back in the server feed is live again under the same key (no duplicate row)");
  EndedSync.apply(c3,Arrays.asList(new EndedSync.Ended(key,pnow)),key2->false,pnow);
  String pk=DealsRepository.applyPushKey(c3,key,"역대급최저가 구매타이밍 로켓프레시 맑은물에 국산콩 100% 촌두부, 550g, 1개","https://fcnt.link/TokPushabcdefghijklmnopqrst","FALLCENT",2500,60,null,pnow+120000);
  check(pk!=null&&HistoryStore.load(c3).get(pk).endedAt==0,"a push for an ended product makes it live again");
  // ---- hot list: ended products lose their 급락 pin
  Context c4=new Context();HotDealStore.put(c4,k(1),"테스트상품 500ml × 2개","https://fcnt.link/x","FALLCENT",5000,10000,50,null);
  Map<String,HistoryStore.H> hm=new HashMap<>();hm.put(k(1),row(k(1),now,50));HistoryStore.save(c4,hm);
  EndedSync.apply(c4,Arrays.asList(new EndedSync.Ended(k(1),now+1000)),key3->false,now+1000);
  check(HotDealStore.all(c4).isEmpty(),"hot-list pin removed when the deal ended");
  System.out.println("Ended: "+pass+" passed, "+fail+" failed");if(fail>0)System.exit(1);
 }
}
