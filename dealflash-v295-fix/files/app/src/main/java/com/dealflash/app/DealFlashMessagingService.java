package com.dealflash.app;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class DealFlashMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token){super.onNewToken(token);ServerSync.registerDevice(this,token,null);}
    @Override public void onMessageReceived(RemoteMessage message){
        super.onMessageReceived(message);Map<String,String> d=message.getData();if(d==null||d.isEmpty())return;
        String key=d.get("product_key"),source=d.get("source"),title=d.get("title"),url=d.get("product_url"),event=d.get("event_id"),kind=d.get("kind"),product=d.get("product_name");
        if(key==null||key.isEmpty()||url==null||url.isEmpty())return;
        int drop=parseInt(d.get("drop_rate")),threshold=ScannerPrefs.threshold(this);
        // Final device-side guard: server state can be briefly stale during setting sync, but the phone must never show a below-threshold deal.
        if(!"TEST".equals(kind)&&drop<threshold)return;
        double price=parseDouble(d.get("price"));String src=source==null?"FALLCENT":source;String openKey=key;
        if(!"TEST".equals(kind)){
            String name=product==null?"상품":product;
            // One shared write path (HistoryStore + HotDealStore). Rejections are recorded in SyncDiag, never swallowed.
            try{String stored=DealsRepository.applyPushKey(this,key,name,url,src,price,drop,d.get("image_url"),System.currentTimeMillis());if(stored!=null)openKey=stored;}
            catch(Throwable t){SyncDiag.error(this,"fcm-store",t);}
            ServerSync.refreshDeals(this,null);
        }
        String body="TEST".equals(kind)?d.get("body"):(product==null?"상품":product)+" · "+won(price)+" · 내 알림 "+threshold+"%+";
        NotificationHelper.alertAuto(this,src,openKey,title==null?"가격 급락 포착":title,body==null?"DealFlash가 새 급락을 포착했어요":body,url,"fcm:"+(event==null?key:event));
    }
    private static int parseInt(String x){try{return Integer.parseInt(x==null?"0":x);}catch(Exception e){return 0;}}
    private static double parseDouble(String x){try{return Double.parseDouble(x==null?"0":x);}catch(Exception e){return 0;}}
    private static String won(double x){return new java.text.DecimalFormat("#,##0").format(Math.round(x))+"원";}
}
