package com.limelight.heokami.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LayoutProfileRepositoryTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final class FakeWorkingCopy implements LayoutProfileRepository.WorkingCopy {
        Map<Integer, String> data = new TreeMap<>();

        @Override
        public Map<Integer, String> read() {
            return new TreeMap<>(data);
        }

        @Override
        public void replace(Map<Integer, String> elements) {
            data = new TreeMap<>(elements);
        }
    }

    private File dir;
    private FakeWorkingCopy wc;
    private AtomicInteger idCounter;
    private AtomicLong time;
    private LayoutProfileRepository repo;

    @Before
    public void setUp() throws Exception {
        dir = new File(tmp.newFolder(), "layout_profiles");
        wc = new FakeWorkingCopy();
        idCounter = new AtomicInteger();
        time = new AtomicLong(1000);
        repo = newRepo();
    }

    private LayoutProfileRepository newRepo() {
        return new LayoutProfileRepository(dir, () -> "p" + idCounter.incrementAndGet(), time::incrementAndGet);
    }

    private static String element(String text) {
        return "{\"TEXT\":\"" + text + "\",\"VK_CODE\":\"0x41\",\"TYPE\":\"DIGITAL_BUTTON\",\"BUTTON_DATA\":{}}";
    }

    @Test
    public void firstRunAdoptsTheCurrentKeyboardAsTheDefaultProfile() {
        wc.data.put(1, element("A"));
        wc.data.put(2, element("B"));
        repo.ensureInitialized(wc, "Default");

        assertEquals(1, repo.list().size());
        assertEquals("Default", repo.getActive().name);
        assertEquals(wc.data, wc.read());

        // a fresh instance reads the same state back from disk
        LayoutProfileRepository again = newRepo();
        again.ensureInitialized(new FakeWorkingCopy(), "ignored");
        assertEquals(1, again.list().size());
        assertEquals(repo.getActiveId(), again.getActiveId());
    }

    @Test
    public void switchingStoresTheWorkingCopyAndLoadsTheTarget() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        String first = repo.getActiveId();

        LayoutProfileRepository.Profile blank = repo.createBlank("Gaming");
        assertTrue(repo.switchTo(blank.id, wc));
        assertTrue(wc.data.isEmpty());
        assertEquals(blank.id, repo.getActiveId());

        wc.data.put(7, element("X"));          // edit while "Gaming" is active
        assertTrue(repo.switchTo(first, wc));
        assertEquals(1, wc.data.size());
        assertTrue(wc.data.get(1).contains("\"A\""));

        assertTrue(repo.switchTo(blank.id, wc)); // the edit was kept
        assertEquals(1, wc.data.size());
        assertTrue(wc.data.get(7).contains("\"X\""));
    }

    @Test
    public void switchToUnknownProfileChangesNothing() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        assertFalse(repo.switchTo("nope", wc));
        assertEquals(1, wc.data.size());
    }

    @Test
    public void switchFailsAndKeepsStateWhenTheTargetFileIsMissing() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        LayoutProfileRepository.Profile other = repo.createBlank("Other");
        assertTrue(new File(dir, other.id + ".json").delete());

        assertFalse(repo.switchTo(other.id, wc));
        assertNotEquals(other.id, repo.getActiveId());
        assertEquals(1, wc.data.size());
    }

    @Test
    public void saveAsNewForksTheScreenAndActivatesTheFork() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        String original = repo.getActiveId();
        wc.data.put(2, element("B"));            // unsaved edit on the original

        LayoutProfileRepository.Profile fork = repo.saveWorkingCopyAsNew("Fork", wc);
        assertEquals(fork.id, repo.getActiveId());
        assertEquals(2, repo.list().size());

        wc.data.clear();
        assertTrue(repo.switchTo(original, wc)); // the original kept the edit too
        assertEquals(2, wc.data.size());
    }

    @Test
    public void duplicateOfTheActiveProfileIncludesUnsyncedEdits() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        wc.data.put(2, element("B"));

        LayoutProfileRepository.Profile copy = repo.duplicate(repo.getActiveId(), "Copy", wc);
        assertNotNull(copy);
        assertNotEquals(repo.getActiveId(), copy.id);
        assertTrue(repo.switchTo(copy.id, wc));
        assertEquals(2, wc.data.size());
    }

    @Test
    public void namesAreMadeUnique() {
        repo.ensureInitialized(wc, "Default");
        assertEquals("Gaming", repo.createBlank("Gaming").name);
        // duplicates are detected case-insensitively but keep the casing the user typed
        assertEquals("gaming (2)", repo.createBlank("gaming").name);
        assertEquals("Gaming (3)", repo.createBlank("Gaming").name);
        assertEquals("Layout", repo.createBlank("   ").name);
    }

    @Test
    public void renameKeepsTheNameUniqueButAllowsKeepingItsOwn() {
        repo.ensureInitialized(wc, "Default");
        LayoutProfileRepository.Profile a = repo.createBlank("A");
        repo.createBlank("B");
        assertTrue(repo.rename(a.id, "A"));
        assertEquals("A", repo.get(a.id).name);
        assertTrue(repo.rename(a.id, "B"));
        assertEquals("B (2)", repo.get(a.id).name);
        assertFalse(repo.rename("nope", "x"));
    }

    @Test
    public void theLastProfileCannotBeDeleted() {
        repo.ensureInitialized(wc, "Default");
        assertFalse(repo.delete(repo.getActiveId(), wc));
        assertEquals(1, repo.list().size());
    }

    @Test
    public void deletingTheActiveProfileLoadsAnotherAndDropsItsBindings() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        String first = repo.getActiveId();
        LayoutProfileRepository.Profile other = repo.createBlank("Other");
        repo.switchTo(other.id, wc);
        repo.bindGlobal(other.id);
        repo.bindComputer("pc1", other.id);
        repo.bindApp("pc1", 5, "Steam", other.id);

        assertTrue(repo.delete(other.id, wc));
        assertEquals(first, repo.getActiveId());
        assertEquals(1, wc.data.size());
        assertNull(repo.getGlobalBinding());
        assertNull(repo.getComputerBinding("pc1"));
        assertNull(repo.getAppBinding("pc1", 5));
        assertFalse(new File(dir, other.id + ".json").exists());
    }

    @Test
    public void deletingAnInactiveProfileStoresTheCurrentEditsFirst() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        String first = repo.getActiveId();
        LayoutProfileRepository.Profile other = repo.createBlank("Other");
        wc.data.put(2, element("B"));

        assertTrue(repo.delete(other.id, wc));
        assertEquals(first, repo.getActiveId());
        LayoutProfileRepository.Profile blank = repo.createBlank("Z");
        repo.switchTo(blank.id, wc);
        repo.switchTo(first, wc);
        assertEquals(2, wc.data.size());
    }

    @Test
    public void mostSpecificBindingWins() {
        repo.ensureInitialized(wc, "Default");
        String def = repo.getActiveId();
        String g = repo.createBlank("Global").id;
        String c = repo.createBlank("Computer").id;
        String a = repo.createBlank("App").id;

        assertNull(repo.resolve("pc1", 5, "Steam"));
        repo.bindGlobal(g);
        assertEquals(g, repo.resolve("pc1", 5, "Steam"));
        repo.bindComputer("pc1", c);
        assertEquals(c, repo.resolve("pc1", 5, "Steam"));
        assertEquals(g, repo.resolve("pc2", 5, "Steam"));
        repo.bindApp("pc1", 5, "Steam", a);
        assertEquals(a, repo.resolve("pc1", 5, "Steam"));
        assertEquals(c, repo.resolve("pc1", 6, "Desktop"));
        assertNotEquals(def, repo.resolve("pc1", 5, "Steam"));

        repo.bindApp("pc1", 5, "Steam", null);
        assertEquals(c, repo.resolve("pc1", 5, "Steam"));
    }

    @Test
    public void anAppBindingStillMatchesByNameWhenTheAppIdChanged() {
        repo.ensureInitialized(wc, "Default");
        String a = repo.createBlank("App").id;
        repo.bindApp("pc1", 5, "Steam", a);
        assertEquals(a, repo.resolve("pc1", 99, "Steam"));
        assertNull(repo.resolve("pc1", 99, "Other"));
        assertNull(repo.resolve("pc2", 99, "Steam"));
    }

    @Test
    public void applyForSessionSwitchesOnlyWhenABindingExists() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        String def = repo.getActiveId();
        LayoutProfileRepository.Profile other = repo.createBlank("Other");

        assertEquals(def, repo.applyForSession("pc1", 5, "Steam", wc));
        assertEquals(1, wc.data.size());

        repo.bindComputer("pc1", other.id);
        assertEquals(other.id, repo.applyForSession("pc1", 5, "Steam", wc));
        assertTrue(wc.data.isEmpty());

        // a computer without a binding keeps the current layout
        assertEquals(other.id, repo.applyForSession("pc2", 5, "Steam", wc));
    }

    @Test
    public void bindingsToUnknownProfilesAreIgnored() {
        repo.ensureInitialized(wc, "Default");
        repo.bindGlobal("nope");
        repo.bindComputer("pc1", "nope");
        assertNull(repo.getGlobalBinding());
        assertNull(repo.getComputerBinding("pc1"));
    }

    @Test
    public void everythingSurvivesAReload() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        LayoutProfileRepository.Profile other = repo.createBlank("Other");
        repo.bindGlobal(other.id);
        repo.bindComputer("pc1", other.id);
        repo.bindApp("pc1", 5, "Steam", other.id);

        LayoutProfileRepository again = newRepo();
        again.ensureInitialized(new FakeWorkingCopy(), "ignored");
        assertEquals(2, again.list().size());
        assertEquals(other.id, again.getGlobalBinding());
        assertEquals(other.id, again.getComputerBinding("pc1"));
        assertEquals("Steam", again.getAppBinding("pc1", 5).appName);
        assertEquals(other.id, again.resolve("pc1", 5, "Steam"));
    }

    @Test
    public void aCorruptIndexIsRebuiltFromTheWorkingCopy() throws Exception {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        try (FileWriter w = new FileWriter(new File(dir, "index.json"))) {
            w.write("{ this is not json");
        }

        LayoutProfileRepository again = newRepo();
        again.ensureInitialized(wc, "Recovered");
        assertEquals(1, again.list().size());
        assertEquals("Recovered", again.getActive().name);
        assertEquals(1, wc.data.size());
    }

    @Test
    public void exportAndImportRoundTrip() throws Exception {
        wc.data.put(1, element("A"));
        wc.data.put(3, element("C"));
        repo.ensureInitialized(wc, "Default");
        String text = repo.exportText(repo.getActiveId(), wc);
        assertTrue(text.contains("ELEMENT_ID"));

        LayoutProfileRepository.Profile imported = repo.importProfile("Imported", text);
        repo.switchTo(imported.id, wc);
        assertEquals(2, wc.data.size());
        assertTrue(wc.data.get(3).contains("\"C\""));
        assertFalse(wc.data.get(3).contains("ELEMENT_ID"));
    }

    @Test
    public void legacyFlatLayoutTextCanBeImported() throws Exception {
        String legacy = "{\"1\":" + element("A") + ",\"2\":" + element("B") + ",\"junk\":\"x\"}";
        Map<Integer, String> parsed = LayoutProfileRepository.parseLayoutText(legacy);
        assertEquals(2, parsed.size());
        assertTrue(parsed.get(2).contains("\"B\""));
    }

    @Test(expected = JSONException.class)
    public void importingGarbageFails() throws Exception {
        repo.ensureInitialized(wc, "Default");
        repo.importProfile("Bad", "not json at all");
    }

    @Test
    public void noTemporaryFilesAreLeftBehind() {
        wc.data.put(1, element("A"));
        repo.ensureInitialized(wc, "Default");
        repo.createBlank("X");
        for (File f : dir.listFiles()) {
            assertFalse(f.getName(), f.getName().endsWith(".tmp"));
        }
    }
}
