package com.example.domain.model

/**
 * Перечисление всех доступных моделей нейросетей в приложении.
 * Внутри DataStore (БД настроек) сохраняется как String (через .name),
 * но в коде используется как безопасный тип данных.
 */
enum class YoloModelType(
    val fileName: String,      // Имя файла в папке assets (например, "yolov8n.tflite")
    val uiName: String         // Человеческое название для вывода на экране настроек
) {
    YOLO_V8_640("yolov8n.tflite", "YOLOv8 640"),
    YOLO_V8_416("yolov8s.tflite", "YOLOv8 416"),
    YOLO_V8_224("yolov8s.tflite", "YOLOv8 224")
}

/**
 * Модель настроек приложения.
 * Именно этот класс отдается через интерфейсы во все остальные модули.
 */
data class AppSettings(
    val isVoiceAlertsEnabled: Boolean = true,     // Включение/выключение голосового прочтения
    val yoloConfidenceThreshold: Float = 0.5f,    // Минимальный порог уверенности (число от 0.0 до 1.0)
    val selectedModel: YoloModelType = YoloModelType.YOLO_V8_640 // Выбранная в данный момент модель
)