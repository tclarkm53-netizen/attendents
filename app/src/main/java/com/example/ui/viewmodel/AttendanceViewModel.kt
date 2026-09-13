package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ClassEntity
import com.example.data.local.entity.FeePaymentEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.ApiClient
import com.example.data.remote.LoginRequest
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ClassReportData
import com.example.data.repository.SingleStudentReportData
import com.example.data.repository.StudentFeeSummary
import com.example.data.repository.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AttendanceRepository(application)
    private val apiClient = ApiClient.getInstance(application)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _isAuthChecking = MutableStateFlow(true)
    val isAuthChecking: StateFlow<Boolean> = _isAuthChecking.asStateFlow()

    val activeUser: StateFlow<UserEntity?> = repository.activeUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus
    val lastSyncTimestamp: StateFlow<Long> = repository.lastSyncTimestamp

    // Classes for active user
    val classes: StateFlow<List<ClassEntity>> = activeUser.flatMapLatest { user ->
        if (user != null) {
            repository.getClasses(user.uuid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedClass = MutableStateFlow<ClassEntity?>(null)
    val selectedClass: StateFlow<ClassEntity?> = _selectedClass.asStateFlow()

    private val _selectedDate = MutableStateFlow(dateFormat.format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Students in selected class
    val studentsInSelectedClass: StateFlow<List<StudentEntity>> = combine(_selectedClass, activeUser) { cls, user ->
        Pair(cls, user)
    }.flatMapLatest { (cls, user) ->
        if (cls != null && user != null) {
            repository.getStudentsByClass(cls.uuid, user.uuid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current attendance state: studentUuid -> Pair(status, remarks)
    private val _attendanceMap = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val attendanceMap: StateFlow<Map<String, Pair<String, String>>> = _attendanceMap.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _unsyncedCount = MutableStateFlow(0)
    val unsyncedCount: StateFlow<Int> = _unsyncedCount.asStateFlow()

    // Server URL
    private val _serverUrl = MutableStateFlow(apiClient.getSavedBaseUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Reports State
    private val _classReportData = MutableStateFlow<ClassReportData?>(null)
    val classReportData: StateFlow<ClassReportData?> = _classReportData.asStateFlow()

    private val _studentReportData = MutableStateFlow<SingleStudentReportData?>(null)
    val studentReportData: StateFlow<SingleStudentReportData?> = _studentReportData.asStateFlow()

    private val _isReportLoading = MutableStateFlow(false)
    val isReportLoading: StateFlow<Boolean> = _isReportLoading.asStateFlow()

    // Fee Collection State
    private val _classFeeSummaries = MutableStateFlow<List<StudentFeeSummary>>(emptyList())
    val classFeeSummaries: StateFlow<List<StudentFeeSummary>> = _classFeeSummaries.asStateFlow()

    private val _isFeeLoading = MutableStateFlow(false)
    val isFeeLoading: StateFlow<Boolean> = _isFeeLoading.asStateFlow()

    init {
        // Observe active user changes to auto-select first class & sync unsynced count
        viewModelScope.launch {
            repository.activeUser.collect { user ->
                _isAuthChecking.value = false
                if (user != null) {
                    triggerSync()
                    repository.getUnsyncedCount(user.uuid).collect { count ->
                        _unsyncedCount.value = count
                    }
                } else {
                    _selectedClass.value = null
                    _attendanceMap.value = emptyMap()
                    _classReportData.value = null
                    _studentReportData.value = null
                    _classFeeSummaries.value = emptyList()
                    _unsyncedCount.value = 0
                }
            }
        }

        // Real-time background sync every 20 seconds
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(20_000)
                val user = activeUser.value
                if (user != null && repository.isNetworkAvailable()) {
                    repository.performSync(user.uuid)
                }
            }
        }

        // Auto select first class when classes update if none selected
        viewModelScope.launch {
            classes.collect { list ->
                if (_selectedClass.value == null && list.isNotEmpty()) {
                    _selectedClass.value = list.first()
                } else if (_selectedClass.value != null && list.none { it.uuid == _selectedClass.value?.uuid }) {
                    _selectedClass.value = list.firstOrNull()
                }
            }
        }

        // Load attendance whenever class or date changes
        viewModelScope.launch {
            _selectedClass.collect { cls ->
                loadAttendanceForCurrentSelection()
            }
        }
        viewModelScope.launch {
            _selectedDate.collect {
                loadAttendanceForCurrentSelection()
            }
        }
        viewModelScope.launch {
            studentsInSelectedClass.collect {
                loadFeeSummariesForSelectedClass()
            }
        }
    }

    fun selectClass(classEntity: ClassEntity) {
        _selectedClass.value = classEntity
    }

    fun setSelectedDate(dateStr: String) {
        _selectedDate.value = dateStr
    }

    fun changeDateByDays(offset: Int) {
        try {
            val cal = Calendar.getInstance()
            val parsed = dateFormat.parse(_selectedDate.value)
            if (parsed != null) {
                cal.time = parsed
                cal.add(Calendar.DAY_OF_YEAR, offset)
                _selectedDate.value = dateFormat.format(cal.time)
            }
        } catch (e: Exception) {
            _selectedDate.value = dateFormat.format(Date())
        }
    }

    private var attendanceJob: kotlinx.coroutines.Job? = null

    fun getTodayDateString(): String = dateFormat.format(Date())

    fun isCurrentDateSelected(): Boolean = _selectedDate.value == getTodayDateString()

    fun isStudentAdmittedOnOrBefore(student: StudentEntity, targetDateStr: String): Boolean {
        val admStr = student.admissionDate.trim()
        if (admStr.isEmpty()) {
            val createdCal = Calendar.getInstance().apply { timeInMillis = student.createdAt }
            val createdDate = String.format(
                Locale.US, "%04d-%02d-%02d",
                createdCal.get(Calendar.YEAR),
                createdCal.get(Calendar.MONTH) + 1,
                createdCal.get(Calendar.DAY_OF_MONTH)
            )
            return targetDateStr >= createdDate
        }
        return try {
            val admDate = dateFormat.parse(admStr)
            val targetDate = dateFormat.parse(targetDateStr)
            if (admDate != null && targetDate != null) {
                !targetDate.before(admDate)
            } else {
                targetDateStr >= admStr
            }
        } catch (e: Exception) {
            targetDateStr >= admStr
        }
    }

    fun loadAttendanceForCurrentSelection() {
        val cls = _selectedClass.value ?: run {
            _attendanceMap.value = emptyMap()
            return
        }
        val user = activeUser.value ?: run {
            _attendanceMap.value = emptyMap()
            return
        }
        val date = _selectedDate.value
        attendanceJob?.cancel()
        attendanceJob = viewModelScope.launch {
            repository.getAttendanceForClassAndDate(cls.uuid, date, user.uuid).collect { records ->
                val map = records.associate { it.studentUuid to Pair(it.status, it.remarks) }
                _attendanceMap.value = map
            }
        }
    }

    fun markStudent(studentUuid: String, status: String, remarks: String = "") {
        val current = _attendanceMap.value.toMutableMap()
        val oldRemarks = current[studentUuid]?.second ?: ""
        current[studentUuid] = Pair(status, if (remarks.isNotBlank()) remarks else oldRemarks)
        _attendanceMap.value = current
    }

    fun markAll(status: String) {
        val date = _selectedDate.value
        val admittedStudents = studentsInSelectedClass.value.filter { isStudentAdmittedOnOrBefore(it, date) }
        val current = _attendanceMap.value.toMutableMap()
        for (s in admittedStudents) {
            val oldRemarks = current[s.uuid]?.second ?: ""
            current[s.uuid] = Pair(status, oldRemarks)
        }
        _attendanceMap.value = current
    }

    fun unmarkAll() {
        _attendanceMap.value = emptyMap()
    }

    fun saveAttendance(onComplete: (Boolean, String) -> Unit) {
        val user = activeUser.value ?: return onComplete(false, "লগইন করা আবশ্যক")
        val cls = _selectedClass.value ?: return onComplete(false, "কোনো ক্লাস নির্বাচন করা হয়নি")
        val date = _selectedDate.value

        val admittedStudents = studentsInSelectedClass.value.filter { isStudentAdmittedOnOrBefore(it, date) }
        if (admittedStudents.isEmpty()) {
            return onComplete(false, "এই তারিখে কোনো ভর্তিকৃত শিক্ষার্থী নেই")
        }

        val currentMap = _attendanceMap.value
        val markedMap = mutableMapOf<String, Pair<String, String>>()
        for (s in admittedStudents) {
            val existing = currentMap[s.uuid]
            if (existing != null && existing.first.isNotBlank()) {
                markedMap[s.uuid] = existing
            }
        }

        if (markedMap.isEmpty()) {
            return onComplete(false, "কোনো শিক্ষার্থীর হাজিরা নির্বাচন করা হয়নি। অনুগ্রহ করে শিক্ষার্থীদের হাজিরা দিন অথবা 'সবাই উপস্থিত' চাপুন।")
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.saveAttendance(
                    userUuid = user.uuid,
                    classUuid = cls.uuid,
                    date = date,
                    studentStatusMap = markedMap
                )
                _isSaving.value = false
                onComplete(true, "$date তারিখের (${markedMap.size} জনের) হাজিরা সফলভাবে সংরক্ষিত হয়েছে")
            } catch (e: Exception) {
                _isSaving.value = false
                onComplete(false, "সংরক্ষণ ব্যর্থ: ${e.localizedMessage}")
            }
        }
    }

    // --- Class & Student Actions ---

    fun addClass(className: String, section: String, subject: String, onResult: (Boolean, String) -> Unit) {
        val user = activeUser.value ?: return onResult(false, "Login required")
        if (className.isBlank()) return onResult(false, "Class name cannot be empty")

        viewModelScope.launch {
            try {
                repository.addClass(user.uuid, className, section, subject)
                onResult(true, "Class added successfully")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Failed to add class")
            }
        }
    }

    fun deleteClass(classUuid: String) {
        val user = activeUser.value ?: return
        viewModelScope.launch {
            repository.deleteClass(classUuid, user.uuid)
        }
    }

    fun addStudent(
        classUuid: String,
        roll: String,
        name: String,
        gender: String,
        phone: String,
        email: String,
        monthlyFee: Double = 0.0,
        admissionDate: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        val user = activeUser.value ?: return onResult(false, "Login required")
        if (roll.isBlank() || name.isBlank()) return onResult(false, "Roll number and name required")

        viewModelScope.launch {
            try {
                repository.addStudent(classUuid, user.uuid, roll, name, gender, phone, email, monthlyFee, admissionDate)
                loadFeeSummariesForSelectedClass()
                onResult(true, "Student added successfully")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Failed to add student")
            }
        }
    }

    fun updateStudentMonthlyFee(studentUuid: String, monthlyFee: Double) {
        val user = activeUser.value ?: return
        viewModelScope.launch {
            repository.updateStudentMonthlyFee(studentUuid, monthlyFee, user.uuid)
            loadFeeSummariesForSelectedClass()
        }
    }

    fun updateStudent(
        studentUuid: String,
        roll: String,
        name: String,
        phone: String,
        monthlyFee: Double,
        admissionDate: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val user = activeUser.value ?: return onResult(false, "লগইন করা প্রয়োজন")
        viewModelScope.launch {
            try {
                repository.updateStudent(
                    studentUuid = studentUuid,
                    userUuid = user.uuid,
                    roll = roll,
                    name = name,
                    phone = phone,
                    monthlyFee = monthlyFee,
                    admissionDate = admissionDate
                )
                loadFeeSummariesForSelectedClass()
                onResult(true, "শিক্ষার্থীর তথ্য সফলভাবে আপডেট হয়েছে")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "শিক্ষার্থী আপডেট ব্যর্থ হয়েছে")
            }
        }
    }

    fun deleteStudent(studentUuid: String) {
        val user = activeUser.value ?: return
        viewModelScope.launch {
            repository.deleteStudent(studentUuid, user.uuid)
            loadFeeSummariesForSelectedClass()
        }
    }

    // --- Fee Collection Actions ---

    fun loadFeeSummariesForSelectedClass() {
        val cls = _selectedClass.value ?: return
        val user = activeUser.value ?: return
        viewModelScope.launch {
            _isFeeLoading.value = true
            try {
                val students = studentsInSelectedClass.value
                val summaries = students.map { student ->
                    repository.calculateStudentFeeSummary(student, user.uuid)
                }
                _classFeeSummaries.value = summaries
            } catch (e: Exception) {
                // Keep existing if any error
            } finally {
                _isFeeLoading.value = false
            }
        }
    }

    fun addFeePayment(
        student: StudentEntity,
        amount: Double,
        paymentDate: String,
        receiptNo: String = "",
        monthCovered: String = "",
        note: String = "",
        onResult: (Boolean, String, FeePaymentEntity?) -> Unit
    ) {
        val user = activeUser.value ?: return onResult(false, "লগইন প্রয়োজন", null)
        if (amount <= 0.0) return onResult(false, "পরিশোধের পরিমাণ ০ এর চেয়ে বেশি হতে হবে", null)

        viewModelScope.launch {
            try {
                val payment = repository.addFeePayment(
                    userUuid = user.uuid,
                    studentUuid = student.uuid,
                    classUuid = student.classUuid,
                    amount = amount,
                    paymentDate = paymentDate,
                    receiptNo = receiptNo,
                    monthCovered = monthCovered,
                    note = note
                )
                loadFeeSummariesForSelectedClass()
                onResult(true, "৳${amount.toInt()} টাকা বেতন সফলভাবে জমা হয়েছে!", payment)
            } catch (e: Exception) {
                onResult(false, "ব্যর্থ হয়েছে: ${e.localizedMessage}", null)
            }
        }
    }

    fun getPaymentsForStudent(studentUuid: String) =
        repository.getPaymentsForStudent(studentUuid, activeUser.value?.uuid)

    fun deleteFeePayment(paymentUuid: String, onResult: (Boolean, String) -> Unit) {
        val user = activeUser.value ?: return onResult(false, "লগইন প্রয়োজন")
        viewModelScope.launch {
            try {
                repository.deleteFeePayment(paymentUuid, user.uuid)
                loadFeeSummariesForSelectedClass()
                onResult(true, "পেমেন্ট সফলভাবে ডিলিট করা হয়েছে")
            } catch (e: Exception) {
                onResult(false, "পেমেন্ট ডিলিট ব্যর্থ: ${e.localizedMessage}")
            }
        }
    }

    // --- Auth Actions ---

    fun login(email: String, pass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.login(email, pass)
            if (result.isSuccess) {
                onResult(true, "Welcome back, ${result.getOrNull()?.name}!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun register(name: String, email: String, pass: String, inst: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.register(name, email, pass, inst)
            if (result.isSuccess) {
                onResult(true, "Account created successfully!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    fun registerOffline(name: String, email: String, inst: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.registerLocally(name, email, inst)
            if (result.isSuccess) {
                onResult(true, "অফলাইন একাউন্ট সফলভাবে তৈরি হয়েছে!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "অফলাইন একাউন্ট তৈরি ব্যর্থ হয়েছে")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _selectedClass.value = null
            _attendanceMap.value = emptyMap()
            _classReportData.value = null
            _studentReportData.value = null
            _classFeeSummaries.value = emptyList()
            _unsyncedCount.value = 0
            repository.logout()
        }
    }

    // --- Sync Actions ---

    fun triggerSync() {
        val user = activeUser.value ?: return
        repository.triggerSync(user.uuid)
    }

    fun setServerUrl(newUrl: String) {
        apiClient.setBaseUrl(newUrl)
        _serverUrl.value = apiClient.getSavedBaseUrl()
    }

    fun testServerConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                if (!repository.isNetworkAvailable()) {
                    onResult(false, "ডিভাইসে কোনো ইন্টারনেট সংযোগ নেই।")
                    return@launch
                }
                // Try pinging login endpoint
                val response = apiClient.getApiService().login(LoginRequest("ping@server.com", "ping"))
                // If response comes back (even 401/400/200), server is reachable!
                if (response.code() in 200..499) {
                    onResult(true, "সার্ভার সংযোগ সফল! (HTTP ${response.code()})")
                } else {
                    onResult(false, "সার্ভার রেসপন্স ত্রুটি: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                onResult(false, "সার্ভারে সংযোগ করা সম্ভব হয়নি: ${e.localizedMessage}")
            }
        }
    }

    // --- Reports Generation ---

    fun loadClassReport(classUuid: String, fromDate: String, toDate: String) {
        viewModelScope.launch {
            _isReportLoading.value = true
            val user = activeUser.value
            val data = repository.getClassReport(classUuid, fromDate, toDate, user?.uuid)
            _classReportData.value = data
            _isReportLoading.value = false
        }
    }

    fun loadStudentReport(studentUuid: String, fromDate: String, toDate: String) {
        viewModelScope.launch {
            _isReportLoading.value = true
            val user = activeUser.value
            val data = repository.getStudentReport(studentUuid, fromDate, toDate, user?.uuid)
            _studentReportData.value = data
            _isReportLoading.value = false
        }
    }
}
