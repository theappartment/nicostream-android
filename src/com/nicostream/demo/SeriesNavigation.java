package com.nicostream.demo;
import org.json.*;
import java.io.IOException;

final class SeriesNavigation {
    static JSONObject initialSeason(JSONArray seasons,JSONArray history,long titleId){
        JSONArray continuing=PlaybackHistory.continuing(history);for(int n=0;n<continuing.length();n++){JSONObject record=continuing.optJSONObject(n),source=record==null?null:record.optJSONObject("source");if(source==null||source.optLong("scTitleId")!=titleId||record.optLong("position")<=0)continue;for(int i=0;i<seasons.length();i++){JSONObject season=seasons.optJSONObject(i);if(season!=null&&season.optInt("seasonNumber")==source.optInt("seasonNumber"))return season;}}
        JSONObject first=null,available=null;for(int n=0;n<seasons.length();n++){JSONObject season=seasons.optJSONObject(n);if(season==null)continue;int number=season.optInt("seasonNumber");if(first==null||number>0&&(first.optInt("seasonNumber")<=0||number<first.optInt("seasonNumber")))first=season;if(season.optInt("episodeCount")>0&&(available==null||number>0&&(available.optInt("seasonNumber")<=0||number<available.optInt("seasonNumber"))))available=season;}return available==null?first:available;
    }
    static void verifySeason(JSONObject props,long titleId,int season)throws Exception {
        if(props.getJSONObject("title").getLong("id")!=titleId||props.getJSONObject("loadedSeason").getInt("number")!=season)throw new IOException("Il catalogo ha restituito una stagione diversa. Riprova.");
    }
}
