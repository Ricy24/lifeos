package com.example.andresfinanzas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        DebtEntity::class,
        GoalEntity::class,
        WishlistEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LifeOSDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao
    abstract fun goalDao(): GoalDao
    abstract fun wishlistDao(): WishlistDao
}
