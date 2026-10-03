package com.limelight.heokami.layout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Named virtual-keyboard layouts ("profiles") and which one applies where.
 *
 * <p>The keyboard code keeps reading and writing one set of elements (the "working copy", the
 * existing {@code OSK} SharedPreferences). A profile is a stored snapshot of such a set. Editing
 * always changes the working copy, which belongs to the <em>active</em> profile; the snapshot is
 * brought up to date whenever the active profile changes, is copied or exported. Switching is
 * therefore: store working copy into the active profile, load the target into the working copy.</p>
 *
 * <p>Profiles can be bound to the whole app (global default), to a computer, or to one app of a
 * computer; the most specific binding wins. No Android dependencies, so it is unit tested.</p>
 *
 * <p>Layout: {@code index.json} (profiles, active id, bindings) and one {@code <id>.json} per
 * profile in the same element format the layout export uses. Files are replaced atomically.</p>
 */
public final class LayoutProfileRepository {
    /** The keyboard's current elements, keyed by element id. Implemented over the OSK prefs. */
    public interface WorkingCopy {
        Map<Integer, String> read();

        void replace(Map<Integer, String> elements);
    }

    public interface IdGenerator {
        String newId();
    }

    public interface Clock {
        long now();
    }

    public static final class Profile {
        public final String id;
        public final String name;
        public final long createdAt;
        public final long updatedAt;

        Profile(String id, String name, long createdAt, long updatedAt) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    public static final class AppBinding {
        public final String computerUuid;
        public final int appId;
        public final String appName;
        public final String profileId;

        AppBinding(String computerUuid, int appId, String appName, String profileId) {
            this.computerUuid = computerUuid;
            this.appId = appId;
            this.appName = appName;
            this.profileId = profileId;
        }
    }

    private static final int INDEX_VERSION = 1;
    private static final String INDEX_FILE = "index.json";

    private final File dir;
    private final IdGenerator ids;
    private final Clock clock;

    private final List<Profile> profiles = new ArrayList<>();
    private String activeId;
    private String globalBinding;
    private final Map<String, String> computerBindings = new LinkedHashMap<>();
    private final Map<String, AppBinding> appBindings = new LinkedHashMap<>();
    private boolean loaded;

    public LayoutProfileRepository(File dir) {
        this(dir, () -> UUID.randomUUID().toString(), System::currentTimeMillis);
    }

    public LayoutProfileRepository(File dir, IdGenerator ids, Clock clock) {
        this.dir = dir;
        this.ids = ids;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Loads the index, or on first use (or if it is unreadable) creates it with a single profile
     * holding whatever the keyboard currently has, so existing users keep their layout.
     */
    public synchronized void ensureInitialized(WorkingCopy workingCopy, String defaultName) {
        if (loaded) {
            return;
        }
        if (!loadIndex()) {
            profiles.clear();
            computerBindings.clear();
            appBindings.clear();
            globalBinding = null;
            Profile first = writeNewProfile(defaultName, workingCopy.read());
            activeId = first.id;
            saveIndex();
        }
        loaded = true;
    }

    // ---------------------------------------------------------------- queries

    public synchronized List<Profile> list() {
        return new ArrayList<>(profiles);
    }

    public synchronized String getActiveId() {
        return activeId;
    }

    public synchronized Profile get(String id) {
        return find(id);
    }

    public synchronized Profile getActive() {
        return find(activeId);
    }

    // ---------------------------------------------------------------- profile operations

    /** Stores the working copy into the active profile. Cheap; call before anything that copies. */
    public synchronized void syncActive(WorkingCopy workingCopy) {
        Profile active = find(activeId);
        if (active == null) {
            return;
        }
        writeElements(active.id, workingCopy.read());
        touch(active.id);
        saveIndex();
    }

    public synchronized Profile createBlank(String name) {
        Profile profile = writeNewProfile(name, new TreeMap<Integer, String>());
        saveIndex();
        return profile;
    }

    /** Forks what is on screen into a new profile and makes it the active one. */
    public synchronized Profile saveWorkingCopyAsNew(String name, WorkingCopy workingCopy) {
        syncActive(workingCopy);
        Profile profile = writeNewProfile(name, workingCopy.read());
        activeId = profile.id;
        saveIndex();
        return profile;
    }

    public synchronized Profile duplicate(String id, String name, WorkingCopy workingCopy) {
        if (find(id) == null) {
            return null;
        }
        if (id.equals(activeId)) {
            syncActive(workingCopy);
        }
        Map<Integer, String> elements = readElements(id);
        if (elements == null) {
            return null;
        }
        Profile profile = writeNewProfile(name, elements);
        saveIndex();
        return profile;
    }

    public synchronized Profile importProfile(String name, String layoutText) throws JSONException {
        Map<Integer, String> elements = parseLayoutText(layoutText);
        Profile profile = writeNewProfile(name, elements);
        saveIndex();
        return profile;
    }

    public synchronized boolean rename(String id, String name) {
        int index = indexOf(id);
        if (index < 0) {
            return false;
        }
        Profile old = profiles.get(index);
        profiles.set(index, new Profile(old.id, uniqueName(name, id), old.createdAt, clock.now()));
        saveIndex();
        return true;
    }

    /**
     * Deletes a profile (never the last one). If it was active, the first remaining profile is
     * loaded into the working copy. Bindings that pointed at it are dropped.
     */
    public synchronized boolean delete(String id, WorkingCopy workingCopy) {
        int index = indexOf(id);
        if (index < 0 || profiles.size() <= 1) {
            return false;
        }
        boolean wasActive = id.equals(activeId);
        if (!wasActive) {
            syncActive(workingCopy);
        }
        profiles.remove(index);
        new File(dir, id + ".json").delete();
        removeBindingsTo(id);

        if (wasActive) {
            String next = profiles.get(0).id;
            Map<Integer, String> elements = readElements(next);
            workingCopy.replace(elements != null ? elements : new TreeMap<Integer, String>());
            activeId = next;
        }
        saveIndex();
        return true;
    }

    /** Makes {@code id} the active profile: stores the working copy, then loads the target. */
    public synchronized boolean switchTo(String id, WorkingCopy workingCopy) {
        if (find(id) == null) {
            return false;
        }
        if (id.equals(activeId)) {
            return true;
        }
        Map<Integer, String> target = readElements(id);
        if (target == null) {
            return false;
        }
        syncActive(workingCopy);
        workingCopy.replace(target);
        activeId = id;
        saveIndex();
        return true;
    }

    /** The profile as layout-export text (same format as the existing layout export). */
    public synchronized String exportText(String id, WorkingCopy workingCopy) throws JSONException {
        if (find(id) == null) {
            return null;
        }
        if (id.equals(activeId)) {
            syncActive(workingCopy);
        }
        Map<Integer, String> elements = readElements(id);
        return elements == null ? null : toLayoutText(elements).toString(4);
    }

    // ---------------------------------------------------------------- bindings

    public synchronized void bindGlobal(String profileId) {
        globalBinding = profileId != null && find(profileId) != null ? profileId : null;
        saveIndex();
    }

    public synchronized void bindComputer(String computerUuid, String profileId) {
        if (computerUuid == null) {
            return;
        }
        if (profileId != null && find(profileId) != null) {
            computerBindings.put(computerUuid, profileId);
        } else {
            computerBindings.remove(computerUuid);
        }
        saveIndex();
    }

    public synchronized void bindApp(String computerUuid, int appId, String appName, String profileId) {
        if (computerUuid == null) {
            return;
        }
        String key = appKey(computerUuid, appId);
        if (profileId != null && find(profileId) != null) {
            appBindings.put(key, new AppBinding(computerUuid, appId, appName == null ? "" : appName, profileId));
        } else {
            appBindings.remove(key);
        }
        saveIndex();
    }

    public synchronized String getGlobalBinding() {
        return globalBinding;
    }

    public synchronized String getComputerBinding(String computerUuid) {
        return computerBindings.get(computerUuid);
    }

    public synchronized AppBinding getAppBinding(String computerUuid, int appId) {
        return appBindings.get(appKey(computerUuid, appId));
    }

    public synchronized Map<String, String> getComputerBindings() {
        return new LinkedHashMap<>(computerBindings);
    }

    public synchronized List<AppBinding> getAppBindings() {
        return new ArrayList<>(appBindings.values());
    }

    /**
     * The profile for this session: the app binding, else the computer binding, else the global
     * default, else null (keep whatever is active). An app binding also matches by name if the
     * app id changed on the host (ids are not stable on every host).
     */
    public synchronized String resolve(String computerUuid, int appId, String appName) {
        String candidate = null;
        if (computerUuid != null) {
            AppBinding app = appBindings.get(appKey(computerUuid, appId));
            if (app == null && appName != null && !appName.isEmpty()) {
                for (AppBinding b : appBindings.values()) {
                    if (computerUuid.equals(b.computerUuid) && appName.equals(b.appName)) {
                        app = b;
                        break;
                    }
                }
            }
            if (app != null) {
                candidate = app.profileId;
            }
            if (candidate == null) {
                candidate = computerBindings.get(computerUuid);
            }
        }
        if (candidate == null) {
            candidate = globalBinding;
        }
        return candidate != null && find(candidate) != null ? candidate : null;
    }

    /** Activates the profile bound to this computer/app, if any. Returns the active id. */
    public synchronized String applyForSession(String computerUuid, int appId, String appName,
                                               WorkingCopy workingCopy) {
        String resolved = resolve(computerUuid, appId, appName);
        if (resolved != null && !resolved.equals(activeId)) {
            switchTo(resolved, workingCopy);
        }
        return activeId;
    }

    // ---------------------------------------------------------------- layout text

    /**
     * Parses layout text: the current export format ({@code "elements": [...]} with ELEMENT_ID) and
     * the legacy flat format (numeric keys mapping to element JSON).
     */
    public static Map<Integer, String> parseLayoutText(String data) throws JSONException {
        JSONObject root = new JSONObject(data);
        Map<Integer, String> out = new TreeMap<>();
        if (root.has("elements")) {
            JSONArray array = root.getJSONArray("elements");
            for (int i = 0; i < array.length(); i++) {
                JSONObject element = array.getJSONObject(i);
                if (!element.has("ELEMENT_ID")) {
                    continue;
                }
                int id = element.getInt("ELEMENT_ID");
                JSONObject copy = new JSONObject(element.toString());
                copy.remove("ELEMENT_ID");
                out.put(id, copy.toString());
            }
        } else {
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    int id = Integer.parseInt(key);
                    Object value = root.get(key);
                    out.put(id, value instanceof JSONObject ? value.toString() : String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // not an element entry
                }
            }
        }
        return out;
    }

    static JSONObject toLayoutText(Map<Integer, String> elements) throws JSONException {
        JSONObject root = new JSONObject();
        JSONObject metadata = new JSONObject();
        metadata.put("app_identifier", "com.limelight.heokami");
        metadata.put("format_version", 2);
        root.put("metadata", metadata);
        JSONArray array = new JSONArray();
        for (Map.Entry<Integer, String> entry : new TreeMap<>(elements).entrySet()) {
            JSONObject element = new JSONObject(entry.getValue());
            element.put("ELEMENT_ID", entry.getKey());
            array.put(element);
        }
        root.put("elements", array);
        return root;
    }

    // ---------------------------------------------------------------- storage

    private static String appKey(String computerUuid, int appId) {
        return computerUuid + "|" + appId;
    }

    private Profile find(String id) {
        int index = indexOf(id);
        return index < 0 ? null : profiles.get(index);
    }

    private int indexOf(String id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void touch(String id) {
        int index = indexOf(id);
        if (index >= 0) {
            Profile old = profiles.get(index);
            profiles.set(index, new Profile(old.id, old.name, old.createdAt, clock.now()));
        }
    }

    private Profile writeNewProfile(String name, Map<Integer, String> elements) {
        String id = ids.newId();
        long now = clock.now();
        Profile profile = new Profile(id, uniqueName(name, null), now, now);
        writeElements(id, elements);
        profiles.add(profile);
        return profile;
    }

    private String uniqueName(String requested, String ignoreId) {
        String base = requested == null || requested.trim().isEmpty() ? "Layout" : requested.trim();
        String candidate = base;
        int n = 2;
        while (nameTaken(candidate, ignoreId)) {
            candidate = base + " (" + n++ + ")";
        }
        return candidate;
    }

    private boolean nameTaken(String name, String ignoreId) {
        for (Profile p : profiles) {
            if (p.name.equalsIgnoreCase(name) && !p.id.equals(ignoreId)) {
                return true;
            }
        }
        return false;
    }

    private void removeBindingsTo(String id) {
        if (id.equals(globalBinding)) {
            globalBinding = null;
        }
        // Iterators, not removeIf: Collection.removeIf needs API 24 and minSdk is 21.
        for (Iterator<String> it = computerBindings.values().iterator(); it.hasNext(); ) {
            if (id.equals(it.next())) {
                it.remove();
            }
        }
        for (Iterator<AppBinding> it = appBindings.values().iterator(); it.hasNext(); ) {
            if (id.equals(it.next().profileId)) {
                it.remove();
            }
        }
    }

    private void writeElements(String id, Map<Integer, String> elements) {
        try {
            writeAtomically(new File(dir, id + ".json"), toLayoutText(elements).toString());
        } catch (JSONException | IOException e) {
            throw new IllegalStateException("Unable to store layout profile " + id, e);
        }
    }

    /** Null if the profile file is missing or unreadable. */
    private Map<Integer, String> readElements(String id) {
        try {
            String text = readFile(new File(dir, id + ".json"));
            return text == null ? null : parseLayoutText(text);
        } catch (JSONException | IOException e) {
            return null;
        }
    }

    private boolean loadIndex() {
        try {
            String text = readFile(new File(dir, INDEX_FILE));
            if (text == null) {
                return false;
            }
            JSONObject root = new JSONObject(text);
            profiles.clear();
            computerBindings.clear();
            appBindings.clear();
            JSONArray array = root.getJSONArray("profiles");
            for (int i = 0; i < array.length(); i++) {
                JSONObject p = array.getJSONObject(i);
                profiles.add(new Profile(p.getString("id"), p.getString("name"),
                        p.optLong("createdAt"), p.optLong("updatedAt")));
            }
            if (profiles.isEmpty()) {
                return false;
            }
            activeId = root.optString("activeId", "");
            if (find(activeId) == null) {
                activeId = profiles.get(0).id;
            }
            JSONObject bindings = root.optJSONObject("bindings");
            if (bindings != null) {
                String global = bindings.optString("global", "");
                globalBinding = find(global) != null ? global : null;
                JSONObject computers = bindings.optJSONObject("computers");
                if (computers != null) {
                    Iterator<String> keys = computers.keys();
                    while (keys.hasNext()) {
                        String uuid = keys.next();
                        String profileId = computers.getString(uuid);
                        if (find(profileId) != null) {
                            computerBindings.put(uuid, profileId);
                        }
                    }
                }
                JSONArray apps = bindings.optJSONArray("apps");
                if (apps != null) {
                    for (int i = 0; i < apps.length(); i++) {
                        JSONObject a = apps.getJSONObject(i);
                        String profileId = a.getString("profileId");
                        if (find(profileId) != null) {
                            AppBinding b = new AppBinding(a.getString("computer"), a.getInt("appId"),
                                    a.optString("appName", ""), profileId);
                            appBindings.put(appKey(b.computerUuid, b.appId), b);
                        }
                    }
                }
            }
            return true;
        } catch (JSONException | IOException e) {
            return false;
        }
    }

    private void saveIndex() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", INDEX_VERSION);
            root.put("activeId", activeId);
            JSONArray array = new JSONArray();
            for (Profile p : profiles) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("createdAt", p.createdAt);
                o.put("updatedAt", p.updatedAt);
                array.put(o);
            }
            root.put("profiles", array);

            JSONObject bindings = new JSONObject();
            bindings.put("global", globalBinding == null ? "" : globalBinding);
            JSONObject computers = new JSONObject();
            for (Map.Entry<String, String> e : computerBindings.entrySet()) {
                computers.put(e.getKey(), e.getValue());
            }
            bindings.put("computers", computers);
            JSONArray apps = new JSONArray();
            for (AppBinding b : appBindings.values()) {
                JSONObject o = new JSONObject();
                o.put("computer", b.computerUuid);
                o.put("appId", b.appId);
                o.put("appName", b.appName);
                o.put("profileId", b.profileId);
                apps.put(o);
            }
            bindings.put("apps", apps);
            root.put("bindings", bindings);

            writeAtomically(new File(dir, INDEX_FILE), root.toString());
        } catch (JSONException | IOException e) {
            throw new IllegalStateException("Unable to store layout profile index", e);
        }
    }

    private static String readFile(File file) throws IOException {
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private void writeAtomically(File target, String text) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        File tmp = new File(dir, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            // renameTo replaces atomically on Android's filesystems; some JVMs refuse to overwrite.
            if (target.exists() && !target.delete()) {
                throw new IOException("Cannot replace " + target);
            }
            if (!tmp.renameTo(target)) {
                throw new IOException("Cannot move " + tmp + " to " + target);
            }
        }
    }
}
