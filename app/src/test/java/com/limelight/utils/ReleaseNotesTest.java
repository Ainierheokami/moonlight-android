package com.limelight.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReleaseNotesTest {
    private static final String BODY =
            "<!-- notes:zh-CN -->\n"
            + "## 更新内容\n"
            + "- 新增悬浮键盘 Ins 键\n"
            + "- 修复备份\n"
            + "<!-- notes:en -->\n"
            + "## What's new\n"
            + "- Added the Ins key\n"
            + "- Fixed backup\n"
            + "<!-- notes:end -->\n"
            + "\n"
            + "### Commits since last build\n"
            + "- feat: something that says commit deadbeef1 in its subject\n"
            + "\n"
            + "<!-- build-commit: fd6e33b3cd0e14e7f3179673e95854b3908e1876 -->\n"
            + "Automated APK build from commit fd6e33b3cd0e14e7f3179673e95854b3908e1876.\n";

    @Test
    public void picksChineseSectionForChineseDevice() {
        String notes = ReleaseNotes.select(BODY, "zh", "CN");
        assertTrue(notes.contains("新增悬浮键盘 Ins 键"));
        assertFalse(notes.contains("Added the Ins key"));
    }

    @Test
    public void fallsBackToSameLanguageWhenCountryDiffers() {
        assertTrue(ReleaseNotes.select(BODY, "zh", "TW").contains("修复备份"));
    }

    @Test
    public void usesEnglishForOtherLanguages() {
        String notes = ReleaseNotes.select(BODY, "de", "DE");
        assertTrue(notes.contains("Added the Ins key"));
        assertFalse(notes.contains("新增"));
    }

    @Test
    public void usesFirstSectionWhenNeitherLanguageNorEnglishExists() {
        String body = "<!-- notes:ja -->\n- 日本語\n<!-- notes:end -->";
        assertEquals("• 日本語", ReleaseNotes.select(body, "fr", "FR"));
    }

    @Test
    public void dropsHeadingsMarkersCommitListAndBuildLine() {
        String notes = ReleaseNotes.select(BODY, "en", "US");
        assertEquals("• Added the Ins key\n• Fixed backup", notes);
        assertFalse(notes.contains("Commits since"));
        assertFalse(notes.contains("Automated APK"));
        assertFalse(notes.contains("deadbeef1"));
    }

    @Test
    public void plainBodiesAreShownWithoutTheBuildLine() {
        String body = "Some manual notes\n\n- item one\n\nAutomated APK build from commit fd6e33b.";
        assertEquals("Some manual notes\n\n• item one", ReleaseNotes.select(body, "en", "US"));
    }

    @Test
    public void legacyAutomatedBodyHasNothingToShow() {
        assertNull(ReleaseNotes.select(
                "Automated APK build from commit fd6e33b3cd0e14e7f3179673e95854b3908e1876.", "en", "US"));
        assertNull(ReleaseNotes.select("", "en", "US"));
        assertNull(ReleaseNotes.select(null, "en", "US"));
    }

    @Test
    public void buildCommitMarkerWinsOverCommitMentionsInNotes() {
        String body = "- feat: revert commit deadbeef1\n<!-- build-commit: fd6e33b3cd0e14e7f3179673e95854b3908e1876 -->";
        assertEquals("fd6e33b3cd0e14e7f3179673e95854b3908e1876", UpdateChecker.extractCommitId(body));
    }

    @Test
    public void commitStillParsedFromLegacyBody() {
        assertEquals("fd6e33b3cd0e14e7f3179673e95854b3908e1876", UpdateChecker.extractCommitId(
                "Automated APK build from commit fd6e33b3cd0e14e7f3179673e95854b3908e1876."));
    }
}
