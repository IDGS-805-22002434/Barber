package com.tovar.barbermanager.ui.promotions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.local.CouponUsage
import com.tovar.barbermanager.databinding.ItemCouponUsageBinding
import com.tovar.barbermanager.ui.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CouponUsageAdapter(
    private val showClient: Boolean
) : ListAdapter<CouponUsage, CouponUsageAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCouponUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false), showClient)

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val b: ItemCouponUsageBinding, private val showClient: Boolean) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: CouponUsage) = with(b) {
            val ctx = root.context
            val redemption = item.redemption
            usageTitle.text = ctx.getString(
                R.string.usage_item_title,
                item.couponCode,
                Format.price(redemption.discountCents)
            )
            usageClient.isVisible = showClient && !item.clientName.isNullOrBlank()
            usageClient.text = ctx.getString(R.string.usage_item_client, item.clientName.orEmpty())
            usageFolio.isVisible = redemption.appointmentId != null
            usageFolio.text = ctx.getString(R.string.usage_item_folio, redemption.appointmentId ?: 0L)
            usageDate.text = DATE.format(Date(redemption.appliedAt))
        }
    }

    private companion object {
        val DATE = SimpleDateFormat(
            "d MMM yyyy, HH:mm",
            Locale.Builder().setLanguage("es").setRegion("MX").build()
        )
        val DIFF = object : DiffUtil.ItemCallback<CouponUsage>() {
            override fun areItemsTheSame(a: CouponUsage, b: CouponUsage) = a.redemption.id == b.redemption.id
            override fun areContentsTheSame(a: CouponUsage, b: CouponUsage) = a == b
        }
    }
}
