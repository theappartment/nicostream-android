package com.nicostream.demo;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class BrowserSessionDataSourceTest {
    private Map<String,String> observed(){
        Map<String,String> m=new HashMap<>();
        m.put("User-Agent","old-agent");m.put("Accept-Encoding","gzip");
        m.put("Cookie","must-not-cross-host=1");m.put("Authorization","Bearer private-test-value");
        m.put("Referer","https://vixcloud.cc/video/episode");m.put("Origin","https://vixcloud.cc");
        m.put("Range","bytes=0-1");return m;
    }
    @Test public void sameOriginKeepsAuthorizationAndBrowserIdentity(){
        Map<String,String> h=BrowserSessionDataSource.headersFor("vixcloud.cc","vixcloud.cc","actual-browser-agent",observed(),"session=own");
        assertEquals("actual-browser-agent",h.get("User-Agent"));assertEquals("identity",h.get("Accept-Encoding"));
        assertEquals("Bearer private-test-value",h.get("Authorization"));assertEquals("session=own",h.get("Cookie"));
        assertFalse(h.containsKey("Range"));
    }
    @Test public void redirectDoesNotCarryPreviousHostCredentials(){
        Map<String,String> h=BrowserSessionDataSource.headersFor("cdn.example.org","vixcloud.cc","actual-browser-agent",observed(),"cdn=own");
        assertFalse(h.containsKey("Authorization"));assertEquals("cdn=own",h.get("Cookie"));
        assertFalse(h.containsValue("must-not-cross-host=1"));
        assertEquals("https://vixcloud.cc/video/episode",h.get("Referer"));
    }
    @Test public void missingDestinationCookieDoesNotReuseCapturedCookie(){
        Map<String,String> h=BrowserSessionDataSource.headersFor("cdn.example.org","vixcloud.cc","actual-browser-agent",observed(),null);
        assertFalse(h.containsKey("Cookie"));assertFalse(h.containsKey("Authorization"));
    }
}
