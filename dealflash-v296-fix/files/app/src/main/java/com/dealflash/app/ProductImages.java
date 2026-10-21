package com.dealflash.app;

import android.app.Activity;
import android.graphics.*;
import android.widget.ImageView;
import android.util.LruCache;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

/**
 * Bounded downloads, decoded to the size actually shown, one shared bounded cache, recycled-view ownership checks.
 * v2.9.6: list thumbnails are decoded at ~view size (RGB_565) instead of up to 600px ARGB_8888, i.e. roughly 1/10 of the memory per image.
 */
public final class ProductImages {
    private static final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(60),new ThreadPoolExecutor.DiscardPolicy());
    /** 1/16 of the app heap, clamped to 4..16 MB. */
    static final int CACHE_BYTES=(int)Math.max(4L*1024*1024,Math.min(16L*1024*1024,Runtime.getRuntime().maxMemory()/16));
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(CACHE_BYTES){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private static final int MAX_BYTES=2*1024*1024;

    /** Full-size (detail screen): up to 600px. */
    public static void load(Activity activity,ImageView view,String url,String key){load(activity,view,url,key,600,false);}
    /** List thumbnail: decoded for a square target of {@code targetPx} pixels. */
    public static void loadThumb(Activity activity,ImageView view,String url,String key,int targetPx){load(activity,view,url,key,Math.max(96,Math.min(600,targetPx)),true);}

    private static void load(Activity activity,ImageView view,String url,String key,int target,boolean thumb){
        String tag=key+"|"+url+"|"+target;view.setTag(tag);view.setImageResource(android.R.drawable.ic_menu_gallery);
        if(url==null||!url.startsWith("https://"))return;
        Bitmap cached=cache.get(tag);if(cached!=null){view.setImageBitmap(cached);return;}
        pool.execute(()->{HttpURLConnection conn=null;try{
            if(!tag.equals(view.getTag()))return;                      // view was recycled while queued: skip the download
            conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(4000);conn.setReadTimeout(5000);conn.setInstanceFollowRedirects(false);
            if(conn.getResponseCode()!=200)return;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=conn.getInputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>MAX_BYTES)return;bytes.write(buffer,0,n);}}
            byte[] raw=bytes.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,o);
            if(o.outWidth<=0||o.outHeight<=0)return;
            o.inSampleSize=ImageSampling.sampleSize(o.outWidth,o.outHeight,target);if(thumb)o.inPreferredConfig=Bitmap.Config.RGB_565;
            o.inJustDecodeBounds=false;Bitmap image=BitmapFactory.decodeByteArray(raw,0,raw.length,o);if(image==null)return;cache.put(tag,image);
            activity.runOnUiThread(()->{if(!activity.isDestroyed()&&tag.equals(view.getTag()))view.setImageBitmap(image);});
        }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}});
    }
}
