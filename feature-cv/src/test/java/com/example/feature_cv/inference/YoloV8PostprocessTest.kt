package com.example.feature_cv.inference.strategies

import com.example.feature_cv.inference.model.LetterboxTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloV8PostprocessTest {

    private val numClasses = 3
    private val inputSize = 640
    private val strategy = YoloV8Strategy(inputSize = inputSize, fileName = "test.tflite", numClasses = numClasses)

    /** Кадр без letterbox-паддинга — квадратный, scale=1, паддингов нет, чтобы координаты считались просто */
    private val identityTransform = LetterboxTransform(scale = 1f, padX = 0f, padY = 0f, origWidth = 640, origHeight = 640)

    /** Строит синтетический rawOutput [4+numClasses][numAnchors] с одной детекцией на заданном anchor-индексе */
    private fun buildRawOutput(
        numAnchors: Int,
        detections: List<Triple<Int, Int, Float>> // (anchorIndex, classId, confidence) — координаты фиксированы ниже
    ): Array<FloatArray> {
        val output = Array(4 + numClasses) { FloatArray(numAnchors) }

        for ((anchorIndex, classId, confidence) in detections) {
            // cx, cy, w, h нормализованы в [0,1] относительно inputSize — фиксируем разумный квадрат в центре
            output[0][anchorIndex] = 0.5f // cx
            output[1][anchorIndex] = 0.5f // cy
            output[2][anchorIndex] = 0.2f // w
            output[3][anchorIndex] = 0.2f // h
            output[4 + classId][anchorIndex] = confidence
        }
        return output
    }

    @Test
    fun `детекция ниже порога confidence отфильтровывается`() {
        val rawOutput = buildRawOutput(numAnchors = 10, detections = listOf(Triple(0, 1, 0.1f)))

        val result = strategy.postprocess(rawOutput, identityTransform, confidenceThreshold = 0.25f)

        assertTrue("детекция с confidence 0.1 при пороге 0.25 не должна пройти", result.isEmpty())
    }

    @Test
    fun `детекция выше порога проходит и координаты корректно нормализованы`() {
        val rawOutput = buildRawOutput(numAnchors = 10, detections = listOf(Triple(0, 2, 0.8f)))

        val result = strategy.postprocess(rawOutput, identityTransform, confidenceThreshold = 0.25f)

        assertEquals(1, result.size)
        val detection = result.first()
        assertEquals(2, detection.classId)
        assertEquals(0.8f, detection.confidence, 0.001f)

        // cx=0.5,cy=0.5,w=0.2,h=0.2 в долях inputSize -> пиксели 320,320,128,128 при identity-трансформе
        // -> x1=(320-64)/640=0.4, x2=(320+64)/640=0.6 (аналогично по y)
        assertEquals(0.4f, detection.xMin, 0.01f)
        assertEquals(0.4f, detection.yMin, 0.01f)
        assertEquals(0.6f, detection.xMax, 0.01f)
        assertEquals(0.6f, detection.yMax, 0.01f)
    }

    @Test
    fun `две сильно пересекающиеся рамки ОДНОГО класса - NMS оставляет только одну с большей confidence`() {
        val rawOutput = buildRawOutput(
            numAnchors = 10,
            detections = listOf(
                Triple(0, 1, 0.6f),
                Triple(1, 1, 0.9f) // тот же класс, те же координаты (buildRawOutput всегда пишет одинаковый бокс)
            )
        )

        val result = strategy.postprocess(rawOutput, identityTransform, confidenceThreshold = 0.25f)

        assertEquals("NMS должен схлопнуть дубли одного класса в одну детекцию", 1, result.size)
        assertEquals(0.9f, result.first().confidence, 0.001f)
    }

    @Test
    fun `две пересекающиеся рамки РАЗНЫХ классов - NMS их не схлопывает`() {
        val rawOutput = buildRawOutput(
            numAnchors = 10,
            detections = listOf(
                Triple(0, 0, 0.7f),
                Triple(1, 1, 0.8f) // другой класс, координаты те же (полное перекрытие)
            )
        )

        val result = strategy.postprocess(rawOutput, identityTransform, confidenceThreshold = 0.25f)

        assertEquals("разные классы не должны гасить друг друга даже при полном перекрытии рамок", 2, result.size)
    }

    @Test
    fun `letterbox с паддингом корректно пересчитывает координаты в исходный кадр`() {
        // Кадр 1280x640 (широкий) -> letterbox в 640x640 даёт scale=0.5, padY=(640-320)/2=160, padX=0
        val transform = LetterboxTransform(scale = 0.5f, padX = 0f, padY = 160f, origWidth = 1280, origHeight = 640)
        val rawOutput = buildRawOutput(numAnchors = 5, detections = listOf(Triple(0, 0, 0.9f)))

        val result = strategy.postprocess(rawOutput, transform, confidenceThreshold = 0.25f)

        assertEquals(1, result.size)
        val d = result.first()
        // cx=0.5*640=320, w=0.2*640=128 -> x1_letterboxed=320-64=256 -> (256-0)/0.5=512 -> /origWidth(1280)=0.4
        assertEquals(0.4f, d.xMin, 0.01f)
        assertEquals(0.6f, d.xMax, 0.01f)
    }

    @Test
    fun `прямоугольная рамка (не квадратная) сохраняет правильное соотношение сторон`() {
        // Реалистичный случай: знак шире, чем выше (типичная прямоугольная табличка)
        val rawOutput = Array(4 + numClasses) { FloatArray(10) }
        rawOutput[0][0] = 0.5f  // cx
        rawOutput[1][0] = 0.5f  // cy
        rawOutput[2][0] = 0.4f  // w — вдвое больше h
        rawOutput[3][0] = 0.2f  // h
        rawOutput[4][0] = 0.9f  // confidence класса 0

        val result = strategy.postprocess(rawOutput, identityTransform, confidenceThreshold = 0.25f)

        assertEquals(1, result.size)
        val d = result.first()

        val width = d.xMax - d.xMin
        val height = d.yMax - d.yMin

        // Ожидаем: width = 0.4 (доля кадра), height = 0.2 — то есть width РОВНО в 2 раза больше height
        assertEquals(0.4f, width, 0.01f)
        assertEquals(0.2f, height, 0.01f)
        assertEquals("рамка не должна случайно стать квадратной — width/height должно быть ~2.0", 2.0f, width / height, 0.05f)

        // И рамка должна остаться центрированной в той же точке (0.5, 0.5), не съехать
        val centerX = (d.xMin + d.xMax) / 2f
        val centerY = (d.yMin + d.yMax) / 2f
        assertEquals(0.5f, centerX, 0.01f)
        assertEquals(0.5f, centerY, 0.01f)
    }

    @Test
    fun `постобработка корректна для ЛЮБОГО inputSize модели, не только 640`() {
        // Одна и та же логика должна одинаково работать для 640, 416 и 224 —
        // именно так у нас организован ModelStrategyFactory (один класс на все три модели)
        for (size in listOf(640, 416, 224)) {
            val strategyForSize = YoloV8Strategy(inputSize = size, fileName = "test_$size.tflite", numClasses = numClasses)
            // identity-трансформ под конкретный размер: квадратный кадр без паддинга
            val transform = LetterboxTransform(scale = 1f, padX = 0f, padY = 0f, origWidth = size, origHeight = size)

            // Иное число anchors на каждый размер (как в реальных моделях: 8400/3549/1029) —
            // конкретное число тут не важно, важно что не захардкожено
            val numAnchors = when (size) {
                640 -> 8400
                416 -> 3549
                else -> 1029
            }
            val rawOutput = buildRawOutput(numAnchors = numAnchors, detections = listOf(Triple(0, 1, 0.9f)))

            val result = strategyForSize.postprocess(rawOutput, transform, confidenceThreshold = 0.25f)

            assertEquals("size=$size: должна быть ровно одна детекция", 1, result.size)
            val d = result.first()
            assertEquals("size=$size: classId должен сохраниться", 1, d.classId)
            // cx=cy=0.5, w=h=0.2 в долях -> пиксели: 0.5*size, 0.2*size -> x1=(0.5size-0.1size)/size=0.4, x2=0.6
            // результат не должен зависеть от size — это и есть признак корректной нормализации
            assertEquals("size=$size: xMin должен быть 0.4 независимо от inputSize", 0.4f, d.xMin, 0.01f)
            assertEquals("size=$size: xMax должен быть 0.6 независимо от inputSize", 0.6f, d.xMax, 0.01f)
        }
    }
}