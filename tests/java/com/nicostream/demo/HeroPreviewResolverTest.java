package com.nicostream.demo;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class HeroPreviewResolverTest {
    @Test public void resolvesNormalPublishedPlaylistAndParams()throws Exception {
        String html="window.masterPlaylist = {url: 'https://video.test/master.m3u8?base=1', params: {'token': 'a+b&c', 'expires': '42', 'empty': ''}}; window.player = {};";
        assertEquals("https://video.test/master.m3u8?base=1&token=a%2Bb%26c&expires=42&h=1",HeroPreviewResolver.playlist(html,"https://vixcloud.cc/embed/1?canPlayFHD=1"));
    }
    @Test public void malformedOrInsecurePlaylistIsRejected()throws Exception {
        for(String html:new String[]{"<html>No preview</html>","window.masterPlaylist = {url:'http://video.test/master.m3u8'};","window.masterPlaylist = {url:'https://user:secret@video.test/master.m3u8'};"}){
            try{HeroPreviewResolver.playlist(html,"https://vixcloud.cc/embed/1");fail("must reject");}catch(java.io.IOException expected){}
        }
    }
    @Test public void onlyKnownHttpsEmbedsAreEligible(){assertTrue(HeroPreviewResolver.allowed("https://vixcloud.cc/embed/123?token=abc"));assertFalse(HeroPreviewResolver.allowed("http://vixcloud.cc/embed/123"));assertFalse(HeroPreviewResolver.allowed("https://unknown.test/embed/123"));assertFalse(HeroPreviewResolver.allowed("https://vixcloud.cc/other/123"));}
    @Test public void previewNeedsExplicitProviderAvailability()throws Exception {
        JSONObject record=new JSONObject("{id:1,name:One,slug:one,type:tv,preview:{is_viewable:0,embed_url:'https://vixcloud.cc/embed/123?token=abc'}}");
        assertFalse(StreamingCommunityCatalog.titleEntry(record,new JSONObject(),"https://source.test").has("previewURL"));record.getJSONObject("preview").put("is_viewable",1);
        JSONObject entry=StreamingCommunityCatalog.titleEntry(record,new JSONObject(),"https://source.test");assertTrue(entry.has("previewURL"));assertEquals("https://source.test/it",entry.getString("previewReferer"));
    }
    @Test public void cachedMetadataDoesNotPersistSignedPreviewUrls()throws Exception {
        JSONObject original=new JSONObject("{hero:{title:One,previewURL:'https://vixcloud.cc/embed/123?token=abc',previewReferer:'https://source.test/it'},rails:[{items:[{title:Two,previewURL:secret}]}]}");
        JSONObject safe=StreamingCommunityCatalog.publicMetadata(original);assertFalse(safe.toString().contains("token=abc"));assertFalse(safe.toString().contains("previewURL"));assertTrue(original.getJSONObject("hero").has("previewURL"));assertEquals("One",safe.getJSONObject("hero").getString("title"));
    }
}
