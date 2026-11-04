package com.nicostream.demo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** User-provided editorial chronology, deliberately not presented as official Marvel phases. */
final class MarvelGuide {
    static final int GREEN=DoomTheme.GREEN;
    static final class Chapter {String saga,name;final List<String[]> entries=new ArrayList<>();}
    final MainActivity app;final LinearLayout view,content;final List<Chapter> chapters=new ArrayList<>();final List<TextView> tabs=new ArrayList<>();final List<View> rows=new ArrayList<>();final TextView back;int selected;final MarvelResolver resolver;TextView resolveStatus,progressText;ProgressBar totalProgress;
    MarvelGuide(MainActivity app){
        this.app=app;resolver=new MarvelResolver(app);view=app.col();
        back=app.button("‹ Home",()->{app.section="Scopri";app.home();});back.setTextSize(13);view.addView(back,new LinearLayout.LayoutParams(-2,-2));app.space(view,12);
        LinearLayout hero=new LinearLayout(app);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(app.dp(20),app.dp(14),app.dp(20),app.dp(14));hero.setBackground(DoomTheme.panel(app.getResources().getDisplayMetrics().density));
        ImageView logo=new ImageView(app);logo.setImageResource(R.drawable.doomsday_logo);logo.setScaleType(ImageView.ScaleType.FIT_START);logo.setContentDescription("Avengers Doomsday");hero.addView(logo,new LinearLayout.LayoutParams(app.dp(230),app.dp(90)));
        LinearLayout progressCopy=app.col();progressCopy.setPadding(app.dp(28),0,0,0);progressCopy.addView(app.text("IL TUO PERCORSO VERSO DOOMSDAY",12,GREEN));progressText=app.title("",20);progressCopy.addView(progressText);app.space(progressCopy,8);totalProgress=new ProgressBar(app,null,android.R.attr.progressBarStyleHorizontal);totalProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));totalProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.argb(35,119,222,139)));progressCopy.addView(totalProgress,new LinearLayout.LayoutParams(-1,app.dp(4)));progressCopy.addView(app.text("Segna le tappe viste, incluse stagioni e blocchi di episodi.",11,app.MUTED));hero.addView(progressCopy,new LinearLayout.LayoutParams(0,-2,1));view.addView(hero);app.space(view,12);
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(app.getAssets().open("marvel-chronology.txt"),"UTF-8"))){Chapter current=null;String line;while((line=reader.readLine())!=null){if(line.startsWith("# ")){String[] fields=line.substring(2).split("\\|",2);current=new Chapter();current.saga=fields[0];current.name=fields[1];chapters.add(current);}else if(current!=null&&line.contains("|"))current.entries.add(line.split("\\|",2));}}catch(IOException ignored){}
        LinearLayout columns=new LinearLayout(app);view.addView(columns,new LinearLayout.LayoutParams(-1,0,1));ScrollView navigation=new ScrollView(app);LinearLayout menu=app.col();menu.setPadding(0,0,app.dp(18),0);navigation.addView(menu);columns.addView(navigation,new LinearLayout.LayoutParams(0,-1,.24f));
        ScrollView timeline=new CenteredScrollView(app);content=app.col();content.setPadding(app.dp(6),0,app.dp(6),app.dp(12));timeline.addView(content);columns.addView(timeline,new LinearLayout.LayoutParams(0,-1,.76f));String previous="";
        for(int n=0;n<chapters.size();n++){final int index=n;Chapter chapter=chapters.get(n);if(!chapter.saga.equals(previous)){TextView saga=app.text(chapter.saga.replace("THE ",""),10,GREEN);saga.setLetterSpacing(.09f);menu.addView(saga);previous=chapter.saga;}
            String label=chapter.name.replace("Phase ","Fase ").replace("Interlude ","Interludio ");TextView tab=app.button(label,()->show(index));tab.setBackground(DoomTheme.focus(app.getResources().getDisplayMetrics().density));tab.setTextSize(13);tab.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);tab.setOnFocusChangeListener((v,on)->{if(on){show(index);timeline.scrollTo(0,0);}});tab.setOnKeyListener((v,key,e)->{if(e.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_RIGHT){if(!rows.isEmpty())rows.get(0).requestFocus();return true;}return false;});tabs.add(tab);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.bottomMargin=app.dp(6);menu.addView(tab,tp);
        }
        TextView note=app.text("Ordine editoriale fornito dall’utente: saghe e fasi non ufficiali. I decimali indicano titoli inseriti successivamente nella cronologia.",10,app.MUTED);view.addView(note);if(!tabs.isEmpty()){int chapter=Math.min(app.marvelChapter,tabs.size()-1);show(chapter);refreshProgress();back.post(()->tabs.get(chapter).requestFocus());}
    }
    void show(int index){
        if(selected==index&&content.getChildCount()>0)return;selected=index;app.marvelChapter=index;for(int n=0;n<tabs.size();n++)tabs.get(n).setSelected(n==index);rows.clear();content.removeAllViews();Chapter chapter=chapters.get(index);content.addView(app.text(chapter.saga,10,GREEN));content.addView(app.title(chapter.name.replace("Phase ","Fase ").replace("Interlude ","Interludio "),24));resolveStatus=app.text(chapter.entries.size()+" tappe · OK per aprire il titolo",11,app.MUTED);content.addView(resolveStatus);app.space(content,10);
        for(String[] entry:chapter.entries){LinearLayout row=new LinearLayout(app);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(app.dp(14),app.dp(10),app.dp(14),app.dp(10));row.setBackground(DoomTheme.focus(app.getResources().getDisplayMetrics().density));row.setFocusable(true);row.setClickable(true);row.setOnClickListener(v->choose(entry));row.setOnKeyListener((v,key,e)->{if(e.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_LEFT){tabs.get(selected).requestFocus();return true;}return false;});TextView number=app.title(entry[0],15);number.setTextColor(GREEN);number.setGravity(Gravity.CENTER);number.setBackground(app.bg(Color.rgb(15,39,23),8));row.addView(number,new LinearLayout.LayoutParams(app.dp(66),app.dp(44)));TextView title=app.title(entry[1],15);title.setMaxLines(2);title.setPadding(app.dp(16),0,app.dp(12),0);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));final int ordinal=rows.size();TextView watched=app.button(seen().contains(entry[0])?"✓ Visto":"Segna visto",()->{});watched.setTextSize(12);watched.setPadding(app.dp(10),app.dp(8),app.dp(10),app.dp(8));watched.setBackground(DoomTheme.focus(app.getResources().getDisplayMetrics().density));watched.setTextColor(GREEN);watched.setContentDescription("Segna o annulla visto: "+entry[1]);Runnable toggle=()->{Set<String> values=seen();if(!values.remove(entry[0]))values.add(entry[0]);app.prefs.edit().putStringSet("marvelSeen",values).apply();watched.setText(values.contains(entry[0])?"✓ Visto":"Segna visto");refreshProgress();};watched.setOnClickListener(v->toggle.run());row.setOnLongClickListener(v->{toggle.run();return true;});row.setOnKeyListener((v,key,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_RIGHT){watched.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT){tabs.get(selected).requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){int next=ordinal+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);if(next>=0&&next<rows.size())rows.get(next).requestFocus();return true;}return false;});watched.setOnKeyListener((v,key,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_LEFT){row.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){int next=ordinal+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);if(next>=0&&next<rows.size())rows.get(next).requestFocus();return true;}return key==KeyEvent.KEYCODE_DPAD_RIGHT;});row.addView(watched,new LinearLayout.LayoutParams(app.dp(112),app.dp(44)));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,app.dp(76));rp.bottomMargin=app.dp(8);content.addView(row,rp);rows.add(row);}
    }
    Set<String> seen(){return new HashSet<>(app.prefs.getStringSet("marvelSeen",Collections.emptySet()));}
    void refreshProgress(){
        Set<String> values=seen();int done=0,total=0;
        for(int i=0;i<chapters.size();i++){Chapter chapter=chapters.get(i);int watched=0;for(String[] entry:chapter.entries){total++;if(values.contains(entry[0])){done++;watched++;}}if(i<tabs.size())tabs.get(i).setText(chapter.name.replace("Phase ","Fase ").replace("Interlude ","Interludio ")+"  "+watched+"/"+chapter.entries.size());}
        totalProgress.setMax(total);totalProgress.setProgress(done);totalProgress.setContentDescription(done+" tappe viste su "+total);progressText.setText(done+" / "+total+" tappe viste · "+(total==0?0:Math.round(done*100f/total))+"%");
    }
    void choose(String[] entry){
        // Preserve combined entries: let the viewer choose the project first.
        String title=entry[1];List<String> projects=new ArrayList<>();
        if(title.contains(" & ")&&!title.startsWith("Ant-Man")&&!title.startsWith("Deadpool")&&!title.startsWith("Shang-Chi")&&!title.startsWith("The Falcon")&&!title.startsWith("Cloak"))projects.addAll(Arrays.asList(title.split(" & ")));
        else projects.add(title);
        if(projects.size()>1)new android.app.AlertDialog.Builder(app).setTitle("Scegli il titolo").setItems(projects.toArray(new String[0]),(dialog,n)->search(projects.get(n))).setNegativeButton("Annulla",null).show();else search(title);
    }
    void search(String title){resolver.open(title,new MarvelResolver.ViewState(){
        void status(String text){resolveStatus.setText(text);}
        boolean active(){return view.isAttachedToWindow()&&app.screen.equals("marvel");}
        void open(org.json.JSONObject item){app.marvelDetail=true;app.openItem(item);}
    });}
}
