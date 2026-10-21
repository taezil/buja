package org.json;
class JSONTokener {
  final String s;int p=0;JSONTokener(String s){this.s=s;}
  void ws(){while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;}
  Object nextValue()throws JSONException{ws();if(p>=s.length())throw new JSONException("eof");char c=s.charAt(p);
    if(c=='{'){JSONObject o=new JSONObject();p++;ws();if(s.charAt(p)=='}'){p++;return o;}while(true){ws();String k=(String)nextValue();ws();if(s.charAt(p++)!=':')throw new JSONException("colon");o.m.put(k,nextValue());ws();char d=s.charAt(p++);if(d=='}')return o;if(d!=',')throw new JSONException("comma");}}
    if(c=='['){JSONArray a=new JSONArray();p++;ws();if(s.charAt(p)==']'){p++;return a;}while(true){a.a.add(nextValue());ws();char d=s.charAt(p++);if(d==']')return a;if(d!=',')throw new JSONException("comma");}}
    if(c=='"'){p++;StringBuilder b=new StringBuilder();while(s.charAt(p)!='"'){char x=s.charAt(p++);if(x=='\\'){char e=s.charAt(p++);switch(e){case 'n':b.append('\n');break;case 't':b.append('\t');break;case 'u':b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;break;default:b.append(e);}}else b.append(x);}p++;return b.toString();}
    if(s.startsWith("null",p)){p+=4;return JSONObject.NULL;} if(s.startsWith("true",p)){p+=4;return true;} if(s.startsWith("false",p)){p+=5;return false;}
    int st=p;while(p<s.length()&&"+-0123456789.eE".indexOf(s.charAt(p))>=0)p++;String n=s.substring(st,p);if(n.isEmpty())throw new JSONException("bad token at "+st);
    if(n.contains(".")||n.contains("e")||n.contains("E"))return Double.parseDouble(n);long l=Long.parseLong(n);return (l>=Integer.MIN_VALUE&&l<=Integer.MAX_VALUE)?(Object)Integer.valueOf((int)l):(Object)Long.valueOf(l);}
  static String quote(String x){StringBuilder b=new StringBuilder("\"");for(char c:x.toCharArray()){if(c=='"'||c=='\\')b.append('\\').append(c);else if(c=='\n')b.append("\\n");else b.append(c);}return b.append('"').toString();}
  static String write(Object v){if(v==null||v==JSONObject.NULL)return "null";if(v instanceof String)return quote((String)v);return String.valueOf(v);}
}
