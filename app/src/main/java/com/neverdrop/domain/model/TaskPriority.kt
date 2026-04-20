package com.neverdrop.domain.model

enum class TaskPriority(val level: Int) {
    CRITICAL(5),
    HIGH(4),
    MEDIUM(3),
    LOW(2),
    MINIMAL(1)
}
