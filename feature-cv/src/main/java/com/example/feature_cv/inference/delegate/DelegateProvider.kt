package com.example.feature_cv.inference.delegate

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.Closeable
import javax.inject.Inject

/**
 * Результат подбора делегата: готовые Interpreter.Options + (опционально) делегат,
 * который нужно явно закрыть при выгрузке/пересоздании Interpreter.
 *
 * ВАЖНО: GpuDelegate и NnApiDelegate реализуют Closeable и держат нативную память —
 * если не закрыть их явно при хот-свапе модели, будет утечка на каждую смену модели
 * в настройках. CPU-фолбэк (XNNPACK) ничего закрывать не требует — closeableDelegate
 * будет null в этом случае.
 */
data class DelegateSetup(
    val options: Interpreter.Options,
    val closeableDelegate: Closeable?
)

/**
 * Подбирает лучший доступный TFLite-делегат для устройства пользователя.
 * Порядок фолбэка: GPU delegate -> NNAPI -> CPU (XNNPACK).
 */
class DelegateProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createDelegateSetup(): DelegateSetup {
        val options = Interpreter.Options()

        createGpuDelegateOrNull()?.let { gpuDelegate ->
            options.addDelegate(gpuDelegate)
            Log.i(TAG, "Используется GPU delegate")
            return DelegateSetup(options, gpuDelegate)
        }

        createNnApiDelegateOrNull()?.let { nnApiDelegate ->
            options.addDelegate(nnApiDelegate)
            Log.i(TAG, "GPU недоступен, используется NNAPI delegate")
            return DelegateSetup(options, nnApiDelegate)
        }

        Log.i(TAG, "GPU и NNAPI недоступны, используется CPU (XNNPACK)")
        options.setUseXNNPACK(true)
        options.numThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
        return DelegateSetup(options, closeableDelegate = null)
    }

    private fun createGpuDelegateOrNull(): GpuDelegate? {
        return try {
            val compatList = CompatibilityList()
            if (!compatList.isDelegateSupportedOnThisDevice) {
                Log.i(TAG, "GPU delegate не поддерживается этим устройством")
                return null
            }
            GpuDelegate(compatList.bestOptionsForThisDevice)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось создать GPU delegate, фолбэк дальше", e)
            null
        }
    }

    private fun createNnApiDelegateOrNull(): NnApiDelegate? {
        return try {
            NnApiDelegate()
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось создать NNAPI delegate, фолбэк на CPU", e)
            null
        }
    }

    companion object {
        private const val TAG = "DelegateProvider"
    }
}