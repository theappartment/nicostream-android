package com.nicostream.demo;

import org.json.*;
import java.io.IOException;
import java.util.regex.*;

/** Public page metadata only; stable episode IDs are never inferred from list positions. */
final class StreamingCommunityCatalog {
    static JSONObject props(String html)throws Exception {
        Matcher m=Pattern.compile("data-page=\"([^\"]+)\"").matcher(html);
        if(!m.find())throw new IOException("Il sito richiede di aprire la pagina prima di continuare.");
        return new JSONObject(decode(m.group(1))).getJSONObject("props");
    }
    static String decode(String value){
        Matcher m=Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|quot|apos|amp|lt|gt);").matcher(value);
        StringBuffer out=new StringBuffer();
        while(m.find()){
            String key=m.group(1),v;
            switch(key){case "quot":v="\"";break;case "apos":v="'";break;case "amp":v="&";break;case "lt":v="<";break;case "gt":v=">";break;
                default:try{v=new String(Character.toChars(Integer.parseInt(key.substring(key.startsWith("#x")?2:1),key.startsWith("#x")?16:10)));}catch(Exception e){v=m.group();}}
            m.appendReplacement(out,Matcher.quoteReplacement(v));
        }
        m.appendTail(out);return out.toString();
    }
    static String watch(String origin,long titleId,long episodeId){
        if(titleId<=0||episodeId<=0)throw new IllegalArgumentException("Episodio non valido");
        // Current site navigation uses e, not episode_id (which silently opens episode one).
        return origin+"/it/watch/"+titleId+"?e="+episodeId;
    }
    static void verifyEpisode(JSONObject props,long titleId,long episodeId)throws Exception {
        if(props.getJSONObject("title").getLong("id")!=titleId||props.getJSONObject("episode").getLong("id")!=episodeId)
            throw new IOException("Il sito ha restituito una puntata diversa. Riprova dal catalogo.");
    }
    static JSONObject entry(String title,String url,String action)throws JSONException {
        return new JSONObject().put("id",url).put("title",title).put("url",url).put("scAction",action).put("symbol","S").put("tag","STREAMINGCOMMUNITY");
    }
    static String artwork(JSONObject props,JSONObject record,String kind){
        JSONArray images=record.optJSONArray("images");if(images==null)return "";
        for(int i=0;i<images.length();i++){
            JSONObject image=images.optJSONObject(i);if(image==null||!kind.equals(image.optString("type")))continue;
            String direct=image.optString("original_url",image.optString("original_url_field",""));
            if(direct.startsWith("https://"))return direct;
            String cdn=props.optString("cdn_url"),file=image.optString("filename");
            if(cdn.startsWith("https://")&&file.matches("[a-zA-Z0-9._-]+"))return cdn.replaceAll("/+$","")+"/images/"+file;
        }return "";
    }
    static JSONObject decorate(JSONObject entry,JSONObject record,JSONObject props)throws Exception {
        String date=record.optString("release_date",record.optString("last_air_date",""));
        return entry.put("art",artwork(props,record,"poster")).put("backdrop",artwork(props,record,"background"))
            .put("thumb",artwork(props,record,"cover")).put("description",record.optString("plot"))
            .put("year",date.length()>=4?date.substring(0,4):"");
    }
    static JSONArray records(Object value){
        JSONArray out=new JSONArray();if(value instanceof JSONObject)value=((JSONObject)value).optJSONArray("data");
        if(value instanceof JSONArray){JSONArray rows=(JSONArray)value;for(int i=0;i<rows.length();i++){Object record=rows.opt(i);if(record instanceof JSONObject)out.put(record);else if(record instanceof JSONArray){JSONArray nested=records(record);for(int n=0;n<nested.length();n++)out.put(nested.opt(n));}}}return out;
    }
    static JSONObject titleEntry(JSONObject record,JSONObject props,String origin)throws Exception {
        JSONObject entry=decorate(entry(record.getString("name"),origin+"/it/titles/"+record.getLong("id")+"-"+record.getString("slug"),"title").put("scTitleId",record.getLong("id")).put("tag",record.optString("type").equals("tv")?"SERIE TV":record.optString("type").equals("movie")?"FILM":"DA SCOPRIRE"),record,props);
        JSONObject preview=record.optJSONObject("preview");if(preview!=null&&(preview.optInt("is_viewable")==1||preview.optBoolean("is_viewable"))&&HeroPreviewResolver.allowed(preview.optString("embed_url")))entry.put("previewURL",preview.getString("embed_url")).put("previewReferer",origin+"/it");return entry;
    }
    static JSONObject publicMetadata(JSONObject input)throws Exception {JSONObject copy=new JSONObject(input.toString());stripPreviews(copy);return copy;}
    static void stripPreviews(Object value)throws Exception {
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;object.remove("previewURL");object.remove("previewReferer");java.util.Iterator<String> keys=object.keys();while(keys.hasNext())stripPreviews(object.opt(keys.next()));}
        else if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int n=0;n<array.length();n++)stripPreviews(array.opt(n));}
    }
    static JSONArray titles(JSONObject props,String origin)throws Exception {
        JSONArray records=records(props.opt("titles")),out=new JSONArray();java.util.Set<Long> seen=new java.util.HashSet<>();
        for(int n=0;n<records.length();n++){JSONObject record=records.getJSONObject(n);if(record.optLong("id")>0&&seen.add(record.getLong("id")))out.put(titleEntry(record,props,origin));}return out;
    }
    static JSONArray filterByTag(JSONArray items,String tag){
        JSONArray filtered=new JSONArray();
        if(items==null)return filtered;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item!=null&&tag.equals(item.optString("tag")))filtered.put(item);
        }
        return filtered;
    }
    static JSONArray mixMoviesAndSeries(JSONArray items){
        JSONArray movies=filterByTag(items,"FILM"),series=filterByTag(items,"SERIE TV"),mixed=new JSONArray();
        for(int i=0;i<Math.max(movies.length(),series.length());i++){
            if(i<movies.length())mixed.put(movies.optJSONObject(i));
            if(i<series.length())mixed.put(series.optJSONObject(i));
        }
        return mixed;
    }
    static JSONObject home(JSONObject props,String origin)throws Exception {
        JSONArray sliders=props.optJSONArray("sliders"),rails=new JSONArray();
        if(sliders!=null)for(int n=0;n<sliders.length();n++){
            JSONObject slider=sliders.optJSONObject(n);if(slider==null)continue;String key=slider.optString("name").toLowerCase(java.util.Locale.ROOT),label=slider.optString("label");
            if(key.contains("coming")||key.contains("upcoming")||label.equalsIgnoreCase("In arrivo"))continue;
            if(key.equals("trending"))label="I titoli del momento";else if(key.equals("latest"))label="Aggiunti di recente";else if(key.equals("top10"))label="Top 10 di oggi";
            JSONArray list=titles(new JSONObject().put("titles",slider.opt("titles")).put("cdn_url",props.optString("cdn_url")),origin);
            JSONArray limited=new JSONArray();for(int i=0;i<list.length()&&i<(key.equals("top10")?10:30);i++)limited.put(list.getJSONObject(i));
            if(limited.length()>0&&!label.isEmpty())rails.put(new JSONObject().put("id",key).put("title",label).put("items",limited));
        }
        JSONObject out=new JSONObject().put("rails",rails).put("genres",props.optJSONArray("genres"));JSONObject featured=props.optJSONObject("title");
        if(featured!=null&&featured.optLong("id")>0){
            // The homepage billboard omits type/year: merge its matching slider record before decorating.
            JSONObject enriched=new JSONObject();if(sliders!=null)for(int n=0;n<sliders.length();n++){JSONArray values=records(sliders.getJSONObject(n).opt("titles"));for(int i=0;i<values.length();i++){JSONObject record=values.getJSONObject(i);if(record.optLong("id")==featured.optLong("id")){enriched=new JSONObject(record.toString());break;}}}
            java.util.Iterator<String> keys=featured.keys();while(keys.hasNext()){String key=keys.next();if(!featured.isNull(key))enriched.put(key,featured.get(key));}out.put("hero",titleEntry(enriched,props,origin));
        }
        else if(rails.length()>0)out.put("hero",rails.getJSONObject(0).getJSONArray("items").getJSONObject(0));
        return out;
    }
    static JSONArray search(JSONObject props,String origin,String query)throws Exception {
        JSONArray all=props.getJSONArray("titles"),out=new JSONArray();
        for(int i=0;i<all.length();i++){
            JSONObject t=all.getJSONObject(i);String name=t.getString("name");
            if(!name.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT)))continue;
            out.put(decorate(entry(name,origin+"/it/titles/"+t.getLong("id")+"-"+t.getString("slug"),"title").put("tag",t.optString("type").equals("tv")?"SERIE TV":"FILM"),t,props));
        }
        return out;
    }
    static JSONArray seasons(JSONObject props,String origin)throws Exception {
        JSONObject t=props.getJSONObject("title");JSONArray all=t.getJSONArray("seasons"),out=new JSONArray();
        for(int i=0;i<all.length();i++){
            JSONObject s=all.getJSONObject(i);int number=s.getInt("number");
            out.put(decorate(entry("Stagione "+number,origin+"/it/titles/"+t.getLong("id")+"-"+t.getString("slug")+"/season-"+number,"season").put("tag",s.optInt("episodes_count")+" EPISODI").put("seasonNumber",number).put("episodeCount",s.optInt("episodes_count")).put("scTitleId",t.getLong("id")),t,props));
        }
        return out;
    }
    static JSONArray episodes(JSONObject props,String origin)throws Exception {
        JSONObject t=props.getJSONObject("title"),s=props.getJSONObject("loadedSeason");JSONArray all=s.getJSONArray("episodes"),out=new JSONArray();
        for(int i=0;i<all.length();i++){
            JSONObject e=all.getJSONObject(i);long id=e.getLong("id");
            String name=t.getString("name")+" · S"+s.getInt("number")+" E"+e.getInt("number")+" · "+e.getString("name");
            out.put(decorate(entry(name,watch(origin,t.getLong("id"),id),"episode").put("scTitleId",t.getLong("id")).put("scEpisodeId",id).put("description",e.optString("plot")).put("tag",e.optInt("duration")+" MIN").put("episodeName",e.getString("name")).put("episodeNumber",e.getInt("number")).put("seasonNumber",s.getInt("number")),e,props));
        }
        return out;
    }
}
