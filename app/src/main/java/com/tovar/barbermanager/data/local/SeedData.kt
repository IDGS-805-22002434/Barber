package com.tovar.barbermanager.data.local

import com.tovar.barbermanager.data.model.ServiceCategory

/**
 * Datos de ejemplo con los que arranca la base local.
 * Se cargan solo si las tablas están vacías (primera ejecución).
 */
object SeedData {

    val services = listOf(
        Service(name = "Corte clásico", description = "Corte con tijera y máquina, acabado con peinado.", priceCents = 12_000, category = ServiceCategory.CORTES),
        Service(name = "Corte degradé (fade)", description = "Degradé profesional con acabado a navaja.", priceCents = 15_000, category = ServiceCategory.CORTES),
        Service(name = "Corte infantil", description = "Corte para menores de 12 años, con paciencia incluida.", priceCents = 9_000, category = ServiceCategory.CORTES),
        Service(name = "Barba completa", description = "Perfilado de barba con toalla caliente y bálsamo.", priceCents = 8_000, category = ServiceCategory.BARBA),
        Service(name = "Barba y bigote", description = "Recorte y perfilado de barba con bigote.", priceCents = 7_000, category = ServiceCategory.BARBA),
        Service(name = "Diseño de barba", description = "Figuras y líneas marcadas a navaja.", priceCents = 10_000, category = ServiceCategory.BARBA),
        Service(name = "Combo corte + barba", description = "Corte completo más perfilado de barba.", priceCents = 18_000, category = ServiceCategory.COMBOS),
        Service(name = "Combo completo", description = "Corte, barba y cejas en una sola sesión.", priceCents = 21_000, category = ServiceCategory.COMBOS),
        Service(name = "Cejas", description = "Perfilado de cejas con navaja.", priceCents = 4_000, category = ServiceCategory.CEJAS),
        Service(name = "Delineado de cejas", description = "Delineado fino de cejas a navaja.", priceCents = 5_000, category = ServiceCategory.CEJAS),
        Service(name = "Tinte para barba", description = "Cobertura de canas en barba, tono natural.", priceCents = 13_000, category = ServiceCategory.COLOR),
        Service(name = "Tinte completo", description = "Tinte de cabello con lavado incluido.", priceCents = 25_000, category = ServiceCategory.COLOR)
    )

    val barbers = listOf(
        Barber(name = "Carlos Mendoza", yearsExperience = 8),
        Barber(name = "Luis Ramírez", yearsExperience = 6),
        Barber(name = "Andrea Sosa", yearsExperience = 10),
        Barber(name = "Jorge Vidal", yearsExperience = 12)
    )

    val specialties = listOf(
        Specialty(name = "Cortes clásicos"),
        Specialty(name = "Degradés y fades"),
        Specialty(name = "Barba y navaja"),
        Specialty(name = "Colorimetría"),
        Specialty(name = "Peinados"),
        Specialty(name = "Cejas")
    )

    /** Especialidades asignadas a cada barbero de prueba (N:N, RF03.03). */
    val barberSpecialtyNames = mapOf(
        "Carlos Mendoza" to listOf("Cortes clásicos", "Degradés y fades"),
        "Luis Ramírez" to listOf("Barba y navaja"),
        "Andrea Sosa" to listOf("Colorimetría"),
        "Jorge Vidal" to listOf("Peinados", "Cortes clásicos")
    )
}
