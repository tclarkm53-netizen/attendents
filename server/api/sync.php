<?php
/**
 * Bidirectional Sync API Endpoint
 * Handles batch syncing of classes, students, and attendance records.
 * Uses UUIDs and Upsert logic to ensure zero data loss and no duplicates.
 * Method: POST
 */

require_once __DIR__ . '/../db.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(false, 'Method not allowed. Use POST.', null, 405);
}

$input = getJsonInput();

$userUuid = trim($input['user_uuid'] ?? '');
$lastSyncTimestamp = intval($input['last_sync_timestamp'] ?? 0);
$classes = $input['classes'] ?? [];
$students = $input['students'] ?? [];
$attendanceList = $input['attendance'] ?? [];

if (empty($userUuid)) {
    sendResponse(false, 'user_uuid is required for synchronization.', null, 400);
}

$currentServerTime = round(microtime(true) * 1000);

try {
    $pdo->beginTransaction();

    // 1. Process incoming Classes
    if (!empty($classes) && is_array($classes)) {
        $stmtClass = $pdo->prepare("
            INSERT INTO classes (uuid, user_uuid, class_name, section, subject, is_deleted, created_at, updated_at)
            VALUES (:uuid, :user_uuid, :class_name, :section, :subject, :is_deleted, :created_at, :updated_at)
            ON DUPLICATE KEY UPDATE
                class_name = VALUES(class_name),
                section = VALUES(section),
                subject = VALUES(subject),
                is_deleted = VALUES(is_deleted),
                updated_at = VALUES(updated_at)
        ");

        foreach ($classes as $cls) {
            if (empty($cls['uuid'])) continue;
            $stmtClass->execute([
                ':uuid' => $cls['uuid'],
                ':user_uuid' => $userUuid,
                ':class_name' => $cls['class_name'] ?? '',
                ':section' => $cls['section'] ?? '',
                ':subject' => $cls['subject'] ?? '',
                ':is_deleted' => !empty($cls['is_deleted']) ? 1 : 0,
                ':created_at' => intval($cls['created_at'] ?? $currentServerTime),
                ':updated_at' => intval($cls['updated_at'] ?? $currentServerTime)
            ]);
        }
    }

    // 2. Process incoming Students
    if (!empty($students) && is_array($students)) {
        $stmtStudent = $pdo->prepare("
            INSERT INTO students (uuid, class_uuid, user_uuid, roll_number, name, gender, phone, email, is_deleted, created_at, updated_at)
            VALUES (:uuid, :class_uuid, :user_uuid, :roll_number, :name, :gender, :phone, :email, :is_deleted, :created_at, :updated_at)
            ON DUPLICATE KEY UPDATE
                class_uuid = VALUES(class_uuid),
                roll_number = VALUES(roll_number),
                name = VALUES(name),
                gender = VALUES(gender),
                phone = VALUES(phone),
                email = VALUES(email),
                is_deleted = VALUES(is_deleted),
                updated_at = VALUES(updated_at)
        ");

        foreach ($students as $stu) {
            if (empty($stu['uuid'])) continue;
            $stmtStudent->execute([
                ':uuid' => $stu['uuid'],
                ':class_uuid' => $stu['class_uuid'] ?? '',
                ':user_uuid' => $userUuid,
                ':roll_number' => $stu['roll_number'] ?? '',
                ':name' => $stu['name'] ?? '',
                ':gender' => $stu['gender'] ?? 'Not Specified',
                ':phone' => $stu['phone'] ?? '',
                ':email' => $stu['email'] ?? '',
                ':is_deleted' => !empty($stu['is_deleted']) ? 1 : 0,
                ':created_at' => intval($stu['created_at'] ?? $currentServerTime),
                ':updated_at' => intval($stu['updated_at'] ?? $currentServerTime)
            ]);
        }
    }

    // 3. Process incoming Attendance records
    if (!empty($attendanceList) && is_array($attendanceList)) {
        $stmtAtt = $pdo->prepare("
            INSERT INTO attendance (uuid, user_uuid, class_uuid, student_uuid, date, status, remarks, is_deleted, created_at, updated_at)
            VALUES (:uuid, :user_uuid, :class_uuid, :student_uuid, :date, :status, :remarks, :is_deleted, :created_at, :updated_at)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                remarks = VALUES(remarks),
                is_deleted = VALUES(is_deleted),
                updated_at = VALUES(updated_at)
        ");

        foreach ($attendanceList as $att) {
            if (empty($att['uuid']) || empty($att['student_uuid']) || empty($att['date'])) continue;
            $validStatus = in_array(strtoupper($att['status'] ?? ''), ['PRESENT', 'ABSENT', 'LATE', 'EXCUSED'])
                ? strtoupper($att['status'])
                : 'PRESENT';

            $stmtAtt->execute([
                ':uuid' => $att['uuid'],
                ':user_uuid' => $userUuid,
                ':class_uuid' => $att['class_uuid'] ?? '',
                ':student_uuid' => $att['student_uuid'],
                ':date' => $att['date'],
                ':status' => $validStatus,
                ':remarks' => $att['remarks'] ?? '',
                ':is_deleted' => !empty($att['is_deleted']) ? 1 : 0,
                ':created_at' => intval($att['created_at'] ?? $currentServerTime),
                ':updated_at' => intval($att['updated_at'] ?? $currentServerTime)
            ]);
        }
    }

    $pdo->commit();

    // 4. Fetch server records updated since $lastSyncTimestamp to send back to client
    $fetchClasses = $pdo->prepare("
        SELECT uuid, user_uuid, class_name, section, subject, is_deleted, created_at, updated_at
        FROM classes
        WHERE user_uuid = ? AND updated_at > ?
    ");
    $fetchClasses->execute([$userUuid, $lastSyncTimestamp]);
    $serverClasses = $fetchClasses->fetchAll();

    $fetchStudents = $pdo->prepare("
        SELECT uuid, class_uuid, user_uuid, roll_number, name, gender, phone, email, is_deleted, created_at, updated_at
        FROM students
        WHERE user_uuid = ? AND updated_at > ?
    ");
    $fetchStudents->execute([$userUuid, $lastSyncTimestamp]);
    $serverStudents = $fetchStudents->fetchAll();

    $fetchAtt = $pdo->prepare("
        SELECT uuid, user_uuid, class_uuid, student_uuid, date, status, remarks, is_deleted, created_at, updated_at
        FROM attendance
        WHERE user_uuid = ? AND updated_at > ?
    ");
    $fetchAtt->execute([$userUuid, $lastSyncTimestamp]);
    $serverAtt = $fetchAtt->fetchAll();

    sendResponse(true, 'Sync completed successfully.', [
        'server_timestamp' => $currentServerTime,
        'classes' => $serverClasses,
        'students' => $serverStudents,
        'attendance' => $serverAtt
    ]);

} catch (Exception $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    sendResponse(false, 'Synchronization failed: ' . $e->getMessage(), null, 500);
}
