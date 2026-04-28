package com.andene.spectra.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andene.spectra.R
import com.andene.spectra.data.models.DeviceCategory
import com.andene.spectra.data.models.DeviceProfile

class DeviceAdapter(
    private val onDeviceClick: (DeviceProfile) -> Unit,
    private val onDeviceLongClick: (DeviceProfile) -> Unit = {}
) : ListAdapter<DeviceProfile, DeviceAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: TextView = view.findViewById(R.id.deviceIcon)
        private val name: TextView = view.findViewById(R.id.itemName)
        private val detail: TextView = view.findViewById(R.id.itemDetail)
        private val commands: TextView = view.findViewById(R.id.itemCommands)

        fun bind(device: DeviceProfile) {
            icon.text = when (device.category) {
                DeviceCategory.TV -> "📺"
                DeviceCategory.AC -> "❄️"
                DeviceCategory.PROJECTOR -> "📽"
                DeviceCategory.SPEAKER -> "🔊"
                DeviceCategory.LIGHT -> "💡"
                DeviceCategory.FAN -> "🌀"
                DeviceCategory.SET_TOP_BOX -> "📦"
                DeviceCategory.UNKNOWN -> "📡"
            }

            name.text = device.name ?: "Unnamed Device"

            val parts = mutableListOf<String>()
            device.manufacturer?.let { parts.add(it) }
            device.irProfile?.protocol?.let { parts.add(it.name) }
            detail.text = parts.joinToString(" · ").ifEmpty { device.category.name }

            val cmdCount = device.irProfile?.commands?.size ?: 0
            commands.text = if (cmdCount > 0) "$cmdCount cmds" else ""

            itemView.setOnClickListener { onDeviceClick(device) }
            itemView.setOnLongClickListener {
                onDeviceLongClick(device)
                true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DeviceProfile>() {
        override fun areItemsTheSame(a: DeviceProfile, b: DeviceProfile) = a.id == b.id
        override fun areContentsTheSame(a: DeviceProfile, b: DeviceProfile) = a == b
    }
}
