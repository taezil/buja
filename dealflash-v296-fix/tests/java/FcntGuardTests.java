package com.dealflash.app;
import android.content.Context;
public class FcntGuardTests {
 static int pass=0,fail=0;
 static void check(boolean v,String l){System.out.println((v?"PASS ":"FAIL ")+l);if(v)pass++;else fail++;}
 public static void main(String[] a)throws Exception{
  Context c=new Context();long now=1_800_000_000_000L;
  check(!FcntGuard.blocked(c,now),"not blocked at first");
  FcntGuard.markBlocked(c,now);
  check(FcntGuard.blocked(c,now+1)&&FcntGuard.blocked(c,now+FcntGuard.BLOCK_MS-1)&&!FcntGuard.blocked(c,now+FcntGuard.BLOCK_MS+1),"blocked for exactly the cooldown");
  check(FcntGuard.blockedMessage(c,now).contains("10분")&&FcntGuard.blockedMessage(c,now+9*60_000L).contains("1분"),"message shows the remaining minutes");
  check(FcntGuard.looksBlocked("Access denied | fcnt.link used Cloudflare to restrict access","")&&FcntGuard.looksBlocked("Just a moment...","")&&FcntGuard.looksBlocked("x","Error 1015 You are being rate limited")&&!FcntGuard.looksBlocked("편강율 키즈 앤 베이비 바디워시","쿠팡 바로가기 15,220원"),"block pages recognised, product pages are not");
  String fp="https://fallcent.com/product/g3YKiTaTjBJ9CVbLo017lfthYkQqxuNe/?from=recommend";
  check("fp:g3YKiTaTjBJ9CVbLo017lfthYkQqxuNe".equals(FcntGuard.cacheKey(fp))&&FcntGuard.cacheKey("https://fallcent.com/product/g3YKiTaTjBJ9CVbLo017lfthYkQqxuNe/").equals(FcntGuard.cacheKey(fp)),"cache key is the stable product id, query ignored");
  check(FcntGuard.cacheKey("https://fcnt.link/AzOynUZ3OlBXm3F5rju7")==null&&FcntGuard.cacheKey(null)==null&&FcntGuard.cacheKey("https://evil.test/fallcent.com/product/g3YKiTaTjBJ9CVbLo017lfthYkQqxuNe")==null,"ephemeral share links and other hosts are never cached");
  String cp="https://link.coupang.com/re/AFFSDP?lptag=x&pageKey=123";
  FcntGuard.remember(c,fp,cp,now);
  check(cp.equals(FcntGuard.recall(c,fp,now+1000))&&cp.equals(FcntGuard.recall(c,"https://fallcent.com/product/g3YKiTaTjBJ9CVbLo017lfthYkQqxuNe/",now+1000)),"a resolved Coupang address is reused for the same product");
  check(FcntGuard.recall(c,fp,now+FcntGuard.CACHE_MS+1)==null,"cache expires after 24h");
  FcntGuard.remember(c,fp,"intent://evil",now);check(cp.equals(FcntGuard.recall(c,fp,now+1000)),"non-http results are never stored");
  check(FcntGuard.recall(c,"https://fallcent.com/product/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/",now)==null,"other products are not affected");
  System.out.println("Guard: "+pass+" passed, "+fail+" failed");if(fail>0)System.exit(1);
 }
}
