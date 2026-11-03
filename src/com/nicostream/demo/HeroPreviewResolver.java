package com.nicostream.demo;

import java.net.*;
import java.util.regex.*;
import java.io.IOException;

/** Port of the existing iOS StreamingCommunityPreviewResolver for the provider's normal preview page. */
final class HeroPreviewResolver {
    static boolean allowed(String address){
        try{URI u=new URI(address);String host=u.getHost();return "https".equals(u.getScheme())&&u.getUserInfo()==null&&u.getPath()!=null&&u.getPath().startsWith("/embed/")&&("vixcloud.cc".equals(host)||"vixcloud.co".equals(host)||"vixsrc.to".equals(host));}catch(Exception e){return false;}
    }
    static String capture(String pattern,String value){Matcher m=Pattern.compile(pattern).matcher(value);return m.find()?m.group(1):null;}
    static String playlist(String html,String embed)throws Exception {
        int marker=html.indexOf("window.masterPlaylist");if(marker<0)throw new IOException("Anteprima non disponibile");
        String block=capture("=\\s*\\{([\\s\\S]*?)\\}\\s*(?:;|window\\.|</script>)",html.substring(marker+"window.masterPlaylist".length()));
        if(block==null)throw new IOException("Anteprima non disponibile");
        String address=capture("\\burl\\s*:\\s*['\"]([^'\"]+)['\"]",block);if(address==null)throw new IOException("Anteprima non disponibile");
        URI uri=new URI(address);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IOException("Anteprima non valida");
        StringBuilder query=new StringBuilder(uri.getRawQuery()==null?"":uri.getRawQuery());String params=capture("\\bparams\\s*:\\s*\\{([^}]*)\\}",block);
        if(params!=null){Matcher pairs=Pattern.compile("['\"]([^'\"]+)['\"]\\s*:\\s*['\"]([^'\"]*)['\"]").matcher(params);int count=0;while(pairs.find()){if(++count>32)throw new IOException("Anteprima non valida");if(!pairs.group(2).isEmpty())append(query,pairs.group(1),pairs.group(2));}}
        URI embedUri=new URI(embed);String eq=embedUri.getRawQuery();if(eq!=null)for(String pair:eq.split("&")){String[] parts=pair.split("=",2);if(parts.length==2&&URLDecoder.decode(parts[0],"UTF-8").equals("canPlayFHD")&&URLDecoder.decode(parts[1],"UTF-8").equals("1")){append(query,"h","1");break;}}
        String base=address.split("\\?",2)[0];return base+(query.length()>0?"?"+query:"");
    }
    static void append(StringBuilder query,String key,String value)throws Exception {if(query.length()>0)query.append('&');query.append(URLEncoder.encode(key,"UTF-8")).append('=').append(URLEncoder.encode(value,"UTF-8"));}
}
