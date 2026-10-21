package com.dealflash.app;

import android.app.Activity;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** v2.9.5 HTTP bridge. Discovery sync is observable and FCM can seed the rendered store. */
public final class ServerSync {
    public static final String BASE="https://dealflash-server.pittytntn1.workers.dev";
    private static final String PREF="dealflash_server_v26";
    private static final java.util.concurrent.atomic.AtomicBoolean DEAL_SYNCING=new java.util.concurrent.atomic.AtomicBoolean(false);
    private ServerSync(){}

    public interface MarketCallback { void done(List<MarketComparison.Offer> offers); }

    public static void uploadDeals(List<PageScanner.Listing> items,String source){ /* v2.8: local fallback never mutates trusted server price data. */ }

    public static void monitor(int status,int found,int accepted,long duration,String error){ /* server-authoritative monitoring */ }

    public static void refreshStatus(Context c,Runnable done){
        new Thread(()->{
            boolean ok=false,monitoring=false,push=false;long checked=0;String version="";
            try{
                JSONObject o=getJson("/api/v1/status",5000,6000);
                ok=o.optBoolean("ok",true);monitoring=o.optBoolean("monitoring",false);push=o.optBoolean("push_ready",false);
                checked=o.optLong("last_scan_at",o.optLong("checked_at",0));version=o.optString("version","");
                try{
                    JSONObject ds=authGetJson(c,"/api/v1/device/settings",5000,6000);
                    // Registration/settings writes reconcile the threshold. Status refresh must not race an explicit local choice and lower it.
                    c.getSharedPreferences(PREF,0).edit().putString("threshold_sync_state","saved").putString("push_state",push?"ready":"registered").apply();
                }catch(Exception ignored){}
            }catch(Exception ignored){}
            ServerState.update(c,ok,monitoring,push,checked,version);
            if(ServerState.active(c)) ServerPollJobService.schedule(c); else ServerPollJobService.cancel(c);
            if(done!=null){try{new android.os.Handler(android.os.Looper.getMainLooper()).post(done);}catch(Exception ignored){done.run();}}
        }).start();
    }

    public static void registerDevice(Context c,String fcmToken,Runnable done){
        final Context app=c.getApplicationContext();
        if(fcmToken==null||fcmToken.length()<20){if(done!=null)done.run();return;}
        new Thread(()->{String state="failed";try{
            final int localThreshold=ScannerPrefs.threshold(app);
            JSONObject o=new JSONObject();o.put("device_id",DeviceIdentity.id(app));o.put("auth_token",DeviceIdentity.authToken(app));o.put("fcm_token",fcmToken);o.put("alert_threshold",localThreshold);o.put("app_version","2.9.5");
            JSONObject r=postJson("/api/v1/devices/register",o);
            DeviceIdentity.acceptServerCredentials(app,r.optString("device_id",""),r.optString("device_token",""));
            // Local user choice is authoritative. Registration/recovery must never silently lower 60% back to an old server value.
            try{JSONObject settings=new JSONObject();settings.put("alert_threshold",localThreshold);authPostJson(app,"/api/v1/device/settings",settings);state="saved";}catch(Exception syncError){state="registered";}
            app.getSharedPreferences(PREF,0).edit().putBoolean("device_registered",true).putString("push_state",r.optBoolean("push_ready",false)?"ready":"registered").apply();
        }catch(Exception ignored){}finally{app.getSharedPreferences(PREF,0).edit().putString("threshold_sync_state",state).apply();if(done!=null){try{new android.os.Handler(android.os.Looper.getMainLooper()).post(done);}catch(Exception ignored){done.run();}}}}).start();
    }

    public static void restoreDeals(Context c){syncServerDeals(c,"",300,null);}
    public static void refreshDeals(Context c,Runnable done){syncServerDeals(c,"",400,done);}
    public static void manualScanAndRefresh(Context c,Runnable done){
        final Context app=c.getApplicationContext();
        new Thread(()->{
            String result="requested";
            try{
                long before=0;try{before=getJson("/api/status",5000,6000).optLong("last_scan_at",0);}catch(Exception ignored){}
                try{
                    authPostJson(app,"/api/v1/device/manual-scan",new JSONObject());
                    long end=System.currentTimeMillis()+28000L;boolean finished=false;
                    while(System.currentTimeMillis()<end){
                        try{Thread.sleep(1500L);}catch(InterruptedException ignored){}
                        try{long now=getJson("/api/status",5000,6000).optLong("last_scan_at",0);if(now>before){finished=true;break;}}catch(Exception ignored){}
                    }
                    result=finished?"scan finished":"scan still running (synced current data)";
                }catch(HttpStatusException e){
                    // 409/423/429 = cooldown or a scan already in progress: do NOT start another, just pull the data that exists.
                    result=(e.code==429||e.code==409||e.code==423)?"cooldown/in-progress (HTTP "+e.code+"), synced current data":"manual-scan HTTP "+e.code;
                    if(e.code==429||e.code==409||e.code==423){try{Thread.sleep(2500L);}catch(InterruptedException ignored){}}
                }
            }catch(Exception error){
                result="manual-scan error: "+error;SyncDiag.error(app,"manual-scan",error);
            }finally{SyncDiag.manual(app,result);syncServerDeals(app,"",400,done);}
        }).start();
    }

    public static void syncDiscovery(Context c,String signal,Runnable done){
        String sig=signal==null||signal.trim().isEmpty()||"전체".equals(signal)?"*":signal.trim();
        String cacheKey="discovery_sync_"+Integer.toHexString(sig.hashCode());long now=System.currentTimeMillis();
        if(now-c.getSharedPreferences(PREF,0).getLong(cacheKey,0)<45_000L){if(done!=null)done.run();return;}
        syncServerDeals(c,sig,400,()->{c.getSharedPreferences(PREF,0).edit().putLong(cacheKey,System.currentTimeMillis()).apply();if(done!=null)done.run();});
    }

    private static final String[] DEAL_PATHS={"/api/v1/deals","/api/deals"};
    private static volatile long dealSyncStartedAt=0;

    private static void syncServerDeals(Context c,String signal,int limit,Runnable done){
        long started=System.currentTimeMillis();
        // A stuck sync (hung socket) must never block every later refresh.
        if(!DEAL_SYNCING.compareAndSet(false,true)){
            if(started-dealSyncStartedAt<60_000L){if(done!=null)done.run();return;}
            DEAL_SYNCING.set(true);
        }
        dealSyncStartedAt=started;
        final Context app=c.getApplicationContext();
        new Thread(()->{try{
            Exception last=null;boolean ok=false;
            int[] limits={Math.max(1,Math.min(500,limit)),150};
            outer:
            for(int lim:limits){
                for(String base:DEAL_PATHS){
                    String path=base+"?limit="+lim;
                    if(signal!=null&&!signal.isEmpty())path+="&signal="+URLEncoder.encode(signal,"UTF-8");
                    try{
                        Object root=getJsonAny(path,8000,20000);
                        DealsRepository.Result r=DealsRepository.applyServerPayload(app,root,path,System.currentTimeMillis());
                        if(r.error.isEmpty()){ok=true;app.getSharedPreferences(PREF,0).edit().putLong("deals_sync",System.currentTimeMillis()).apply();break outer;}
                        last=new IllegalStateException(r.error);
                    }catch(Exception e){last=e;SyncDiag.error(app,"GET "+path,e);}
                }
            }
            if(!ok&&last!=null)SyncDiag.error(app,"sync failed after retries",last);
        }catch(Throwable error){
            SyncDiag.error(app,"sync",error);
        }finally{DEAL_SYNCING.set(false);if(done!=null){try{new android.os.Handler(android.os.Looper.getMainLooper()).post(done);}catch(Exception ignored){done.run();}}}}).start();
    }

    @SuppressWarnings("unused") private static String mergeSignals(String a,String b){
        LinkedHashSet<String> set=new LinkedHashSet<>();for(String s:new String[]{a,b})if(s!=null&&!s.isEmpty())for(String x:s.split("\\|")){x=x.trim();if(!x.isEmpty())set.add(x);}return String.join("|",set);
    }

    public static long lastDealSync(Context c){return c.getSharedPreferences(PREF,0).getLong("deals_sync",0);}

    public static void setAlertThreshold(Context c,int threshold,Runnable done){
        final int value=Math.max(10,Math.min(90,Math.round(threshold/5f)*5));
        ScannerPrefs.threshold(c,value);c.getSharedPreferences(PREF,0).edit().putString("threshold_sync_state","saving").apply();
        new Thread(()->{String state="failed";try{JSONObject o=new JSONObject();o.put("alert_threshold",value);JSONObject r=authPostJson(c,"/api/v1/device/settings",o);ScannerPrefs.threshold(c,r.optInt("alert_threshold",value));state="saved";}catch(Exception ignored){PushBootstrap.start(c);}finally{c.getSharedPreferences(PREF,0).edit().putString("threshold_sync_state",state).apply();if(done!=null){try{new android.os.Handler(android.os.Looper.getMainLooper()).post(done);}catch(Exception ignored){done.run();}}}}).start();
    }
    public static boolean thresholdSyncOk(Context c){return "saved".equals(thresholdSyncState(c));}
    public static String thresholdSyncState(Context c){return c.getSharedPreferences(PREF,0).getString("threshold_sync_state","");}

    public static void fetchHistory(final Activity a,String key,int days,final PriceHistoryView chart){
        new Thread(()->{try{
            String enc=URLEncoder.encode(key,"UTF-8");JSONObject root=getJson("/api/history/"+enc+"?days="+Math.max(1,Math.min(365,days)),8000,8000);
            final JSONArray ar=root.optJSONArray("history");a.runOnUiThread(()->{if(ar!=null)chart.setHistory(ar,days+"일 서버 이력");});
        }catch(Exception ignored){}}).start();
    }
    public static void fetchHistory(final Activity a,String key,final PriceHistoryView chart){fetchHistory(a,key,180,chart);}

    public static void fetchMarketOffers(final Activity a,String key,boolean refresh,final MarketCallback cb){
        new Thread(()->{
            ArrayList<MarketComparison.Offer> out=new ArrayList<>();
            try{
                String enc=URLEncoder.encode(key,"UTF-8");
                if(refresh){try{post("/api/compare/"+enc,new JSONObject());}catch(Exception ignored){}}
                JSONObject root=getJson("/api/markets/"+enc,10000,12000);JSONArray ar=root.optJSONArray("offers");
                if(ar!=null)for(int i=0;i<ar.length();i++){
                    JSONObject o=ar.optJSONObject(i);if(o==null)continue;MarketComparison.Offer m=new MarketComparison.Offer();
                    m.source=o.optString("market",o.optString("source",""));m.label=MarketComparison.label(m.source);m.url=o.optString("url","");
                    m.price=o.optDouble("total_price",o.optDouble("price",0));m.itemPrice=o.optDouble("price",m.price);m.shipping=o.optDouble("shipping",Math.max(0,m.price-m.itemPrice));
                    m.checkedAt=o.optLong("checked_at",0);m.rocket=o.optBoolean("rocket",false);m.confidence=o.optInt("match_confidence",0);if(m.price>0)out.add(m);
                }
                out.sort((x,y)->Double.compare(x.price,y.price));
            }catch(Exception ignored){}
            if(cb!=null)a.runOnUiThread(()->cb.done(out));
        }).start();
    }

    public static void watch(String key,boolean enabled){
        new Thread(()->{try{JSONObject o=new JSONObject();o.put("enabled",enabled);post("/api/watch/"+URLEncoder.encode(key,"UTF-8"),o);}catch(Exception ignored){}}).start();
    }

    /** Fallback sync for events that FCM may have missed. FCM is the primary alert path. */
    public static void fetchAlerts(Context c,Runnable done){
        new Thread(()->{
            try{
                android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);long after=p.getLong("alert_event_cursor",0);boolean bootstrap=after==0;
                JSONObject root=authGetJson(c,"/api/v1/device/events?after="+after+"&limit=50",8000,8000);JSONArray a=root.optJSONArray("items");long max=after;
                if(a!=null)for(int i=0;i<a.length();i++){
                    JSONObject o=a.optJSONObject(i);if(o==null)continue;long eventId=o.optLong("event_id",0);max=Math.max(max,eventId);if(bootstrap)continue;
                    String source=o.optString("source","FALLCENT"),key=o.optString("product_key",""),url=o.optString("product_url","");if(key.isEmpty()||url.isEmpty())continue;
                    int drop=o.optInt("drop_rate",0),localThreshold=ScannerPrefs.threshold(c);if(drop<localThreshold)continue;
                    String product=o.optString("product_name","상품"),title=o.optString("title","가격 급락 포착");double price=o.optDouble("price",0);
                    String stored=DealsRepository.applyPushKey(c,key,product,url,source,price,drop,null,System.currentTimeMillis());
                    String body=product+" · "+formatWon(price)+" · 내 알림 "+localThreshold+"%+";
                    NotificationHelper.alertAuto(c,source,stored!=null?stored:key,title,body,url,"event:"+eventId);
                }
                if(max>after)p.edit().putLong("alert_event_cursor",max).apply();
            }catch(Exception error){SyncDiag.error(c,"fetchAlerts",error);}finally{if(done!=null)done.run();}
        }).start();
    }

    public static final class HttpStatusException extends IOException{public final int code;HttpStatusException(int code){super("HTTP "+code);this.code=code;}}
    private static Object getJsonAny(String path,int connect,int read)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(connect);c.setReadTimeout(read);c.setRequestMethod("GET");c.setRequestProperty("Accept","application/json");
        int code=c.getResponseCode();if(code/100!=2){c.disconnect();throw new HttpStatusException(code);}String s=readAll(c.getInputStream());c.disconnect();
        s=s.trim();return s.startsWith("[")?new JSONArray(s):new JSONObject(s);
    }
    private static JSONObject getJson(String path,int connect,int read)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(connect);c.setReadTimeout(read);c.setRequestMethod("GET");c.setRequestProperty("Accept","application/json");
        int code=c.getResponseCode();if(code/100!=2){c.disconnect();throw new HttpStatusException(code);}String s=readAll(c.getInputStream());c.disconnect();return new JSONObject(s);
    }
    private static void post(String path,JSONObject body)throws Exception{postJson(path,body);}
    private static JSONObject postJson(String path,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(12000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        byte[] b=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(b);}int code=c.getResponseCode();if(code/100!=2){c.disconnect();throw new HttpStatusException(code);}String raw="{}";try{InputStream in=c.getInputStream();if(in!=null){raw=readAll(in);in.close();}}catch(Exception ignored){}c.disconnect();return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
    }
    private static JSONObject authGetJson(Context ctx,String path,int connect,int read)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(connect);c.setReadTimeout(read);c.setRequestMethod("GET");c.setRequestProperty("Accept","application/json");c.setRequestProperty("Authorization","Bearer "+DeviceIdentity.authToken(ctx));
        int code=c.getResponseCode();if(code/100!=2){c.disconnect();throw new HttpStatusException(code);}String raw=readAll(c.getInputStream());c.disconnect();return new JSONObject(raw);
    }
    private static JSONObject authPostJson(Context ctx,String path,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(12000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Authorization","Bearer "+DeviceIdentity.authToken(ctx));
        byte[] b=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(b);}int code=c.getResponseCode();if(code/100!=2){c.disconnect();throw new HttpStatusException(code);}String raw="{}";try{InputStream in=c.getInputStream();if(in!=null){raw=readAll(in);in.close();}}catch(Exception ignored){}c.disconnect();return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
    }
    private static String formatWon(double v){return new java.text.DecimalFormat("#,##0").format(Math.round(v))+"원";}
    private static String readAll(InputStream in)throws Exception{BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String z;while((z=br.readLine())!=null)b.append(z);return b.toString();}
}
