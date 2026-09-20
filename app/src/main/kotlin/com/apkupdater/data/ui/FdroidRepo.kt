package com.apkupdater.data.ui

data class FdroidRepo(
    val name: String,
    val url: String
)

fun FdroidRepo.normalized() = copy(
    name = name.trim(),
    url = url.trim().let { if (it.endsWith('/')) it else "$it/" }
)
