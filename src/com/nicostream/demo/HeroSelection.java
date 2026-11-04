package com.nicostream.demo;
import org.json.*;
import java.util.*;

final class HeroSelection {
    static JSONArray topTen(JSONObject feed){
        JSONArray result=new JSONArray();if(feed==null)return result;JSONArray rails=feed.optJSONArray("rails");Set<Long> seen=new HashSet<>();
        if(rails!=null)for(int n=0;n<rails.length();n++){JSONObject rail=rails.optJSONObject(n);if(rail==null||!rail.optString("id").equals("top10"))continue;JSONArray items=rail.optJSONArray("items");if(items!=null)for(int i=0;i<items.length()&&result.length()<10;i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optLong("scTitleId")>0&&seen.add(item.optLong("scTitleId")))result.put(item);}}
        return result;
    }
    static JSONArray candidates(JSONObject feed){
        JSONArray titles=topTen(feed),result=new JSONArray();for(int i=0;i<titles.length()&&result.length()<8;i++){JSONObject item=titles.optJSONObject(i);if(HeroPreviewResolver.allowed(item.optString("previewURL")))result.put(item);}
        if(result.length()==0){if(titles.length()>0)result.put(titles.optJSONObject(0));else if(feed!=null&&feed.optJSONObject("hero")!=null)result.put(feed.optJSONObject("hero"));}return result;
    }
}
