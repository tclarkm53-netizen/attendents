package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.ClassEntity
import com.example.data.local.entity.FeePaymentEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isLoggedIn = 1 LIMIT 1")
    fun getActiveUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE isLoggedIn = 1 LIMIT 1")
    suspend fun getActiveUser(): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(user: UserEntity)

    @Query("UPDATE users SET isLoggedIn = 0")
    suspend fun logoutAll()

    @Query("DELETE FROM users WHERE email = :email")
    suspend fun deleteByEmail(email: String)
}

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes WHERE userUuid = :userUuid AND isDeleted = 0 ORDER BY className ASC, section ASC")
    fun getClassesForUser(userUuid: String): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE userUuid = :userUuid AND isDeleted = 0")
    suspend fun getClassesList(userUuid: String): List<ClassEntity>

    @Query("SELECT * FROM classes WHERE uuid = :uuid LIMIT 1")
    suspend fun getClassByUuid(uuid: String): ClassEntity?

    @Query("SELECT * FROM classes WHERE isSynced = 0 AND userUuid = :userUuid")
    suspend fun getUnsyncedClasses(userUuid: String): List<ClassEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(classEntity: ClassEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(classes: List<ClassEntity>)

    @Query("UPDATE classes SET isSynced = 1 WHERE uuid IN (:uuids)")
    suspend fun markAsSynced(uuids: List<String>)

    @Query("UPDATE classes SET isDeleted = 1, isSynced = 0, updatedAt = :timestamp WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM classes WHERE userUuid = :userUuid AND isDeleted = 0")
    fun getClassCount(userUuid: String): Flow<Int>

    @Query("DELETE FROM classes WHERE uuid IN (:uuids)")
    suspend fun hardDelete(uuids: List<String>)

    @Query("DELETE FROM classes WHERE isDeleted = 1")
    suspend fun purgeDeleted()

    @Query("UPDATE classes SET isSynced = 0 WHERE uuid IN (:uuids)")
    suspend fun markAsUnsynced(uuids: List<String>)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE classUuid = :classUuid AND isDeleted = 0 ORDER BY CAST(rollNumber AS INTEGER) ASC, rollNumber ASC")
    fun getStudentsByClass(classUuid: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE classUuid = :classUuid AND userUuid = :userUuid AND isDeleted = 0 ORDER BY CAST(rollNumber AS INTEGER) ASC, rollNumber ASC")
    fun getStudentsByClass(classUuid: String, userUuid: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE userUuid = :userUuid AND isDeleted = 0 ORDER BY name ASC")
    fun getAllStudentsForUser(userUuid: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE userUuid = :userUuid AND isDeleted = 0 ORDER BY name ASC")
    suspend fun getAllStudentsForUserList(userUuid: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE classUuid = :classUuid AND isDeleted = 0")
    suspend fun getStudentsByClassList(classUuid: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE classUuid = :classUuid AND userUuid = :userUuid AND isDeleted = 0")
    suspend fun getStudentsByClassList(classUuid: String, userUuid: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE uuid = :uuid LIMIT 1")
    suspend fun getStudentByUuid(uuid: String): StudentEntity?

    @Query("SELECT * FROM students WHERE isSynced = 0 AND userUuid = :userUuid")
    suspend fun getUnsyncedStudents(userUuid: String): List<StudentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(students: List<StudentEntity>)

    @Query("UPDATE students SET isSynced = 1 WHERE uuid IN (:uuids)")
    suspend fun markAsSynced(uuids: List<String>)

    @Query("UPDATE students SET isDeleted = 1, isSynced = 0, updatedAt = :timestamp WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE students SET monthlyFee = :monthlyFee, updatedAt = :timestamp, isSynced = 0 WHERE uuid = :uuid")
    suspend fun updateMonthlyFee(uuid: String, monthlyFee: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE students SET rollNumber = :roll, name = :name, phone = :phone, monthlyFee = :monthlyFee, admissionDate = :admissionDate, updatedAt = :timestamp, isSynced = 0 WHERE uuid = :uuid")
    suspend fun updateStudentDetails(uuid: String, roll: String, name: String, phone: String, monthlyFee: Double, admissionDate: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM students WHERE userUuid = :userUuid AND isDeleted = 0")
    fun getStudentCount(userUuid: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM students WHERE classUuid = :classUuid AND isDeleted = 0")
    fun getStudentCountForClass(classUuid: String): Flow<Int>

    @Query("DELETE FROM students WHERE uuid IN (:uuids)")
    suspend fun hardDelete(uuids: List<String>)

    @Query("DELETE FROM students WHERE isDeleted = 1")
    suspend fun purgeDeleted()

    @Query("UPDATE students SET isSynced = 0 WHERE uuid IN (:uuids)")
    suspend fun markAsUnsynced(uuids: List<String>)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE classUuid = :classUuid AND date = :date AND isDeleted = 0")
    fun getAttendanceForClassAndDate(classUuid: String, date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE classUuid = :classUuid AND userUuid = :userUuid AND date = :date AND isDeleted = 0")
    fun getAttendanceForClassAndDate(classUuid: String, userUuid: String, date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE classUuid = :classUuid AND date = :date AND isDeleted = 0")
    suspend fun getAttendanceListForClassAndDate(classUuid: String, date: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE classUuid = :classUuid AND userUuid = :userUuid AND date = :date AND isDeleted = 0")
    suspend fun getAttendanceListForClassAndDate(classUuid: String, userUuid: String, date: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE studentUuid = :studentUuid AND isDeleted = 0 ORDER BY date DESC")
    fun getAttendanceForStudent(studentUuid: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE studentUuid = :studentUuid AND userUuid = :userUuid AND isDeleted = 0 ORDER BY date DESC")
    fun getAttendanceForStudent(studentUuid: String, userUuid: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE studentUuid = :studentUuid AND date BETWEEN :fromDate AND :toDate AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getAttendanceForStudentBetweenDates(studentUuid: String, fromDate: String, toDate: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE studentUuid = :studentUuid AND userUuid = :userUuid AND date BETWEEN :fromDate AND :toDate AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getAttendanceForStudentBetweenDates(studentUuid: String, userUuid: String, fromDate: String, toDate: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE classUuid = :classUuid AND date BETWEEN :fromDate AND :toDate AND isDeleted = 0")
    suspend fun getAttendanceForClassBetweenDates(classUuid: String, fromDate: String, toDate: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE classUuid = :classUuid AND userUuid = :userUuid AND date BETWEEN :fromDate AND :toDate AND isDeleted = 0")
    suspend fun getAttendanceForClassBetweenDates(classUuid: String, userUuid: String, fromDate: String, toDate: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE userUuid = :userUuid AND isDeleted = 0")
    suspend fun getAllAttendanceForUserList(userUuid: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE isSynced = 0 AND userUuid = :userUuid")
    suspend fun getUnsyncedAttendance(userUuid: String): List<AttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(attendance: AttendanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(attendanceList: List<AttendanceEntity>)

    @Query("UPDATE attendance SET isSynced = 1 WHERE uuid IN (:uuids)")
    suspend fun markAsSynced(uuids: List<String>)

    @Query("SELECT COUNT(*) FROM attendance WHERE userUuid = :userUuid AND isDeleted = 0")
    fun getAttendanceCount(userUuid: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance WHERE isSynced = 0 AND userUuid = :userUuid")
    fun getUnsyncedCount(userUuid: String): Flow<Int>

    @Query("DELETE FROM attendance WHERE uuid IN (:uuids)")
    suspend fun hardDelete(uuids: List<String>)

    @Query("DELETE FROM attendance WHERE isDeleted = 1")
    suspend fun purgeDeleted()

    @Query("UPDATE attendance SET isSynced = 0 WHERE uuid IN (:uuids)")
    suspend fun markAsUnsynced(uuids: List<String>)
}

@Dao
interface FeePaymentDao {
    @Query("SELECT * FROM fee_payments WHERE studentUuid = :studentUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getPaymentsForStudent(studentUuid: String): Flow<List<FeePaymentEntity>>

    @Query("SELECT * FROM fee_payments WHERE studentUuid = :studentUuid AND userUuid = :userUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getPaymentsForStudent(studentUuid: String, userUuid: String): Flow<List<FeePaymentEntity>>

    @Query("SELECT * FROM fee_payments WHERE studentUuid = :studentUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getPaymentsListForStudent(studentUuid: String): List<FeePaymentEntity>

    @Query("SELECT * FROM fee_payments WHERE studentUuid = :studentUuid AND userUuid = :userUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getPaymentsListForStudent(studentUuid: String, userUuid: String): List<FeePaymentEntity>

    @Query("SELECT * FROM fee_payments WHERE userUuid = :userUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllPaymentsForUser(userUuid: String): Flow<List<FeePaymentEntity>>

    @Query("SELECT * FROM fee_payments WHERE userUuid = :userUuid AND isDeleted = 0")
    suspend fun getAllPaymentsForUserList(userUuid: String): List<FeePaymentEntity>

    @Query("SELECT * FROM fee_payments WHERE classUuid = :classUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getPaymentsForClass(classUuid: String): Flow<List<FeePaymentEntity>>

    @Query("SELECT * FROM fee_payments WHERE classUuid = :classUuid AND userUuid = :userUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getPaymentsForClass(classUuid: String, userUuid: String): Flow<List<FeePaymentEntity>>

    @Query("SELECT COALESCE(SUM(amountPaid), 0.0) FROM fee_payments WHERE studentUuid = :studentUuid AND isDeleted = 0")
    fun getTotalPaidForStudent(studentUuid: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(amountPaid), 0.0) FROM fee_payments WHERE studentUuid = :studentUuid AND isDeleted = 0")
    suspend fun getTotalPaidAmountForStudent(studentUuid: String): Double

    @Query("SELECT COALESCE(SUM(amountPaid), 0.0) FROM fee_payments WHERE studentUuid = :studentUuid AND userUuid = :userUuid AND isDeleted = 0")
    suspend fun getTotalPaidAmountForStudent(studentUuid: String, userUuid: String): Double

    @Query("SELECT * FROM fee_payments WHERE isSynced = 0 AND userUuid = :userUuid")
    suspend fun getUnsyncedPayments(userUuid: String): List<FeePaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(payment: FeePaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(payments: List<FeePaymentEntity>)

    @Query("UPDATE fee_payments SET isSynced = 1 WHERE uuid IN (:uuids)")
    suspend fun markAsSynced(uuids: List<String>)

    @Query("UPDATE fee_payments SET isDeleted = 1, isSynced = 0, updatedAt = :timestamp WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM fee_payments WHERE isSynced = 0 AND userUuid = :userUuid")
    fun getUnsyncedCount(userUuid: String): Flow<Int>

    @Query("DELETE FROM fee_payments WHERE uuid IN (:uuids)")
    suspend fun hardDelete(uuids: List<String>)

    @Query("DELETE FROM fee_payments WHERE isDeleted = 1")
    suspend fun purgeDeleted()

    @Query("UPDATE fee_payments SET isSynced = 0 WHERE uuid IN (:uuids)")
    suspend fun markAsUnsynced(uuids: List<String>)
}
