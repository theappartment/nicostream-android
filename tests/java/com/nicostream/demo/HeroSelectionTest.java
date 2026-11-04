package com.nicostream.demo;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class HeroSelectionTest {
    JSONObject item(int id,boolean preview)throws Exception {JSONObject item=new JSONObject().put("id","title-"+id).put("scTitleId",id).put("title","Title "+id);if(preview)item.put("previewURL","https://vixcloud.cc/embed/"+id);return item;}
    JSONObject feed(JSONArray top)throws Exception {return new JSONObject().put("hero",item(99,true)).put("rails",new JSONArray().put(new JSONObject().put("id","trending").put("items",new JSONArray().put(item(98,true)))).put(new JSONObject().put("id","top10").put("items",top)));}
    @Test public void candidatesUseTopTenOrderAndSkipTitlesWithoutTrailer()throws Exception {
        JSONObject feed=feed(new JSONArray().put(item(1,false)).put(item(2,true)).put(item(2,true)).put(item(3,true)));
        JSONArray candidates=HeroSelection.candidates(feed);assertEquals(2,candidates.length());assertEquals(2,candidates.getJSONObject(0).getInt("scTitleId"));assertEquals(3,candidates.getJSONObject(1).getInt("scTitleId"));
    }
    @Test public void unhydratedTopTenStartsWithPhotoOfItsFirstTitle()throws Exception {
        JSONArray candidates=HeroSelection.candidates(feed(new JSONArray().put(item(1,false)).put(item(2,false))));assertEquals(1,candidates.length());assertEquals(1,candidates.getJSONObject(0).getInt("scTitleId"));assertFalse(candidates.getJSONObject(0).has("previewURL"));
    }
    @Test public void missingTopTenUsesFeaturedButInvalidPreviewsAreExcluded()throws Exception {
        assertEquals(99,HeroSelection.candidates(feed(new JSONArray())).getJSONObject(0).getInt("scTitleId"));
        JSONObject invalid=item(1,true).put("previewURL","https://unrelated.example/embed/1");JSONArray result=HeroSelection.candidates(feed(new JSONArray().put(invalid).put(item(2,true))));assertEquals(1,result.length());assertEquals(2,result.getJSONObject(0).getInt("scTitleId"));
    }
    @Test public void playlistIsBoundedAndCachedMetadataDoesNotPersistPreviewUrls()throws Exception {
        JSONArray top=new JSONArray();for(int n=1;n<=10;n++)top.put(item(n,true));JSONObject feed=feed(top);assertEquals(8,HeroSelection.candidates(feed).length());JSONObject cached=StreamingCommunityCatalog.publicMetadata(feed);assertFalse(cached.toString().contains("vixcloud"));assertEquals(10,HeroSelection.topTen(cached).length());
    }
}
