package com.nicostream.demo;
import org.json.*;
import java.text.Normalizer;
import java.util.*;

final class SearchMemory {
    static String clean(String value){String text=value==null?"":value.trim().replaceAll("\\s+"," ");int count=text.codePointCount(0,text.length());return count>100?text.substring(0,text.offsetByCodePoints(0,100)):text;}
    static String key(String value){return Normalizer.normalize(clean(value),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
    static JSONArray remember(JSONArray previous,String query){
        JSONArray result=new JSONArray();query=clean(query);if(query.isEmpty())return previous==null?result:previous;result.put(query);Set<String> seen=new HashSet<>();seen.add(key(query));
        if(previous!=null)for(int i=0;i<previous.length()&&result.length()<12;i++){String value=clean(previous.optString(i));if(!value.isEmpty()&&seen.add(key(value)))result.put(value);}return result;
    }
    static JSONArray forget(JSONArray previous,String query){JSONArray result=new JSONArray();if(previous!=null)for(int i=0;i<previous.length();i++)if(!key(previous.optString(i)).equals(key(query)))result.put(previous.optString(i));return result;}
    static JSONArray cacheTitles(JSONArray before,JSONArray newer)throws JSONException {
        JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();for(JSONArray source:new JSONArray[]{newer,before})if(source!=null)for(int n=0;n<source.length()&&result.length()<60;n++){
            JSONObject item=source.optJSONObject(n);if(item==null||item.optBoolean("isNotice")||item.optString("title").isEmpty())continue;String title=clean(item.optString("title"));if(seen.add(key(title)))result.put(new JSONObject().put("title",title).put("searchProvider",item.optString("searchProvider")).put("tag",item.optString("tag")).put("year",item.optString("year")));
        }return result;
    }
    static JSONArray catalog(JSONObject feed,JSONArray favorites,JSONArray cached){
        JSONArray result=new JSONArray();if(feed!=null){JSONArray top=HeroSelection.topTen(feed);for(int i=0;i<top.length();i++)result.put(top.optJSONObject(i));JSONArray rails=feed.optJSONArray("rails");if(rails!=null)for(int n=0;n<rails.length();n++){JSONArray items=rails.optJSONObject(n)==null?null:rails.optJSONObject(n).optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++)result.put(items.optJSONObject(i));}}
        for(JSONArray source:new JSONArray[]{favorites,cached})if(source!=null)for(int i=0;i<source.length();i++){JSONObject item=source.optJSONObject(i);if(item!=null&&!item.optBoolean("isNotice")&&!item.optBoolean("isPlayable")&&!item.optString("scAction").equals("episode"))result.put(item);}return result;
    }
    static JSONArray content(String query,JSONArray catalog,JSONArray remote){
        JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();String wanted=key(query);
        for(JSONArray source:new JSONArray[]{remote,catalog})if(source!=null)for(int n=0;n<source.length()&&result.length()<36;n++){
            JSONObject item=source.optJSONObject(n);if(item==null||item.optBoolean("isNotice")||item.optString("url").isEmpty()||!key(item.optString("title")).contains(wanted))continue;
            String identity=item.optString("id");if(identity.isEmpty())identity=item.optString("url");if(seen.add(identity))result.put(item);
        }return result;
    }
    static JSONArray suggestions(String query,JSONArray recent,JSONArray catalog,JSONArray remote)throws JSONException {
        String wanted=key(query);List<JSONObject> ranked=new ArrayList<>();Set<String> seen=new HashSet<>();
        if(!wanted.isEmpty()&&recent!=null)for(int i=0;i<recent.length();i++){String value=clean(recent.optString(i));if(key(value).contains(wanted)&&seen.add(key(value)))ranked.add(new JSONObject().put("query",value).put("subtitle","Ricerca recente").put("score",key(value).startsWith(wanted)?300:200));}
        for(JSONArray source:new JSONArray[]{remote,catalog})if(source!=null)for(int i=0;i<source.length();i++){
            JSONObject item=source.optJSONObject(i);if(item==null||item.optBoolean("isNotice"))continue;String title=clean(item.optString("title")),normalized=key(title);if(title.isEmpty()||!normalized.contains(wanted)||!seen.add(normalized))continue;
            String provider=item.optString("searchProvider");if(provider.isEmpty())provider=item.optString("addonId").contains("toonitalia")?"ToonItalia":"StreamingCommunity";
            String subtitle=item.optString("tag");if(!item.optString("year").isEmpty())subtitle+=(subtitle.isEmpty()?"":" · ")+item.optString("year");subtitle+=(subtitle.isEmpty()?"":" · ")+provider;
            ranked.add(new JSONObject().put("query",title).put("subtitle",subtitle).put("score",wanted.isEmpty()?0:normalized.startsWith(wanted)?100:50));
        }
        ranked.sort((a,b)->Integer.compare(b.optInt("score"),a.optInt("score")));JSONArray result=new JSONArray();for(int i=0;i<Math.min(8,ranked.size());i++)result.put(ranked.get(i));return result;
    }
}
