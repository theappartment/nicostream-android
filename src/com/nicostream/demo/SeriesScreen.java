package com.nicostream.demo;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** TV adaptation of iPad SeasonCollectionLayout / EpisodeCollectionLayout (40/60 columns). */
final class SeriesScreen {
    final MainActivity app;final LinearLayout view;final TextView back;
    final List<View> leftControls=new ArrayList<>(),rows=new ArrayList<>(),seasonTabs=new ArrayList<>();
    View lastLeft,lastRight,seasonFilter;
    final String fallbackPoster;
    HeroPreview preview;boolean closed;FrameLayout trailerFrame;TextureView trailerSurface;
    SeriesScreen(MainActivity app,JSONObject media,JSONArray seasons,JSONArray entries,boolean episodes){
        this.app=app;int entryCount=0;for(int n=0;n<entries.length();n++){JSONObject entry=entries.optJSONObject(n);if(entry!=null&&!entry.optBoolean("isNotice")&&(!episodes||!entry.optBoolean("isFolder")))entryCount++;}view=app.col();LinearLayout toolbar=new LinearLayout(app);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        back=app.button("‹ Indietro",()->{if(!app.history.isEmpty())app.previous();else{app.section="Scopri";app.home();}});back.setFocusableInTouchMode(true);back.setTextSize(14);back.setPadding(app.dp(14),app.dp(8),app.dp(14),app.dp(8));toolbar.addView(back);TextView context=app.text(episodes?"EPISODI":"STAGIONI",11,app.MUTED);context.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);toolbar.addView(context,new LinearLayout.LayoutParams(0,-2,1));view.addView(toolbar);app.space(view,12);
        LinearLayout columns=new LinearLayout(app);columns.setClipChildren(false);view.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout infoScroll=new FrameLayout(app);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-1,.4f);ip.rightMargin=app.dp(20);columns.addView(infoScroll,ip);
        LinearLayout info=app.col();info.setPadding(app.dp(14),app.dp(14),app.dp(14),app.dp(14));GradientDrawable panel=app.bg(Color.rgb(13,19,26),10);panel.setStroke(app.dp(1),Color.argb(25,255,255,255));info.setBackground(panel);infoScroll.addView(info);
        String poster=media.optString("art");if(poster.isEmpty())poster=media.optString("thumb");fallbackPoster=poster;
        int previewWidth=Math.max(240,(int)((app.getResources().getDisplayMetrics().widthPixels/app.getResources().getDisplayMetrics().density-72)*.4f)-28),previewHeight=Math.min(150,Math.round(previewWidth*9f/16f));
        trailerFrame=new FrameLayout(app);trailerFrame.setBackground(app.bg(app.PANEL,8));trailerFrame.setClipToOutline(true);String backdrop=media.optString("backdrop");if(backdrop.isEmpty())backdrop=media.optString("thumb",poster);if(backdrop.isEmpty())backdrop=poster;trailerFrame.addView(app.artwork(backdrop,-1,previewHeight),new FrameLayout.LayoutParams(-1,-1));trailerSurface=new TextureView(app);trailerSurface.setAlpha(0);trailerSurface.setOpaque(false);trailerSurface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);trailerFrame.addView(trailerSurface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));info.addView(trailerFrame,new LinearLayout.LayoutParams(-1,app.dp(previewHeight)));app.space(info,0);
        LinearLayout identity=new LinearLayout(app);identity.setGravity(Gravity.TOP);FrameLayout coverFrame=new FrameLayout(app);coverFrame.setPadding(app.dp(3),app.dp(3),app.dp(3),app.dp(3));coverFrame.setBackground(app.bg(Color.rgb(13,19,26),9));coverFrame.addView(app.artwork(poster,72,108),new FrameLayout.LayoutParams(app.dp(72),app.dp(108)));identity.addView(coverFrame,new LinearLayout.LayoutParams(app.dp(78),app.dp(114)));LinearLayout details=app.col();details.setPadding(app.dp(10),app.dp(30),0,0);identity.addView(details,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams identityParams=new LinearLayout.LayoutParams(-1,-2);identityParams.topMargin=-app.dp(28);info.addView(identity,identityParams);
        TextView title=app.title(media.optString("title"),23);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(title);String year=media.optString("year"),summary="Serie TV"+(year.isEmpty()?"":" · "+year);
        if(episodes){int season=entries.length()>0?entries.optJSONObject(0).optInt("seasonNumber"):0;summary+=(season>0?" · Stagione "+season:"")+" · "+entryCount+(entryCount==1?" episodio":" episodi");}else summary+=" · "+seasons.length()+(seasons.length()==1?" stagione":" stagioni");
        TextView metadata=app.text(summary,11,app.MINT);metadata.setMaxLines(2);details.addView(metadata);
        if(!media.optString("description").isEmpty()){app.space(info,6);TextView plot=app.text(media.optString("description"),12,app.MUTED);plot.setMaxLines(3);plot.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(plot);}app.space(info,8);
        LinearLayout actions=new LinearLayout(app);
        JSONObject continuing=app.continuingSeries(media);if(continuing!=null){JSONObject source=continuing.optJSONObject("source");TextView resume=control("▶ Riprendi · S"+source.optInt("seasonNumber")+" E"+source.optInt("episodeNumber"),()->app.resumeSource(source,continuing.optLong("position")));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.rightMargin=app.dp(8);actions.addView(resume,rp);}
        if(media.optLong("scTitleId")>0){TextView favorite=control(app.isFavorite(media)?"✓ La mia lista":"＋ La mia lista",()->{app.toggleFavorite(media);app.home();});actions.addView(favorite,new LinearLayout.LayoutParams(0,-2,1));}
        LinearLayout right=app.col();columns.addView(right,new LinearLayout.LayoutParams(0,-1,.6f));if(actions.getChildCount()>0){toolbar.removeView(context);View toolbarGap=new View(app);toolbar.addView(toolbarGap,new LinearLayout.LayoutParams(0,1,1));toolbar.addView(actions,new LinearLayout.LayoutParams(Math.round((app.getResources().getDisplayMetrics().widthPixels-app.dp(72))*.6f),-2));}int season=entries.length()>0?entries.optJSONObject(0).optInt("seasonNumber"):0;
        if(!episodes){right.addView(app.title("Stagioni",22));app.space(right,6);}
        if(episodes&&seasons.length()>0){
            HorizontalScrollView tabsScroll=new HorizontalScrollView(app);tabsScroll.setHorizontalScrollBarEnabled(false);tabsScroll.setClipToPadding(false);LinearLayout tabs=new LinearLayout(app);tabs.setPadding(app.dp(3),app.dp(4),app.dp(3),app.dp(8));tabsScroll.addView(tabs);
            for(int n=0;n<seasons.length();n++){JSONObject target=seasons.optJSONObject(n);if(target==null)continue;boolean active=target.optInt("seasonNumber")==season;TextView tab=app.button(target.optString("title"),()->{if(!active)app.scCatalog(target);});tab.setTextSize(13);tab.setPadding(app.dp(14),app.dp(9),app.dp(14),app.dp(9));tab.setTextColor(active?app.MINT:app.MUTED);tab.setContentDescription(target.optString("title")+(active?", selezionata":""));tab.setSelected(active);tab.setBackground(app.focus());LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.rightMargin=app.dp(8);tabs.addView(tab,tp);seasonTabs.add(tab);if(active||seasonFilter==null)seasonFilter=tab;}
            right.addView(tabsScroll,new LinearLayout.LayoutParams(-1,-2));app.space(right,4);
        }
        CenteredScrollView scroll=new CenteredScrollView(app);LinearLayout list=app.col();list.setPadding(app.dp(5),0,app.dp(5),0);scroll.addView(list);right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(int n=0;n<entries.length();n++){JSONObject item=entries.optJSONObject(n);if(item==null)continue;View row=episodes?episode(item):season(item);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,episodes?app.dp(105):app.dp(77));rp.bottomMargin=app.dp(10);list.addView(row,rp);rows.add(row);}
        back.post(()->{if(back.isAttachedToWindow())back.requestFocus();});
        loadPreview(media);

    }
    void loadPreview(JSONObject media){
        if(HeroPreviewResolver.allowed(media.optString("previewURL"))){startPreview(media);return;}
        if(media.optLong("scTitleId")<=0||media.optString("url").isEmpty())return;
        app.worker.execute(()->{try{JSONObject props=app.scPage(media.optString("url"),android.webkit.WebSettings.getDefaultUserAgent(app));JSONObject record=props.getJSONObject("title");if(record.optLong("id")!=media.optLong("scTitleId"))return;JSONObject ready=StreamingCommunityCatalog.titleEntry(record,props,props.optString("_sourceOrigin",app.providerOrigin()));app.main.post(()->{if(!closed&&app.seriesScreen==this)startPreview(ready);});}catch(Exception ignored){}});
    }
    void startPreview(JSONObject media){if(closed||!HeroPreviewResolver.allowed(media.optString("previewURL")))return;preview=new HeroPreview(app,trailerFrame,trailerSurface,app.worker,media.optString("previewURL"),media.optString("previewReferer",app.providerOrigin()+"/it"),android.webkit.WebSettings.getDefaultUserAgent(app),false,false,true);}
    void setActive(boolean active){if(preview!=null)preview.setActive(active);}
    void close(){closed=true;if(preview!=null){preview.dispose();preview=null;}}
    TextView control(String label,Runnable action){TextView button=app.button(label,action);button.setTextSize(11);button.setPadding(app.dp(12),app.dp(10),app.dp(12),app.dp(10));leftControls.add(button);return button;}
    View season(JSONObject item){
        LinearLayout row=new LinearLayout(app);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(app.dp(12),app.dp(10),app.dp(14),app.dp(10));app.focusCard(row,item);row.setContentDescription(item.optString("title")+" · "+item.optString("tag"));
        TextView number=app.title(String.format(Locale.ROOT,"%02d",item.optInt("seasonNumber")),20);number.setTextColor(app.MINT);number.setGravity(Gravity.CENTER);number.setBackground(app.bg(Color.argb(22,255,255,255),8));row.addView(number,new LinearLayout.LayoutParams(app.dp(44),app.dp(44)));
        LinearLayout copy=app.col();copy.setPadding(app.dp(14),0,app.dp(12),0);copy.addView(app.title(item.optString("title"),17));copy.addView(app.text(item.optString("tag"),11,app.MUTED));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));row.addView(app.text("›",24,app.MUTED));return row;
    }
    View episode(JSONObject item){
        if(item.optBoolean("isNotice")||item.optBoolean("isFolder"))return app.button(item.optString("title"),()->app.openItem(item));
        JSONObject record=PlaybackHistory.find(app.playbackHistory(),item);boolean completed=record!=null&&record.optBoolean("completed");long resume=app.resumePosition(item),duration=record==null?0:record.optLong("duration");int season=item.optInt("seasonNumber"),episode=item.optInt("episodeNumber");String name=item.optString("episodeName",item.optString("title"));
        FrameLayout outer=new FrameLayout(app);app.focusCard(outer,item);outer.setContentDescription((season>0?season+"x":"")+(episode>0?String.format(Locale.ROOT,"%02d",episode)+" · ":"")+name+(completed?" · Guardato":resume>0?" · Riprendi da "+app.time(resume):""));
        LinearLayout content=new LinearLayout(app);content.setGravity(Gravity.CENTER_VERTICAL);content.setPadding(app.dp(12),app.dp(10),app.dp(12),app.dp(10));
        TextView code=app.title(episode>0?(season>0?season+"x":"")+String.format(Locale.ROOT,"%02d",episode):"▶",17);code.setTextColor(completed?app.MINT:app.WHITE);code.setMaxLines(1);content.addView(code,new LinearLayout.LayoutParams(app.dp(48),-2));String image=item.optString("thumb");if(image.isEmpty())image=item.optString("art");if(image.isEmpty())image=fallbackPoster;content.addView(app.artwork(image,108,61),new LinearLayout.LayoutParams(app.dp(108),app.dp(61)));
        LinearLayout copy=app.col();copy.setPadding(app.dp(8),0,app.dp(10),0);TextView title=app.title(name,15);title.setLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);String tag=item.optString("tag");if(tag.equals("0 MIN"))tag="";String status=completed?"✓ Guardato":resume>0?"Riprendi da "+app.time(resume):"Da guardare";TextView metadata=app.text((tag.isEmpty()?"":tag+" · ")+status,11,completed||resume>0?app.MINT:app.MUTED);metadata.setSingleLine(true);metadata.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(metadata);content.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView play=app.text(completed?"✓":"▶",13,app.BG);play.setGravity(Gravity.CENTER);play.setBackground(app.bg(Color.rgb(232,239,240),18));content.addView(play,new LinearLayout.LayoutParams(app.dp(34),app.dp(34)));outer.addView(content,new FrameLayout.LayoutParams(-1,-1));
        if(completed||resume>0){View track=new View(app);track.setBackgroundColor(Color.argb(30,255,255,255));FrameLayout.LayoutParams trackParams=new FrameLayout.LayoutParams(-1,app.dp(2),Gravity.BOTTOM);trackParams.leftMargin=app.dp(10);trackParams.rightMargin=app.dp(10);outer.addView(track,trackParams);float fraction=completed?1:duration>0?Math.min(1,(float)resume/duration):0;View fill=new View(app);fill.setBackgroundColor(app.MINT);outer.addView(fill,new FrameLayout.LayoutParams(0,app.dp(2),Gravity.BOTTOM));outer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{FrameLayout.LayoutParams fp=(FrameLayout.LayoutParams)fill.getLayoutParams();int width=Math.round(Math.max(0,r-l-app.dp(20))*fraction);if(fp.width!=width){fp.width=width;fp.leftMargin=app.dp(10);fill.setLayoutParams(fp);}});}
        return outer;
    }
    boolean within(View parent,View focused){if(focused==parent)return true;for(android.view.ViewParent node=focused==null?null:focused.getParent();node!=null;node=node.getParent())if(node==parent)return true;return false;}
    boolean focus(View target){if(target!=null&&target.isAttachedToWindow()&&target.isShown())target.requestFocus();return true;}
    boolean move(View focused,int key){
        boolean horizontal=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;if(!horizontal&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
        if(within(app.nav,focused)){if(key==KeyEvent.KEYCODE_DPAD_DOWN)return focus(lastRight!=null&&lastRight.isAttachedToWindow()?lastRight:leftControls.isEmpty()?(seasonFilter!=null?seasonFilter:rows.isEmpty()?back:rows.get(0)):leftControls.get(0));return false;}
        if(focused==back){if(key==KeyEvent.KEYCODE_DPAD_UP)return focus(app.nav.getChildAt(3));if(key==KeyEvent.KEYCODE_DPAD_DOWN)return focus(seasonFilter!=null?seasonFilter:rows.isEmpty()?back:rows.get(0));if(key==KeyEvent.KEYCODE_DPAD_RIGHT)return focus(leftControls.isEmpty()?rows.isEmpty()?back:rows.get(0):leftControls.get(0));return true;}
        int tab=seasonTabs.indexOf(focused);if(tab>=0){seasonFilter=focused;if(key==KeyEvent.KEYCODE_DPAD_UP)return focus(leftControls.isEmpty()?back:leftControls.get(0));if(key==KeyEvent.KEYCODE_DPAD_DOWN)return focus(rows.isEmpty()?focused:rows.get(0));if(key==KeyEvent.KEYCODE_DPAD_LEFT&&tab==0)return true;int next=tab+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);return focus(next<0||next>=seasonTabs.size()?focused:seasonTabs.get(next));}
        int left=leftControls.indexOf(focused);if(left>=0){lastLeft=focused;if(key==KeyEvent.KEYCODE_DPAD_UP)return focus(back);if(key==KeyEvent.KEYCODE_DPAD_DOWN)return focus(seasonFilter!=null?seasonFilter:rows.isEmpty()?focused:rows.get(0));int next=left+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);return focus(next<0||next>=leftControls.size()?focused:leftControls.get(next));}
        int row=rows.indexOf(focused);if(row>=0){lastRight=focused;if(key==KeyEvent.KEYCODE_DPAD_LEFT)return true;if(key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;int next=row+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);return focus(next<0?seasonFilter!=null?seasonFilter:back:next>=rows.size()?focused:rows.get(next));}return false;
    }
}
