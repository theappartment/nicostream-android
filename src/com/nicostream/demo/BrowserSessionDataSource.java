package com.nicostream.demo;

import android.net.Uri;
import android.webkit.CookieManager;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import java.io.*;
import java.net.*;
import java.util.*;

/** Device-local HTTP transport sharing this app's normal browser session.
 * Cookies are scoped independently for each request/redirect by CookieManager.
 * No media URLs, cookies or browser credentials are sent to the VPS.
 */
final class BrowserSessionDataSource extends BaseDataSource {
    private final String userAgent;
    private final Map<String,String> browserHeaders;
    private final String mediaHost;
    private HttpURLConnection connection;
    private InputStream input;
    private Uri uri;
    private long remaining=C.LENGTH_UNSET;
    private boolean opened;
    BrowserSessionDataSource(String userAgent,Map<String,String> headers,String mediaHost){
        super(true);this.userAgent=userAgent;this.browserHeaders=new HashMap<>(headers);this.mediaHost=mediaHost;
    }
    static Map<String,String> headersFor(String targetHost,String originalMediaHost,String userAgent,Map<String,String> captured,String scopedCookies){
        Map<String,String> result=new LinkedHashMap<>();
        result.put("User-Agent",userAgent);result.put("Accept-Encoding","identity");
        for(Map.Entry<String,String> h:captured.entrySet()){
            String key=h.getKey().toLowerCase(Locale.ROOT);
            if(key.equals("referer")||key.equals("origin")||key.equals("accept")||key.equals("accept-language")||
              (key.equals("authorization")&&targetHost.equalsIgnoreCase(originalMediaHost)))result.put(h.getKey(),h.getValue());
        }
        // Only cookies selected by CookieManager for this URL are eligible.
        if(scopedCookies!=null&&!scopedCookies.isEmpty())result.put("Cookie",scopedCookies);
        return result;
    }
    @Override public long open(DataSpec spec)throws IOException{
        transferInitializing(spec);
        if(spec.httpMethod!=DataSpec.HTTP_METHOD_GET)throw new IOException("Metodo video non supportato");
        URL target=new URL(spec.uri.toString());
        try{
            for(int redirects=0;redirects<=5;redirects++){
                if(!target.getProtocol().equals("https")||target.getUserInfo()!=null)throw new IOException("Il video richiede un indirizzo HTTPS valido");
                connection=(HttpURLConnection)target.openConnection();
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(25000);
                String cookies=CookieManager.getInstance().getCookie(target.toString());
                for(Map.Entry<String,String> header:headersFor(target.getHost(),mediaHost,userAgent,browserHeaders,cookies).entrySet())connection.setRequestProperty(header.getKey(),header.getValue());
                if(spec.position>0||spec.length!=C.LENGTH_UNSET){
                    String range="bytes="+spec.position+"-"+(spec.length!=C.LENGTH_UNSET?Long.toString(spec.position+spec.length-1):"");
                    connection.setRequestProperty("Range",range);
                }
                int code=connection.getResponseCode();
                for(Map.Entry<String,List<String>> entry:connection.getHeaderFields().entrySet()){
                    if(entry.getKey()!=null&&entry.getKey().equalsIgnoreCase("Set-Cookie"))for(String value:entry.getValue())CookieManager.getInstance().setCookie(target.toString(),value);
                }
                if(code==301||code==302||code==303||code==307||code==308){
                    String location=connection.getHeaderField("Location");
                    if(location==null)throw new IOException("Redirect video non valido");
                    target=new URL(target,location);connection.disconnect();connection=null;continue;
                }
                if(code<200||code>=300)throw new HttpDataSource.InvalidResponseCodeException(code,connection.getResponseMessage(),null,connection.getHeaderFields(),spec,new byte[0]);
                if(code==200&&spec.position>0)throw new IOException("La sorgente non supporta questa posizione video");
                uri=Uri.parse(target.toString());
                remaining=spec.length!=C.LENGTH_UNSET?spec.length:connection.getContentLengthLong();
                if(remaining<0)remaining=C.LENGTH_UNSET;
                input=connection.getInputStream();opened=true;transferStarted(spec);return remaining;
            }
            throw new IOException("Troppi redirect video");
        }catch(IOException|RuntimeException e){
            if(connection!=null){connection.disconnect();connection=null;}
            if(e instanceof IOException)throw (IOException)e;
            throw new IOException("Sessione browser non disponibile",e);
        }
    }
    @Override public int read(byte[] buffer,int offset,int length)throws IOException{
        if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;
        if(input==null)throw new IOException("Sorgente video chiusa");
        int count=input.read(buffer,offset,remaining==C.LENGTH_UNSET?length:(int)Math.min(length,remaining));
        if(count==-1)return C.RESULT_END_OF_INPUT;
        if(remaining!=C.LENGTH_UNSET)remaining-=count;bytesTransferred(count);return count;
    }
    @Override public Uri getUri(){return uri;}
    @Override public Map<String,List<String>> getResponseHeaders(){return connection==null?Collections.emptyMap():connection.getHeaderFields();}
    @Override public void close()throws IOException{
        try{if(input!=null)input.close();}finally{input=null;if(connection!=null){connection.disconnect();connection=null;}if(opened){opened=false;transferEnded();}}
    }
}
