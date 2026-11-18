package com.tovar.barbermanager.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cupón aplicado por un usuario (RF08.05).
 * [userId] no tiene llave foránea: con sesión de servidor el id es el del servidor
 * y no existe como fila en la tabla local `users`.
 */
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

/** Cupón aplicado con el código y el cliente, para las listas de uso. */
data class CouponUsage(
    @Embedded val redemption: CouponRedemption,
    val couponCode: String,
    val clientName: String?
)
