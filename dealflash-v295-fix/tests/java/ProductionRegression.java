package com.dealflash.app;

import org.json.*;
import java.nio.file.*;

/** Replays REAL production /api/deals items through the strict identity gate. Run against ORIGINAL and PATCHED classes. */
public class ProductionRegression {
  public static void main(String[] a)throws Exception{
    JSONArray items=new JSONObject(new String(Files.readAllBytes(Paths.get(a[0])),"UTF-8")).optJSONArray("items");
    int ok=0,total=items.length();
    for(int i=0;i<total;i++){
      JSONObject o=items.optJSONObject(i);
      String key=o.optString("product_key"),url=o.optString("product_url"),title=o.optString("product_name");
      boolean v=DataQuality.valid(key,o.optString("source"),url,title,o.optDouble("current_price"),o.optInt("last_drop_rate"));
      String local=ProductUrl.key("FALLCENT",ProductUrl.canonical("FALLCENT",url));
      if(v)ok++;
      System.out.println((v?"ACCEPT ":"REJECT ")+key+"   app would derive: "+local);
    }
    System.out.println("RESULT original DataQuality.valid(): "+ok+"/"+total+" accepted");
  }
}
