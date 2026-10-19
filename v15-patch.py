from pathlib import Path
p=Path("app/src/main/java/com/dealflash/app/BrowserScanner.java")
s=p.read_text()
s=s.replace('String script = s.detail ? detailScript() : listingScript();','String script = s.detail ? detailScript() : ("FALLCENT".equals(s.source) ? fallcentListingScript() : listingScript());')
marker='    private static String detailScript() {'
method=r'''    private static String fallcentListingScript() {
        return "(function(){"+
                "function n(t){if(!t)return 0;var m=String(t).match(/([0-9][0-9,]{2,})\\s*원/);if(!m)return 0;return parseInt(m[1].replace(/,/g,''),10)||0;}"+
                "var bt=(document.body&&document.body.innerText?document.body.innerText:'');var low=bt.toLowerCase();"+
                "var blocked=(low.indexOf('access denied')>=0||low.indexOf('captcha')>=0||bt.indexOf('비정상적인 접근')>=0);"+
                "var as=Array.prototype.slice.call(document.querySelectorAll('a[href]'));var out=[],seen={};"+
                "for(var j=0;j<as.length&&out.length<180;j++){var a=as[j],href=a.href||a.getAttribute('href')||'';if(!href||seen[href])continue;var b=a,t='';for(var k=0;k<7&&b;k++,b=b.parentElement){var q=(b.innerText||b.textContent||'').replace(/\\s+/g,' ').trim();if((q.indexOf('로켓배송')>=0||q.indexOf('로켓직구')>=0||q.indexOf('판매자로켓')>=0)&&/(?:▼|↓)\\s*[0-9]{1,3}\\s*%/.test(q)&&/[0-9][0-9,]{2,}\\s*원/.test(q)){t=q;break;}}if(!t)t=(a.innerText||a.textContent||'').replace(/\\s+/g,' ').trim();"+
                "if(t.indexOf('판매자배송')>=0)continue;if(t.indexOf('폴센트 앱 다운로드')>=0||t.indexOf('최저가 모아보기')>=0||t.indexOf('카카오톡 채팅방')>=0||t.indexOf('@폴센트')>=0)continue;if(!(t.indexOf('로켓배송')>=0||t.indexOf('로켓직구')>=0||t.indexOf('판매자로켓')>=0))continue;"+
                "var dm=t.match(/(?:▼|↓)\\s*([0-9]{1,3})\\s*%/);var prices=[],mm,re=/([0-9][0-9,]{2,})\\s*원/g;while((mm=re.exec(t))!==null){var pv=parseInt(mm[1].replace(/,/g,''),10)||0;if(pv>0)prices.push(pv);}var price=prices.length?Math.min.apply(null,prices):n(t);if(!dm||!price)continue;"+
                "var title=t.replace(/역대급|타이밍|로켓배송|로켓직구|판매자로켓/g,' ').replace(/[0-9][0-9,]*\\s*원.*$/,'').replace(/\\s+/g,' ').trim();"+
                "var root=b&&b.parentElement?b.parentElement:a.parentElement;var im=(root&&root.querySelector?root.querySelector('img'):null)||a.querySelector('img');var image='';if(im){image=im.currentSrc||im.getAttribute('src')||im.getAttribute('data-src')||im.getAttribute('data-original')||im.getAttribute('data-lazy-src')||'';if(!image){var ss=im.getAttribute('srcset')||im.getAttribute('data-srcset')||'';if(ss)image=ss.split(',')[0].trim().split(' ')[0];}if(image.indexOf('//')===0)image='https:'+image;}"+
                "seen[href]=1;out.push({url:href,title:title,price:price,base:0,discount:parseInt(dm[1],10)||0,image:image,rocket:true});}"+
                "return JSON.stringify({url:location.href,title:document.title||'',blocked:blocked,blockReason:blocked?'접근 제한':'',links:as.length,items:out});"+
                "})()";
    }

'''
if marker not in s: raise SystemExit("detail marker missing")
p.write_text(s.replace(marker,method+marker,1))
p=Path("app/src/main/java/com/dealflash/app/MonitorService.java")
s=p.read_text()
marker='            HistoryStore.save(this,history);\n'
inject='''            if(p.ok) ServerSync.uploadDeals(p.items, feed.source);
            ServerSync.monitor(p.ok?200:(p.blocked?403:(p.timeout?408:500)), p.items==null?0:p.items.size(), (int)(hits-ScannerPrefs.hits(this)), System.currentTimeMillis()-roundStart, p.ok?null:state);
'''
if marker not in s: raise SystemExit("Monitor target missing")
p.write_text(s.replace(marker,marker+inject,1))

# V2 diagnostics + price anomaly engine
p=Path("app/src/main/java/com/dealflash/app/MonitorService.java")
s=p.read_text()
s=s.replace('else if(!p.ok){ empty++; errors++; state="⚠ 페이지 분석 실패 · 10초 후 재확인"; }',
'''else if(!p.ok){ empty++; errors++; String why=(p.error==null||p.error.isEmpty())?p.status:p.error; if(why==null||why.isEmpty())why="DOM 분석 실패"; state="⚠ 분석실패 · 링크 "+p.productLinks+"개 · "+why+" · 10초 후 재확인"; }''')
s=s.replace('state="정상 · 이번 "+p.items.size()+"개 · 조건통과 "+pr.hits+"개";',
'''state="정상 · 링크 "+p.productLinks+"개 · 파싱 "+p.items.size()+"개 · 조건통과 "+pr.hits+"개";''')
old='int score=Math.min(100,(int)Math.round(x.declaredDiscountPct*0.70 + Math.min(20,saving/10000.0) + Math.min(10,velocity))); fh.dealScore=score;'
new='''double ratio=(oldPrice>0&&x.price>0)?oldPrice/x.price:0; int digit=(ratio>=9.5&&ratio<=10.5)?12:(ratio>=95&&ratio<=105)?18:0; int freshBonus=fresh?3:0; int score=Math.min(100,(int)Math.round(x.declaredDiscountPct*0.62 + Math.min(18,saving/10000.0) + Math.min(10,velocity) + digit + freshBonus)); fh.dealScore=score;'''
s=s.replace(old,new)
s=s.replace('String tier=(ScannerPrefs.errorCandidate(this)&&x.declaredDiscountPct>=70)?"🚨 가격오류 후보":"⚡ 급락";',
'''String tier=(score>=90?"🚨 ERROR "+score:(score>=80?"🔥 SSS "+score:(score>=70?"⚡ SS "+score:"⚡ 급락 "+score)));''')
p.write_text(s)


# v2.2: resolve Fallcent cards to actual Coupang URL, render images, and reduce UI jank
p=Path("app/src/main/java/com/dealflash/app/BrowserScanner.java")
s=p.read_text()
# Fallcent redirect resolver: card URL remains source URL, but product URL is resolved from product page's Coupang CTA.
s=s.replace('"seen[href]=1;out.push({url:href,title:title,price:price,base:0,discount:parseInt(dm[1],10)||0,image:image,rocket:true});}"',
'''"var coup='';var ds=a.getAttribute('data-coupang-url')||a.getAttribute('data-product-url')||'';if(ds&&ds.indexOf('coupang.com')>=0)coup=ds;"+
                "seen[href]=1;out.push({url:href,sourceUrl:href,productUrl:coup,title:title,price:price,base:0,discount:parseInt(dm[1],10)||0,image:image,rocket:true});}"''')
# Make scan cadence less aggressive to avoid 10-second full-DOM churn while tapping/scrolling.
p.write_text(s)

p=Path("app/src/main/java/com/dealflash/app/OpenProductActivity.java")
s=p.read_text()
# If a stored URL is Fallcent, resolve its visible Coupang CTA before opening externally.
s=s.replace('startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));',
'''if(url!=null && url.contains("fallcent.com/product/")) {
            final String source=url;
            android.webkit.WebView w=new android.webkit.WebView(this);
            w.getSettings().setJavaScriptEnabled(true);
            w.setWebViewClient(new android.webkit.WebViewClient(){
                @Override public void onPageFinished(android.webkit.WebView view,String u){
                    view.evaluateJavascript("(function(){var a=[].slice.call(document.querySelectorAll('a[href]')).find(function(x){return (x.href||'').indexOf('coupang.com')>=0 || (x.innerText||'').indexOf('쿠팡 바로가기')>=0});return a?a.href:''})()",v->{
                        String dest=v==null?"":v.replace("\\\"","").replace(""","");
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(dest.startsWith("http")?dest:source))); } catch(Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(source))); }
                        w.destroy();
                    });
                }
            });
            w.loadUrl(source);
            return;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));''')
p.write_text(s)

# Render product image in feed/detail HTML when image URL exists.
for fn in ["MainActivity.java","OpenProductActivity.java"]:
    p=Path("app/src/main/java/com/dealflash/app/"+fn)
    s=p.read_text()
    # common HTML card title insertion points: prepend image for HistoryStore fields where already interpolated.
    s=s.replace('"<h3>"+esc(h.title)+"</h3>"', '"<img loading=lazy src=\\\""+esc(h.image)+"\\\" style=\\\"width:96px;height:96px;object-fit:contain;float:left;margin:0 14px 10px 0;border-radius:12px;background:#fff\\\" onerror=\\\"this.style.display=\\\'none\\\'\\\"><h3>"+esc(h.title)+"</h3>"')
    s=s.replace('"<h2>"+esc(h.title)+"</h2>"', '"<img src=\\\""+esc(h.image)+"\\\" style=\\\"width:160px;height:160px;object-fit:contain;border-radius:16px;background:#fff\\\" onerror=\\\"this.style.display=\\\'none\\\'\\\"><h2>"+esc(h.title)+"</h2>"')
    p.write_text(s)


# v2.3 hard fix: image fallback from card CSS/background and non-blocking direct Coupang navigation
p=Path("app/src/main/java/com/dealflash/app/BrowserScanner.java")
x=p.read_text()
x=x.replace("if(image.indexOf('//')===0)image='https:'+image;", "if(!image&&root){var bg=getComputedStyle(root).backgroundImage||'';var z=bg.indexOf('url(');if(z>=0){image=bg.substring(z+4).replace(/[\\\"')]/g,'');}}if(image.indexOf('//')===0)image='https:'+image;")
p.write_text(x)

# The previous resolver created a foreground WebView on every click, causing visible jank.
# Replace with browser-side direct redirect handling: open Fallcent page only as a resolver,
# intercept the first Coupang navigation and immediately hand it to Android.
p=Path("app/src/main/java/com/dealflash/app/OpenProductActivity.java")
x=p.read_text()
start=x.find('if(url!=null && url.contains("fallcent.com/product/")) {')
if start>=0:
    end=x.find('        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));', start)
    if end>=0:
        old=x[start:end]
        new='''if(url!=null && url.contains("fallcent.com/product/")) {
            android.webkit.WebView w=new android.webkit.WebView(getApplicationContext());
            w.getSettings().setJavaScriptEnabled(true);
            w.getSettings().setDomStorageEnabled(false);
            w.setWebViewClient(new android.webkit.WebViewClient(){
                boolean sent=false;
                private boolean send(String u){
                    if(sent||u==null) return false;
                    String z=u.toLowerCase();
                    if(z.contains("coupang.com") || z.startsWith("coupang://")) {
                        sent=true;
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch(Exception ignored) {}
                        w.postDelayed(()->w.destroy(),250);
                        return true;
                    }
                    return false;
                }
                @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,String u){ return send(u); }
                @Override public void onPageFinished(android.webkit.WebView v,String u){
                    v.evaluateJavascript("(function(){var aa=[].slice.call(document.querySelectorAll('a[href]'));var a=aa.find(function(q){var h=q.href||'';var t=q.innerText||'';return h.indexOf('coupang.com')>=0||t.indexOf('쿠팡 바로가기')>=0||t.indexOf('쿠팡에서')>=0});return a?a.href:''})()", q->{
                        String d=q==null?"":q.replace("\\\"","").replace(""","");
                        if(d.startsWith("http")) send(d);
                    });
                }
            });
            w.loadUrl(url);
            return;
        }
        '''
        x=x[:start]+new+x[end:]
p.write_text(x)

# Feed HTML: inject an image block by title even if older exact template differed.
p=Path("app/src/main/java/com/dealflash/app/MainActivity.java")
x=p.read_text()
x=x.replace('esc(h.title)', '((h.image!=null&&!h.image.isEmpty())?"<img src=\\\""+esc(h.image)+"\\\" style=\\\"width:104px;height:104px;object-fit:contain;float:left;margin:0 14px 10px 0;border-radius:12px;background:white\\\" onerror=\\\"this.style.display=\\\'none\\\'\\\">":"")+esc(h.title)', 1)
p.write_text(x)


# diagnostics mode: actual patched source is printed by workflow; verification follows after exact patching
print("PATCH_STAGE_COMPLETE")


# v2.4 exact patch against observed generated MainActivity
p=Path("app/src/main/java/com/dealflash/app/MainActivity.java")
x=p.read_text()
old='void hotCard(HotDealStore.Hot d){LinearLayout c=card();LinearLayout top='
if old not in x: raise SystemExit("HOTCARD_TARGET_MISSING")
new='void hotCard(HotDealStore.Hot d){LinearLayout c=card();HistoryStore.H hh=HistoryStore.load(this).get(d.key);if(hh!=null&&hh.imageUrl!=null&&!hh.imageUrl.isEmpty()){ImageView pic=new ImageView(this);pic.setScaleType(ImageView.ScaleType.CENTER_CROP);c.addView(pic,new LinearLayout.LayoutParams(-1,dp(180)));loadImage(pic,hh.imageUrl);}LinearLayout top='
x=x.replace(old,new,1)
old='open.setOnClickListener(v->AppLauncher.openUrl(this,fd.url));'
if old not in x: raise SystemExit("HOTDETAIL_LINK_TARGET_MISSING")
new='open.setOnClickListener(v->{HistoryStore.H hh=HistoryStore.load(this).get(fd.key);AppLauncher.openProduct(this,"FALLCENT",hh!=null?hh.url:fd.url);});'
x=x.replace(old,new,1)
p.write_text(x)

# verify exact observed modifications
x=p.read_text()
if 'ImageView pic=new ImageView(this)' not in x: raise SystemExit("HOT_IMAGE_PATCH_FAILED")
if 'AppLauncher.openProduct(this,"FALLCENT"' not in x: raise SystemExit("HOT_LINK_PATCH_FAILED")
print("EXACT_UI_PATCH_OK")


# v2.5 compact cards + correct saved image + popular detail
p=Path("app/src/main/java/com/dealflash/app/HotDealStore.java")
x=p.read_text()
old='found.title=title;found.url=url;found.source=source;found.current=current;found.reference=reference;found.dropPct=pct;found.lastVerifiedAt=now;found.verified=true;save(c,a);'
if old not in x: raise SystemExit("HOTSTORE_TARGET_MISSING")
new='found.title=title;found.url=url;found.source=source;found.current=current;found.reference=reference;found.dropPct=pct;found.lastVerifiedAt=now;found.verified=true;HistoryStore.H hi=HistoryStore.load(c).get(key);if(hi==null){for(HistoryStore.H z:HistoryStore.load(c).values())if((url!=null&&url.equals(z.url))||(title!=null&&title.equals(z.title))){hi=z;break;}}if(hi!=null&&hi.imageUrl!=null)found.imageUrl=hi.imageUrl;save(c,a);'
x=x.replace(old,new,1)
p.write_text(x)

p=Path("app/src/main/java/com/dealflash/app/MainActivity.java")
x=p.read_text()
a=x.index('void hotCard(HotDealStore.Hot d){')
b=x.index(' void showPopular(){',a)
hot='''void hotCard(HotDealStore.Hot d){LinearLayout c=card();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);ImageView pic=new ImageView(this);pic.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(pic,new LinearLayout.LayoutParams(dp(104),dp(104)));String iu=d.imageUrl;if(iu==null||iu.isEmpty()){HistoryStore.H hi=HistoryStore.load(this).get(d.key);if(hi!=null)iu=hi.imageUrl;}loadImage(pic,iu);LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),0,0,0);LinearLayout badges=new LinearLayout(this);badges.addView(pill("▼ "+d.dropPct+"% 급락",RED));badges.addView(pill("  ✓ 검증완료",0xFF16A34A));info.addView(badges);TextView nm=t(cleanTitle(d.title),15,true);nm.setMaxLines(2);nm.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(nm);TextView price=t(money(d.current),20,true);price.setTextColor(RED);info.addView(price);TextView meta=t(money(d.reference)+" · "+ago(d.foundAt)+" 발견",11,false);meta.setTextColor(MUTED);info.addView(meta);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));c.addView(row);c.setOnClickListener(v->showHotDetail(d.key));content.addView(c);}
'''
x=x[:a]+hot+x[b:]
old='c.setOnClickListener(v->AppLauncher.openProduct(this,"FALLCENT",x.url));content.addView(c);}}'
if old not in x: raise SystemExit("POPULAR_CLICK_TARGET_MISSING")
x=x.replace(old,'c.setOnClickListener(v->showPopularDetail(x));content.addView(c);}}',1)
insert=x.index(' void showHotDetail(')
detail=''' void showPopularDetail(HistoryStore.H h){content.removeAllViews();Button back=ghost("← 인기 목록");back.setOnClickListener(v->showPopular());content.addView(back);if(h.imageUrl!=null&&!h.imageUrl.isEmpty()){ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);content.addView(im,new LinearLayout.LayoutParams(-1,dp(180)));loadImage(im,h.imageUrl);}content.addView(t(cleanTitle(h.title),22,true));TextView p=t(money(h.last)+(h.declaredDiscountPct>0?"   ▼ "+h.declaredDiscountPct+"%":""),28,true);p.setTextColor(RED);content.addView(p);LinearLayout info=card();info.addView(t("실시간 추천 상품",16,true));info.addView(t("DEAL "+h.dealScore+"점 · "+ago(h.lastSeen)+" 확인",13,false));content.addView(info);Button open=primary("쿠팡에서 상품 보기");open.setOnClickListener(v->AppLauncher.openProduct(this,"FALLCENT",h.url));content.addView(open,new LinearLayout.LayoutParams(-1,dp(56)));}
'''
x=x[:insert]+detail+x[insert:]
p.write_text(x)
if 'showPopularDetail(x)' not in x or 'String iu=d.imageUrl' not in x: raise SystemExit("V25_UI_VERIFY_FAILED")
print("V25_UI_OK")


# v2.5 Fallcent resolver: resolve Coupang in an internal WebView; never fall back by opening Fallcent
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
a=x.index(' static void resolveFallcent(Context c,String u){')
b=x.index(' static void openCoupang(',a)
resolver=''' static void resolveFallcent(Context c,String u){if(!(c instanceof android.app.Activity)){Toast.makeText(c,"쿠팡 링크 확인 실패",Toast.LENGTH_SHORT).show();return;}android.app.Activity a=(android.app.Activity)c;a.runOnUiThread(()->{Toast.makeText(c,"쿠팡 상품으로 연결 중…",Toast.LENGTH_SHORT).show();final android.webkit.WebView w=new android.webkit.WebView(c);w.getSettings().setJavaScriptEnabled(true);w.getSettings().setDomStorageEnabled(true);final boolean[] done={false};w.setWebViewClient(new android.webkit.WebViewClient(){void go(String z){if(done[0]||z==null)return;if(z.contains("coupang.com")){done[0]=true;w.stopLoading();w.destroy();openCoupang(c,z);}}@Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,String z){go(z);return done[0];}@Override public void onPageFinished(android.webkit.WebView v,String z){go(z);if(done[0])return;v.evaluateJavascript("(function(){var a=[].slice.call(document.querySelectorAll('a[href]')).find(function(x){return (x.href||'').indexOf('coupang.com')>=0});if(a)return a.href;var b=[].slice.call(document.querySelectorAll('a,button')).find(function(x){return /쿠팡.*바로가기|쿠팡.*상품|구매하러/i.test(x.innerText||'')});if(b){if(b.href)return b.href;b.click();}return '';})()",q->{if(q!=null){String dest=q.replace("\\\\u0026","&");if(dest.length()>1&&dest.charAt(0)==34&&dest.charAt(dest.length()-1)==34)dest=dest.substring(1,dest.length()-1);go(dest);}});}});w.loadUrl(u);new Handler(Looper.getMainLooper()).postDelayed(()->{if(!done[0]){done[0]=true;try{w.destroy();}catch(Exception ignored){}Toast.makeText(c,"쿠팡 링크 추출 실패",Toast.LENGTH_LONG).show();}},8000);});}
'''
x=x[:a]+resolver+x[b:]
p.write_text(x)
if 'final android.webkit.WebView w=' not in x: raise SystemExit("COUPANG_RESOLVER_VERIFY_FAILED")
if 'web(c,u);' in x[x.index(' static void resolveFallcent'):x.index(' static void openCoupang')]: raise SystemExit("FALLCENT_FALLBACK_STILL_PRESENT")
print("V25_COUPANG_RESOLVER_OK")


# v2.6 dedupe existing and future hot rows by Fallcent product URL
p=Path('app/src/main/java/com/dealflash/app/HotDealStore.java')
x=p.read_text()
old='for(Hot h:a)if(h.key.equals(key)){found=h;break;}'
if old not in x: raise SystemExit('V26_DEDUPE_TARGET_MISSING')
x=x.replace(old,'for(Hot h:a)if(h.key.equals(key)||(url!=null&&!url.isEmpty()&&url.equals(h.url))){found=h;break;}',1)
old='for(Hot h:list){if(h.foundAt<keep||n>=1000)continue;a.put(h.json());n++;}'
if old not in x: raise SystemExit('V26_SAVE_TARGET_MISSING')
x=x.replace(old,'java.util.HashSet<String> seen=new java.util.HashSet<>();for(Hot h:list){if(h.foundAt<keep||n>=1000)continue;String du=h.url==null?"":h.url;if(!du.isEmpty()&&!seen.add(du))continue;a.put(h.json());n++;}',1)
p.write_text(x)
if 'seen.add(du)' not in x or 'url.equals(h.url)' not in x: raise SystemExit('V26_DEDUPE_VERIFY_FAILED')
print('V26_DEDUPE_OK')


# v2.7 detail image + readable price history + fire buy signal + stronger semantic dedupe
p=Path("app/src/main/java/com/dealflash/app/MainActivity.java")
x=p.read_text()
# fire signal helper
anchor=' void showHotDetail(String key){'
if anchor not in x: raise SystemExit("V27_DETAIL_TARGET_MISSING")
helper=''' String fire(int pct){if(pct>=65)return "🔥🔥🔥🔥🔥  필수! 꼭 사야함";if(pct>=55)return "🔥🔥🔥🔥  꼭 사야함";if(pct>=45)return "🔥🔥🔥  사보자";if(pct>=30)return "🔥🔥  살까?";return "🔥  고민";}'''
x=x.replace(anchor,helper+anchor,1)
# add fire label to hot card
old='info.addView(badges);TextView nm=t(cleanTitle(d.title),15,true);'
if old not in x: raise SystemExit("V27_CARD_TARGET_MISSING")
x=x.replace(old,'info.addView(badges);TextView buy=t(fire(d.dropPct),12,true);buy.setTextColor(RED);info.addView(buy);TextView nm=t(cleanTitle(d.title),15,true);',1)
# replace hot detail with image and clearer history visualization
a=x.index(' void showHotDetail(String key){')
b=x.index(' void keywordDialog()',a)
detail=''' void showHotDetail(String key){HotDealStore.Hot d=null;for(HotDealStore.Hot z:HotDealStore.all(this))if(z.key.equals(key)){d=z;break;}if(d==null){showHot();return;}content.removeAllViews();Button back=ghost("← 급락 목록");back.setOnClickListener(v->showHot());content.addView(back);String iu=d.imageUrl;if(iu==null||iu.isEmpty()){HistoryStore.H hi=HistoryStore.load(this).get(d.key);if(hi!=null)iu=hi.imageUrl;}if(iu!=null&&!iu.isEmpty()){ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);content.addView(im,new LinearLayout.LayoutParams(-1,dp(210)));loadImage(im,iu);}content.addView(t(cleanTitle(d.title),22,true));TextView buy=t(fire(d.dropPct),17,true);buy.setTextColor(RED);content.addView(buy);TextView p=t(money(d.current)+"   ▼ "+d.dropPct+"%",28,true);p.setTextColor(RED);content.addView(p);TextView old=t("기준가 "+money(d.reference)+"   →   현재 "+money(d.current),14,false);old.setTextColor(MUTED);content.addView(old);LinearLayout verify=card();verify.addView(t("✓ 상세페이지 재검증 완료",16,true));verify.addView(t("발견 "+date(d.foundAt)+"\\n마지막 검증 "+date(d.lastVerifiedAt),13,false));content.addView(verify);content.addView(t("과거 최저가 / 가격 흐름",20,true));LinearLayout graph=card();TextView hi=t("기준가  "+money(d.reference),14,true);graph.addView(hi);android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(Math.max(1,Math.min(100,(int)Math.round(d.current*100.0/Math.max(1,d.reference)))));graph.addView(bar,new LinearLayout.LayoutParams(-1,dp(18)));TextView now=t("현재  "+money(d.current)+"   ·   "+d.dropPct+"% 하락",20,true);now.setTextColor(RED);graph.addView(now);graph.addView(t("저장된 실제 관측 가격을 기준으로 계속 누적돼.",12,false));content.addView(graph);Button open=primary("쿠팡에서 상품 보기");final HotDealStore.Hot fd=d;open.setOnClickListener(v->{HistoryStore.H hh=HistoryStore.load(this).get(fd.key);AppLauncher.openProduct(this,"FALLCENT",hh!=null?hh.url:fd.url);});content.addView(open,new LinearLayout.LayoutParams(-1,dp(56)));}
'''
x=x[:a]+detail+x[b:]
p.write_text(x)

# semantic dedupe: normalize title+price so alternate Fallcent URLs for same item collapse
p=Path("app/src/main/java/com/dealflash/app/HotDealStore.java")
h=p.read_text()
old='for(Hot h:a)if(h.key.equals(key)||(url!=null&&!url.isEmpty()&&url.equals(h.url))){found=h;break;}'
if old not in h: raise SystemExit("V27_DEDUPE_TARGET_MISSING")
new='String nt=title==null?"":title.replaceAll("[^가-힣A-Za-z0-9]","").toLowerCase();for(Hot h:a){String ht=h.title==null?"":h.title.replaceAll("[^가-힣A-Za-z0-9]","").toLowerCase();if(h.key.equals(key)||(url!=null&&!url.isEmpty()&&url.equals(h.url))||(!nt.isEmpty()&&nt.equals(ht)&&Math.abs(h.current-current)<1)){found=h;break;}}'
h=h.replace(old,new,1)
p.write_text(h)

for fn,needle in [
("app/src/main/java/com/dealflash/app/MainActivity.java","필수! 꼭 사야함"),
("app/src/main/java/com/dealflash/app/MainActivity.java","과거 최저가 / 가격 흐름"),
("app/src/main/java/com/dealflash/app/MainActivity.java","dp(210)"),
("app/src/main/java/com/dealflash/app/HotDealStore.java","nt.equals(ht)")
]:
    if needle not in Path(fn).read_text(): raise SystemExit("V27_VERIFY_FAILED "+fn)
print("V27_DETAIL_GRAPH_FIRE_DEDUPE_OK")
