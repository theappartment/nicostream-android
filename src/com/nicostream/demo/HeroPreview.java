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

/** Disposable native preview; Home stays silent, series detail can opt into audio. */
final class HeroPreview {
    final Context context;final View hero;final TextureView surface;final ExecutorService executor;final String embed,referer,ua;
    final Handler handler=new Handler(Looper.getMainLooper());
    final boolean audioEnabled;
    float videoAspect;
    boolean preload,immediate,failed,ended,ready;
    ExoPlayer player;boolean active=true,disposed=false,attempted=false,scheduled=false,firstFrame=false,progressLogged=false;long generation=0;
    HeroPreview(Context context,View hero,TextureView surface,ExecutorService executor,String embed,String referer,String ua,boolean preload,boolean immediate){this(context,hero,surface,executor,embed,referer,ua,preload,immediate,false);}
    HeroPreview(Context context,View hero,TextureView surface,ExecutorService executor,String embed,String referer,String ua,boolean preload,boolean immediate,boolean audioEnabled){this.audioEnabled=audioEnabled;this.context=context;this.hero=hero;this.surface=surface;this.executor=executor;this.embed=embed;this.referer=referer;this.ua=ua;this.preload=preload;this.immediate=immediate;surface.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB)->updateCover());handler.post(monitor);}
    void promote(){preload=false;immediate=true;if(player!=null){if(firstFrame)surface.setAlpha(1);player.play();}android.util.Log.i("NicoStreamPreview","PROMOTED prepared="+ready);}
    final Runnable monitor=new Runnable(){public void run(){
        if(disposed)return;Rect visible=new Rect();boolean onScreen=active&&hero.isAttachedToWindow()&&(preload||hero.getGlobalVisibleRect(visible)&&visible.height()>0);
        if(!onScreen){if(attempted||scheduled)stop(true);}else if(!attempted&&!scheduled){scheduled=true;final long request=generation;handler.postDelayed(()->{if(disposed||request!=generation||!active)return;scheduled=false;attempted=true;failed=false;ended=false;resolve(request);},immediate||preload?0:1200);}
        if(player!=null&&firstFrame&&player.isPlaying()&&player.getCurrentPosition()>1500&&!progressLogged){progressLogged=true;android.util.Log.i("NicoStreamPreview","PROGRESS_MS="+player.getCurrentPosition()+" MUTED="+(player.getVolume()==0));}
        handler.postDelayed(this,400);
    }};
    void resolve(long request){
        android.util.Log.i("NicoStreamPreview","LOADING");executor.execute(()->{try{
            BrowserSessionDataSource data=new BrowserSessionDataSource(ua,Collections.singletonMap("Referer",referer),Uri.parse(embed).getHost());String html;
            try{data.open(new DataSpec(Uri.parse(embed)));ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=data.read(b,0,b.length))!=-1){out.write(b,0,n);if(out.size()>2097152)throw new IOException("Anteprima troppo grande");}html=out.toString("UTF-8");}finally{data.close();}
            String playlist=HeroPreviewResolver.playlist(html,embed);handler.post(()->{if(!disposed&&active&&generation==request)play(playlist,request);});
        }catch(Exception e){handler.post(()->{if(!disposed&&generation==request){android.util.Log.i("NicoStreamPreview","UNAVAILABLE");failed=true;stop(false);}});}});
    }
    void play(String url,long request){
        Map<String,String> headers=Collections.singletonMap("Referer",embed);String host=Uri.parse(url).getHost();
        player=new ExoPlayer.Builder(context).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(context,()->new BrowserSessionDataSource(ua,headers,host)))).build();final ExoPlayer current=player;
        current.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);current.setVolume(audioEnabled?1f:0f);current.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),audioEnabled);
        current.setTrackSelectionParameters(current.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,!audioEnabled).setMaxVideoSize(1280,720).build());current.setVideoTextureView(surface);
        current.addListener(new Player.Listener(){
            public void onRenderedFirstFrame(){if(player!=current)return;firstFrame=true;updateCover();surface.animate().alpha(1).setDuration(immediate?0:250).start();android.util.Log.i("NicoStreamPreview","FIRST_FRAME MUTED="+(current.getVolume()==0));}
            public void onVideoSizeChanged(VideoSize size){if(player!=current||size.height<=0||size.width<=0)return;videoAspect=(float)size.width*size.pixelWidthHeightRatio/size.height;surface.post(()->{if(player==current)updateCover();});}
            public void onPlaybackStateChanged(int state){if(player==current&&state==Player.STATE_READY){ready=true;android.util.Log.i("NicoStreamPreview","READY preload="+preload);}if(player==current&&state==Player.STATE_ENDED){ended=true;android.util.Log.i("NicoStreamPreview","ENDED durationMs="+current.getDuration());}}
            public void onPlayerError(PlaybackException error){if(player==current){android.util.Log.i("NicoStreamPreview","UNAVAILABLE code="+error.getErrorCodeName());failed=true;stop(false);}}
        });
        current.setMediaItem(new MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build());current.prepare();if(!preload)current.play();
        handler.postDelayed(()->{if(player==current&&!firstFrame&&(!preload||!ready)){failed=true;stop(false);}},15000);
    }
    void updateCover(){
        if(videoAspect<=0||!(surface.getParent() instanceof android.view.View))return;
        android.view.View viewport=(android.view.View)surface.getParent();if(viewport.getWidth()<=0||viewport.getHeight()<=0)return;
        // Give the video its natural aspect ratio, then physically enlarge and
        // center it inside the clipped frame. This avoids decoder/texture scaling
        // introducing bars when the hero is much wider than the source video.
        float height=Math.max(viewport.getHeight(),viewport.getWidth()/videoAspect);
        // Fixed 150% zoom requested for previews with encoded cinema bars.
        surface.setScaleX(1.5f);surface.setScaleY(1.5f);
        int targetWidth=(int)Math.ceil(height*videoAspect),targetHeight=(int)Math.ceil(height);
        android.view.ViewGroup.LayoutParams existing=surface.getLayoutParams();
        if(existing.width!=targetWidth||existing.height!=targetHeight){surface.setTransform(new Matrix());surface.setLayoutParams(new android.widget.FrameLayout.LayoutParams(targetWidth,targetHeight,android.view.Gravity.CENTER));}
    }
    void stop(boolean reset){generation++;scheduled=false;firstFrame=false;ready=false;progressLogged=false;surface.animate().cancel();surface.setAlpha(0);if(player!=null){ExoPlayer previous=player;player=null;previous.release();android.util.Log.i("NicoStreamPreview","STOPPED");}if(reset)attempted=false;}
    void setActive(boolean value){if(active==value)return;active=value;if(!value)stop(true);}
    void dispose(){disposed=true;stop(false);handler.removeCallbacksAndMessages(null);}
}
