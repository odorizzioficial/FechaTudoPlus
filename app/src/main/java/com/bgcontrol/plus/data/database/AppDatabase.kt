package com.bgcontrol.plus.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bgcontrol.plus.data.dao.BlockedAppDao
import com.bgcontrol.plus.data.dao.RestrictedAppDao
import com.bgcontrol.plus.data.dao.ScheduleDao
import com.bgcontrol.plus.data.entities.BlockedAppEntity
import com.bgcontrol.plus.data.entities.RestrictedAppEntity
import com.bgcontrol.plus.data.entities.ScheduleAppEntity
import com.bgcontrol.plus.data.entities.ScheduleGroupEntity

/**
 * Banco local (Room sobre SQLite). Fica apenas no dispositivo:
 * sem servidor, sem conta, sem sincronização.
 */
@Database(
    entities = [
        RestrictedAppEntity::class,
        BlockedAppEntity::class,
        ScheduleGroupEntity::class,
        ScheduleAppEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun restrictedAppDao(): RestrictedAppDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Versão 2 traz os grupos agendados. A migração cria só as tabelas
         * novas — as listas de restritos e bloqueados do usuário permanecem.
         */
        /**
         * Versão 3 troca dia/mês por dias da semana. A tabela é recriada porque
         * o SQLite não remove colunas; os grupos existentes viram diários.
         */
        /** Versão 4: repetição opcional e data única para grupos que rodam uma vez. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `schedule_groups` ADD COLUMN " +
                        "`repeatEnabled` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL("ALTER TABLE `schedule_groups` ADD COLUMN `runAtDate` INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_groups_novo` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`mode` TEXT NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, " +
                        "`second` INTEGER NOT NULL, " +
                        "`daysOfWeek` INTEGER NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL, " +
                        "`lastRunAt` INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO `schedule_groups_novo` " +
                        "(`id`, `name`, `mode`, `hour`, `minute`, `second`, " +
                        "`daysOfWeek`, `isEnabled`, `lastRunAt`) " +
                        "SELECT `id`, `name`, `mode`, `hour`, `minute`, `second`, " +
                        "0, `isEnabled`, `lastRunAt` FROM `schedule_groups`"
                )
                db.execSQL("DROP TABLE `schedule_groups`")
                db.execSQL("ALTER TABLE `schedule_groups_novo` RENAME TO `schedule_groups`")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_groups` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`mode` TEXT NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, " +
                        "`second` INTEGER NOT NULL, " +
                        "`dayOfMonth` INTEGER, " +
                        "`month` INTEGER, " +
                        "`isEnabled` INTEGER NOT NULL, " +
                        "`lastRunAt` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_apps` (" +
                        "`groupId` INTEGER NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`appName` TEXT NOT NULL, " +
                        "PRIMARY KEY(`groupId`, `packageName`))"
                )
            }
        }

        /** Versão 5: repetição por intervalo em segundos. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `schedule_groups` ADD COLUMN `intervalSeconds` INTEGER"
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "bg_control_plus.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }
    }
}
