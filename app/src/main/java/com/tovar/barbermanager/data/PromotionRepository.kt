package com.tovar.barbermanager.data


import com.tovar.barbermanager.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

sealed interface CouponCheck {
    data class Valid(val coupon: Coupon, val discountCents: Long) : CouponCheck
    data object NotFound : CouponCheck
    data object Inactive : CouponCheck
    data object NotStarted : CouponCheck
    data object Expired : CouponCheck
    data object Exhausted : CouponCheck
    data object AlreadyUsed : CouponCheck
    data object NotApplicable : CouponCheck
}

data class PromotionItem(val promotion: Promotion, val coupon: Coupon?)

class PromotionRepository(db: AppDatabase) {
    private val promotions = db.promotionDao()
    private val coupons = db.couponDao()
    private val redemptions = db.couponRedemptionDao()

    /** Promociones activas y vigentes, con su cupón (RF08.01, RF08.02). */
    fun observeAvailable(): Flow<List<PromotionItem>> =
        combine(promotions.observeAll(), coupons.observeAll()) { promos, all ->
            val now = System.currentTimeMillis()
            promos.filter { it.active && now in it.startsAt..it.endsAt }
                .map { p -> PromotionItem(p, all.firstOrNull { it.promotionId == p.id && it.active }) }
        }

    /** Valida vigencia y condiciones antes de aplicar (RF08.03, RF08.04). */
    suspend fun validate(code: String, service: Service, userId: Long): CouponCheck {
        val now = System.currentTimeMillis()
        val coupon = coupons.getByCode(code.trim().uppercase()) ?: return CouponCheck.NotFound
        if (!coupon.active) return CouponCheck.Inactive
        if (now < coupon.startsAt) return CouponCheck.NotStarted
        if (now > coupon.endsAt) return CouponCheck.Expired
        val max = coupon.maxUses
        if (max != null && coupon.currentUses >= max) return CouponCheck.Exhausted
        if (redemptions.countForUser(coupon.id, userId) > 0) return CouponCheck.AlreadyUsed
        val cat = coupon.serviceCategory
        if (cat != null && cat != service.category.name) return CouponCheck.NotApplicable
        if (service.priceCents < coupon.minPriceCents) return CouponCheck.NotApplicable
        return CouponCheck.Valid(coupon, discountFor(coupon, service.priceCents))
    }

    companion object {
        fun discountFor(coupon: Coupon, priceCents: Long): Long {
            val raw = if (coupon.discountType == DiscountType.PERCENT)
                priceCents * coupon.discountValue / 100
            else coupon.discountValue
            return raw.coerceIn(0L, priceCents)
        }
    }
}