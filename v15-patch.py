from pathlib import Path
p=Path("app/src/main/java/com/dealflash/app/BrowserScanner.java")
s=p.read_text()
marker='    public synchronized PageResult scanListings(String url, String source, long timeoutMs) {\n        PageResult out = new PageResult();\n'
inject='''        if ("FALLCENT".equals(source)) {
            WebFetcher.Result r = WebFetcher.fetch(url);
            out.finalUrl = r.finalUrl;
            if (r.code >= 200 && r.code < 400 && r.body != null && !r.body.isEmpty()) {
                out.items.addAll(PageScanner.scan(r.body, r.finalUrl, source));
                out.productLinks = out.items.size();
                out.ok = !out.items.isEmpty();
                out.status = out.ok ? "정상 " + out.items.size() + "상품" : "폴센트 상품 파싱 0개";
                if (!out.ok) out.error = out.status;
            } else {
                out.blocked = r.code == 403 || r.code == 429;
                out.error = r.code > 0 ? "HTTP " + r.code : (r.error.isEmpty() ? "네트워크 오류" : r.error);
                out.status = out.error;
            }
            return out;
        }
'''
if marker not in s: raise SystemExit("BrowserScanner target missing")
p.write_text(s.replace(marker,marker+inject,1))
p=Path("app/src/main/java/com/dealflash/app/MonitorService.java")
s=p.read_text()
marker='            HistoryStore.save(this,history);\n'
inject='''            if(p.ok) ServerSync.uploadDeals(p.items, feed.source);
            ServerSync.monitor(p.ok?200:(p.blocked?403:(p.timeout?408:500)), p.items==null?0:p.items.size(), (int)(hits-ScannerPrefs.hits(this)), System.currentTimeMillis()-roundStart, p.ok?null:state);
'''
if marker not in s: raise SystemExit("Monitor target missing")
p.write_text(s.replace(marker,marker+inject,1))
