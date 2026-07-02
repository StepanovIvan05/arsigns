package com.example.domain.api

import kotlinx.coroutines.flow.Flow
import com.example.domain.model.AppSettings
import com.example.domain.model.YoloModelType
import com.example.domain.model.SignMetadata

/**
 * Интерфейс взаимодействия со слоем данных (:core-data).
 * Предоставляет доступ к локальному хранилищу настроек (DataStore)
 * и базе данных знаков ПДД (Room/SQLite).
 */
interface DataLayerApi {

    // ========================================================================
    // РАБОТА С НАСТРОЙКАМИ (DataStore)
    // ========================================================================

    /**
     * Поток настроек приложения в реальном времени.
     * Твой модуль :feature-cv подпишется на него, чтобы динамически менять
     * порог уверенности (threshold) и переключать файлы моделей (.tflite).
     */
    val appSettings: Flow<AppSettings>

    /**
     * Сохранить новый порог уверенности для YOLO.
     */
    suspend fun updateConfidenceThreshold(threshold: Float)

    /**
     * Переключить выбранную модель нейросети.
     */
    suspend fun updateSelectedModel(modelType: YoloModelType)

    /**
     * Включить или выключить голосовые оповещения.
     */
    suspend fun updateVoiceAlertsEnabled(isEnabled: Boolean)


    // ========================================================================
    // РАБОТА С БАЗОЙ ДАННЫХ ЗНАКОВ (SQLite / Room)
    // ========================================================================

    /**
     * Получить метаданные конкретного знака по индексу класса, который вернула YOLO.
     * Именно этот метод вызовет :app модуль (или ViewModel), чтобы узнать,
     * что под индексом "12" скрывается знак "Уступи дорогу".
     *
     * @param yoloClassIndex Строковый индекс класса из модели (YOLO_Class_Index)
     * @return Метаданные знака или null, если такой индекс не найден в БД
     */
    suspend fun getSignMetadataByYoloIndex(yoloClassIndex: String): SignMetadata?

    /**
     * Получить вообще все знаки из базы данных (например, для экрана "Справочник ПДД").
     */
    suspend fun getAllSignsMetadata(): List<SignMetadata>
}