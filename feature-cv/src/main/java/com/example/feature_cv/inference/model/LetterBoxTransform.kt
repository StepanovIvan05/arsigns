package com.example.feature_cv.inference.model

import java.nio.ByteBuffer

/**
 * Параметры letterbox-трансформации конкретного кадра — нужны postprocess(),
 * чтобы пересчитать координаты из системы координат входа модели обратно
 * в систему координат исходного кадра с камеры.
 *
 * Ровно то же самое, что scale/(dw,dh) в Python-версии letterbox().
 */
data class LetterboxTransform(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val origWidth: Int,
    val origHeight: Int
)

/**
 * Результат preprocess() — готовый входной тензор + трансформация, которую
 * нужно передать в postprocess() того же кадра (они всегда идут парой,
 * поэтому не разносим их по разным вызовам без связи друг с другом).
 */
data class PreprocessResult(
    val inputBuffer: ByteBuffer,
    val transform: LetterboxTransform
)