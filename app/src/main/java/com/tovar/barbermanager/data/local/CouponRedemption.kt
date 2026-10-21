package com.tovar.barbermanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "coupon_redemptions",
    foreignKeys = [
        ForeignKey(
            entity = Coupon::class,
            parentColumns = ["id"],
            childColumns = ["couponId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserAccount::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Appointment::class,
            parentColumns = ["id"],
            childColumns = ["appointmentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["couponId"]), Index(value = ["userId"]), Index(value = ["appointmentId"])]
)
data class CouponRedemption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val couponId: Long,
    val userId: Long,
    val appointmentId: Long? = null,
    val discountCents: Long,
    val appliedAt: Long = System.currentTimeMillis()
)
