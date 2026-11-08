package com.nicostream.demo;

import androidx.media3.common.PlaybackException;
import org.json.JSONObject;

final class ProgramLiveRecovery {
    private static final int[] DELAYS_MS = {2000, 5000, 10000};
    private static final long STALL_MS = 35000, STABLE_MS = 30000;

    final JSONObject source;
    final String diagnosticSession = java.util.UUID.randomUUID().toString();
    long diagnosticStart;
    String diagnosticJob = "";
    private String resolvedTitle;
    int attempts;
    boolean pending;
    private long playingSince = -1, bufferingSince = -1;

    ProgramLiveRecovery(JSONObject source, String resolvedTitle) {
        this.source = source;
        this.resolvedTitle = resolvedTitle;
    }

    static boolean supports(JSONObject route) {
        if (route == null || !route.optBoolean("isLive")
                || !route.optString("addonId").equals("plugin.video.nikod.programfeed")) return false;
        String url = route.optString("url");
        return url.startsWith("plugin://plugin.video.nikod.programfeed/")
            || url.startsWith("/v1/route/");
    }

    boolean acceptTitle(String candidate) {
        if(candidate.isEmpty())return false;
        if(resolvedTitle.isEmpty())resolvedTitle=candidate;
        return resolvedTitle.equals(candidate);
    }

    static boolean retryable(int code) {
        return code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
            || code == PlaybackException.ERROR_CODE_TIMEOUT
            || (code >= PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                && code <= PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
    }

    int nextDelay() {
        playingSince = bufferingSince = -1;
        return attempts < DELAYS_MS.length ? DELAYS_MS[attempts++] : -1;
    }

    boolean update(long now, boolean playing, boolean buffering) {
        if (playing) {
            if (playingSince < 0) playingSince = now;
            if (now - playingSince >= STABLE_MS) attempts = 0;
        } else playingSince = -1;
        if (!buffering) {
            bufferingSince = -1;
            return false;
        }
        if (bufferingSince < 0) bufferingSince = now;
        return now - bufferingSince >= STALL_MS;
    }
}
