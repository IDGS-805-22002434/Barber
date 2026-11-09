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
        UserAccount::class, Service::class, Barber::class, Specialty::class, BarberSpecialty::class,
        Appointment::class, AppointmentHistory::class,
        BarberSchedule::class, BlockedSlot::class,
        Promotion::class, Coupon::class, CouponRedemption::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun serviceDao(): ServiceDao
    abstract fun barberDao(): BarberDao
    abstract fun specialtyDao(): SpecialtyDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun barberScheduleDao(): BarberScheduleDao

    abstract fun promotionDao(): PromotionDao

    abstract fun couponDao(): CouponDao

    abstract fun couponRedemptionDao(): CouponRedemptionDao

    companion object {
        private const val DATABASE_NAME = "barbermanager.db"

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

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tmp_barber_specialty` (" +
                            "`barberId` INTEGER NOT NULL, `name` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO tmp_barber_specialty (barberId, name) " +
                            "SELECT id, TRIM(specialty) FROM barbers WHERE TRIM(specialty) <> ''"
                )

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

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createBookingTables(db)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createBookingTables(db)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `promotions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `conditions` TEXT NOT NULL, `startsAt` INTEGER NOT NULL, `endsAt` INTEGER NOT NULL, `active` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `coupons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `promotionId` INTEGER, `code` TEXT NOT NULL, `discountType` TEXT NOT NULL, `discountValue` INTEGER NOT NULL, `conditions` TEXT NOT NULL, `serviceCategory` TEXT, `minPriceCents` INTEGER NOT NULL, `startsAt` INTEGER NOT NULL, `endsAt` INTEGER NOT NULL, `maxUses` INTEGER, `currentUses` INTEGER NOT NULL, `active` INTEGER NOT NULL, FOREIGN KEY(`promotionId`) REFERENCES `promotions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_coupons_code` ON `coupons` (`code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coupons_promotionId` ON `coupons` (`promotionId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `coupon_redemptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `couponId` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `appointmentId` INTEGER, `discountCents` INTEGER NOT NULL, `appliedAt` INTEGER NOT NULL, FOREIGN KEY(`couponId`) REFERENCES `coupons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`appointmentId`) REFERENCES `appointments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coupon_redemptions_couponId` ON `coupon_redemptions` (`couponId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coupon_redemptions_userId` ON `coupon_redemptions` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coupon_redemptions_appointmentId` ON `coupon_redemptions` (`appointmentId`)")
                db.execSQL("ALTER TABLE `appointments` ADD COLUMN `couponCode` TEXT")
                db.execSQL("ALTER TABLE `appointments` ADD COLUMN `discountCents` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private fun createBookingTables(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `appointments`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `appointments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userEmail` TEXT NOT NULL, " +
                        "`clientName` TEXT NOT NULL, " +
                        "`clientPhone` TEXT NOT NULL, " +
                        "`barberId` INTEGER NOT NULL, " +
                        "`barberName` TEXT NOT NULL, " +
                        "`serviceId` INTEGER NOT NULL, " +
                        "`serviceName` TEXT NOT NULL, " +
                        "`priceCents` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`time` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_appointments_barberId_date_time` " +
                        "ON `appointments` (`barberId`, `date`, `time`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_appointments_userEmail` " +
                        "ON `appointments` (`userEmail`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `appointment_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`appointmentId` INTEGER NOT NULL, " +
                        "`action` TEXT NOT NULL, " +
                        "`oldDate` TEXT NOT NULL, " +
                        "`oldTime` TEXT NOT NULL, " +
                        "`newDate` TEXT NOT NULL, " +
                        "`newTime` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_appointment_history_appointmentId` " +
                        "ON `appointment_history` (`appointmentId`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `barber_schedules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`barberId` INTEGER NOT NULL, " +
                        "`dayOfWeek` INTEGER NOT NULL, " +
                        "`startMinutes` INTEGER NOT NULL, " +
                        "`endMinutes` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_barber_schedules_barberId_dayOfWeek` " +
                        "ON `barber_schedules` (`barberId`, `dayOfWeek`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `blocked_slots` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`barberId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`time` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_blocked_slots_barberId_date_time` " +
                        "ON `blocked_slots` (`barberId`, `date`, `time`)"
            )
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase {
            val db = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_4, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
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
                if (db.promotionDao().count() == 0) {
                    seedPromotions(db)
                }
            }
            return db
        }

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
        private suspend fun seedPromotions(db: AppDatabase) {
            val now = System.currentTimeMillis()
            val year = 365L * 24 * 60 * 60 * 1000
            val p1 = db.promotionDao().insert(Promotion(
                title = "Tu primer corte con 15% de descuento",
                description = "Estrena la barbería con 15% de descuento en cualquier corte.",
                conditions = "Solo servicios de la categoría Cortes. Un uso por cliente.",
                startsAt = now, endsAt = now + year))
            db.couponDao().insert(Coupon(
                promotionId = p1, code = "BIENVENIDO15",
                discountType = DiscountType.PERCENT, discountValue = 15,
                conditions = "Solo cortes", serviceCategory = "CORTES",
                startsAt = now, endsAt = now + year))
            val p2 = db.promotionDao().insert(Promotion(
                title = "Barba con \$20 de descuento",
                description = "Descuento fijo en servicios de barba.",
                conditions = "Servicios de barba de \$70 o más. Un uso por cliente.",
                startsAt = now, endsAt = now + year))
            db.couponDao().insert(Coupon(
                promotionId = p2, code = "BARBA20",
                discountType = DiscountType.AMOUNT_CENTS, discountValue = 2_000,
                conditions = "Solo barba", serviceCategory = "BARBA", minPriceCents = 7_000,
                startsAt = now, endsAt = now + year, maxUses = 50))
        }

    }
}
