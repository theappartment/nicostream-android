package com.nicostream.demo;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class StreamingCommunityCatalogTest {
    @Test public void pageEntitiesAreDecodedExactlyOnce()throws Exception {
        JSONObject p=StreamingCommunityCatalog.props("<div data-page=\"{&quot;props&quot;:{&quot;name&quot;:&quot;A &amp; B &#x1f600;&quot;}}\">");
        assertEquals("A & B 😀",p.getString("name"));
        assertEquals("&quot;",StreamingCommunityCatalog.decode("&amp;quot;"));
    }
    @Test public void episodesUseStableIdsAndCurrentWatchParameter()throws Exception {
        JSONObject p=new JSONObject("{title:{id:10504,name:Landman},loadedSeason:{number:1,episodes:[{id:80818,number:1,name:Pilot,duration:56},{id:80819,number:2,name:Second,duration:55}]}}");
        JSONArray es=StreamingCommunityCatalog.episodes(p,"https://example.test");
        assertEquals("https://example.test/it/watch/10504?e=80818",es.getJSONObject(0).getString("url"));
        assertEquals("https://example.test/it/watch/10504?e=80819",es.getJSONObject(1).getString("url"));
        assertTrue(es.getJSONObject(1).getString("title").contains("S1 E2"));
    }
    @Test public void mismatchedEpisodeIsRejected()throws Exception {
        JSONObject p=new JSONObject("{title:{id:10504},episode:{id:80818}}");
        StreamingCommunityCatalog.verifyEpisode(p,10504,80818);
        try{StreamingCommunityCatalog.verifyEpisode(p,10504,80819);fail("must reject first episode for second selection");}catch(java.io.IOException expected){}
    }
    @Test public void unrelatedSearchCardsAreExcluded()throws Exception {
        JSONObject p=new JSONObject("{titles:[{id:10504,name:Landman,slug:landman,type:tv},{id:1,name:Halloween,slug:halloween,type:movie}]}");
        JSONArray es=StreamingCommunityCatalog.search(p,"https://example.test","Landman");assertEquals(1,es.length());assertTrue(es.getJSONObject(0).getString("url").endsWith("10504-landman"));
    }
    @Test public void artworkUsesPublishedCdnAndMissingImagesStayEmpty()throws Exception {
        JSONObject props=new JSONObject("{cdn_url:'https://cdn.example.test/'}");
        JSONObject record=new JSONObject("{images:[{type:poster,filename:poster.webp},{type:cover,original_url:'https://images.example.test/episode.webp'}]}");
        assertEquals("https://cdn.example.test/images/poster.webp",StreamingCommunityCatalog.artwork(props,record,"poster"));
        assertEquals("https://images.example.test/episode.webp",StreamingCommunityCatalog.artwork(props,record,"cover"));
        assertEquals("",StreamingCommunityCatalog.artwork(props,new JSONObject(),"poster"));
        assertEquals("",StreamingCommunityCatalog.artwork(props,new JSONObject("{images:[{type:poster,filename:'../secret'}]}"),"poster"));
    }
    @Test public void homeFlattensDeduplicatesAndOmitsUpcoming()throws Exception {
        JSONObject p=new JSONObject("{cdn_url:'https://cdn.example.test',sliders:[{name:trending,label:Trending,titles:[[{id:1,name:One,slug:one,type:tv}],[{id:1,name:One,slug:one,type:tv},{id:2,name:Two,slug:two,type:movie}]]},{name:coming_soon,label:'In arrivo',titles:[{id:3,name:Three,slug:three,type:movie}]}]}");
        JSONObject feed=StreamingCommunityCatalog.home(p,"https://source.test");JSONArray rails=feed.getJSONArray("rails");assertEquals(1,rails.length());assertEquals("I titoli del momento",rails.getJSONObject(0).getString("title"));assertEquals(2,rails.getJSONObject(0).getJSONArray("items").length());assertEquals("One",feed.getJSONObject("hero").getString("title"));
    }
    @Test public void billboardInheritsSeriesTypeAndYearFromMatchingSlider()throws Exception {
        JSONObject p=new JSONObject("{title:{id:1,name:MobLand,slug:mobland,plot:'Billboard plot'},sliders:[{name:trending,titles:{data:[{id:1,name:MobLand,slug:mobland,type:tv,release_date:'2025-03-30'}]}}]}");
        JSONObject hero=StreamingCommunityCatalog.home(p,"https://source.test").getJSONObject("hero");assertEquals("SERIE TV",hero.getString("tag"));assertEquals("2025",hero.getString("year"));assertEquals("Billboard plot",hero.getString("description"));
    }
    @Test public void topTenIsCappedAndKeepsPublishedOrder()throws Exception {
        JSONArray records=new JSONArray();for(int n=1;n<=15;n++)records.put(new JSONObject().put("id",n).put("name","Title "+n).put("slug","title-"+n).put("type","movie"));JSONObject p=new JSONObject().put("sliders",new JSONArray().put(new JSONObject().put("name","top10").put("titles",records)));
        JSONArray titles=StreamingCommunityCatalog.home(p,"https://source.test").getJSONArray("rails").getJSONObject(0).getJSONArray("items");assertEquals(10,titles.length());assertEquals("Title 1",titles.getJSONObject(0).getString("title"));assertEquals("Title 10",titles.getJSONObject(9).getString("title"));
    }
}
