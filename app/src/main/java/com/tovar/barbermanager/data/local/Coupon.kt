package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "coupons",
    foreignKeys = [
        ForeignKey(
            entity = Promotion::class,
            parentColumns = ["id"],
            childColumns = ["promotionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["code"], unique = true), Index(value = ["promotionId"])]
)
data class Coupon(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val promotionId: Long? = null,
    val code: String,
    val discountType: String,
    val discountValue: Long,
    val conditions: String = "",
    val serviceCategory: String? = null,
    val minPriceCents: Long = 0L,
    val startsAt: Long,
    val endsAt: Long,
    val maxUses: Int? = null,
    val currentUses: Int = 0,
    val active: Boolean = true,

)

object DiscountType {
    const val PERCENT = "PERCENT"
    const val AMOUNT_CENTS = "AMOUNT_CENTS"
}
