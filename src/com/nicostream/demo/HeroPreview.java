package com.nicostream.demo;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Silent, disposable native background video. Separate from the main player and its history. */
final class HeroPreview {
    final Context context;final View hero;final TextureView surface;final ExecutorService executor;final String embed,referer,ua;
    final Handler handler=new Handler(Looper.getMainLooper());
    float videoAspect,letterboxZoom=1f;
    ExoPlayer player;boolean active=true,disposed=false,attempted=false,scheduled=false,firstFrame=false,progressLogged=false;long generation=0;
    HeroPreview(Context context,View hero,TextureView surface,ExecutorService executor,String embed,String referer,String ua){this.context=context;this.hero=hero;this.surface=surface;this.executor=executor;this.embed=embed;this.referer=referer;this.ua=ua;surface.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB)->updateCover());handler.post(monitor);}
    final Runnable monitor=new Runnable(){public void run(){
        if(disposed)return;Rect visible=new Rect();boolean onScreen=active&&hero.isAttachedToWindow()&&hero.getGlobalVisibleRect(visible)&&visible.height()>0;
        if(!onScreen){if(attempted||scheduled)stop(true);}else if(!attempted&&!scheduled){scheduled=true;final long request=generation;handler.postDelayed(()->{if(disposed||request!=generation||!active)return;scheduled=false;attempted=true;resolve(request);},3000);}
        if(player!=null&&firstFrame&&player.isPlaying()&&player.getCurrentPosition()>1500&&!progressLogged){progressLogged=true;android.util.Log.i("NicoStreamPreview","PROGRESS_MS="+player.getCurrentPosition()+" MUTED="+(player.getVolume()==0));}
        handler.postDelayed(this,400);
    }};
    void resolve(long request){
        android.util.Log.i("NicoStreamPreview","LOADING");executor.execute(()->{try{
            BrowserSessionDataSource data=new BrowserSessionDataSource(ua,Collections.singletonMap("Referer",referer),Uri.parse(embed).getHost());String html;
            try{data.open(new DataSpec(Uri.parse(embed)));ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=data.read(b,0,b.length))!=-1){out.write(b,0,n);if(out.size()>2097152)throw new IOException("Anteprima troppo grande");}html=out.toString("UTF-8");}finally{data.close();}
            String playlist=HeroPreviewResolver.playlist(html,embed);handler.post(()->{if(!disposed&&active&&generation==request)play(playlist,request);});
        }catch(Exception e){handler.post(()->{if(!disposed&&generation==request){android.util.Log.i("NicoStreamPreview","UNAVAILABLE");stop(false);}});}});
    }
    void play(String url,long request){
        Map<String,String> headers=Collections.singletonMap("Referer",embed);String host=Uri.parse(url).getHost();
        letterboxZoom=1f;player=new ExoPlayer.Builder(context).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(context,()->new BrowserSessionDataSource(ua,headers,host)))).build();final ExoPlayer current=player;
        current.setVolume(0);current.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),false);
        current.setTrackSelectionParameters(current.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).build());current.setVideoTextureView(surface);
        current.addListener(new Player.Listener(){
            public void onRenderedFirstFrame(){if(player!=current)return;firstFrame=true;updateCover();handler.postDelayed(()->{if(player==current)cropLetterbox();},350);handler.postDelayed(()->{if(player==current)cropLetterbox();},1200);surface.animate().alpha(1).setDuration(300).start();android.util.Log.i("NicoStreamPreview","FIRST_FRAME MUTED=true");}
            public void onVideoSizeChanged(VideoSize size){if(player!=current||size.height<=0||size.width<=0)return;videoAspect=(float)size.width*size.pixelWidthHeightRatio/size.height;surface.post(()->{if(player==current)updateCover();});}
            public void onPlaybackStateChanged(int state){if(player==current&&state==Player.STATE_ENDED)stop(false);}
            public void onPlayerError(PlaybackException error){if(player==current){android.util.Log.i("NicoStreamPreview","UNAVAILABLE code="+error.getErrorCodeName());stop(false);}}
        });
        current.setMediaItem(new MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build());current.prepare();current.play();
        handler.postDelayed(()->{if(player==current&&!firstFrame)stop(false);},15000);
        handler.postDelayed(()->{if(player==current)stop(false);},60000);
    }
    boolean blackRow(android.graphics.Bitmap frame,int y){
        for(int x=0;x<frame.getWidth();x++){int color=frame.getPixel(x,y);if(android.graphics.Color.red(color)>14||android.graphics.Color.green(color)>14||android.graphics.Color.blue(color)>14)return false;}return true;
    }
    void cropLetterbox(){
        if(disposed||!firstFrame||surface.getWidth()==0||surface.getHeight()==0)return;
        // Some trailers encode cinema bars into the picture itself. Detect only
        // symmetric, entirely black margins; don't treat a dark scene as a bar.
        android.graphics.Bitmap frame=surface.getBitmap(160,Math.max(32,Math.round(160f*surface.getHeight()/surface.getWidth())));if(frame==null)return;
        try{
            int height=frame.getHeight(),limit=height/5,top=0,bottom=0;
            while(top<limit&&blackRow(frame,top))top++;
            while(bottom<limit&&blackRow(frame,height-1-bottom))bottom++;
            if(top<2||bottom<2||top>=limit||bottom>=limit||Math.abs(top-bottom)>2)return;
            float zoom=(float)height/(height-top-bottom);if(zoom>letterboxZoom){letterboxZoom=Math.min(1.55f,zoom);updateCover();}
        }finally{frame.recycle();}
    }
    void updateCover(){
        if(videoAspect<=0||surface.getWidth()<=0||surface.getHeight()<=0)return;
        // TextureView initially stretches the frame; expand one axis to restore its
        // aspect ratio and center-crop to the whole hero viewport, also after layout.
        float viewport=(float)surface.getWidth()/surface.getHeight();Matrix transform=new Matrix();
        transform.setScale(letterboxZoom*Math.max(1f,videoAspect/viewport),letterboxZoom*Math.max(1f,viewport/videoAspect),surface.getWidth()/2f,surface.getHeight()/2f);surface.setTransform(transform);
    }
    void stop(boolean reset){generation++;scheduled=false;firstFrame=false;progressLogged=false;surface.animate().cancel();surface.setAlpha(0);if(player!=null){ExoPlayer previous=player;player=null;previous.release();android.util.Log.i("NicoStreamPreview","STOPPED");}if(reset)attempted=false;}
    void setActive(boolean value){active=value;if(!value)stop(true);}
    void dispose(){disposed=true;stop(false);handler.removeCallbacksAndMessages(null);}
}
