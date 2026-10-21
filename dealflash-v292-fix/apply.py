from pathlib import Path
# Accept the server's fallback stable Fallcent identity too. It was previously rejected at the presentation gate.
p=Path("dealflash_build_min/app/src/main/java/com/dealflash/app/ProductIdentity.java")
s=p.read_text().replace('key.startsWith("fcfp:") || key.startsWith("fcx:")','key.startsWith("fcfp:") || key.startsWith("fcx:") || key.startsWith("fcurl:")')
p.write_text(s)

p=Path("dealflash_build_min/app/src/main/java/com/dealflash/app/ServerSync.java")
s=p.read_text()
s=s.replace('public static void refreshDeals(Context c,Runnable done){syncServerDeals(c,"",400,done);}','''public static void refreshDeals(Context c,Runnable done){syncServerDeals(c,"",400,done);}
    public static void manualScanAndRefresh(Context c,Runnable done){
        final Context app=c.getApplicationContext();
        new Thread(()->{
            try{
                long before=0;try{before=getJson("/api/status",5000,6000).optLong("last_scan_at",0);}catch(Exception ignored){}
                authPostJson(app,"/api/v1/device/manual-scan",new JSONObject());
                long end=System.currentTimeMillis()+28000L;
                while(System.currentTimeMillis()<end){
                    try{Thread.sleep(1500L);}catch(InterruptedException ignored){}
                    try{long now=getJson("/api/status",5000,6000).optLong("last_scan_at",0);if(now>before)break;}catch(Exception ignored){}
                }
            }catch(Exception error){
                app.getSharedPreferences(PREF,0).edit().putString("manual_scan_error",String.valueOf(error.getMessage())).putLong("manual_scan_error_at",System.currentTimeMillis()).apply();
            }finally{syncServerDeals(app,"",400,done);}
        }).start();
    }''')
p.write_text(s)

p=Path("dealflash_build_min/app/src/main/java/com/dealflash/app/MainActivity.java")
s=p.read_text()
old='''           if(ServerState.active(this)){
               lastForegroundSync=System.currentTimeMillis();
               ServerSync.refreshDeals(this,()->{
                   manualRefreshInFlight=false;
                   reload();
                   Toast.makeText(this,"최신 상품으로 새로고침했어요",Toast.LENGTH_SHORT).show();
               });'''
new='''           if(ServerState.active(this)){
               lastForegroundSync=System.currentTimeMillis();
               ServerSync.manualScanAndRefresh(this,()->{
                   manualRefreshInFlight=false;
                   reload();
                   Toast.makeText(this,"서버 즉시 스캔을 완료했어요",Toast.LENGTH_SHORT).show();
               });'''
if old not in s: raise SystemExit("pull refresh anchor missing")
s=s.replace(old,new)
# Empty messages should distinguish a completed empty result from an endless loading state.
s=s.replace('if("발견".equals(screen))return "서버의 인기·타이밍 신호를 불러오는 중이에요";','if("발견".equals(screen))return "현재 수집된 발견 신호가 없어요 · 아래로 당겨 즉시 스캔";')
p.write_text(s)

g=Path("dealflash_build_min/app/build.gradle")
t=g.read_text().replace("versionCode 42","versionCode 43").replace("versionName '2.9.1'","versionName '2.9.2'")
g.write_text(t)
