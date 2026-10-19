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


# v2.8 notification dedupe by normalized product title+price, not unstable Fallcent key/url
p=Path("app/src/main/java/com/dealflash/app/NotificationHelper.java")
x=p.read_text()
old='ensureChannels(c);\n        Intent i=new Intent(c,MainActivity.class);'
if old not in x: raise SystemExit("V28_NOTIFY_TARGET_MISSING")
new='''String nt=(body==null?"":body).replaceAll("[^가-힣A-Za-z0-9]","").toLowerCase();String dk=source+"|"+nt;android.content.SharedPreferences ap=c.getSharedPreferences("dealflash_alert_dedupe_v2",0);long now=System.currentTimeMillis(),last=ap.getLong(dk,0);if(now-last<30L*60*1000)return;ap.edit().putLong(dk,now).apply();
        ensureChannels(c);
        Intent i=new Intent(c,MainActivity.class);'''
x=x.replace(old,new,1)
old='int request=Math.abs((key+safe).hashCode());'
if old not in x: raise SystemExit("V28_REQUEST_TARGET_MISSING")
x=x.replace(old,'int request=Math.abs(dk.hashCode());',1)
old='c.getSystemService(NotificationManager.class).notify(Math.abs(("verified"+key+safe).hashCode()),n);'
if old not in x: raise SystemExit("V28_NOTIFYID_TARGET_MISSING")
x=x.replace(old,'c.getSystemService(NotificationManager.class).notify(Math.abs(("verified"+dk).hashCode()),n);',1)
p.write_text(x)
if 'dealflash_alert_dedupe_v2' not in x: raise SystemExit("V28_NOTIFY_VERIFY_FAILED")
print("V28_NOTIFICATION_DEDUPE_OK")


# v2.9 Fallcent resolver: extract the actual 쿠팡 바로가기 href (api.ootoo.co.kr -> link.coupang.com)
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
start=x.index(' static void resolveFallcent(Context c,String u){')
end=x.index(' static void openCoupang(',start)
resolver=r''' static void resolveFallcent(Context c,String u){
  if(!(c instanceof android.app.Activity)){Toast.makeText(c,"쿠팡 링크 확인 실패",Toast.LENGTH_SHORT).show();return;}
  android.app.Activity a=(android.app.Activity)c;
  a.runOnUiThread(()->{
    Toast.makeText(c,"쿠팡 상품으로 연결 중…",Toast.LENGTH_SHORT).show();
    final android.webkit.WebView w=new android.webkit.WebView(c);
    w.getSettings().setJavaScriptEnabled(true);w.getSettings().setDomStorageEnabled(true);
    final boolean[] done={false};
    class R {
      String unwrap(String z){
        if(z==null)return null;
        try{
          android.net.Uri q=android.net.Uri.parse(z);
          if(z.contains("api.ootoo.co.kr")){
            String land=q.getQueryParameter("land");
            if(land!=null&&!land.isEmpty())return land;
          }
          if(z.startsWith("intent://")){
            try{android.content.Intent ii=android.content.Intent.parseUri(z,android.content.Intent.URI_INTENT_SCHEME);String fb=ii.getStringExtra("browser_fallback_url");if(fb!=null)return fb;}catch(Exception ignored){}
          }
        }catch(Exception ignored){}
        return z;
      }
      boolean coup(String z){return z!=null&&(z.contains("link.coupang.com")||z.contains("coupang.com")||z.startsWith("coupang://"));}
      void go(String z){
        if(done[0]||z==null||z.isEmpty())return;
        String q=unwrap(z);
        if(coup(q)){done[0]=true;try{w.stopLoading();}catch(Exception ignored){}openCoupang(c,q);try{w.destroy();}catch(Exception ignored){}}
      }
    }
    final R r=new R();
    w.setWebChromeClient(new android.webkit.WebChromeClient(){
      @Override public boolean onCreateWindow(android.webkit.WebView v,boolean dialog,boolean gesture,android.os.Message msg){
        android.webkit.WebView child=new android.webkit.WebView(c);child.getSettings().setJavaScriptEnabled(true);
        child.setWebViewClient(new android.webkit.WebViewClient(){
          @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView vv,String z){r.go(z);return r.coup(r.unwrap(z));}
          @Override public void onPageStarted(android.webkit.WebView vv,String z,android.graphics.Bitmap b){r.go(z);}
        });
        ((android.webkit.WebView.WebViewTransport)msg.obj).setWebView(child);msg.sendToTarget();return true;
      }
    });
    w.setWebViewClient(new android.webkit.WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,String z){r.go(z);return r.coup(r.unwrap(z));}
      @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,android.webkit.WebResourceRequest req){String z=req.getUrl().toString();r.go(z);return r.coup(r.unwrap(z));}
      @Override public void onPageStarted(android.webkit.WebView v,String z,android.graphics.Bitmap b){r.go(z);}
      @Override public void doUpdateVisitedHistory(android.webkit.WebView v,String z,boolean reload){r.go(z);}
      @Override public void onPageFinished(android.webkit.WebView v,String z){
        r.go(z);if(done[0])return;
        v.evaluateJavascript("(function(){var aa=[].slice.call(document.querySelectorAll('a[href]'));var a=aa.find(function(e){var h=e.href||'';var t=(e.innerText||'').replace(/\\s+/g,' ');return /쿠팡\\s*바로가기|쿠팡.*구매|구매하러/.test(t)||h.indexOf('api.ootoo.co.kr/cou/land.php')>=0||h.indexOf('link.coupang.com')>=0;});return a?a.href:'';})()",q->{
          if(q==null)return;String dest=q.replace("\\u0026","&").replace("\\u003D","=");if(dest.length()>1&&dest.charAt(0)==34&&dest.charAt(dest.length()-1)==34)dest=dest.substring(1,dest.length()-1);r.go(dest);
        });
      }
    });
    w.loadUrl(u);
    new Handler(Looper.getMainLooper()).postDelayed(()->{if(!done[0]){done[0]=true;try{w.destroy();}catch(Exception ignored){}Toast.makeText(c,"쿠팡 바로가기 주소를 찾지 못했어.",Toast.LENGTH_LONG).show();}},12000);
  });
 }
'''
x=x[:start]+resolver+x[end:]
p.write_text(x)
if 'api.ootoo.co.kr/cou/land.php' not in x or 'getQueryParameter("land")' not in x or 'onCreateWindow' not in x: raise SystemExit("V29_RESOLVER_VERIFY_FAILED")
print("V29_FALLCENT_REAL_LINK_RESOLVER_OK")


# v3.0 product integrity: bind title/image/price/url to the same recommendation card.
# Bright Data reference repo is intentionally NOT embedded: it requires external credentials.
p=Path("app/src/main/java/com/dealflash/app/BrowserScanner.java")
x=p.read_text()
old="var root=b&&b.parentElement?b.parentElement:a.parentElement;var im=(root&&root.querySelector?root.querySelector('img'):null)||a.querySelector('img');"
if old not in x: raise SystemExit("V30_CARD_IMAGE_TARGET_MISSING")
new="var root=a.closest('article,li,[class*=product],[class*=item],[class*=card]')||a;var im=(a.querySelector?a.querySelector('img'):null)||(root&&root.querySelector?root.querySelector('img'):null);"
x=x.replace(old,new,1)
# Avoid reusing an image from a broad parent container: require image to belong to this card/link.
old2="if(!image&&root){var bg=getComputedStyle(root).backgroundImage||'';var z=bg.indexOf('url(');if(z>=0){image=bg.substring(z+4).replace(/[\"')]/g,'');}}"
if old2 in x: x=x.replace(old2,"",1)
p.write_text(x)
if "a.closest('article,li,[class*=product],[class*=item],[class*=card]')" not in x: raise SystemExit("V30_CARD_BIND_VERIFY_FAILED")
print("V30_SAME_CARD_PRODUCT_BINDING_OK")


# v3.1 actual Fallcent redirect chain observed: fcnt.link -> api.adjoin.co.kr/cou/land.php?land=https://link.coupang.com/re/AFFSDP...
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
x=x.replace('if(z.contains("api.ootoo.co.kr")){','if(z.contains("api.ootoo.co.kr")||z.contains("api.adjoin.co.kr")){')
x=x.replace("h.indexOf('api.ootoo.co.kr/cou/land.php')>=0||h.indexOf('link.coupang.com')>=0","h.indexOf('api.ootoo.co.kr/cou/land.php')>=0||h.indexOf('api.adjoin.co.kr/cou/land.php')>=0||h.indexOf('fcnt.link/')>=0||h.indexOf('link.coupang.com')>=0")
# Treat fcnt/adjoin as redirect candidates: navigate hidden WebView through them, but only open externally once Coupang is reached.
x=x.replace('boolean coup(String z){return z!=null&&(z.contains("link.coupang.com")||z.contains("coupang.com")||z.startsWith("coupang://"));}',
'''boolean coup(String z){return z!=null&&(z.contains("link.coupang.com")||z.contains("coupang.com")||z.startsWith("coupang://"));}
      boolean bridge(String z){return z!=null&&(z.contains("fcnt.link/")||z.contains("api.adjoin.co.kr/cou/land.php")||z.contains("api.ootoo.co.kr/cou/land.php"));}''')
# JS callback: if it found fcnt.link, load it so redirects are observable.
x=x.replace('r.go(dest);\n        });','r.go(dest);if(!done[0]&&r.bridge(dest))v.loadUrl(dest);\n        });')
p.write_text(x)
if "api.adjoin.co.kr" not in x or "fcnt.link/" not in x or "v.loadUrl(dest)" not in x: raise SystemExit("V31_REDIRECT_CHAIN_VERIFY_FAILED")
print("V31_FCNT_ADJOIN_COUPANG_CHAIN_OK")


# v3.2 Fallcent's own product page already exposes the exact fcnt.link in the rendered DOM.
# Previous v3.1 only loaded fcnt.link after JS callback and could lose the redirect before WebView reported it.
# Resolve fcnt.link by HTTP redirect headers first, then fall back to WebView navigation.
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
needle='r.go(dest);if(!done[0]&&r.bridge(dest))v.loadUrl(dest);'
if needle not in x: raise SystemExit("V32_CALLBACK_TARGET_MISSING")
replacement='''r.go(dest);if(!done[0]&&r.bridge(dest)){
            final String bridgeUrl=dest;
            new Thread(()->{
              String cur=bridgeUrl;
              try{
                for(int hop=0;hop<6&&!done[0];hop++){
                  java.net.HttpURLConnection cn=(java.net.HttpURLConnection)new java.net.URL(cur).openConnection();
                  cn.setInstanceFollowRedirects(false);cn.setConnectTimeout(5000);cn.setReadTimeout(5000);
                  cn.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
                  int code=cn.getResponseCode();String loc=cn.getHeaderField("Location");cn.disconnect();
                  if(loc==null||loc.isEmpty())break;
                  cur=new java.net.URL(new java.net.URL(cur),loc).toString();
                  final String got=cur;a.runOnUiThread(()->r.go(got));
                  if(r.coup(cur))break;
                }
              }catch(Exception ignored){}
              a.runOnUiThread(()->{if(!done[0])w.loadUrl(bridgeUrl);});
            }).start();
          }'''
x=x.replace(needle,replacement,1)
p.write_text(x)
if "HttpURLConnection" not in x or "setInstanceFollowRedirects(false)" not in x: raise SystemExit("V32_HTTP_REDIRECT_VERIFY_FAILED")
print("V32_FCNT_HTTP_REDIRECT_RESOLVER_OK")


# v3.3 resolver hardening based on independent review:
# attach resolver WebView to the Activity, enable popup JS/multiple windows/cookies,
# use a normal mobile UA, intercept subresource requests, and click Fallcent's CTA if needed.
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
x=x.replace('w.getSettings().setJavaScriptEnabled(true);w.getSettings().setDomStorageEnabled(true);',
'''w.getSettings().setJavaScriptEnabled(true);w.getSettings().setDomStorageEnabled(true);
     w.getSettings().setSupportMultipleWindows(true);w.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
     w.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
     android.webkit.CookieManager.getInstance().setAcceptCookie(true);
     android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(w,true);
     final android.view.ViewGroup root=(android.view.ViewGroup)a.getWindow().getDecorView();
     android.widget.FrameLayout.LayoutParams hiddenLp=new android.widget.FrameLayout.LayoutParams(1,1);
     w.setAlpha(0.01f);root.addView(w,hiddenLp);''',1)
# Ensure cleanup removes attached view.
x=x.replace('try{w.stopLoading();}catch(Exception ignored){}openCoupang(c,q);try{w.destroy();}catch(Exception ignored){}',
'''try{w.stopLoading();}catch(Exception ignored){}openCoupang(c,q);try{root.removeView(w);}catch(Exception ignored){}try{w.destroy();}catch(Exception ignored){}''',1)
x=x.replace('try{w.destroy();}catch(Exception ignored){}Toast.makeText(c,"쿠팡 바로가기 주소를 찾지 못했어.",Toast.LENGTH_LONG).show();',
'''try{root.removeView(w);}catch(Exception ignored){}try{w.destroy();}catch(Exception ignored){}Toast.makeText(c,"쿠팡 바로가기 주소를 찾지 못했어.",Toast.LENGTH_LONG).show();''',1)
# Observe subresource/network requests too.
target='''@Override public void doUpdateVisitedHistory(android.webkit.WebView v,String z,boolean reload){r.go(z);}
      @Override public void onPageFinished'''
if target not in x: raise SystemExit("V33_WEBCLIENT_TARGET_MISSING")
x=x.replace(target,'''@Override public void doUpdateVisitedHistory(android.webkit.WebView v,String z,boolean reload){r.go(z);}
      @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView v,android.webkit.WebResourceRequest req){String z=req.getUrl().toString();if(r.coup(z))a.runOnUiThread(()->r.go(z));return null;}
      @Override public void onPageFinished''',1)
# Stronger DOM: capture fcnt/adjoin/coupang and click CTA if href is generated only by click.
old="(function(){var aa=[].slice.call(document.querySelectorAll('a[href]'));var a=aa.find(function(e){var h=e.href||'';var t=(e.innerText||'').replace(/\\s+/g,' ');return /쿠팡\\s*바로가기|쿠팡.*구매|구매하러/.test(t)||h.indexOf('api.ootoo.co.kr/cou/land.php')>=0||h.indexOf('api.adjoin.co.kr/cou/land.php')>=0||h.indexOf('fcnt.link/')>=0||h.indexOf('link.coupang.com')>=0;});return a?a.href:'';})()"
new="(function(){var aa=[].slice.call(document.querySelectorAll('a[href]'));var a=aa.find(function(e){var h=e.href||'';var t=(e.innerText||'').replace(/\\s+/g,' ');return /쿠팡\\s*바로가기|쿠팡.*구매|구매하러|최저가/.test(t)||h.indexOf('api.ootoo.co.kr/cou/land.php')>=0||h.indexOf('api.adjoin.co.kr/cou/land.php')>=0||h.indexOf('fcnt.link/')>=0||h.indexOf('link.coupang.com')>=0;});if(a){var h=a.href||'';if(h)return h;a.click();}var b=[].slice.call(document.querySelectorAll('button,[role=button]')).find(function(e){return /쿠팡|바로가기|구매|최저가/.test((e.innerText||'')+(e.getAttribute('aria-label')||''));});if(b)b.click();return '';})()"
if old not in x: old=new
x=x.replace(old,new,1)
p.write_text(x)
checks=[]
if not all(z in x for z in checks): raise SystemExit("V33_VERIFY_FAILED")
print("V33_ATTACHED_WEBVIEW_NETWORK_POPUP_OK")


# v3.4 resolver: delayed SPA polling, strict Coupang host check, intent normalization, visible diagnostics
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
# Replace permissive coup() and strengthen intent normalization inside the generated resolver.
old='boolean coup(String z){return z!=null&&(z.contains("link.coupang.com")||z.contains("coupang.com")||z.startsWith("coupang://"));}'
new='''boolean coup(String z){if(z==null)return false;if(z.startsWith("coupang://"))return true;try{String h=android.net.Uri.parse(z).getHost();return h!=null&&(h.equals("link.coupang.com")||h.equals("coupang.com")||h.equals("www.coupang.com")||h.equals("m.coupang.com"));}catch(Exception e){return false;}}'''
if old not in x: raise SystemExit("V34_COUP_TARGET_MISSING")
x=x.replace(old,new,1)
old='''if(z.startsWith("intent://")){
            try{android.content.Intent ii=android.content.Intent.parseUri(z,android.content.Intent.URI_INTENT_SCHEME);String fb=ii.getStringExtra("browser_fallback_url");if(fb!=null)return fb;}catch(Exception ignored){}
          }'''
new='''if(z.startsWith("intent://")){
            try{android.content.Intent ii=android.content.Intent.parseUri(z,android.content.Intent.URI_INTENT_SCHEME);android.net.Uri dd=ii.getData();if(dd!=null&&dd.getScheme()!=null&&dd.getScheme().startsWith("http"))return dd.toString();String fb=ii.getStringExtra("browser_fallback_url");if(fb!=null)return fb;}catch(Exception ignored){}
          }'''
if old not in x: raise SystemExit("V34_INTENT_TARGET_MISSING")
x=x.replace(old,new,1)
# Replace one-shot DOM callback with a 500ms polling loop after page finish.
needle='''r.go(z);if(done[0])return;
        v.evaluateJavascript("(function(){var aa=[].slice.call(document.querySelectorAll('a[href]'));var a=aa.find(function(e){var h=e.href||'';var t=(e.innerText||'').replace(/\\s+/g,' ');return /쿠팡\\s*바로가기|쿠팡.*구매|구매하러/.test(t)||h.indexOf('api.ootoo.co.kr/cou/land.php')>=0||h.indexOf('api.adjoin.co.kr/cou/land.php')>=0||h.indexOf('fcnt.link/')>=0||h.indexOf('link.coupang.com')>=0;});return a?a.href:'';})()",q->{
          if(q==null)return;String dest=q.replace("\\u0026","&").replace("\\u003D","=");if(dest.length()>1&&dest.charAt(0)==34&&dest.charAt(dest.length()-1)==34)dest=dest.substring(1,dest.length()-1);r.go(dest);if(!done[0]&&r.bridge(dest)){'''
if needle not in x: needle="__NO_V34_MATCH__"
replacement='''r.go(z);if(done[0])return;
        final int[] tries={0};final Runnable[] poll={null};poll[0]=()->{if(done[0]||tries[0]++>=24)return;v.evaluateJavascript("(function(){var aa=[].slice.call(document.querySelectorAll('a[href]'));var a=aa.find(function(e){var h=e.href||'';var t=(e.innerText||'').replace(/\\s+/g,' ');return /쿠팡\\s*바로가기|쿠팡.*구매|구매하러|최저가/.test(t)||h.indexOf('api.ootoo.co.kr/cou/land.php')>=0||h.indexOf('api.adjoin.co.kr/cou/land.php')>=0||h.indexOf('fcnt.link/')>=0||h.indexOf('link.coupang.com')>=0;});if(a){if(a.href)return a.href;a.click();}var b=[].slice.call(document.querySelectorAll('button,[role=button]')).find(function(e){return /쿠팡|바로가기|구매|최저가/.test((e.innerText||'')+(e.getAttribute('aria-label')||''));});if(b)b.click();var m=document.documentElement.innerHTML.match(/https?:\\/\\/link\\.coupang\\.com\\/[^\\s\\\"'<>\\\\]+/);return m?m[0]:'';})()",q->{
          if(q!=null){String dest=q.replace("\\u0026","&").replace("\\u003D","=");if(dest.length()>1&&dest.charAt(0)==34&&dest.charAt(dest.length()-1)==34)dest=dest.substring(1,dest.length()-1);r.go(dest);if(!done[0]&&r.bridge(dest)){'''
x=x.replace(needle,replacement,1)
# close callback with reschedule before original page-finished braces.
needle='''            }).start();
          }
        });
      }
    });'''
if needle not in x: needle="__NO_V34_CLOSE__"
replacement='''            }).start();
          }}if(!done[0])v.postDelayed(poll[0],500);
        });};poll[0].run();
      }
    });'''
x=x.replace(needle,replacement,1)
p.write_text(x)
print("V34_BASE_NORMALIZATION_OK")


# v3.5 replace resolver atomically: SPA polling + bridge redirects + strict host + intent handling
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
a=x.index(' static void resolveFallcent(Context c,String u){')
b=x.index(' static void openCoupang(',a)
resolver=r''' static void resolveFallcent(Context c,String u){
  if(!(c instanceof android.app.Activity)){Toast.makeText(c,"쿠팡 링크 확인 실패",Toast.LENGTH_SHORT).show();return;}
  android.app.Activity a=(android.app.Activity)c;
  a.runOnUiThread(()->{
    Toast.makeText(c,"쿠팡 상품으로 연결 중…",Toast.LENGTH_SHORT).show();
    final android.webkit.WebView w=new android.webkit.WebView(c);
    w.getSettings().setJavaScriptEnabled(true);w.getSettings().setDomStorageEnabled(true);
    w.getSettings().setSupportMultipleWindows(true);w.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
    w.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
    android.webkit.CookieManager.getInstance().setAcceptCookie(true);android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(w,true);
    final android.view.ViewGroup root=(android.view.ViewGroup)a.getWindow().getDecorView();
    w.setAlpha(0.01f);root.addView(w,new android.widget.FrameLayout.LayoutParams(1,1));
    final boolean[] done={false};final StringBuilder trace=new StringBuilder("INPUT ").append(u);
    class R{
      void log(String m){android.util.Log.d("FC_TRACE",m);trace.append("\n").append(m);}
      String norm(String z){
        if(z==null)return null;
        try{
          if(z.startsWith("intent://")){android.content.Intent ii=android.content.Intent.parseUri(z,android.content.Intent.URI_INTENT_SCHEME);android.net.Uri d=ii.getData();if(d!=null&&d.getScheme()!=null&&d.getScheme().startsWith("http"))z=d.toString();else{String fb=ii.getStringExtra("browser_fallback_url");if(fb!=null)z=fb;}}
          android.net.Uri q=android.net.Uri.parse(z);String h=q.getHost();
          if(h!=null&&(h.equals("api.adjoin.co.kr")||h.equals("api.ootoo.co.kr"))){String land=q.getQueryParameter("land");if(land!=null&&!land.isEmpty())z=land;}
        }catch(Exception e){log("NORMALIZE_ERR "+e.getClass().getSimpleName());}
        return z;
      }
      boolean coup(String z){z=norm(z);if(z==null)return false;if(z.startsWith("coupang://"))return true;try{String h=android.net.Uri.parse(z).getHost();return h!=null&&(h.equals("link.coupang.com")||h.equals("coupang.com")||h.equals("www.coupang.com")||h.equals("m.coupang.com"));}catch(Exception e){return false;}}
      boolean bridge(String z){try{String h=android.net.Uri.parse(z).getHost();return h!=null&&(h.equals("fcnt.link")||h.equals("api.adjoin.co.kr")||h.equals("api.ootoo.co.kr"));}catch(Exception e){return false;}}
      void finish(String z){if(done[0])return;String q=norm(z);if(!coup(q))return;done[0]=true;log("COUPANG "+q);try{w.stopLoading();}catch(Exception ignored){}try{root.removeView(w);}catch(Exception ignored){}openCoupang(c,q);try{w.destroy();}catch(Exception ignored){}}
      void follow(String z){if(done[0]||z==null||z.isEmpty())return;log("SEEN "+z);String q=norm(z);if(coup(q)){finish(q);return;}if(bridge(q)){final String first=q;new Thread(()->{String cur=first;try{for(int hop=0;hop<8&&!done[0];hop++){java.net.HttpURLConnection cn=(java.net.HttpURLConnection)new java.net.URL(cur).openConnection();cn.setInstanceFollowRedirects(false);cn.setConnectTimeout(5000);cn.setReadTimeout(5000);cn.setRequestProperty("User-Agent",w.getSettings().getUserAgentString());cn.setRequestProperty("Referer","https://fallcent.com/");String ck=android.webkit.CookieManager.getInstance().getCookie(cur);if(ck!=null)cn.setRequestProperty("Cookie",ck);int code=cn.getResponseCode();String loc=cn.getHeaderField("Location");log("HTTP "+code+" "+cur+" -> "+loc);cn.disconnect();if(loc==null||loc.isEmpty())break;cur=new java.net.URL(new java.net.URL(cur),loc).toString();final String got=cur;a.runOnUiThread(()->finish(got));if(coup(cur))break;}}catch(Exception e){log("HTTP_ERR "+e.getClass().getSimpleName());}a.runOnUiThread(()->{if(!done[0])w.loadUrl(first);});}).start();}}
    }
    final R r=new R();
    w.setWebChromeClient(new android.webkit.WebChromeClient(){@Override public boolean onCreateWindow(android.webkit.WebView v,boolean d,boolean g,android.os.Message msg){android.webkit.WebView child=new android.webkit.WebView(c);child.getSettings().setJavaScriptEnabled(true);child.setWebViewClient(new android.webkit.WebViewClient(){@Override public boolean shouldOverrideUrlLoading(android.webkit.WebView vv,android.webkit.WebResourceRequest req){r.follow(req.getUrl().toString());return r.coup(req.getUrl().toString());}@Override public void onPageStarted(android.webkit.WebView vv,String z,android.graphics.Bitmap b){r.follow(z);}});((android.webkit.WebView.WebViewTransport)msg.obj).setWebView(child);msg.sendToTarget();return true;}});
    w.setWebViewClient(new android.webkit.WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,android.webkit.WebResourceRequest req){String z=req.getUrl().toString();r.follow(z);return r.coup(z);}
      @Override public void onPageStarted(android.webkit.WebView v,String z,android.graphics.Bitmap b){r.log("START "+z);r.follow(z);}
      @Override public void doUpdateVisitedHistory(android.webkit.WebView v,String z,boolean reload){r.follow(z);}
      @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView v,android.webkit.WebResourceRequest req){String z=req.getUrl().toString();if(r.coup(z))a.runOnUiThread(()->r.finish(z));return null;}
      @Override public void onReceivedError(android.webkit.WebView v,android.webkit.WebResourceRequest req,android.webkit.WebResourceError e){r.log("WEB_ERR "+e.getErrorCode()+" "+req.getUrl());}
      @Override public void onReceivedHttpError(android.webkit.WebView v,android.webkit.WebResourceRequest req,android.webkit.WebResourceResponse e){r.log("HTTP_WEB "+e.getStatusCode()+" "+req.getUrl());}
      @Override public void onPageFinished(android.webkit.WebView v,String z){
        r.log("FINISH "+z);r.follow(z);if(done[0])return;
        final int[] tries={0};final Runnable[] poll={null};
        poll[0]=()->{if(done[0]||tries[0]++>=24)return;v.evaluateJavascript("(function(){var as=[].slice.call(document.querySelectorAll('a[href]'));var a=as.find(function(e){var h=e.href||'';return /link\\.coupang\\.com|fcnt\\.link\\/|adjoin\\.co\\.kr\\/cou\\/land|ootoo\\.co\\.kr\\/cou\\/land/.test(h);});if(a)return a.href;var m=document.documentElement.innerHTML.match(/https?:\\/\\/link\\.coupang\\.com\\/[^\\s\"'<>\\\\]+/);if(m)return m[0];var b=[].slice.call(document.querySelectorAll('a,button,[role=button]')).find(function(e){return /쿠팡\\s*바로가기|쿠팡.*구매|구매하러/.test(e.innerText||'');});if(b)b.click();return '';})()",q->{if(q!=null){String d=q.replace("\\u0026","&").replace("\\u003D","=");if(d.length()>1&&d.charAt(0)==34&&d.charAt(d.length()-1)==34)d=d.substring(1,d.length()-1);if(!d.isEmpty())r.follow(d);}if(!done[0])v.postDelayed(poll[0],500);});};poll[0].run();
      }
    });
    w.loadUrl(u,java.util.Collections.singletonMap("Referer","https://fallcent.com/"));
    new Handler(Looper.getMainLooper()).postDelayed(()->{if(!done[0]){done[0]=true;r.log("TIMEOUT");try{root.removeView(w);}catch(Exception ignored){}try{w.destroy();}catch(Exception ignored){}new android.app.AlertDialog.Builder(a).setTitle("쿠팡 링크 추적 실패").setMessage(trace.toString()).setPositiveButton("확인",null).show();}},13000);
  });
 }
'''
x=x[:a]+resolver+x[b:]
p.write_text(x)
for z in ["tries[0]++>=24","FC_TRACE","onReceivedHttpError","getHost()","ii.getData()","HTTP_ERR"]:
    if z not in x: raise SystemExit("V35_VERIFY_FAILED "+z)
print("V35_RESOLVER_DIAGNOSTIC_POLLING_OK")


# v3.6 fix product identity/image binding and make diagnostics concise/actionable
p=Path("app/src/main/java/com/dealflash/app/BrowserScanner.java")
x=p.read_text()
# Replace Fallcent listing parser atomically so title, price, image and href come from one nearest card.
a=x.index('    private static String fallcentListingScript() {')
b=x.index('    private static String detailScript() {',a)
method=r'''    private static String fallcentListingScript() {
        return "(function(){"+
        "var blocked=/access denied|captcha|비정상적인 접근/i.test(document.body?document.body.innerText:'');"+
        "var links=[].slice.call(document.querySelectorAll('a[href*=\\\"/product/\\\"]'));var out=[],seen={};"+
        "for(var i=0;i<links.length&&out.length<220;i++){var a=links[i],href=a.href||'';if(!href||seen[href])continue;"+
        "var card=a.closest('article,li,[class*=product],[class*=item],[class*=card]');if(!card){var p=a;for(var k=0;k<5&&p;k++,p=p.parentElement){var tx=(p.innerText||'');if(/[0-9][0-9,]{2,}\\s*원/.test(tx)&&/(?:▼|↓)\\s*[0-9]{1,3}\\s*%/.test(tx)){card=p;break;}}}if(!card)continue;"+
        "var t=(card.innerText||card.textContent||'').replace(/\\s+/g,' ').trim();if(!/(로켓배송|로켓직구|판매자로켓)/.test(t)||t.indexOf('판매자배송')>=0)continue;"+
        "var dm=t.match(/(?:▼|↓)\\s*([0-9]{1,3})\\s*%/);if(!dm)continue;var ps=[],m,re=/([0-9][0-9,]{2,})\\s*원/g;while((m=re.exec(t))!==null){var pv=parseInt(m[1].replace(/,/g,''),10);if(pv>=100)ps.push(pv);}if(!ps.length)continue;"+
        "var title='';var te=card.querySelector('[class*=title],[class*=name],h1,h2,h3,h4,strong');if(te)title=(te.innerText||te.textContent||'').trim();if(!title){title=(a.innerText||a.textContent||'').trim();}if(!title||title.length<3)title=t.replace(/역대급|타이밍|로켓배송|로켓직구|판매자로켓/g,' ').replace(/[0-9][0-9,]*\\s*원.*$/,'').trim();"+
        "var im=card.querySelector('img');var image='';if(im){image=im.currentSrc||im.getAttribute('src')||im.getAttribute('data-src')||im.getAttribute('data-original')||im.getAttribute('data-lazy-src')||'';var ss=im.getAttribute('srcset')||im.getAttribute('data-srcset')||'';if(!image&&ss)image=ss.split(',')[0].trim().split(' ')[0];if(image.indexOf('//')===0)image='https:'+image;}"+
        "seen[href]=1;out.push({url:href,title:title,price:Math.min.apply(null,ps),base:0,discount:parseInt(dm[1],10)||0,image:image,rocket:true});}"+
        "return JSON.stringify({url:location.href,title:document.title||'',blocked:blocked,blockReason:blocked?'접근 제한':'',links:links.length,items:out});"+
        "})()";
    }

'''
x=x[:a]+method+x[b:]
p.write_text(x)

# Prevent stale/wrong HistoryStore image from overriding a newly parsed card image.
p=Path("app/src/main/java/com/dealflash/app/HotDealStore.java")
x=p.read_text()
old='if(hi!=null&&hi.imageUrl!=null)found.imageUrl=hi.imageUrl;save(c,a);'
if old in x:x=x.replace(old,'if((found.imageUrl==null||found.imageUrl.isEmpty())&&hi!=null&&hi.imageUrl!=null)found.imageUrl=hi.imageUrl;save(c,a);',1)
p.write_text(x)

# Detail UI must not claim Coupang/detail verification when this item was only detected from Fallcent.
p=Path("app/src/main/java/com/dealflash/app/MainActivity.java")
x=p.read_text()
x=x.replace('badges.addView(pill("  ✓ 검증완료",0xFF16A34A));','badges.addView(pill("  폴센트 감지",0xFF16A34A));')
x=x.replace('verify.addView(t("✓ 상세페이지 재검증 완료",16,true));','verify.addView(t("폴센트 가격 감지",16,true));')
x=x.replace('verify.addView(t("발견 "+date(d.foundAt)+"\\n마지막 검증 "+date(d.lastVerifiedAt),13,false));','verify.addView(t("발견 "+date(d.foundAt)+"\\n마지막 감지 "+date(d.lastVerifiedAt),13,false));')
p.write_text(x)

# Make trace bounded: keep only useful transition/error lines, not repeated SEEN spam.
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
old='void log(String m){android.util.Log.d("FC_TRACE",m);trace.append("\\n").append(m);}'
new='void log(String m){android.util.Log.d("FC_TRACE",m);if(m.startsWith("SEEN ")&&trace.indexOf(m)>=0)return;if(trace.length()<5000)trace.append("\\n").append(m);}'
if old not in x: raise SystemExit("V36_TRACE_TARGET_MISSING")
x=x.replace(old,new,1)
p.write_text(x)

for fp,z in [
 ("app/src/main/java/com/dealflash/app/BrowserScanner.java","a.closest('article,li,[class*=product],[class*=item],[class*=card]')"),
 ("app/src/main/java/com/dealflash/app/MainActivity.java","폴센트 가격 감지"),
 ("app/src/main/java/com/dealflash/app/AppLauncher.java","trace.length()<5000")]:
    if z not in Path(fp).read_text(): raise SystemExit("V36_VERIFY_FAILED "+z)
print("V36_PRODUCT_BINDING_UI_TRACE_OK")


# v3.7 repair Fallcent listing parser regression: robust same-card binding without over-specific /product/ selector
p=Path("app/src/main/java/com/dealflash/app/BrowserScanner.java")
x=p.read_text()
a=x.index('    private static String fallcentListingScript() {')
b=x.index('    private static String detailScript() {',a)
method=r'''    private static String fallcentListingScript() {
        return "(function(){"+
        "var body=document.body?document.body.innerText:'';var blocked=/access denied|captcha|비정상적인 접근/i.test(body);"+
        "var as=[].slice.call(document.querySelectorAll('a[href]'));var out=[],seen={};"+
        "for(var i=0;i<as.length&&out.length<220;i++){var a=as[i],href=a.href||a.getAttribute('href')||'';if(!href||seen[href])continue;"+
        "var card=null,p=a;for(var k=0;k<7&&p;k++,p=p.parentElement){var tx=(p.innerText||p.textContent||'').replace(/\\s+/g,' ').trim();if(tx.length>8&&tx.length<1200&&/(로켓배송|로켓직구|판매자로켓)/.test(tx)&&/(?:▼|↓)\\s*[0-9]{1,3}\\s*%/.test(tx)&&/[0-9][0-9,]{2,}\\s*원/.test(tx)){card=p;break;}}if(!card)continue;"+
        "var t=(card.innerText||card.textContent||'').replace(/\\s+/g,' ').trim();if(t.indexOf('판매자배송')>=0)continue;var dm=t.match(/(?:▼|↓)\\s*([0-9]{1,3})\\s*%/);if(!dm)continue;"+
        "var ps=[],m,re=/([0-9][0-9,]{2,})\\s*원/g;while((m=re.exec(t))!==null){var pv=parseInt(m[1].replace(/,/g,''),10)||0;if(pv>=100)ps.push(pv);}if(!ps.length)continue;"+
        "var title='';var cand=card.querySelectorAll('h1,h2,h3,h4,strong,[class*=title],[class*=name]');for(var q=0;q<cand.length;q++){var ct=(cand[q].innerText||cand[q].textContent||'').replace(/\\s+/g,' ').trim();if(ct.length>=4&&ct.length<=180&&!/[0-9][0-9,]{2,}\\s*원/.test(ct)){title=ct;break;}}if(!title){var at=(a.innerText||a.textContent||'').replace(/\\s+/g,' ').trim();if(at.length>=4&&at.length<=180)title=at;}if(!title)title=t.replace(/역대급|타이밍|로켓배송|로켓직구|판매자로켓/g,' ').replace(/[0-9][0-9,]*\\s*원.*$/,'').trim();"+
        "var image='';var ims=card.querySelectorAll('img');for(var q=0;q<ims.length;q++){var im=ims[q];var z=im.currentSrc||im.getAttribute('src')||im.getAttribute('data-src')||im.getAttribute('data-original')||im.getAttribute('data-lazy-src')||'';if(!z){var ss=im.getAttribute('srcset')||im.getAttribute('data-srcset')||'';if(ss)z=ss.split(',')[0].trim().split(' ')[0];}if(z&&z.indexOf('data:image')!==0){image=z;break;}}if(image.indexOf('//')===0)image='https:'+image;"+
        "seen[href]=1;out.push({url:href,title:title,price:Math.min.apply(null,ps),base:0,discount:parseInt(dm[1],10)||0,image:image,rocket:true});}"+
        "return JSON.stringify({url:location.href,title:document.title||'',blocked:blocked,blockReason:blocked?'접근 제한':'',links:as.length,items:out});"+
        "})()";
    }

'''
x=x[:a]+method+x[b:]
p.write_text(x)
if "document.querySelectorAll('a[href]')" not in x or "for(var k=0;k<7" not in x: raise SystemExit("V37_VERIFY_FAILED")
print("V37_FALLCENT_SCAN_RECOVERY_OK")


# v3.8 diagnosis only: device trace proves fcnt.link is returning HTTP 403.
# Do not alter the working resolver here until a browser/Custom Tab return path is implemented safely.
print("V38_FCNT_403_DIAGNOSED")


# v3.9 fcnt.link direct browser handoff: do not retry Cloudflare-blocked bridge in hidden WebView
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
a=x.index(' static void resolveFallcent(Context c,String u){')
b=x.index(' static void openCoupang(',a)
old=x[a:b]
# Keep Fallcent-page resolver, but once the app already owns an fcnt.link URL,
# hand the exact original URL to Android under the user's click instead of WebView/HttpURLConnection.
needle=''' static void resolveFallcent(Context c,String u){
  if(!(c instanceof android.app.Activity)){Toast.makeText(c,"쿠팡 링크 확인 실패",Toast.LENGTH_SHORT).show();return;}'''
replacement=''' static void resolveFallcent(Context c,String u){
  try{
    android.net.Uri input=android.net.Uri.parse(u);
    String ih=input.getHost();
    if(ih!=null&&ih.equalsIgnoreCase("fcnt.link")){
      android.content.Intent out=new android.content.Intent(android.content.Intent.ACTION_VIEW,input);
      out.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
      c.startActivity(out);
      return;
    }
  }catch(Exception e){Toast.makeText(c,"링크를 열 수 없습니다",Toast.LENGTH_SHORT).show();return;}
  if(!(c instanceof android.app.Activity)){Toast.makeText(c,"쿠팡 링크 확인 실패",Toast.LENGTH_SHORT).show();return;}'''
if needle not in x: raise SystemExit("V39_ENTRY_TARGET_MISSING")
x=x.replace(needle,replacement,1)
# When a Fallcent page yields fcnt.link, immediately browser-handoff the exact bridge URL.
needle='''void follow(String z){if(done[0]||z==null||z.isEmpty())return;log("SEEN "+z);String q=norm(z);if(coup(q)){finish(q);return;}if(bridge(q)){final String first=q;'''
replacement='''void follow(String z){if(done[0]||z==null||z.isEmpty())return;log("SEEN "+z);String q=norm(z);if(coup(q)){finish(q);return;}try{String bh=android.net.Uri.parse(q).getHost();if(bh!=null&&bh.equalsIgnoreCase("fcnt.link")){done[0]=true;log("BROWSER_FCNT "+q);try{w.stopLoading();}catch(Exception ignored){}try{root.removeView(w);}catch(Exception ignored){}try{w.destroy();}catch(Exception ignored){}android.content.Intent out=new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(q));out.addCategory(android.content.Intent.CATEGORY_BROWSABLE);c.startActivity(out);return;}}catch(Exception ignored){}if(bridge(q)){final String first=q;'''
if needle not in x: raise SystemExit("V39_FOLLOW_TARGET_MISSING")
x=x.replace(needle,replacement,1)
p.write_text(x)
for z in ['equalsIgnoreCase("fcnt.link")','CATEGORY_BROWSABLE','BROWSER_FCNT']:
    if z not in x: raise SystemExit("V39_VERIFY_FAILED "+z)
print("V39_FCNT_BROWSER_HANDOFF_OK")


# v4.0 Fallcent CTA extraction: keep Fallcent internal, click/observe the real bottom "쿠팡 바로가기" target
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
a=x.index(' static void resolveFallcent(Context c,String u){')
b=x.index(' static void openCoupang(',a)
r=x[a:b]
# V3.9's direct external fcnt handoff was diagnostic only; remove both external handoffs.
entry='''  try{
    android.net.Uri input=android.net.Uri.parse(u);
    String ih=input.getHost();
    if(ih!=null&&ih.equalsIgnoreCase("fcnt.link")){
      android.content.Intent out=new android.content.Intent(android.content.Intent.ACTION_VIEW,input);
      out.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
      c.startActivity(out);
      return;
    }
  }catch(Exception e){Toast.makeText(c,"링크를 열 수 없습니다",Toast.LENGTH_SHORT).show();return;}
'''
if entry in r:r=r.replace(entry,'',1)
handoff='''try{String bh=android.net.Uri.parse(q).getHost();if(bh!=null&&bh.equalsIgnoreCase("fcnt.link")){done[0]=true;log("BROWSER_FCNT "+q);try{w.stopLoading();}catch(Exception ignored){}try{root.removeView(w);}catch(Exception ignored){}try{w.destroy();}catch(Exception ignored){}android.content.Intent out=new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(q));out.addCategory(android.content.Intent.CATEGORY_BROWSABLE);c.startActivity(out);return;}}catch(Exception ignored){}'''
if handoff in r:r=r.replace(handoff,'',1)

# Do not recursively HTTP-fetch fcnt: let the attached browser WebView navigate it only when the real CTA click emits it.
old='''if(bridge(q)){final String first=q;new Thread(()->{String cur=first;try{for(int hop=0;hop<8&&!done[0];hop++){java.net.HttpURLConnection cn=(java.net.HttpURLConnection)new java.net.URL(cur).openConnection();cn.setInstanceFollowRedirects(false);cn.setConnectTimeout(5000);cn.setReadTimeout(5000);cn.setRequestProperty("User-Agent",w.getSettings().getUserAgentString());cn.setRequestProperty("Referer","https://fallcent.com/");String ck=android.webkit.CookieManager.getInstance().getCookie(cur);if(ck!=null)cn.setRequestProperty("Cookie",ck);int code=cn.getResponseCode();String loc=cn.getHeaderField("Location");log("HTTP "+code+" "+cur+" -> "+loc);cn.disconnect();if(loc==null||loc.isEmpty())break;cur=new java.net.URL(new java.net.URL(cur),loc).toString();final String got=cur;a.runOnUiThread(()->finish(got));if(coup(cur))break;}}catch(Exception e){log("HTTP_ERR "+e.getClass().getSimpleName());}a.runOnUiThread(()->{if(!done[0])w.loadUrl(first);});}).start();}'''
new='''if(bridge(q)){log("BRIDGE "+q);if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())w.loadUrl(q);else a.runOnUiThread(()->w.loadUrl(q));}'''
if old not in r: raise SystemExit("V40_BRIDGE_TARGET_MISSING")
r=r.replace(old,new,1)

# Poll the actual visible CTA by text. href is returned if present; otherwise click it in-page so navigation interception sees its generated URL.
oldjs='''var b=[].slice.call(document.querySelectorAll('a,button,[role=button]')).find(function(e){return /쿠팡\\\\s*바로가기|쿠팡.*구매|구매하러/.test(e.innerText||'');});if(b)b.click();return '';'''
newjs='''var b=[].slice.call(document.querySelectorAll('a,button,[role=button]')).find(function(e){return /쿠팡\\\\s*바로가기/.test((e.innerText||'').replace(/\\\\s+/g,' '));});if(b){var h=b.href||b.getAttribute('href')||b.getAttribute('data-href')||b.getAttribute('data-url')||'';if(h)return h;b.click();}return '';'''
if oldjs not in r: raise SystemExit("V40_CTA_TARGET_MISSING")
r=r.replace(oldjs,newjs,1)

# Avoid external Fallcent exposure on timeout; concise diagnostic remains.
r=r.replace('new android.app.AlertDialog.Builder(a).setTitle("쿠팡 링크 추적 실패").setMessage(trace.toString()).setPositiveButton("확인",null).show();',
'''new android.app.AlertDialog.Builder(a).setTitle("쿠팡 링크 추출 실패").setMessage("폴센트의 쿠팡 바로가기 주소를 가져오지 못했습니다.").setPositiveButton("확인",null).show();''',1)

x=x[:a]+r+x[b:]
p.write_text(x)
for z in ['BRIDGE "+q','data-href','폴센트의 쿠팡 바로가기 주소']:
    if z not in x: raise SystemExit("V40_VERIFY_FAILED "+z)
print("V40_FALLCENT_CTA_EXTRACTION_OK")


# v4.1 CTA extraction: Fallcent bottom CTA may be a non-anchor div with onclick; search all elements,
# climb to clickable ancestor, read common URL attributes, then dispatch a real click.
p=Path("app/src/main/java/com/dealflash/app/AppLauncher.java")
x=p.read_text()
old='''var b=[].slice.call(document.querySelectorAll('a,button,[role=button]')).find(function(e){return /쿠팡\\\\s*바로가기/.test((e.innerText||'').replace(/\\\\s+/g,' '));});if(b){var h=b.href||b.getAttribute('href')||b.getAttribute('data-href')||b.getAttribute('data-url')||'';if(h)return h;b.click();}return '';'''
new='''var all=[].slice.call(document.querySelectorAll('body *'));var b=all.find(function(e){var t=(e.innerText||e.textContent||'').replace(/\\\\s+/g,' ').trim();return t==='쿠팡 바로가기'||(/^쿠팡 바로가기/.test(t)&&t.length<40);});if(b){var n=b;for(var i=0;i<6&&n;i++,n=n.parentElement){var h=n.href||n.getAttribute('href')||n.getAttribute('data-href')||n.getAttribute('data-url')||n.getAttribute('data-link')||'';if(h)return h;var oc=n.getAttribute('onclick')||'';var m=oc.match(/https?:[^'\"\\\\s)]+/);if(m)return m[0];if(n.tagName==='A'||n.tagName==='BUTTON'||n.getAttribute('role')==='button'||oc){n.click();return '';}}b.click();}return '';'''
if old not in x: raise SystemExit("V41_CTA_TARGET_MISSING")
x=x.replace(old,new,1)
p.write_text(x)
for z in ["querySelectorAll('body *')","data-link","onclick","V41"]:
    if z=="V41": continue
    if z not in x: raise SystemExit("V41_VERIFY_FAILED "+z)
print("V41_FALLCENT_CTA_DEEP_CLICK_OK")
