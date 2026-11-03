package com.nicostream.demo;

import org.json.*;
import java.util.*;

/** Retain provider routes and identities: matching titles need not be the same edition. */
final class UnifiedSearch {
    static JSONArray combine(JSONArray stream,JSONArray toon)throws JSONException {
        JSONArray result=new JSONArray();append(result,stream,"Stream4me · StreamingCommunity");append(result,toon,"ToonItalia");return result;
    }
    private static void append(JSONArray result,JSONArray source,String label)throws JSONException {
        if(source==null)return;Set<String> seen=new HashSet<>();
        for(int n=0;n<source.length();n++){
            JSONObject original=source.optJSONObject(n);if(original==null||original.optBoolean("isNotice"))continue;
            String key=original.optString("id",original.optString("url"));if(!key.isEmpty()&&!seen.add(key))continue;
            result.put(new JSONObject(original.toString()).put("searchProvider",label));
        }
    }
}
