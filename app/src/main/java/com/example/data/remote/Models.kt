package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val timestamp: Long? = null,
    val data: T? = null
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val uuid: String,
    val name: String,
    val email: String,
    val password: String,
    val institution: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class UserResponseContainer(
    val user: UserDto
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val uuid: String,
    val name: String,
    val email: String,
    val institution: String? = null,
    val token: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncClassDto(
    val uuid: String,
    @Json(name = "class_name") val className: String,
    val section: String? = "",
    val subject: String? = "",
    @Json(name = "is_deleted") val isDeleted: Int = 0,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "updated_at") val updatedAt: Long
)

@JsonClass(generateAdapter = true)
data class SyncStudentDto(
    val uuid: String,
    @Json(name = "class_uuid") val classUuid: String,
    @Json(name = "roll_number") val rollNumber: String,
    val name: String,
    val gender: String? = "Not Specified",
    val phone: String? = "",
    val email: String? = "",
    @Json(name = "monthly_fee") val monthlyFee: Double? = 500.0,
    @Json(name = "admission_date") val admissionDate: String? = "",
    @Json(name = "is_deleted") val isDeleted: Int = 0,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "updated_at") val updatedAt: Long
)

@JsonClass(generateAdapter = true)
data class SyncAttendanceDto(
    val uuid: String,
    @Json(name = "class_uuid") val classUuid: String,
    @Json(name = "student_uuid") val studentUuid: String,
    val date: String,
    val status: String,
    val remarks: String? = "",
    @Json(name = "is_deleted") val isDeleted: Int = 0,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "updated_at") val updatedAt: Long
)

@JsonClass(generateAdapter = true)
data class SyncPaymentDto(
    val uuid: String,
    @Json(name = "user_uuid") val userUuid: String? = null,
    @Json(name = "student_uuid") val studentUuid: String,
    @Json(name = "class_uuid") val classUuid: String,
    @Json(name = "amount_paid") val amountPaid: Double,
    @Json(name = "payment_date") val paymentDate: String,
    @Json(name = "receipt_no") val receiptNo: String? = "",
    @Json(name = "month_covered") val monthCovered: String? = "",
    val note: String? = "",
    @Json(name = "is_deleted") val isDeleted: Int = 0,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "updated_at") val updatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class SyncRequest(
    @Json(name = "user_uuid") val userUuid: String,
    @Json(name = "last_sync_timestamp") val lastSyncTimestamp: Long,
    val classes: List<SyncClassDto>,
    val students: List<SyncStudentDto>,
    val attendance: List<SyncAttendanceDto>,
    val payments: List<SyncPaymentDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class SyncResponseData(
    @Json(name = "server_timestamp") val serverTimestamp: Long,
    val classes: List<SyncClassDto>? = emptyList(),
    val students: List<SyncStudentDto>? = emptyList(),
    val attendance: List<SyncAttendanceDto>? = emptyList(),
    val payments: List<SyncPaymentDto>? = emptyList()
)
