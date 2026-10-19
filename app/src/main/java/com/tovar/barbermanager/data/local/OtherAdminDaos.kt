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
}

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupons ORDER BY code COLLATE NOCASE")
    fun observeAll(): Flow<List<Coupon>>
    @Query("SELECT * FROM coupons WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Coupon?
    @Insert suspend fun insert(coupon: Coupon): Long
    @Update suspend fun update(coupon: Coupon)
    @Query("UPDATE coupons SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}

@Dao
interface CouponRedemptionDao {
    @Query("SELECT * FROM coupon_redemptions WHERE userId = :userId ORDER BY appliedAt DESC")
    fun observeForUser(userId: Long): Flow<List<CouponRedemption>>
    @Insert suspend fun insert(redemption: CouponRedemption): Long
}

@Dao
interface AppNotificationDao {
    @Query("SELECT * FROM app_notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeForUser(userId: Long): Flow<List<AppNotification>>
    @Insert suspend fun insert(notification: AppNotification): Long
    @Query("UPDATE app_notifications SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}

@Dao
interface DeviceRegistrationDao {
    @Query("SELECT * FROM device_registrations WHERE userId = :userId AND active = 1")
    fun observeActiveForUser(userId: Long): Flow<List<DeviceRegistration>>
    @Insert suspend fun insert(registration: DeviceRegistration): Long
    @Update suspend fun update(registration: DeviceRegistration)
    @Query("UPDATE device_registrations SET active = :active WHERE fcmToken = :token")
    suspend fun setTokenActive(token: String, active: Boolean)
}
