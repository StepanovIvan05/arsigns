package com.example.feature_cv.inference

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.feature_cv.inference.delegate.DelegateProvider
import com.example.feature_cv.inference.model.RawDetection
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над TFLite Interpreter. Единая точка запуска модели.
 *
 * ВАЖНО — ПОТОКОБЕЗОПАСНОСТЬ: Interpreter НЕ потокобезопасен. И load(), и run(),
 * и close() ОБЯЗАНЫ вызываться строго с одного и того же диспетчера
 * (см. di/DispatcherModule -> InferenceDispatcher). Параллельный вызов run() из
 * двух потоков может незаметно "перепутать" входные/выходные буферы одного кадра
 * с другим — без единой ошибки, просто с неверным результатом (см. обсуждение
 * потокобезопасности Interpreter).
 *
 * ВАЖНО — УТЕЧКИ ПАМЯТИ: GPU/NNAPI делегаты держат нативную память и должны быть
 * явно закрыты при пересоздании Interpreter (хот-свап модели) — это учтено в load()/close().
 */
@Singleton
class InferenceEngine @Inject constructor(
    private val delegateProvider: DelegateProvider,
    @ApplicationContext private val context: Context
) {

    private var interpreter: Interpreter? = null
    private var currentStrategy: ModelStrategy? = null
    private var currentDelegateSetup: com.example.feature_cv.inference.delegate.DelegateSetup? = null

    /** [batchSize, channels, numAnchors] выходного тензора текущей загруженной модели */
    private var outputShape: IntArray? = null

    /**
     * (Пере)загружает модель из assets по заданной стратегии.
     * Закрывает предыдущие Interpreter и делегат, если были — без этого при смене
     * модели в настройках нативная память будет утекать на каждое переключение.
     */
    fun load(strategy: ModelStrategy) {
        releaseCurrent()

        val modelBuffer = loadModelFile(strategy.fileName)
        val delegateSetup = delegateProvider.createDelegateSetup()

        val newInterpreter = Interpreter(modelBuffer, delegateSetup.options)
        newInterpreter.allocateTensors()

        interpreter = newInterpreter
        currentStrategy = strategy
        currentDelegateSetup = delegateSetup
        // Форма вида [1, 4+numClasses, numAnchors] — numAnchors у 640/416/224 РАЗНЫЙ (8400/3549/1029),
        // поэтому берём её из реальной модели, а не хардкодим под конкретный размер.
        outputShape = newInterpreter.getOutputTensor(0).shape()
    }

    /**
     * Прогоняет один кадр через модель. Вызывается строго с одного и того же
     * диспетчера (не параллельно) — см. предупреждение о потокобезопасности выше.
     *
     * @return список сырых детекций (после NMS, до трекера)
     */
    fun run(bitmap: Bitmap, confidenceThreshold: Float): List<RawDetection> {
        val strategy = currentStrategy
            ?: error("InferenceEngine.run() вызван до load() — модель не загружена")
        val interp = interpreter
            ?: error("InferenceEngine: interpreter отсутствует, хотя strategy установлена — неконсистентное состояние")
        val shape = outputShape
            ?: error("InferenceEngine: output shape неизвестен")

        val preprocessResult = strategy.preprocess(bitmap)

        val channels = shape[1]
        val numAnchors = shape[2]
        // TFLite Interpreter.run(Object, Object) ждёт выходной массив ровно той формы,
        // что и выходной тензор, включая batch-размерность — поэтому [1][channels][numAnchors],
        // а не сразу [channels][numAnchors].
        val outputWithBatch = Array(1) { Array(channels) { FloatArray(numAnchors) } }

        interp.run(preprocessResult.inputBuffer, outputWithBatch)

        val rawOutput = outputWithBatch[0] // убираем batch-размерность -> [channels][numAnchors]

        return strategy.postprocess(rawOutput, preprocessResult.transform, confidenceThreshold)
    }

    /** Полностью освобождает Interpreter и делегат. Вызывать при уничтожении компонента, владеющего движком. */
    fun close() {
        releaseCurrent()
    }

    private fun releaseCurrent() {
        interpreter?.close()
        interpreter = null
        currentStrategy = null
        outputShape = null

        // GpuDelegate/NnApiDelegate держат нативную память — обязаны быть закрыты явно,
        // Interpreter.close() делегаты сам не закрывает.
        currentDelegateSetup?.closeableDelegate?.close()
        currentDelegateSetup = null
    }

    /**
     * Открывает модель из assets через memory-mapped файл — эффективно по памяти
     * (страницы модели подгружаются ОС по требованию, а не копируются в heap целиком).
     * Требует, чтобы модель в APK была НЕ сжата (noCompress("tflite") в build.gradle.kts) —
     * иначе mmap на сжатый файл внутри APK не сработает.
     */
    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(fileName)
        return assetFileDescriptor.use { afd ->
            FileInputStream(afd.fileDescriptor).use { inputStream ->
                inputStream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength
                )
            }
        }
    }
}