package com.example.andresfinanzas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.andresfinanzas.data.local.dao.AccountDao
import com.example.andresfinanzas.data.local.dao.DebtDao
import com.example.andresfinanzas.data.local.dao.GoalDao
import com.example.andresfinanzas.data.local.dao.TransactionDao
import com.example.andresfinanzas.data.local.dao.WishlistDao
import com.example.andresfinanzas.data.local.entities.AccountEntity
import com.example.andresfinanzas.data.local.entities.DebtEntity
import com.example.andresfinanzas.data.local.entities.GoalEntity
import com.example.andresfinanzas.data.local.entities.TransactionEntity
import com.example.andresfinanzas.data.local.entities.WishlistEntity
import com.example.andresfinanzas.data.local.entities.WorkSessionEntity
import com.example.andresfinanzas.data.local.dao.WorkSessionDao
import com.example.andresfinanzas.data.local.dao.MotorcycleDao
import com.example.andresfinanzas.data.local.entities.MotorcycleEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        DebtEntity::class,
        GoalEntity::class,
        WishlistEntity::class,
        WorkSessionEntity::class,
        MotorcycleEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LifeOSDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao
    abstract fun goalDao(): GoalDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun workSessionDao(): WorkSessionDao
    abstract fun motorcycleDao(): MotorcycleDao
}
