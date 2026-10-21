package org.json;
import java.util.*;
/** Test shim mimicking Android's org.json semantics (incl. optString(JSON null) == "null", put(NaN) throws). */
public class JSONObject {
  public static final Object NULL=new Object(){public boolean equals(Object o){return o==this||o==null;} public String toString(){return "null";}};
  final LinkedHashMap<String,Object> m=new LinkedHashMap<>();
  public JSONObject(){}
  public JSONObject(String s)throws JSONException{Object v=new JSONTokener(s).nextValue();if(!(v instanceof JSONObject))throw new JSONException("not an object");m.putAll(((JSONObject)v).m);}
  public JSONObject put(String k,Object v)throws JSONException{if(v instanceof Double&&(((Double)v).isNaN()||((Double)v).isInfinite()))throw new JSONException("Forbidden numeric value: "+v);m.put(k,v);return this;}
  public JSONObject put(String k,double v)throws JSONException{return put(k,(Object)Double.valueOf(v));}
  public JSONObject put(String k,long v)throws JSONException{return put(k,(Object)Long.valueOf(v));}
  public JSONObject put(String k,int v)throws JSONException{return put(k,(Object)Integer.valueOf(v));}
  public JSONObject put(String k,boolean v)throws JSONException{return put(k,(Object)Boolean.valueOf(v));}
  public Object remove(String k){return m.remove(k);}
  public boolean has(String k){return m.containsKey(k);}
  public boolean isNull(String k){Object v=m.get(k);return v==null||v==NULL;}
  public Object opt(String k){return m.get(k);}
  public Iterator<String> keys(){return m.keySet().iterator();}
  public int length(){return m.size();}
  public String optString(String k){return optString(k,"");}
  public String optString(String k,String d){Object v=m.get(k);return v==null?d:String.valueOf(v);}
  public double optDouble(String k){return optDouble(k,Double.NaN);}
  public double optDouble(String k,double d){Object v=m.get(k);if(v instanceof Number)return ((Number)v).doubleValue();if(v instanceof String)try{return Double.parseDouble((String)v);}catch(Exception e){}return d;}
  public int optInt(String k){return optInt(k,0);}
  public int optInt(String k,int d){Object v=m.get(k);if(v instanceof Number)return ((Number)v).intValue();if(v instanceof String)try{return (int)Double.parseDouble((String)v);}catch(Exception e){}return d;}
  public long optLong(String k){return optLong(k,0);}
  public long optLong(String k,long d){Object v=m.get(k);if(v instanceof Number)return ((Number)v).longValue();if(v instanceof String)try{return (long)Double.parseDouble((String)v);}catch(Exception e){}return d;}
  public boolean optBoolean(String k){return optBoolean(k,false);}
  public boolean optBoolean(String k,boolean d){Object v=m.get(k);if(v instanceof Boolean)return (Boolean)v;if(v instanceof String){String s=((String)v).toLowerCase();if(s.equals("true"))return true;if(s.equals("false"))return false;}return d;}
  public JSONArray optJSONArray(String k){Object v=m.get(k);return v instanceof JSONArray?(JSONArray)v:null;}
  public JSONObject optJSONObject(String k){Object v=m.get(k);return v instanceof JSONObject?(JSONObject)v:null;}
  public String toString(){StringBuilder b=new StringBuilder("{");boolean f=true;for(Map.Entry<String,Object> e:m.entrySet()){if(!f)b.append(',');f=false;b.append(JSONTokener.quote(e.getKey())).append(':').append(JSONTokener.write(e.getValue()));}return b.append('}').toString();}
}
