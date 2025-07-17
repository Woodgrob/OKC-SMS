package com.example.myapplication

data class DeviceConfig(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val simSlot: Int = 0
)