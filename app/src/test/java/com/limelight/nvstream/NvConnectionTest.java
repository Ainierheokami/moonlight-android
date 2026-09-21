package com.limelight.nvstream;

import com.limelight.nvstream.http.NvHTTP;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NvConnectionTest {
    @Test
    public void acceptsDisplayNameDeviceIdAndFriendlySuffix() {
        NvHTTP.DisplayInfo display = new NvHTTP.DisplayInfo(
                "\\\\.\\DISPLAY1", "HP 27mq", "display-device-id");

        assertTrue(NvConnection.isDisplaySelectionAvailable(
                Collections.singletonList(display), "\\\\.\\DISPLAY1"));
        assertTrue(NvConnection.isDisplaySelectionAvailable(
                Collections.singletonList(display), "display-device-id"));
        assertTrue(NvConnection.isDisplaySelectionAvailable(
                Collections.singletonList(display), "\\\\.\\DISPLAY1 (HP 27mq)"));
    }

    @Test
    public void rejectsDisplayThatIsNoLongerAdvertised() {
        NvHTTP.DisplayInfo display = new NvHTTP.DisplayInfo(
                "\\\\.\\DISPLAY1", "HP 27mq", "new-display-device-id");

        assertFalse(NvConnection.isDisplaySelectionAvailable(
                Collections.singletonList(display), "old-display-device-id"));
        assertFalse(NvConnection.isDisplaySelectionAvailable(
                Collections.<NvHTTP.DisplayInfo>emptyList(), "old-display-device-id"));
    }

    @Test
    public void treatsEmptySelectionAsTheHostDefault() {
        assertTrue(NvConnection.isDisplaySelectionAvailable(
                Collections.<NvHTTP.DisplayInfo>emptyList(), ""));
        assertTrue(NvConnection.isDisplaySelectionAvailable(
                Collections.<NvHTTP.DisplayInfo>emptyList(), null));
    }
}
