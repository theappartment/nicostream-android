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
    SeriesScreen(MainActivity app,JSONObject media,JSONArray seasons,JSONArray entries,boolean episodes){
        this.app=app;int entryCount=0;for(int n=0;n<entries.length();n++){JSONObject entry=entries.optJSONObject(n);if(entry!=null&&!entry.optBoolean("isNotice")&&(!episodes||!entry.optBoolean("isFolder")))entryCount++;}view=app.col();LinearLayout toolbar=new LinearLayout(app);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        back=app.button("‹ Indietro",()->{if(!app.history.isEmpty())app.previous();else{app.section="Scopri";app.home();}});back.setFocusableInTouchMode(true);back.setTextSize(14);back.setPadding(app.dp(14),app.dp(8),app.dp(14),app.dp(8));toolbar.addView(back);TextView context=app.text(episodes?"EPISODI":"STAGIONI",11,app.MUTED);context.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);toolbar.addView(context,new LinearLayout.LayoutParams(0,-2,1));view.addView(toolbar);app.space(view,12);
        LinearLayout columns=new LinearLayout(app);columns.setClipChildren(false);view.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        CenteredScrollView infoScroll=new CenteredScrollView(app);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-1,.4f);ip.rightMargin=app.dp(20);columns.addView(infoScroll,ip);
        LinearLayout info=app.col();info.setPadding(app.dp(14),app.dp(14),app.dp(14),app.dp(14));GradientDrawable panel=app.bg(Color.rgb(13,19,26),10);panel.setStroke(app.dp(1),Color.argb(25,255,255,255));info.setBackground(panel);infoScroll.addView(info);
        String poster=media.optString("art");if(poster.isEmpty())poster=media.optString("thumb");fallbackPoster=poster;FrameLayout posterFrame=new FrameLayout(app);ImageView cover=app.artwork(poster,142,212);posterFrame.addView(cover,new FrameLayout.LayoutParams(app.dp(142),app.dp(212),Gravity.CENTER));info.addView(posterFrame,new LinearLayout.LayoutParams(-1,app.dp(212)));app.space(info,12);
        TextView title=app.title(media.optString("title"),26);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(title);String year=media.optString("year"),summary="Serie TV"+(year.isEmpty()?"":" · "+year);
        if(episodes){int season=entries.length()>0?entries.optJSONObject(0).optInt("seasonNumber"):0;summary+=(season>0?" · Stagione "+season:"")+" · "+entryCount+(entryCount==1?" episodio":" episodi");}else summary+=" · "+seasons.length()+(seasons.length()==1?" stagione":" stagioni");
        TextView metadata=app.text(summary,11,app.MINT);metadata.setMaxLines(2);info.addView(metadata);
        if(!media.optString("description").isEmpty()){app.space(info,6);TextView plot=app.text(media.optString("description"),12,app.MUTED);plot.setMaxLines(5);plot.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(plot);}app.space(info,12);
        JSONObject continuing=app.continuingSeries(media);if(continuing!=null){JSONObject source=continuing.optJSONObject("source");TextView resume=control("▶ Riprendi · S"+source.optInt("seasonNumber")+" E"+source.optInt("episodeNumber"),()->app.resumeSource(source,continuing.optLong("position")));info.addView(resume,new LinearLayout.LayoutParams(-1,-2));app.space(info,8);}
        if(media.optLong("scTitleId")>0){TextView favorite=control(app.isFavorite(media)?"✓ La mia lista":"＋ La mia lista",()->{app.toggleFavorite(media);app.home();});info.addView(favorite,new LinearLayout.LayoutParams(-1,-2));app.space(info,8);}
        LinearLayout right=app.col();columns.addView(right,new LinearLayout.LayoutParams(0,-1,.6f));LinearLayout heading=new LinearLayout(app);heading.setGravity(Gravity.CENTER_VERTICAL);int season=entries.length()>0?entries.optJSONObject(0).optInt("seasonNumber"):0;
        int total=entryCount;
        heading.addView(app.title(episodes?"Episodi":"Stagioni",22),new LinearLayout.LayoutParams(0,-2,1));
        TextView count=app.text(String.valueOf(total),11,app.BG);count.setGravity(Gravity.CENTER);count.setBackground(app.bg(app.MINT,16));count.setPadding(app.dp(10),app.dp(4),app.dp(10),app.dp(4));LinearLayout.LayoutParams countParams=new LinearLayout.LayoutParams(-2,-2);countParams.leftMargin=app.dp(10);heading.addView(count,countParams);right.addView(heading);right.addView(app.text(episodes?"OK per guardare · Il punto di ripresa è salvato":"Scegli una stagione per vedere gli episodi",11,app.MUTED));app.space(right,6);
        if(episodes&&seasons.length()>0){
            HorizontalScrollView tabsScroll=new HorizontalScrollView(app);tabsScroll.setHorizontalScrollBarEnabled(false);tabsScroll.setClipToPadding(false);LinearLayout tabs=new LinearLayout(app);tabs.setPadding(app.dp(3),app.dp(4),app.dp(3),app.dp(8));tabsScroll.addView(tabs);
            for(int n=0;n<seasons.length();n++){JSONObject target=seasons.optJSONObject(n);if(target==null)continue;boolean active=target.optInt("seasonNumber")==season;TextView tab=app.button(target.optString("title"),()->{if(!active)app.scCatalog(target);});tab.setTextSize(13);tab.setPadding(app.dp(14),app.dp(9),app.dp(14),app.dp(9));tab.setTextColor(active?app.MINT:app.MUTED);tab.setContentDescription(target.optString("title")+(active?", selezionata":""));tab.setSelected(active);tab.setBackground(app.focus());LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.rightMargin=app.dp(8);tabs.addView(tab,tp);seasonTabs.add(tab);if(active||seasonFilter==null)seasonFilter=tab;}
            right.addView(tabsScroll,new LinearLayout.LayoutParams(-1,-2));app.space(right,4);
        }
        CenteredScrollView scroll=new CenteredScrollView(app);LinearLayout list=app.col();list.setPadding(app.dp(5),0,app.dp(5),0);scroll.addView(list);right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(int n=0;n<entries.length();n++){JSONObject item=entries.optJSONObject(n);if(item==null)continue;View row=episodes?episode(item):season(item);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,episodes?app.dp(105):app.dp(77));rp.bottomMargin=app.dp(10);list.addView(row,rp);rows.add(row);}
        back.post(()->{if(back.isAttachedToWindow())back.requestFocus();});
    }
    TextView control(String label,Runnable action){TextView button=app.button(label,action);button.setTextSize(13);button.setPadding(app.dp(12),app.dp(10),app.dp(12),app.dp(10));leftControls.add(button);return button;}
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
        if(within(app.nav,focused)){if(key==KeyEvent.KEYCODE_DPAD_DOWN)return focus(lastRight!=null&&lastRight.isAttachedToWindow()?lastRight:seasonFilter!=null?seasonFilter:rows.isEmpty()?back:rows.get(0));return false;}
        if(focused==back){if(key==KeyEvent.KEYCODE_DPAD_UP)return focus(app.nav.getChildAt(2));if(key==KeyEvent.KEYCODE_DPAD_DOWN)return focus(seasonFilter!=null?seasonFilter:rows.isEmpty()?leftControls.isEmpty()?back:leftControls.get(0):rows.get(0));if(key==KeyEvent.KEYCODE_DPAD_RIGHT)return focus(leftControls.isEmpty()?rows.isEmpty()?back:rows.get(0):leftControls.get(0));return true;}
        int tab=seasonTabs.indexOf(focused);if(tab>=0){seasonFilter=focused;if(key==KeyEvent.KEYCODE_DPAD_UP)return focus(back);if(key==KeyEvent.KEYCODE_DPAD_DOWN)return focus(rows.isEmpty()?focused:rows.get(0));if(key==KeyEvent.KEYCODE_DPAD_LEFT&&tab==0)return focus(lastLeft!=null&&lastLeft.isAttachedToWindow()?lastLeft:leftControls.isEmpty()?back:leftControls.get(0));int next=tab+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);return focus(next<0||next>=seasonTabs.size()?focused:seasonTabs.get(next));}
        int left=leftControls.indexOf(focused);if(left>=0){lastLeft=focused;if(key==KeyEvent.KEYCODE_DPAD_RIGHT)return focus(lastRight!=null&&lastRight.isAttachedToWindow()?lastRight:rows.isEmpty()?back:rows.get(0));if(key==KeyEvent.KEYCODE_DPAD_LEFT)return true;int next=left+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);return focus(next<0?back:next>=leftControls.size()?focused:leftControls.get(next));}
        int row=rows.indexOf(focused);if(row>=0){lastRight=focused;if(key==KeyEvent.KEYCODE_DPAD_LEFT)return focus(lastLeft!=null&&lastLeft.isAttachedToWindow()?lastLeft:leftControls.isEmpty()?back:leftControls.get(0));if(key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;int next=row+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);return focus(next<0?seasonFilter!=null?seasonFilter:back:next>=rows.size()?focused:rows.get(next));}return false;
    }
}
