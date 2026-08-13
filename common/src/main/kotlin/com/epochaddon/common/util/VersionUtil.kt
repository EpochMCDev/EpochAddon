package com.epochaddon.common.util

import org.bukkit.Server

object VersionUtil {

    fun serverVersion(server: Server): String = server.minecraftVersion

    fun isNewerThan(server: Server, target: String): Boolean =
        compareVersions(server.minecraftVersion, target) > 0

    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split(".").mapNotNull { it.toIntOrNull() }
        val bParts = b.split(".").mapNotNull { it.toIntOrNull() }
        val max = maxOf(aParts.size, bParts.size)
        for (i in 0 until max) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av - bv
        }
        return 0
    }
}
