package com.example.domain.model

data class DetectedSign(
    val id: Int,                     // ID от трекера (для v1.0 — 0)
    val yoloClassIndex: String,      // Идентификатор класса, который узнала YOLO
    val confidence: Float,           // Уверенность нейросети (0.0 - 1.0)

    // Координаты рамки
    val xMin: Float,
    val yMin: Float,
    val xMax: Float,
    val yMax: Float
)