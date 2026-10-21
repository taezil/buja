package com.dealflash.app;
import java.util.*;
public final class ProductUX{
 private ProductUX(){}
 public static String clean(String s){
  if(s==null)return "";
  String x=ListingIntegrity.cleanTitle(s).replaceAll("[🔥🚀⚡💥⭐★]+"," ").trim();
  String promotion="^(?:역대급\\s*최저가|역대급|구매\\s*타이밍|최저가\\s*타이밍|타이밍|오늘의\\s*특가|초특가|특가|핫딜|추천상품|득템|놓치면\\s*후회)(?=\\s|$)\\s*";
  String previous;
  do {previous=x;x=x.replaceFirst(promotion,"").trim();} while(!previous.equals(x));
  x=x.replaceAll("(?:\\[)?(?:판매자\\s*로켓|로켓\\s*배송|로켓프레시|로켓직구)(?:\\])?(?=\\s|$)"," ");
  x=x.replaceAll("(?i)([0-9]+(?:\\.[0-9]+)?\\s*(?:ml|kg|g|l))\\s*[, ]+\\s*([0-9]+개)","$1 × $2").replaceAll("\\s+"," ").trim();
  return x.isEmpty()?s.trim():x;
 }

 /** Title for the screen: promo/badge text removed and repeated option/quantity text shown once. Never used as identity. */
 public static String display(String s){return ServerIdentity.displayTitle(clean(s));}

 /** Weighted keyword classifier. Specific product words beat broad category words. */
 public static String category(String s){
  String x=clean(s==null?"":s).toLowerCase(Locale.KOREA);
  LinkedHashMap<String,Integer> score=new LinkedHashMap<>();Map<String,Integer> longest=new HashMap<>();
  for(String c:new String[]{"식품","생활/주방","가전/디지털","패션/의류","뷰티","육아/유아","스포츠/레저","자동차","반려동물","가구/인테리어","건강"})score.put(c,0);

  add(score,longest,"반려동물",x,7,"강아지","고양이","반려견","반려묘","애견","캣","펫");
  add(score,longest,"반려동물",x,4,"사료","배변패드","츄르","고양이모래","하네스");
  // "강아지 닭고기 간식" is pet food: an explicit pet word outranks the human-food words in the rest of the title.
  if(score.get("반려동물")>=7)score.put("반려동물",score.get("반려동물")+8);

  add(score,longest,"육아/유아",x,7,"기저귀","분유","젖병","유모차","카시트","아기","유아","베이비","출산");
  add(score,longest,"건강",x,7,"비타민","멀티비타민","영양제","유산균","오메가","루테인","프로바이오틱","밀크씨슬","마그네슘","콜라겐");
  add(score,longest,"건강",x,4,"단백질보충","프로틴","건강기능","보충제");

  add(score,longest,"가전/디지털",x,7,"노트북","모니터","스마트폰","아이폰","갤럭시","아이패드","태블릿","이어폰","헤드폰","키보드","마우스","ssd","공유기","그래픽카드","프린터");
  add(score,longest,"가전/디지털",x,7,"냉장고","세탁기","건조기","에어컨","청소기","전자레인지","에어프라이어","전기포트","커피머신","선풍기","제습기","가습기","공기청정기");
  add(score,longest,"가전/디지털",x,4,"충전기","보조배터리","케이블","멀티탭","스피커","카메라","렌즈","tv","티비");

  add(score,longest,"생활/주방",x,8,"텀블러","밀폐용기","프라이팬","후라이팬","냄비","도마","칼세트","식기","그릇","접시","수저","컵","머그","보온병","주전자");
  add(score,longest,"생활/주방",x,5,"세제","휴지","물티슈","수건","청소포","빨래","주방","욕실","생활용품","수납함","옷걸이");

  add(score,longest,"식품",x,7,"라면","과자","쌀","김치","만두","우유","치즈","요구르트","아이스크림","두유","생수","음료","커피","주스","고기","돼지고기","소고기","닭가슴살","참치","과일","견과","소스","햇반","즉석밥");
  add(score,longest,"식품",x,4,"식품","간식","초콜릿","캔디","빵","시리얼","차 ");

  add(score,longest,"패션/의류",x,7,"티셔츠","셔츠","바지","팬츠","자켓","재킷","코트","원피스","속옷","패딩","양말","후드","니트","청바지","레깅스");
  add(score,longest,"패션/의류",x,6,"운동화","스니커즈","구두","샌들","슬리퍼","가방","백팩","지갑","모자");

  add(score,longest,"뷰티",x,7,"샴푸","트리트먼트","화장품","크림","로션","세럼","앰플","선크림","마스크팩","향수","클렌징","립밤","틴트","쿠션","파운데이션");
  add(score,longest,"스포츠/레저",x,7,"캠핑","골프","러닝","등산","자전거","낚시","텐트","침낭","덤벨","바벨","헬스");
  add(score,longest,"자동차",x,7,"자동차","차량","와이퍼","세차","엔진오일","타이어","카매트","블랙박스","차량용");
  add(score,longest,"가구/인테리어",x,7,"침대","의자","책상","소파","매트리스","가구","서랍장","테이블","행거","인테리어","스탠드조명");

  // Highest score wins; on a tie the category whose matched keyword is longer (more specific: 커피머신 > 커피) wins.
  String best="기타";int max=0,bestLen=0;
  for(Map.Entry<String,Integer> e:score.entrySet()){int len=longest.getOrDefault(e.getKey(),0);if(e.getValue()>max||(e.getValue()==max&&max>0&&len>bestLen)){max=e.getValue();best=e.getKey();bestLen=len;}}
  return max>=4?best:"기타";
 }
 private static void add(Map<String,Integer> score,Map<String,Integer> longest,String cat,String x,int weight,String... words){int n=0;for(String w:words)if(x.contains(w)){n++;longest.merge(cat,w.length(),Math::max);}if(n>0)score.put(cat,score.get(cat)+weight+n-1);}
 public static String grade(int drop,int score){int q=score;return q>=95?"S+":q>=88?"S":q>=80?"A":q>=70?"B":"C";}
 public static String verdict(int drop,int score){String g=grade(drop,score);return ("S+".equals(g)||"S".equals(g))?"지금 확인할 가치가 높은 딜":"A".equals(g)?"가격 메리트가 큰 편":"B".equals(g)?"조건 확인 후 구매":"평소 가격과 비교 권장";}
}
