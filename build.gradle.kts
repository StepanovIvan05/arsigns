plugins {
    // Базовые плагины для сборки Android и Kotlin
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false

    // Поддержка Jetpack Compose компилятора (для Kotlin 2.x+)
    alias(libs.plugins.kotlin.compose) apply false

    // Кодогенерация (KSP) — нужна для Room и Hilt
    alias(libs.plugins.google.ksp) apply false

    // Внедрение зависимостей (Dagger Hilt)
    alias(libs.plugins.hilt.android) apply false
}