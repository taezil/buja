from pathlib import Path

def replace(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"missing marker in {path}: {old[:80]!r}")
    p.write_text(s.replace(old, new))

# Version / preview package metadata.
replace("app/build.gradle", "versionCode 25\n        versionName '2.5.0'", "versionCode 27\n        versionName '2.5.2'")
replace("build-apk.sh",
        "r.set(ns+'versionCode','25');r.set(ns+'versionName','2.5.0-preview' if sys.argv[1]=='preview' else '2.5.0')",
        "r.set(ns+'versionCode','26');r.set(ns+'versionName','2.5.2-preview' if sys.argv[1]=='preview' else '2.5.2')")
replace("build-apk.sh", "DealFlash 2.5 Preview", "DealFlash 2.5.2 Preview")
replace("build-apk.sh", "DealFlash-v2.5-$MODE.apk", "DealFlash-v2.5.2-$MODE.apk")
replace("app/src/main/java/com/dealflash/app/NotificationHelper.java",
        "DealFlash V2.5 · 가격 감시 중", "DealFlash V2.5.2 · 가격 감시 중")

# Fallcent URL identity: accept detail paths that are not the old one-segment shape.
replace("app/src/main/java/com/dealflash/app/ProductUrl.java",
'''        if ("FALLCENT".equals(source)) {
            Matcher m = FALLCENT.matcher(s);
            return m.matches() && !FALLCENT_RESERVED.contains(m.group(1).toLowerCase(java.util.Locale.ROOT))
                ? "https://fallcent.com/product/" + m.group(1) : null;
        }''',
'''        if ("FALLCENT".equals(source)) {
            return canonicalFallcent(s);
        }''')

replace("app/src/main/java/com/dealflash/app/ProductUrl.java",
'''        if ("FALLCENT".equals(source)) {
            // 안정 키: 폴센트 상품 id (대소문자 구분). 쿼리/해시/도메인 표기가 달라도 같은 상품이면 같은 키.
            Matcher fm = FALLCENT.matcher(unescape(url.trim()));
            if (fm.matches() && !FALLCENT_RESERVED.contains(fm.group(1).toLowerCase())) return "fc:" + fm.group(1);
            return "fc:h:" + sha1Hex(stripQuery(unescape(url.trim())), 12);
        }''',
'''        if ("FALLCENT".equals(source)) {
            String direct = canonicalFallcent(url);
            if (direct == null) return "";
            try {
                URI u = new URI(direct);
                String host = u.getHost() == null ? "" : u.getHost().toLowerCase(java.util.Locale.ROOT);
                if ("fcnt.link".equals(host)) {
                    String token = u.getPath() == null ? "" : u.getPath().replaceAll("^/+|/+$", "");
                    return token.isEmpty() ? "" : "fc:l:" + sha1Hex(token, 16);
                }
            } catch (Exception ignored) {}
            String path = fallcentProductPath(direct);
            if (path == null) return "";
            if (path.matches("[A-Za-z0-9_-]{6,}")) return "fc:" + path;
            return "fc:p:" + sha1Hex(path, 16);
        }''')

replace("app/src/main/java/com/dealflash/app/ProductUrl.java",
'''    private static String stripQuery(String url) {''',
'''    private static String canonicalFallcent(String raw) {
        if (raw == null) return null;
        try {
            URI u = new URI(unescape(raw.trim()));
            String host = u.getHost();
            if (host == null) return null;
            String lower = host.toLowerCase(java.util.Locale.ROOT).replaceFirst("^www\\\\.", "");
            if ("fcnt.link".equals(lower)) {
                String token = u.getPath() == null ? "" : u.getPath().replaceAll("^/+|/+$", "");
                return token.isEmpty() ? null : "https://fcnt.link/" + token;
            }
            String path = fallcentProductPath(raw);
            return path == null ? null : "https://fallcent.com/product/" + path;
        } catch (Exception e) { return null; }
    }

    private static String fallcentProductPath(String raw) {
        if (raw == null) return null;
        try {
            URI u = new URI(unescape(raw.trim()));
            String host = u.getHost();
            if (host == null || !(host.equalsIgnoreCase("fallcent.com") || host.equalsIgnoreCase("www.fallcent.com"))) return null;
            String path = u.getPath();
            if (path == null || !path.toLowerCase(java.util.Locale.ROOT).startsWith("/product/")) return null;
            String rest = path.substring("/product/".length()).replaceAll("^/+|/+$", "");
            if (rest.isEmpty()) return null;
            String first = rest.split("/", 2)[0].toLowerCase(java.util.Locale.ROOT);
            if (FALLCENT_RESERVED.contains(first)) return null;
            return rest;
        } catch (Exception e) { return null; }
    }

    private static String stripQuery(String url) {''')

# More tolerant dynamic-page timing + accurate diagnostics.
replace("app/src/main/java/com/dealflash/app/BrowserScanner.java",
        "if (!now.page.ok && !now.page.blocked && now.extractAttempts < 2) {",
        "if (!now.page.ok && !now.page.blocked && now.extractAttempts < 3) {")
replace("app/src/main/java/com/dealflash/app/BrowserScanner.java",
        "main.postDelayed(() -> extract(view, token), 900L);",
        "main.postDelayed(() -> extract(view, token), 1200L);")
replace("app/src/main/java/com/dealflash/app/BrowserScanner.java",
'''                    String emittedId=o.optString("id", "");
                    if(fallcent && !emittedId.isEmpty() && !x.key.equals("fc:"+emittedId))continue;
''',
'''                    // Canonical URL/key is authoritative. Do not reject a safely isolated card
                    // just because Fallcent changed the detail URL shape.
''')
replace("app/src/main/java/com/dealflash/app/BrowserScanner.java",
'''            } else if (out.productLinks > 0) {
                out.error = "상품 링크 " + out.productLinks + "개 · 가격 DOM 없음";
                out.status = out.error;''',
'''            } else if (out.productLinks > 0) {
                boolean bodyHasPrices = root.optBoolean("bodyHasPrices", false);
                out.error = bodyHasPrices
                        ? "상품 링크 " + out.productLinks + "개 · 카드 연결 실패"
                        : "상품 링크 " + out.productLinks + "개 · 가격 DOM 없음";
                out.status = out.error;''')

# Give the dynamic recommendation page enough time.
replace("app/src/main/java/com/dealflash/app/MonitorService.java",
        "browser.scanListings(feed.url,feed.source,9_000L)",
        "browser.scanListings(feed.url,feed.source,16_000L)")

# A manual retry should actually retry now instead of keeping an old empty-DOM cooldown.
replace("app/src/main/java/com/dealflash/app/MainActivity.java",
        "if(ScannerPrefs.enabled(this))start();",
        "if(ScannerPrefs.enabled(this))start(false);")
replace("app/src/main/java/com/dealflash/app/MainActivity.java",
        "start.setOnClickListener(v->start());",
        "start.setOnClickListener(v->start(true));")
replace("app/src/main/java/com/dealflash/app/MainActivity.java",
        "private void start(){ScannerPrefs.enabled(this,true);Intent i=new Intent(this,MonitorService.class);startForegroundService(i);refreshStatus();}",
        "private void start(boolean manual){if(manual)FeedCooldownStore.clear(this);ScannerPrefs.enabled(this,true);Intent i=new Intent(this,MonitorService.class);startForegroundService(i);refreshStatus();}")

# Replace the browser-side collector after v2.5 base patch has been applied.
overlay = Path("../dealflash-v251-overlay/fallcent_listing.js")
Path("app/src/main/assets/fallcent_listing.js").write_text(overlay.read_text())
