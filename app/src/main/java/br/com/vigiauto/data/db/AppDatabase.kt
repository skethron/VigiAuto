package br.com.vigiauto.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VehicleRecordEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleRecordDao(): VehicleRecordDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "vigiauto.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()

        /**
         * Migração histórica da versão 1: preserva o histórico e adiciona
         * cidade/estado e as colunas da primeira implementação NMT.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN city TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN state TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN nmtStatus TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN nmtCheckedAt INTEGER")
            }
        }

        /**
         * Reinicia a fila NMT com a implementação de background, mas preserva
         * a última categoria concluída. Assim uma atualização do app não faz
         * "Veículo particular/aplicativo" desaparecer enquanto a primeira
         * consulta nova ainda está pendente. A fila é persistida em
         * `nmtNextAttemptAt`, que começa em 0 para todos os registros.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN nmtRawStatus TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN nmtAttempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN nmtNextAttemptAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE vehicle_records SET nmtRawStatus = NULL, " +
                        "nmtAttempts = 0, nmtNextAttemptAt = 0"
                )
            }
        }

        /**
         * Localização do aparelho no momento de cada consulta. As colunas são
         * opcionais para preservar todo o histórico anterior à atualização.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN locationStreet TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN locationNumber TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN locationNeighborhood TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN locationCity TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN locationState TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN locationPostalCode TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN locationCapturedAt INTEGER")
            }
        }

        /**
         * Captura real da placa: o recorte, as coordenadas da caixa do YOLO e as
         * confianças ficam ao lado da fotografia. Todas as colunas são opcionais
         * ou têm padrão para preservar os registros e as fotos anteriores.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN plateCropPath TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN plateOcrText TEXT")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN plateBoxLeft REAL")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN plateBoxTop REAL")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN plateBoxRight REAL")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN plateBoxBottom REAL")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN plateDetectorConfidence REAL")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN plateOcrConfidence REAL")
                db.execSQL("ALTER TABLE vehicle_records ADD COLUMN plateCropAspectRatio REAL")
                db.execSQL(
                    "ALTER TABLE vehicle_records ADD COLUMN plateQualityStatus TEXT " +
                        "NOT NULL DEFAULT 'UNAVAILABLE'"
                )
                db.execSQL(
                    "ALTER TABLE vehicle_records ADD COLUMN plateNeedsReview INTEGER " +
                        "NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
