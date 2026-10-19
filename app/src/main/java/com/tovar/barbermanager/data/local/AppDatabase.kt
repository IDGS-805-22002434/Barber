package com.tovar.barbermanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Service::class, Barber::class, Specialty::class,
        BarberSpecialty::class, Appointment::class, UserAccount::class,
        BarberSchedule::class, BarberBlockedSlot::class, AppointmentChange::class,
        Promotion::class, Coupon::class, CouponRedemption::class,
        AppNotification::class, DeviceRegistration::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun serviceDao(): ServiceDao
    abstract fun barberDao(): BarberDao
    abstract fun specialtyDao(): SpecialtyDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun userDao(): UserDao
    abstract fun barberScheduleDao(): BarberScheduleDao
    abstract fun barberBlockedSlotDao(): BarberBlockedSlotDao
    abstract fun appointmentChangeDao(): AppointmentChangeDao
    abstract fun promotionDao(): PromotionDao
    abstract fun couponDao(): CouponDao
    abstract fun couponRedemptionDao(): CouponRedemptionDao
    abstract fun appNotificationDao(): AppNotificationDao
    abstract fun deviceRegistrationDao(): DeviceRegistrationDao


    companion object {
        private const val DATABASE_NAME = "barbermanager.db"

        /**
         * v1 → v2 (RF03.03): la columna de texto `barbers.specialty` pasa a ser
         * el catálogo `specialties` + relación N:N `barber_specialties`.
         * Las especialidades ya escritas en los barberos se conservan y quedan enlazadas.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `specialties` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`active` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_specialties_name` ON `specialties` (`name`)"
                )
                db.execSQL(
                    "INSERT INTO specialties (name, active) " +
                            "SELECT TRIM(specialty), 1 FROM barbers " +
                            "WHERE TRIM(specialty) <> '' GROUP BY specialty"
                )

                // Guarda la relación barbero↔especialidad antes de recrear la tabla.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tmp_barber_specialty` (" +
                            "`barberId` INTEGER NOT NULL, `name` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO tmp_barber_specialty (barberId, name) " +
                            "SELECT id, TRIM(specialty) FROM barbers WHERE TRIM(specialty) <> ''"
                )

                // Recrea barbers sin la columna de texto libre.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `barbers_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`yearsExperience` INTEGER NOT NULL, " +
                            "`active` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `barbers_new` (`id`, `name`, `yearsExperience`, `active`) " +
                            "SELECT `id`, `name`, `yearsExperience`, `active` FROM `barbers`"
                )
                db.execSQL("DROP TABLE `barbers`")
                db.execSQL("ALTER TABLE `barbers_new` RENAME TO `barbers`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `barber_specialties` (" +
                            "`barberId` INTEGER NOT NULL, " +
                            "`specialtyId` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`barberId`, `specialtyId`), " +
                            "FOREIGN KEY(`barberId`) REFERENCES `barbers`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`specialtyId`) REFERENCES `specialties`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_barber_specialties_specialtyId` " +
                            "ON `barber_specialties` (`specialtyId`)"
                )
                db.execSQL(
                    "INSERT INTO barber_specialties (barberId, specialtyId) " +
                            "SELECT t.barberId, s.id FROM tmp_barber_specialty t " +
                            "JOIN specialties s ON s.name = t.name"
                )
                db.execSQL("DROP TABLE tmp_barber_specialty")
            }
        }

        /** v2 → v3: agrega la tabla `appointments` (citas). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `appointments` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`clientName` TEXT NOT NULL, " +
                            "`clientPhone` TEXT NOT NULL, " +
                            "`barberId` INTEGER NOT NULL, " +
                            "`barberName` TEXT NOT NULL, " +
                            "`serviceId` INTEGER NOT NULL, " +
                            "`serviceName` TEXT NOT NULL, " +
                            "`servicePrice` REAL NOT NULL, " +
                            "`date` TEXT NOT NULL, " +
                            "`time` TEXT NOT NULL, " +
                            "`status` TEXT NOT NULL)"
                )
            }
        }

        /** v3 → v4: incorpora cuentas locales y roles, sin eliminar los datos existentes. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `users` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`fullName` TEXT NOT NULL, " +
                            "`email` TEXT NOT NULL, " +
                            "`passwordHash` TEXT NOT NULL, " +
                            "`passwordSalt` TEXT NOT NULL, " +
                            "`role` TEXT NOT NULL, " +
                            "`active` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)"
                )
            }
        }

        /** v4 → v5: horarios, bloqueos, historial, promociones, cupones y notificaciones. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Relaciona las nuevas citas con la cuenta cliente sin romper citas existentes.
                db.execSQL("ALTER TABLE `appointments` ADD COLUMN `clientUserId` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_appointments_clientUserId` " +
                            "ON `appointments` (`clientUserId`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `barber_schedules` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`barberId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, " +
                            "`startTime` TEXT NOT NULL, `endTime` TEXT NOT NULL, " +
                            "`slotDurationMinutes` INTEGER NOT NULL, `active` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`barberId`) REFERENCES `barbers`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_barber_schedules_barberId` ON `barber_schedules` (`barberId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_barber_schedules_barberId_dayOfWeek` ON `barber_schedules` (`barberId`, `dayOfWeek`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `barber_blocked_slots` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`barberId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                            "`startTime` TEXT NOT NULL, `endTime` TEXT NOT NULL, " +
                            "`reason` TEXT NOT NULL, `active` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`barberId`) REFERENCES `barbers`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_barber_blocked_slots_barberId` ON `barber_blocked_slots` (`barberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_barber_blocked_slots_barberId_date` ON `barber_blocked_slots` (`barberId`, `date`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `appointment_changes` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`appointmentId` INTEGER NOT NULL, `changeType` TEXT NOT NULL, " +
                            "`previousDate` TEXT NOT NULL, `previousTime` TEXT NOT NULL, " +
                            "`newDate` TEXT, `newTime` TEXT, `reason` TEXT, `changedAt` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`appointmentId`) REFERENCES `appointments`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_changes_appointmentId` ON `appointment_changes` (`appointmentId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `promotions` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                            "`conditions` TEXT NOT NULL, `startsAt` INTEGER NOT NULL, " +
                            "`endsAt` INTEGER NOT NULL, `active` INTEGER NOT NULL)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `coupons` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`promotionId` INTEGER, `code` TEXT NOT NULL, " +
                            "`discountType` TEXT NOT NULL, `discountValue` INTEGER NOT NULL, " +
                            "`conditions` TEXT NOT NULL, `startsAt` INTEGER NOT NULL, " +
                            "`endsAt` INTEGER NOT NULL, `maxUses` INTEGER, " +
                            "`currentUses` INTEGER NOT NULL, `active` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`promotionId`) REFERENCES `promotions`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_coupons_code` ON `coupons` (`code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coupons_promotionId` ON `coupons` (`promotionId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `coupon_redemptions` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`couponId` INTEGER NOT NULL, `userId` INTEGER NOT NULL, " +
                            "`appointmentId` INTEGER, `discountCents` INTEGER NOT NULL, " +
                            "`appliedAt` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`couponId`) REFERENCES `coupons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`appointmentId`) REFERENCES `appointments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coupon_redemptions_couponId` ON `coupon_redemptions` (`couponId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coupon_redemptions_userId` ON `coupon_redemptions` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coupon_redemptions_appointmentId` ON `coupon_redemptions` (`appointmentId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_notifications` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`userId` INTEGER NOT NULL, `appointmentId` INTEGER, " +
                            "`title` TEXT NOT NULL, `message` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`appointmentId`) REFERENCES `appointments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_notifications_userId` ON `app_notifications` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_notifications_appointmentId` ON `app_notifications` (`appointmentId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `device_registrations` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`userId` INTEGER NOT NULL, `fcmToken` TEXT NOT NULL, " +
                            "`active` INTEGER NOT NULL, `registeredAt` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_device_registrations_userId` ON `device_registrations` (`userId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_device_registrations_fcmToken` ON `device_registrations` (`fcmToken`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase {
            val db = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
            CoroutineScope(Dispatchers.IO).launch {
                if (db.serviceDao().count() == 0) {
                    db.serviceDao().insertAll(SeedData.services)
                }
                if (db.barberDao().count() == 0) {
                    db.barberDao().insertAll(SeedData.barbers)
                }
                if (db.specialtyDao().count() == 0) {
                    db.specialtyDao().insertAll(SeedData.specialties)
                }
                if (db.barberDao().specialtyLinkCount() == 0) {
                    seedSpecialtyLinks(db)
                }
            }
            return db
        }

        /** Enlaza los barberos de prueba con sus especialidades (instalaciones nuevas). */
        private suspend fun seedSpecialtyLinks(db: AppDatabase) {
            val barbersByName = db.barberDao().getAll().associateBy { it.name }
            val specialtiesByName = db.specialtyDao().getAll().associateBy { it.name }
            val links = SeedData.barberSpecialtyNames.flatMap { (barberName, specialtyNames) ->
                val barberId = barbersByName[barberName]?.id ?: return@flatMap emptyList()
                specialtyNames.mapNotNull { specialtyName ->
                    specialtiesByName[specialtyName]?.id?.let { BarberSpecialty(barberId, it) }
                }
            }
            if (links.isNotEmpty()) {
                db.barberDao().insertBarberSpecialties(links)
            }
        }
    }
}