package com.example.feature_cv.inference

import android.graphics.Bitmap
import com.example.feature_cv.inference.model.LetterboxTransform
import com.example.feature_cv.inference.model.PreprocessResult
import com.example.feature_cv.inference.model.RawDetection

/**
 * Контракт "одна модель = одна стратегия препроцессинга/постпроцессинга".
 * Позволяет добавлять новые архитектуры моделей, не трогая InferenceEngine.
 *
 * @see YoloV8Strategy текущая (и пока единственная) реализация
 */
interface ModelStrategy {

    /** Размер стороны входного квадратного изображения (640/416/224) */
    val inputSize: Int

    /** Имя файла модели в assets */
    val fileName: String

    /**
     * Letterbox-ресайз кадра под inputSize x inputSize + конвертация в тензор нужного layout.
     * Возвращает буфер вместе с трансформацией, которую нужно передать в postprocess
     * ЭТОГО ЖЕ кадра.
     */
    fun preprocess(bitmap: Bitmap): PreprocessResult

    /**
     * Декодирует сырой выход модели [channels][numAnchors] в детекции,
     * уже пересчитанные в координаты исходного кадра (нормализованные [0,1]).
     * Включает NMS (раздельный по классам).
     *
     * @param rawOutput выход интерпретатора после squeeze батч-размерности: [4+numClasses][numAnchors]
     * @param transform letterbox-параметры ИМЕННО ТОГО кадра, для которого получен rawOutput
     * @param confidenceThreshold порог фильтрации (базовый "пол", не пользовательский — см. обсуждение архитектуры)
     */
    fun postprocess(
        rawOutput: Array<FloatArray>,
        transform: LetterboxTransform,
        confidenceThreshold: Float
    ): List<RawDetection>
}