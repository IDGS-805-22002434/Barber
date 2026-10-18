package com.tovar.barbermanager.data.local

import androidx.room.TypeConverter
import com.tovar.barbermanager.data.model.ServiceCategory

class Converters {

    @TypeConverter
    fun fromCategory(category: ServiceCategory): String = category.name

    @TypeConverter
    fun toCategory(raw: String?): ServiceCategory = ServiceCategory.from(raw)
}
