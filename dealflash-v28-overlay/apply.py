from pathlib import Path

root=Path("dealflash_build_min")

def replace(path, old, new):
    p=root/path
    s=p.read_text()
    if old not in s:
        raise SystemExit(f"missing patch marker in {path}: {old[:80]!r}")
    p.write_text(s.replace(old,new))

# Root Gradle: Firebase plugin.
p=root/"build.gradle"
p.write_text("""plugins {
    id 'com.android.application' version '8.7.3' apply false
    id 'com.google.gms.google-services' version '4.4.2' apply false
}
""")

p=root/"gradle.properties"
s=p.read_text().replace("android.useAndroidX=false","android.useAndroidX=true")
p.write_text(s)

# App Gradle.
p=root/"app/build.gradle"
s=p.read_text()
s=s.replace("plugins {\n    id 'com.android.application'\n}", "plugins {\n    id 'com.android.application'\n    id 'com.google.gms.google-services'\n}")
s=re.sub(r"versionCode\\s+\\d+","versionCode 35",s)
s=re.sub(r"versionName\\s+'[^']+'","versionName '2.8.0-alpha2'",s)
if "firebase-messaging" not in s:
    s=s.rstrip()+"\n\ndependencies {\n    implementation platform('com.google.firebase:firebase-bom:33.5.1')\n    implementation 'com.google.firebase:firebase-messaging'\n}\n"
p.write_text(s)

# Manifest: notification permission + FCM service.
p=root/"app/src/main/AndroidManifest.xml"
s=p.read_text()
if "android.permission.POST_NOTIFICATIONS" not in s:
    s=s.replace('<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />','<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />')
if ".DealFlashMessagingService" not in s:
    s=s.replace('<service android:name=".MonitorService" android:exported="false" />','<service android:name=".MonitorService" android:exported="false" />\n        <service android:name=".DealFlashMessagingService" android:exported="false">\n            <intent-filter>\n                <action android:name="com.google.firebase.MESSAGING_EVENT" />\n            </intent-filter>\n        </service>')
p.write_text(s)

# MainActivity: remove hero block already introduced in v2.7, keep BIG HOT DEAL inside normal cards, wire FCM.
p=root/"app/src/main/java/com/dealflash/app/MainActivity.java"
s=p.read_text()
s=s.replace('/** DealFlash v2.7.1 premium price-intelligence UI. Collection, ranking and alerts are intentionally separate. */','/** DealFlash v2.8 alpha: fast deal radar UI. Accuracy checks stay mostly internal; the feed stays simple. */')
s=s.replace('private static final String[] SORTS={"추천순","할인율 높은순","절약금액 높은순","최근 발견순","긴급도 높은순","가격 낮은순"};','private static final String[] SORTS={"추천순","할인율 높은순","절약금액 높은순","최근 발견순","신호 많은순","가격 낮은순"};')
s=s.replace('DataMigration.run(this);ServerSync.restoreDeals(this);NotificationHelper.ensureChannels(this);','DataMigration.run(this);ServerSync.restoreDeals(this);NotificationHelper.ensureChannels(this);PushManager.ensure(this);')
s=s.replace('return "실시간 급락 · 시장가격 검증";','return "실시간 급락 · 자동 포착";')
# Hero card: remove the entire method containing the literal, regardless of its method name.
import re
def remove_method_with_literal(src,literal):
    pos=src.find(literal)
    if pos<0:return src
    starts=list(re.finditer(r'(?m)^\\s*(?:private|public|protected)\\s+(?:static\\s+)?[^;\\n{]+\\([^;\\n]*\\)\\s*\\{',src[:pos+1]))
    if not starts:return src.replace(literal,'')
    m=starts[-1];brace=src.find('{',m.start());depth=0;end=-1
    for i in range(brace,len(src)):
        if src[i]=='{':depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:
                end=i+1
                while end<len(src) and src[end] in ' \\t\\r\\n':end+=1
                break
    if end<0:return src.replace(literal,'')
    header=src[m.start():brace]
    nm=re.search(r'([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\([^()]*\\)\\s*$',header.strip())
    name=nm.group(1) if nm else ''
    src=src[:m.start()]+src[end:]
    if name:
        src=re.sub(r'(?m)^\\s*'+re.escape(name)+r'\\s*\\([^;]*\\)\\s*;\\s*$', '', src)
    return src
while '지금 가장 강한 딜' in s:
    old_s=s;s=remove_method_with_literal(s,'지금 가장 강한 딜')
    if s==old_s:break
s=s.replace('        addHeroDeal();\\n','').replace('        addHeroDeal(name);\\n','')
# Simplify list badges: remove urgency/confidence chips.
s=s.replace('TextView name,price,bigHot,discount,urgent,confidence,categoryLine,signalLine,ref,saving,meta,heart,trendLabel;','TextView name,price,bigHot,discount,categoryLine,signalLine,ref,saving,meta,heart,trendLabel;')
s=s.replace('h.badges=row();h.bigHot=badge("🔥 BIG HOT DEAL",Color.WHITE,accent);h.discount=badge("",accent,accentSoft);h.urgent=badge("",Color.WHITE,accent);h.confidence=badge("",muted,soft);h.badges.addView(h.bigHot);h.badges.addView(space(6,1));h.badges.addView(h.discount);h.badges.addView(space(6,1));h.badges.addView(h.urgent);h.badges.addView(space(6,1));h.badges.addView(h.confidence);c.addView(h.badges);',
'''h.badges=row();h.bigHot=badge("🔥 BIG HOT DEAL",Color.WHITE,accent);h.discount=badge("",accent,accentSoft);h.badges.addView(h.bigHot);h.badges.addView(space(6,1));h.badges.addView(h.discount);c.addView(h.badges);''')
s=s.replace('h.discount.setText("▼ "+x.declaredDiscountPct+"%");h.urgent.setText(DealUrgency.grade(u)+" · 긴급도 "+u);setConfidenceBadge(h.confidence,e.confidence);','h.discount.setText("▼ "+x.declaredDiscountPct+"%");')
s=s.replace('h.meta.setText((e.errorCandidate?"🚨 가격오류 의심 · ":"")+ago(x.firstSeen)+" 발견 · "+ago(x.lastSeen)+" 확인"+(System.currentTimeMillis()-x.lastSeen>15*60*1000?" · 갱신 대기":""));','h.meta.setText(ago(x.firstSeen)+" 발견 · "+ago(x.lastSeen)+" 확인"+(System.currentTimeMillis()-x.lastSeen>10*60*1000?" · 업데이트 지연":""));')
s=s.replace('case 4:c=Integer.compare(uy,ux);break;','case 4:c=Integer.compare(signalCount(b),signalCount(a));break;')
s=s.replace('v2.7은 서버 감시를 우선 사용합니다.', 'v2.8도 수집 범위와 내 알림 기준을 분리합니다.')
s=s.replace('v2.7 서버는 15분 주기로', 'v2.8 서버는 5분 주기로')
s=s.replace('현재 기기 알림 확인은 복구 폴링 방식이며 FCM 즉시 푸시는 다음 연결 단계에서 켭니다.','FCM 즉시 푸시가 우선이고, 15분 기기 폴링은 놓친 알림 복구용입니다.')
p.write_text(s)

# ServerSync: authenticated per-device threshold + FCM event recovery.
p=root/"app/src/main/java/com/dealflash/app/ServerSync.java"
s=p.read_text()
s=s.replace('/** v2.7.1 HTTP bridge. Collection stays independent from the user alert threshold. */','/** v2.8 alpha2 HTTP bridge. Collection stays independent from the user alert threshold; FCM is primary, polling is recovery. */')
# Stop obsolete trusted-data writes from the phone.
s=re.sub(r'\s*public static void uploadDeals\([\s\S]*?\n\s*public static void refreshStatus\(', '\n    /** v2.8 collection is server-managed. The phone never writes trusted market data. */\n    public static void uploadDeals(List<PageScanner.Listing> items,String source){}\n\n    /** v2.8 server health is written only by the Worker. */\n    public static void monitor(int status,int found,int accepted,long duration,String error){}\n\n    public static void refreshStatus(', s, count=1)
# Refresh status + device setting read.
old='''                JSONObject o=getJson("/api/status",5000,6000);
                ok=o.optBoolean("ok",true);monitoring=o.optBoolean("monitoring",false);push=o.optBoolean("push_ready",false);
                checked=o.optLong("last_scan_at",o.optLong("checked_at",0));version=o.optString("version","");'''
new='''                JSONObject o=getJson("/api/v1/status",5000,6000);
                ok=o.optBoolean("ok",true);monitoring=o.optBoolean("monitoring",false);push=o.optBoolean("push_ready",false);
                checked=o.optLong("last_scan_at",o.optLong("checked_at",0));version=o.optString("version","");
                String auth=deviceToken(c);
                if(!auth.isEmpty()){
                    try{JSONObject d=getJsonAuth("/api/v1/device/settings",auth,5000,6000);ScannerPrefs.threshold(c,d.optInt("alert_threshold",ScannerPrefs.threshold(c)));c.getSharedPreferences(PREF,0).edit().putString("threshold_sync_state","saved").apply();}catch(Exception ignored){}
                }'''
if old in s:s=s.replace(old,new)
# Replace threshold setter body.
s=re.sub(r'    public static void setAlertThreshold\(Context c,int threshold,Runnable done\)\{[\s\S]*?\n    \}\n    public static boolean thresholdSyncOk',
'''    public static void setAlertThreshold(Context c,int threshold,Runnable done){
        final int value=Math.max(10,Math.min(90,Math.round(threshold/5f)*5));
        ScannerPrefs.threshold(c,value);
        c.getSharedPreferences(PREF,0).edit().putString("threshold_sync_state","saving").apply();
        new Thread(()->{String state="failed";try{
            String auth=deviceToken(c);
            if(auth.isEmpty()){
                String fcm=PushManager.fcmToken(c);if(fcm.isEmpty())throw new IOException("FCM token not ready");
                registerPushTokenBlocking(c,fcm);auth=deviceToken(c);
            }
            JSONObject o=new JSONObject();o.put("alert_threshold",value);JSONObject r=postJsonAuth("/api/v1/device/settings",o,auth);
            ScannerPrefs.threshold(c,r.optInt("alert_threshold",value));state="saved";
        }catch(Exception ignored){}finally{c.getSharedPreferences(PREF,0).edit().putString("threshold_sync_state",state).apply();if(done!=null){try{new android.os.Handler(android.os.Looper.getMainLooper()).post(done);}catch(Exception ignored){done.run();}}}}).start();
    }
    public static boolean thresholdSyncOk''', s, count=1)
insert='''

    public static void registerPushToken(Context c,String fcmToken,Runnable done){
        if(fcmToken==null||fcmToken.length()<40){if(done!=null)done.run();return;}
        new Thread(()->{try{
            String auth=deviceToken(c);
            if(auth.isEmpty())registerPushTokenBlocking(c,fcmToken);
            else{JSONObject b=new JSONObject();b.put("fcm_token",fcmToken);postJsonAuth("/api/v1/device/token",b,auth);}
        }catch(Exception ignored){}finally{if(done!=null){try{new android.os.Handler(android.os.Looper.getMainLooper()).post(done);}catch(Exception ignored){done.run();}}}}).start();
    }

    private static void registerPushTokenBlocking(Context c,String fcmToken)throws Exception{
        JSONObject b=new JSONObject();b.put("fcm_token",fcmToken);b.put("alert_threshold",ScannerPrefs.threshold(c));b.put("platform","android");b.put("app_version","2.8.0-alpha2");
        JSONObject r=postJson("/api/v1/devices/register",b);String id=r.optString("device_id",""),token=r.optString("device_token","");if(id.isEmpty()||token.isEmpty())throw new IOException("device registration failed");
        c.getSharedPreferences(PREF,0).edit().putString("device_id",id).putString("device_token",token).putString("threshold_sync_state","saved").apply();
        ScannerPrefs.threshold(c,r.optInt("alert_threshold",ScannerPrefs.threshold(c)));
    }
    private static String deviceToken(Context c){return c.getSharedPreferences(PREF,0).getString("device_token","");}
    public static void markPushEventSeen(Context c,long eventId){if(eventId<=0)return;android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);long old=p.getLong("event_cursor",0);if(eventId>old)p.edit().putLong("event_cursor",eventId).apply();}
'''
marker='    public static void fetchHistory(final Activity a,String key,int days,final PriceHistoryView chart){'
if insert.strip() not in s:s=s.replace(marker,insert+"\n"+marker)
s=re.sub(r'    public static void watch\(String key,boolean enabled\)\{[\s\S]*?\n    \}', '    /** Bookmarks are local UI state; they do not change server collection. */\n    public static void watch(String key,boolean enabled){}', s, count=1)
# Replace alert polling.
s=re.sub(r'    /\*\* Pull alerts produced by the server\.[\s\S]*?\n    \}\n\n    private static JSONObject getJson',
'''    /** FCM is primary. JobScheduler pulls device events only to recover missed pushes. */
    public static void fetchAlerts(Context c,Runnable done){
        new Thread(()->{
            try{
                android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);String auth=deviceToken(c);
                if(auth.isEmpty()){if(!PushManager.fcmToken(c).isEmpty())registerPushTokenBlocking(c,PushManager.fcmToken(c));auth=deviceToken(c);}
                if(auth.isEmpty())return;
                long after=p.getLong("event_cursor",0);JSONObject root=getJsonAuth("/api/v1/device/events?after="+after+"&limit=50",auth,8000,8000);JSONArray a=root.optJSONArray("items");long max=after;
                if(a!=null)for(int i=0;i<a.length();i++){
                    JSONObject o=a.optJSONObject(i);if(o==null)continue;long id=o.optLong("event_id",0);max=Math.max(max,id);
                    String source=o.optString("source","FALLCENT"),key=o.optString("product_key",""),url=o.optString("product_url","");if(key.isEmpty()||url.isEmpty())continue;
                    String title=o.optString("title","가격 급락 포착"),body=o.optString("body",o.optString("product_name","상품"));
                    NotificationHelper.alertAuto(c,source,key,title,body,url,id>0?"event:"+id:key+"|"+body);
                }
                if(max>after)p.edit().putLong("event_cursor",max).apply();
            }catch(Exception ignored){}finally{if(done!=null)done.run();}
        }).start();
    }

    private static JSONObject getJson''', s, count=1)
# Auth-capable HTTP helpers.
s=re.sub(r'    private static JSONObject getJson\(String path,int connect,int read\)throws Exception\{[\s\S]*?\n    private static String readAll',
'''    private static JSONObject getJson(String path,int connect,int read)throws Exception{return getJsonAuth(path,"",connect,read);}
    private static JSONObject getJsonAuth(String path,String auth,int connect,int read)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(connect);c.setReadTimeout(read);c.setRequestMethod("GET");c.setRequestProperty("Accept","application/json");if(auth!=null&&!auth.isEmpty())c.setRequestProperty("Authorization","Bearer "+auth);
        int code=c.getResponseCode();if(code/100!=2){String er="";try{InputStream e=c.getErrorStream();if(e!=null)er=readAll(e);}catch(Exception ignored){}c.disconnect();throw new IOException("HTTP "+code+" "+er);}String raw=readAll(c.getInputStream());c.disconnect();return new JSONObject(raw);
    }
    private static void post(String path,JSONObject body)throws Exception{postJson(path,body);}
    private static JSONObject postJson(String path,JSONObject body)throws Exception{return postJsonAuth(path,body,"");}
    private static JSONObject postJsonAuth(String path,JSONObject body,String auth)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(12000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");if(auth!=null&&!auth.isEmpty())c.setRequestProperty("Authorization","Bearer "+auth);
        byte[] b=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(b);}int code=c.getResponseCode();if(code/100!=2){String er="";try{InputStream e=c.getErrorStream();if(e!=null)er=readAll(e);}catch(Exception ignored){}c.disconnect();throw new IOException("HTTP "+code+" "+er);}String raw="{}";try{InputStream in=c.getInputStream();if(in!=null){raw=readAll(in);in.close();}}catch(Exception ignored){}c.disconnect();return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
    }
    private static String readAll''', s, count=1)
p.write_text(s)

# New FCM classes.
(root/"app/src/main/java/com/dealflash/app/PushManager.java").write_text(r'''package com.dealflash.app;

import android.content.Context;
import com.google.firebase.messaging.FirebaseMessaging;

public final class PushManager {
    private static final String PREF="dealflash_push_v28";
    private PushManager(){}
    public static void ensure(Context c){
        final Context app=c.getApplicationContext();
        NotificationHelper.ensureChannels(app);
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task->{
            if(!task.isSuccessful()||task.getResult()==null)return;
            onNewToken(app,task.getResult());
        });
    }
    public static void onNewToken(Context c,String token){
        if(token==null||token.length()<40)return;
        c.getSharedPreferences(PREF,0).edit().putString("fcm_token",token).apply();
        ServerSync.registerPushToken(c,token,null);
    }
    public static String fcmToken(Context c){return c.getSharedPreferences(PREF,0).getString("fcm_token","");}
}
''')

(root/"app/src/main/java/com/dealflash/app/DealFlashMessagingService.java").write_text(r'''package com.dealflash.app;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class DealFlashMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token){PushManager.onNewToken(this,token);}
    @Override public void onMessageReceived(RemoteMessage message){
        Map<String,String> d=message.getData();if(d==null||d.isEmpty())return;
        String key=value(d,"product_key"),source=value(d,"source"),url=value(d,"product_url"),title=value(d,"title"),body=value(d,"body"),event=value(d,"event_id");
        if(key.isEmpty()||url.isEmpty())return;
        long eventId=0;try{eventId=Long.parseLong(event);}catch(Exception ignored){}
        if(eventId>0)ServerSync.markPushEventSeen(this,eventId);
        NotificationHelper.alertAuto(this,source.isEmpty()?"FALLCENT":source,key,title.isEmpty()?"가격 급락 포착":title,body,url,eventId>0?"event:"+eventId:key+"|"+body);
    }
    private static String value(Map<String,String> m,String k){String v=m.get(k);return v==null?"":v;}
}
''')

# Firebase app config. This is public client configuration, not the service-account private key.
(root/"app/google-services.json").write_text(r'''{
  "project_info": {
    "project_number": "311340984467",
    "project_id": "dealflash-9e830",
    "storage_bucket": "dealflash-9e830.firebasestorage.app"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:311340984467:android:ce38ea4f8e44aba5a44566",
        "android_client_info": {"package_name": "com.dealflash.app"}
      },
      "oauth_client": [],
      "api_key": [{"current_key": "AIzaSyAHtRlczRJdMvdrnBAzkZPcFsavzErvJM4"}],
      "services": {"appinvite_service": {"other_platform_oauth_client": []}}
    }
  ],
  "configuration_version": "1"
}
''')

# v2.8 notes.
(root/"V2.8-NOTES.md").write_text("""# DealFlash v2.8.0-alpha2

- BIG HOT DEAL is a fixed 60%+ visual class inside normal cards; no hero card.
- User alert threshold is per device and never changes server collection.
- Firebase Cloud Messaging is the primary alert path; 15-minute polling is recovery only.
- 5-minute server scan target, browser closes immediately after extraction.
- Third-party automatic comparison is disabled while identity/price accuracy is rebuilt.
""")
print("v2.8 overlay applied")
