package com.tovar.barbermanager.data


import com.tovar.barbermanager.data.local.*
import androidx.room.withTransaction
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

/** Datos del formulario de administración: una promoción con un cupón opcional. */
data class PromotionForm(
    val promotionId: Long = 0L,
    val couponId: Long = 0L,
    val title: String,
    val description: String,
    val conditions: String,
    val startsAt: Long,
    val endsAt: Long,
    val active: Boolean,
    val code: String?,
    val discountType: String,
    val discountValue: Long,
    val serviceCategory: String?,
    val minPriceCents: Long,
    val maxUses: Int?
)

sealed interface PromotionSaveResult {
    data object Saved : PromotionSaveResult
    data object CodeTaken : PromotionSaveResult
}

class PromotionRepository(private val db: AppDatabase) {
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

    /** Administrador: todas las promociones, vigentes o no (RF08.01). */
    fun observeAll(): Flow<List<PromotionItem>> =
        combine(promotions.observeAll(), coupons.observeAll()) { promos, all ->
            promos.map { p -> PromotionItem(p, all.firstOrNull { it.promotionId == p.id }) }
        }

    suspend fun getItem(promotionId: Long): PromotionItem? {
        val promotion = promotions.getById(promotionId) ?: return null
        return PromotionItem(promotion, coupons.getByPromotion(promotionId))
    }

    /** Alta y edición de promociones con su cupón (administrador). */
    suspend fun save(form: PromotionForm): PromotionSaveResult = db.withTransaction {
        val code = form.code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (code != null) {
            val other = coupons.getByCode(code)
            if (other != null && other.id != form.couponId) {
                return@withTransaction PromotionSaveResult.CodeTaken
            }
        }
        val promotion = Promotion(
            id = form.promotionId,
            title = form.title,
            description = form.description,
            conditions = form.conditions,
            startsAt = form.startsAt,
            endsAt = form.endsAt,
            active = form.active
        )
        val promotionId = if (form.promotionId == 0L) {
            promotions.insert(promotion)
        } else {
            promotions.update(promotion)
            form.promotionId
        }
        if (code != null) {
            val previous = if (form.couponId != 0L) coupons.getById(form.couponId) else null
            val coupon = Coupon(
                id = form.couponId,
                promotionId = promotionId,
                code = code,
                discountType = form.discountType,
                discountValue = form.discountValue,
                conditions = form.conditions,
                serviceCategory = form.serviceCategory,
                minPriceCents = form.minPriceCents,
                startsAt = form.startsAt,
                endsAt = form.endsAt,
                maxUses = form.maxUses,
                currentUses = previous?.currentUses ?: 0,
                active = form.active
            )
            if (form.couponId == 0L) coupons.insert(coupon) else coupons.update(coupon)
        } else if (form.couponId != 0L) {
            coupons.setActive(form.couponId, false)
        }
        PromotionSaveResult.Saved
    }

    /** Cupones aplicados: todos (administrador) o solo los del usuario (RF08.05). */
    fun observeUsage(userId: Long, isAdmin: Boolean): Flow<List<CouponUsage>> =
        if (isAdmin) redemptions.observeAllUsage() else redemptions.observeUsageForUser(userId)

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