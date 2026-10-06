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
import kotlinx.coroutines.delay
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
    val items: List<StudentReportItem>,
    val skippedFridaysCount: Int = 0
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
    val records: List<AttendanceEntity>,
    val skippedFridaysCount: Int = 0
)

fun normalizeDateStr(raw: String): String {
    val trimmed = raw.trim().take(10)
    val sb = StringBuilder(trimmed.length)
    for (ch in trimmed) {
        when (ch) {
            in '০'..'৯' -> sb.append((ch - '০' + '0'.code).toChar())
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

fun isFridayDate(dateStr: String): Boolean {
    return try {
        val clean = normalizeDateStr(dateStr)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = sdf.parse(clean) ?: return false
        val cal = Calendar.getInstance().apply { time = parsed }
        cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
    } catch (e: Exception) {
        false
    }
}

fun getBengaliDayOfWeek(dateStr: String): String {
    return try {
        val clean = normalizeDateStr(dateStr)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = sdf.parse(clean) ?: return ""
        val cal = Calendar.getInstance().apply { time = parsed }
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.FRIDAY -> "শুক্রবার (ছুটি)"
            Calendar.SATURDAY -> "শনিবার"
            Calendar.SUNDAY -> "রবিবার"
            Calendar.MONDAY -> "সোমবার"
            Calendar.TUESDAY -> "মঙ্গলবার"
            Calendar.WEDNESDAY -> "বুধবার"
            Calendar.THURSDAY -> "বৃহস্পতিবার"
            else -> ""
        }
    } catch (e: Exception) {
        ""
    }
}

data class StudentFeeSummary(
    val student: StudentEntity,
    val monthlyFee: Double,
    val admissionDate: String,
    val elapsedDays: Long, // Counted active class/attendance days (P + A + L only)
    val elapsedMonths: Int, // elapsedDays / 30
    val runningMonthDays: Int = 0, // elapsedDays % 30
    val totalPayable: Double,
    val totalPaid: Double,
    val dueAmount: Double,
    val dueMonths: Int,
    val isFullyPaid: Boolean,
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val lateCount: Int = 0,
    val excusedCount: Int = 0,
    val skippedFridaysCount: Int = 0
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
                val msg = e.localizedMessage ?: ""
                serverError = if (msg.contains("JsonReader") || msg.contains("malformed JSON")) {
                    "সার্ভার থেকে সঠিক JSON রেসপন্স পাওয়া যায়নি। সার্ভারের PHP কনফিগারেশন বা URL পরীক্ষা করুন।"
                } else {
                    msg
                }
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
            return@withContext Result.failure(Exception("রেজিস্ট্রেশন করার জন্য ইন্টারনেট সংযোগ আবশ্যক। অফলাইনে ব্যবহার করতে চাইলে 'অফলাইনে একাউন্ট খুলুন' ব্যবহার করুন।"))
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
            val msg = e.localizedMessage ?: "সংযোগ পাওয়া যায়নি"
            val cleanMsg = if (msg.contains("JsonReader") || msg.contains("malformed JSON")) {
                "সার্ভার থেকে সঠিক JSON পাওয়া যায়নি। সার্ভার কনফিগারেশন বা PHP কোড চেক করুন।"
            } else {
                msg
            }
            return@withContext Result.failure(Exception("সার্ভারে রেজিস্ট্রেশন ব্যর্থ: $cleanMsg"))
        }
    }

    suspend fun registerLocally(name: String, email: String, inst: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        val existing = userDao.getUserByEmail(trimmedEmail)
        if (existing != null) {
            val updated = existing.copy(isLoggedIn = true, lastLoginTime = System.currentTimeMillis())
            userDao.insertOrUpdate(updated)
            return@withContext Result.success(updated)
        }
        val user = UserEntity(
            uuid = UUID.randomUUID().toString(),
            name = name.trim(),
            email = trimmedEmail,
            institution = inst.trim(),
            token = "offline_${System.currentTimeMillis()}",
            isLoggedIn = true,
            lastLoginTime = System.currentTimeMillis()
        )
        userDao.insertOrUpdate(user)
        Result.success(user)
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

    fun getStudentsByClass(classUuid: String, userUuid: String? = null): Flow<List<StudentEntity>> =
        if (userUuid != null) studentDao.getStudentsByClass(classUuid, userUuid)
        else studentDao.getStudentsByClass(classUuid)

    fun getAllStudents(userUuid: String): Flow<List<StudentEntity>> = studentDao.getAllStudentsForUser(userUuid)

    suspend fun addStudent(
        classUuid: String,
        userUuid: String,
        roll: String,
        name: String,
        gender: String,
        phone: String,
        email: String,
        monthlyFee: Double = 0.0,
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

    suspend fun updateStudent(
        studentUuid: String,
        userUuid: String,
        roll: String,
        name: String,
        phone: String,
        monthlyFee: Double,
        admissionDate: String
    ) = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val finalAdmDate = admissionDate.trim().ifBlank {
            val existing = studentDao.getStudentByUuid(studentUuid)
            existing?.admissionDate?.ifBlank { today } ?: today
        }
        studentDao.updateStudentDetails(
            uuid = studentUuid,
            roll = roll.trim(),
            name = name.trim(),
            phone = phone.trim(),
            monthlyFee = monthlyFee,
            admissionDate = finalAdmDate
        )
        triggerSync(userUuid)
    }

    suspend fun deleteStudent(studentUuid: String, userUuid: String) = withContext(Dispatchers.IO) {
        studentDao.softDelete(studentUuid)
        triggerSync(userUuid)
    }

    // --- Fee Collection & Payments ---

    fun getPaymentsForStudent(studentUuid: String, userUuid: String? = null): Flow<List<FeePaymentEntity>> =
        if (userUuid != null) feePaymentDao.getPaymentsForStudent(studentUuid, userUuid)
        else feePaymentDao.getPaymentsForStudent(studentUuid)

    fun getAllPaymentsForUser(userUuid: String): Flow<List<FeePaymentEntity>> =
        feePaymentDao.getAllPaymentsForUser(userUuid)

    fun getPaymentsForClass(classUuid: String, userUuid: String? = null): Flow<List<FeePaymentEntity>> =
        if (userUuid != null) feePaymentDao.getPaymentsForClass(classUuid, userUuid)
        else feePaymentDao.getPaymentsForClass(classUuid)

    fun getTotalPaidForStudent(studentUuid: String): Flow<Double> =
        feePaymentDao.getTotalPaidForStudent(studentUuid)

    suspend fun deleteFeePayment(paymentUuid: String, userUuid: String) = withContext(Dispatchers.IO) {
        feePaymentDao.softDelete(paymentUuid)
        triggerSync(userUuid)
    }

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
        val now = System.currentTimeMillis()

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
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
            isSynced = false
        )
        feePaymentDao.insertOrUpdate(payment)
        triggerSync(userUuid)
        payment
    }

    suspend fun calculateStudentFeeSummary(student: StudentEntity, userUuid: String? = null): StudentFeeSummary = withContext(Dispatchers.IO) {
        val totalPaid = feePaymentDao.getTotalPaidAmountForStudent(student.uuid)
        val monthlyFee = student.monthlyFee
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val normAdm = normalizeDateStr(student.admissionDate)

        val startDate = try {
            if (normAdm.isNotBlank()) sdf.parse(normAdm) ?: Date(student.createdAt)
            else Date(student.createdAt)
        } catch (e: Exception) {
            Date(student.createdAt)
        }

        // Fetch student's attendance records and deduplicate by normalized date
        val rawAttendance = attendanceDao.getAttendanceListForStudent(student.uuid)
        val allAttendance = rawAttendance
            .groupBy { normalizeDateStr(it.date) }
            .map { (_, list) -> list.maxByOrNull { it.updatedAt } ?: list.first() }

        // Rule: Only count PRESENT, ABSENT, LATE.
        // Friday is automatically skipped. Excused (ছুটি) is also not counted.
        val validAttendance = allAttendance.filter {
            !isFridayDate(it.date) && (it.status == "PRESENT" || it.status == "ABSENT" || it.status == "LATE")
        }

        val presentCount = validAttendance.count { it.status == "PRESENT" }
        val absentCount = validAttendance.count { it.status == "ABSENT" }
        val lateCount = validAttendance.count { it.status == "LATE" }
        val excusedCount = allAttendance.count { !isFridayDate(it.date) && it.status == "EXCUSED" }
        val skippedFridaysCount = allAttendance.count { isFridayDate(it.date) }

        val totalAttendedDays = validAttendance.size.toLong()

        // If attendance records exist, use the exact counted attendance days (e.g. 66 days).
        // If no attendance records exist at all yet, compute working days since admission (excluding Fridays).
        val elapsedDays: Long = if (allAttendance.isNotEmpty()) {
            totalAttendedDays
        } else {
            val todayParsed = sdf.parse(sdf.format(Date())) ?: Date()
            if (!startDate.after(todayParsed)) {
                var workingCount = 0L
                val cal = Calendar.getInstance().apply { time = startDate }
                val endCal = Calendar.getInstance().apply { time = todayParsed }
                while (!cal.after(endCal)) {
                    if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                        workingCount++
                    }
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
                workingCount
            } else 0L
        }

        // 30 days = 1 month billing cycle
        // e.g. 66 days -> 66 / 30 = 2 billable months, 66 % 30 = 6 days in running month
        val elapsedMonths = (elapsedDays / 30).toInt()
        val runningMonthDays = (elapsedDays % 30).toInt()

        val totalPayable = elapsedMonths * monthlyFee
        val dueAmount = maxOf(0.0, totalPayable - totalPaid)
        val dueMonths = if (monthlyFee > 0.0) {
            Math.ceil(dueAmount / monthlyFee).toInt()
        } else 0
        val isFullyPaid = dueAmount <= 0.0

        StudentFeeSummary(
            student = student,
            monthlyFee = monthlyFee,
            admissionDate = sdf.format(startDate),
            elapsedDays = elapsedDays,
            elapsedMonths = elapsedMonths,
            runningMonthDays = runningMonthDays,
            totalPayable = totalPayable,
            totalPaid = totalPaid,
            dueAmount = dueAmount,
            dueMonths = dueMonths,
            isFullyPaid = isFullyPaid,
            presentCount = presentCount,
            absentCount = absentCount,
            lateCount = lateCount,
            excusedCount = excusedCount,
            skippedFridaysCount = skippedFridaysCount
        )
    }

    // --- Attendance ---

    fun getAttendanceForClassAndDate(classUuid: String, date: String, userUuid: String? = null): Flow<List<AttendanceEntity>> =
        attendanceDao.getAttendanceForClassAndDate(classUuid, normalizeDateStr(date))

    suspend fun saveAttendance(
        userUuid: String,
        classUuid: String,
        date: String,
        studentStatusMap: Map<String, Pair<String, String>> // studentUuid -> Pair(status, remarks)
    ) = withContext(Dispatchers.IO) {
        val normalizedDate = normalizeDateStr(date)
        val now = System.currentTimeMillis()
        val existing = attendanceDao.getAttendanceListForClassAndDate(classUuid, normalizedDate).associateBy { it.studentUuid }
        val toSave = mutableListOf<AttendanceEntity>()

        for ((studentUuid, pair) in studentStatusMap) {
            val (status, remarks) = pair
            val existingItem = existing[studentUuid]
            val record = if (existingItem != null) {
                existingItem.copy(
                    userUuid = userUuid,
                    classUuid = classUuid,
                    date = normalizedDate,
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
                    date = normalizedDate,
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
        toDate: String,
        userUuid: String? = null
    ): ClassReportData? = withContext(Dispatchers.IO) {
        val cls = classDao.getClassByUuid(classUuid) ?: return@withContext null
        val normFrom = normalizeDateStr(fromDate)
        val normTo = normalizeDateStr(toDate)
        val students = studentDao.getStudentsByClassList(classUuid)
        val allClassAtt = attendanceDao.getAttendanceForClassBetweenDates(classUuid, "1970-01-01", "2099-12-31")
        val rawAttendanceList = allClassAtt.filter {
            val d = normalizeDateStr(it.date)
            d in normFrom..normTo
        }

        // Friday & Excused Auto-Skip Rule:
        // Weekly holiday (Friday) and Leave (Excused) are skipped from active class count.
        // Only PRESENT, ABSENT, and LATE count towards total days and attendance percentage.
        val nonFridayRecords = rawAttendanceList.filter { !isFridayDate(it.date) }
        val skippedFridaysCount = rawAttendanceList.filter { isFridayDate(it.date) }.map { normalizeDateStr(it.date) }.distinct().size

        // Deduplicate by (studentUuid, normalizedDate) taking latest updatedAt
        val dedupedRecords = nonFridayRecords
            .groupBy { it.studentUuid to normalizeDateStr(it.date) }
            .map { (_, list) -> list.maxByOrNull { it.updatedAt } ?: list.first() }

        val groupedByStudent = dedupedRecords.groupBy { it.studentUuid }
        val items = mutableListOf<StudentReportItem>()

        var totalClassPossible = 0
        var totalClassPresent = 0

        for (student in students) {
            val studentAtt = groupedByStudent[student.uuid] ?: emptyList()
            val present = studentAtt.count { it.status == "PRESENT" }
            val absent = studentAtt.count { it.status == "ABSENT" }
            val late = studentAtt.count { it.status == "LATE" }
            val excused = studentAtt.count { it.status == "EXCUSED" }
            // Only Present, Absent, Late are counted as working class days
            val totalDays = present + absent + late

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
            fromDate = normFrom,
            toDate = normTo,
            totalStudents = students.size,
            overallPercentage = overallPercentage,
            items = items,
            skippedFridaysCount = skippedFridaysCount
        )
    }

    suspend fun getStudentReport(
        studentUuid: String,
        fromDate: String,
        toDate: String,
        userUuid: String? = null
    ): SingleStudentReportData? = withContext(Dispatchers.IO) {
        val student = studentDao.getStudentByUuid(studentUuid) ?: return@withContext null
        val cls = classDao.getClassByUuid(student.classUuid)
        val normFrom = normalizeDateStr(fromDate)
        val normTo = normalizeDateStr(toDate)
        val allStudentAtt = attendanceDao.getAttendanceListForStudent(studentUuid)
        val rawRecords = allStudentAtt.filter {
            val d = normalizeDateStr(it.date)
            d in normFrom..normTo
        }

        // Friday & Excused Auto-Detect and Skip:
        // Friday is excluded completely.
        // Only Present, Absent, and Late count towards total working days and attendance percentage.
        val nonFridayRecords = rawRecords
            .filter { !isFridayDate(it.date) }
            .groupBy { normalizeDateStr(it.date) }
            .map { (_, list) -> list.maxByOrNull { it.updatedAt } ?: list.first() }
            .sortedByDescending { normalizeDateStr(it.date) }
        val skippedFridaysCount = rawRecords.filter { isFridayDate(it.date) }.map { normalizeDateStr(it.date) }.distinct().size

        val present = nonFridayRecords.count { it.status == "PRESENT" }
        val absent = nonFridayRecords.count { it.status == "ABSENT" }
        val late = nonFridayRecords.count { it.status == "LATE" }
        val excused = nonFridayRecords.count { it.status == "EXCUSED" }

        // Only P + A + L are counted as active class days
        val totalDays = present + absent + late

        val percentage = if (totalDays > 0) {
            ((present.toFloat() + (late.toFloat() * 0.5f)) / totalDays.toFloat()) * 100f
        } else 0f

        SingleStudentReportData(
            student = student,
            classEntity = cls,
            fromDate = normFrom,
            toDate = normTo,
            totalDays = totalDays,
            present = present,
            absent = absent,
            late = late,
            excused = excused,
            percentage = percentage,
            records = nonFridayRecords,
            skippedFridaysCount = skippedFridaysCount
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
                    userUuid = it.userUuid,
                    studentUuid = it.studentUuid,
                    classUuid = it.classUuid,
                    amountPaid = it.amountPaid,
                    paymentDate = it.paymentDate,
                    receiptNo = it.receiptNo,
                    monthCovered = it.monthCovered,
                    note = it.note,
                    isDeleted = if (it.isDeleted) 1 else 0,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
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

                // For items that were soft-deleted locally and uploaded to server (server permanently deleted them):
                // Hard delete them from Room so they are completely removed locally as well
                val deletedClasses = unsyncedClasses.filter { it.isDeleted }.map { it.uuid }
                if (deletedClasses.isNotEmpty()) classDao.hardDelete(deletedClasses)

                val deletedStudents = unsyncedStudents.filter { it.isDeleted }.map { it.uuid }
                if (deletedStudents.isNotEmpty()) studentDao.hardDelete(deletedStudents)

                val deletedAtt = unsyncedAttendance.filter { it.isDeleted }.map { it.uuid }
                if (deletedAtt.isNotEmpty()) attendanceDao.hardDelete(deletedAtt)

                val deletedPayments = unsyncedPayments.filter { it.isDeleted }.map { it.uuid }
                if (deletedPayments.isNotEmpty()) feePaymentDao.hardDelete(deletedPayments)

                val uploadedAttKeys = unsyncedAttendance
                    .map { it.studentUuid to normalizeDateStr(it.date) }
                    .toSet()

                // Mark remaining active uploaded items as synced
                val syncedClasses = unsyncedClasses.filter { !it.isDeleted }.map { it.uuid }
                if (syncedClasses.isNotEmpty()) classDao.markAsSynced(syncedClasses)

                val syncedStudents = unsyncedStudents.filter { !it.isDeleted }.map { it.uuid }
                if (syncedStudents.isNotEmpty()) studentDao.markAsSynced(syncedStudents)

                val syncedAttendance = unsyncedAttendance.filter { !it.isDeleted }.map { it.uuid }
                if (syncedAttendance.isNotEmpty()) attendanceDao.markAsSynced(syncedAttendance)

                val syncedPayments = unsyncedPayments.filter { !it.isDeleted }.map { it.uuid }
                if (syncedPayments.isNotEmpty()) feePaymentDao.markAsSynced(syncedPayments)

                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val currentUnsyncedAttKeys = attendanceDao.getUnsyncedAttendance(userUuid)
                    .map { it.studentUuid to normalizeDateStr(it.date) }
                    .toSet() + uploadedAttKeys

                // Merge server updates into Room ONLY when genuinely new or newer than local records
                syncData?.classes?.let { serverClasses ->
                    val localByUuid = classDao.getClassesList(userUuid).associateBy { it.uuid }
                    val entities = serverClasses.mapNotNull {
                        val local = localByUuid[it.uuid]
                        val incoming = ClassEntity(
                            uuid = it.uuid,
                            userUuid = userUuid,
                            className = it.className,
                            section = it.section ?: "",
                            subject = it.subject ?: "",
                            isDeleted = it.isDeleted == 1,
                            createdAt = if (it.createdAt > 0L) it.createdAt else (local?.createdAt ?: System.currentTimeMillis()),
                            updatedAt = if (it.updatedAt > 0L) it.updatedAt else (local?.updatedAt ?: System.currentTimeMillis()),
                            isSynced = true
                        )
                        if (local == null) {
                            if (!incoming.isDeleted) incoming else null
                        } else if (!local.isSynced || local.updatedAt >= incoming.updatedAt) {
                            null
                        } else if (
                            local.className != incoming.className ||
                            local.section != incoming.section ||
                            local.subject != incoming.subject ||
                            local.isDeleted != incoming.isDeleted
                        ) {
                            incoming
                        } else {
                            null
                        }
                    }
                    if (entities.isNotEmpty()) {
                        classDao.insertOrUpdateAll(entities)
                        if (entities.any { it.isDeleted }) classDao.purgeDeleted()
                    }
                }

                syncData?.students?.let { serverStudents ->
                    val localByUuid = studentDao.getAllStudentsForUserList(userUuid).associateBy { it.uuid }
                    val entities = serverStudents.mapNotNull {
                        val local = localByUuid[it.uuid]
                        val incomingAdm = if (!it.admissionDate.isNullOrBlank()) normalizeDateStr(it.admissionDate) else (local?.admissionDate ?: todayStr)
                        val incoming = StudentEntity(
                            uuid = it.uuid,
                            classUuid = it.classUuid,
                            userUuid = userUuid,
                            rollNumber = it.rollNumber,
                            name = it.name,
                            gender = it.gender ?: "Not Specified",
                            phone = it.phone ?: "",
                            email = it.email ?: "",
                            monthlyFee = it.monthlyFee ?: 0.0,
                            admissionDate = incomingAdm,
                            isDeleted = it.isDeleted == 1,
                            createdAt = if (it.createdAt > 0L) it.createdAt else (local?.createdAt ?: System.currentTimeMillis()),
                            updatedAt = if (it.updatedAt > 0L) it.updatedAt else (local?.updatedAt ?: System.currentTimeMillis()),
                            isSynced = true
                        )
                        if (local == null) {
                            if (!incoming.isDeleted) incoming else null
                        } else if (!local.isSynced || local.updatedAt >= incoming.updatedAt) {
                            null
                        } else if (
                            local.classUuid != incoming.classUuid ||
                            local.rollNumber != incoming.rollNumber ||
                            local.name != incoming.name ||
                            local.gender != incoming.gender ||
                            local.phone != incoming.phone ||
                            local.email != incoming.email ||
                            local.monthlyFee != incoming.monthlyFee ||
                            local.admissionDate != incoming.admissionDate ||
                            local.isDeleted != incoming.isDeleted
                        ) {
                            incoming
                        } else {
                            null
                        }
                    }
                    if (entities.isNotEmpty()) {
                        studentDao.insertOrUpdateAll(entities)
                        if (entities.any { it.isDeleted }) studentDao.purgeDeleted()
                    }
                }

                syncData?.attendance?.let { serverAtt ->
                    val localAll = attendanceDao.getAllAttendanceForUserList(userUuid)
                    val localByKey = localAll.associateBy { it.studentUuid to normalizeDateStr(it.date) }
                    val entities = serverAtt.mapNotNull {
                        val normDate = normalizeDateStr(it.date)
                        val key = it.studentUuid to normDate
                        if (key in currentUnsyncedAttKeys) return@mapNotNull null
                        val local = localByKey[key]
                        val incoming = AttendanceEntity(
                            uuid = local?.uuid ?: it.uuid,
                            userUuid = userUuid,
                            classUuid = it.classUuid,
                            studentUuid = it.studentUuid,
                            date = normDate,
                            status = it.status,
                            remarks = it.remarks ?: "",
                            isDeleted = it.isDeleted == 1,
                            createdAt = if (it.createdAt > 0L) it.createdAt else (local?.createdAt ?: System.currentTimeMillis()),
                            updatedAt = if (it.updatedAt > 0L) it.updatedAt else (local?.updatedAt ?: System.currentTimeMillis()),
                            isSynced = true
                        )
                        if (local == null) {
                            if (!incoming.isDeleted) incoming else null
                        } else if (!local.isSynced || local.updatedAt >= incoming.updatedAt) {
                            null
                        } else if (
                            local.status != incoming.status ||
                            local.remarks != incoming.remarks ||
                            local.isDeleted != incoming.isDeleted
                        ) {
                            incoming
                        } else {
                            null
                        }
                    }
                    if (entities.isNotEmpty()) {
                        attendanceDao.insertOrUpdateAll(entities)
                        if (entities.any { it.isDeleted }) attendanceDao.purgeDeleted()
                    }
                }

                syncData?.payments?.let { serverPayments ->
                    val localByUuid = feePaymentDao.getAllPaymentsForUserList(userUuid).associateBy { it.uuid }
                    val entities = serverPayments.mapNotNull {
                        val local = localByUuid[it.uuid]
                        val incoming = FeePaymentEntity(
                            uuid = it.uuid,
                            userUuid = userUuid,
                            studentUuid = it.studentUuid,
                            classUuid = it.classUuid,
                            amountPaid = it.amountPaid,
                            paymentDate = normalizeDateStr(it.paymentDate),
                            receiptNo = it.receiptNo ?: "",
                            monthCovered = it.monthCovered ?: "",
                            note = it.note ?: "",
                            isDeleted = it.isDeleted == 1,
                            createdAt = if (it.createdAt > 0L) it.createdAt else (local?.createdAt ?: System.currentTimeMillis()),
                            updatedAt = if (it.updatedAt > 0L) it.updatedAt else (local?.updatedAt ?: System.currentTimeMillis()),
                            isSynced = true
                        )
                        if (local == null) {
                            if (!incoming.isDeleted) incoming else null
                        } else if (!local.isSynced || local.updatedAt >= incoming.updatedAt) {
                            null
                        } else if (
                            local.amountPaid != incoming.amountPaid ||
                            local.paymentDate != incoming.paymentDate ||
                            local.receiptNo != incoming.receiptNo ||
                            local.monthCovered != incoming.monthCovered ||
                            local.note != incoming.note ||
                            local.isDeleted != incoming.isDeleted
                        ) {
                            incoming
                        } else {
                            null
                        }
                    }
                    if (entities.isNotEmpty()) {
                        feePaymentDao.insertOrUpdateAll(entities)
                        if (entities.any { it.isDeleted }) feePaymentDao.purgeDeleted()
                    }
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
