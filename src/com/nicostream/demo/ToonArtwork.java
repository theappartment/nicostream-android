package com.nicostream.demo;
import android.graphics.Rect;
import android.widget.ImageView;
import org.json.JSONObject;
import java.util.concurrent.*;

/** Viewport-triggered artwork enrichment, never a Kodi navigation job. */
final class ToonArtwork {
    final MainActivity app;final ExecutorService worker=Executors.newSingleThreadExecutor();boolean closed;
    ToonArtwork(MainActivity app){this.app=app;}
    void hydrate(JSONObject item){
        if(!item.optString("addonId").contains("toonitalia"))return;
        try{JSONObject saved=new JSONObject(app.prefs.getString("toonArtwork:"+android.net.Uri.encode(item.optString("url")),"{}"));for(String field:new String[]{"art","backdrop"})if(item.optString(field).isEmpty()&&!saved.optString(field).isEmpty())item.put(field,saved.getString(field));}catch(Exception ignored){}
    }
    void bind(JSONObject item,ImageView image,int height){
        if(!item.optString("addonId").contains("toonitalia")||!item.optString("action").equals("title")||!item.optString("art").isEmpty())return;
        String route=item.optString("url"),key="toonArtwork:"+android.net.Uri.encode(route);
        Runnable visible=new Runnable(){public void run(){
            if(closed||!app.alive)return;if(!image.isAttachedToWindow())return;
            Rect rect=new Rect();if(!image.getGlobalVisibleRect(rect)||rect.width()<8||rect.height()<8){image.postDelayed(this,600);return;}
            try{JSONObject saved=new JSONObject(app.prefs.getString(key,"{}"));if(System.currentTimeMillis()-saved.optLong("time")< (saved.optString("art").isEmpty()?900000L:30L*86400000)){apply(item,image,saved,height);return;}}catch(Exception ignored){}
            worker.execute(()->{try{if(closed)return;JSONObject art=app.api("/v1/artwork/toonitalia?url="+android.net.Uri.encode(route),null);art.put("time",System.currentTimeMillis());app.prefs.edit().putString(key,art.toString()).apply();app.main.post(()->apply(item,image,art,height));}catch(Exception ignored){}});
        }};image.postDelayed(visible,100);
    }
    void apply(JSONObject item,ImageView image,JSONObject art,int height){
        if(closed||!image.isAttachedToWindow())return;String url=art.optString("art");if(!url.startsWith("https://"))return;
        try{item.put("art",url);if(!art.optString("backdrop").isEmpty())item.put("backdrop",art.getString("backdrop"));}catch(Exception ignored){}
        ImageView loaded=app.artwork(url,-1,height);
        // Reuse the actual image view so asynchronous bitmap loading remains attached.
        if(image.getParent() instanceof android.view.ViewGroup){android.view.ViewGroup parent=(android.view.ViewGroup)image.getParent();int index=parent.indexOfChild(image);android.view.ViewGroup.LayoutParams params=image.getLayoutParams();parent.removeView(image);parent.addView(loaded,index,params);}
    }
    void close(){closed=true;worker.shutdownNow();}
}
