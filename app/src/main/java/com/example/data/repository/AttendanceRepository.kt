package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.ClassEntity
import com.example.data.local.entity.FeePaymentEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.ApiClient
import com.example.data.remote.LoginRequest
import com.example.data.remote.RegisterRequest
import com.example.data.remote.SyncAttendanceDto
import com.example.data.remote.SyncClassDto
import com.example.data.remote.SyncPaymentDto
import com.example.data.remote.SyncRequest
import com.example.data.remote.SyncStudentDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val lastSyncTime: Long, val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class StudentReportItem(
    val studentUuid: String,
    val rollNumber: String,
    val name: String,
    val totalDays: Int,
    val present: Int,
    val absent: Int,
    val late: Int,
    val excused: Int,
    val percentage: Float
)

data class ClassReportData(
    val classEntity: ClassEntity,
    val fromDate: String,
    val toDate: String,
    val totalStudents: Int,
    val overallPercentage: Float,
    val items: List<StudentReportItem>
)

data class SingleStudentReportData(
    val student: StudentEntity,
    val classEntity: ClassEntity?,
    val fromDate: String,
    val toDate: String,
    val totalDays: Int,
    val present: Int,
    val absent: Int,
    val late: Int,
    val excused: Int,
    val percentage: Float,
    val records: List<AttendanceEntity>
)

data class StudentFeeSummary(
    val student: StudentEntity,
    val monthlyFee: Double,
    val admissionDate: String,
    val elapsedDays: Long, // মোট অতিবাহিত দিন (যেমন ৬৬ দিন)
    val elapsedMonths: Int, // বিলকৃত পূর্ণ মাস (যেমন ২ মাস)
    val totalPayable: Double,
    val totalPaid: Double,
    val dueAmount: Double,
    val dueMonths: Int,
    val isFullyPaid: Boolean,
    val totalDaysEnrolled: Long = elapsedDays,
    val billedMonths: Int = elapsedMonths,
    val runningDays: Int = (elapsedDays % 30).toInt()
)

class AttendanceRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val userDao = db.userDao()
    private val classDao = db.classDao()
    private val studentDao = db.studentDao()
    private val attendanceDao = db.attendanceDao()
    private val feePaymentDao = db.feePaymentDao()
    private val apiClient = ApiClient.getInstance(context)
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(prefs.getLong("last_sync_timestamp", 0L))
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    val activeUser: Flow<UserEntity?> = userDao.getActiveUserFlow()

    init {
        // Clean up any demo or test dummy accounts so only genuine user data exists
        repositoryScope.launch {
            try {
                userDao.deleteByEmail("teacher@school.edu")
            } catch (e: Exception) {
                Log.e("AttendanceRepo", "Error cleaning demo user: ${e.message}")
            }
        }
    }

    // --- Authentication ---

    suspend fun login(email: String, pass: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        var serverError: String? = null

        if (isNetworkAvailable()) {
            try {
                val response = apiClient.getApiService().login(LoginRequest(trimmedEmail, pass))
                if (response.isSuccessful && response.body()?.success == true) {
                    val uDto = response.body()?.data?.user
                        ?: throw Exception("সার্ভার থেকে ইউজার ডাটা পাওয়া যায়নি।")
                    val user = UserEntity(
                        uuid = uDto.uuid,
                        name = uDto.name,
                        email = uDto.email,
                        institution = uDto.institution ?: "",
                        token = uDto.token ?: "",
                        isLoggedIn = true,
                        lastLoginTime = System.currentTimeMillis()
                    )
                    userDao.insertOrUpdate(user)
                    // Trigger sync after login
                    triggerSync(user.uuid)
                    return@withContext Result.success(user)
                } else {
                    val errMsg = response.body()?.message ?: "লগইন ব্যর্থ হয়েছে। সঠিক ইমেইল ও পাসওয়ার্ড প্রদান করুন।"
                    return@withContext Result.failure(Exception(errMsg))
                }
            } catch (e: Exception) {
                Log.e("AttendanceRepo", "Online login error: ${e.message}")
                serverError = e.localizedMessage
            }
        }

        // Offline Fallback: Check local DB if already logged in before
        val localUser = userDao.getUserByEmail(trimmedEmail)
        if (localUser != null) {
            val updated = localUser.copy(isLoggedIn = true, lastLoginTime = System.currentTimeMillis())
            userDao.insertOrUpdate(updated)
            return@withContext Result.success(updated)
        }

        Result.failure(Exception(serverError ?: "সার্ভারের সাথে সংযোগ করা যায়নি। ইন্টারনেট সংযোগ ও সার্ভার URL পরীক্ষা করুন।"))
    }

    suspend fun register(name: String, email: String, pass: String, inst: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        val userUuid = UUID.randomUUID().toString()

        if (!isNetworkAvailable()) {
            return@withContext Result.failure(Exception("রেজিস্ট্রেশন করার জন্য ইন্টারনেট সংযোগ আবশ্যক।"))
        }

        try {
            val response = apiClient.getApiService().register(
                RegisterRequest(userUuid, name.trim(), trimmedEmail, pass, inst.trim())
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val uDto = response.body()?.data?.user
                    ?: throw Exception("সার্ভার থেকে ইউজার ডাটা পাওয়া যায়নি।")
                val user = UserEntity(
                    uuid = uDto.uuid,
                    name = uDto.name,
                    email = uDto.email,
                    institution = uDto.institution ?: inst.trim(),
                    token = uDto.token ?: "",
                    isLoggedIn = true,
                    lastLoginTime = System.currentTimeMillis()
                )
                userDao.insertOrUpdate(user)
                return@withContext Result.success(user)
            } else {
                val errMsg = response.body()?.message ?: "রেজিস্ট্রেশন ব্যর্থ হয়েছে।"
                return@withContext Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Log.e("AttendanceRepo", "Online registration error: ${e.message}")
            return@withContext Result.failure(Exception("সার্ভারে রেজিস্ট্রেশন ব্যর্থ: ${e.localizedMessage ?: "সংযোগ পাওয়া যায়নি"}"))
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        userDao.logoutAll()
    }

    // --- Classes ---

    fun getClasses(userUuid: String): Flow<List<ClassEntity>> = classDao.getClassesForUser(userUuid)

    suspend fun addClass(userUuid: String, className: String, section: String, subject: String) = withContext(Dispatchers.IO) {
        val classEntity = ClassEntity(
            uuid = UUID.randomUUID().toString(),
            userUuid = userUuid,
            className = className.trim(),
            section = section.trim(),
            subject = subject.trim(),
            isDeleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        classDao.insertOrUpdate(classEntity)
        triggerSync(userUuid)
    }

    suspend fun deleteClass(classUuid: String, userUuid: String) = withContext(Dispatchers.IO) {
        classDao.softDelete(classUuid)
        triggerSync(userUuid)
    }

    // --- Students ---

    fun getStudentsByClass(classUuid: String): Flow<List<StudentEntity>> = studentDao.getStudentsByClass(classUuid)

    fun getAllStudents(userUuid: String): Flow<List<StudentEntity>> = studentDao.getAllStudentsForUser(userUuid)

    suspend fun addStudent(
        classUuid: String,
        userUuid: String,
        roll: String,
        name: String,
        gender: String,
        phone: String,
        email: String,
        monthlyFee: Double = 500.0,
        admissionDate: String = ""
    ) = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val student = StudentEntity(
            uuid = UUID.randomUUID().toString(),
            classUuid = classUuid,
            userUuid = userUuid,
            rollNumber = roll.trim(),
            name = name.trim(),
            gender = gender.trim(),
            phone = phone.trim(),
            email = email.trim(),
            monthlyFee = monthlyFee,
            admissionDate = admissionDate.ifBlank { today },
            isDeleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        studentDao.insertOrUpdate(student)
        triggerSync(userUuid)
    }

    suspend fun updateStudentMonthlyFee(studentUuid: String, monthlyFee: Double, userUuid: String) = withContext(Dispatchers.IO) {
        studentDao.updateMonthlyFee(studentUuid, monthlyFee)
        triggerSync(userUuid)
    }

    suspend fun deleteStudent(studentUuid: String, userUuid: String) = withContext(Dispatchers.IO) {
        studentDao.softDelete(studentUuid)
        triggerSync(userUuid)
    }

    // --- Fee Collection & Payments ---

    fun getPaymentsForStudent(studentUuid: String): Flow<List<FeePaymentEntity>> =
        feePaymentDao.getPaymentsForStudent(studentUuid)

    fun getAllPaymentsForUser(userUuid: String): Flow<List<FeePaymentEntity>> =
        feePaymentDao.getAllPaymentsForUser(userUuid)

    fun getPaymentsForClass(classUuid: String): Flow<List<FeePaymentEntity>> =
        feePaymentDao.getPaymentsForClass(classUuid)

    fun getTotalPaidForStudent(studentUuid: String): Flow<Double> =
        feePaymentDao.getTotalPaidForStudent(studentUuid)

    suspend fun addFeePayment(
        userUuid: String,
        studentUuid: String,
        classUuid: String,
        amount: Double,
        paymentDate: String,
        receiptNo: String = "",
        monthCovered: String = "",
        note: String = ""
    ): FeePaymentEntity = withContext(Dispatchers.IO) {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val randPart = (1000..9999).random()
        val generatedReceipt = receiptNo.ifBlank { "REC-$datePart-$randPart" }

        val payment = FeePaymentEntity(
            uuid = UUID.randomUUID().toString(),
            userUuid = userUuid,
            studentUuid = studentUuid,
            classUuid = classUuid,
            amountPaid = amount,
            paymentDate = paymentDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) },
            receiptNo = generatedReceipt,
            monthCovered = monthCovered,
            note = note.trim(),
            createdAt = System.currentTimeMillis(),
            isSynced = false
        )
        feePaymentDao.insertOrUpdate(payment)
        triggerSync(userUuid)
        payment
    }

    suspend fun calculateStudentFeeSummary(student: StudentEntity): StudentFeeSummary = withContext(Dispatchers.IO) {
        val totalPaid = feePaymentDao.getTotalPaidAmountForStudent(student.uuid)
        val monthlyFee = student.monthlyFee
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val startDate = try {
            if (student.admissionDate.isNotBlank()) sdf.parse(student.admissionDate) ?: Date(student.createdAt)
            else Date(student.createdAt)
        } catch (e: Exception) {
            Date(student.createdAt)
        }

        val now = Date()
        val diffMillis = maxOf(0L, now.time - startDate.time)
        val totalDays = (diffMillis / (1000L * 60 * 60 * 24))

        // একজন ছাত্র ভর্তির ডেট থেকে ৬৬ দিন পড়লে বিলে ২ মাস এবং রানিং ৬ দিন কাউন্ট হবে
        val billedMonths = (totalDays / 30).toInt()
        val runningDays = (totalDays % 30).toInt()

        val totalPayable = billedMonths * monthlyFee
        val dueAmount = maxOf(0.0, totalPayable - totalPaid)
        val dueMonths = if (monthlyFee > 0.0) {
            Math.ceil(dueAmount / monthlyFee).toInt()
        } else 0
        val isFullyPaid = dueAmount <= 0.0

        StudentFeeSummary(
            student = student,
            monthlyFee = monthlyFee,
            admissionDate = sdf.format(startDate),
            elapsedDays = totalDays,
            elapsedMonths = billedMonths,
            totalPayable = totalPayable,
            totalPaid = totalPaid,
            dueAmount = dueAmount,
            dueMonths = dueMonths,
            isFullyPaid = isFullyPaid,
            totalDaysEnrolled = totalDays,
            billedMonths = billedMonths,
            runningDays = runningDays
        )
    }

    // --- Attendance ---

    fun getAttendanceForClassAndDate(classUuid: String, date: String): Flow<List<AttendanceEntity>> =
        attendanceDao.getAttendanceForClassAndDate(classUuid, date)

    suspend fun saveAttendance(
        userUuid: String,
        classUuid: String,
        date: String,
        studentStatusMap: Map<String, Pair<String, String>> // studentUuid -> Pair(status, remarks)
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = attendanceDao.getAttendanceListForClassAndDate(classUuid, date).associateBy { it.studentUuid }
        val toSave = mutableListOf<AttendanceEntity>()

        for ((studentUuid, pair) in studentStatusMap) {
            val (status, remarks) = pair
            val existingItem = existing[studentUuid]
            val record = if (existingItem != null) {
                existingItem.copy(
                    status = status,
                    remarks = remarks,
                    isDeleted = false,
                    updatedAt = now,
                    isSynced = false
                )
            } else {
                AttendanceEntity(
                    uuid = UUID.randomUUID().toString(),
                    userUuid = userUuid,
                    classUuid = classUuid,
                    studentUuid = studentUuid,
                    date = date,
                    status = status,
                    remarks = remarks,
                    isDeleted = false,
                    createdAt = now,
                    updatedAt = now,
                    isSynced = false
                )
            }
            toSave.add(record)
        }

        if (toSave.isNotEmpty()) {
            attendanceDao.insertOrUpdateAll(toSave)
        }

        triggerSync(userUuid)
    }

    // --- Reports Generation (Local & Offline Capable) ---

    suspend fun getClassReport(
        classUuid: String,
        fromDate: String,
        toDate: String
    ): ClassReportData? = withContext(Dispatchers.IO) {
        val cls = classDao.getClassByUuid(classUuid) ?: return@withContext null
        val students = studentDao.getStudentsByClassList(classUuid)
        val attendanceList = attendanceDao.getAttendanceForClassBetweenDates(classUuid, fromDate, toDate)

        val groupedByStudent = attendanceList.groupBy { it.studentUuid }
        val items = mutableListOf<StudentReportItem>()

        var totalClassPossible = 0
        var totalClassPresent = 0

        for (student in students) {
            val studentAtt = groupedByStudent[student.uuid] ?: emptyList()
            val present = studentAtt.count { it.status == "PRESENT" }
            val absent = studentAtt.count { it.status == "ABSENT" }
            val late = studentAtt.count { it.status == "LATE" }
            val excused = studentAtt.count { it.status == "EXCUSED" }

            // ছুটি (EXCUSED) এসাইন করলে সেটা কাউন্ট হবে না, শুধু হাজিরা, এবসেন্ট, লেট এটেন্ড এগুলা কাউন্ট করবে
            val countableDays = present + absent + late
            val totalDays = countableDays

            val percentage = if (totalDays > 0) {
                ((present.toFloat() + (late.toFloat() * 0.5f)) / totalDays.toFloat()) * 100f
            } else 0f

            totalClassPossible += totalDays
            totalClassPresent += present

            items.add(
                StudentReportItem(
                    studentUuid = student.uuid,
                    rollNumber = student.rollNumber,
                    name = student.name,
                    totalDays = totalDays,
                    present = present,
                    absent = absent,
                    late = late,
                    excused = excused,
                    percentage = percentage
                )
            )
        }

        val overallPercentage = if (totalClassPossible > 0) {
            (totalClassPresent.toFloat() / totalClassPossible.toFloat()) * 100f
        } else 0f

        ClassReportData(
            classEntity = cls,
            fromDate = fromDate,
            toDate = toDate,
            totalStudents = students.size,
            overallPercentage = overallPercentage,
            items = items
        )
    }

    suspend fun getStudentReport(
        studentUuid: String,
        fromDate: String,
        toDate: String
    ): SingleStudentReportData? = withContext(Dispatchers.IO) {
        val student = studentDao.getStudentByUuid(studentUuid) ?: return@withContext null
        val cls = classDao.getClassByUuid(student.classUuid)
        val records = attendanceDao.getAttendanceForStudentBetweenDates(studentUuid, fromDate, toDate)

        val present = records.count { it.status == "PRESENT" }
        val absent = records.count { it.status == "ABSENT" }
        val late = records.count { it.status == "LATE" }
        val excused = records.count { it.status == "EXCUSED" }

        // ছুটি (EXCUSED) এসাইন করলে সেটা কাউন্ট হবে না, শুধু হাজিরা, এবসেন্ট, লেট এটেন্ড এগুলা কাউন্ট করবে
        val countableDays = present + absent + late
        val totalDays = countableDays

        val percentage = if (totalDays > 0) {
            ((present.toFloat() + (late.toFloat() * 0.5f)) / totalDays.toFloat()) * 100f
        } else 0f

        SingleStudentReportData(
            student = student,
            classEntity = cls,
            fromDate = fromDate,
            toDate = toDate,
            totalDays = totalDays,
            present = present,
            absent = absent,
            late = late,
            excused = excused,
            percentage = percentage,
            records = records
        )
    }

    // --- Sync Engine ---

    fun triggerSync(userUuid: String) {
        repositoryScope.launch {
            performSync(userUuid)
        }
    }

    suspend fun performSync(userUuid: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            _syncStatus.value = SyncStatus.Error("No internet connection. Changes saved offline.")
            return@withContext Result.failure(Exception("Offline"))
        }

        _syncStatus.value = SyncStatus.Syncing

        try {
            val unsyncedClasses = classDao.getUnsyncedClasses(userUuid)
            val unsyncedStudents = studentDao.getUnsyncedStudents(userUuid)
            val unsyncedAttendance = attendanceDao.getUnsyncedAttendance(userUuid)
            val unsyncedPayments = feePaymentDao.getUnsyncedPayments(userUuid)

            val classDtos = unsyncedClasses.map {
                SyncClassDto(
                    uuid = it.uuid,
                    className = it.className,
                    section = it.section,
                    subject = it.subject,
                    isDeleted = if (it.isDeleted) 1 else 0,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }

            val studentDtos = unsyncedStudents.map {
                SyncStudentDto(
                    uuid = it.uuid,
                    classUuid = it.classUuid,
                    rollNumber = it.rollNumber,
                    name = it.name,
                    gender = it.gender,
                    phone = it.phone,
                    email = it.email,
                    monthlyFee = it.monthlyFee,
                    admissionDate = it.admissionDate,
                    isDeleted = if (it.isDeleted) 1 else 0,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }

            val attendanceDtos = unsyncedAttendance.map {
                SyncAttendanceDto(
                    uuid = it.uuid,
                    classUuid = it.classUuid,
                    studentUuid = it.studentUuid,
                    date = it.date,
                    status = it.status,
                    remarks = it.remarks,
                    isDeleted = if (it.isDeleted) 1 else 0,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }

            val paymentDtos = unsyncedPayments.map {
                SyncPaymentDto(
                    uuid = it.uuid,
                    studentUuid = it.studentUuid,
                    classUuid = it.classUuid,
                    amountPaid = it.amountPaid,
                    paymentDate = it.paymentDate,
                    receiptNo = it.receiptNo,
                    monthCovered = it.monthCovered,
                    note = it.note,
                    createdAt = it.createdAt
                )
            }

            val lastTime = prefs.getLong("last_sync_timestamp", 0L)
            val request = SyncRequest(
                userUuid = userUuid,
                lastSyncTimestamp = lastTime,
                classes = classDtos,
                students = studentDtos,
                attendance = attendanceDtos,
                payments = paymentDtos
            )

            val response = apiClient.getApiService().sync(request)
            if (response.isSuccessful && response.body()?.success == true) {
                val syncData = response.body()?.data

                // Mark local uploaded items as synced
                if (unsyncedClasses.isNotEmpty()) {
                    classDao.markAsSynced(unsyncedClasses.map { it.uuid })
                }
                if (unsyncedStudents.isNotEmpty()) {
                    studentDao.markAsSynced(unsyncedStudents.map { it.uuid })
                }
                if (unsyncedAttendance.isNotEmpty()) {
                    attendanceDao.markAsSynced(unsyncedAttendance.map { it.uuid })
                }
                if (unsyncedPayments.isNotEmpty()) {
                    feePaymentDao.markAsSynced(unsyncedPayments.map { it.uuid })
                }

                // Merge server updates into Room
                syncData?.classes?.let { serverClasses ->
                    val entities = serverClasses.map {
                        ClassEntity(
                            uuid = it.uuid,
                            userUuid = userUuid,
                            className = it.className,
                            section = it.section ?: "",
                            subject = it.subject ?: "",
                            isDeleted = it.isDeleted == 1,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            isSynced = true
                        )
                    }
                    if (entities.isNotEmpty()) classDao.insertOrUpdateAll(entities)
                }

                syncData?.students?.let { serverStudents ->
                    val entities = serverStudents.map {
                        StudentEntity(
                            uuid = it.uuid,
                            classUuid = it.classUuid,
                            userUuid = userUuid,
                            rollNumber = it.rollNumber,
                            name = it.name,
                            gender = it.gender ?: "Not Specified",
                            phone = it.phone ?: "",
                            email = it.email ?: "",
                            monthlyFee = it.monthlyFee ?: 500.0,
                            admissionDate = it.admissionDate ?: "",
                            isDeleted = it.isDeleted == 1,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            isSynced = true
                        )
                    }
                    if (entities.isNotEmpty()) studentDao.insertOrUpdateAll(entities)
                }

                syncData?.attendance?.let { serverAtt ->
                    val entities = serverAtt.map {
                        AttendanceEntity(
                            uuid = it.uuid,
                            userUuid = userUuid,
                            classUuid = it.classUuid,
                            studentUuid = it.studentUuid,
                            date = it.date,
                            status = it.status,
                            remarks = it.remarks ?: "",
                            isDeleted = it.isDeleted == 1,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            isSynced = true
                        )
                    }
                    if (entities.isNotEmpty()) attendanceDao.insertOrUpdateAll(entities)
                }

                syncData?.payments?.let { serverPayments ->
                    val entities = serverPayments.map {
                        FeePaymentEntity(
                            uuid = it.uuid,
                            userUuid = userUuid,
                            studentUuid = it.studentUuid,
                            classUuid = it.classUuid,
                            amountPaid = it.amountPaid,
                            paymentDate = it.paymentDate,
                            receiptNo = it.receiptNo ?: "",
                            monthCovered = it.monthCovered ?: "",
                            note = it.note ?: "",
                            createdAt = it.createdAt,
                            isSynced = true
                        )
                    }
                    if (entities.isNotEmpty()) feePaymentDao.insertOrUpdateAll(entities)
                }

                val newTimestamp = syncData?.serverTimestamp ?: System.currentTimeMillis()
                prefs.edit().putLong("last_sync_timestamp", newTimestamp).apply()
                _lastSyncTimestamp.value = newTimestamp

                val successMsg = "Synced successfully with server."
                _syncStatus.value = SyncStatus.Success(newTimestamp, successMsg)
                return@withContext Result.success(successMsg)
            } else {
                val errorMsg = response.body()?.message ?: "Server sync rejected (${response.code()})"
                _syncStatus.value = SyncStatus.Error(errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Sync error: ${e.localizedMessage ?: "Failed to reach server"}. Saved locally."
            _syncStatus.value = SyncStatus.Error(errorMsg)
            return@withContext Result.failure(e)
        }
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getUnsyncedCount(userUuid: String): Flow<Int> {
        return combine(
            attendanceDao.getUnsyncedCount(userUuid),
            feePaymentDao.getUnsyncedCount(userUuid)
        ) { att, fee ->
            att + fee
        }
    }
}
