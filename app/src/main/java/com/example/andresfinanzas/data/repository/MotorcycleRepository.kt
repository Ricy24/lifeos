package com.example.andresfinanzas.data.repository

import com.example.andresfinanzas.data.local.dao.MotorcycleDao
import com.example.andresfinanzas.data.local.entities.MotorcycleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class ComponentHealth(
    val name: String,
    val icon: String,
    val currentUsageKm: Int,
    val maxLifeKm: Int,
    val remainingKm: Int,
    val progress: Float, // 0.0 to 1.0 (1.0 = expired / need service)
    val status: MaintenanceStatus
)

enum class MaintenanceStatus {
    OPTIMAL,
    ATTENTION,
    URGENT
}

data class DocumentStatus(
    val title: String,
    val expiryTimestamp: Long,
    val daysRemaining: Int,
    val isExpired: Boolean,
    val status: MaintenanceStatus
)

@Singleton
class MotorcycleRepository @Inject constructor(
    private val motorcycleDao: MotorcycleDao
) {
    val motorcycle: Flow<MotorcycleEntity?> = motorcycleDao.getMotorcycle()

    suspend fun getOrCreateDefault(): MotorcycleEntity {
        val existing = motorcycleDao.getMotorcycle().firstOrNull()
        if (existing != null) return existing

        val defaultMoto = MotorcycleEntity(
            name = "Mi Moto",
            model = "2024",
            currentMileage = 12500,
            oilChangeInterval = 2500,
            lastOilChangeMileage = 11000,
            frontTireMileage = 2000,
            frontTireLifeKm = 18000,
            rearTireMileage = 4500,
            rearTireLifeKm = 12000,
            brakePadsMileage = 3000,
            brakePadsLifeKm = 8000,
            chainMaintenanceMileage = 12200,
            chainMaintenanceInterval = 1000,
            costPerKm = 45.0
        )
        motorcycleDao.insertMotorcycle(defaultMoto)
        return defaultMoto
    }

    suspend fun updateMileage(motoId: String, newMileage: Int) {
        motorcycleDao.updateMileage(motoId, newMileage)
    }

    suspend fun recordOilChange(motoId: String, currentMileage: Int) {
        motorcycleDao.recordOilChange(motoId, currentMileage)
    }

    suspend fun recordFrontTireChange(motoId: String, currentMileage: Int) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        motorcycleDao.updateMotorcycle(moto.copy(frontTireMileage = currentMileage, lastUpdated = System.currentTimeMillis()))
    }

    suspend fun recordRearTireChange(motoId: String, currentMileage: Int) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        motorcycleDao.updateMotorcycle(moto.copy(rearTireMileage = currentMileage, lastUpdated = System.currentTimeMillis()))
    }

    suspend fun recordBrakePadsChange(motoId: String, currentMileage: Int) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        motorcycleDao.updateMotorcycle(moto.copy(brakePadsMileage = currentMileage, lastUpdated = System.currentTimeMillis()))
    }

    suspend fun recordChainMaintenance(motoId: String, currentMileage: Int) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        motorcycleDao.updateMotorcycle(moto.copy(chainMaintenanceMileage = currentMileage, lastUpdated = System.currentTimeMillis()))
    }

    suspend fun updateMotoDetails(
        name: String,
        model: String,
        oilInterval: Int,
        soatExpiry: Long,
        technoExpiry: Long
    ) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        motorcycleDao.updateMotorcycle(
            moto.copy(
                name = name,
                model = model,
                oilChangeInterval = oilInterval,
                soatExpiryDate = soatExpiry,
                technoExpiryDate = technoExpiry,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    fun computeComponentsHealth(moto: MotorcycleEntity): List<ComponentHealth> {
        // 1. Aceite de Motor
        val oilUsage = (moto.currentMileage - moto.lastOilChangeMileage).coerceAtLeast(0)
        val oilRemaining = (moto.oilChangeInterval - oilUsage).coerceAtLeast(0)
        val oilProgress = (oilUsage.toFloat() / moto.oilChangeInterval.toFloat()).coerceIn(0f, 1f)
        val oilStatus = when {
            oilProgress >= 0.95f -> MaintenanceStatus.URGENT
            oilProgress >= 0.75f -> MaintenanceStatus.ATTENTION
            else -> MaintenanceStatus.OPTIMAL
        }

        // 2. Llanta Trasera
        val rearUsage = (moto.currentMileage - moto.rearTireMileage).coerceAtLeast(0)
        val rearRemaining = (moto.rearTireLifeKm - rearUsage).coerceAtLeast(0)
        val rearProgress = (rearUsage.toFloat() / moto.rearTireLifeKm.toFloat()).coerceIn(0f, 1f)
        val rearStatus = when {
            rearProgress >= 0.9f -> MaintenanceStatus.URGENT
            rearProgress >= 0.75f -> MaintenanceStatus.ATTENTION
            else -> MaintenanceStatus.OPTIMAL
        }

        // 3. Llanta Delantera
        val frontUsage = (moto.currentMileage - moto.frontTireMileage).coerceAtLeast(0)
        val frontRemaining = (moto.frontTireLifeKm - frontUsage).coerceAtLeast(0)
        val frontProgress = (frontUsage.toFloat() / moto.frontTireLifeKm.toFloat()).coerceIn(0f, 1f)
        val frontStatus = when {
            frontProgress >= 0.9f -> MaintenanceStatus.URGENT
            frontProgress >= 0.75f -> MaintenanceStatus.ATTENTION
            else -> MaintenanceStatus.OPTIMAL
        }

        // 4. Pastillas de Freno
        val brakeUsage = (moto.currentMileage - moto.brakePadsMileage).coerceAtLeast(0)
        val brakeRemaining = (moto.brakePadsLifeKm - brakeUsage).coerceAtLeast(0)
        val brakeProgress = (brakeUsage.toFloat() / moto.brakePadsLifeKm.toFloat()).coerceIn(0f, 1f)
        val brakeStatus = when {
            brakeProgress >= 0.9f -> MaintenanceStatus.URGENT
            brakeProgress >= 0.75f -> MaintenanceStatus.ATTENTION
            else -> MaintenanceStatus.OPTIMAL
        }

        // 5. Cadena / Kit de Arrastre
        val chainUsage = (moto.currentMileage - moto.chainMaintenanceMileage).coerceAtLeast(0)
        val chainRemaining = (moto.chainMaintenanceInterval - chainUsage).coerceAtLeast(0)
        val chainProgress = (chainUsage.toFloat() / moto.chainMaintenanceInterval.toFloat()).coerceIn(0f, 1f)
        val chainStatus = when {
            chainProgress >= 0.9f -> MaintenanceStatus.URGENT
            chainProgress >= 0.75f -> MaintenanceStatus.ATTENTION
            else -> MaintenanceStatus.OPTIMAL
        }

        return listOf(
            ComponentHealth("Aceite de Motor", "🛢️", oilUsage, moto.oilChangeInterval, oilRemaining, oilProgress, oilStatus),
            ComponentHealth("Cadena (Lubricación)", "⛓️", chainUsage, moto.chainMaintenanceInterval, chainRemaining, chainProgress, chainStatus),
            ComponentHealth("Pastillas de Freno", "🛑", brakeUsage, moto.brakePadsLifeKm, brakeRemaining, brakeProgress, brakeStatus),
            ComponentHealth("Llanta Trasera", "🛞", rearUsage, moto.rearTireLifeKm, rearRemaining, rearProgress, rearStatus),
            ComponentHealth("Llanta Delantera", "🛞", frontUsage, moto.frontTireLifeKm, frontRemaining, frontProgress, frontStatus)
        )
    }

    fun computeDocumentStatus(soatTimestamp: Long, technoTimestamp: Long): Pair<DocumentStatus, DocumentStatus> {
        val now = System.currentTimeMillis()
        val oneDayMs = 24L * 60 * 60 * 1000

        val soatDays = ((soatTimestamp - now) / oneDayMs).toInt()
        val soatStatus = when {
            soatDays <= 0 -> MaintenanceStatus.URGENT
            soatDays <= 15 -> MaintenanceStatus.URGENT
            soatDays <= 30 -> MaintenanceStatus.ATTENTION
            else -> MaintenanceStatus.OPTIMAL
        }

        val technoDays = ((technoTimestamp - now) / oneDayMs).toInt()
        val technoStatus = when {
            technoDays <= 0 -> MaintenanceStatus.URGENT
            technoDays <= 15 -> MaintenanceStatus.URGENT
            technoDays <= 30 -> MaintenanceStatus.ATTENTION
            else -> MaintenanceStatus.OPTIMAL
        }

        return Pair(
            DocumentStatus("SOAT", soatTimestamp, soatDays, soatDays <= 0, soatStatus),
            DocumentStatus("Tecnomecánica", technoTimestamp, technoDays, technoDays <= 0, technoStatus)
        )
    }
}
