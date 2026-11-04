package com.nicostream.demo;
import org.json.*;
import android.webkit.WebSettings;
import java.util.Locale;

/** Editorial watch path, not an official prerequisite list. Routes are resolved from the catalog. */
final class DoomsdayCollection {
    static final String LABEL="Verso Avengers: Doomsday";
    static final String[][] TITLES={
        {"The Avengers","Avengers","2012","FILM"},
        {"Avengers: Age of Ultron","Age of Ultron","2015","FILM"},
        {"Captain America: Civil War","Civil War","2016","FILM"},
        {"Doctor Strange","Doctor Strange","2016","FILM"},
        {"Thor: Ragnarok","Ragnarok","2017","FILM"},
        {"Black Panther","Black Panther","2018","FILM"},
        {"Avengers: Infinity War","Infinity War","2018","FILM"},
        {"Ant-Man and the Wasp","Ant-Man","2018","FILM"},
        {"Avengers: Endgame","Endgame","2019","FILM"},
        {"WandaVision","WandaVision","2021","SERIE TV"},
        {"The Falcon and the Winter Soldier","Falcon","2021","SERIE TV"},
        {"Loki","Loki","2021","SERIE TV"},
        {"Black Widow","Black Widow","2021","FILM"},
        {"Shang-Chi e la leggenda dei Dieci Anelli","Shang-Chi","2021","FILM"},
        {"Spider-Man: No Way Home","No Way Home","2021","FILM"},
        {"Doctor Strange nel Multiverso della Follia","Doctor Strange","2022","FILM"},
        {"Black Panther: Wakanda Forever","Wakanda Forever","2022","FILM"},
        {"Ant-Man and the Wasp: Quantumania","Quantumania","2023","FILM"},
        {"The Marvels","The Marvels","2023","FILM"},
        {"X-Men","X-Men","2000","FILM"},
        {"X-Men 2","X-Men","2003","FILM"},
        {"X-Men: Conflitto finale","X-Men","2006","FILM"},
        {"X-Men: Giorni di un futuro passato","X-Men","2014","FILM"},
        {"Deadpool & Wolverine","Deadpool","2024","FILM"},
        {"Captain America: Brave New World","Brave New World","2025","FILM"},
        {"Thunderbolts*","Thunderbolts","2025","FILM"},
        {"I Fantastici 4: Gli inizi","Fantastici","2025","FILM"}
    };
    final MainActivity app;JSONArray items;boolean loading;
    DoomsdayCollection(MainActivity app){this.app=app;try{items=new JSONArray(app.prefs.getString("doomsdayCollection","[]"));}catch(Exception ignored){}if(items==null||items.length()!=TITLES.length)items=seeds();}
    static JSONArray seeds(){JSONArray result=new JSONArray();for(String[] title:TITLES)try{result.put(new JSONObject().put("id","doomsday:"+result.length()).put("title",title[0]).put("collectionQuery",title[1]).put("year",title[2]).put("tag",title[3]).put("symbol","A").put("description","Percorso consigliato verso Avengers: Doomsday. Cerca questo titolo nei cataloghi."));}catch(Exception ignored){}return result;}
    void load(){
        String origin=app.providerOrigin();if(loading||origin.isEmpty())return;
        if(origin.equals(app.prefs.getString("doomsdayOrigin",""))&&System.currentTimeMillis()-app.prefs.getLong("doomsdayUpdated",0)<86400000)return;
        loading=true;app.worker.execute(()->{JSONArray resolved=seeds();String ua=WebSettings.getDefaultUserAgent(app);int matches=0;
            for(int n=0;n<TITLES.length&&app.alive;n++)try{
                String[] seed=TITLES[n];JSONObject props=app.scPage(origin+"/it/search?q="+android.net.Uri.encode(seed[1]),ua);JSONArray found=StreamingCommunityCatalog.search(props,props.optString("_sourceOrigin",origin),seed[1]);JSONObject match=null;
                for(int i=0;i<found.length();i++){JSONObject candidate=found.optJSONObject(i);if(candidate==null)continue;boolean series=seed[3].equals("SERIE TV");if(!series&&!candidate.optString("year").equals(seed[2]))continue;String name=SearchMemory.key(candidate.optString("title"));if(name.equals(SearchMemory.key(seed[0]))||(!series&&found.length()>0&&name.contains(SearchMemory.key(seed[1])))){match=candidate;break;}}
                if(match!=null){resolved.put(n,match);matches++;}
            }catch(Exception ignored){}
            final int count=matches;app.main.post(()->{loading=false;if(!app.alive||!origin.equals(app.providerOrigin()))return;if(count>0){items=resolved;try{app.prefs.edit().putString("doomsdayCollection",StreamingCommunityCatalog.publicMetadata(new JSONObject().put("items",resolved)).getJSONArray("items").toString()).putString("doomsdayOrigin",origin).putLong("doomsdayUpdated",System.currentTimeMillis()).apply();}catch(Exception ignored){}app.renderDoomsday();}});
        });
    }
}
