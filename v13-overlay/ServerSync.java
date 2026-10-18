package com.dealflash.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ServerSync {
    private static final String BASE="https://dealflash-server.pittytntn1.workers.dev";
    private ServerSync(){}
    public static void uploadDeals(List<PageScanner.Listing> items,String source){
        if(items==null||items.isEmpty())return;
        try{
            JSONArray a=new JSONArray();
            for(PageScanner.Listing x:items){
                String u=ProductUrl.canonical(source,x.url); if(u==null)continue;
                JSONObject o=new JSONObject();
                o.put("product_key",ProductUrl.key(source,u));o.put("product_name",x.title==null?"상품":x.title);
                o.put("product_url",u);o.put("coupang_url",u);o.put("image_url",x.imageUrl==null?"":x.imageUrl);
                o.put("shipping_type",x.rocket?"ROCKET":"UNKNOWN");o.put("category",source);
                o.put("current_price",Math.round(x.price));o.put("drop_rate",x.declaredDiscountPct);
                o.put("deal_score",Math.min(100,(int)Math.round(x.declaredDiscountPct*0.70)));
                o.put("is_price_error_candidate",x.declaredDiscountPct>=70?1:0);a.put(o);
            }
            if(a.length()>0){JSONObject root=new JSONObject();root.put("items",a);post("/api/deals",root);}
        }catch(Exception ignored){}
    }
    public static void monitor(int status,int found,int accepted,long duration,String error){
        try{JSONObject o=new JSONObject();o.put("http_status",status);o.put("products_found",found);o.put("products_accepted",accepted);o.put("alerts_generated",accepted);o.put("duration_ms",duration);if(error!=null)o.put("error_message",error);post("/api/monitor",o);}catch(Exception ignored){}
    }
    private static void post(String path,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(4000);c.setReadTimeout(5000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        byte[] b=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream os=c.getOutputStream()){os.write(b);}c.getResponseCode();c.disconnect();
    }
}