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
