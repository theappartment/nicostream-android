package com.nicostream.demo;
import org.junit.Test;
import static org.junit.Assert.*;
public class EngineAccessTest {
 @Test public void credentialsStayOnExactHttpsOrigin(){
  String server="https://nicostream.theappartment.it";
  assertEquals("Bearer example",EngineAccess.authorization(server,server+"/v1/media/test","example"));
  for(String target:new String[]{"http://nicostream.theappartment.it/v1/media/test","https://foreign.test/video","https://nicostream.theappartment.it:444/video","https://nicostream.theappartment.it@foreign.test/video"})assertEquals("",EngineAccess.authorization(server,target,"example"));
 }
 @Test public void defaultHttpsPortIsEquivalentAndMissingKeyIsEmpty(){assertTrue(EngineAccess.sameOrigin("https://engine.test","https://ENGINE.test:443/health"));assertEquals("",EngineAccess.authorization("https://engine.test","https://engine.test/health",""));assertFalse(EngineAccess.sameOrigin("https://user@engine.test","https://engine.test/video"));}
}
