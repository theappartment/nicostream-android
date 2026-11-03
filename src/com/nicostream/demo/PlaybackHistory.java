package com.nicostream.demo;

import org.json.*;
import java.util.*;

/** Public source metadata and positions only. Resolved URLs and session headers never enter history. */
final class PlaybackHistory {
    static long normalize(long position,long duration){
        if(position<=10000)return 0;
        if(duration>0&&duration-position<30000)return 0;
        return duration>0?Math.min(position,Math.max(0,duration-30000)):position;
    }
    static JSONObject source(JSONObject item)throws JSONException {
        JSONObject out=new JSONObject();
        for(String k:new String[]{"id","title","url","addonId","seriesKey","isPlayable","sources","scAction","scTitleId","scEpisodeId","seasonNumber","episodeNumber","episodeName","seriesTitle","art","thumb","backdrop","description","year","tag","isWeb","isPlugin","mime"})
            if(item.has(k))out.put(k,item.get(k));
        return out;
    }
    static String key(JSONObject item){if(item==null)return "";if(item.optString("addonId").equals("plugin.video.nikod.toonitalia")&&!item.optString("seriesKey").isEmpty()&&item.optInt("episodeNumber")>0)return "toon:"+item.optString("seriesKey")+":"+item.optInt("seasonNumber")+":"+item.optInt("episodeNumber");return item.optLong("scEpisodeId")>0?"sc:"+item.optLong("scTitleId")+":"+item.optLong("scEpisodeId"):item.optString("id",item.optString("url"));}
    static String group(JSONObject item){if(item.optString("addonId").equals("plugin.video.nikod.toonitalia")&&!item.optString("seriesKey").isEmpty()&&item.optInt("episodeNumber")>0)return "toon:"+item.optString("seriesKey");return item.optLong("scTitleId")>0?"sc:"+item.optLong("scTitleId"):key(item);}
    static JSONArray update(JSONArray before,JSONObject item,long position,long duration,boolean ended,long now)throws JSONException {
        JSONObject safe=source(item);boolean done=ended||(duration>0&&duration-position<30000);
        JSONObject entry=new JSONObject().put("source",safe).put("position",normalize(position,duration)).put("duration",Math.max(0,duration)).put("completed",done).put("updated",now);
        JSONArray out=new JSONArray().put(entry);
        for(int i=0;i<before.length()&&out.length()<12;i++){JSONObject old=before.optJSONObject(i);if(old!=null&&!key(old.getJSONObject("source")).equals(key(safe)))out.put(old);}
        return out;
    }
    static JSONObject find(JSONArray all,JSONObject source){for(int i=0;i<all.length();i++){JSONObject e=all.optJSONObject(i);if(e!=null&&key(e.optJSONObject("source")).equals(key(source)))return e;}return null;}
    static JSONArray continuing(JSONArray all){
        Set<String> groups=new HashSet<>();JSONArray out=new JSONArray();
        for(int i=0;i<all.length();i++){JSONObject e=all.optJSONObject(i);if(e==null||e.optJSONObject("source")==null)continue;
            if(groups.add(group(e.optJSONObject("source")))&&!e.optBoolean("completed"))out.put(e);
        }return out;
    }
    static JSONObject next(JSONObject current,JSONArray episodes){
        boolean toon=current.optString("addonId").equals("plugin.video.nikod.toonitalia")&&!current.optString("seriesKey").isEmpty()&&current.optInt("episodeNumber")>0;
        if(!toon&&current.optLong("scTitleId")<=0)return null;
        JSONObject best=null;for(int i=0;i<episodes.length();i++){JSONObject e=episodes.optJSONObject(i);if(e==null)continue;
            if(toon){if(!e.optString("addonId").equals(current.optString("addonId"))||!e.optString("seriesKey").equals(current.optString("seriesKey"))||!e.optBoolean("isPlayable")||e.optInt("episodeNumber")<=0)continue;}
            else if(e.optLong("scTitleId")!=current.optLong("scTitleId"))continue;
            int s=e.optInt("seasonNumber"),n=e.optInt("episodeNumber"),cs=current.optInt("seasonNumber"),cn=current.optInt("episodeNumber");
            if(s<cs||(s==cs&&n<=cn))continue;
            if(best==null||s<best.optInt("seasonNumber")||(s==best.optInt("seasonNumber")&&n<best.optInt("episodeNumber")))best=e;
        }return best;
    }
}
