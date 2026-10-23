package com.dealflash.app;
import android.content.*;import android.net.Uri;import android.os.*;import android.widget.Toast;import java.util.regex.*;
public class AppLauncher{
 static final String COUPANG="com.coupang.mobile";
 public static boolean isInstalled(Context c,String p){try{c.getPackageManager().getPackageInfo(p,0);return true;}catch(Exception e){return false;}}
 public static void openProduct(Context c,String source,String raw){if("FALLCENT".equalsIgnoreCase(source)&&c instanceof android.app.Activity){openFallcentClean((android.app.Activity)c,raw);return;}
  if("FALLCENT".equals(source)|| (raw!=null&&raw.contains("fallcent.com/product/"))){resolveFallcent(c,raw);return;}
  String url=ProductUrl.canonical(source,raw);if(url==null){Toast.makeText(c,"상품 링크가 올바르지 않아.",Toast.LENGTH_SHORT).show();return;}if("COUPANG".equals(source))openCoupang(c,url);else web(c,url);
 }
 static void resolveFallcent(Context c,String u){
  if(c instanceof android.app.Activity){openFallcentClean((android.app.Activity)c,u);return;}
  Toast.makeText(c,"쿠팡 링크 확인 실패",Toast.LENGTH_SHORT).show();
 }
 static void openCoupang(Context c,String url){
if(isInstalled(c,COUPANG)){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));i.setPackage(COUPANG);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);c.startActivity(i);return;}catch(Exception ignored){}}web(c,url);}
 static void web(Context c,String u){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(u));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}catch(Exception e){Toast.makeText(c,"상품 페이지를 열 수 없어.",Toast.LENGTH_SHORT).show();}}
 public static void openUrl(Context c,String u){if(u!=null&&u.contains("fallcent.com/product/")){resolveFallcent(c,u);return;}String s=DealItem.detectSource(u);if("COUPANG".equals(s)){openProduct(c,s,u);return;}web(c,u);}
 public static void warmCoupang(Context c){if(isInstalled(c,COUPANG)){Intent i=c.getPackageManager().getLaunchIntentForPackage(COUPANG);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);return;}}web(c,"https://www.coupang.com/");}
 public static void warmToss(Context c){web(c,"https://toss.im/");}

  /**
   * 폴센트 상품페이지를 화면 밖 WebView 로 열고, 사람이 누르는 것과 똑같이 "쿠팡 바로가기" 버튼을 눌러서
   * 그 결과 이동하는 쿠팡 주소(link.coupang.com / coupang:// / intent://)를 가로채 쿠팡 앱으로 보낸다.
   * 폴센트 페이지나 fcnt.link 를 브라우저로 여는 일은 절대 하지 않는다.
   */
  static void openFallcentClean(final android.app.Activity a,final String input){
    if(a==null||input==null||input.isEmpty())return;
    a.runOnUiThread(()->{
      Toast.makeText(a,"쿠팡으로 연결 중…",Toast.LENGTH_SHORT).show();
      final android.util.DisplayMetrics dm=a.getResources().getDisplayMetrics();
      final android.view.ViewGroup root=(android.view.ViewGroup)a.getWindow().getDecorView();
      final java.util.ArrayList<android.webkit.WebView> views=new java.util.ArrayList<>();
      final boolean[] done={false};
      final long[] lastClick={0};final int[] clicks={0},tries={0};final String[] pageLine={"PAGE ?"},lastNav={""};
      final StringBuilder trace=new StringBuilder("INPUT ").append(input);
      final android.os.Handler h=new android.os.Handler(android.os.Looper.getMainLooper());

      class R{
        void log(String m){android.util.Log.d("FC_TRACE",m);if(trace.length()<4000)trace.append("\n").append(m);}
        android.webkit.WebView make(boolean attach){
          android.webkit.WebView w=new android.webkit.WebView(a);
          android.webkit.WebSettings st=w.getSettings();
          st.setJavaScriptEnabled(true);st.setDomStorageEnabled(true);
          st.setSupportMultipleWindows(true);st.setJavaScriptCanOpenWindowsAutomatically(true);
          st.setUserAgentString(android.webkit.WebSettings.getDefaultUserAgent(a));
          android.webkit.CookieManager.getInstance().setAcceptCookie(true);
          android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(w,true);
          if(attach){ // 화면 밖에 실제 크기로 붙여야 레이아웃/JS 가 정상 동작한다.
            w.setTranslationX(-dm.widthPixels*2f);
            root.addView(w,new android.widget.FrameLayout.LayoutParams(dm.widthPixels,dm.heightPixels));
          }
          views.add(w);
          w.setWebViewClient(client());
          w.setWebChromeClient(new android.webkit.WebChromeClient(){
            @Override public boolean onCreateWindow(android.webkit.WebView v,boolean d,boolean g,android.os.Message msg){
              android.webkit.WebView child=make(false);
              ((android.webkit.WebView.WebViewTransport)msg.obj).setWebView(child);msg.sendToTarget();return true;}
          });
          return w;
        }
        String norm(String z){
          if(z==null)return null;
          try{
            if(z.startsWith("intent://")){
              android.content.Intent ii=android.content.Intent.parseUri(z,android.content.Intent.URI_INTENT_SCHEME);
              android.net.Uri d=ii.getData();String fb=ii.getStringExtra("browser_fallback_url");
              if(d!=null&&d.getScheme()!=null&&(d.getScheme().startsWith("http")||d.getScheme().equals("coupang")))z=d.toString();
              else if(fb!=null)z=fb;
            }
            android.net.Uri q=android.net.Uri.parse(z);String hh=q.getHost();
            if(hh!=null&&(hh.equals("api.adjoin.co.kr")||hh.equals("api.ootoo.co.kr"))){String land=q.getQueryParameter("land");if(land!=null&&!land.isEmpty())z=land;}
          }catch(Exception e){log("NORM_ERR "+e.getClass().getSimpleName());}
          return z;
        }
        boolean coup(String z){
          z=norm(z);if(z==null)return false;if(z.startsWith("coupang://"))return true;
          try{String hh=android.net.Uri.parse(z).getHost();if(hh==null)return false;hh=hh.toLowerCase(java.util.Locale.ROOT);
            return hh.equals("link.coupang.com")||hh.equals("coupang.com")||hh.endsWith(".coupang.com");}catch(Exception e){return false;}
        }
        void cleanup(){
          h.removeCallbacksAndMessages(null);
          for(android.webkit.WebView w:views){try{w.stopLoading();}catch(Exception ignored){}try{root.removeView(w);}catch(Exception ignored){}try{w.destroy();}catch(Exception ignored){}}
          views.clear();
        }
        void launch(String q){
          log("LAUNCH "+q);
          try{
            android.net.Uri u=android.net.Uri.parse(q);
            Intent i=new Intent(Intent.ACTION_VIEW,u);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if(isInstalled(a,COUPANG)){i.setPackage(COUPANG);try{a.startActivity(i);return;}catch(Exception e1){i.setPackage(null);}}
            a.startActivity(i);
          }catch(Exception e){Toast.makeText(a,"쿠팡을 열 수 없어.",Toast.LENGTH_SHORT).show();}
        }
        /** 쿠팡 주소를 잡으면 즉시 종료하고 쿠팡 앱으로 넘긴다. */
        boolean follow(String z){
          if(done[0]||z==null||z.isEmpty())return false;
          String q=norm(z);
          if(coup(q)){done[0]=true;launch(q);h.post(()->cleanup());return true;}
          return false;
        }
        android.webkit.WebViewClient client(){
          return new android.webkit.WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,android.webkit.WebResourceRequest req){
              String z=req.getUrl().toString();if(!z.equals(lastNav[0])){lastNav[0]=z;log("NAV "+z);}
              if(follow(z))return true;
              String sc=req.getUrl().getScheme();
              return sc!=null&&!sc.startsWith("http"); // intent:// 등 알 수 없는 스킴은 WebView 가 에러 페이지를 띄우지 않게 막는다
            }
            @Override public void onPageStarted(android.webkit.WebView v,String z,android.graphics.Bitmap b){log("START "+z);follow(z);}
            @Override public void doUpdateVisitedHistory(android.webkit.WebView v,String z,boolean reload){follow(z);}
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView v,android.webkit.WebResourceRequest req){
              if(req.isForMainFrame()){final String z=req.getUrl().toString();if(coup(z))h.post(()->follow(z));}
              return null;
            }
            @Override public void onReceivedError(android.webkit.WebView v,android.webkit.WebResourceRequest req,android.webkit.WebResourceError e){
              if(req.isForMainFrame())log("WEB_ERR "+e.getErrorCode()+" "+req.getUrl());}
          };
        }
        String js(boolean click){
          return "(function(doClick){try{"
           +"var all=[].slice.call(document.querySelectorAll('a,button,[role=button],div,span,p'));"
           +"var c=all.filter(function(e){var t=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim();return t.indexOf('쿠팡 바로가기')===0&&t.length<30;});"
           +"if(!c.length)return 'NOTFOUND';"
           +"var el=c.filter(function(e){return !c.some(function(o){return o!==e&&e.contains(o);});})[0]||c[0];"
           +"var n=el;for(var i=0;i<8&&n;i++,n=n.parentElement){if(n.tagName==='A'||n.tagName==='BUTTON'||n.getAttribute('role')==='button'||n.getAttribute('onclick'))break;}"
           +"if(!n)n=el;"
           +"var hf=(n.tagName==='A')?(n.href||''):'';"
           +"if(/^https?:\\/\\/[^\\/]*coupang\\.com/.test(hf))return 'HREF:'+hf;"
           +"if(!doClick)return 'FOUND';"
           +"try{n.scrollIntoView({block:'center'});}catch(e){}"
           +"['mousedown','mouseup'].forEach(function(t){try{n.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window}));}catch(e){}});"
           +"n.click();return 'CLICKED '+(hf||n.tagName);"
           +"}catch(e){return 'ERR '+e;}})("+click+")";
        }
        void poll(final android.webkit.WebView w){
          if(done[0])return;
          if(tries[0]++>=36){fail();return;}
          final long now=System.currentTimeMillis();
          final boolean click=clicks[0]<4&&now-lastClick[0]>1500;
          w.evaluateJavascript(js(click),raw->{
            if(done[0])return;
            String r=raw==null?"":raw;
            try{Object o=new org.json.JSONTokener(r).nextValue();r=o==null?"":o.toString();}catch(Exception ignore){}
            if(!r.equals("NOTFOUND"))log("JS "+r);
            if(r.startsWith("HREF:")){
              String u=r.substring(5);
              follow(u); // 쿠팡 주소면 바로 쿠팡으로. 그 외 링크(fcnt.link 등)는 직접 열지 않고 클릭 흐름으로만 처리
            }else if(r.startsWith("CLICKED")){clicks[0]++;lastClick[0]=System.currentTimeMillis();}
            if(!done[0])h.postDelayed(()->poll(w),500);
          });
        }
        /** On timeout: ask the page what it is (URL, title, first text - e.g. a Cloudflare "Just a moment..." check) before tearing it down, then show the trace with that line FIRST. */
        void fail(){
          if(done[0])return;done[0]=true;log("TIMEOUT");
          final boolean[] shown={false};
          final Runnable show=()->{
            if(shown[0])return;shown[0]=true;cleanup();
            String tail=trace.length()>1300?trace.substring(trace.length()-1300):trace.toString();
            new android.app.AlertDialog.Builder(a).setTitle("쿠팡 바로가기를 못 눌렀어").setMessage(pageLine[0]+"\n\n"+tail)
              .setPositiveButton("확인",null)
              .setNeutralButton("폴센트에서 열기",(d,wh)->web(a,input)).show();
          };
          final android.webkit.WebView w=views.isEmpty()?null:views.get(0);
          if(w==null){show.run();return;}
          try{
            w.evaluateJavascript("(function(){try{var b=(document.body&&document.body.innerText)||'';return location.href.slice(0,80)+' | '+(document.title||'')+' | '+b.replace(/\\s+/g,' ').slice(0,140);}catch(e){return 'ERR '+e;}})()",raw->{
              pageLine[0]="PAGE "+(raw==null?"":raw);show.run();});
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(show,1500);
          }catch(Exception e){pageLine[0]="PAGE ERR "+e;show.run();}
        }
      }

      final R r=new R();
      final android.webkit.WebView main=r.make(true);
      main.loadUrl(input);
      h.postDelayed(()->r.poll(main),800);
    });
  }
}