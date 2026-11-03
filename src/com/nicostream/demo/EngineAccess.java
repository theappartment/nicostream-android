package com.nicostream.demo;
import java.net.URI;
/** The private engine key belongs only to its exact HTTPS origin. */
final class EngineAccess {
 static boolean sameOrigin(String server,String target){
  try{URI a=new URI(server),b=new URI(target);return "https".equals(a.getScheme())&&"https".equals(b.getScheme())&&a.getHost()!=null&&a.getHost().equalsIgnoreCase(b.getHost())&&port(a)==port(b)&&a.getUserInfo()==null&&b.getUserInfo()==null;}catch(Exception e){return false;}
 }
 static int port(URI u){return u.getPort()==-1?443:u.getPort();}
 static String authorization(String server,String target,String key){return key!=null&&!key.isEmpty()&&sameOrigin(server,target)?"Bearer "+key:"";}
}
