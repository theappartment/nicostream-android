package com.nicostream.demo;
import android.os.*;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** One visible preview and at most one prepared successor. Never rebuilds the Home. */
final class HeroCarousel {
    static final class Slide {final View view;final HeroPreview preview;Slide(View view,HeroPreview preview){this.view=view;this.preview=preview;}}
    interface Factory {Slide create(JSONObject item,boolean preload,boolean immediate);}
    final FrameLayout host;final Factory factory;final TextView counter;final View previous,next;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Set<String> failedIds=new HashSet<>();JSONArray deferred;
    JSONArray titles=new JSONArray();Slide current,warm;String warmId="";int index,pendingDirection;boolean active=true,disposed,audioEnabled;
    HeroCarousel(FrameLayout host,Factory factory,TextView counter,View previous,View next){this.host=host;this.factory=factory;this.counter=counter;this.previous=previous;this.next=next;previous.setOnClickListener(v->move(-1));next.setOnClickListener(v->move(1));handler.post(tick);}
    String id(JSONObject item){return item==null?"":item.optString("id",item.optString("url"));}
    JSONObject item(){return titles.optJSONObject(index);}
    boolean visible(){Rect rect=new Rect();return active&&host.isAttachedToWindow()&&host.getGlobalVisibleRect(rect)&&rect.height()>host.getHeight()/3;}
    void update(JSONArray updated){
        if(disposed||updated==null||updated.length()==0)return;JSONArray valid=new JSONArray();for(int n=0;n<updated.length();n++)if(!failedIds.contains(id(updated.optJSONObject(n))))valid.put(updated.optJSONObject(n));if(valid.length()==0)return;String old=id(item());int match=-1;for(int n=0;n<valid.length();n++)if(id(valid.optJSONObject(n)).equals(old))match=n;if(current!=null&&match<0&&containsFocus()){deferred=valid;return;}titles=valid;int found=-1;for(int n=0;n<titles.length();n++)if(id(titles.optJSONObject(n)).equals(old)){found=n;break;}
        if(current==null){index=0;showInitial();}
        else if(found<0){index=0;disposeSlides();showInitial();}
        else{index=found;if(current.preview==null&&HeroPreviewResolver.allowed(item().optString("previewURL"))){if(containsFocus()){deferred=valid;return;}disposeSlides();showInitial();}}
        labels();prepareNext(pendingDirection==0?1:pendingDirection);
    }
    void showInitial(){current=factory.create(item(),false,false);if(current.preview!=null)current.preview.setAudioEnabled(audioEnabled);host.addView(current.view,0,new FrameLayout.LayoutParams(-1,-1));}
    void labels(){counter.setText((index+1)+" / "+titles.length());previous.setVisibility(titles.length()>1?View.VISIBLE:View.GONE);next.setVisibility(titles.length()>1?View.VISIBLE:View.GONE);counter.setVisibility(titles.length()>1?View.VISIBLE:View.GONE);}
    void prepareNext(int direction){
        if(!active||titles.length()<2)return;JSONObject target=titles.optJSONObject((index+direction+titles.length())%titles.length());String identity=id(target);
        if(warm!=null&&identity.equals(warmId))return;clearWarm();warmId=identity;warm=factory.create(target,true,true);warm.view.setVisibility(View.INVISIBLE);host.addView(warm.view,0,new FrameLayout.LayoutParams(-1,-1));
    }
    void move(int direction){if(disposed||titles.length()<2)return;pendingDirection=direction;prepareNext(direction);advanceWhenReady();}
    void advanceWhenReady(){
        if(pendingDirection==0||warm==null)return;
        if(warm.preview!=null&&warm.preview.failed){
            String currentId=id(item());failedIds.add(warmId);JSONArray remaining=new JSONArray();for(int n=0;n<titles.length();n++)if(!failedIds.contains(id(titles.optJSONObject(n))))remaining.put(titles.optJSONObject(n));titles=remaining;index=0;for(int n=0;n<titles.length();n++)if(id(titles.optJSONObject(n)).equals(currentId))index=n;clearWarm();pendingDirection=titles.length()>1?1:0;labels();prepareNext(1);return;
        }
        if(warm.preview!=null&&!warm.preview.ready)return;
        index=(index+pendingDirection+titles.length())%titles.length();pendingDirection=0;
        if(current!=null){if(current.preview!=null)current.preview.dispose();host.removeView(current.view);}
        current=warm;warm=null;warmId="";current.view.setVisibility(View.VISIBLE);if(current.preview!=null){current.preview.setAudioEnabled(audioEnabled);current.preview.promote();}labels();prepareNext(1);
    }
    final Runnable tick=new Runnable(){public void run(){
        if(disposed)return;if(deferred!=null&&!containsFocus()){JSONArray update=deferred;deferred=null;update(update);}boolean onScreen=visible();
        if(current!=null&&current.preview!=null)current.preview.setActive(onScreen);
        if(!onScreen){clearWarm();}
        else{prepareNext(pendingDirection==0?1:pendingDirection);if(pendingDirection!=0)advanceWhenReady();
            boolean focused=host.getRootView().findFocus()!=null&&containsFocus();
            if(!focused&&titles.length()>1&&pendingDirection==0&&(current!=null&&current.preview!=null&&(current.preview.failed||current.preview.ended))){pendingDirection=1;advanceWhenReady();}}
        handler.postDelayed(this,400);
    }};
    boolean containsFocus(){View focused=host.getRootView().findFocus();for(android.view.ViewParent parent=focused==null?null:focused.getParent();parent!=null;parent=parent.getParent())if(parent==host.getParent())return true;return false;}
    void clearWarm(){if(warm!=null){if(warm.preview!=null)warm.preview.dispose();host.removeView(warm.view);warm=null;}warmId="";}
    void disposeSlides(){clearWarm();if(current!=null){if(current.preview!=null)current.preview.dispose();host.removeView(current.view);current=null;}}
    void setAudioEnabled(boolean enabled){audioEnabled=enabled;if(current!=null&&current.preview!=null)current.preview.setAudioEnabled(enabled);}
    void setActive(boolean value){active=value;if(current!=null&&current.preview!=null)current.preview.setActive(value);if(!value)clearWarm();}
    void dispose(){disposed=true;handler.removeCallbacksAndMessages(null);disposeSlides();}
}
