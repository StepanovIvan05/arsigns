package com.example.core_data.mapper

import com.example.core_data.room.SignEntityDb
import com.example.domain.model.SignEntity

object SignMapper {
    fun SignEntityDb.toDomain(): SignEntity = SignEntity(
        id = originalCategoryId,
        pddCode = gostSignNumber,
        title = title,
        ttsTitle = ttsTitle,
        description = description,
        svgPath = photoPath.toAssetPath()
    )

    private fun String.toAssetPath(): String =
        trim()
            .replace('\\', '/')
            .removePrefix("/")
            .replace("sources/ signs/", "sources/signs/")
}
