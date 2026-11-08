package com.nicostream.demo;

import androidx.media3.common.PlaybackException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgramLiveRecoveryTest {
    @Test public void recognizesProgramFeedThroughTheVpsCompactRoute() throws Exception {
        JSONObject route = new JSONObject().put("isLive", true)
            .put("addonId", "plugin.video.nikod.programfeed").put("url", "/v1/route/compact");
        assertTrue(ProgramLiveRecovery.supports(route));
        assertFalse(ProgramLiveRecovery.supports(route.put("isLive", false)));
    }

    @Test public void liveJumpIsBoundedAndDoesNotSpendAnUrlRetry() {
        ProgramLiveRecovery recovery = new ProgramLiveRecovery(new JSONObject(), "");
        assertTrue(recovery.beginLiveJump(1000));
        assertEquals(0, recovery.attempts);
        assertFalse(recovery.beginLiveJump(1001));
        assertFalse(recovery.liveJumpTimedOut(10999));
        assertTrue(recovery.liveJumpTimedOut(11000));
        assertTrue(recovery.completeLiveJump());
        assertFalse(recovery.liveJumpTimedOut(12000));
        assertFalse(recovery.beginLiveJump(12000));
        assertEquals(2000, recovery.nextDelay());
        assertTrue(recovery.beginLiveJump(13000));
    }

    @Test public void liveJumpExtendsOnlyWhileMediaIsRecentAndStopsAtFifteenSeconds() {
        ProgramLiveRecovery recovery = new ProgramLiveRecovery(new JSONObject(), "");
        assertTrue(recovery.beginLiveJump(1000));
        recovery.recordJumpMedia(9000);
        assertFalse(recovery.liveJumpTimedOut(11000));
        assertTrue(recovery.liveJumpTimedOut(13001));
        recovery.recordJumpMedia(14000);
        assertFalse(recovery.liveJumpTimedOut(15999));
        assertTrue(recovery.liveJumpTimedOut(16000));
    }

    @Test public void retriesIoButNotDecoderErrors() {
        assertTrue(ProgramLiveRecovery.retryable(PlaybackException.ERROR_CODE_IO_UNSPECIFIED));
        assertFalse(ProgramLiveRecovery.retryable(PlaybackException.ERROR_CODE_DECODING_FAILED));
    }

    @Test public void capsConsecutiveAttemptsAndResetsAfterStablePlayback() {
        ProgramLiveRecovery recovery = new ProgramLiveRecovery(new JSONObject(), "Formula 1");
        assertEquals(2000, recovery.nextDelay());
        assertEquals(5000, recovery.nextDelay());
        assertEquals(10000, recovery.nextDelay());
        assertEquals(-1, recovery.nextDelay());
        recovery.update(1, true, false);
        recovery.update(30001, true, false);
        assertEquals(2000, recovery.nextDelay());
    }

    @Test public void initialResolutionPinsTheFirstValidTitle() {
        ProgramLiveRecovery recovery = new ProgramLiveRecovery(new JSONObject(), "");
        assertFalse(recovery.acceptTitle(""));
        assertTrue(recovery.acceptTitle("Internazionale x Como"));
        assertFalse(recovery.acceptTitle("Another event"));
    }

    @Test public void requiresContinuousBufferingAndVerifiesResolvedTitle() {
        ProgramLiveRecovery recovery = new ProgramLiveRecovery(new JSONObject(), "Formula 1");
        assertFalse(recovery.update(1, false, true));
        assertFalse(recovery.update(35000, false, false));
        assertFalse(recovery.update(36000, false, true));
        assertTrue(recovery.update(71000, false, true));
        assertTrue(recovery.acceptTitle("Formula 1"));
        assertFalse(recovery.acceptTitle("Another event"));
    }
}
