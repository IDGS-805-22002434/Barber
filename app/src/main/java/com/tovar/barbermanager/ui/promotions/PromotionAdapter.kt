package com.tovar.barbermanager.ui.promotions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.PromotionItem
import com.tovar.barbermanager.data.local.DiscountType
import com.tovar.barbermanager.databinding.ItemPromotionBinding
import com.tovar.barbermanager.ui.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PromotionAdapter(
    private val onCopy: (String) -> Unit
) : ListAdapter<PromotionItem, PromotionAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPromotionBinding.inflate(LayoutInflater.from(parent.context), parent, false), onCopy)

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val b: ItemPromotionBinding,
        private val onCopy: (String) -> Unit
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: PromotionItem) = with(b) {
            val ctx = root.context
            val promo = item.promotion
            val coupon = item.coupon
            promoTitle.text = promo.title
            promoDescription.text = promo.description
            promoConditions.isVisible = promo.conditions.isNotBlank()
            promoConditions.text = ctx.getString(R.string.promo_conditions, promo.conditions)
            promoValidity.text = ctx.getString(R.string.promo_valid_until, DATE.format(Date(promo.endsAt)))

            promoDiscount.isVisible = coupon != null
            promoCodeRow.isVisible = coupon != null
            if (coupon != null) {
                promoDiscount.text = if (coupon.discountType == DiscountType.PERCENT)
                    ctx.getString(R.string.promo_discount_percent, coupon.discountValue.toInt())
                else ctx.getString(R.string.promo_discount_amount, Format.price(coupon.discountValue))
                promoCode.text = ctx.getString(R.string.promo_code_label, coupon.code)
                promoCopyButton.setOnClickListener { onCopy(coupon.code) }
            }
        }
    }

    private companion object {
        val DATE = SimpleDateFormat(
            "d 'de' MMMM 'de' yyyy",
            Locale.Builder().setLanguage("es").setRegion("MX").build()
        )
        val DIFF = object : DiffUtil.ItemCallback<PromotionItem>() {
            override fun areItemsTheSame(a: PromotionItem, b: PromotionItem) = a.promotion.id == b.promotion.id
            override fun areContentsTheSame(a: PromotionItem, b: PromotionItem) = a == b
        }
    }
}