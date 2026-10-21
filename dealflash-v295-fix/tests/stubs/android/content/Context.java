package android.content;
import java.util.*;
/** JVM test stub: in-memory SharedPreferences (same String instances are returned, like Android). */
public class Context {
  public static final int MODE_PRIVATE=0; public static final String JOB_SCHEDULER_SERVICE="jobscheduler";
  public Object getSystemService(String n){return null;}
  private final Map<String,Map<String,Object>> files=new HashMap<>();
  public Context getApplicationContext(){return this;}
  public SharedPreferences getSharedPreferences(String name,int mode){
    Map<String,Object> m=files.computeIfAbsent(name,k->new HashMap<>());
    return new SharedPreferences(){
      public String getString(String k,String d){Object v=m.get(k);return v instanceof String?(String)v:d;}
      public int getInt(String k,int d){Object v=m.get(k);return v instanceof Integer?(Integer)v:d;}
      public long getLong(String k,long d){Object v=m.get(k);return v instanceof Long?(Long)v:d;}
      public boolean getBoolean(String k,boolean d){Object v=m.get(k);return v instanceof Boolean?(Boolean)v:d;}
      public Editor edit(){return new Editor(){
        Editor put(String k,Object v){m.put(k,v);return this;}
        public Editor putString(String k,String v){return put(k,v);} public Editor putInt(String k,int v){return put(k,v);}
        public Editor putLong(String k,long v){return put(k,v);} public Editor putBoolean(String k,boolean v){return put(k,v);}
        public Editor remove(String k){m.remove(k);return this;} public Editor clear(){m.clear();return this;}
        public void apply(){} public boolean commit(){return true;}};}
    };
  }
}
