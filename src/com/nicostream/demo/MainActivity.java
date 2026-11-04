package com.nicostream.demo;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.webkit.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DefaultDataSource;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    final int BG=Color.rgb(7,11,17), PANEL=Color.rgb(18,24,33), MINT=Color.rgb(153,239,202), WHITE=Color.rgb(240,246,246), MUTED=Color.rgb(157,177,184);
    AppOpening opening;
    final TvFocusNavigation tvFocus=new TvFocusNavigation();
    LinearLayout root,body,nav,transport;
    JSONArray items=new JSONArray();
    String section="Scopri",screen="home",catalogTitle="Stream4me";
    JSONObject selected,currentSeries,playingSource,discoverFeed;
    JSONArray addonRoot=new JSONArray();
    LinearLayout discoverBody;
    ScrollView discoverScroll;
    int discoverScrollY=0;
    final Map<String,Integer> discoverRowOffsets=new HashMap<>();
    boolean discoverLoading=false;
    boolean sourceIndexLoading=false,sourceIndexWarning=false;
    long sourceIndexUpdated=0;
    int sourceIndexFailures=0;
    final ArrayList<Runnable> sourceIndexWaiters=new ArrayList<>();
    boolean programHomeLoading=false;
    long programHomeUpdated=0;
    long programHomeRevision=0;
    String programHomeServer="";
    JSONObject programHomeFeed=null;
    LinearLayout programHomeSlot;
    String discoverError="";
    long discoverUpdated=0;
    boolean playingStarted=false;
    long lastProgressSave=0;
    JSONArray seriesSeasons=new JSONArray();
    String focusItem="";
    final Map<Long,JSONObject> knownSeries=new HashMap<>();
    final Map<Long,JSONArray> knownSeasons=new HashMap<>();
    final android.util.LruCache<String,android.graphics.Bitmap> artworkCache=new android.util.LruCache<String,android.graphics.Bitmap>(16*1024*1024){protected int sizeOf(String k,android.graphics.Bitmap b){return b.getByteCount();}};
    final ExecutorService artworkWorker=Executors.newFixedThreadPool(2);
    android.content.SharedPreferences prefs;
    final ExecutorService worker=Executors.newFixedThreadPool(3);
    final Handler main=new Handler(Looper.getMainLooper());
    TextView status,clock,pauseButton,sourceDomainStatus;
    ExoPlayer player;
    SearchScreen searchScreen;
    boolean unifiedSearchBusy;
    String searchBackSection="Scopri",searchBackTitle="Stream4me",searchLastQuery="";
    JSONArray searchBackItems=new JSONArray(),searchLastResults;

    PosterBadges posterBadges;
    HeroCarousel heroCarousel;
    volatile long heroEnrichmentGeneration;
    final Map<String,JSONObject> heroRecordCache=new ConcurrentHashMap<>();
    final Set<String> heroLookups=Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
    AppUpdates updates;
    SeekBar seek;
    volatile String currentJob="";
    volatile long generation=0;
    volatile boolean alive=true;
    boolean seeking=false;
    long controlUntil=0;
    int revealKey=KeyEvent.KEYCODE_UNKNOWN;
    ArrayDeque<JSONArray> history=new ArrayDeque<>();
    ArrayDeque<String> titles=new ArrayDeque<>();
    ArrayDeque<String> historySections=new ArrayDeque<>();
    AlertDialog activeDialog;
    WebView browser;
    JSONObject browserMediaCandidate;
    String browserPage="";
    String browserIdentity="";
    TextView nativePlayerButton;
    JSONObject automaticEpisode;
    FrameLayout webFrame;
    View webCustomView;
    WebChromeClient.CustomViewCallback webCustomCallback;

    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("nico",0);posterBadges=new PosterBadges();
        updates=new AppUpdates(this,()->screen.equals("home")&&section.equals("Scopri")&&currentJob.isEmpty());
        if(getIntent().getBooleanExtra("import_engine_setup",false)){
            File setup=new File(getExternalFilesDir(null),"engine-setup.json");
            try{JSONObject config=new JSONObject(new String(java.nio.file.Files.readAllBytes(setup.toPath()),"UTF-8"));String endpoint=config.getString("server"),key=config.getString("key");if(endpoint.equals(EngineAccess.SERVER)&&key.matches("[a-f0-9]{64}"))prefs.edit().putString("serverKey",key).apply();}catch(Exception ignored){}finally{setup.delete();getIntent().removeExtra("import_engine_setup");}
        }
        prefs.edit().remove("server").apply();
        android.util.Log.i("NicoStreamPlayback","WIDEVINE_SUPPORTED="+android.media.MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID));
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        try{currentSeries=new JSONObject(prefs.getString("scLastTitle","{}"));}catch(Exception ignored){}
        try{if(prefs.getInt("discoverVersion",0)==1){discoverFeed=new JSONObject(prefs.getString("discoverFeed","{}"));discoverUpdated=0;}}catch(Exception ignored){}
        localCatalog();items=new JSONArray();if(prefs.getString("serverKey","").isEmpty())settings();else{refreshSourceIndex(true,null);home();}
        if(b==null){opening=new AppOpening(this);opening.show();}
    }
    String server(){return EngineAccess.SERVER;}
    void localCatalog(){try{items=new JSONObject(read(getAssets().open("catalog.json"))).getJSONArray("items");}catch(Exception e){items=new JSONArray();}}
    String read(InputStream in)throws IOException{
        try(InputStream s=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;
            while((n=s.read(b))!=-1){out.write(b,0,n);if(out.size()>2097152)throw new IOException("Risposta troppo grande");}
            return out.toString("UTF-8");
        }
    }
    JSONObject api(String path,JSONObject post)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(server()+path).openConnection();
        c.setConnectTimeout(10000);c.setReadTimeout(65000);c.setInstanceFollowRedirects(false);
        String authorization=EngineAccess.authorization(server(),server()+path,prefs.getString("serverKey",""));if(!authorization.isEmpty())c.setRequestProperty("Authorization",authorization);
        try{
            if(post!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(post.toString().getBytes("UTF-8"));}}
            int code=c.getResponseCode();
            if(code==401||code==403)throw new IOException("Accesso al server non autorizzato. Controlla la chiave nelle impostazioni.");
            JSONObject result=new JSONObject(read(code<400?c.getInputStream():c.getErrorStream()));
            if(code>=400)throw new IOException(result.optString("error","Errore server "+code));
            return result;
        }finally{c.disconnect();}
    }
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    android.graphics.drawable.Drawable focus(){
        StateListDrawable d=new StateListDrawable();
        GradientDrawable on=bg(Color.rgb(36,54,58),10);on.setStroke(dp(2),MINT);
        d.addState(new int[]{android.R.attr.state_focused},on);
        GradientDrawable pressed=bg(Color.rgb(36,54,58),10);pressed.setStroke(dp(2),MINT);
        d.addState(new int[]{android.R.attr.state_pressed},pressed);d.addState(new int[]{},bg(PANEL,10));return d;
    }
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setIncludeFontPadding(false);t.setLineSpacing(dp(2),1.04f);t.setPadding(0,dp(4),0,dp(4));return t;}
    TextView title(String s,int size){TextView t=text(s,size,WHITE);t.setTypeface(Typeface.create("sans-serif-medium",0));return t;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setClipChildren(false);l.setClipToPadding(false);return l;}
    void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    TextView button(String label,Runnable action){TextView b=text(label,16,WHITE);b.setGravity(Gravity.CENTER);b.setPadding(dp(20),dp(13),dp(20),dp(13));b.setMinHeight(dp(46));b.setBackground(focus());b.setFocusable(true);b.setClickable(true);b.setOnClickListener(v->action.run());return b;}
    void base(){
        if(searchScreen!=null){searchScreen.close();searchScreen=null;if(unifiedSearchBusy){unifiedSearchBusy=false;cancelJob();}}
        revealKey=KeyEvent.KEYCODE_UNKNOWN;
        if(heroCarousel!=null){heroCarousel.dispose();heroCarousel=null;}
        savePlayback(false);playingSource=null;playingStarted=false;
        automaticEpisode=null;
        if(browser!=null){try{ServiceWorkerController.getInstance().setServiceWorkerClient(null);}catch(RuntimeException ignored){}browser.stopLoading();browser.destroy();browser=null;}
        if(webCustomCallback!=null){webCustomCallback.onCustomViewHidden();webCustomCallback=null;}webCustomView=null;
        if(player!=null){player.release();player=null;}
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        transport=null;root=col();root.setBackgroundColor(BG);root.setPadding(dp(26),dp(18),dp(26),dp(12));setContentView(root);
    }
    void addNavigation(String activeSection){
        HorizontalScrollView menu=new HorizontalScrollView(this);menu.setHorizontalScrollBarEnabled(false);nav=new LinearLayout(this);
        for(String s:new String[]{"Scopri","Cerca","Catalogo","Preferiti","Impostazioni"}){
            TextView b=button(s,()->{if(s.equals("Impostazioni")){settings();return;}if(s.equals("Cerca")){searchCatalog();return;}if(s.equals("Scopri")){discoverScrollY=0;history.clear();titles.clear();historySections.clear();items=addonRoot;catalogTitle="Stream4me";section=s;focusItem="";home();return;}if(s.equals("Catalogo")){showAddons();return;}section=s;focusItem="";home();});
            b.setTextSize(14);b.setTextColor(activeSection.equals(s)?WHITE:MUTED);b.setPadding(dp(14),dp(7),dp(14),dp(5));b.setMinHeight(dp(40));
            StateListDrawable tabBackground=new StateListDrawable();GradientDrawable active=bg(Color.rgb(29,46,54),6);active.setStroke(dp(1),MINT);tabBackground.addState(new int[]{android.R.attr.state_focused},active);tabBackground.addState(new int[]{android.R.attr.state_pressed},active);tabBackground.addState(new int[]{},bg(Color.TRANSPARENT,6));b.setBackground(tabBackground);
            GradientDrawable underline=bg(activeSection.equals(s)?WHITE:Color.TRANSPARENT,1);underline.setSize(dp(20),dp(2));b.setCompoundDrawablePadding(dp(4));b.setCompoundDrawablesWithIntrinsicBounds(null,null,null,underline);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));p.setMargins(0,0,dp(6),0);nav.addView(b,p);
        }
        menu.addView(nav);root.addView(menu);space(root,8);
    }
    void home(){
        if(section.equals("Cerca")&&searchLastResults!=null){searchCatalog(searchLastQuery,searchLastResults);return;}
        screen="home";base();
        root.setPadding(dp(26),dp(8),dp(26),dp(12));
        addNavigation(section);
        ScrollView scroll=new CenteredScrollView(this);body=col();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(section.equals("Scopri")){
            discoverBody=body;discoverScroll=scroll;renderDiscover();loadDiscover(false);refreshSourceIndex(false,null);loadProgramHome(false);if(updates!=null)updates.maybeCheck();scroll.post(()->scroll.scrollTo(0,discoverScrollY));
            root.post(()->{if(root.findFocus()==null)nav.getChildAt(0).requestFocus();});return;
        }
        if(section.equals("Cerca")){String query=prefs.getString("lastSearchQuery","");if(!query.isEmpty()){body.addView(button("⌕ "+query+" · Modifica ricerca",()->searchCatalog(query)),new LinearLayout.LayoutParams(-2,-2));space(body,12);}}
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(title(section.equals("Preferiti")?"La tua selezione":server().isEmpty()?"Da esplorare":catalogTitle,21),new LinearLayout.LayoutParams(0,-2,1));
        if(!history.isEmpty())heading.addView(button("‹ Indietro",this::previous));
        if(!server().isEmpty())heading.addView(button("⌂ Addon",this::showAddons));body.addView(heading);
        if(items.length()>0&&items.optJSONObject(0)!=null&&items.optJSONObject(0).optString("addonId").equals("plugin.video.nikod.programfeed"))body.addView(button("Impostazioni Program Feed",()->{try{action(new JSONObject().put("url","plugin://plugin.video.nikod.programfeed/?android_settings=1").put("title","Program Feed"));}catch(Exception ignored){}}));
        boolean nativeCatalog=!section.equals("Cerca")&&!section.equals("Preferiti")&&items.length()>0&&items.optJSONObject(0)!=null&&!items.optJSONObject(0).optString("scAction").isEmpty();
        boolean toonDirectory=!section.equals("Cerca")&&!section.equals("Preferiti")&&items.length()>0&&items.optJSONObject(0)!=null&&items.optJSONObject(0).optString("addonId").equals("plugin.video.nikod.toonitalia");
        boolean episodes=(nativeCatalog&&items.optJSONObject(0).optString("scAction").equals("episode"))||(!section.equals("Preferiti")&&toonEpisodes());
        boolean seasons=nativeCatalog&&items.optJSONObject(0).optString("scAction").equals("season");
        if(nativeCatalog){long id=items.optJSONObject(0).optLong("scTitleId");if(knownSeries.containsKey(id)){currentSeries=knownSeries.get(id);seriesSeasons=knownSeasons.get(id);}}
        if(seasons&&currentSeries!=null)seriesBanner(currentSeries,false);
        if(episodes&&!toonDirectory){
            HorizontalScrollView selector=new HorizontalScrollView(this);selector.setHorizontalScrollBarEnabled(false);LinearLayout tabs=new LinearLayout(this);
            for(int n=0;n<seriesSeasons.length();n++){JSONObject season=seriesSeasons.optJSONObject(n);if(season==null)continue;TextView tab=button(season.optString("title"),()->scCatalog(season));if(season.optInt("seasonNumber")==items.optJSONObject(0).optInt("seasonNumber"))tab.setTextColor(MINT);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.setMargins(0,0,dp(8),0);tabs.addView(tab,tp);}selector.addView(tabs);body.addView(selector);space(body,12);
        }
        if(!seasons)body.addView(text(section.equals("Cerca")?"Risultati da tutti gli addon · La sorgente è indicata su ogni scheda":toonDirectory?"ToonItalia · OK per aprire · Tieni premuto OK per sorgenti e preferiti":nativeCatalog?episodes?"Scegli una puntata · OK per guardare":"StreamingCommunity":section.equals("Preferiti")?"I titoli che hai salvato":"Esplora il catalogo",12,MUTED));space(body,10);
        // Wrap rows on TV, allowing vertical navigation through large catalogues.
        JSONArray visible=section.equals("Preferiti")?favorites():items;
        int minimumCard=168;
        for(int n=0;n<visible.length();n++){JSONObject item=visible.optJSONObject(n);if(item!=null&&!posterItem(item))minimumCard=208;}
        int windowWidth=Build.VERSION.SDK_INT>=30?getWindowManager().getCurrentWindowMetrics().getBounds().width():getResources().getDisplayMetrics().widthPixels;
        int available=Math.max(1,windowWidth-root.getPaddingLeft()-root.getPaddingRight()),gap=dp(12);
        // Account for gaps only between cards, then distribute the remaining pixels evenly.
        int columns=Math.max(1,(available+gap)/(dp(minimumCard)+gap));
        int cardWidth=Math.max(1,(available-gap*(columns-1))/columns);
        int count=0;LinearLayout row=null;
        for(int i=0;i<visible.length();i++){
            JSONObject item=visible.optJSONObject(i);if(item==null)continue;
            if(isScSearch(item))try{item.put("title","StreamingCommunity · Scegli un titolo").put("tag","TITOLI, STAGIONI E PUNTATE");}catch(Exception ignored){}
            if(seasons){count++;continue;}
            if(episodes){if(item.optBoolean("isNotice"))body.addView(button("ⓘ Informazioni",()->openItem(item)));else if(item.optBoolean("isFolder"))body.addView(button(item.optString("title"),()->openItem(item)));else body.addView(episodeRow(item));count++;continue;}
            if(count%columns==0){row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);row.setPadding(0,dp(8),0,dp(8));body.addView(row);}
            View tile=card(item);LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(cardWidth,-2);
            tileParams.setMargins(0,0,(count%columns==columns-1)?0:gap,dp(12));row.addView(tile,tileParams);count++;
        }
        if(count==0)body.addView(text(section.equals("Preferiti")?"Nessun preferito. Aggiungi un contenuto dalla sua scheda.":"Nessun titolo trovato. Prova con un’altra ricerca.",16,MUTED));
        space(body,18);body.addView(text("Frecce per navigare · OK per scegliere · Indietro per tornare",11,MUTED));
        root.post(()->{if(root.findFocus()==null)nav.getChildAt(section.equals("Cerca")?1:section.equals("Catalogo")?2:section.equals("Preferiti")?3:0).requestFocus();});
    }
    String safeMetadata(JSONObject item){try{return StreamingCommunityCatalog.publicMetadata(item).toString();}catch(Exception e){return "{}";}}
    void refreshSourceIndex(boolean force,Runnable after){
        if(!force&&System.currentTimeMillis()-sourceIndexUpdated<60000){if(after!=null)after.run();return;}
        if(after!=null)sourceIndexWaiters.add(after);if(sourceIndexLoading)return;sourceIndexLoading=true;
        worker.execute(()->{
            try{JSONObject index=api("/v1/sources/stream4me"+(force?"?refresh=1":""),null);main.post(()->{
                if(!alive)return;
                try{applySourceIndex(index);sourceIndexWarning=index.optBoolean("usingLastValid");sourceIndexUpdated=System.currentTimeMillis();sourceIndexFailures=0;}catch(Exception e){sourceIndexWarning=true;sourceIndexFailures++;}
                finishSourceIndex();
            });}catch(Exception e){main.post(()->{if(!alive)return;sourceIndexWarning=true;sourceIndexFailures++;finishSourceIndex();});}
        });
    }
    void finishSourceIndex(){
        sourceIndexLoading=false;if(sourceDomainStatus!=null&&sourceDomainStatus.isAttachedToWindow())sourceDomainStatus.setText("StreamingCommunity: "+(providerOrigin().isEmpty()?"dominio non disponibile":Uri.parse(providerOrigin()).getHost())+(sourceIndexWarning?" · ultima copia valida":""));ArrayList<Runnable> ready=new ArrayList<>(sourceIndexWaiters);sourceIndexWaiters.clear();for(Runnable task:ready)task.run();
        if(screen.equals("home")&&section.equals("Scopri")){
            if(providerOrigin().isEmpty()){
                if(sourceIndexWarning)discoverError="Connessione alle sorgenti temporaneamente non disponibile.";
                renderDiscover();
                if(sourceIndexFailures>0&&sourceIndexFailures<=3)main.postDelayed(()->{if(alive&&screen.equals("home")&&section.equals("Scopri")&&providerOrigin().isEmpty())refreshSourceIndex(false,null);},15000);
            }else loadDiscover(false);
        }
    }
    void withSourceIndex(Runnable task){final long request=generation;refreshSourceIndex(false,()->{if(alive&&generation==request)task.run();});}
    JSONObject currentSource(JSONObject item){try{JSONObject copy=new JSONObject(item.toString());SourceDomains.migrate(copy,providerOrigin());return copy;}catch(Exception e){return item;}}
    void applySourceIndex(JSONObject index)throws Exception {
        String origin=SourceDomains.origin(index.optString("streamingCommunity"));if(origin.isEmpty())throw new IOException("Indice sorgenti non valido");
        String previous=providerOrigin();android.content.SharedPreferences.Editor edit=prefs.edit().putString("scOrigin",origin).putString("sourceIndexRevision",index.optString("revision"));
        if(!origin.equals(previous)){
            for(String key:new String[]{"favorites","playbackHistory","scLastTitle"}){String json=prefs.getString(key,"");if(json.isEmpty())continue;try{Object value=new JSONTokener(json).nextValue();SourceDomains.migrate(value,origin);edit.putString(key,value.toString());}catch(Exception ignored){}}
            edit.putString("scLastPage",SourceDomains.rebase(prefs.getString("scLastPage",""),origin)).remove("discoverFeed").remove("discoverUpdated");
            SourceDomains.migrate(items,origin);SourceDomains.migrate(addonRoot,origin);SourceDomains.migrate(currentSeries,origin);SourceDomains.migrate(seriesSeasons,origin);
            for(JSONArray directory:history)SourceDomains.migrate(directory,origin);
            for(JSONObject series:knownSeries.values())SourceDomains.migrate(series,origin);
            for(JSONArray seasons:knownSeasons.values())SourceDomains.migrate(seasons,origin);
            focusItem=SourceDomains.rebase(focusItem,origin);discoverFeed=null;discoverUpdated=0;
        }
        edit.apply();
        if(!origin.equals(previous)&&screen.equals("home")&&section.equals("Scopri")){renderDiscover();loadDiscover(true);}
    }
    String providerOrigin(){
        String configured=SourceDomains.origin(prefs.getString("scOrigin",""));if(!configured.isEmpty())return configured;
        try{Uri u=Uri.parse(new JSONObject(prefs.getString("scLastTitle","{}")).optString("url"));if(sourceHost(u.getHost()))return "https://"+u.getHost();}catch(Exception ignored){}
        for(int n=0;n<items.length();n++){JSONObject item=items.optJSONObject(n);if(item!=null&&isScSearch(item))return "https://"+Uri.parse(item.optString("url")).getHost();}return "";
    }
    void loadDiscover(boolean force){
        String origin=providerOrigin();if(sourceIndexLoading||server().isEmpty()||origin.isEmpty()||discoverLoading||(!force&&discoverFeed!=null&&discoverFeed.optJSONArray("rails")!=null&&System.currentTimeMillis()-discoverUpdated<600000))return;
        discoverLoading=true;discoverError="";final String ua=WebSettings.getDefaultUserAgent(this);
        worker.execute(()->{try{JSONObject page=scPage(origin+"/it",ua);JSONObject feed=StreamingCommunityCatalog.home(page,page.optString("_sourceOrigin",origin));if(feed.getJSONArray("rails").length()==0)throw new IOException("Nessuna raccolta disponibile");
            main.post(()->{if(!alive)return;discoverLoading=false;if(!origin.equals(providerOrigin())){if(screen.equals("home")&&section.equals("Scopri"))loadDiscover(true);return;}discoverFeed=feed;discoverUpdated=System.currentTimeMillis();prefs.edit().putString("discoverFeed",safeMetadata(feed)).putInt("discoverVersion",1).putLong("discoverUpdated",discoverUpdated).apply();if(screen.equals("home")&&section.equals("Scopri")&&discoverBody==body){boolean browsing=body.hasFocus();if(!browsing||force){renderDiscover();if(force)nav.getChildAt(0).requestFocus();}else{TextView update=button("Nuovi titoli disponibili · Aggiorna",()->{focusItem="";home();});body.addView(update,0);}}enrichHero(feed);});
        }catch(Exception e){main.post(()->{if(!alive)return;discoverLoading=false;if(!origin.equals(providerOrigin())){if(screen.equals("home")&&section.equals("Scopri"))loadDiscover(true);return;}discoverError="Non riesco ad aggiornare i titoli. Puoi riprovare o usare Cerca.";if(screen.equals("home")&&section.equals("Scopri")&&discoverBody==body&&(!body.hasFocus()||force)){renderDiscover();if(force)nav.getChildAt(0).requestFocus();}});}});
    }
    void enrichHero(JSONObject feed){
        final long revision=++heroEnrichmentGeneration;final String origin=providerOrigin(),ua=WebSettings.getDefaultUserAgent(this);final JSONArray titles=HeroSelection.topTen(feed);
        worker.execute(()->{
            for(int n=0;n<titles.length();n++){
                if(!alive||revision!=heroEnrichmentGeneration)return;JSONObject target=titles.optJSONObject(n);if(target==null||HeroPreviewResolver.allowed(target.optString("previewURL")))continue;
                String key=origin+":"+target.optLong("scTitleId");JSONObject ready=heroRecordCache.get(key);
                if(ready==null&&!heroLookups.add(key))continue;
                try{
                    if(ready==null){JSONObject props=scPage(target.optString("url"),ua);JSONObject record=props.getJSONObject("title");if(record.optLong("id")!=target.optLong("scTitleId"))continue;ready=StreamingCommunityCatalog.titleEntry(record,props,props.optString("_sourceOrigin",origin));if(!HeroPreviewResolver.allowed(ready.optString("previewURL")))continue;heroRecordCache.put(key,ready);}
                    final JSONObject enriched=ready;
                    main.post(()->{if(!alive||revision!=heroEnrichmentGeneration||discoverFeed!=feed)return;try{for(java.util.Iterator<String> keys=enriched.keys();keys.hasNext();){String field=keys.next();target.put(field,enriched.get(field));}if(screen.equals("home")&&section.equals("Scopri")&&heroCarousel!=null)heroCarousel.update(HeroSelection.candidates(feed));}catch(JSONException ignored){}});
                }catch(Exception ignored){}
            }
        });
    }
    void loadProgramHome(boolean force){
        if(server().isEmpty()||programHomeLoading)return;
        final String endpoint=server();final long revision=programHomeRevision;
        if(!force&&endpoint.equals(programHomeServer)&&System.currentTimeMillis()-programHomeUpdated<30000)return;
        programHomeLoading=true;
        worker.execute(()->{
            try{JSONObject feed=api("/v1/home/programfeed",null);main.post(()->{
                programHomeLoading=false;if(!alive||!server().equals(endpoint))return;
                if(revision!=programHomeRevision){if(screen.equals("home")&&section.equals("Scopri"))loadProgramHome(true);return;}
                programHomeServer=endpoint;programHomeFeed=feed;programHomeUpdated=System.currentTimeMillis();renderProgramHome();
            });}catch(Exception e){main.post(()->{programHomeLoading=false;if(alive&&server().equals(endpoint)&&programHomeFeed==null&&programHomeSlot!=null&&programHomeSlot.isAttachedToWindow()){programHomeSlot.removeAllViews();programHomeSlot.addView(button("Program Feed · Riprova",()->loadProgramHome(true)));}});}
        });
    }
    void renderProgramHome(){
        if(programHomeSlot==null||!programHomeSlot.isAttachedToWindow())return;
        programHomeSlot.removeAllViews();
        if(programHomeFeed==null||!programHomeServer.equals(server())||!programHomeFeed.optBoolean("enabled"))return;
        LinearLayout previousBody=body;body=programHomeSlot;
        JSONArray proposals=programHomeFeed.optJSONArray("items");
        if(proposals!=null&&proposals.length()>0)discoverRail("Program Feed · Oggi",proposals,false);
        else{body.addView(title("Program Feed · Oggi",22));body.addView(text("Nessun evento futuro o in corso stimato nel palinsesto di oggi.",14,MUTED));space(body,20);}
        body=previousBody;
    }
    void renderDiscover(){
        if(heroCarousel!=null){heroCarousel.dispose();heroCarousel=null;}
        body.removeAllViews();if(server().isEmpty()){body.addView(title("La tua demo",32));body.addView(text("Scegli un film per provare il player.",16,MUTED));space(body,18);for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null)body.addView(card(item));}return;}JSONObject featured=discoverFeed==null?null:discoverFeed.optJSONObject("hero");
        if(featured!=null)discoverHero(featured);
        else if(currentSeries!=null&&!currentSeries.optString("url").isEmpty())discoverHero(currentSeries);
        else{body.addView(text("IL TUO SPAZIO PER FILM E SERIE",11,MINT));body.addView(title("Cosa guardiamo stasera?",32));body.addView(button("Cerca un titolo",this::searchCatalog));space(body,20);}
        programHomeSlot=col();body.addView(programHomeSlot);programHomeSlot.post(this::renderProgramHome);
        continueShelf();
        JSONArray saved=favorites();if(saved.length()>0)discoverRail("La tua lista",saved,false);
        JSONArray rails=discoverFeed==null?null:discoverFeed.optJSONArray("rails");
        // Keep Top 10 first among discovery rails, preserving the order of the others.
        if(rails!=null)for(int pass=0;pass<2;pass++)for(int i=0;i<rails.length();i++){
            JSONObject rail=rails.optJSONObject(i);if(rail==null)continue;
            boolean ranked=rail.optString("id").equals("top10");
            if(ranked==(pass==0))discoverRail(rail.optString("title"),rail.optJSONArray("items"),ranked);
        }
        JSONArray genres=discoverFeed==null?null:discoverFeed.optJSONArray("genres");String origin=providerOrigin();
        if(genres!=null&&!origin.isEmpty()){
            body.addView(title("Esplora per genere",22));HorizontalScrollView scroll=tvShelf();scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);
            for(int i=0;i<genres.length();i++){JSONObject genre=genres.optJSONObject(i);if(genre==null||genre.optInt("id")<=0)continue;try{JSONObject item=new JSONObject().put("url",origin+"/it/archive?genre%5B%5D="+genre.getInt("id")).put("scAction","collection").put("title",genre.getString("name"));TextView tab=button(genre.getString("name"),()->scCatalog(item));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-2,-2);gp.setMargins(0,dp(8),dp(10),dp(16));row.addView(tab,gp);}catch(Exception ignored){}}
            scroll.addView(row);body.addView(scroll);
        }
        if(rails==null||rails.length()==0){body.addView(text(discoverError.isEmpty()?providerOrigin().isEmpty()?"Preparazione automatica dei suggerimenti…":"Caricamento dei titoli…":discoverError,14,MUTED));if(!discoverError.isEmpty())body.addView(button("Riprova",()->refreshSourceIndex(true,()->loadDiscover(true))));}
        else{LinearLayout footer=new LinearLayout(this);footer.addView(text("Scopri qualcosa di nuovo, oppure riparti da dove eri rimasto.",12,MUTED),new LinearLayout.LayoutParams(0,-2,1));footer.addView(button("Aggiorna",()->{refreshSourceIndex(true,()->loadDiscover(true));loadProgramHome(true);}));body.addView(footer);if(!discoverError.isEmpty())body.addView(text(discoverError,12,MUTED));}
        if(server().isEmpty()&&(rails==null||rails.length()==0))for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null)body.addView(card(item));}
        space(body,20);
    }
    void discoverHero(JSONObject item){
        FrameLayout outer=new FrameLayout(this),stage=new FrameLayout(this);outer.setTag("tv-focus-anchor");outer.setClipChildren(false);outer.addView(stage,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout pager=new LinearLayout(this);pager.setGravity(Gravity.CENTER_VERTICAL);TextView previous=button("‹",()->{}),next=button("›",()->{}),counter=text("",12,MUTED);previous.setContentDescription("Titolo precedente");next.setContentDescription("Titolo successivo");counter.setGravity(Gravity.CENTER);pager.addView(previous);pager.addView(counter,new LinearLayout.LayoutParams(dp(65),-2));pager.addView(next);
        FrameLayout.LayoutParams pagerParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);pagerParams.rightMargin=dp(28);pagerParams.bottomMargin=dp(12);outer.addView(pager,pagerParams);
        body.addView(outer,new LinearLayout.LayoutParams(-1,dp(440)));space(body,24);heroCarousel=new HeroCarousel(stage,this::buildHeroSlide,counter,previous,next);JSONArray candidates=HeroSelection.candidates(discoverFeed);if(candidates.length()==0)candidates.put(item);heroCarousel.update(candidates);
    }
    HeroCarousel.Slide buildHeroSlide(JSONObject item,boolean preload,boolean immediate){
        HeroPreview previewPlayer=null;
        int viewport=getResources().getDisplayMetrics().widthPixels-dp(52);
        boolean compact=viewport<dp(720);int posterWidth=compact?96:112,posterHeight=posterWidth*3/2;
        FrameLayout hero=new FrameLayout(this);hero.setTag("tv-focus-anchor");hero.setClipChildren(false);hero.setClipToPadding(false);
        FrameLayout backdrop=new FrameLayout(this);backdrop.setBackground(bg(PANEL,14));backdrop.setClipToOutline(true);FrameLayout.LayoutParams backdropParams=new FrameLayout.LayoutParams(-1,dp(350));hero.addView(backdrop,backdropParams);
        String image=item.optString("backdrop");if(image.isEmpty())image=item.optString("thumb");if(image.isEmpty())image=item.optString("art");
        backdrop.addView(artwork(image,-1,350),new FrameLayout.LayoutParams(-1,-1));
        String preview=item.optString("previewURL");if(HeroPreviewResolver.allowed(preview)){
            TextureView trailer=new TextureView(this);trailer.setAlpha(0);trailer.setOpaque(false);trailer.setFocusable(false);trailer.setClickable(false);trailer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);backdrop.addView(trailer,new FrameLayout.LayoutParams(-1,-1));previewPlayer=new HeroPreview(this,backdrop,trailer,worker,preview,item.optString("previewReferer",providerOrigin()+"/it"),WebSettings.getDefaultUserAgent(this),preload,immediate);
        }
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.argb(170,7,11,17),Color.argb(60,7,11,17),Color.TRANSPARENT}));backdrop.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        View bottomShade=new View(this);bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.TRANSPARENT,BG}));backdrop.addView(bottomShade,new FrameLayout.LayoutParams(-1,dp(85),Gravity.BOTTOM));
        LinearLayout foreground=new LinearLayout(this);foreground.setGravity(Gravity.CENTER_VERTICAL);foreground.setPadding(dp(30),dp(120),dp(30),dp(24));foreground.setTranslationY(dp(30));foreground.setClipChildren(false);foreground.setClipToPadding(false);
        String poster=item.optString("art");if(poster.startsWith("https://")){
            FrameLayout cover=new FrameLayout(this);GradientDrawable border=bg(Color.rgb(35,43,51),10);border.setStroke(dp(1),Color.argb(70,255,255,255));cover.setBackground(border);cover.setPadding(dp(2),dp(2),dp(2),dp(2));cover.setElevation(dp(10));cover.setTranslationY(dp(26));cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            ImageView artwork=artwork(poster,posterWidth,posterHeight);artwork.setBackground(bg(PANEL,8));cover.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout.LayoutParams coverParams=new LinearLayout.LayoutParams(dp(posterWidth+4),dp(posterHeight+4));coverParams.rightMargin=dp(compact?16:22);foreground.addView(cover,coverParams);
        }
        LinearLayout copy=col();copy.setGravity(Gravity.CENTER_VERTICAL);
        String heroLabel="IN EVIDENZA";JSONArray ranking=HeroSelection.topTen(discoverFeed);for(int rank=0;rank<ranking.length();rank++)if(ranking.optJSONObject(rank).optLong("scTitleId")==item.optLong("scTitleId")){heroLabel="TOP 10 · #"+(rank+1);break;}
        TextView eyebrow=text(heroLabel,10,MINT);eyebrow.setLetterSpacing(.16f);copy.addView(eyebrow);space(copy,8);
        TextView name=title(item.optString("title"),compact?26:30);name.setShadowLayer(dp(4),0,dp(2),Color.BLACK);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(name);space(copy,8);
        TextView metadata=text(item.optString("tag")+(item.optString("year").isEmpty()?"":"  ·  "+item.optString("year")),11,MUTED);metadata.setLetterSpacing(.06f);copy.addView(metadata);space(copy,8);
        TextView plot=text(item.optString("description"),12,WHITE);plot.setShadowLayer(dp(3),0,dp(1),Color.BLACK);plot.setMaxLines(2);plot.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(plot);space(copy,12);
        LinearLayout actions=new LinearLayout(this);TextView open=button("▶ Apri titolo",()->openItem(item));actions.addView(open);TextView favorite=button(isFavorite(item)?"✓ Nella mia lista":"＋ La mia lista",()->{toggleFavorite(item);home();});LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,-2);fp.leftMargin=dp(8);actions.addView(favorite,fp);for(TextView action:new TextView[]{open,favorite}){action.setTextSize(13);action.setMinHeight(dp(40));action.setPadding(dp(14),dp(10),dp(14),dp(10));}copy.addView(actions);
        int copyWidth=Math.min(dp(compact?285:340),viewport-dp(60+(poster.startsWith("https://")?posterWidth+4+(compact?16:22):0)));
        foreground.addView(copy,new LinearLayout.LayoutParams(Math.max(dp(220),copyWidth),-2));hero.addView(foreground,new FrameLayout.LayoutParams(-1,-1));return new HeroCarousel.Slide(hero,previewPlayer);
    }
    HorizontalScrollView tvShelf(){
        HorizontalScrollView shelf=new HorizontalScrollView(this);shelf.setClipChildren(false);shelf.setClipToPadding(false);shelf.setPadding(dp(10),dp(12),dp(10),dp(12));shelf.setHorizontalScrollBarEnabled(false);return shelf;
    }
    void discoverRail(String label,JSONArray list,boolean ranked){
        if(list==null||list.length()==0)return;LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(title(label,22),new LinearLayout.LayoutParams(0,-2,1));heading.addView(button("Vedi tutti",()->{pushHistory();items=list;catalogTitle=label;section="Catalogo";focusItem="";home();}));body.addView(heading);space(body,8);
        HorizontalScrollView scroll=tvShelf();scroll.setClipToPadding(false);scroll.setPadding(dp(10),dp(12),dp(10),dp(12));scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);
        for(int n=0;n<Math.min(12,list.length());n++){JSONObject item=list.optJSONObject(n);if(item==null)continue;row.addView(ranked?rankedCard(item,n+1):card(item));}
        scroll.addView(row);tvFocus.shelf(scroll,heading.getChildAt(1));scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,oldX,oldY)->discoverRowOffsets.put(label,x));scroll.post(()->scroll.scrollTo(discoverRowOffsets.getOrDefault(label,0),0));body.addView(scroll);space(body,20);
    }
    View rankedCard(JSONObject item,int rank){
        // Match iPad: an outlined number behind a 2:3 poster, with wider space for 10.
        int height=228,posterWidth=152,numberWidth=Math.round(height*(rank>=10?1.22f:.82f));
        int inset=Math.round(numberWidth*.78f);
        FrameLayout card=new FrameLayout(this);card.setPadding(dp(4),dp(4),dp(4),dp(4));focusCard(card,item);
        StateListDrawable highlight=new StateListDrawable();GradientDrawable outline=bg(Color.TRANSPARENT,4);outline.setStroke(dp(2),MINT);highlight.addState(new int[]{android.R.attr.state_focused},outline);highlight.addState(new int[]{android.R.attr.state_pressed},outline);highlight.addState(new int[]{},bg(Color.TRANSPARENT,0));card.setBackground(highlight);
        card.setContentDescription("Numero "+rank+": "+item.optString("title"));
        View number=new View(this){
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            final android.graphics.Path glyph=new android.graphics.Path();
            @Override protected void onDraw(android.graphics.Canvas canvas){
                super.onDraw(canvas);String label=String.valueOf(rank);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(100);
                glyph.reset();paint.getTextPath(label,0,label.length(),0,0,glyph);glyph.op(glyph,android.graphics.Path.Op.UNION);android.graphics.RectF bounds=new android.graphics.RectF();glyph.computeBounds(bounds,true);
                if(bounds.width()==0||bounds.height()==0)return;
                float stroke=getResources().getDisplayMetrics().density*2.5f,scale=Math.min((getWidth()-stroke*2)/bounds.width(),(getHeight()-stroke*2)/bounds.height());
                android.graphics.Matrix matrix=new android.graphics.Matrix();matrix.setScale(scale,scale);matrix.postTranslate((getWidth()-bounds.width()*scale)/2-bounds.left*scale,(getHeight()-bounds.height()*scale)/2-bounds.top*scale);
                android.graphics.Path fitted=new android.graphics.Path();glyph.transform(matrix,fitted);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(Color.rgb(97,97,97));canvas.drawPath(fitted,paint);paint.setStyle(android.graphics.Paint.Style.FILL);
            }
        };
        number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(number,new FrameLayout.LayoutParams(dp(numberWidth),dp(height)));
        ImageView poster=artwork(item.optString("art"),posterWidth,height);poster.setBackground(bg(PANEL,2));poster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams imageParams=new FrameLayout.LayoutParams(dp(posterWidth),dp(height));imageParams.leftMargin=dp(inset);card.addView(poster,imageParams);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(inset+posterWidth+8),dp(height+8));params.setMargins(0,0,dp(14),dp(12));card.setLayoutParams(params);return card;
    }
    void hero(JSONObject item){
        LinearLayout h=col();h.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(29,68,66),Color.rgb(22,35,46)}));h.setPadding(dp(28),dp(22),dp(28),dp(24));
        h.addView(text("IN EVIDENZA / "+item.optString("duration"),11,MINT));h.addView(title(item.optString("title"),30));
        TextView desc=text(item.optString("description"),14,WHITE);desc.setMaxLines(2);h.addView(desc);space(h,10);h.addView(button("▶ Scopri il film",()->openItem(item)),new LinearLayout.LayoutParams(-2,-2));body.addView(h);space(body,16);
    }
    void bindArtwork(ImageView image,String url,android.graphics.Bitmap bitmap){
        image.setImageBitmap(bitmap);if(image instanceof PosterImageView&&posterBadges!=null)posterBadges.inspect(url,bitmap,(PosterImageView)image);
    }
    ImageView artwork(String url,int width,int height){
        ImageView image=new PosterImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(bg(PANEL,8));image.setClipToOutline(true);image.setLayoutParams(new LinearLayout.LayoutParams(width<0?width:dp(width),dp(height)));image.setTag(url);
        android.graphics.Bitmap cached=artworkCache.get(url);if(cached!=null){bindArtwork(image,url,cached);return image;}if(!url.startsWith("https://"))return image;
        artworkWorker.execute(()->{
            android.graphics.Bitmap ready=artworkCache.get(url);if(ready!=null){main.post(()->{if(alive&&image.isAttachedToWindow())bindArtwork(image,url,ready);});return;}
            try{
                HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(7000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);
                byte[] bytes;
                try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){out.write(b,0,n);if(out.size()>4194304)throw new IOException("Immagine troppo grande");}bytes=out.toByteArray();}finally{connection.disconnect();}
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inSampleSize=1;
                while(options.outWidth/options.inSampleSize>(height>=230?1280:640)||options.outHeight/options.inSampleSize>(height>=230?1280:640))options.inSampleSize*=2;
                options.inJustDecodeBounds=false;android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap!=null){artworkCache.put(url,bitmap);main.post(()->{if(alive&&url.equals(image.getTag())&&image.isAttachedToWindow())bindArtwork(image,url,bitmap);});}
            }catch(Exception ignored){}
        });return image;
    }
    void focusCard(View view,JSONObject item){
        view.setBackground(focus());view.setFocusable(true);view.setClickable(true);view.setContentDescription(item.optString("title"));view.setOnClickListener(v->openItem(item));view.setTag(item);view.setOnLongClickListener(v->{if(item.optString("addonId").equals("plugin.video.nikod.toonitalia")){toonActions(item);return true;}return false;});
        view.setOnFocusChangeListener((v,on)->{float scale=on&&!item.optString("scAction").equals("episode")?1.025f:1f;v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();if(on)focusItem=item.optString("id");});
        if(screen.equals("home")&&!section.equals("Scopri")&&item.optString("id").equals(focusItem))view.post(()->{if(view.isAttachedToWindow())view.requestFocus();});
    }
    android.graphics.drawable.Drawable badgePlaceholder(String name){
        final String[] words=name.trim().split("\\s+");String letters=words.length>1?words[0].substring(0,1)+words[words.length-1].substring(0,1):name.substring(0,Math.min(2,name.length()));final String initials=letters.toUpperCase(Locale.ROOT);
        return new android.graphics.drawable.Drawable(){
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(android.graphics.Canvas canvas){android.graphics.Rect r=getBounds();float x=r.exactCenterX(),y=r.exactCenterY();paint.setColor(Color.rgb(215,227,230));canvas.drawCircle(x,y,Math.min(r.width(),r.height())*.42f,paint);paint.setColor(Color.rgb(35,64,70));paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(android.graphics.Paint.Align.CENTER);paint.setTextSize(Math.min(r.width(),r.height())*.3f);canvas.drawText(initials,x,y-(paint.ascent()+paint.descent())/2,paint);}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }
    View sportsArtwork(JSONObject visual){
        LinearLayout panel=new LinearLayout(this);panel.setGravity(Gravity.CENTER);panel.setPadding(dp(10),dp(10),dp(10),dp(10));panel.setBackground(bg(Color.rgb(241,246,247),8));panel.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(100)));
        JSONArray participants=visual.optJSONArray("participants");if(participants==null)return panel;
        for(int n=0;n<Math.min(2,participants.length());n++){
            JSONObject participant=participants.optJSONObject(n);if(participant==null)continue;
            if(n==1&&visual.optString("kind").equals("match")){TextView versus=text("VS",11,Color.rgb(85,105,112));versus.setGravity(Gravity.CENTER);panel.addView(versus,new LinearLayout.LayoutParams(dp(28),-1));}
            ImageView logo=artwork(participant.optString("badge"),0,76);logo.setBackgroundColor(Color.TRANSPARENT);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription(participant.optString("name"));logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if(logo.getDrawable()==null)logo.setImageDrawable(badgePlaceholder(participant.optString("name","Sport")));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(76),1);lp.setMargins(dp(3),0,dp(3),0);panel.addView(logo,lp);
        }
        return panel;
    }
    View card(JSONObject item){
        LinearLayout c=col();c.setPadding(dp(8),dp(8),dp(8),dp(10));focusCard(c,item);
        boolean poster=posterItem(item);int width=poster?168:208;
        String art=item.optString("art");JSONObject visual=item.optJSONObject("sportsArtwork");if(visual!=null)c.addView(sportsArtwork(visual));else if(art.startsWith("https://"))c.addView(artwork(art,-1,poster?218:100));
        else{TextView icon=title(item.optString("symbol","N"),32);icon.setGravity(Gravity.CENTER);icon.setTextColor(MINT);icon.setBackground(new GradientDrawable(GradientDrawable.Orientation.BL_TR,new int[]{Color.rgb(30,60,64),PANEL}));c.addView(icon,new LinearLayout.LayoutParams(-1,dp(poster?218:100)));}
        space(c,7);TextView name=title(item.optString("title"),15);name.setLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);c.addView(name);TextView metadata=text(item.optString("tag")+(item.optString("year").isEmpty()?"":" · "+item.optString("year")),10,MUTED);metadata.setSingleLine(true);metadata.setEllipsize(android.text.TextUtils.TruncateAt.END);c.addView(metadata);if(section.equals("Cerca")){TextView provider=text(item.optString("searchProvider"),10,MINT);provider.setSingleLine(true);provider.setEllipsize(android.text.TextUtils.TruncateAt.END);c.addView(provider);}
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(width),-2);p.setMargins(0,0,dp(12),dp(12));c.setLayoutParams(p);return c;
    }
    View episodeRow(JSONObject item){
        LinearLayout row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(16),dp(10));focusCard(row,item);
        row.addView(artwork(item.optString("thumb"),150,84));LinearLayout copy=col();copy.setPadding(dp(18),0,dp(16),0);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        copy.addView(title((item.optInt("episodeNumber")>0?String.format(Locale.ROOT,"%02d",item.optInt("episodeNumber"))+"   ":"")+item.optString("episodeName",item.optString("title")),18));TextView plot=text(item.optString("description"),13,MUTED);plot.setMaxLines(2);plot.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(plot);long resume=resumePosition(item);copy.addView(text(item.optString("tag")+(resume>0?" · Riprendi da "+time(resume):""),11,MINT));row.addView(text("▶",22,WHITE));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));row.setLayoutParams(p);return row;
    }
    void seriesBanner(JSONObject item,boolean shortcut){
        LinearLayout panel=new LinearLayout(this);panel.setPadding(dp(18),dp(18),dp(18),dp(18));panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(25,45,53),BG}));
        panel.addView(artwork(item.optString("art"),shortcut?112:96,shortcut?165:142));LinearLayout copy=col();copy.setPadding(dp(24),0,0,0);panel.addView(copy,new LinearLayout.LayoutParams(0,-2,1));copy.addView(text(shortcut?"ESPLORATO DI RECENTE":"SERIE TV · "+item.optString("year"),11,MINT));copy.addView(title(item.optString("title"),32));TextView plot=text(item.optString("description"),14,MUTED);plot.setMaxLines(shortcut?3:2);plot.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(plot);space(copy,10);
        LinearLayout actions=new LinearLayout(this);copy.addView(actions);if(shortcut)actions.addView(button("Scegli una puntata",()->scCatalog(item)));else for(int n=0;n<seriesSeasons.length();n++){JSONObject season=seriesSeasons.optJSONObject(n);if(season==null)continue;TextView tab=button(season.optString("title"),()->scCatalog(season));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.setMargins(0,0,dp(8),0);actions.addView(tab,sp);}
        TextView favorite=button(isFavorite(item)?"✓ Nei preferiti":"＋ Preferiti",()->{toggleFavorite(item);home();});LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,-2);fp.setMargins(dp(10),0,0,0);actions.addView(favorite,fp);
        body.addView(panel);space(body,16);
    }
    JSONArray favorites(){try{return new JSONArray(prefs.getString("favorites","[]"));}catch(Exception e){return new JSONArray();}}
    boolean isFavorite(JSONObject item){if(item.optBoolean("localBrowserSession")&&currentSeries!=null)item=currentSeries;JSONArray saved=favorites();for(int i=0;i<saved.length();i++)if(saved.optJSONObject(i).optString("id").equals(item.optString("id")))return true;return false;}
    void toggleFavorite(JSONObject item){if(item.optBoolean("localBrowserSession")&&currentSeries!=null)item=currentSeries;JSONArray before=favorites(),after=new JSONArray();boolean found=false;for(int i=0;i<before.length();i++){JSONObject entry=before.optJSONObject(i);if(entry.optString("id").equals(item.optString("id"))){found=true;continue;}after.put(entry);}if(!found)try{after.put(StreamingCommunityCatalog.publicMetadata(item));}catch(Exception ignored){}prefs.edit().putString("favorites",after.toString()).apply();}
    void searchCatalog(){searchCatalog("",null);}
    void searchCatalog(String initial){searchCatalog(initial,null);}
    void searchCatalog(String initial,JSONArray preset){
        if(server().isEmpty()){settings();return;}
        if(!screen.equals("search")&&!section.equals("Cerca")){searchBackSection=section;searchBackTitle=catalogTitle;searchBackItems=items;}
        rememberDiscover();screen="search";base();root.setPadding(dp(26),dp(8),dp(26),dp(12));addNavigation("Cerca");
        searchScreen=new SearchScreen(this,initial,preset,this::searchSuggestions,this::unifiedSearch);root.addView(searchScreen.view,new LinearLayout.LayoutParams(-1,0,1));refreshSourceIndex(false,null);
    }
    void cancelSearch(){unifiedSearchBusy=false;cancelJob();if(searchScreen!=null)searchScreen.setSearching(false);}
    void openSearchItem(JSONObject item,String query,JSONArray shown){
        if(unifiedSearchBusy){Toast.makeText(this,"Ricerca in corso: attendi o premi Annulla",Toast.LENGTH_SHORT).show();return;}
        searchLastQuery=query;searchLastResults=shown;String remembered=SearchMemory.clean(item.optString("title",query));
        JSONArray recent;try{recent=new JSONArray(prefs.getString("recentSearches","[]"));}catch(Exception ignored){recent=new JSONArray();}prefs.edit().putString("recentSearches",SearchMemory.remember(recent,remembered).toString()).apply();
        items=shown;section="Cerca";catalogTitle="Risultati per “"+query+"”";openItem(item);
    }
    void searchItemActions(JSONObject item,String query,JSONArray shown){
        if(unifiedSearchBusy)return;searchLastQuery=query;searchLastResults=shown;items=shown;section="Cerca";catalogTitle="Risultati per “"+query+"”";toonActions(item);
    }
    void searchSuggestions(String query,java.util.function.BiConsumer<JSONArray,Boolean> complete){
        final String origin=providerOrigin(),ua=WebSettings.getDefaultUserAgent(this);
        if(origin.isEmpty()){complete.accept(new JSONArray(),true);return;}
        worker.execute(()->{JSONArray found=new JSONArray();boolean failed=false;try{JSONObject props=scPage(origin+"/it/search?q="+Uri.encode(query),ua);found=StreamingCommunityCatalog.search(props,props.optString("_sourceOrigin",origin),query);}catch(Exception ignored){failed=true;}
            final JSONArray ready=found;final boolean unavailable=failed;main.post(()->{if(alive)complete.accept(ready,unavailable);});
        });
    }
    void unifiedSearch(String raw){
        final String query=SearchMemory.clean(raw);if(query.isEmpty())return;if(unifiedSearchBusy||!currentJob.isEmpty()){Toast.makeText(this,"Attendi il completamento dell’operazione in corso",Toast.LENGTH_SHORT).show();return;}
        JSONArray recent;try{recent=new JSONArray(prefs.getString("recentSearches","[]"));}catch(Exception ignored){recent=new JSONArray();}
        prefs.edit().putString("recentSearches",SearchMemory.remember(recent,query).toString()).putString("lastSearchQuery",query).apply();final SearchScreen source=searchScreen;
        withSourceIndex(()->{if(source==null||source==searchScreen)unifiedSearchResolved(query);});
    }
    void unifiedSearchResolved(String query){
        if(!currentJob.isEmpty())return;if(searchScreen==null)searchCatalog(query);
        final SearchScreen target=searchScreen;final long request=++generation;final String origin=providerOrigin(),ua=WebSettings.getDefaultUserAgent(this);
        final JSONArray[] results={new JSONArray(),new JSONArray()};final String[] errors={"",""};final int[] pending={2};
        unifiedSearchBusy=true;target.setSearching(true);
        for(int provider=0;provider<2;provider++){
            final int index=provider;
            worker.execute(()->{
                JSONArray found=new JSONArray();String failure="";
                try{
                    if(index==0){if(origin.isEmpty())throw new IOException("Dominio non disponibile");JSONObject page=scPage(origin+"/it/search?q="+Uri.encode(query),ua);found=StreamingCommunityCatalog.search(page,page.optString("_sourceOrigin",origin),query);}
                    else found=toonSearch(query,request);
                }catch(Exception e){failure=index==0?"StreamingCommunity non disponibile. Riprova tra poco.":"ToonItalia non disponibile. Riprova tra poco.";}
                final JSONArray ready=found;final String error=failure;
                main.post(()->{
                    if(!alive||generation!=request||searchScreen!=target)return;results[index]=ready;errors[index]=error;if(--pending[0]!=0)return;unifiedSearchBusy=false;
                    try{
                        JSONArray combined=UnifiedSearch.combine(results[0],results[1]);JSONArray cached;try{cached=new JSONArray(prefs.getString("searchTitles","[]"));}catch(Exception ignored){cached=new JSONArray();}prefs.edit().putString("searchTitles",SearchMemory.cacheTitles(cached,combined).toString()).apply();
                        for(String problem:errors)if(!problem.isEmpty())combined.put(new JSONObject().put("title",problem).put("isNotice",true));
                        searchLastQuery=query;searchLastResults=combined;target.showResults(query,combined);
                    }catch(Exception e){target.setSearching(false);Toast.makeText(this,"Non riesco a mostrare i risultati",Toast.LENGTH_SHORT).show();}
                });
            });
        }
    }
    JSONArray toonSearch(String query,long request)throws Exception {
        String job="";
        try{
            if(!alive||generation!=request)throw new IOException("Ricerca annullata");
            job=api("/v1/actions",new JSONObject().put("url","plugin://plugin.video.nikod.toonitalia/?action=search_results&q="+Uri.encode(query))).getString("job");
            if(!alive||generation!=request)throw new IOException("Ricerca annullata");
            currentJob=job;long deadline=System.currentTimeMillis()+60000;
            while(alive&&generation==request&&System.currentTimeMillis()<deadline){
                JSONObject state=api("/v1/jobs/"+job,null);String phase=state.optString("status");
                if(phase.equals("complete")){
                    JSONObject result=state.getJSONObject("result");if(!result.optString("kind").equals("directory"))throw new IOException("Risposta inattesa");
                    JSONArray list=result.optJSONArray("items");return list==null?new JSONArray():list;
                }
                if(phase.equals("error")||phase.equals("cancelled")||phase.equals("dialog"))throw new IOException("Ricerca non disponibile");
                Thread.sleep(400);
            }
            throw new IOException("Ricerca scaduta");
        }finally{if(!job.isEmpty()){cancelRemote(job);if(currentJob.equals(job))currentJob="";}}
    }
    void openItem(JSONObject item){rememberDiscover();focusItem=item.optString("id");if(item.optBoolean("isNotice")){new AlertDialog.Builder(this).setTitle(item.optString("title")).setMessage(item.optString("description")).setPositiveButton("OK",null).show();return;}if(item.optString("addonId").equals("plugin.video.nikod.toonitalia")&&item.optBoolean("isPlayable")){chooseEpisode(item);return;}if(item.optString("scAction").equals("episode")){chooseEpisode(item);return;}if(!item.optString("scAction").isEmpty()||isScSearch(item)){scCatalog(item);return;}if(item.optBoolean("isWeb")){openSourcePage(item);return;}if((item.optString("url").startsWith("plugin://")||item.optString("url").startsWith("/v1/route/"))){action(item);return;}details(item);}
    boolean posterItem(JSONObject item){return item.optString("scAction").equals("title")||(item.optString("addonId").equals("plugin.video.nikod.toonitalia")&&item.optString("action").equals("title"));}
    boolean toonEpisodes(){for(int n=0;n<items.length();n++){JSONObject item=items.optJSONObject(n);if(item!=null&&item.optString("addonId").equals("plugin.video.nikod.toonitalia")&&item.optBoolean("isPlayable"))return true;}return false;}
    void showAddons(){
        if(server().isEmpty()){section="Catalogo";localCatalog();home();return;}
        history.clear();titles.clear();historySections.clear();items=new JSONArray();
        try{items.put(new JSONObject().put("id","addon-s4me").put("title","Stream4me").put("url","plugin://plugin.video.s4me/").put("isPlugin",true).put("isFolder",true).put("symbol","S").put("tag","FILM, SERIE E DIRETTE"));items.put(new JSONObject().put("id","addon-toonitalia").put("title","ToonItalia").put("url","plugin://plugin.video.nikod.toonitalia/").put("isPlugin",true).put("isFolder",true).put("symbol","T").put("tag","ANIME, ANIMAZIONE E SERIE TV"));items.put(new JSONObject().put("id","addon-programfeed").put("title","Program Feed").put("url","plugin://plugin.video.nikod.programfeed/").put("isPlugin",true).put("isFolder",true).put("symbol","P").put("tag","PROGRAMMI E CANALI IN DIRETTA"));}catch(Exception ignored){}
        section="Catalogo";catalogTitle="I tuoi addon";focusItem="";home();
    }
    void toonFailure(JSONObject item,String message){new AlertDialog.Builder(this).setTitle("Sorgente ToonItalia non disponibile").setMessage(message).setPositiveButton("Scegli sorgente",(d,w)->toonActions(item)).setNegativeButton("Catalogo",null).show();}
    void toonActions(JSONObject item){
        JSONArray sources=item.optJSONArray("sources");ArrayList<String> labels=new ArrayList<>();labels.add(item.optBoolean("isPlayable")?"Guarda episodio · Automatico":"Apri");
        if(sources!=null)for(int n=0;n<sources.length();n++){String name=sources.optJSONObject(n).optString("name");int total=0,number=0;for(int k=0;k<sources.length();k++)if(sources.optJSONObject(k).optString("name").equals(name)){total++;if(k<=n)number++;}labels.add("Riproduci con "+name+(total>1?" · "+number:""));}
        labels.add(isFavorite(item)?"Rimuovi dai preferiti":"Aggiungi ai preferiti");
        new AlertDialog.Builder(this).setTitle(item.optString("title")).setItems(labels.toArray(new String[0]),(dialog,index)->{
            if(index==0){openItem(item);return;}if(index==labels.size()-1){toggleFavorite(item);Toast.makeText(this,isFavorite(item)?"Aggiunto ai preferiti":"Rimosso dai preferiti",Toast.LENGTH_SHORT).show();if(section.equals("Preferiti"))home();return;}
            try{JSONObject request=new JSONObject(item.toString()).put("url",sources.getJSONObject(index-1).getString("url"));long position=resumePosition(item);request.put("resumeAt",position);action(request);}catch(Exception e){showError("Sorgente non disponibile.");}
        }).setNegativeButton("Annulla",null).show();
    }
    boolean isScSearch(JSONObject item){
        Uri u=Uri.parse(item.optString("url"));return item.optBoolean("isWeb")&&sourceHost(u.getHost())&&u.getPath()!=null&&u.getPath().endsWith("/search");
    }
    JSONObject scPage(String url,String ua)throws Exception {
        try{return scPageRequest(url,ua);}
        catch(Exception original){
            JSONObject index;try{index=api("/v1/sources/stream4me?refresh=1",null);}catch(Exception unavailable){throw original;}
            String origin=SourceDomains.origin(index.optString("streamingCommunity")),updated=SourceDomains.rebase(url,origin);
            if(origin.isEmpty()||updated.equals(url))throw original;
            main.post(()->{if(alive)try{applySourceIndex(index);sourceIndexUpdated=System.currentTimeMillis();sourceIndexWarning=index.optBoolean("usingLastValid");}catch(Exception ignored){}});
            return scPageRequest(updated,ua);
        }
    }
    JSONObject scPageRequest(String url,String ua)throws Exception {
        BrowserSessionDataSource data=new BrowserSessionDataSource(ua,Collections.singletonMap("Accept","text/html"),Uri.parse(url).getHost());
        try{
            data.open(new androidx.media3.datasource.DataSpec(Uri.parse(url)));ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;
            while((n=data.read(bytes,0,bytes.length))!=-1){out.write(bytes,0,n);if(out.size()>2097152)throw new IOException("Catalogo troppo grande");}
            return StreamingCommunityCatalog.props(out.toString("UTF-8")).put("_sourceOrigin","https://"+Uri.parse(url).getHost());
        }finally{data.close();}
    }
    void scCatalog(JSONObject item){withSourceIndex(()->scCatalogResolved(currentSource(item)));}
    void scCatalogResolved(JSONObject item){
        final String url=item.optString("url"),ua=WebSettings.getDefaultUserAgent(this);Uri uri=Uri.parse(url);
        if(!"https".equals(uri.getScheme())||!sourceHost(uri.getHost())){showError("Catalogo non consentito");return;}
        final boolean replaceSeason=item.optString("scAction").equals("season")&&items.length()>0&&items.optJSONObject(0).optString("scAction").equals("episode");
        final long request=++generation;screen="loading";base();root.addView(title(item.optString("title","StreamingCommunity"),26));root.addView(text("Caricamento…",16,MUTED));root.addView(new ProgressBar(this));root.addView(button("Annulla",()->{generation++;home();}));
        worker.execute(()->{
            try{
                JSONObject props=scPage(url,ua);String origin=props.optString("_sourceOrigin","https://"+uri.getHost()),action=item.optString("scAction");
                if(action.equals("episode")){
                    StreamingCommunityCatalog.verifyEpisode(props,item.getLong("scTitleId"),item.getLong("scEpisodeId"));
                    JSONObject title=props.getJSONObject("title"),episode=props.getJSONObject("episode");
                    int season=episode.getJSONObject("season").getInt("number");String titleUrl=origin+"/it/titles/"+title.getLong("id")+"-"+title.getString("slug");
                    JSONObject seasonProps=scPage(titleUrl+"/season-"+season,ua);
                    JSONArray directory=StreamingCommunityCatalog.episodes(seasonProps,origin),seasons=StreamingCommunityCatalog.seasons(seasonProps,origin);
                    JSONObject prepared=new JSONObject(item.toString()).put("seriesTitle",title.getString("name")).put("seasonNumber",season).put("episodeNumber",episode.getInt("number"));
                    JSONObject next=PlaybackHistory.next(prepared,directory);
                    if(next==null){
                        JSONObject following=null;for(int n=0;n<seasons.length();n++){JSONObject candidate=seasons.getJSONObject(n);if(candidate.optInt("seasonNumber")>season&&(following==null||candidate.optInt("seasonNumber")<following.optInt("seasonNumber")))following=candidate;}
                        if(following!=null)try{next=PlaybackHistory.next(prepared,StreamingCommunityCatalog.episodes(scPage(following.getString("url"),ua),origin));}catch(Exception ignored){android.util.Log.i("NicoStreamPlayback","SC_NEXT_UNAVAILABLE");}
                    }
                    if(next!=null)prepared.put("nextEpisode",next);
                    JSONObject series=StreamingCommunityCatalog.decorate(StreamingCommunityCatalog.entry(title.getString("name"),titleUrl,"title"),seasonProps.getJSONObject("title"),seasonProps);
                    final JSONObject resolved=prepared;
                    runOnUiThread(()->{if(!alive||generation!=request)return;
                        boolean same=items.length()>0&&items.optJSONObject(0)!=null&&items.optJSONObject(0).optLong("scTitleId")==resolved.optLong("scTitleId")&&items.optJSONObject(0).optInt("seasonNumber")==season&&items.optJSONObject(0).optString("scAction").equals("episode");
                        if(!same){pushHistory();items=directory;catalogTitle=title.optString("name")+" · Stagione "+season;}
                        currentSeries=series;seriesSeasons=seasons;knownSeries.put(resolved.optLong("scTitleId"),series);knownSeasons.put(resolved.optLong("scTitleId"),seasons);section="Catalogo";focusItem=resolved.optString("id");
                        openSourcePage(resolved,true);
                    });return;
                }
                JSONArray list;
                if(isScSearch(item))list=StreamingCommunityCatalog.search(props,origin,uri.getQueryParameter("q")==null?"":uri.getQueryParameter("q"));
                else if(action.equals("collection"))list=StreamingCommunityCatalog.titles(props,origin);
                else if(action.equals("season"))list=StreamingCommunityCatalog.episodes(props,origin);
                else if(props.getJSONObject("title").optString("type").equals("movie")){
                    JSONObject movie=new JSONObject(item.toString()).put("scAction","movie").put("url",origin+"/it/watch/"+props.getJSONObject("title").getLong("id"));
                    runOnUiThread(()->{if(alive&&generation==request)openSourcePage(movie,true);});return;
                }else list=StreamingCommunityCatalog.seasons(props,origin);
                runOnUiThread(()->{if(!alive||generation!=request)return;if(action.equals("title")){try{currentSeries=StreamingCommunityCatalog.decorate(currentSource(item),props.getJSONObject("title"),props);seriesSeasons=list;long seriesId=props.getJSONObject("title").getLong("id");knownSeries.put(seriesId,currentSeries);knownSeasons.put(seriesId,list);prefs.edit().putString("scLastTitle",safeMetadata(currentSeries)).apply();}catch(Exception ignored){}}if(!replaceSeason){pushHistory();}focusItem="";items=list;catalogTitle=isScSearch(item)?item.optString("title","StreamingCommunity"):action.equals("season")&&currentSeries!=null?currentSeries.optString("title")+" · "+item.optString("title"):item.optString("title");section="Catalogo";home();});
            }catch(Exception e){runOnUiThread(()->{if(!alive||generation!=request)return;home();new AlertDialog.Builder(this).setTitle("Catalogo non disponibile").setMessage(e.getMessage()).setPositiveButton("Apri pagina",(d,w)->openSourcePage(item)).setNegativeButton("Chiudi",null).show();});}
        });
    }
    void details(JSONObject item){
        selected=item;screen="details";base();ScrollView scroll=new ScrollView(this);LinearLayout content=col();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(button("‹ Torna al catalogo",this::home),new LinearLayout.LayoutParams(-2,-2));space(content,28);
        LinearLayout layout=new LinearLayout(this);layout.setGravity(Gravity.TOP);content.addView(layout);
        String art=item.optString("art");if(art.startsWith("https://"))layout.addView(artwork(art,185,278));
        LinearLayout copy=col();copy.setPadding(dp(30),dp(8),dp(24),dp(24));layout.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView eyebrow=text(item.optString("tag")+(item.optString("year").isEmpty()?"":" · "+item.optString("year")),12,MINT);eyebrow.setLetterSpacing(.08f);copy.addView(eyebrow);space(copy,8);
        copy.addView(title(item.optString("title"),38));space(copy,12);TextView description=text(item.optString("description"),16,MUTED);description.setMaxWidth(dp(650));copy.addView(description);space(copy,24);
        LinearLayout actions=new LinearLayout(this);copy.addView(actions);TextView play=button("▶ Guarda ora",()->play(item));actions.addView(play,new LinearLayout.LayoutParams(-2,-2));
        TextView favorite=button(isFavorite(item)?"✓ Nella mia lista":"＋ La mia lista",()->{toggleFavorite(item);details(item);});LinearLayout.LayoutParams favoriteParams=new LinearLayout.LayoutParams(-2,-2);favoriteParams.leftMargin=dp(12);actions.addView(favorite,favoriteParams);play.requestFocus();
    }
    void action(JSONObject item){action(item,false);}
    void action(JSONObject item,boolean isRoot){
        if(!currentJob.isEmpty())return;
        final long request=++generation;
        screen="loading";base();LinearLayout loading=col();loading.setGravity(Gravity.CENTER);root.addView(loading,new LinearLayout.LayoutParams(-1,0,1));
        loading.addView(text("NICOSTREAM",12,MINT));space(loading,16);loading.addView(title(item.optString("title","Preparazione del catalogo"),28));space(loading,20);
        ProgressBar spinner=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);spinner.setIndeterminate(true);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(MINT));loading.addView(spinner,new LinearLayout.LayoutParams(dp(220),dp(3)));space(loading,14);loading.addView(text("Un momento, stiamo preparando i contenuti",14,MUTED));space(loading,24);loading.addView(button("Annulla",()->{cancelJob();home();}),new LinearLayout.LayoutParams(-2,-2));
        worker.execute(()->{
            String job="";String lastDialog="";
            try{
                job=api("/v1/actions",new JSONObject().put("url",item.optString("url"))).getString("job");
                if(!alive||generation!=request){cancelRemote(job);return;}
                currentJob=job;
                long deadline=System.currentTimeMillis()+300000;
                while(alive&&generation==request&&System.currentTimeMillis()<deadline){
                    JSONObject state=api("/v1/jobs/"+job,null);String phase=state.optString("status");
                    if(phase.equals("dialog")){
                        JSONObject dialog=state.getJSONObject("dialog");String id=dialog.getString("id");
                        if(!id.equals(lastDialog)){lastDialog=id;final String active=job;runOnUiThread(()->{if(alive&&generation==request)showRemoteDialog(active,dialog,request);});}
                    }else if(phase.equals("complete")){
                        JSONObject result=state.getJSONObject("result");
                        runOnUiThread(()->{
                            if(!alive||generation!=request)return;currentJob="";dismissDialog();if(item.optString("url").contains("android_settings=1")){programHomeUpdated=0;programHomeFeed=null;programHomeRevision++;}
                            if(result.optString("kind").equals("playback")){
                                try{JSONObject media=new JSONObject(item.toString());media.put("url",result.getString("url"));media.put("mime",result.optString("mime"));media.put("headers",result.optJSONObject("headers"));media.put("subtitles",result.optJSONArray("subtitles"));media.put("drm",result.optJSONObject("drm"));media.put("isPlugin",false);media.put("resumeSource",PlaybackHistory.source(item));if(item.optString("addonId").equals("plugin.video.nikod.toonitalia")){JSONObject next=PlaybackHistory.next(item,items);if(next!=null)media.put("nextEpisode",next);}if(item.has("resumeAt"))media.put("resumeAt",item.getLong("resumeAt"));play(media);}catch(Exception e){showError(e.getMessage());home();}
                            }else{
                                if(isRoot){history.clear();titles.clear();historySections.clear();}else{pushHistory();}
                                items=result.optJSONArray("items");if(items==null)items=new JSONArray();catalogTitle=isRoot?"Stream4me":item.optString("title","Stream4me");if(isRoot)addonRoot=items;section=isRoot?"Scopri":"Catalogo";focusItem="";home();
                            }
                        });return;
                    }else if(phase.equals("error"))throw new IOException(state.optString("error","Errore addon"));
                    else if(phase.equals("cancelled")){runOnUiThread(()->{if(generation==request){currentJob="";dismissDialog();home();}});return;}
                    Thread.sleep(600);
                }
                cancelRemote(job);
                if(alive&&generation==request)throw new IOException("L’addon ha impiegato troppo tempo. Riprova o scegli un’altra sorgente.");
            }catch(Exception e){
                final String message=e.getMessage();runOnUiThread(()->{if(alive&&generation==request){currentJob="";dismissDialog();home();if(item.optString("addonId").equals("plugin.video.nikod.toonitalia")&&item.optBoolean("isPlayable"))toonFailure(item,message);else showError(message);}});
            }
        });
    }
    void dismissDialog(){if(activeDialog!=null){activeDialog.setOnCancelListener(null);activeDialog.dismiss();activeDialog=null;}}
    void showRemoteDialog(String job,JSONObject dialog,long request){
        dismissDialog();String type=dialog.optString("type"),id=dialog.optString("id");
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(dialog.optString("title","Stream4me"));
        String message=dialog.optString("message");if(!message.isEmpty())b.setMessage(message);
        if(type.equals("input")){
            EditText input=new EditText(this);input.setSingleLine(true);input.setText(dialog.optString("default"));if(dialog.optBoolean("hidden"))input.setInputType(129);b.setView(input);
            b.setPositiveButton("Conferma",(d,w)->answer(job,id,input.getText().toString(),false));
            b.setNegativeButton("Annulla",(d,w)->answer(job,id,null,true));
        }else if(type.equals("select")||type.equals("multiselect")){
            JSONArray values=dialog.optJSONArray("options");String[] labels=new String[values==null?0:values.length()];for(int i=0;i<labels.length;i++)labels[i]=values.optString(i);
            if(type.equals("multiselect")){
                boolean[] checked=new boolean[labels.length];b.setMultiChoiceItems(labels,checked,(d,i,on)->checked[i]=on);b.setPositiveButton("Conferma",(d,w)->{JSONArray choices=new JSONArray();for(int i=0;i<checked.length;i++)if(checked[i])choices.put(i);answer(job,id,choices,false);});
            }else b.setItems(labels,(d,index)->answer(job,id,index,false));
            b.setNegativeButton("Annulla",(d,w)->answer(job,id,null,true));
        }else{
            b.setPositiveButton(type.equals("yesno")?"Sì":"OK",(d,w)->answer(job,id,true,false));
            if(type.equals("yesno"))b.setNegativeButton("No",(d,w)->answer(job,id,false,false));
        }
        b.setOnCancelListener(d->answer(job,id,null,true));activeDialog=b.create();activeDialog.show();
    }
    void answer(String job,String id,Object value,boolean cancelled){
        worker.execute(()->{try{api("/v1/jobs/"+job+"/answer",new JSONObject().put("id",id).put("value",value==null?JSONObject.NULL:value).put("cancelled",cancelled));}catch(Exception e){runOnUiThread(()->{if(alive)showError("Risposta non inviata. Verifica il collegamento alla VPS.");});}});
    }
    void cancelRemote(String job){if(job.isEmpty())return;try{api("/v1/jobs/"+job+"/cancel",new JSONObject().put("cancel",true));}catch(Exception ignored){}}
    void cancelJob(){generation++;String job=currentJob;currentJob="";dismissDialog();if(!job.isEmpty())worker.execute(()->cancelRemote(job));}
    void showError(String message){new AlertDialog.Builder(this).setTitle("Operazione non riuscita").setMessage(message==null?"Riprova dal catalogo.":message).setPositiveButton("OK",null).show();}
    void rememberDiscover(){if(section.equals("Scopri")&&discoverScroll!=null)discoverScrollY=discoverScroll.getScrollY();}
    void pushHistory(){rememberDiscover();history.push(items);titles.push(catalogTitle);historySections.push(section);}
    void previous(){if(!history.isEmpty()){items=history.pop();catalogTitle=titles.pop();section=historySections.isEmpty()?"Catalogo":historySections.pop();}focusItem="";home();}

    JSONArray playbackHistory(){try{return new JSONArray(prefs.getString("playbackHistory","[]"));}catch(Exception e){return new JSONArray();}}
    long resumePosition(JSONObject source){if(source==null)return 0;JSONObject entry=PlaybackHistory.find(playbackHistory(),source);return entry==null||entry.optBoolean("completed")?0:entry.optLong("position");}
    void savePlayback(boolean durable){
        if(player==null||playingSource==null||playingSource.optBoolean("isLive")||!playingStarted||player.getDuration()<=0||player.isCurrentMediaItemLive())return;
        try{JSONArray updated=PlaybackHistory.update(playbackHistory(),playingSource,player.getCurrentPosition(),player.getDuration(),player.getPlaybackState()==Player.STATE_ENDED,System.currentTimeMillis());android.content.SharedPreferences.Editor edit=prefs.edit().putString("playbackHistory",updated.toString());if(durable)edit.commit();else edit.apply();lastProgressSave=System.currentTimeMillis();}catch(Exception ignored){}
    }
    void chooseEpisode(JSONObject source){
        long position=resumePosition(source);if(position<=0){resumeSource(source,0);return;}
        new AlertDialog.Builder(this).setTitle(source.optString("episodeName",source.optString("title")))
            .setMessage("Vuoi continuare da "+time(position)+"?")
            .setPositiveButton("Riprendi",(d,w)->resumeSource(source,position))
            .setNeutralButton("Dall’inizio",(d,w)->resumeSource(source,0)).setNegativeButton("Annulla",null).show();
    }
    void resumeSource(JSONObject source,long position){
        try{JSONObject request=new JSONObject(source.toString()).put("resumeAt",position);if(!request.optString("scAction").isEmpty())scCatalog(request);else if(request.optString("url").startsWith("plugin://")||request.optString("url").startsWith("/v1/route/"))action(request);else play(request);}catch(Exception e){showError("Non riesco a riaprire questo titolo.");}
    }
    void startNext(JSONObject next){
        if(next==null)return;savePlayback(true);
        try{JSONObject fresh=PlaybackHistory.source(next).put("resumeAt",0);android.util.Log.i("NicoStreamPlayback","NEXT_EPISODE id="+fresh.optLong("scEpisodeId"));resumeSource(fresh,0);prefs.edit().putString("playbackHistory",PlaybackHistory.update(playbackHistory(),fresh,0,0,false,System.currentTimeMillis()).toString()).apply();}catch(Exception e){home();showError("La puntata successiva non è disponibile.");}
    }
    void continueShelf(){
        JSONArray entries=PlaybackHistory.continuing(playbackHistory());if(entries.length()==0)return;
        body.addView(title("Continua a guardare",22));space(body,8);HorizontalScrollView scroll=tvShelf();scroll.setHorizontalScrollBarEnabled(false);LinearLayout shelf=new LinearLayout(this);shelf.setClipChildren(false);shelf.setClipToPadding(false);
        for(int i=0;i<entries.length();i++){
            JSONObject entry=entries.optJSONObject(i),source=entry.optJSONObject("source");if(source==null)continue;long position=entry.optLong("position"),duration=entry.optLong("duration");
            LinearLayout card=col();card.setPadding(dp(10),dp(10),dp(10),dp(10));card.setBackground(focus());card.setFocusable(true);card.setClickable(true);card.setContentDescription("Riprendi "+source.optString("title"));card.setOnClickListener(v->resumeSource(source,position));
            String image=source.optString("thumb");if(image.isEmpty())image=source.optString("art");card.addView(artwork(image,-1,92));TextView name=title(source.optString("seriesTitle",source.optString("title")),16);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name);
            if(source.optInt("episodeNumber")>0)card.addView(text("Stagione "+source.optInt("seasonNumber")+" · Episodio "+source.optInt("episodeNumber"),12,MUTED));
            ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));progress.setMax(1000);progress.setProgress(duration>0?(int)(position*1000/duration):0);card.addView(progress,new LinearLayout.LayoutParams(-1,dp(5)));card.addView(text(position>0?"▶ Riprendi da "+time(position):"▶ Guarda ora",12,MINT));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(260),-2);cp.setMargins(0,0,dp(14),0);shelf.addView(card,cp);
        }scroll.addView(shelf);body.addView(scroll);space(body,20);
    }
    void play(JSONObject item){
        screen="player";base();selected=item;playingSource=item.optJSONObject("resumeSource");if(playingSource==null&&!item.optBoolean("localBrowserSession"))try{playingSource=PlaybackHistory.source(item);}catch(Exception ignored){}lastProgressSave=0;root.setPadding(0,0,0,0);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String url=item.optString("url");if(url.startsWith("/v1/media/"))url=server()+url;
        if(!(url.startsWith("https://")||url.startsWith("http://127.0.0.1:"))){home();showError("Protocollo video non supportato");return;}
        FrameLayout frame=new FrameLayout(this);root.addView(frame,new LinearLayout.LayoutParams(-1,-1));
        SurfaceView surface=new SurfaceView(this);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        TextView loading=text("Caricamento di "+item.optString("title")+"…",18,WHITE);loading.setGravity(Gravity.CENTER);frame.addView(loading,new FrameLayout.LayoutParams(-1,-1));
        transport=col();transport.setPadding(dp(20),dp(10),dp(20),dp(12));transport.setBackgroundColor(Color.argb(235,9,17,24));transport.addView(title(item.optString("title"),18));
        clock=text("0:00",12,MUTED);transport.addView(clock);seek=new SeekBar(this);seek.setId(View.generateViewId());seek.setFocusable(true);seek.setContentDescription("Posizione nel video. Sinistra e destra spostano di 10 secondi.");seek.setMax(1000);transport.addView(seek);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        controls.addView(button("‹ Catalogo",this::home));controls.addView(button("−10 s",()->{seekBy(-10000);}));
        pauseButton=button("Ⅱ Pausa",this::togglePlay);controls.addView(pauseButton);
        controls.addView(button("+10 s",()->{seekBy(10000);}));controls.addView(button("Audio / CC",this::tracks));if(item.optJSONObject("nextEpisode")!=null)controls.addView(button("Successiva ▶",()->startNext(item.optJSONObject("nextEpisode"))));if(item.optBoolean("localBrowserSession"))controls.addView(button("Player web",()->returnToSource(item)));transport.addView(controls);
        for(int n=0;n<controls.getChildCount();n++)controls.getChildAt(n).setId(View.generateViewId());
        for(int n=0;n<controls.getChildCount();n++){
            View control=controls.getChildAt(n);control.setNextFocusLeftId(controls.getChildAt(Math.max(0,n-1)).getId());control.setNextFocusRightId(controls.getChildAt(Math.min(controls.getChildCount()-1,n+1)).getId());control.setNextFocusUpId(seek.getId());control.setNextFocusDownId(control.getId());
        }
        seek.setNextFocusDownId(pauseButton.getId());seek.setNextFocusUpId(seek.getId());
        transport.addView(text("↑ Barra temporale · ← → Scegli comando · OK Conferma",11,MUTED));
        frame.addView(transport,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){seeking=true;}public void onStopTrackingTouch(SeekBar s){if(player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*s.getProgress()/1000);seeking=false;showControls();}public void onProgressChanged(SeekBar s,int p,boolean user){}});
        ExoPlayer.Builder builder=new ExoPlayer.Builder(this);
        if(item.optString("url").startsWith("/v1/media/")&&!prefs.getString("serverKey","").isEmpty()){
            final String endpoint=server(),key=prefs.getString("serverKey","");
            builder.setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,()->new BrowserSessionDataSource(endpoint,key))));
        }
        if(item.optBoolean("localBrowserSession")){
            Map<String,String> captured=new HashMap<>();JSONObject h=item.optJSONObject("browserHeaders");
            if(h!=null){Iterator<String> keys=h.keys();while(keys.hasNext()){String k=keys.next();captured.put(k,h.optString(k));}}
            final String browserUa=item.optString("browserIdentity"), mediaHost=Uri.parse(url).getHost();
            builder.setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,()->new BrowserSessionDataSource(browserUa,captured,mediaHost))));
        }
        player=builder.build();final ExoPlayer current=player;player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){if(player!=current)return;playingStarted=true;savePlayback(false);loading.setVisibility(View.GONE);android.util.Log.i("NicoStreamPlayback","FIRST_FRAME");}
            @Override public void onVideoSizeChanged(VideoSize size){frame.post(()->{if(player!=current||size.height==0)return;float aspect=(float)size.width*size.pixelWidthHeightRatio/size.height;int w=frame.getWidth(),h=frame.getHeight();if(w==0||h==0)return;int vw=Math.min(w,(int)(h*aspect)),vh=(int)(vw/aspect);surface.setLayoutParams(new FrameLayout.LayoutParams(vw,vh,Gravity.CENTER));});}
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(player==current&&playingStarted)savePlayback(false);}
            @Override public void onIsPlayingChanged(boolean isPlaying){if(player==current&&!isPlaying)savePlayback(false);}
            @Override public void onPlaybackStateChanged(int state){if(player!=current)return;if(state==Player.STATE_ENDED){savePlayback(true);JSONObject next=item.optJSONObject("nextEpisode");if(next!=null)startNext(next);else home();}else if(state==Player.STATE_BUFFERING)loading.setVisibility(View.VISIBLE);else if(state==Player.STATE_READY&&current.getCurrentPosition()>0)loading.setVisibility(View.GONE);}
            @Override public void onPlayerError(PlaybackException e){
                if(player!=current)return;android.util.Log.e("NicoStreamPlayback",e.getErrorCodeName(),e);
                if(item.optString("addonId").equals("plugin.video.nikod.toonitalia")){
                    JSONObject source=item.optJSONObject("resumeSource");home();toonFailure(source==null?item:source,"La sorgente non ha accettato la riproduzione. Puoi provare un altro server dell’episodio.");
                }else if(item.optBoolean("localBrowserSession")){
                    details(item);new AlertDialog.Builder(MainActivity.this).setTitle("Passaggio al player non riuscito")
                        .setMessage("La sorgente non ha accettato la riproduzione nativa. Puoi continuare nel player web della puntata.")
                        .setPositiveButton("Player web",(d,w)->returnToSource(item)).setNegativeButton("Catalogo",(d,w)->home()).show();
                }else{details(item);showError("Sorgente non riproducibile ("+e.getErrorCodeName()+"). Torna al catalogo e prova un’altra sorgente.");}
            }
        });
        surface.setOnClickListener(v->showControls());surface.setFocusable(false);
        MediaItem.Builder media=new MediaItem.Builder().setUri(url);String mime=item.optString("mime");if(!mime.isEmpty())media.setMimeType(mime);
        JSONObject drm=item.optJSONObject("drm");if(drm!=null&&drm.optString("scheme").equals("widevine")){
            if(!android.media.MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)){details(item);showError("Questo emulatore non dispone di Widevine. Questa sorgente richiede un dispositivo Android compatibile.");return;}
            String license=drm.optString("licenseUrl");if(license.startsWith("/v1/license/"))license=server()+license;
            media.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(license).setLicenseRequestHeaders(EngineAccess.sameOrigin(server(),license)?Collections.singletonMap("Authorization",EngineAccess.authorization(server(),license,prefs.getString("serverKey",""))):Collections.emptyMap()).setForceDefaultLicenseUri(true).setMultiSession(true).build());
        }
        JSONArray subs=item.optJSONArray("subtitles");if(subs!=null){ArrayList<MediaItem.SubtitleConfiguration> configs=new ArrayList<>();for(int i=0;i<subs.length();i++){String sub=subs.optString(i);if(sub.startsWith("/v1/media/"))sub=server()+sub;if(sub.startsWith("https://")||sub.startsWith("http://127.0.0.1:"))configs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub)).setMimeType(sub.contains(".vtt")?MimeTypes.TEXT_VTT:MimeTypes.APPLICATION_SUBRIP).setLabel("Sottotitoli "+(i+1)).build());}media.setSubtitleConfigurations(configs);}
        long resume=item.optBoolean("isLive")?0:item.optLong("resumeAt",resumePosition(playingSource));if(item.optBoolean("isLive"))player.setMediaItem(media.build());else player.setMediaItem(media.build(),Math.max(0,resume));if(resume>0)android.util.Log.i("NicoStreamPlayback","RESUME_REQUEST_MS="+resume);player.prepare();player.play();showControls();pauseButton.requestFocus();
        final boolean[] playbackVerified={false};
        Runnable tick=new Runnable(){public void run(){if(!alive||player!=current)return;long duration=current.getDuration(),pos=current.getCurrentPosition();if(playingStarted&&System.currentTimeMillis()-lastProgressSave>=5000)savePlayback(false);if(playingStarted&&current.getVideoFormat()!=null&&pos>=5000&&!playbackVerified[0]){playbackVerified[0]=true;android.util.Log.i("NicoStreamPlayback","PROGRESS_MS="+pos+" VIDEO="+(current.getVideoFormat()!=null)+" AUDIO="+(current.getAudioFormat()!=null));}clock.setText(time(pos)+(duration>0?" / "+time(duration):" · DIRETTA"));if(duration>0&&!seeking)seek.setProgress((int)(pos*1000/duration));pauseButton.setText(current.isPlaying()?"Ⅱ Pausa":"▶ Riprendi");if(System.currentTimeMillis()>controlUntil&&current.isPlaying()&&!seeking&&!seek.hasFocus())transport.setVisibility(View.GONE);main.postDelayed(this,500);}};main.post(tick);
        main.postDelayed(()->{if(alive&&player==current&&current.getPlaybackState()==Player.STATE_BUFFERING&&current.getCurrentPosition()==0){details(item);showError("La sorgente non risponde. Prova un’altra sorgente dal catalogo.");}},45000);
    }
    String time(long ms){long s=Math.max(0,ms/1000);return s>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    void togglePlay(){if(player!=null){if(player.isPlaying())player.pause();else player.play();showControls();}}
    void seekBy(long delta){
        if(player==null)return;long target=Math.max(0,player.getCurrentPosition()+delta),duration=player.getDuration();if(duration>0)target=Math.min(target,duration);player.seekTo(target);showControls();
    }
    void showControls(){if(transport!=null){boolean hidden=transport.getVisibility()!=View.VISIBLE;transport.setVisibility(View.VISIBLE);controlUntil=System.currentTimeMillis()+6000;if(hidden&&pauseButton!=null)pauseButton.requestFocus();}}
    void tracks(){
        if(player==null)return;showControls();Tracks all=player.getCurrentTracks();ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
        labels.add("Disattiva sottotitoli");actions.add(()->player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()));
        for(Tracks.Group group:all.getGroups()){
            if(group.getType()!=C.TRACK_TYPE_AUDIO&&group.getType()!=C.TRACK_TYPE_TEXT)continue;
            for(int i=0;i<group.length;i++){if(!group.isTrackSupported(i))continue;final int index=i;Format f=group.getTrackFormat(i);labels.add((group.getType()==C.TRACK_TYPE_AUDIO?"Audio: ":"CC: ")+(f.label!=null?f.label:f.language!=null?f.language:"Traccia "+(i+1)));actions.add(()->player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(group.getType(),false).setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),index)).build()));}
        }
        new AlertDialog.Builder(this).setTitle("Audio e sottotitoli").setItems(labels.toArray(new String[0]),(d,i)->{if(player!=null)actions.get(i).run();}).setNegativeButton("Chiudi",null).show();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(opening!=null&&opening.visible()){if(e.getAction()==KeyEvent.ACTION_DOWN)opening.dismiss();return true;}
        if(screen.equals("search")&&e.getAction()==KeyEvent.ACTION_DOWN&&getWindow().getDecorView().hasWindowFocus()){
            if(searchScreen!=null&&searchScreen.move(getCurrentFocus(),e.getKeyCode()))return true;
            if(searchScreen!=null&&searchScreen.hasFocusWithin(nav)&&tvFocus.move(root,getCurrentFocus(),e.getKeyCode()))return true;
        }
        if(screen.equals("home")&&e.getAction()==KeyEvent.ACTION_DOWN&&getWindow().getDecorView().hasWindowFocus()&&tvFocus.move(root,getCurrentFocus(),e.getKeyCode()))return true;
        if(screen.equals("home")&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getKeyCode()==KeyEvent.KEYCODE_MENU){View focused=getCurrentFocus();if(focused!=null&&focused.getTag() instanceof JSONObject){JSONObject item=(JSONObject)focused.getTag();if(item.optString("addonId").equals("plugin.video.nikod.toonitalia")){toonActions(item);return true;}}}
        if(screen.equals("player")&&player!=null){
            int key=e.getKeyCode();
            // A key that only reveals controls must not activate the newly focused button on key-up/repeat.
            if(key==revealKey){if(e.getAction()==KeyEvent.ACTION_UP)revealKey=KeyEvent.KEYCODE_UNKNOWN;return true;}
            if(e.getAction()==KeyEvent.ACTION_DOWN){
                if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(e.getRepeatCount()==0)togglePlay();return true;}
                if(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND){seekBy(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?10000:-10000);return true;}
                boolean horizontal=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
                boolean navigation=horizontal||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
                if(navigation&&transport!=null&&transport.getVisibility()!=View.VISIBLE){showControls();pauseButton.requestFocus();revealKey=key;return true;}
                if(navigation)showControls();
                if(horizontal&&seek!=null&&seek.hasFocus()){seekBy(key==KeyEvent.KEYCODE_DPAD_RIGHT?10000:-10000);return true;}
                if(seek!=null&&seek.hasFocus()&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER)){
                    if(e.getRepeatCount()==0)pauseButton.requestFocus();revealKey=key;return true;
                }
                // Visible buttons use Android's normal focus navigation and OK activation.
            }
        }
        return super.dispatchKeyEvent(e);
    }
    boolean sourceHost(String host){
        if(host==null)return false;
        return host.toLowerCase(Locale.ROOT).matches("(?:www\\.)?streaming-?community[a-z0-9-]*\\.[a-z]{2,}") || host.equals("vixsrc.to") || host.equals("vixcloud.co") || host.equals("vixcloud.cc");
    }
    void openSourcePage(JSONObject item){openSourcePage(item,false);}
    void openSourcePage(JSONObject item,boolean automatic){withSourceIndex(()->openSourcePageResolved(currentSource(item),automatic));}
    void openSourcePageResolved(JSONObject item,boolean automatic){
        Uri uri=Uri.parse(item.optString("url"));
        if(!"https".equals(uri.getScheme())||!sourceHost(uri.getHost())){showError("Pagina sorgente non consentita");return;}
        browserMediaCandidate=null;browserPage=uri.toString();
        screen="web";base();automaticEpisode=automatic?item:null;root.setPadding(dp(14),dp(10),dp(14),dp(8));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(button("‹ Catalogo",this::home));
        TextView label=text("Pagina sorgente · sessione sul dispositivo",14,MUTED);bar.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        nativePlayerButton=button("Player dell’app",()->{if(browserMediaCandidate!=null)play(browserMediaCandidate);});
        nativePlayerButton.setEnabled(false);nativePlayerButton.setAlpha(.45f);bar.addView(nativePlayerButton);
        bar.addView(button("Ricarica",()->refreshSourceIndex(true,()->openSourcePage(currentSource(item),automatic))));root.addView(bar);
        webFrame=new FrameLayout(this);root.addView(webFrame,new LinearLayout.LayoutParams(-1,0,1));
        try{browser=new WebView(this);}catch(Exception e){home();showError("Il componente browser Android non è disponibile su questo dispositivo.");return;}
        browser.setBackgroundColor(BG);webFrame.addView(browser,new FrameLayout.LayoutParams(-1,-1));
        WebSettings settings=browser.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(!automatic);
        browserIdentity=settings.getUserAgentString();
        final WebView sourceBrowser=browser;
        final boolean streamingCommunitySession=uri.getHost()!=null&&uri.getHost().toLowerCase(Locale.ROOT).matches("(?:www\\.)?streaming-?community[a-z0-9-]*\\.[a-z]{2,}");
        browser.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                observeSourceRequest(sourceBrowser,request,streamingCommunitySession);return null;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){
                browserPage=url;browserMediaCandidate=null;nativePlayerButton.setEnabled(false);nativePlayerButton.setAlpha(.45f);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return false;
                Uri target=request.getUrl();return !"https".equals(target.getScheme())||!sourceHost(target.getHost());
            }
            @Override public void onPageFinished(WebView view,String url){
                label.setText("Pagina sorgente · "+Uri.parse(url).getHost());
                Uri pageUri=Uri.parse(url);String path=pageUri.getPath()==null?"":pageUri.getPath();
                android.util.Log.i("NicoStreamPlayback","SC_PAGE path="+path+" title="+view.getTitle());
                if(streamingCommunitySession&&(path.contains("/watch")||path.contains("/iframe/")))prefs.edit().putString("scLastPage",url).apply();
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame())label.setText("Il sito risponde HTTP "+response.getStatusCode());}
        });
        try{ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request){
                observeSourceRequest(sourceBrowser,request,streamingCommunitySession);return null;
            }
        });}catch(RuntimeException ignored){}
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(automatic||webCustomView!=null){callback.onCustomViewHidden();return;}
                webCustomView=view;webCustomCallback=callback;browser.setVisibility(View.GONE);bar.setVisibility(View.GONE);webFrame.addView(view,new FrameLayout.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView(){closeWebFullscreen();bar.setVisibility(View.VISIBLE);}
            @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
        });
        if(automatic){
            // Normal browser loading continues behind an opaque native loading panel.
            bar.setVisibility(View.GONE);LinearLayout loading=col();loading.setBackgroundColor(BG);loading.setGravity(Gravity.CENTER);loading.addView(title(item.optString("title"),24));loading.addView(text("Avvio della puntata…",18,MUTED));loading.addView(new ProgressBar(this));loading.addView(button("Annulla",this::home));webFrame.addView(loading,new FrameLayout.LayoutParams(-1,-1));
            main.postDelayed(()->{if(browser!=sourceBrowser||automaticEpisode!=item)return;home();new AlertDialog.Builder(this).setTitle("La puntata richiede un passaggio sul sito").setMessage("Puoi aprire la pagina della puntata per continuare.").setPositiveButton("Apri pagina",(d,w)->openSourcePage(item)).setNegativeButton("Catalogo",null).show();},30000);
        }
        browser.loadUrl(uri.toString());if(!automatic)browser.requestFocus();
    }
    void observeSourceRequest(WebView sourceView,WebResourceRequest request,boolean scSession){
                Uri mediaUri=request.getUrl();String path=mediaUri.getPath()==null?"":mediaUri.getPath().toLowerCase(Locale.ROOT);
                String host=mediaUri.getHost()==null?"":mediaUri.getHost().toLowerCase(Locale.ROOT);
                boolean videoHost=host.equals("vixsrc.to")||host.endsWith(".vixsrc.to")||host.equals("vixcloud.co")||host.endsWith(".vixcloud.co")||host.equals("vixcloud.cc")||host.endsWith(".vixcloud.cc");
                String referringHost="";
                for(Map.Entry<String,String> h:request.getRequestHeaders().entrySet())if(h.getKey().equalsIgnoreCase("Referer"))referringHost=Uri.parse(h.getValue()).getHost();
                boolean observedMedia=path.endsWith(".m3u8")||path.endsWith(".mpd")||path.endsWith(".mp4")||path.contains("/playlist/");
                if(scSession&&observedMedia)android.util.Log.i("NicoStreamPlayback","SC_BROWSER_MEDIA host="+host+" refererHost="+referringHost);
                videoHost=videoHost||sourceHost(referringHost);
                if(scSession&&videoHost&&"https".equals(mediaUri.getScheme())&&"GET".equals(request.getMethod())&&(path.endsWith(".m3u8")||path.endsWith(".mpd")||path.endsWith(".mp4")||path.contains("/playlist/"))){
                    final Map<String,String> headers=new HashMap<>(request.getRequestHeaders());
                    main.post(()->{
                        if(browser!=sourceView||!screen.equals("web")||browserMediaCandidate!=null)return;
                        try{
                            JSONObject candidate=new JSONObject();candidate.put("id","sc-local");candidate.put("url",mediaUri.toString());
                            candidate.put("title",automaticEpisode!=null?automaticEpisode.optString("title"):sourceView.getTitle()==null?catalogTitle:sourceView.getTitle());candidate.put("tag","STREAMINGCOMMUNITY · PLAYER NATIVO");
                            candidate.put("localBrowserSession",true);candidate.put("browserHeaders",new JSONObject(headers));candidate.put("browserIdentity",browserIdentity);
                            candidate.put("webSource",sourceView.getUrl());candidate.put("mime",(path.endsWith(".m3u8")||path.contains("/playlist/"))?MimeTypes.APPLICATION_M3U8:path.endsWith(".mpd")?MimeTypes.APPLICATION_MPD:MimeTypes.VIDEO_MP4);
                            if(automaticEpisode!=null){candidate.put("resumeSource",PlaybackHistory.source(automaticEpisode));candidate.put("resumeAt",automaticEpisode.optLong("resumeAt",resumePosition(automaticEpisode)));if(automaticEpisode.optJSONObject("nextEpisode")!=null)candidate.put("nextEpisode",automaticEpisode.getJSONObject("nextEpisode"));}
                            browserMediaCandidate=candidate;nativePlayerButton.setEnabled(true);nativePlayerButton.setAlpha(1f);nativePlayerButton.setText("▶ Player dell’app");
                            android.util.Log.i("NicoStreamPlayback","SC_LOCAL_MEDIA_AVAILABLE host="+host+" type="+candidate.optString("mime"));
                            if(automaticEpisode!=null){android.util.Log.i("NicoStreamPlayback","SC_AUTO_NATIVE episode="+automaticEpisode.optLong("scEpisodeId"));automaticEpisode=null;play(candidate);}
                        }catch(Exception ignored){}
                    });
                }
    }
    void returnToSource(JSONObject media){
        try{JSONObject page=new JSONObject();page.put("url",media.optString("webSource"));page.put("title",media.optString("title"));page.put("isWeb",true);openSourcePage(page);}catch(Exception e){home();}
    }
    void closeWebFullscreen(){
        if(webCustomView!=null&&webFrame!=null){webFrame.removeView(webCustomView);webCustomView=null;}
        if(browser!=null)browser.setVisibility(View.VISIBLE);
        if(webCustomCallback!=null){webCustomCallback.onCustomViewHidden();webCustomCallback=null;}
        if(root!=null&&root.getChildCount()>0)root.getChildAt(0).setVisibility(View.VISIBLE);
    }
    void settings(){
        screen="settings";base();ScrollView scroll=new ScrollView(this);LinearLayout content=col();scroll.addView(content);root.addView(scroll);
        content.addView(title("Aggiornamenti",26));content.addView(text("Versione "+BuildConfig.VERSION_NAME,15,MUTED));
        content.addView(button("Controlla aggiornamenti",()->updates.check(true)),new LinearLayout.LayoutParams(-2,-2));content.addView(text("Le nuove versioni arrivano da GitHub. Il controllo automatico avviene una volta al giorno.",13,MUTED));space(content,24);
        content.addView(title("Domini delle sorgenti",24));content.addView(text("L’indice Stream4me viene aggiornato automaticamente. Se non è disponibile, viene mantenuta l’ultima copia valida.",14,MUTED));
        sourceDomainStatus=text("StreamingCommunity: "+(providerOrigin().isEmpty()?"verifica in corso":Uri.parse(providerOrigin()).getHost())+(sourceIndexWarning?" · ultima copia valida":""),14,MINT);content.addView(sourceDomainStatus);space(content,8);
        content.addView(button("Aggiorna domini sorgenti",()->refreshSourceIndex(true,()->Toast.makeText(this,sourceIndexWarning?"Uso l’ultima copia valida delle sorgenti":"Domini delle sorgenti aggiornati",Toast.LENGTH_LONG).show())));space(content,24);
        content.addView(title("Connessione a NicoStream",28));content.addView(text("Il server è già configurato. La connessione parte automaticamente all’apertura dell’app.",16,MUTED));space(content,12);
        content.addView(text(EngineAccess.SERVER,16,MINT));space(content,12);
        boolean authorized=!prefs.getString("serverKey","").isEmpty();
        content.addView(text(authorized?"Accesso salvato su questo dispositivo.":"Autorizza questo dispositivo una sola volta con la tua chiave di accesso.",15,MUTED));space(content,12);
        if(authorized){content.addView(button("Aggiorna la home",()->{section="Scopri";home();refreshSourceIndex(true,()->loadDiscover(true));loadProgramHome(true);}));space(content,12);}
        content.addView(button(authorized?"Modifica chiave di accesso":"Autorizza dispositivo",this::editEngineAccess));space(content,12);
        if(authorized)content.addView(button("‹ Torna al catalogo",this::home));
    }
    void editEngineAccess(){
        EditText key=new EditText(this);key.setSingleLine(true);key.setInputType(129);key.setSaveEnabled(false);key.setHint("Chiave privata NicoStream");key.setText(prefs.getString("serverKey",""));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Accesso al dispositivo").setView(key).setPositiveButton("Salva",null).setNegativeButton("Annulla",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=key.getText().toString().trim();if(!value.matches("[a-f0-9]{64}")){key.setError("La chiave deve contenere 64 caratteri esadecimali");return;}prefs.edit().putString("serverKey",value).apply();dialog.dismiss();refreshSourceIndex(true,null);home();}));dialog.show();
    }

    @Override public void onBackPressed(){if(screen.equals("search")){cancelSearch();section=searchBackSection;catalogTitle=searchBackTitle;items=searchBackItems;home();}else if(screen.equals("web")){if(automaticEpisode!=null){automaticEpisode=null;home();}else if(webCustomView!=null)closeWebFullscreen();else if(browser!=null&&browser.canGoBack())browser.goBack();else home();}else if(screen.equals("loading")){cancelJob();home();}else if(screen.equals("player")){if(selected!=null&&(selected.optBoolean("localBrowserSession")||selected.optString("addonId").equals("plugin.video.nikod.toonitalia")||selected.optString("addonId").equals("plugin.video.nikod.programfeed")))home();else details(selected);}else if(!screen.equals("home")){home();}else if(!history.isEmpty()){previous();}else super.onBackPressed();}
    @Override protected void onPause(){if(heroCarousel!=null)heroCarousel.setActive(false);savePlayback(true);super.onPause();if(player!=null)player.pause();if(browser!=null)browser.onPause();}
    @Override protected void onResume(){super.onResume();if(updates!=null)updates.onResume();if(heroCarousel!=null)heroCarousel.setActive(true);if(browser!=null)browser.onResume();}
    @Override protected void onDestroy(){if(searchScreen!=null){searchScreen.close();searchScreen=null;}if(posterBadges!=null)posterBadges.close();if(updates!=null)updates.close();if(heroCarousel!=null){heroCarousel.dispose();heroCarousel=null;}savePlayback(true);alive=false;cancelJob();if(browser!=null){try{ServiceWorkerController.getInstance().setServiceWorkerClient(null);}catch(RuntimeException ignored){}browser.destroy();browser=null;}if(player!=null){player.release();player=null;}main.removeCallbacksAndMessages(null);worker.shutdown();artworkWorker.shutdownNow();artworkCache.evictAll();super.onDestroy();}
}
