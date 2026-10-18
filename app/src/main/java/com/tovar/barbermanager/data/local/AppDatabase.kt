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
    entities = [Service::class, Barber::class, Specialty::class, BarberSpecialty::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun serviceDao(): ServiceDao
    abstract fun barberDao(): BarberDao
    abstract fun specialtyDao(): SpecialtyDao

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

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase {
            val db = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
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
