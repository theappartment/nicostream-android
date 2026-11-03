package com.nicostream.demo;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class SourceDomainsTest {
 @Test public void validatesOnlyHttpsProviderOrigins(){assertEquals("https://streamingcommunityz.boo",SourceDomains.origin("https://streamingcommunityz.boo/it/"));for(String u:new String[]{"http://streamingcommunityz.boo","https://foreign.test","https://user@streamingcommunityz.boo","https://streamingcommunityz.boo:444","https://streamingcommunityz.boo/path","https://streamingcommunityz.boo?q=1"})assertEquals("",SourceDomains.origin(u));}
 @Test public void rebasesRoutesWithoutChangingEpisodeOrRawParameters(){assertEquals("https://streamingcommunityz.boo/it/watch/111?e=222&x=a%2Bb#part",SourceDomains.rebase("https://streamingcommunityz.associates/it/watch/111?e=222&x=a%2Bb#part","https://streamingcommunityz.boo"));}
 @Test public void preservesExternalSignedMediaAndOpaqueAddonRoutes(){for(String u:new String[]{"https://vixcloud.cc/playlist/master.m3u8?token=private","/v1/route/opaque","https://source.test/video.mp4","plugin://plugin.video.nikod.toonitalia/"})assertEquals(u,SourceDomains.rebase(u,"https://streamingcommunityz.boo"));}
 @Test public void migratesBookmarksHistoryAndUrlIdsButNotCredentials()throws Exception{
  JSONObject o=new JSONObject("{source:{id:'https://streamingcommunityz.associates/it/watch/1?e=2',url:'https://streamingcommunityz.associates/it/watch/1?e=2',scTitleId:1,scEpisodeId:2},position:123456,headers:{url:'https://streamingcommunityz.associates/private',Cookie:secret}}");
  SourceDomains.migrate(o,"https://streamingcommunityz.boo");assertTrue(o.getJSONObject("source").getString("url").contains(".boo/"));assertEquals(o.getJSONObject("source").getString("url"),o.getJSONObject("source").getString("id"));assertEquals(2,o.getJSONObject("source").getInt("scEpisodeId"));assertEquals(123456,o.getLong("position"));assertTrue(o.getJSONObject("headers").getString("url").contains(".associates/"));assertEquals("secret",o.getJSONObject("headers").getString("Cookie"));
 }
}
