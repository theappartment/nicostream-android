package com.nicostream.demo;
import java.net.URI;
import java.util.*;
import org.json.*;
/** Public provider routes only. Never moves browser cookies, headers or signed media URLs. */
final class SourceDomains {
 static boolean providerHost(String host){return host!=null&&host.toLowerCase(Locale.ROOT).matches("(?:www\\.)?streaming-?community[a-z0-9-]*\\.[a-z]{2,}");}
 static String origin(String value){
  try{URI u=new URI(value);if(!"https".equals(u.getScheme())||!providerHost(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443||u.getRawQuery()!=null||u.getRawFragment()!=null)return "";String path=u.getRawPath();if(path!=null&&!path.isEmpty()&&!path.equals("/")&&!path.equals("/it")&&!path.equals("/it/"))return "";return "https://"+u.getHost().toLowerCase(Locale.ROOT);}catch(Exception e){return "";}
 }
 static String rebase(String url,String candidate){
  String root=origin(candidate);if(root.isEmpty())return url;
  try{URI u=new URI(url);if(!"https".equals(u.getScheme())||!providerHost(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443)return url;
   return root+(u.getRawPath()==null?"":u.getRawPath())+(u.getRawQuery()==null?"":"?"+u.getRawQuery())+(u.getRawFragment()==null?"":"#"+u.getRawFragment());
  }catch(Exception e){return url;}
 }
 static void migrate(Object node,String origin)throws JSONException {
  if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int n=0;n<a.length();n++)migrate(a.opt(n),origin);}
  else if(node instanceof JSONObject){JSONObject o=(JSONObject)node;Iterator<String> keys=o.keys();while(keys.hasNext()){String key=keys.next();Object value=o.opt(key);
   if(value instanceof String&&(key.equals("url")||key.equals("id")||key.equals("webSource")||key.equals("previewReferer")))o.put(key,rebase((String)value,origin));
   else if(!key.equals("headers")&&!key.equals("browserHeaders")&&!key.equals("drm"))migrate(value,origin);
  }}
 }
}
