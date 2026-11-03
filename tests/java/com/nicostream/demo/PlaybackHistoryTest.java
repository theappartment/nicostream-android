package com.nicostream.demo;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class PlaybackHistoryTest {
    JSONObject episode(long title,long id,int season,int number)throws Exception{return new JSONObject().put("id","public:"+id).put("url","https://source.test/it/watch/"+title+"?e="+id).put("scAction","episode").put("scTitleId",title).put("scEpisodeId",id).put("seasonNumber",season).put("episodeNumber",number).put("title","Episode "+number);}
    @Test public void iosResumeBoundaries(){assertEquals(0,PlaybackHistory.normalize(10000,100000));assertEquals(10001,PlaybackHistory.normalize(10001,100000));assertEquals(70000,PlaybackHistory.normalize(70000,100000));assertEquals(0,PlaybackHistory.normalize(70001,100000));assertEquals(0,PlaybackHistory.normalize(-1,100000));}
    @Test public void sourceNeverPersistsResolvedContext()throws Exception {JSONObject e=episode(1,2,1,1).put("headers",new JSONObject().put("Cookie","secret")).put("browserHeaders",new JSONObject().put("Authorization","secret")).put("resumeAt",100).put("nextEpisode",new JSONObject());JSONObject safe=PlaybackHistory.source(e);assertFalse(safe.has("headers"));assertFalse(safe.has("browserHeaders"));assertFalse(safe.has("nextEpisode"));assertFalse(safe.has("resumeAt"));assertEquals(2,safe.getLong("scEpisodeId"));}
    @Test public void stableIdUpdatesExistingRecordAcrossNewMetadata()throws Exception {JSONObject e=episode(1,2,1,1);JSONArray h=PlaybackHistory.update(new JSONArray(),e,20000,200000,false,1);e.put("title","Updated");h=PlaybackHistory.update(h,e,42000,200000,false,2);assertEquals(1,h.length());assertEquals(42000,PlaybackHistory.find(h,e).getLong("position"));assertEquals(2,h.getJSONObject(0).getLong("updated"));}
    @Test public void shelfGroupsSeriesAndDoesNotResurrectOldEpisodeAfterCompletion()throws Exception {JSONArray h=new JSONArray();h=PlaybackHistory.update(h,episode(1,1,1,1),20000,200000,false,1);h=PlaybackHistory.update(h,episode(2,3,1,1),20000,200000,false,2);h=PlaybackHistory.update(h,episode(1,2,1,2),40000,200000,false,3);JSONArray shelf=PlaybackHistory.continuing(h);assertEquals(2,shelf.length());assertEquals(2,shelf.getJSONObject(0).getJSONObject("source").getLong("scEpisodeId"));h=PlaybackHistory.update(h,episode(1,2,1,2),200000,200000,true,4);assertEquals(1,PlaybackHistory.continuing(h).length());}
    @Test public void nextEpisodeUsesSeriesAndSeasonOrder()throws Exception {JSONObject current=episode(1,8,1,2),next=episode(1,99,1,3);JSONArray list=new JSONArray().put(episode(8,9,1,3)).put(episode(1,77,2,1)).put(next).put(episode(1,7,1,1));assertEquals(99,PlaybackHistory.next(current,list).getLong("scEpisodeId"));assertEquals(77,PlaybackHistory.next(next,list).getLong("scEpisodeId"));assertNull(PlaybackHistory.next(episode(1,77,2,1),list));}
    @Test public void historyIsBoundedAndRoundTrips()throws Exception {JSONArray h=new JSONArray();for(int i=1;i<20;i++)h=PlaybackHistory.update(h,episode(i,i,1,1),20000,200000,false,i);h=new JSONArray(h.toString());assertEquals(12,h.length());assertEquals(19,h.getJSONObject(0).getJSONObject("source").getLong("scEpisodeId"));}
    JSONObject toon(String series,int season,int number,String route)throws Exception {
        return new JSONObject().put("id",route).put("url","/v1/route/"+route).put("addonId","plugin.video.nikod.toonitalia").put("seriesKey",series).put("seriesTitle",series).put("seasonNumber",season).put("episodeNumber",number).put("isPlayable",true).put("title","Episode "+number);
    }
    @Test public void toonHistorySurvivesSourceChangesAndGroupsBySeries()throws Exception {
        JSONObject first=toon("dragonball",1,1,"uqload"),manual=toon("dragonball",1,1,"voe");assertEquals(PlaybackHistory.key(first),PlaybackHistory.key(manual));
        JSONArray h=PlaybackHistory.update(new JSONArray(),first,20000,200000,false,1);h=PlaybackHistory.update(h,manual,35000,200000,false,2);assertEquals(1,h.length());assertEquals(35000,PlaybackHistory.find(h,first).getLong("position"));
        h=PlaybackHistory.update(h,toon("dragonball",1,2,"next"),20000,200000,false,3);assertEquals(1,PlaybackHistory.continuing(h).length());assertEquals("dragonball",h.getJSONObject(0).getJSONObject("source").getString("seriesKey"));
    }
    @Test public void toonSuccessorDoesNotMixOtherSeriesOrExtras()throws Exception {
        JSONObject first=toon("dragonball",1,1,"one"),next=toon("dragonball",1,2,"two");JSONArray directory=new JSONArray().put(toon("naruto",1,2,"other")).put(toon("dragonball",0,0,"extra")).put(next);
        assertEquals(PlaybackHistory.key(next),PlaybackHistory.key(PlaybackHistory.next(first,directory)));assertNull(PlaybackHistory.next(toon("dragonball",0,0,"extra"),directory));assertNull(PlaybackHistory.next(new JSONObject().put("id","unknown"),directory));
    }
    @Test public void toonExtraDoesNotHideSeriesResume()throws Exception {
        JSONArray history=PlaybackHistory.update(new JSONArray(),toon("dragonball",1,1,"episode"),20000,200000,false,1);
        history=PlaybackHistory.update(history,toon("dragonball",0,0,"opening"),20000,200000,false,2);
        assertEquals(2,PlaybackHistory.continuing(history).length());
    }
}
