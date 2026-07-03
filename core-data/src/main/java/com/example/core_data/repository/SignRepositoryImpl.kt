package com.example.core_data.repository

import com.example.core_data.mapper.SignMapper.toDomain
import com.example.core_data.room.SignDao
import com.example.domain.model.SignEntity
import com.example.domain.repository.ISignRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignRepositoryImpl @Inject constructor(
    private val signDao: SignDao
) : ISignRepository {

    // thread-safe кэш, работающий на чтение без блокировок
    @Volatile
    private var cachedSigns: Map<Int, SignEntity> = emptyMap()

    override suspend fun preloadCache() = withContext(Dispatchers.IO) {
        cachedSigns = signDao.getAll()
            .map { it.toDomain() }
            .associateBy { it.id }
    }

    override suspend fun getSignById(id: Int): SignEntity? {
        // 1. Идеальный путь: берем из RAM за O(1)
        cachedSigns[id]?.let { return it }

        // 2. Фолбэк: если кэш пуст или знак новый, идем в БД один раз
        return withContext(Dispatchers.IO) {
            // Приводим id к String, если этого требует твой Dao
            signDao.getById(id.toString())?.toDomain()?.also { entity ->
                // На всякий случай сохраняем в кэш, чтобы не ходить в БД на следующем кадре
                cachedSigns = cachedSigns + (id to entity)
            }
        }
    }
}
