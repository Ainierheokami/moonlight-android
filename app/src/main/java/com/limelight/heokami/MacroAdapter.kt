package com.limelight.heokami

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.limelight.R
import java.util.Collections
import android.content.Context

// ... MacroAction 数据类

class MacroAdapter(private val actions: MutableList<MacroAction>, private val dialog: AlertDialog, private val showAddMacroDialog: (Int) -> Unit, private val context: Context) :
    ListAdapter<MacroAction, MacroAdapter.MacroViewHolder>(MacroDiffCallback()) {

    inner class MacroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val editText: EditText = itemView.findViewById(R.id.item_edit_text)
        val editButton: Button = itemView.findViewById(R.id.item_edit_button)
        val deleteButton: Button = itemView.findViewById(R.id.item_delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MacroViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.macro_item, parent, false)
        return MacroViewHolder(itemView)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MacroViewHolder, position: Int) {
        val action = getItem(position)
//        holder.editText.setText("Index: $position, Type: ${action.type}, Data: ${action.data}")
        var dataText = "${action.data}"
        val typeText = getDisplayNameByType(action.type, context)

        when(action.type){
            MacroType.KEY_DOWN.toString(), MacroType.KEY_UP.toString() -> dataText = VirtualKeyboardVkCode.getVKNameByCode(action.data)
            MacroType.KEY_TOGGLE.toString() -> dataText = "按键id ${action.data}"
            MacroType.KEY_TOGGLE_GROUP.toString() -> dataText = "组id ${action.data}"
            MacroType.SLEEP.toString() -> dataText = "${action.data}ms"
            MacroType.TOUCH_TOGGLE.toString() ->
                when(action.data){
                    0 -> dataText = context.getString(R.string.game_switch_to_multi_touch_mode)
                    1 -> dataText = context.getString(R.string.game_switch_to_touch_pad_mode)
                    2 -> dataText = context.getString(R.string.game_switch_to_mouse_mode)
                }
            MacroType.PORTAL_TOGGLE.toString() ->
                when(action.data){
                    1 -> dataText = context.getString(R.string.macro_portal_toggle_on)
                    2 -> dataText = context.getString(R.string.macro_portal_toggle_off)
                    else -> dataText = context.getString(R.string.macro_portal_toggle_toggle)
                }
        }

        holder.editText.setText("Index: $position, Type: ${typeText}, Data: $dataText")
        holder.editText.isFocusable = false

        holder.deleteButton.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) { // 检查位置是否有效
                val newList = currentList.toMutableList()
                if (adapterPosition < newList.size) { // 再次检查，防止并发问题
                    newList.removeAt(adapterPosition)
                    updateActions(newList)
                }
            }
        }

        holder.editButton.setOnClickListener {
            showAddMacroDialog(position)
            dialog.dismiss()
        }
    }

    class MacroDiffCallback : DiffUtil.ItemCallback<MacroAction>() {
        override fun areItemsTheSame(oldItem: MacroAction, newItem: MacroAction): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: MacroAction, newItem: MacroAction): Boolean {
            return oldItem == newItem
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val newList = currentList.toMutableList()
        Collections.swap(newList, fromPosition, toPosition)
        updateActions(newList)
    }

    private fun updateActions(newActions: List<MacroAction>) {
        actions.clear()
        actions.addAll(newActions)
        submitList(newActions)
    }
}
