package com.limelight.heokami.layout

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import com.limelight.R
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.utils.AppToast
import com.limelight.utils.OverlayAlertDialog

/**
 * The dialogs for switching, creating, copying, renaming, deleting and binding keyboard layouts.
 * Used both from Settings (no stream) and from the in-stream menu, where the current computer and
 * app are known, so a layout can also be bound to "this computer" / "this app".
 */
object LayoutProfileDialogs {
    /** What is being streamed, and how to refresh the keyboard after the active layout changed. */
    class StreamContext(
        val computerUuid: String?,
        val computerName: String?,
        val appId: Int,
        val appName: String?,
        val onLayoutChanged: () -> Unit
    )

    @JvmStatic
    fun show(activity: Activity, stream: StreamContext?) {
        val repo = LayoutProfileManager.get(activity)
        val profiles = repo.list()
        val activeId = repo.getActiveId()

        val labels = ArrayList<CharSequence>()
        for (p in profiles) {
            labels.add((if (p.id == activeId) "✓ " else "    ") + p.name + bindingHint(activity, repo, p.id, stream))
        }
        labels.add(activity.getString(R.string.layout_item_new))

        OverlayAlertDialog.Builder(activity)
            .setTitle(R.string.layout_dialog_title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < profiles.size) showProfileActions(activity, profiles[which].id, stream)
                else showNewMenu(activity, stream)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun bindingHint(activity: Activity, repo: LayoutProfileRepository, id: String, stream: StreamContext?): String {
        val tags = ArrayList<String>()
        if (repo.getGlobalBinding() == id) tags.add(activity.getString(R.string.layout_tag_global))
        if (stream?.computerUuid != null && repo.getComputerBinding(stream.computerUuid) == id) {
            tags.add(activity.getString(R.string.layout_tag_computer))
        }
        if (stream?.computerUuid != null && repo.getAppBinding(stream.computerUuid, stream.appId)?.profileId == id) {
            tags.add(activity.getString(R.string.layout_tag_app))
        }
        return if (tags.isEmpty()) "" else "  · " + tags.joinToString(" / ")
    }

    private fun showNewMenu(activity: Activity, stream: StreamContext?) {
        val items = arrayOf<CharSequence>(
            activity.getString(R.string.layout_new_blank),
            activity.getString(R.string.layout_new_copy)
        )
        OverlayAlertDialog.Builder(activity)
            .setTitle(R.string.layout_new_title)
            .setItems(items) { _, which ->
                val copy = which == 1
                promptName(activity, R.string.layout_new_title,
                    activity.getString(R.string.layout_name_default_new)) { name ->
                    val repo = LayoutProfileManager.get(activity)
                    val wc = LayoutProfileManager.workingCopy(activity)
                    val created = if (copy) {
                        repo.saveWorkingCopyAsNew(name, wc)
                    } else {
                        repo.createBlank(name).also { repo.switchTo(it.id, wc) }
                    }
                    toast(activity, activity.getString(R.string.layout_created, created.name))
                    stream?.onLayoutChanged?.invoke()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showProfileActions(activity: Activity, id: String, stream: StreamContext?) {
        val repo = LayoutProfileManager.get(activity)
        val profile = repo.get(id) ?: return
        val isActive = repo.getActiveId() == id
        val canDelete = repo.list().size > 1

        val actions = ArrayList<Pair<CharSequence, () -> Unit>>()
        if (!isActive) {
            actions.add(activity.getString(R.string.layout_action_use) to { switchTo(activity, id, stream) })
        }
        actions.add(activity.getString(R.string.layout_action_rename) to { rename(activity, id, stream) })
        actions.add(activity.getString(R.string.layout_action_duplicate) to { duplicate(activity, id, stream) })
        actions.add(activity.getString(R.string.layout_action_bind) to { showBindMenu(activity, id, stream) })
        if (canDelete) {
            actions.add(activity.getString(R.string.layout_action_delete) to { confirmDelete(activity, id, stream) })
        }

        OverlayAlertDialog.Builder(activity)
            .setTitle(profile.name)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun switchTo(activity: Activity, id: String, stream: StreamContext?) {
        if (LayoutProfileManager.switchTo(activity, id)) {
            val name = LayoutProfileManager.get(activity).get(id)?.name ?: ""
            toast(activity, activity.getString(R.string.layout_switched, name))
            stream?.onLayoutChanged?.invoke()
        }
    }

    private fun rename(activity: Activity, id: String, stream: StreamContext?) {
        val repo = LayoutProfileManager.get(activity)
        val current = repo.get(id)?.name ?: return
        promptName(activity, R.string.layout_rename_title, current) { name ->
            repo.rename(id, name)
        }
    }

    private fun duplicate(activity: Activity, id: String, stream: StreamContext?) {
        val repo = LayoutProfileManager.get(activity)
        val current = repo.get(id)?.name ?: return
        promptName(activity, R.string.layout_action_duplicate,
            activity.getString(R.string.layout_name_default_copy, current)) { name ->
            val copy = repo.duplicate(id, name, LayoutProfileManager.workingCopy(activity))
            if (copy != null) toast(activity, activity.getString(R.string.layout_created, copy.name))
        }
    }

    private fun confirmDelete(activity: Activity, id: String, stream: StreamContext?) {
        val repo = LayoutProfileManager.get(activity)
        val name = repo.get(id)?.name ?: return
        OverlayAlertDialog.Builder(activity)
            .setTitle(R.string.layout_delete_confirm_title)
            .setMessage(activity.getString(R.string.layout_delete_confirm_message, name))
            .setPositiveButton(R.string.layout_action_delete) { _, _ ->
                val wasActive = repo.getActiveId() == id
                if (repo.delete(id, LayoutProfileManager.workingCopy(activity))) {
                    toast(activity, activity.getString(R.string.layout_deleted, name))
                    if (wasActive) stream?.onLayoutChanged?.invoke()
                } else {
                    toast(activity, activity.getString(R.string.layout_cannot_delete_last))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBindMenu(activity: Activity, id: String, stream: StreamContext?) {
        val repo = LayoutProfileManager.get(activity)
        val profile = repo.get(id) ?: return
        val actions = ArrayList<Pair<CharSequence, () -> Unit>>()

        val isGlobal = repo.getGlobalBinding() == id
        actions.add(activity.getString(
            if (isGlobal) R.string.layout_bind_global_off else R.string.layout_bind_global_on) to {
            repo.bindGlobal(if (isGlobal) null else id)
            toast(activity, activity.getString(R.string.layout_bind_done))
        })

        val uuid = stream?.computerUuid
        if (stream != null && uuid != null) {
            val computerName = stream.computerName ?: uuid
            val isComputer = repo.getComputerBinding(uuid) == id
            val computerLabel = if (isComputer) activity.getString(R.string.layout_bind_computer_off, computerName)
                else activity.getString(R.string.layout_bind_computer, computerName)
            actions.add(computerLabel to {
                repo.bindComputer(uuid, if (isComputer) null else id)
                toast(activity, activity.getString(R.string.layout_bind_done))
            })
            val appName = stream.appName ?: ""
            val isApp = repo.getAppBinding(uuid, stream.appId)?.profileId == id
            val appLabel = if (isApp) activity.getString(R.string.layout_bind_app_off, appName)
                else activity.getString(R.string.layout_bind_app, appName)
            actions.add(appLabel to {
                repo.bindApp(uuid, stream.appId, appName, if (isApp) null else id)
                toast(activity, activity.getString(R.string.layout_bind_done))
            })
        } else {
            actions.add(activity.getString(R.string.layout_bind_pick_computer) to { pickComputer(activity, id) })
        }

        OverlayAlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.layout_bind_title, profile.name))
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickComputer(activity: Activity, profileId: String) {
        val computers = try {
            val db = ComputerDatabaseManager(activity)
            try { db.allComputers } finally { db.close() }
        } catch (e: Exception) {
            emptyList<ComputerDetails>()
        }
        if (computers.isEmpty()) {
            toast(activity, activity.getString(R.string.layout_no_computers))
            return
        }
        val repo = LayoutProfileManager.get(activity)
        val labels = computers.map { pc ->
            val bound = repo.getComputerBinding(pc.uuid)
            val mark = if (bound == profileId) "✓ " else "    "
            (mark + pc.name) as CharSequence
        }.toTypedArray()
        OverlayAlertDialog.Builder(activity)
            .setTitle(R.string.layout_pick_computer_title)
            .setItems(labels) { _, which ->
                val pc = computers[which]
                val already = repo.getComputerBinding(pc.uuid) == profileId
                repo.bindComputer(pc.uuid, if (already) null else profileId)
                toast(activity, activity.getString(R.string.layout_bind_done))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptName(activity: Activity, titleRes: Int, initial: String, onOk: (String) -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setText(initial)
            setSelection(text.length)
            setTextColor(0xFFF5F8FC.toInt())
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val holder = FrameLayout(activity).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        OverlayAlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(holder)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(activity: Activity, text: String) {
        AppToast.makeText(activity, text, AppToast.LENGTH_SHORT).show()
    }
}
