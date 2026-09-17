<?php
/**
 * Reports API Endpoint
 * Provides aggregated statistics for:
 * 1. Class Report (?type=class&class_uuid=...&from_date=...&to_date=...)
 * 2. Student Report (?type=student&student_uuid=...&from_date=...&to_date=...)
 */

require_once __DIR__ . '/../db.php';

$type = $_GET['type'] ?? 'class';
$classUuid = $_GET['class_uuid'] ?? '';
$studentUuid = $_GET['student_uuid'] ?? '';
$fromDate = $_GET['from_date'] ?? '2000-01-01';
$toDate = $_GET['to_date'] ?? date('Y-m-d');

try {
    if ($type === 'student') {
        if (empty($studentUuid)) {
            sendResponse(false, 'student_uuid is required for student report.', null, 400);
        }

        // Student details
        $stuStmt = $pdo->prepare("
            SELECT s.*, c.class_name, c.section
            FROM students s
            LEFT JOIN classes c ON s.class_uuid = c.uuid
            WHERE s.uuid = ? AND s.is_deleted = 0
            LIMIT 1
        ");
        $stuStmt->execute([$studentUuid]);
        $student = $stuStmt->fetch();

        if (!$student) {
            sendResponse(false, 'Student not found.', null, 404);
        }

        // Attendance records
        $attStmt = $pdo->prepare("
            SELECT date, status, remarks
            FROM attendance
            WHERE student_uuid = ? AND date BETWEEN ? AND ? AND is_deleted = 0
            ORDER BY date DESC
        ");
        $attStmt->execute([$studentUuid, $fromDate, $toDate]);
        $records = $attStmt->fetchAll();

        // Summary counts
        $totalDays = count($records);
        $presentCount = 0;
        $absentCount = 0;
        $lateCount = 0;
        $excusedCount = 0;

        foreach ($records as $r) {
            if ($r['status'] === 'PRESENT') $presentCount++;
            else if ($r['status'] === 'ABSENT') $absentCount++;
            else if ($r['status'] === 'LATE') $lateCount++;
            else if ($r['status'] === 'EXCUSED') $excusedCount++;
        }

        $percentage = $totalDays > 0 ? round((($presentCount + ($lateCount * 0.5)) / $totalDays) * 100, 1) : 0;

        sendResponse(true, 'Student report fetched successfully.', [
            'student' => $student,
            'summary' => [
                'total_days' => $totalDays,
                'present' => $presentCount,
                'absent' => $absentCount,
                'late' => $lateCount,
                'excused' => $excusedCount,
                'percentage' => $percentage
            ],
            'records' => $records
        ]);

    } else {
        // Class report
        if (empty($classUuid)) {
            sendResponse(false, 'class_uuid is required for class report.', null, 400);
        }

        $classStmt = $pdo->prepare("SELECT * FROM classes WHERE uuid = ? AND is_deleted = 0 LIMIT 1");
        $classStmt->execute([$classUuid]);
        $classInfo = $classStmt->fetch();

        if (!$classInfo) {
            sendResponse(false, 'Class not found.', null, 404);
        }

        // Students in this class
        $studentsStmt = $pdo->prepare("
            SELECT uuid, roll_number, name, gender, phone
            FROM students
            WHERE class_uuid = ? AND is_deleted = 0
            ORDER BY CAST(roll_number AS UNSIGNED) ASC, roll_number ASC
        ");
        $studentsStmt->execute([$classUuid]);
        $students = $studentsStmt->fetchAll();

        // Attendance stats per student in range
        $statsStmt = $pdo->prepare("
            SELECT
                student_uuid,
                COUNT(*) as total_days,
                SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END) as present_count,
                SUM(CASE WHEN status = 'ABSENT' THEN 1 ELSE 0 END) as absent_count,
                SUM(CASE WHEN status = 'LATE' THEN 1 ELSE 0 END) as late_count,
                SUM(CASE WHEN status = 'EXCUSED' THEN 1 ELSE 0 END) as excused_count
            FROM attendance
            WHERE class_uuid = ? AND date BETWEEN ? AND ? AND is_deleted = 0
            GROUP BY student_uuid
        ");
        $statsStmt->execute([$classUuid, $fromDate, $toDate]);
        $statsMap = [];
        while ($row = $statsStmt->fetch()) {
            $statsMap[$row['student_uuid']] = $row;
        }

        $studentSummaries = [];
        $totalClassPresent = 0;
        $totalClassPossible = 0;

        foreach ($students as $stu) {
            $sData = $statsMap[$stu['uuid']] ?? [
                'total_days' => 0,
                'present_count' => 0,
                'absent_count' => 0,
                'late_count' => 0,
                'excused_count' => 0
            ];
            $tDays = intval($sData['total_days']);
            $pCount = intval($sData['present_count']);
            $pct = $tDays > 0 ? round(($pCount / $tDays) * 100, 1) : 0;

            $totalClassPresent += $pCount;
            $totalClassPossible += $tDays;

            $studentSummaries[] = [
                'student_uuid' => $stu['uuid'],
                'roll_number' => $stu['roll_number'],
                'name' => $stu['name'],
                'total_days' => $tDays,
                'present' => $pCount,
                'absent' => intval($sData['absent_count']),
                'late' => intval($sData['late_count']),
                'excused' => intval($sData['excused_count']),
                'percentage' => $pct
            ];
        }

        $overallPct = $totalClassPossible > 0 ? round(($totalClassPresent / $totalClassPossible) * 100, 1) : 0;

        sendResponse(true, 'Class report fetched successfully.', [
            'class' => $classInfo,
            'period' => [
                'from_date' => $fromDate,
                'to_date' => $toDate
            ],
            'total_students' => count($students),
            'overall_attendance_percentage' => $overallPct,
            'students' => $studentSummaries
        ]);
    }
} catch (PDOException $e) {
    sendResponse(false, 'Database error: ' . $e->getMessage(), null, 500);
}
