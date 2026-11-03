package com.nicostream.demo;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class UnifiedSearchTest {
 @Test public void preservesBothProvidersAndOriginalRoutes()throws Exception{
  JSONObject first=new JSONObject("{id:same,title:Naruto,url:'https://source.test/it/titles/1',scAction:title}");
  JSONObject second=new JSONObject("{id:same,title:Naruto,url:'/v1/route/opaque',addonId:'plugin.video.nikod.toonitalia',action:title}");
  JSONArray combined=UnifiedSearch.combine(new JSONArray().put(first),new JSONArray().put(second));
  assertEquals(2,combined.length());assertEquals("Stream4me · StreamingCommunity",combined.getJSONObject(0).getString("searchProvider"));assertEquals("ToonItalia",combined.getJSONObject(1).getString("searchProvider"));
  assertEquals(second.getString("url"),combined.getJSONObject(1).getString("url"));assertEquals("title",combined.getJSONObject(0).getString("scAction"));assertFalse(first.has("searchProvider"));
 }
 @Test public void removesNoticesAndDuplicatesButKeepsPagination()throws Exception{
  JSONArray input=new JSONArray("[{id:1,title:A},{id:1,title:A},{isNotice:true,title:Empty},{id:next,title:Next,isFolder:true,url:'/v1/route/next'}]");
  JSONArray combined=UnifiedSearch.combine(null,input);assertEquals(2,combined.length());assertEquals("/v1/route/next",combined.getJSONObject(1).getString("url"));
 }
 @Test public void acceptsOneUnavailableProvider()throws Exception{
  assertEquals(1,UnifiedSearch.combine(new JSONArray("[{id:1,title:A}]"),null).length());assertEquals(0,UnifiedSearch.combine(null,null).length());
 }
}
