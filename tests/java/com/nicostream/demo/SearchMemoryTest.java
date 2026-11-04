package com.nicostream.demo;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SearchMemoryTest {
    @Test public void historyIsNewestFirstDeduplicatedAndBounded()throws Exception {
        JSONArray history=new JSONArray();for(int n=0;n<15;n++)history=SearchMemory.remember(history,"Title "+n);assertEquals(12,history.length());assertEquals("Title 14",history.getString(0));
        history=SearchMemory.remember(history,"  TITLE   10  ");assertEquals(12,history.length());assertEquals("TITLE 10",history.getString(0));assertEquals("Title 14",history.getString(1));
        assertEquals(11,SearchMemory.forget(history,"title 10").length());assertEquals(11,SearchMemory.remember(SearchMemory.forget(history,"title 10")," ").length());
    }
    @Test public void normalizationHandlesAccentsAndDoesNotSplitUnicode(){
        assertEquals(SearchMemory.key("COLISIÓN"),SearchMemory.key("colision"));StringBuilder text=new StringBuilder();for(int n=0;n<99;n++)text.append('a');text.append("😀extra");String clean=SearchMemory.clean(text.toString());assertEquals(100,clean.codePointCount(0,clean.length()));assertTrue(clean.endsWith("😀"));
    }
    JSONObject title(String name)throws Exception{return new JSONObject().put("title",name).put("searchProvider","ToonItalia");}
    @Test public void suggestionsPreferRecentAndPrefixesAndDeduplicateSources()throws Exception {
        JSONArray catalog=new JSONArray().put(title("The Landman Story")).put(title("Landman"));JSONArray remote=new JSONArray().put(title("LANDMAN")).put(title("Land of the Lost"));
        JSONArray suggestions=SearchMemory.suggestions("land",new JSONArray().put("Land before time"),catalog,remote);
        assertEquals(4,suggestions.length());assertEquals("Land before time",suggestions.getJSONObject(0).getString("query"));assertEquals("LANDMAN",suggestions.getJSONObject(1).getString("query"));assertEquals("The Landman Story",suggestions.getJSONObject(3).getString("query"));
    }
    @Test public void titleCacheStoresOnlyPublicSuggestionFields()throws Exception {
        JSONObject item=title("Hunter x Hunter").put("url","https://example.test/signed?token=private").put("headers",new JSONObject().put("Cookie","private"));
        JSONArray cached=SearchMemory.cacheTitles(new JSONArray(),new JSONArray().put(item).put(new JSONObject().put("title","Offline").put("isNotice",true)));
        assertEquals(1,cached.length());assertFalse(cached.toString().contains("private"));assertFalse(cached.toString().contains("url"));assertEquals("ToonItalia",cached.getJSONObject(0).getString("searchProvider"));
    }
    @Test public void contentGridRequiresRoutesAndKeepsDifferentProviders()throws Exception {
        JSONObject stream=title("Landman").put("url","https://example.test/title/1").put("id","sc-1");
        JSONObject toon=title("Landman").put("url","plugin://toon/1").put("id","toon-1");
        JSONArray local=new JSONArray().put(stream).put(title("Landman cached")).put(title("Other").put("url","https://example.test/title/2"));
        JSONArray result=SearchMemory.content("land",local,new JSONArray().put(stream).put(toon));assertEquals(2,result.length());assertEquals("sc-1",result.getJSONObject(0).getString("id"));assertEquals("toon-1",result.getJSONObject(1).getString("id"));
    }
    @Test public void emptyQueryCanSuggestHomeTitlesAndHistoryIsNotInvented()throws Exception {
        JSONArray result=SearchMemory.suggestions("",new JSONArray().put("Old search"),new JSONArray().put(title("Lupin")),new JSONArray());assertEquals(1,result.length());assertEquals("Lupin",result.getJSONObject(0).getString("query"));
        assertEquals(0,SearchMemory.suggestions("unmatched",new JSONArray(),new JSONArray().put(title("Lupin")),new JSONArray()).length());
    }
}
