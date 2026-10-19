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
                "var as=Array.prototype.slice.call(document.querySelectorAll('a[href*=\"/product/\"]'));var out=[],seen={};"+
                "for(var j=0;j<as.length&&out.length<180;j++){var a=as[j],href=a.href||a.getAttribute('href')||'';if(!href||seen[href])continue;var b=a,t='';for(var k=0;k<7&&b;k++,b=b.parentElement){var q=(b.innerText||b.textContent||'').replace(/\\s+/g,' ').trim();if((q.indexOf('로켓배송')>=0||q.indexOf('로켓직구')>=0||q.indexOf('판매자로켓')>=0)&&/(?:▼|↓)\\s*[0-9]{1,3}\\s*%/.test(q)&&/[0-9][0-9,]{2,}\\s*원/.test(q)){t=q;break;}}if(!t)t=(a.innerText||a.textContent||'').replace(/\\s+/g,' ').trim();"+
                "if(t.indexOf('판매자배송')>=0)continue;if(!(t.indexOf('로켓배송')>=0||t.indexOf('로켓직구')>=0||t.indexOf('판매자로켓')>=0))continue;"+
                "var dm=t.match(/(?:▼|↓)\\s*([0-9]{1,3})\\s*%/);var prices=[],mm,re=/([0-9][0-9,]{2,})\\s*원/g;while((mm=re.exec(t))!==null){var pv=parseInt(mm[1].replace(/,/g,''),10)||0;if(pv>0)prices.push(pv);}var price=prices.length?Math.min.apply(null,prices):n(t);if(!dm||!price)continue;"+
                "var title=t.replace(/역대급|타이밍|로켓배송|로켓직구|판매자로켓/g,' ').replace(/[0-9][0-9,]*\\s*원.*$/,'').replace(/\\s+/g,' ').trim();"+
                "var root=b&&b.parentElement?b.parentElement:a.parentElement;var im=(root&&root.querySelector?root.querySelector('img'):null)||a.querySelector('img');var image=im?(im.currentSrc||im.src||im.getAttribute('data-src')||''):'';"+
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
