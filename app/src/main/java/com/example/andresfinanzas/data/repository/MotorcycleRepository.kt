package com.example.andresfinanzas.data.repository

import com.example.andresfinanzas.data.local.dao.MotorcycleDao
import com.example.andresfinanzas.data.local.entities.MotorcycleEntity
import com.example.andresfinanzas.data.remote.api.MotorcycleApi
import com.example.andresfinanzas.data.remote.api.MotorcycleUpdateRemote
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
    val status: MaintenanceStatus,
    val lastServiceMileage: Int,
    val intervalKm: Int
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
    private val motorcycleDao: MotorcycleDao,
    private val motorcycleApi: MotorcycleApi
) {
    val motorcycle: Flow<MotorcycleEntity?> = motorcycleDao.getMotorcycle()

    suspend fun getOrCreateDefault(): MotorcycleEntity {
        // 1. Try to fetch from backend first
        try {
            val remote = motorcycleApi.getMotorcycle()
            val entity = MotorcycleEntity(
                id = remote.id,
                name = remote.name,
                model = remote.model,
                currentMileage = remote.current_mileage,
                oilChangeInterval = remote.oil_change_interval,
                lastOilChangeMileage = remote.last_oil_change_mileage,
                frontTireMileage = remote.front_tire_mileage,
                frontTireLifeKm = remote.front_tire_life_km,
                rearTireMileage = remote.rear_tire_mileage,
                rearTireLifeKm = remote.rear_tire_life_km,
                brakePadsMileage = remote.brake_pads_mileage,
                brakePadsLifeKm = remote.brake_pads_life_km,
                chainMaintenanceMileage = remote.chain_maintenance_mileage,
                chainMaintenanceInterval = remote.chain_maintenance_interval,
                soatExpiryDate = remote.soat_expiry_date,
                technoExpiryDate = remote.techno_expiry_date,
                costPerKm = remote.cost_per_km,
                lastUpdated = remote.last_updated
            )
            motorcycleDao.insertMotorcycle(entity)
            return entity
        } catch (e: Exception) {
            // Offline fallback
        }

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
        pushToRemote(defaultMoto)
        return defaultMoto
    }

    suspend fun syncWithBackend() {
        try {
            val remote = motorcycleApi.getMotorcycle()
            val local = motorcycleDao.getMotorcycle().firstOrNull()
            if (local == null || remote.last_updated > local.lastUpdated) {
                motorcycleDao.insertMotorcycle(
                    MotorcycleEntity(
                        id = remote.id,
                        name = remote.name,
                        model = remote.model,
                        currentMileage = remote.current_mileage,
                        oilChangeInterval = remote.oil_change_interval,
                        lastOilChangeMileage = remote.last_oil_change_mileage,
                        frontTireMileage = remote.front_tire_mileage,
                        frontTireLifeKm = remote.front_tire_life_km,
                        rearTireMileage = remote.rear_tire_mileage,
                        rearTireLifeKm = remote.rear_tire_life_km,
                        brakePadsMileage = remote.brake_pads_mileage,
                        brakePadsLifeKm = remote.brake_pads_life_km,
                        chainMaintenanceMileage = remote.chain_maintenance_mileage,
                        chainMaintenanceInterval = remote.chain_maintenance_interval,
                        soatExpiryDate = remote.soat_expiry_date,
                        technoExpiryDate = remote.techno_expiry_date,
                        costPerKm = remote.cost_per_km,
                        lastUpdated = remote.last_updated
                    )
                )
            } else if (local.lastUpdated > remote.last_updated) {
                pushToRemote(local)
            }
        } catch (e: Exception) {
            // Keep local offline
        }
    }

    private suspend fun pushToRemote(moto: MotorcycleEntity) {
        try {
            motorcycleApi.updateMotorcycle(
                MotorcycleUpdateRemote(
                    name = moto.name,
                    model = moto.model,
                    current_mileage = moto.currentMileage,
                    oil_change_interval = moto.oilChangeInterval,
                    last_oil_change_mileage = moto.lastOilChangeMileage,
                    front_tire_mileage = moto.frontTireMileage,
                    front_tire_life_km = moto.frontTireLifeKm,
                    rear_tire_mileage = moto.rearTireMileage,
                    rear_tire_life_km = moto.rearTireLifeKm,
                    brake_pads_mileage = moto.brakePadsMileage,
                    brake_pads_life_km = moto.brakePadsLifeKm,
                    chain_maintenance_mileage = moto.chainMaintenanceMileage,
                    chain_maintenance_interval = moto.chainMaintenanceInterval,
                    soat_expiry_date = moto.soatExpiryDate,
                    techno_expiry_date = moto.technoExpiryDate,
                    cost_per_km = moto.costPerKm
                )
            )
        } catch (e: Exception) {
            // Will retry on next sync
        }
    }

    suspend fun updateMileage(motoId: String, newMileage: Int) {
        motorcycleDao.updateMileage(motoId, newMileage)
        val updated = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        pushToRemote(updated)
    }

    suspend fun updateEntireMotorcycle(moto: MotorcycleEntity) {
        val withTimestamp = moto.copy(lastUpdated = System.currentTimeMillis())
        motorcycleDao.updateMotorcycle(withTimestamp)
        pushToRemote(withTimestamp)
    }

    suspend fun recordOilChange(motoId: String, currentMileage: Int) {
        motorcycleDao.recordOilChange(motoId, currentMileage)
        val updated = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        pushToRemote(updated)
    }

    suspend fun editOilSettings(lastOilMileage: Int, intervalKm: Int) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        val updated = moto.copy(
            lastOilChangeMileage = lastOilMileage,
            oilChangeInterval = intervalKm,
            lastUpdated = System.currentTimeMillis()
        )
        motorcycleDao.updateMotorcycle(updated)
        pushToRemote(updated)
    }

    suspend fun editTireSettings(isRear: Boolean, installedMileage: Int, lifeKm: Int) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        val updated = if (isRear) {
            moto.copy(rearTireMileage = installedMileage, rearTireLifeKm = lifeKm, lastUpdated = System.currentTimeMillis())
        } else {
            moto.copy(frontTireMileage = installedMileage, frontTireLifeKm = lifeKm, lastUpdated = System.currentTimeMillis())
        }
        motorcycleDao.updateMotorcycle(updated)
        pushToRemote(updated)
    }

    suspend fun editBrakeSettings(installedMileage: Int, lifeKm: Int) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        val updated = moto.copy(
            brakePadsMileage = installedMileage,
            brakePadsLifeKm = lifeKm,
            lastUpdated = System.currentTimeMillis()
        )
        motorcycleDao.updateMotorcycle(updated)
        pushToRemote(updated)
    }

    suspend fun editChainSettings(lubricatedMileage: Int, intervalKm: Int) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        val updated = moto.copy(
            chainMaintenanceMileage = lubricatedMileage,
            chainMaintenanceInterval = intervalKm,
            lastUpdated = System.currentTimeMillis()
        )
        motorcycleDao.updateMotorcycle(updated)
        pushToRemote(updated)
    }

    suspend fun editDocumentExpiry(isSoat: Boolean, expiryTimestamp: Long) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        val updated = if (isSoat) {
            moto.copy(soatExpiryDate = expiryTimestamp, lastUpdated = System.currentTimeMillis())
        } else {
            moto.copy(technoExpiryDate = expiryTimestamp, lastUpdated = System.currentTimeMillis())
        }
        motorcycleDao.updateMotorcycle(updated)
        pushToRemote(updated)
    }

    suspend fun editCostPerKm(cost: Double) {
        val moto = motorcycleDao.getMotorcycle().firstOrNull() ?: return
        val updated = moto.copy(costPerKm = cost, lastUpdated = System.currentTimeMillis())
        motorcycleDao.updateMotorcycle(updated)
        pushToRemote(updated)
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
            ComponentHealth("Aceite de Motor", "🛢️", oilUsage, moto.oilChangeInterval, oilRemaining, oilProgress, oilStatus, moto.lastOilChangeMileage, moto.oilChangeInterval),
            ComponentHealth("Cadena (Lubricación)", "⛓️", chainUsage, moto.chainMaintenanceInterval, chainRemaining, chainProgress, chainStatus, moto.chainMaintenanceMileage, moto.chainMaintenanceInterval),
            ComponentHealth("Pastillas de Freno", "🛑", brakeUsage, moto.brakePadsLifeKm, brakeRemaining, brakeProgress, brakeStatus, moto.brakePadsMileage, moto.brakePadsLifeKm),
            ComponentHealth("Llanta Trasera", "🛞", rearUsage, moto.rearTireLifeKm, rearRemaining, rearProgress, rearStatus, moto.rearTireMileage, moto.rearTireLifeKm),
            ComponentHealth("Llanta Delantera", "🛞", frontUsage, moto.frontTireLifeKm, frontRemaining, frontProgress, frontStatus, moto.frontTireMileage, moto.frontTireLifeKm)
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
