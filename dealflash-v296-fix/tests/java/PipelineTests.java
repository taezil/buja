package com.dealflash.app;
import android.content.Context;
import java.util.*;
public class PipelineTests {
 static int pass=0,fail=0;
 static void check(boolean value,String label){System.out.println((value?"PASS ":"FAIL ")+label);if(value)pass++;else fail++;}
 static String url="https://fallcent.com/product/Stable001";
 static String K="";   // stored identity of the product: since v2.8 a FALLCENT product is keyed by its title identity, not by the transport key
 static PageScanner.Listing item(double price,int pct){PageScanner.Listing x=new PageScanner.Listing();x.key="fc:Stable001";x.url=url;x.title="OO 생수 2L 12개";x.imageUrl="https://img.test/water.jpg";x.price=price;x.declaredDiscountPct=pct;return x;}
 public static void main(String[] args)throws Exception{
  Context c=new Context();Map<String,HistoryStore.H> h=new HashMap<>();long now=System.currentTimeMillis();
  DealPipeline.Result first=DealPipeline.accept(c,h,item(10000,80),"FALLCENT",50,0,now);K=first.key;
  check(K.startsWith("fcfp:")&&K.equals(ServerIdentity.keyFor("OO 생수 2L 12개")),"on-device scan stores the product under the SAME key the server would assign");
  check(first.accepted&&first.notify,"new candidate is retained and notified honestly");
  check(first.evidence.score<40&&first.evidence.reference==0,"80% label cannot fabricate history or high score");
  check(DealUrgency.score(h.get(K),first.evidence,now)>=80&&first.evidence.confidence.equals("데이터 부족"),"urgent new deal stays visible while confidence remains separate");
  long found=HotDealStore.all(c).get(0).foundAt,verified=HotDealStore.all(c).get(0).lastVerifiedAt;
  Thread.sleep(10);
  DealPipeline.Result repeat=DealPipeline.accept(c,h,item(10000,80),"FALLCENT",50,0,now+10000);
  check(!repeat.notify,"same price no repeat notification");
  check(HotDealStore.all(c).get(0).foundAt==found&&HotDealStore.all(c).get(0).lastVerifiedAt>verified,"unchanged scan updates verifiedAt, preserves foundAt");
  DealPipeline.Result badge=DealPipeline.accept(c,h,item(10000,90),"FALLCENT",50,0,now+20000);
  check(!badge.notify,"badge-only change is not price movement");
  check(DealScore.points(h.get(K).observations,now+20000).size()==1,"10-second polls do not inflate observations");
  PageScanner.Listing bad=item(9000,80);bad.key="fc:Other001";
  check(!DealPipeline.accept(c,h,bad,"FALLCENT",50,0,now).accepted,"identity mismatch rejected before history and hot store");
  bad=item(Double.NaN,80);check(!DealPipeline.accept(c,h,bad,"FALLCENT",50,0,now).accepted,"NaN fails closed");
  bad=item(9000,80);bad.title="OO 3,050원 ▼40%";check(!DealPipeline.accept(c,h,bad,"FALLCENT",50,0,now).accepted,"embedded price-title contamination rejected");
  bad=item(9000,80);bad.url="https://evil.test/product/Stable001";check(!DealPipeline.accept(c,h,bad,"FALLCENT",50,0,now).accepted,"wrong origin rejected");
  check(h.size()==1&&h.get(K).last==10000,"rejected data never mutates stored item");
  DealPipeline.accept(c,h,item(10000,0),"FALLCENT",50,0,now+30000);check(HotDealStore.all(c).isEmpty(),"expired discount removed from hot list");
  DealPipeline.accept(c,h,item(9000,60),"FALLCENT",50,0,now+40000);
  check(HotDealStore.all(c).get(0).foundAt==found,"requalifying product still retains original discovery time");
  HistoryStore.H hist=new HistoryStore.H();hist.last=100000;hist.lastSeen=now-10000;
  // DealScore.observe() keeps an unchanged price only every 6h (HISTORY_SAMPLE), so a dense 30-hour record is written directly.
  StringBuilder dense=new StringBuilder();for(int i=0;i<30;i++)dense.append(now-(30-i)*60*60*1000L).append(":100000.0;");hist.observations=dense.toString();
  DealScore.Evidence e=DealScore.evaluate(hist,10000,0,now);
  check(e.score>=88&&e.errorCandidate&&e.confidence.equals("신뢰도 높음"),"real observed 90% drop can qualify as strong error candidate");
  check(e.low==100000&&e.average==100000&&e.saving==90000,"prior low, average, saving exclude current price");
  HistoryStore.H sparse=new HistoryStore.H();DealScore.observe(sparse,100000,now-10000);sparse.last=100000;sparse.lastSeen=now-10000;
  check(DealScore.evaluate(sparse,1000,99,now).score<=39,"single high outlier cannot earn high grade");
  check(ProductUX.grade(99,0).equals("C"),"grade no longer derived from displayed discount");
  String a="https://www.coupang.com/vp/products/123?itemId=4&vendorItemId=5&utm_source=x";
  String b="https://m.coupang.com/vp/products/123?vendorItemId=5&itemId=4&ref=abc#frag";
  check(ProductUrl.canonicalCoupang(a).equals(ProductUrl.canonicalCoupang(b)),"canonical URL ignores tracking, order and fragment");
  check(!ProductUrl.key("COUPANG",a).equals(ProductUrl.key("COUPANG",a.replace("vendorItemId=5","vendorItemId=6"))),"different variants remain separate");
  check(ProductUrl.canonicalCoupang(a+"&vendorItemId=99")==null,"conflicting duplicate variant query rejected");
  check(ProductUrl.canonicalCoupang(a.replace("vendorItemId=5","vendorItemId=5xyz"))==null,"malformed variant cannot alias a real ID");
  check(ProductUrl.canonical("FALLCENT","https://fallcent.com/product/recommend")==null,"feed URL cannot become product");
  check(ProductUX.category("강아지 닭고기 간식").equals("반려동물"),"pet food is not human food");
  check(ProductUX.clean("역대급 타이밍 판매자로켓 OO 생수 2L 12개").equals("OO 생수 2L × 12개"),"marketing removed, quantity preserved");
  check(ProductUX.clean("자동차 타이밍벨트 (정품)").equals("자동차 타이밍벨트 (정품)"),"real product descriptors are preserved");
  check(ProductUX.category("전자동 커피머신").equals("가전/디지털"),"coffee machine is appliance");
  check(ProductUX.category("바닐라 아이스크림").equals("식품"),"ice cream is not beauty cream");
  String mixed="<li class='search-product'><a href='https://www.coupang.com/vp/products/1'>상품 A</a><a href='https://www.coupang.com/vp/products/2'>상품 B</a><strong class='price-value'>1000</strong></li>";
  check(PageScanner.scan(mixed,"https://www.coupang.com","COUPANG").isEmpty(),"mixed product HTML container cannot supply a price");
  HistoryStore.save(c,h);check(!HistoryStore.load(c).get(K).observations.isEmpty(),"observation history survives persistence");
  ListingIntegrity.Rec polluted=new ListingIntegrity.Rec("fc:X","bad","a",100,50,200,50,false),good=new ListingIntegrity.Rec("fc:X","good","b",200,50,200,50,false);
  check(ListingIntegrity.filter(Arrays.asList(polluted,good),new ListingIntegrity.Report()).get(0)==good,"bad first duplicate does not suppress a valid later record");
  HistoryStore.H fc=new HistoryStore.H();fc.source="FALLCENT";fc.key="fc:Linked01";fc.linkedCoupangKey="cp:123:v:5";fc.firstSeen=10;fc.lastSeen=30;fc.title="FC";fc.last=100;
  HistoryStore.H cp=new HistoryStore.H();cp.source="COUPANG";cp.key="cp:123:v:5";cp.firstSeen=20;cp.lastSeen=40;cp.title="CP";cp.last=200;
  java.util.List<HistoryStore.H> linked=ProductLinks.group(Arrays.asList(fc,cp));
  check(linked.size()==1&&linked.get(0)==fc&&fc.title.equals("FC")&&fc.last==100&&fc.firstSeen==10&&fc.sources.contains("+"),"explicit cross-source link keeps ONE whole record (best discount, then lowest price, then newest), never a mix of two, and preserves sources/first discovery");
  Map<String,HistoryStore.H> marketMap=new HashMap<>();fc.trusted=true;fc.url="https://fallcent.com/product/Linked01";fc.title="Linked";fc.declaredDiscountPct=20;cp.trusted=true;cp.url="https://www.coupang.com/vp/products/123?vendorItemId=5";cp.title="Linked";cp.declaredDiscountPct=20;marketMap.put(fc.key,fc);marketMap.put(cp.key,cp);check(MarketComparison.offers(marketMap,cp).size()==2,"market comparison only uses explicit exact-option cross-source links");
  fc.linkedCoupangKey="";check(ProductLinks.group(Arrays.asList(fc,cp)).size()==2,"no cross-source merge without explicit proof");
  check(ProductLinks.exactOptionKey("https://www.coupang.com/vp/products/123").isEmpty(),"product-only outbound link cannot merge unknown options");
  HistoryStore.H c1=new HistoryStore.H();c1.source="FALLCENT";c1.key="fcfp:c1";c1.title="AOKOWN 지압슬리퍼 다이어트 붓기 건강 실내 사무실 슬리퍼 다크 그레이";c1.last=3300;c1.declaredDiscountPct=61;c1.lastSeen=now;
  HistoryStore.H c2=new HistoryStore.H();c2.source="FALLCENT";c2.key="fcfp:c2";c2.title="AOKOWN 지압슬리퍼 다이어트 붓기 건강 실내 사무실 슬리퍼 라이트 그레이";c2.last=3500;c2.declaredDiscountPct=55;c2.lastSeen=now;
  check(ProductLinks.group(Arrays.asList(c1,c2)).size()==1,"cosmetic colour variants collapse to one feed family card");
  HistoryStore.H q1=new HistoryStore.H();q1.source="FALLCENT";q1.key="fcfp:q1";q1.title="생수 2L 6개";q1.last=3000;
  HistoryStore.H q2=new HistoryStore.H();q2.source="FALLCENT";q2.key="fcfp:q2";q2.title="생수 2L 12개";q2.last=5000;
  check(ProductLinks.group(Arrays.asList(q1,q2)).size()==2,"quantity variants remain separate feed cards");
  HistoryStore.H fr1=new HistoryStore.H();fr1.lastSeen=now-5*60*1000L;HistoryStore.H st1=new HistoryStore.H();st1.lastSeen=now-31*60*1000L;HistoryStore.H un=new HistoryStore.H();
  check(!FeedFilter.isPriceStale(fr1,now)&&FeedFilter.isPriceStale(st1,now)&&!FeedFilter.isPriceStale(un,now),"price older than 30 min is flagged possibly ended; unknown time is not");
  java.util.List<HistoryStore.H> ord=new java.util.ArrayList<>(Arrays.asList(st1,fr1));ord.sort((o1,o2)->FeedFilter.freshFirst(o1,o2,now));check(ord.get(0)==fr1,"freshly confirmed prices sort before possibly-ended ones");
  System.out.println("Pipeline: "+pass+" passed, "+fail+" failed");if(fail>0)System.exit(1);
 }
}
