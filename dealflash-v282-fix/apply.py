from pathlib import Path

root=Path('.')

def replace(path, old, new):
    p=root/path
    s=p.read_text()
    if old not in s:
        raise SystemExit(f'needle missing: {path}: {old[:100]!r}')
    p.write_text(s.replace(old,new,1))

replace('app/build.gradle', "versionCode 36\n        versionName '2.8.1'", "versionCode 37\n        versionName '2.8.2'")

p=root/'app/src/main/java/com/dealflash/app/DealFlashMessagingService.java'
s=p.read_text()
old='''        String key=d.get("product_key"),source=d.get("source"),title=d.get("title"),body=d.get("body"),url=d.get("product_url"),event=d.get("event_id");
        if(key==null||key.isEmpty()||url==null||url.isEmpty())return;
        NotificationHelper.alertAuto(this,source==null?"FALLCENT":source,key,title==null?"가격 급락 포착":title,body==null?"DealFlash가 새 급락을 포착했어요":body,url,"fcm:"+(event==null?key:event));'''
new='''        String key=d.get("product_key"),source=d.get("source"),title=d.get("title"),url=d.get("product_url"),event=d.get("event_id"),kind=d.get("kind"),product=d.get("product_name");
        if(key==null||key.isEmpty()||url==null||url.isEmpty())return;
        int drop=parseInt(d.get("drop_rate")),threshold=ScannerPrefs.threshold(this);
        // Final device-side guard: server state can be briefly stale during setting sync, but the phone must never show a below-threshold deal.
        if(!"TEST".equals(kind)&&drop<threshold)return;
        double price=parseDouble(d.get("price"));String src=source==null?"FALLCENT":source;
        if(!"TEST".equals(kind))HotDealStore.put(this,key,product==null?"상품":product,url,src,price,0,drop,null);
        String body="TEST".equals(kind)?d.get("body"):(product==null?"상품":product)+" · "+won(price)+" · 내 알림 "+threshold+"%+";
        NotificationHelper.alertAuto(this,src,key,title==null?"가격 급락 포착":title,body==null?"DealFlash가 새 급락을 포착했어요":body,url,"fcm:"+(event==null?key:event));'''
if old not in s: raise SystemExit('FCM body needle missing')
s=s.replace(old,new,1)
s=s.replace('\n}', '\n    private static int parseInt(String x){try{return Integer.parseInt(x==null?"0":x);}catch(Exception e){return 0;}}\n    private static double parseDouble(String x){try{return Double.parseDouble(x==null?"0":x);}catch(Exception e){return 0;}}\n    private static String won(double x){return new java.text.DecimalFormat("#,##0").format(Math.round(x))+"원";}\n}',1)
p.write_text(s)

replace('app/src/main/java/com/dealflash/app/ServerSync.java',
'''                    ScannerPrefs.threshold(c,ds.optInt("alert_threshold",ScannerPrefs.threshold(c)));
                    c.getSharedPreferences(PREF,0).edit().putString("threshold_sync_state","saved").putString("push_state",push?"ready":"registered").apply();''',
'''                    // Registration/settings writes reconcile the threshold. Status refresh must not race an explicit local choice and lower it.
                    c.getSharedPreferences(PREF,0).edit().putString("threshold_sync_state","saved").putString("push_state",push?"ready":"registered").apply();''')
replace('app/src/main/java/com/dealflash/app/ServerSync.java',
'JSONObject o=new JSONObject();o.put("device_id",DeviceIdentity.id(app));o.put("fcm_token",fcmToken);o.put("alert_threshold",ScannerPrefs.threshold(app));o.put("app_version","2.8.1");',
'JSONObject o=new JSONObject();o.put("device_id",DeviceIdentity.id(app));o.put("auth_token",DeviceIdentity.authToken(app));o.put("fcm_token",fcmToken);o.put("alert_threshold",ScannerPrefs.threshold(app));o.put("app_version","2.8.2");')
replace('app/src/main/java/com/dealflash/app/ServerSync.java',
'''                    String source=o.optString("source","FALLCENT"),key=o.optString("product_key",""),url=o.optString("product_url","");if(key.isEmpty()||url.isEmpty())continue;
                    String title=o.optString("title","가격 급락 포착"),body=o.optString("body",o.optString("product_name","상품"));
                    NotificationHelper.alertAuto(c,source,key,title,body,url,"event:"+eventId);''',
'''                    String source=o.optString("source","FALLCENT"),key=o.optString("product_key",""),url=o.optString("product_url","");if(key.isEmpty()||url.isEmpty())continue;
                    int drop=o.optInt("drop_rate",0),localThreshold=ScannerPrefs.threshold(c);if(drop<localThreshold)continue;
                    String product=o.optString("product_name","상품"),title=o.optString("title","가격 급락 포착");double price=o.optDouble("price",0);
                    HotDealStore.put(c,key,product,url,source,price,0,drop,null);
                    String body=product+" · "+formatWon(price)+" · 내 알림 "+localThreshold+"%+";
                    NotificationHelper.alertAuto(c,source,key,title,body,url,"event:"+eventId);''')
p=root/'app/src/main/java/com/dealflash/app/ServerSync.java'
s=p.read_text()
needle='    private static String readAll(InputStream in)throws Exception{'
if needle not in s: raise SystemExit('readAll needle missing')
p.write_text(s.replace(needle,'    private static String formatWon(double v){return new java.text.DecimalFormat("#,##0").format(Math.round(v))+"원";}\n'+needle,1))

replace('app/src/main/java/com/dealflash/app/MainActivity.java',
'if("발견".equals(name))return "새로 포착한 가격 신호";',
'if("발견".equals(name))return "실시간 인기 · 오늘 인기 · 타이밍 신호";')
replace('app/src/main/java/com/dealflash/app/MainActivity.java',
'TextView signalLabel=text("DEAL SIGNALS",10,true,muted);',
'TextView signalLabel=text("DEAL SIGNALS · 알림과 무관한 발견 신호",10,true,muted);')
replace('app/src/main/java/com/dealflash/app/MainActivity.java',
'@Override protected void onResume(){super.onResume();handler.post(tick);}',
'@Override protected void onResume(){super.onResume();handler.post(tick);if(ServerState.active(this)&&System.currentTimeMillis()-ServerSync.lastDealSync(this)>4L*60*1000)ServerSync.restoreDeals(this);}')
replace('app/src/main/java/com/dealflash/app/MainActivity.java',
'''    private void reload(){
        if(list==null||loading)return;loading=true;final ListView target=list;final String targetScreen=screen;
        reads.execute(()->{try{final Map<String,HistoryStore.H> snapshot=HistoryStore.load(this);final List<HotDealStore.Hot> hotSnapshot="급락".equals(targetScreen)?HotDealStore.recent(this):Collections.emptyList();runOnUiThread(()->{loading=false;if(!isDestroyed()&&list==target)applySnapshot(snapshot,hotSnapshot);else if(!isDestroyed()&&list!=null)reload();});}catch(Exception error){runOnUiThread(()->{loading=false;if(list==target&&count!=null)count.setText("목록을 읽지 못했어요. 잠시 후 다시 시도합니다.");});}});
    }''',
'''    private void reload(){
        if(list==null||loading)return;final ListView target=list;final String targetScreen=screen;
        // Make tab/filter taps instant from the last immutable snapshot, then refresh storage off the UI thread.
        if(!latestHistory.isEmpty())applySnapshot(new HashMap<>(latestHistory),new ArrayList<>(hot.values()));
        loading=true;
        try{reads.execute(()->{try{final Map<String,HistoryStore.H> snapshot=HistoryStore.load(this);final List<HotDealStore.Hot> hotSnapshot="급락".equals(targetScreen)?HotDealStore.recent(this):Collections.emptyList();runOnUiThread(()->{loading=false;if(!isDestroyed()&&list==target)applySnapshot(snapshot,hotSnapshot);else if(!isDestroyed()&&list!=null)reload();});}catch(Exception error){runOnUiThread(()->{loading=false;if(list==target&&count!=null)count.setText("목록을 읽지 못했어요. 잠시 후 다시 시도합니다.");});}});}catch(Exception ignored){loading=false;}
    }''')
replace('app/src/main/java/com/dealflash/app/MainActivity.java',
'Map<String,HistoryStore.H> all=HistoryStore.load(this);HistoryStore.H x=all.get(key);',
'Map<String,HistoryStore.H> all=latestHistory.isEmpty()?HistoryStore.load(this):new HashMap<>(latestHistory);HistoryStore.H x=all.get(key);')
replace('app/src/main/java/com/dealflash/app/MainActivity.java',
'@Override protected void onDestroy(){handler.removeCallbacks(tick);reads.shutdownNow();super.onDestroy();}',
'@Override protected void onDestroy(){handler.removeCallbacks(tick);try{reads.shutdownNow();}catch(Exception ignored){}super.onDestroy();}')

(root/'V2.8.2-NOTES.md').write_text('''# DealFlash v2.8.2

- 발견 탭은 실시간 인기/오늘 인기/지금이 타이밍/카테고리 급락 신호 탐색용이며 알림 기준과 분리.
- FCM과 폴링 복구 경로 모두 기기 로컬 알림 기준을 최종 검사해 기준 미만 알림 차단.
- 기기 재등록 시 인증 토큰을 보내 서버의 기기별 알림 기준을 안전하게 동기화.
- 상태 새로고침이 사용자의 방금 선택한 기준을 낮은 서버 값으로 덮어쓰는 경합 제거.
- 탭/카테고리/정렬 전환은 메모리 스냅샷을 즉시 렌더링하고 저장소 갱신은 백그라운드 처리.
- 종료 시 executor 경합 방어 및 상세 화면의 불필요한 전체 JSON 재파싱 감소.
- v2.8.1의 이미지 다운로드 제한, 목록 상한, 미니 차트 제거 안정화 유지.
''')
print('v2.8.2 hotfix applied')
