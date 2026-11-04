package com.nicostream.demo;
import org.json.*;
import android.webkit.WebSettings;
import java.util.*;

/** Resolve editorial titles to actual catalog IDs, with conservative exact-title matching. */
final class MarvelResolver {
    final MainActivity app;
    MarvelResolver(MainActivity app){this.app=app;}
    static String key(String text){return SearchMemory.key(text).replace("&","and").replace("marvel's ","").replace("marvel’s ","").replaceAll("[^a-z0-9]","");}
    static String query(String title){
        String q=title.replaceAll("(?i)\\s+(?:season\\s+\\d+|s\\d+\\s+e\\d+.*)$","").replace("Dr. Strange","Doctor Strange");
        String[][] aliases={{"Captain America: The First Avenger","Captain America - Il primo vendicatore"},{"The Incredible Hulk","L'incredibile Hulk"},{"Guardians of the Galaxy vol. 2","Guardiani della Galassia Vol. 2"},{"Guardians of the Galaxy vol. 3","Guardiani della Galassia Vol. 3"},{"Guardians of the Galaxy","Guardiani della Galassia"},{"I Am Groot","Io sono Groot"},{"Shang-Chi & The Legend of the 10 Rings","Shang-Chi e la leggenda dei Dieci Anelli"},{"Doctor Strange in the Multiverse of Madness","Doctor Strange nel Multiverso della Follia"},{"GotG Holiday Special","Guardiani della Galassia Holiday Special"},{"The Fantastic Four: First Steps","I Fantastici 4: Gli inizi"},{"What If","What If...?"}};
        for(String[] alias:aliases)if(q.equalsIgnoreCase(alias[0]))return alias[1];return q;
    }
    void open(String title,ViewState state){
        String query=query(title),cache="marvelLink:"+key(title);boolean series=title.matches("(?i).*(?:season\\s+\\d+|s\\d+\\s+e\\d+.*)$")||Arrays.asList("WandaVision","Ironheart","Secret Invasion","Marvel Zombies","Wonder Man","Hawkeye","Moon Knight","Echo","She-Hulk: Attorney at Law","Ms. Marvel","Agatha All Along","Inhumans","The Defenders","Eyes of Wakanda").contains(title);
        try{JSONObject saved=new JSONObject(app.prefs.getString(cache,"{}"));if(saved.optLong("scTitleId")>0){state.open(saved);return;}}catch(Exception ignored){}
        if(state.busy)return;state.busy=true;state.status("Cerco il titolo nel catalogo…");
        app.worker.execute(()->{JSONObject match=null;JSONArray candidates=new JSONArray();try{
            LinkedHashSet<String> queries=new LinkedHashSet<>();queries.add(query);queries.add(title.replaceAll("(?i)\\s+(?:season\\s+\\d+|s\\d+\\s+e\\d+.*)$",""));
            String origin=app.providerOrigin(),ua=WebSettings.getDefaultUserAgent(app);Set<Long> seen=new HashSet<>();List<JSONObject> exact=new ArrayList<>();
            for(String q:queries){if(origin.isEmpty())break;JSONObject props=app.scPage(origin+"/it/search?q="+android.net.Uri.encode(q),ua);JSONArray result=StreamingCommunityCatalog.search(props,props.optString("_sourceOrigin",origin),q);
                for(int n=0;n<result.length();n++){JSONObject item=result.optJSONObject(n);if(item==null||!seen.add(item.optLong("scTitleId")))continue;candidates.put(item);boolean tv=item.optString("tag").equals("SERIE TV");if(tv!=series)continue;String name=key(item.optString("title"));if(name.equals(key(query))||name.equals(key(q)))exact.add(item);}
                if(exact.size()==1){match=exact.get(0);break;}
            }
        }catch(Exception ignored){}
            final JSONObject ready=match;final JSONArray found=candidates;app.main.post(()->{state.busy=false;if(!state.active())return;if(ready!=null){try{app.prefs.edit().putString(cache,StreamingCommunityCatalog.publicMetadata(ready).toString()).apply();}catch(Exception ignored){}state.open(ready);}else{state.status(found.length()>0?"Scegli la versione del titolo":"Titolo non trovato automaticamente");app.marvelSearch=true;app.searchCatalog(query,found.length()>0?found:null);if(found.length()==0)app.unifiedSearch(query);}});
        });
    }
    abstract static class ViewState {boolean busy;abstract void status(String text);abstract boolean active();abstract void open(JSONObject item);}
}
