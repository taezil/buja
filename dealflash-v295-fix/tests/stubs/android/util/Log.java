package android.util;
public final class Log { public static boolean QUIET=true;
  public static int i(String t,String m){if(!QUIET)System.out.println("I/"+t+": "+m);return 0;}
  public static int w(String t,String m){if(!QUIET)System.out.println("W/"+t+": "+m);return 0;}
  public static int e(String t,String m){if(!QUIET)System.out.println("E/"+t+": "+m);return 0;}
  public static int e(String t,String m,Throwable x){return e(t,m);}
  public static int d(String t,String m){return 0;} }
