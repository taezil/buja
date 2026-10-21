package org.json;
import java.util.*;
public class JSONArray {
  final ArrayList<Object> a=new ArrayList<>();
  public JSONArray(){}
  public JSONArray(String s)throws JSONException{Object v=new JSONTokener(s).nextValue();if(!(v instanceof JSONArray))throw new JSONException("not an array");a.addAll(((JSONArray)v).a);}
  public JSONArray put(Object v){a.add(v);return this;}
  public int length(){return a.size();}
  public Object opt(int i){return i>=0&&i<a.size()?a.get(i):null;}
  public JSONObject optJSONObject(int i){Object v=opt(i);return v instanceof JSONObject?(JSONObject)v:null;}
  public JSONObject getJSONObject(int i)throws JSONException{JSONObject o=optJSONObject(i);if(o==null)throw new JSONException("no object at "+i);return o;}
  public String toString(){StringBuilder b=new StringBuilder("[");for(int i=0;i<a.size();i++){if(i>0)b.append(',');b.append(JSONTokener.write(a.get(i)));}return b.append(']').toString();}
}
