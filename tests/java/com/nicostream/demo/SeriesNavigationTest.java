package com.nicostream.demo;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SeriesNavigationTest {
    JSONObject season(int number,int episodes)throws Exception{return new JSONObject().put("seasonNumber",number).put("episodeCount",episodes);}
    JSONObject record(long series,int season,boolean completed)throws Exception{return new JSONObject().put("source",new JSONObject().put("scTitleId",series).put("scEpisodeId",100+season).put("seasonNumber",season).put("episodeNumber",1)).put("position",completed?0:45000).put("completed",completed);}
    @Test public void firstAvailableRegularSeasonIsSelectedWithoutHistory()throws Exception {
        JSONArray seasons=new JSONArray().put(season(0,2)).put(season(2,10)).put(season(1,10));assertEquals(1,SeriesNavigation.initialSeason(seasons,new JSONArray(),42).getInt("seasonNumber"));
        assertEquals(2,SeriesNavigation.initialSeason(new JSONArray().put(season(1,0)).put(season(2,8)),new JSONArray(),42).getInt("seasonNumber"));
    }
    @Test public void validResumeChoosesItsSeasonWithoutChangingHistory()throws Exception {
        JSONArray seasons=new JSONArray().put(season(1,10)).put(season(2,10)),history=new JSONArray().put(record(42,2,false));String before=history.toString();assertEquals(2,SeriesNavigation.initialSeason(seasons,history,42).getInt("seasonNumber"));assertEquals(before,history.toString());
    }
    @Test public void UnrelatedCompletedAndUnavailableResumeSeasonsDoNotSelectWrongSeason()throws Exception {
        JSONArray seasons=new JSONArray().put(season(1,10)).put(season(2,10));
        for(JSONObject record:new JSONObject[]{record(99,2,false),record(42,2,true),record(42,3,false)})assertEquals(1,SeriesNavigation.initialSeason(seasons,new JSONArray().put(record),42).getInt("seasonNumber"));
        assertNull(SeriesNavigation.initialSeason(new JSONArray(),new JSONArray(),42));
    }
    @Test public void providerMustReturnTheRequestedSeriesAndSeason()throws Exception {
        JSONObject props=new JSONObject("{title:{id:42},loadedSeason:{number:2}}");SeriesNavigation.verifySeason(props,42,2);
        try{SeriesNavigation.verifySeason(props,41,2);fail("wrong series must fail");}catch(java.io.IOException expected){}
        try{SeriesNavigation.verifySeason(props,42,1);fail("wrong season must fail");}catch(java.io.IOException expected){}
    }
}
