package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val email: String,
    val institution: String,
    val token: String,
    val isLoggedIn: Boolean = true,
    val lastLoginTime: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "classes",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["userUuid"])]
)
data class ClassEntity(
    @PrimaryKey val uuid: String,
    val userUuid: String,
    val className: String,
    val section: String = "",
    val subject: String = "",
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["classUuid"]),
        Index(value = ["userUuid"])
    ]
)
data class StudentEntity(
    @PrimaryKey val uuid: String,
    val classUuid: String,
    val userUuid: String,
    val rollNumber: String,
    val name: String,
    val gender: String = "Not Specified",
    val phone: String = "",
    val email: String = "",
    val monthlyFee: Double = 500.0,
    val admissionDate: String = "",
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(
    tableName = "fee_payments",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["studentUuid"]),
        Index(value = ["classUuid"]),
        Index(value = ["userUuid"])
    ]
)
data class FeePaymentEntity(
    @PrimaryKey val uuid: String,
    val userUuid: String,
    val studentUuid: String,
    val classUuid: String,
    val amountPaid: Double,
    val paymentDate: String, // Format: YYYY-MM-DD
    val receiptNo: String = "",
    val monthCovered: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(
    tableName = "attendance",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["studentUuid", "date"], unique = true),
        Index(value = ["classUuid", "date"]),
        Index(value = ["userUuid"])
    ]
)
data class AttendanceEntity(
    @PrimaryKey val uuid: String,
    val userUuid: String,
    val classUuid: String,
    val studentUuid: String,
    val date: String, // Format: YYYY-MM-DD
    val status: String, // "PRESENT", "ABSENT", "LATE", "EXCUSED"
    val remarks: String = "",
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
