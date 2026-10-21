package com.dealflash.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

/** Persistent, human-readable record of the last sync so an empty feed can always explain itself. */
public final class SyncDiag {
    private static final String PREF="dealflash_sync_diag";
    private SyncDiag(){}
    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,0);}

    public static void error(Context c,String stage,Throwable t){
        String m=stage+": "+t.getClass().getSimpleName()+": "+t.getMessage();
        android.util.Log.e("DealFlashSync",m,t);
        p(c).edit().putString("deals_sync_error",m).putLong("deals_sync_error_at",System.currentTimeMillis()).apply();
    }
    public static void record(Context c,String endpoint,ServerDealsParser.Counters k,int saved,int visibleAfterSave,int persisted,String saveError,String sampleRaw){
        StringBuilder reasons=new StringBuilder();for(Map.Entry<String,Integer> e:k.reasons.entrySet()){if(reasons.length()>0)reasons.append(", ");reasons.append(e.getKey()).append('=').append(e.getValue());}
        StringBuilder prefixes=new StringBuilder();for(Map.Entry<String,Integer> e:k.keyPrefixes.entrySet()){if(prefixes.length()>0)prefixes.append(", ");prefixes.append(e.getKey()).append(e.getValue());}
        p(c).edit().putString("deals_endpoint",endpoint).putInt("deals_server_count",k.serverItems).putInt("deals_parsed_count",k.parsed)
          .putInt("deals_saved_count",saved).putInt("deals_rejected_count",k.invalid).putString("deals_reject_reasons",reasons.toString())
          .putString("deals_reject_samples",String.join("\n",k.samples)).putInt("deals_stale_count",k.stale).putInt("deals_visible_after_save",visibleAfterSave)
          .putInt("deals_persisted_count",persisted).putString("deals_save_error",saveError==null?"":saveError)
          .putString("deals_key_prefixes",prefixes.toString()).putString("deals_root_keys",k.rootKeys+" -> "+k.itemsKey)
          .putString("deals_raw_sample",sampleRaw==null?"":sampleRaw).putInt("deals_sec_ts",k.secondsTimestamps).putInt("deals_no_ts",k.noTimestamp)
          .putLong("deals_last_sync",System.currentTimeMillis()).remove("deals_sync_error").apply();
        android.util.Log.i("DealFlashSync","server="+k.serverItems+" parsed="+k.parsed+" rejected="+k.invalid+" ["+reasons+"] saved="+saved+" persisted="+persisted+" visible="+visibleAfterSave+" stale="+k.stale);
    }
    public static void push(Context c,String key,String result,String reason){
        p(c).edit().putLong("push_last_at",System.currentTimeMillis()).putString("push_last_key",key==null?"":key).putString("push_last_result",result).putString("push_last_reason",reason==null?"":reason).apply();
    }
    public static void manual(Context c,String result){p(c).edit().putString("manual_scan_result",result).putLong("manual_scan_at",System.currentTimeMillis()).apply();}

    /** Short line for the empty-state message. */
    public static String oneLine(Context c){
        SharedPreferences s=p(c);
        if(s.getLong("deals_last_sync",0)==0&&s.getString("deals_sync_error","").isEmpty())return "서버 동기화 대기 중";
        String err=s.getString("deals_sync_error","");
        if(!err.isEmpty())return "동기화 오류 · "+err;
        return "서버 "+s.getInt("deals_server_count",0)+" · 통과 "+s.getInt("deals_parsed_count",0)+" · 제외 "+s.getInt("deals_rejected_count",0)
            +(s.getInt("deals_stale_count",0)>0?" · 24h초과 "+s.getInt("deals_stale_count",0):"")+" · 표시가능 "+s.getInt("deals_visible_after_save",0);
    }
    public static String report(Context c){
        SharedPreferences s=p(c);
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("MM.dd HH:mm:ss",java.util.Locale.KOREA);
        StringBuilder b=new StringBuilder();
        b.append("endpoint ").append(s.getString("deals_endpoint","-")).append("\nroot ").append(s.getString("deals_root_keys","-")).append('\n');
        b.append("server=").append(s.getInt("deals_server_count",0)).append(" parsed=").append(s.getInt("deals_parsed_count",0)).append(" saved=").append(s.getInt("deals_saved_count",0))
         .append(" persisted=").append(s.getInt("deals_persisted_count",0)).append(" rejected=").append(s.getInt("deals_rejected_count",0)).append(" stale24h=").append(s.getInt("deals_stale_count",0)).append(" visible=").append(s.getInt("deals_visible_after_save",0)).append('\n');
        b.append("reasons ").append(s.getString("deals_reject_reasons","-")).append("\nkey prefixes ").append(s.getString("deals_key_prefixes","-")).append('\n');
        String smp=s.getString("deals_reject_samples","");if(!smp.isEmpty())b.append("rejected samples:\n").append(smp).append('\n');
        String raw=s.getString("deals_raw_sample","");if(!raw.isEmpty())b.append("first item: ").append(raw).append('\n');
        long t=s.getLong("deals_last_sync",0);b.append("last sync ").append(t>0?f.format(new java.util.Date(t)):"never").append('\n');
        String err=s.getString("deals_sync_error","");if(!err.isEmpty())b.append("ERROR ").append(err).append('\n');
        String se=s.getString("deals_save_error","");if(!se.isEmpty())b.append("SAVE ERROR ").append(se).append('\n');
        b.append("push ").append(s.getString("push_last_result","-")).append(' ').append(s.getString("push_last_reason","")).append(' ').append(s.getString("push_last_key","")).append('\n');
        b.append("manual ").append(s.getString("manual_scan_result","-"));
        return b.toString();
    }
}
