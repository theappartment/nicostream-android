package com.nicostream.demo;

import org.json.*;
import java.net.URI;
import java.util.Locale;

/** Strict public GitHub release contract; no GitHub credentials are shipped in the app. */
final class UpdateRelease {
    static final String MANIFEST="nicostream-update.json",APK="NicoStream.apk";
    static final long MAX_APK=200L*1024*1024;
    final String versionName,sha256,apkUrl,notes;
    final long versionCode,size;
    UpdateRelease(JSONObject manifest,String repository,String apkUrl,String notes)throws Exception {
        if(manifest.getInt("schema")!=1||!"com.nicostream.demo".equals(manifest.getString("packageName"))||!APK.equals(manifest.getString("apkName")))throw new IllegalArgumentException("Pacchetto di aggiornamento non compatibile");
        versionCode=manifest.getLong("versionCode");versionName=manifest.getString("versionName");size=manifest.getLong("size");sha256=manifest.getString("sha256").toLowerCase(Locale.ROOT);
        if(versionCode<=0||versionName.isEmpty()||versionName.length()>80||size<=0||size>MAX_APK||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Dati di aggiornamento non validi");
        if(!assetUrl(apkUrl,repository))throw new IllegalArgumentException("Indirizzo aggiornamento non valido");
        this.apkUrl=apkUrl;this.notes=notes==null?"":notes.substring(0,Math.min(3000,notes.length()));
    }
    static boolean repository(String value){return value!=null&&value.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9][A-Za-z0-9._-]{0,99}");}
    static boolean assetUrl(String value,String repository){
        try{URI u=new URI(value);String path=u.getRawPath();return repository(repository)&&"https".equals(u.getScheme())&&"github.com".equalsIgnoreCase(u.getHost())&&u.getUserInfo()==null&&u.getPort()==-1&&u.getRawQuery()==null&&u.getFragment()==null&&path.startsWith("/"+repository+"/releases/download/")&&!path.contains("/../")&&!path.toLowerCase(Locale.ROOT).contains("%2f");}catch(Exception e){return false;}
    }
    static boolean downloadHost(String value){try{URI u=new URI(value);String h=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT);return "https".equals(u.getScheme())&&u.getUserInfo()==null&&u.getPort()==-1&&(h.equals("github.com")||h.equals("api.github.com")||h.endsWith(".githubusercontent.com"));}catch(Exception e){return false;}}
    static String asset(JSONObject release,String name,String repository){
        JSONArray assets=release.optJSONArray("assets");if(assets==null)return "";for(int n=0;n<assets.length();n++){JSONObject a=assets.optJSONObject(n);if(a!=null&&name.equals(a.optString("name"))&&assetUrl(a.optString("browser_download_url"),repository))return a.optString("browser_download_url");}return "";
    }
}
