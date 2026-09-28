package com.example.andresfinanzas.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.andresfinanzas.data.local.LifeOSDatabase
import com.example.andresfinanzas.data.local.dao.AccountDao
import com.example.andresfinanzas.data.local.dao.DebtDao
import com.example.andresfinanzas.data.local.dao.GoalDao
import com.example.andresfinanzas.data.local.dao.WishlistDao
import com.example.andresfinanzas.data.local.dao.TransactionDao
import com.example.andresfinanzas.data.local.dao.WorkSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""CREATE TABLE IF NOT EXISTS goals (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                targetAmount REAL NOT NULL,
                currentAmount REAL NOT NULL,
                category TEXT NOT NULL,
                description TEXT,
                priority INTEGER NOT NULL,
                targetDate INTEGER,
                status TEXT NOT NULL,
                syncStatus TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL
            )""")
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""CREATE TABLE IF NOT EXISTS wishlist_items (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                price REAL NOT NULL,
                url TEXT,
                store TEXT,
                imageUrl TEXT,
                category TEXT,
                priority INTEGER NOT NULL,
                savedAmount REAL NOT NULL,
                status TEXT NOT NULL,
                notes TEXT,
                syncStatus TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL
            )""")
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""CREATE TABLE IF NOT EXISTS work_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                userId TEXT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                durationMinutes INTEGER,
                income INTEGER NOT NULL,
                activityType TEXT NOT NULL,
                location TEXT,
                notes TEXT,
                version INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER
            )""")
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""CREATE TABLE IF NOT EXISTS motorcycles (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                model TEXT NOT NULL,
                currentMileage INTEGER NOT NULL,
                oilChangeInterval INTEGER NOT NULL,
                lastOilChangeMileage INTEGER NOT NULL,
                frontTireMileage INTEGER NOT NULL,
                frontTireLifeKm INTEGER NOT NULL,
                rearTireMileage INTEGER NOT NULL,
                rearTireLifeKm INTEGER NOT NULL,
                brakePadsMileage INTEGER NOT NULL,
                brakePadsLifeKm INTEGER NOT NULL,
                chainMaintenanceMileage INTEGER NOT NULL,
                chainMaintenanceInterval INTEGER NOT NULL,
                soatExpiryDate INTEGER NOT NULL,
                technoExpiryDate INTEGER NOT NULL,
                costPerKm REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )""")
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""CREATE TABLE IF NOT EXISTS visited_places (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                addressOrArea TEXT NOT NULL,
                rating INTEGER NOT NULL,
                averageCost REAL NOT NULL,
                notes TEXT,
                mapsUrl TEXT,
                visitedDate INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )""")
        }
    }

    @Provides
    @Singleton
    fun provideLifeOSDatabase(
        @ApplicationContext context: Context
    ): LifeOSDatabase {
        return Room.databaseBuilder(
            context,
            LifeOSDatabase::class.java,
            "lifeos_finance.db"
        ).addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6)
         .fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideAccountDao(database: LifeOSDatabase): AccountDao {
        return database.accountDao()
    }

    @Provides
    fun provideTransactionDao(database: LifeOSDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    fun provideDebtDao(database: LifeOSDatabase): DebtDao {
        return database.debtDao()
    }

    @Provides
    fun provideGoalDao(database: LifeOSDatabase): GoalDao {
        return database.goalDao()
    }

    @Provides
    fun provideWishlistDao(database: LifeOSDatabase): WishlistDao {
        return database.wishlistDao()
    }

    @Provides
    fun provideWorkSessionDao(database: LifeOSDatabase): WorkSessionDao {
        return database.workSessionDao()
    }

    @Provides
    fun provideMotorcycleDao(database: LifeOSDatabase): com.example.andresfinanzas.data.local.dao.MotorcycleDao {
        return database.motorcycleDao()
    }

    @Provides
    fun provideVisitedPlaceDao(database: LifeOSDatabase): com.example.andresfinanzas.data.local.dao.VisitedPlaceDao {
        return database.visitedPlaceDao()
    }
}
