package com.example.domain.model

data class SignMetadata(
    val yoloClassIndex: String,    // "YOLO_Class_Index" — связь с нейросетью
    val gostSignNumber: String,    // "GOST_Sign_Number" (например, "3.24")
    val title: String,             // "название"
    val voiceText: String,         // "название для прочтения"
    val description: String,       // "описание"
    val photoPath: String          // "путь к фото"
)