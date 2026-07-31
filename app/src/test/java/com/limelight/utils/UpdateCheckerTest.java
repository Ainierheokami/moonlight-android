package com.limelight.utils;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UpdateCheckerTest {
    @Test
    public void comparesHeokamiBuildDatesAndSequences() {
        assertTrue(UpdateChecker.isNewerVersion(
                "12.1-heokami-20260724(9)", "12.1-heokami-20260725.1."));
        assertTrue(UpdateChecker.isNewerVersion(
                "12.1-heokami-20260725(1)", "12.1-heokami-20260725.2."));
        assertFalse(UpdateChecker.isNewerVersion(
                "12.1-heokami-20260725(2)", "12.1-heokami-20260725.2."));
        assertFalse(UpdateChecker.isNewerVersion(
                "12.1-heokami-20260726(1)", "12.1-heokami-20260725.2."));
    }

    @Test
    public void extractsVersionFromPublishedApkName() {
        assertEquals("12.1-heokami-20260725.2.", UpdateChecker.extractVersionFromAssetName(
                "Moonlight-12.1-heokami-20260725.2.-release.apk"));
    }

    @Test
    public void selectsAssetMatchingBuildType() throws JSONException {
        String json = "{" +
                "\"html_url\":\"https://github.com/Ainierheokami/moonlight-android/releases/tag/latest\"," +
                "\"assets\":[" +
                "{\"name\":\"Moonlight-12.1-heokami-20260725.1.-debug.apk\"," +
                "\"browser_download_url\":\"https://github.com/debug.apk\"}," +
                "{\"name\":\"Moonlight-12.1-heokami-20260725.2.-release.apk\"," +
                "\"browser_download_url\":\"https://github.com/release.apk\"}]}";

        assertEquals("Moonlight-12.1-heokami-20260725.1.-debug.apk",
                UpdateChecker.parseRelease(json, true).apkFileName);
        assertEquals("Moonlight-12.1-heokami-20260725.2.-release.apk",
                UpdateChecker.parseRelease(json, false).apkFileName);
    }

    @Test
    public void refusesWrongBuildTypeFallback() throws JSONException {
        String json = "{" +
                "\"html_url\":\"https://github.com/Ainierheokami/moonlight-android/releases/tag/latest\"," +
                "\"assets\":[{\"name\":\"Moonlight-12.1-heokami-20260725.2.-release.apk\"," +
                "\"browser_download_url\":\"https://github.com/release.apk\"}]}";

        try {
            UpdateChecker.parseRelease(json, true);
            fail("Debug builds must not fall back to a release APK");
        }
        catch (JSONException expected) {
            // Expected: no APK matching the installed build type.
        }
    }
}
