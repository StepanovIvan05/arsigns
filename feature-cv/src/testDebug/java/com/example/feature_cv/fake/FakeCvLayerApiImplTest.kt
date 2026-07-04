package com.example.feature_cv.fake

import com.example.domain.model.DetectedSign
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Чистый JVM-тест, без Android SDK и без эмулятора — FakeCvLayerApiImpl не имеет
 * Android-зависимостей, поэтому гоняется как обычный JUnit-тест за секунды.
 */
class FakeCvLayerApiImplTest {

    @Test
    fun `до старта список пуст, после старта появляются детекции`() = runBlocking {
        val fake = FakeCvLayerApiImpl()

        assertTrue(fake.liveDetectedSigns.value.isEmpty())

        fake.startDetection()
        delay(100) // одного тика (33мс) достаточно благодаря гарантированному спавну

        val signs = fake.liveDetectedSigns.value
        assertTrue("после старта должны появиться детекции", signs.isNotEmpty())
        assertAllSignsValid(signs)

        fake.stopDetection()
    }

    @Test
    fun `список продолжает обновляться, пока идёт детекция`() = runBlocking {
        val fake = FakeCvLayerApiImpl()
        fake.startDetection()

        delay(100)
        val snapshot1 = fake.liveDetectedSigns.value

        delay(300)
        val snapshot2 = fake.liveDetectedSigns.value

        // Знаки двигаются каждый тик, поэтому координаты/confidence не могут совпадать 1-в-1
        assertNotEquals(snapshot1, snapshot2)

        fake.stopDetection()
    }

    @Test
    fun `после stopDetection список перестаёт меняться`() = runBlocking {
        val fake = FakeCvLayerApiImpl()
        fake.startDetection()
        delay(150)

        fake.stopDetection()
        val afterStop1 = fake.liveDetectedSigns.value

        delay(300) // ждём дольше обычного интервала тика — если бы цикл не остановился, список бы изменился

        val afterStop2 = fake.liveDetectedSigns.value
        assertEquals("после stopDetection список не должен меняться", afterStop1, afterStop2)
    }

    @Test
    fun `id знака стабилен между соседними кадрами (минимум 5 кадров жизни)`() = runBlocking {
        val fake = FakeCvLayerApiImpl(maxConcurrentSigns = 2, tickIntervalMs = 20)
        fake.startDetection()

        delay(40) // 1-2 тика
        val ids1 = fake.liveDetectedSigns.value.map { it.id }.toSet()

        delay(60) // ещё 2-3 тика — знак живёт минимум 5, значит id должен пересечься
        val ids2 = fake.liveDetectedSigns.value.map { it.id }.toSet()

        fake.stopDetection()

        assertTrue(
            "ожидали хотя бы один общий id между близкими кадрами",
            ids1.intersect(ids2).isNotEmpty()
        )
    }

    private fun assertAllSignsValid(signs: List<DetectedSign>) {
        signs.forEach { sign ->
            assertTrue("confidence в диапазоне [0,1]: ${sign.confidence}", sign.confidence in 0f..1f)
            assertTrue("xMin < xMax", sign.xMin < sign.xMax)
            assertTrue("yMin < yMax", sign.yMin < sign.yMax)
            assertTrue("x в пределах кадра [0,1]", sign.xMin >= 0f && sign.xMax <= 1f)
            assertTrue("y в пределах кадра [0,1]", sign.yMin >= 0f && sign.yMax <= 1f)
            assertTrue("classId не отрицательный", sign.classId >= 0)
        }
    }
}
