package com.tovar.barbermanager.data.model

import androidx.annotation.StringRes
import com.tovar.barbermanager.R

/** Catálogo fijo de categorías del catálogo de servicios. */
enum class ServiceCategory(@StringRes val titleRes: Int) {
    CORTES(R.string.cat_cuts),
    BARBA(R.string.cat_beard),
    COMBOS(R.string.cat_combos),
    CEJAS(R.string.cat_brows),
    COLOR(R.string.cat_color);

    companion object {
        fun from(raw: String?): ServiceCategory =
            entries.firstOrNull { it.name == raw } ?: CORTES
    }
}
