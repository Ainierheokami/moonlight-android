package com.limelight.heokami.layout

import android.content.Context
import android.util.Log
import com.limelight.R
import org.json.JSONObject
import java.io.File

/**
 * Android entry point to the keyboard layout profiles: owns the repository for the app's files
 * directory and the OSK working copy it switches in and out of.
 */
object LayoutProfileManager {
    private const val TAG = "LayoutProfiles"
    private const val DIR_NAME = "layout_profiles"

    @Volatile
    private var repository: LayoutProfileRepository? = null

    private fun dir(context: Context) = File(context.applicationContext.filesDir, DIR_NAME)

    fun workingCopy(context: Context): LayoutProfileRepository.WorkingCopy =
        OskWorkingCopy(context.applicationContext)

    /** The repository, created and (on first run) seeded from the current keyboard layout. */
    @Synchronized
    fun get(context: Context): LayoutProfileRepository {
        repository?.let { return it }
        val app = context.applicationContext
        val repo = LayoutProfileRepository(dir(app))
        repo.ensureInitialized(workingCopy(app), app.getString(R.string.layout_default_name))
        repository = repo
        return repo
    }

    /**
     * Activates the layout bound to this computer/app before the stream builds its keyboard.
     * Never throws: a broken profile store must not stop a stream from starting.
     */
    @JvmStatic
    fun applyForSession(context: Context, computerUuid: String?, appId: Int, appName: String?) {
        try {
            val app = context.applicationContext
            get(app).applyForSession(computerUuid, appId, appName, workingCopy(app))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to apply the keyboard layout for this session", e)
        }
    }

    fun switchTo(context: Context, id: String): Boolean {
        val app = context.applicationContext
        return get(app).switchTo(id, workingCopy(app))
    }

    /** Makes the repository re-read the files (after a backup restore replaced them). */
    @Synchronized
    fun reload() {
        repository = null
    }

    // ---- backup / restore

    /** Every profile file as name to text, with the active profile brought up to date first. */
    fun exportForBackup(context: Context): JSONObject {
        val app = context.applicationContext
        val repo = get(app)
        repo.syncActive(workingCopy(app))
        val files = JSONObject()
        dir(app).listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach {
            files.put(it.name, it.readText(Charsets.UTF_8))
        }
        return files
    }

    /**
     * Replaces the stored profiles with those from a backup. Files are written next to the old
     * ones first and only then swapped in, so a failure halfway keeps the previous profiles.
     */
    fun importFromBackup(context: Context, files: JSONObject) {
        val app = context.applicationContext
        val target = dir(app)
        val staging = File(app.filesDir, "$DIR_NAME.restoring")
        staging.deleteRecursively()
        staging.mkdirs()
        val names = files.keys()
        while (names.hasNext()) {
            val name = names.next()
            // Only plain "<id>.json" files; never let a backup write outside the directory.
            if (name.contains('/') || name.contains('\\') || name.startsWith(".") || !name.endsWith(".json")) continue
            File(staging, name).writeText(files.getString(name), Charsets.UTF_8)
        }
        if (!File(staging, "index.json").isFile) {
            staging.deleteRecursively()
            return
        }
        val old = File(app.filesDir, "$DIR_NAME.old")
        old.deleteRecursively()
        if (target.exists() && !target.renameTo(old)) {
            staging.deleteRecursively()
            return
        }
        if (!staging.renameTo(target)) {
            old.renameTo(target)
            staging.deleteRecursively()
            return
        }
        old.deleteRecursively()
        reload()
    }
}
