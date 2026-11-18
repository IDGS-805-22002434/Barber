package com.tovar.barbermanager.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.local.AppNotification
import com.tovar.barbermanager.data.local.NotificationStatus
import com.tovar.barbermanager.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val showRecipient: Boolean,
    private val onClick: (AppNotification) -> Unit
) : ListAdapter<AppNotification, NotificationAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        showRecipient,
        onClick
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val b: ItemNotificationBinding,
        private val showRecipient: Boolean,
        private val onClick: (AppNotification) -> Unit
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: AppNotification) = with(b) {
            val ctx = root.context
            notificationTitle.text = item.title
            notificationMessage.text = item.message
            notificationDate.text = DATE.format(Date(item.createdAt))
            unreadDot.isVisible = !item.isRead && !showRecipient
            notificationRecipient.isVisible = showRecipient
            notificationRecipient.text = ctx.getString(R.string.notif_recipient, item.userEmail)
            notificationStatus.setText(
                when (item.status) {
                    NotificationStatus.ENVIADA -> R.string.notif_status_sent
                    NotificationStatus.FALLIDA -> R.string.notif_status_failed
                    else -> R.string.notif_status_pending
                }
            )
            notificationFailedHint.isVisible =
                item.status == NotificationStatus.FALLIDA && !showRecipient
            root.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val DATE = SimpleDateFormat(
            "d MMM yyyy, HH:mm",
            Locale.Builder().setLanguage("es").setRegion("MX").build()
        )
        val DIFF = object : DiffUtil.ItemCallback<AppNotification>() {
            override fun areItemsTheSame(a: AppNotification, b: AppNotification) = a.id == b.id
            override fun areContentsTheSame(a: AppNotification, b: AppNotification) = a == b
        }
    }
}
