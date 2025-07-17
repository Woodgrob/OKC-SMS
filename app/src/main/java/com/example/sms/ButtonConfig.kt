package com.example.myapplication

data class ButtonConfig(
    val id: String,
    val name: String,
    val message: String,
    val deviceId: String,  // ID одного устройства
    val objectId: String,   // ID одного объекта
    val objectNames: String = "",
    val deviceNames: String = "",
    var order: Int = 0 ,// Новое поле для порядка сортировки
    var requireConfirmation: Boolean = true // Новое поле - требуется подтверждение
)