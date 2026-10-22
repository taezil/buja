package com.dealflash.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Browser-backed scanner. It intentionally behaves like a normal Android WebView and does not
 * attempt to bypass CAPTCHAs or access controls. When Coupang serves a challenge/access-denied
 * page, that is reported as a diagnostic instead of being treated as a product page.
 */
public final class BrowserScanner {
    public static final class PageResult {
        public final List<PageScanner.Listing> items = new ArrayList<>();
        public String finalUrl = "";
        public String title = "";
        public String status = "";
        public String error = "";
        public int productLinks = 0;
        /** 검증에서 제외된 항목 수 / 카드 경계를 확신할 수 없어 건너뛴 수 / 진단 메모 */
        public int rejected = 0, ambiguous = 0;
        public String note = "", path = "";
        public boolean blocked = false;
        public boolean timeout = false;
        public boolean ok = false;
    }

    public static final class DetailResult {
        public String url = "";
        public String title = "";
        public String error = "";
        public boolean blocked = false;
        public boolean timeout = false;
        public boolean ok = false;
        public double current = 0;
        public double base = 0;
        public boolean currentMatchedExpected = false;
        /** Every distinct price the page matched (page-wide); a single one is required for ok=true, but all are kept for diagnostics. */
        public final List<Double> currentCandidates = new ArrayList<>();
        /** Short, human-readable trace of what the page looked like (final host/path, title, http/web error, candidate counts). */
        public String debug = "";
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CountDownLatch initLatch = new CountDownLatch(1);
    private final AtomicLong tokenCounter = new AtomicLong();
    private WebView webView;
    private volatile Session current;

    private static final class Session {
        long token;
        boolean detail;
        String source;
        String requestedUrl;
        double expectedCurrent;
        double expectedBase;
        final CountDownLatch done = new CountDownLatch(1);
        PageResult page;
        DetailResult detailResult;
        volatile boolean finished;
        int httpStatus;
        String receivedError = "";
        int extractAttempts = 0;
    }

    public BrowserScanner(Context c) {
        context = c.getApplicationContext();
        if(Looper.myLooper()==Looper.getMainLooper())createWebView();else main.post(this::createWebView);
        try { initLatch.await(6, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        try {
            webView = new WebView(context);
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setLoadsImagesAutomatically(false);
            s.setBlockNetworkImage(true);
            s.setJavaScriptCanOpenWindowsAutomatically(false);
            s.setSupportMultipleWindows(false);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            if (android.os.Build.VERSION.SDK_INT >= 23) s.setOffscreenPreRaster(true);
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            try { cm.setAcceptThirdPartyCookies(webView, true); } catch (Throwable ignored) {}
            webView.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
                    Session s = current;
                    if (s == null || s.finished) return;
                    long token = s.token;
                    // Give dynamic product cards time to render, then scroll twice to trigger lazy DOM work.
                    main.postDelayed(() -> {
                        Session now = current;
                        if (now == null || now.finished || now.token != token) return;
                        try { view.evaluateJavascript("window.scrollTo(0, Math.min(document.body.scrollHeight, 1400));", null); } catch (Throwable ignored) {}
                        main.postDelayed(() -> {
                            Session now2 = current;
                            if (now2 == null || now2.finished || now2.token != token) return;
                            try { view.evaluateJavascript("window.scrollTo(0, Math.min(document.body.scrollHeight, 3200));", null); } catch (Throwable ignored) {}
                            main.postDelayed(() -> extract(view, token), 850L);
                        }, 700L);
                    }, 900L);
                }

                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request != null && request.isForMainFrame()) {
                        Session s = current;
                        if (s != null && !s.finished) s.receivedError = "WEB " + error.getErrorCode() + " " + String.valueOf(error.getDescription());
                    }
                }

                @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                    if (request != null && request.isForMainFrame()) {
                        Session s = current;
                        if (s != null && !s.finished && errorResponse != null) s.httpStatus = errorResponse.getStatusCode();
                    }
                }
            });
            // A non-attached WebView can still execute JS. Give it a realistic viewport for lazy layouts.
            int w = 1080, h = 2400;
            webView.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY));
            webView.layout(0, 0, w, h);
        } catch (Throwable ignored) {
            webView = null;
        } finally {
            initLatch.countDown();
        }
    }

    public synchronized PageResult scanListings(String url, String source, long timeoutMs) {
        PageResult out = new PageResult();
        if (webView == null) { out.error = "WebView 초기화 실패"; return out; }
        Session s = new Session();
        s.token = tokenCounter.incrementAndGet();
        s.detail = false;
        s.source = source;
        s.requestedUrl = url;
        s.page = out;
        current = s;
        main.post(() -> startLoad(s));
        waitFor(s, timeoutMs);
        if (!s.finished) {
            out.timeout = true; out.error = "브라우저 로딩 시간초과"; finishSession(s);
        }
        return out;
    }

    public synchronized DetailResult scanDetail(String url, String source, double expectedCurrent, double expectedBase, long timeoutMs) {
        DetailResult out = new DetailResult();
        if (webView == null) { out.error = "WebView 초기화 실패"; return out; }
        Session s = new Session();
        s.token = tokenCounter.incrementAndGet();
        s.detail = true;
        s.source = source;
        s.requestedUrl = url;
        s.expectedCurrent = expectedCurrent;
        s.expectedBase = expectedBase;
        s.detailResult = out;
        current = s;
        main.post(() -> startLoad(s));
        waitFor(s, timeoutMs);
        if (!s.finished) {
            out.timeout = true; out.error = "상세페이지 로딩 시간초과"; out.debug = debugLine(safeUrl(), "", s.httpStatus, s.receivedError, -1, -1); finishSession(s);
        }
        return out;
    }

    private void startLoad(Session s) {
        if (webView == null || current != s || s.finished) { finishSession(s); return; }
        try {
            webView.stopLoading();
            webView.loadUrl(s.requestedUrl);
        } catch (Throwable t) {
            if (s.detail) s.detailResult.error = "브라우저 열기 실패";
            else s.page.error = "브라우저 열기 실패";
            finishSession(s);
        }
    }

    private void waitFor(Session s, long timeoutMs) {
        try { s.done.await(Math.max(6000L, timeoutMs), TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void extract(WebView view, long token) {
        Session s = current;
        if (s == null || s.finished || s.token != token) return;
        String script = s.detail ? detailScript() : ("FALLCENT".equals(s.source) ? fallcentListingScript() : listingScript());
        try {
            s.extractAttempts++;
            view.evaluateJavascript(script, value -> {
                Session now = current;
                if (now == null || now.finished || now.token != token) return;
                if (now.detail) {
                    parseDetail(now, value);
                    finishSession(now);
                } else {
                    parseListing(now, value);
                    // Dynamic listing prices sometimes appear after product links. Re-read the same
                    // already-loaded DOM once before declaring it empty; no extra HTTP navigation.
                    if (!now.page.ok && !now.page.blocked && now.extractAttempts < 3) {
                        main.postDelayed(() -> {
                            Session again = current;
                            if (again == null || again.finished || again.token != token) return;
                            try { view.evaluateJavascript("window.scrollTo(0, Math.min(document.body.scrollHeight, 5200));", null); } catch (Throwable ignored) {}
                            main.postDelayed(() -> extract(view, token), 1200L);
                        }, 1200L);
                    } else finishSession(now);
                }
            });
        } catch (Throwable t) {
            if (s.detail) s.detailResult.error = "DOM 읽기 실패";
            else s.page.error = "DOM 읽기 실패";
            finishSession(s);
        }
    }

    private void parseListing(Session s, String raw) {
        PageResult out = s.page;
        out.items.clear();out.ok=false;
        out.finalUrl = safeUrl();
        if (s.httpStatus >= 400) out.error = "HTTP " + s.httpStatus;
        if (!s.receivedError.isEmpty()) out.error = s.receivedError;
        try {
            JSONObject root = new JSONObject(decodeJsString(raw));
            out.finalUrl = root.optString("url", out.finalUrl);
            out.title = root.optString("title", "");
            out.productLinks = root.optInt("links", 0);
            out.blocked = root.optBoolean("blocked", false);
            String why = root.optString("blockReason", "");
            if (out.blocked || s.httpStatus>=400 || !s.receivedError.isEmpty()) {
                out.error = why.isEmpty() ? "접근 제한/캡차" : why;
                out.status = out.error;
                return;
            }
            out.ambiguous = root.optInt("ambiguous", 0);
            out.path = root.optString("path", "");
            final boolean fallcent = "FALLCENT".equals(s.source);
            final List<ListingIntegrity.Rec> recs = new ArrayList<>();
            JSONArray items = root.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length() && out.items.size() < 260; i++) {
                    JSONObject o = items.optJSONObject(i); if (o == null) continue;
                    String href = o.optString("url", "");
                    if ("COUPANG".equals(s.source)) href = ProductUrl.canonicalCoupang(href);
                    else href = ProductUrl.canonical(s.source, href);
                    if (href == null) continue;
                    double price = o.optDouble("price", 0);
                    if (!Double.isFinite(price) || price < 100 || price > 1_000_000_000L) continue;
                    PageScanner.Listing x = new PageScanner.Listing();
                    x.url = href;
                    x.key = ProductUrl.key(s.source, href);
                    // Canonical URL/key is authoritative. Do not reject a safely isolated card
                    // just because Fallcent changed the detail URL shape.
                    String rawTitle = o.optString("title", "상품");
                    x.title = cleanTitle(fallcent ? ListingIntegrity.cleanTitle(rawTitle) : rawTitle);
                    x.price = price;
                    x.basePrice = o.optDouble("base", 0);
                    if (!PageScanner.validPair(x.basePrice, x.price)) x.basePrice = 0;
                    x.declaredDiscountPct = o.optInt("discount", 0);
                    x.imageUrl = o.optString("image", "");
                    x.linkedCoupangUrl=o.optString("coupangUrl", "");
                    x.rocket = o.optBoolean("rocket", false);
                    out.items.add(x);
                    recs.add(new ListingIntegrity.Rec(x.key, x.title, x.imageUrl, x.price, x.declaredDiscountPct,
                            o.optDouble("embPrice", 0), o.optInt("embPct", 0), o.optBoolean("weak", false)));
                }
            }
            if (fallcent && !recs.isEmpty()) {
                // 제목에 적힌 가격/할인율과 읽은 값이 다르거나, 같은 (이미지,가격,할인율)이 여러 상품에 반복되면 제외.
                ListingIntegrity.Report rep = new ListingIntegrity.Report();
                java.util.Set<ListingIntegrity.Rec> ok = new java.util.HashSet<>(ListingIntegrity.filter(recs, rep));
                List<PageScanner.Listing> kept = new ArrayList<>();
                for (int idx=0;idx<out.items.size();idx++) if(ok.contains(recs.get(idx)))kept.add(out.items.get(idx));
                out.items.clear(); out.items.addAll(kept);
                out.rejected = rep.dropped(); out.note = rep.summary();
            }
            if (!out.items.isEmpty()) {
                out.ok = true;
                out.status = "정상 " + out.items.size() + "상품" + (out.rejected > 0 ? " · 검증제외 " + out.rejected : "");
            } else if (out.ambiguous > 0) {
                out.error = "카드 경계 모호 " + out.ambiguous + "개 · " + out.path;
                out.status = out.error;
            } else if (out.productLinks > 0) {
                boolean bodyHasPrices = root.optBoolean("bodyHasPrices", false);
                out.error = bodyHasPrices
                        ? "상품 링크 " + out.productLinks + "개 · 카드 연결 실패"
                        : "상품 링크 " + out.productLinks + "개 · 가격 DOM 없음";
                out.status = out.error;
            } else {
                out.error = out.error.isEmpty() ? "상품 DOM 없음" : out.error;
                out.status = out.error;
            }
        } catch (Exception e) {
            out.error = "DOM JSON 파싱 실패";
            out.status = out.error;
        }
    }

    private void parseDetail(Session s, String raw) {
        DetailResult out = s.detailResult;
        out.url = safeUrl();
        if (s.httpStatus >= 400) out.error = "HTTP " + s.httpStatus;
        if (!s.receivedError.isEmpty()) out.error = s.receivedError;
        try {
            JSONObject root = new JSONObject(decodeJsString(raw));
            out.url = root.optString("url", out.url);
            out.title = cleanTitle(root.optString("title", ""));
            out.blocked = root.optBoolean("blocked", false);
            if (out.blocked || s.httpStatus>=400 || !s.receivedError.isEmpty()) { out.error = root.optString("blockReason", "접근 제한/캡차"); return; }
            List<Double> currents = toList(root.optJSONArray("current"));
            List<Double> bases = toList(root.optJSONArray("base"));
            out.currentCandidates.addAll(currents);
            out.debug = debugLine(out.url, out.title, s.httpStatus, s.receivedError, currents.size(), bases.size());
            out.current = currents.size()==1 ? currents.get(0) : 0; // Ambiguous page-wide prices cannot verify a product.
            if (s.expectedCurrent > 0 && out.current > 0) {
                double tol = Math.max(300.0, s.expectedCurrent * 0.02);
                out.currentMatchedExpected = Math.abs(out.current - s.expectedCurrent) <= tol;
            } else out.currentMatchedExpected = out.current > 0;
            out.base = chooseBase(bases, out.current, s.expectedBase);
            out.ok = out.current > 0;
            if (!out.ok && out.error.isEmpty()) out.error = currents.isEmpty() ? "상세가격 DOM 없음" : "가격 후보 " + currents.size() + "개(하나로 특정 불가)";
        } catch (Exception e) {
            out.error = "상세 DOM JSON 파싱 실패";
            if (out.debug.isEmpty()) out.debug = debugLine(out.url, out.title, s.httpStatus, s.receivedError, -1, -1);
        }
    }

    /** e.g. "m.coupang.com/vm/products/123 | 상품명… | http 200 | cur 2 base 1" - enough to tell a redirect page / block / wrong DOM apart. */
    static String debugLine(String url, String title, int http, String webErr, int cur, int base) {
        String u = url == null ? "" : url;
        try { android.net.Uri p = android.net.Uri.parse(u); String path = p.getPath() == null ? "" : p.getPath(); u = (p.getHost() == null ? "" : p.getHost()) + (path.length() > 28 ? path.substring(0, 28) : path); } catch (Throwable ignored) {}
        String t = title == null ? "" : title.trim(); if (t.length() > 18) t = t.substring(0, 18);
        return u + " | " + t + " | http " + http + (webErr == null || webErr.isEmpty() ? "" : " | " + webErr) + (cur >= 0 ? " | cur " + cur + " base " + base : "");
    }

    private static List<Double> toList(JSONArray a) {
        ArrayList<Double> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length() && out.size() < 40; i++) {
            double v = a.optDouble(i, 0);
            if (v >= 100 && v <= 1_000_000_000L) {
                boolean dup = false; for (double x : out) if (Math.abs(x-v) < 0.5) { dup = true; break; }
                if (!dup) out.add(v);
            }
        }
        return out;
    }

    private static double chooseClosest(List<Double> list, double expected) {
        if (list.isEmpty()) return 0;
        if (expected <= 0) return list.get(0);
        double best = list.get(0), d = Math.abs(best - expected);
        for (double v : list) { double nd = Math.abs(v - expected); if (nd < d) { best = v; d = nd; } }
        return best;
    }

    private static double chooseBase(List<Double> list, double current, double expectedBase) {
        double best = 0, bestScore = Double.MAX_VALUE;
        for (double v : list) {
            if (current <= 0 || v <= current) continue;
            double pct = (1.0 - current / v) * 100.0;
            if (pct < 1 || pct > 95) continue;
            double score = expectedBase > current ? Math.abs(v - expectedBase) : (v-current);
            if (score < bestScore) { best = v; bestScore = score; }
        }
        return best;
    }

    private String safeUrl() {
        try { return webView == null ? "" : String.valueOf(webView.getUrl()); } catch (Throwable t) { return ""; }
    }

    private void finishSession(Session s) {
        if (s == null || s.finished) return;
        s.finished = true;
        s.done.countDown();
    }

    private static String decodeJsString(String raw) throws Exception {
        if (raw == null || "null".equals(raw)) return "{}";
        // evaluateJavascript returns a JSON string literal around our JSON.stringify result.
        JSONArray box = new JSONArray("[" + raw + "]");
        return box.getString(0);
    }

    private static String cleanTitle(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private static String listingScript() {
        return "(function(){"+
                "function n(t){if(!t)return 0;var m=String(t).replace(/\\u00a0/g,' ').match(/([0-9][0-9,]{2,})/);if(!m)return 0;var v=parseInt(m[1].replace(/,/g,''),10);return (v>=100&&v<=1000000000)?v:0;}"+
                "function val(box,sels){for(var i=0;i<sels.length;i++){var e=box.querySelector(sels[i]);if(e){var v=n(e.getAttribute('content')||e.textContent);if(v)return v;}}return 0;}"+
                "function txt(box,sels){for(var i=0;i<sels.length;i++){var e=box.querySelector(sels[i]);if(e&&e.textContent&&e.textContent.trim())return e.textContent.trim();}return '';}"+
                "function box(a){var x=a;for(var i=0;i<7&&x;i++,x=x.parentElement){var c=(x.className||'').toString().toLowerCase();if(x.tagName==='LI'||c.indexOf('search-product')>=0||c.indexOf('product-unit')>=0||c.indexOf('product-card')>=0||c.indexOf('baby-product')>=0)return x;}return a.parentElement||a;}"+
                "var bt=(document.body&&document.body.innerText?document.body.innerText:'').toLowerCase();"+
                "var blocked=(bt.indexOf('access denied')>=0||bt.indexOf('captcha')>=0||bt.indexOf('자동입력')>=0||bt.indexOf('로봇이 아닙니다')>=0||bt.indexOf('비정상적인 접근')>=0);"+
                "var reason=bt.indexOf('captcha')>=0?'CAPTCHA':(bt.indexOf('access denied')>=0?'Access Denied':(bt.indexOf('비정상적인 접근')>=0?'비정상 접근 차단':(bt.indexOf('자동입력')>=0?'자동입력 확인':'')));"+
                "var as=Array.prototype.slice.call(document.querySelectorAll('a[href*=\"/vp/products/\"],a[href*=\"/np/products/\"],a[href*=\"/vm/products/\"]'));var out=[],seen={};"+
                "for(var j=0;j<as.length&&out.length<260;j++){var a=as[j],href=a.href||a.getAttribute('href')||'';var mm=href.match(/\\/(?:vp|np|vm)\\/products\\/(\\d+)/i);if(!mm)continue;var u=new URL(href),vk=mm[1]+'|'+(u.searchParams.get('vendorItemId')||u.searchParams.get('itemId')||'');if(seen[vk])continue;var b=box(a);var links=b.querySelectorAll('a[href]'),other=false;for(var z=0;z<links.length;z++){var lu=new URL(links[z].href),lm=lu.pathname.match(/products\\/(\\d+)/);if(lm&&(lm[1]+'|'+(lu.searchParams.get('vendorItemId')||lu.searchParams.get('itemId')||''))!==vk)other=true;}if(other)continue;"+
                "var price=val(b,['strong.price-value','.price-value','[class*=\"price-value\"]','[class*=\"sale-price\"]','[class*=\"sales-price\"]','[class*=\"discount-price\"]','[class*=\"final-price\"]']);"+
                "if(!price)continue;var base=val(b,['.base-price','[class*=\"base-price\"]','[class*=\"original-price\"]','[class*=\"regular-price\"]','[class*=\"list-price\"]','del','s']);"+
                "var title=txt(b,['.name','[class*=\"product-name\"]','[class*=\"product-title\"]','[class*=\"name\"]']);if(!title)title=(a.getAttribute('title')||a.textContent||'').trim();"+
                "var disc=0;var de=b.querySelector('[class*=\"discount-percentage\"],[class*=\"discount-rate\"],[class*=\"discount\"]');if(de){var dm=(de.textContent||'').match(/([0-9]{1,3})\\s*%/);if(dm)disc=parseInt(dm[1],10)||0;}"+
                "var im=b.querySelector('img');var image=im?(im.currentSrc||im.src||im.getAttribute('data-img-src')||im.getAttribute('data-src')||''):'';if(image&&image.indexOf('//')===0)image='https:'+image;var rocket=/로켓배송|로켓와우|rocket/i.test(b.innerText||'');"+
                "seen[vk]=1;out.push({url:href,title:title,price:price,base:base,discount:disc,image:image,rocket:rocket});}"+
                "return JSON.stringify({url:location.href,title:document.title||'',blocked:blocked,blockReason:reason,links:as.length,items:out});"+
                "})()";
    }

    private String fallcentScript;
    /** 수집 스크립트는 assets/fallcent_listing.js 에 있다 (읽기 쉽고 테스트 가능). */
    private String fallcentListingScript() {
        if (fallcentScript != null) return fallcentScript;
        try (java.io.InputStream in = context.getAssets().open("fallcent_listing.js");
             java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            fallcentScript = bo.toString("UTF-8");
        } catch (Exception e) {
            fallcentScript = "JSON.stringify({url:location.href,title:'',blocked:false,links:0,items:[],ambiguous:0,path:'script-load-failed'})";
        }
        return fallcentScript;
    }

    private static String detailScript() {
        return "(function(){"+
                "function n(t){if(!t)return 0;var m=String(t).replace(/\\u00a0/g,' ').match(/([0-9][0-9,]{2,})/);if(!m)return 0;var v=parseInt(m[1].replace(/,/g,''),10);return (v>=100&&v<=1000000000)?v:0;}"+
                "function add(arr,v){if(!v)return;for(var i=0;i<arr.length;i++)if(Math.abs(arr[i]-v)<1)return;arr.push(v);}"+
                "function vals(root,sels){var a=[];for(var i=0;i<sels.length;i++){var es=root.querySelectorAll(sels[i]);for(var j=0;j<es.length&&a.length<40;j++)add(a,n(es[j].getAttribute('content')||es[j].textContent));}return a;}"+
                "function selectedCard(){var all=document.querySelectorAll('body *'),label=null;for(var i=0;i<all.length;i++){var t=(all[i].textContent||'').replace(/\\s+/g,' ').trim();if(t==='선택한 상품'){label=all[i];break;}}if(!label)return null;"+
                "var x=label;for(var d=0;d<9&&x&&x!==document.body;d++,x=x.parentElement){var text=(x.innerText||'').replace(/\\s+/g,' ').trim();if(text.indexOf('선택한 상품')<0)continue;var links=x.querySelectorAll('a[href*=\"/products/\"]');var prices=x.querySelectorAll('[class*=\"price\"],strong,del,s');if(links.length>=1&&prices.length>=1&&text.length<1200)return x;}return label.parentElement;}"+
                "function salePrice(card){if(!card)return 0;var sels=['strong.price-value','.price-value','[class*=\"price-value\"]','[class*=\"sale-price\"]','[class*=\"sales-price\"]','[class*=\"discount-price\"]','[class*=\"final-price\"]','strong'];"+
                "for(var i=0;i<sels.length;i++){var es=card.querySelectorAll(sels[i]);for(var j=0;j<es.length;j++){var e=es[j],txt=(e.textContent||'').replace(/\\s+/g,' ').trim();if(txt.indexOf('캐시')>=0||txt.indexOf('적립')>=0||txt.indexOf('1매당')>=0)continue;var st='';try{st=getComputedStyle(e).textDecorationLine||getComputedStyle(e).textDecoration||'';}catch(z){}if(e.closest('del,s')||st.indexOf('line-through')>=0)continue;var v=n(txt);if(v>=1000)return v;}}"+
                "var nodes=card.querySelectorAll('*');for(var k=0;k<nodes.length;k++){var q=nodes[k],tx=(q.textContent||'').replace(/\\s+/g,' ').trim();if(!/^[0-9][0-9,]{2,}\\s*원(?:\\s|$)/.test(tx)||tx.indexOf('캐시')>=0||tx.indexOf('적립')>=0||tx.indexOf('1매당')>=0)continue;var ds='';try{ds=getComputedStyle(q).textDecorationLine||getComputedStyle(q).textDecoration||'';}catch(z){}if(q.closest('del,s')||ds.indexOf('line-through')>=0)continue;var vv=n(tx);if(vv>=1000)return vv;}return 0;}"+
                "var bt=(document.body&&document.body.innerText?document.body.innerText:'').toLowerCase();var blocked=(bt.indexOf('access denied')>=0||bt.indexOf('captcha')>=0||bt.indexOf('자동입력')>=0||bt.indexOf('로봇이 아닙니다')>=0||bt.indexOf('비정상적인 접근')>=0);"+
                "var reason=bt.indexOf('captcha')>=0?'CAPTCHA':(bt.indexOf('access denied')>=0?'Access Denied':(bt.indexOf('비정상적인 접근')>=0?'비정상 접근 차단':''));"+
                "function geoPrice(){var all=document.querySelectorAll('body *'),lab=null;for(var i=0;i<all.length;i++){var tt=(all[i].textContent||'').replace(/\\s+/g,' ').trim();if(tt==='선택한 상품'){lab=all[i];break;}}if(!lab)return 0;var lr=lab.getBoundingClientRect(),best=0,bestScore=1e9;for(var j=0;j<all.length;j++){var e=all[j];if(e.children&&e.children.length>0)continue;var tx=(e.textContent||'').replace(/\\s+/g,' ').trim();if(!/^[0-9][0-9,]{2,}\\s*원(?:\\s*\\([^)]*\\))?$/.test(tx))continue;if(tx.indexOf('캐시')>=0||tx.indexOf('적립')>=0||tx.indexOf('1매당')>=0)continue;var r=e.getBoundingClientRect();if(r.width<=0||r.height<=0)continue;var dx=Math.abs((r.left+r.right)/2-(lr.left+lr.right)/2),dy=r.top-lr.bottom;if(dy<0||dy>900||dx>420)continue;var cs=null;try{cs=getComputedStyle(e);}catch(z){}var deco=cs?(cs.textDecorationLine||cs.textDecoration||''):'';if(e.closest('del,s')||deco.indexOf('line-through')>=0)continue;var v=n(tx);if(v<1000)continue;var fs=cs?parseFloat(cs.fontSize)||0:0,weight=cs?parseInt(cs.fontWeight,10)||400:400;var score=dy+dx*0.35-fs*8-(weight>=600?80:0);if(score<bestScore){bestScore=score;best=v;}}return best;}"+
                "var card=selectedCard(),cur=[],bas=[],selected=false;if(card){var sp=salePrice(card);if(sp){add(cur,sp);selected=true;}bas=vals(card,['.base-price','[class*=\"base-price\"]','[class*=\"original-price\"]','[class*=\"regular-price\"]','[class*=\"list-price\"]','del','s']);}if(!selected){var gp=geoPrice();if(gp){add(cur,gp);selected=true;}}"+
                "if(!selected){cur=vals(document,['meta[itemprop=\"price\"]','meta[property=\"product:price:amount\"]','.total-price strong','.total-price','[class*=\"total-price\"] strong','[class*=\"sale-price\"]','[class*=\"sales-price\"]','[class*=\"discount-price\"]','.price-value','[class*=\"price-value\"]']);bas=vals(document,['.base-price','[class*=\"base-price\"]','[class*=\"original-price\"]','[class*=\"regular-price\"]','[class*=\"list-price\"]','del','s']);"+
                "try{var scripts=document.querySelectorAll('script[type=\"application/ld+json\"]');for(var a=0;a<scripts.length;a++){var o=JSON.parse(scripts[a].textContent||'{}');var ar=Array.isArray(o)?o:[o];for(var z=0;z<ar.length;z++){var of=ar[z]&&ar[z].offers;if(of){of=Array.isArray(of)?of[0]:of;if(of)add(cur,n(of.price));}}}}catch(e){}}"+
                "var title='';if(card){var names=card.querySelectorAll('[class*=\"name\"],[class*=\"title\"],a[href*=\"/products/\"]');for(var y=0;y<names.length;y++){var nt=(names[y].textContent||'').replace(/\\s+/g,' ').trim();if(nt&&nt!=='선택한 상품'&&nt.length>8){title=nt;break;}}}"+
                "if(!title){var h=document.querySelector('h1');if(h)title=(h.textContent||'').trim();}if(!title){var og=document.querySelector('meta[property=\"og:title\"]');if(og)title=og.getAttribute('content')||'';}if(!title)title=document.title||'';"+
                "return JSON.stringify({url:location.href,title:title,blocked:blocked,blockReason:reason,current:cur,base:bas,selectedCard:selected});"+
                "})()";
    }

    public void destroy() {
        main.post(() -> {
            try { if (webView != null) { webView.stopLoading(); webView.destroy(); } } catch (Throwable ignored) {}
            webView = null;
        });
    }
}
