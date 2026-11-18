package com.tovar.barbermanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BarberBlockedSlotDao {
    @Query("SELECT * FROM barber_blocked_slots WHERE active = 1 ORDER BY date, startTime")
    fun observeActive(): Flow<List<BarberBlockedSlot>>
    @Insert suspend fun insert(slot: BarberBlockedSlot): Long
    @Update suspend fun update(slot: BarberBlockedSlot)
    @Query("UPDATE barber_blocked_slots SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}

@Dao
interface AppointmentChangeDao {
    @Query("SELECT * FROM appointment_changes WHERE appointmentId = :appointmentId ORDER BY changedAt DESC")
    fun observeForAppointment(appointmentId: Long): Flow<List<AppointmentChange>>
    @Insert suspend fun insert(change: AppointmentChange): Long
}

@Dao
interface PromotionDao {
    @Query("SELECT * FROM promotions ORDER BY startsAt DESC")
    fun observeAll(): Flow<List<Promotion>>
    @Insert suspend fun insert(promotion: Promotion): Long
    @Update suspend fun update(promotion: Promotion)
    @Query("UPDATE promotions SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("SELECT COUNT(*) FROM promotions")
    suspend fun count(): Int

    @Query("SELECT * FROM promotions WHERE id = :id")
    suspend fun getById(id: Long): Promotion?
}

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupons ORDER BY code COLLATE NOCASE")
    fun observeAll(): Flow<List<Coupon>>
    @Query("SELECT * FROM coupons WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Coupon?
    @Query("SELECT * FROM coupons WHERE id = :id")
    suspend fun getById(id: Long): Coupon?
    @Query("SELECT * FROM coupons WHERE promotionId = :promotionId ORDER BY id LIMIT 1")
    suspend fun getByPromotion(promotionId: Long): Coupon?
    @Insert suspend fun insert(coupon: Coupon): Long
    @Update suspend fun update(coupon: Coupon)
    @Query("UPDATE coupons SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
    @Query("SELECT COUNT(*) FROM coupons")
    suspend fun count(): Int
    @Query("UPDATE coupons SET currentUses = currentUses + 1 WHERE id = :id AND (maxUses IS NULL OR currentUses < maxUses)")
    suspend fun incrementUses(id: Long): Int
}

@Dao
interface CouponRedemptionDao {
    @Query("SELECT * FROM coupon_redemptions WHERE userId = :userId ORDER BY appliedAt DESC")
    fun observeForUser(userId: Long): Flow<List<CouponRedemption>>
    @Insert suspend fun insert(redemption: CouponRedemption): Long
    @Query("SELECT COUNT(*) FROM coupon_redemptions WHERE couponId = :couponId AND userId = :userId")
    suspend fun countForUser(couponId: Long, userId: Long): Int

    /** Todos los cupones aplicados (administrador). */
    @Query(
        "SELECT r.*, c.code AS couponCode, a.clientName AS clientName " +
            "FROM coupon_redemptions r " +
            "INNER JOIN coupons c ON c.id = r.couponId " +
            "LEFT JOIN appointments a ON a.id = r.appointmentId " +
            "ORDER BY r.appliedAt DESC"
    )
    fun observeAllUsage(): Flow<List<CouponUsage>>

    /** Cupones aplicados por un usuario (cliente). */
    @Query(
        "SELECT r.*, c.code AS couponCode, a.clientName AS clientName " +
            "FROM coupon_redemptions r " +
            "INNER JOIN coupons c ON c.id = r.couponId " +
            "LEFT JOIN appointments a ON a.id = r.appointmentId " +
            "WHERE r.userId = :userId " +
            "ORDER BY r.appliedAt DESC"
    )
    fun observeUsageForUser(userId: Long): Flow<List<CouponUsage>>
}
