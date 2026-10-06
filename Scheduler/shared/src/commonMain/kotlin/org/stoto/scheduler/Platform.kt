package org.stoto.scheduler

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform