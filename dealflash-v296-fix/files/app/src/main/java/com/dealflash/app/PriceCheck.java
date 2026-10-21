package com.dealflash.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * On-demand re-check of a drop on the live Coupang page, run on the user's own phone (own IP, normal WebView, no bypass of any challenge):
 *   1) Fallcent product page -> press "쿠팡 바로가기" in an off-screen WebView and capture the Coupang address it leads to
 *      (the same mechanism AppLauncher.openFallcentClean uses, which stays untouched);
 *   2) BrowserScanner.scanDetail() reads that Coupang page once and the price is compared to what the feed showed.
 * Any failure (blocked, no button, several prices on the page, timeout) is reported as UNKNOWN with a reason; nothing is guessed.
 */
public final class PriceCheck {
    private PriceCheck() {}
    private static final String PREF = "price_check";

    public interface Callback { void done(String status, double price, String note); }

    public static String[] stored(Context c, String key) {
        try { return PriceVerdict.decode(c.getSharedPreferences(PREF, 0).getString(key, null)); } catch (Throwable t) { return null; }
    }
    public static boolean endedByCheck(Context c, HistoryStore.H h) { return PriceVerdict.endedByCheck(stored(c, h.key), h.lastSeen); }

    private static void save(Context c, String key, String status, double price, String note) {
        try { c.getSharedPreferences(PREF, 0).edit().putString(key, PriceVerdict.encode(status, price, System.currentTimeMillis(), note)).apply(); } catch (Throwable ignored) {}
    }

    /** Runs off the UI thread; the callback is delivered on the UI thread. */
    public static void run(final Activity a, final HistoryStore.H x, final Callback cb) {
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            String status = PriceVerdict.UNKNOWN, note = "";
            double price = 0;
            BrowserScanner browser = null;
            try {
                String target;
                if ("FALLCENT".equals(x.source) || (x.url != null && x.url.contains("fallcent.com"))) {
                    Resolver r = new Resolver(a, x.url);
                    r.start();
                    boolean ok = r.await(40_000L);
                    r.abort();
                    target = ok ? r.result : null;
                    if (target == null) note = r.reason.isEmpty() ? "쿠팡 주소를 얻지 못함" : r.reason;
                } else {
                    target = ProductUrl.canonical(x.source, x.url);
                    if (target == null) note = "상품 링크 검증 실패";
                }
                if (target != null && !target.startsWith("http")) { target = null; note = "쿠팡 앱 링크만 반환됨"; }
                if (target != null) {
                    browser = new BrowserScanner(app);
                    BrowserScanner.DetailResult d = browser.scanDetail(target, "COUPANG", x.last, 0, 25_000L);
                    if (d.blocked) note = "쿠팡 접근 제한/캡차";
                    else if (d.timeout) note = "쿠팡 로딩 시간초과";
                    else if (!d.ok || d.current <= 0) note = d.error.isEmpty() ? "쿠팡 페이지에서 가격을 읽지 못함" : d.error;
                    else { price = d.current; status = PriceVerdict.classify(x.last, d.current); }
                }
            } catch (Throwable t) {
                note = "확인 중 오류: " + t.getClass().getSimpleName();
            } finally {
                if (browser != null) try { browser.destroy(); } catch (Throwable ignored) {}
            }
            final String fs = status, fn = note; final double fp = price;
            save(app, x.key, fs, fp, fn);
            a.runOnUiThread(() -> { if (!a.isFinishing()) cb.done(fs, fp, fn); });
        }, "price-check").start();
    }

    /** Off-screen Fallcent page -> captured Coupang address. Mirrors AppLauncher.openFallcentClean but returns the URL instead of launching it. */
    private static final class Resolver {
        final Activity a; final String input;
        final CountDownLatch latch = new CountDownLatch(1);
        final Handler h = new Handler(Looper.getMainLooper());
        final ArrayList<WebView> views = new ArrayList<>();
        volatile String result; volatile String reason = "";
        boolean done; int tries, clicks; long lastClick;

        Resolver(Activity a, String input) { this.a = a; this.input = input; }

        boolean await(long ms) { try { return latch.await(ms, TimeUnit.MILLISECONDS) && result != null; } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; } }

        void start() {
            a.runOnUiThread(() -> {
                try {
                    final WebView w = make(true);
                    w.loadUrl(input);
                    h.postDelayed(() -> poll(w), 800);
                } catch (Throwable t) { reason = "브라우저 열기 실패"; finish(); }
            });
        }
        void abort() { h.post(this::finish); }

        void finish() {
            done = true;
            h.removeCallbacksAndMessages(null);
            ViewGroup root = null;
            try { root = (ViewGroup) a.getWindow().getDecorView(); } catch (Throwable ignored) {}
            for (WebView w : views) {
                try { w.stopLoading(); } catch (Throwable ignored) {}
                try { if (root != null) root.removeView(w); } catch (Throwable ignored) {}
                try { w.destroy(); } catch (Throwable ignored) {}
            }
            views.clear();
            latch.countDown();
        }

        WebView make(boolean attach) {
            WebView w = new WebView(a);
            WebSettings st = w.getSettings();
            st.setJavaScriptEnabled(true); st.setDomStorageEnabled(true);
            st.setSupportMultipleWindows(true); st.setJavaScriptCanOpenWindowsAutomatically(true);
            st.setUserAgentString(WebSettings.getDefaultUserAgent(a));
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
            if (attach) {
                DisplayMetrics dm = a.getResources().getDisplayMetrics();
                w.setTranslationX(-dm.widthPixels * 2f);
                ((ViewGroup) a.getWindow().getDecorView()).addView(w, new FrameLayout.LayoutParams(dm.widthPixels, dm.heightPixels));
            }
            views.add(w);
            w.setWebViewClient(client());
            w.setWebChromeClient(new WebChromeClient() {
                @Override public boolean onCreateWindow(WebView v, boolean d, boolean g, android.os.Message msg) {
                    WebView child = make(false);
                    ((WebView.WebViewTransport) msg.obj).setWebView(child); msg.sendToTarget(); return true;
                }
            });
            return w;
        }

        String norm(String z) {
            if (z == null) return null;
            try {
                if (z.startsWith("intent://")) {
                    Intent ii = Intent.parseUri(z, Intent.URI_INTENT_SCHEME);
                    android.net.Uri d = ii.getData(); String fb = ii.getStringExtra("browser_fallback_url");
                    if (d != null && d.getScheme() != null && (d.getScheme().startsWith("http") || d.getScheme().equals("coupang"))) z = d.toString();
                    else if (fb != null) z = fb;
                }
                android.net.Uri q = android.net.Uri.parse(z); String hh = q.getHost();
                if (hh != null && (hh.equals("api.adjoin.co.kr") || hh.equals("api.ootoo.co.kr"))) { String land = q.getQueryParameter("land"); if (land != null && !land.isEmpty()) z = land; }
            } catch (Exception ignored) {}
            return z;
        }
        boolean coup(String z) {
            z = norm(z); if (z == null) return false; if (z.startsWith("coupang://")) return true;
            try { String hh = android.net.Uri.parse(z).getHost(); if (hh == null) return false; hh = hh.toLowerCase(Locale.ROOT);
                return hh.equals("link.coupang.com") || hh.equals("coupang.com") || hh.endsWith(".coupang.com"); } catch (Exception e) { return false; }
        }
        boolean follow(String z) {
            if (done || z == null || z.isEmpty()) return false;
            String q = norm(z);
            if (coup(q)) { result = q; finish(); return true; }
            return false;
        }
        WebViewClient client() {
            return new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                    if (follow(req.getUrl().toString())) return true;
                    String sc = req.getUrl().getScheme();
                    return sc != null && !sc.startsWith("http");
                }
                @Override public void onPageStarted(WebView v, String z, Bitmap b) { follow(z); }
                @Override public void doUpdateVisitedHistory(WebView v, String z, boolean reload) { follow(z); }
                @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError e) {
                    if (req.isForMainFrame() && !done) reason = "폴센트 페이지 오류 " + e.getErrorCode();
                }
            };
        }
        String js(boolean click) {
            return "(function(doClick){try{"
                + "var all=[].slice.call(document.querySelectorAll('a,button,[role=button],div,span,p'));"
                + "var c=all.filter(function(e){var t=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim();return t.indexOf('쿠팡 바로가기')===0&&t.length<30;});"
                + "if(!c.length)return 'NOTFOUND';"
                + "var el=c.filter(function(e){return !c.some(function(o){return o!==e&&e.contains(o);});})[0]||c[0];"
                + "var n=el;for(var i=0;i<8&&n;i++,n=n.parentElement){if(n.tagName==='A'||n.tagName==='BUTTON'||n.getAttribute('role')==='button'||n.getAttribute('onclick'))break;}"
                + "if(!n)n=el;"
                + "var hf=(n.tagName==='A')?(n.href||''):'';"
                + "if(/^https?:\\/\\/[^\\/]*coupang\\.com/.test(hf))return 'HREF:'+hf;"
                + "if(!doClick)return 'FOUND';"
                + "try{n.scrollIntoView({block:'center'});}catch(e){}"
                + "['mousedown','mouseup'].forEach(function(t){try{n.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window}));}catch(e){}});"
                + "n.click();return 'CLICKED '+(hf||n.tagName);"
                + "}catch(e){return 'ERR '+e;}})(" + click + ")";
        }
        void poll(final WebView w) {
            if (done) return;
            if (tries++ >= 36) { if (reason.isEmpty()) reason = "'쿠팡 바로가기' 버튼을 찾지 못함"; finish(); return; }
            final long now = System.currentTimeMillis();
            final boolean click = clicks < 4 && now - lastClick > 1500;
            w.evaluateJavascript(js(click), raw -> {
                if (done) return;
                String r = raw == null ? "" : raw;
                try { Object o = new org.json.JSONTokener(r).nextValue(); r = o == null ? "" : o.toString(); } catch (Exception ignore) {}
                if (r.startsWith("HREF:")) follow(r.substring(5));
                else if (r.startsWith("CLICKED")) { clicks++; lastClick = System.currentTimeMillis(); }
                if (!done) h.postDelayed(() -> poll(w), 500);
            });
        }
    }
}
