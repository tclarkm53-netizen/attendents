-- Student Attendance System Database Schema
-- Compatible with MySQL 5.7+ / MariaDB 10.2+

CREATE DATABASE IF NOT EXISTS `attendance_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `attendance_db`;

-- Users / Teachers Table
CREATE TABLE IF NOT EXISTS `users` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `uuid` VARCHAR(64) NOT NULL UNIQUE,
    `name` VARCHAR(100) NOT NULL,
    `email` VARCHAR(120) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `institution` VARCHAR(150) DEFAULT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Classes Table
CREATE TABLE IF NOT EXISTS `classes` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `uuid` VARCHAR(64) NOT NULL UNIQUE,
    `user_uuid` VARCHAR(64) NOT NULL,
    `class_name` VARCHAR(100) NOT NULL,
    `section` VARCHAR(50) DEFAULT '',
    `subject` VARCHAR(100) DEFAULT '',
    `is_deleted` TINYINT(1) DEFAULT 0,
    `created_at` BIGINT NOT NULL,
    `updated_at` BIGINT NOT NULL,
    INDEX `idx_classes_user` (`user_uuid`),
    INDEX `idx_classes_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Students Table
CREATE TABLE IF NOT EXISTS `students` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `uuid` VARCHAR(64) NOT NULL UNIQUE,
    `class_uuid` VARCHAR(64) NOT NULL,
    `user_uuid` VARCHAR(64) NOT NULL,
    `roll_number` VARCHAR(50) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `gender` VARCHAR(20) DEFAULT 'Not Specified',
    `phone` VARCHAR(30) DEFAULT '',
    `email` VARCHAR(100) DEFAULT '',
    `is_deleted` TINYINT(1) DEFAULT 0,
    `created_at` BIGINT NOT NULL,
    `updated_at` BIGINT NOT NULL,
    INDEX `idx_students_class` (`class_uuid`),
    INDEX `idx_students_user` (`user_uuid`),
    INDEX `idx_students_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Attendance Table
CREATE TABLE IF NOT EXISTS `attendance` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `uuid` VARCHAR(64) NOT NULL UNIQUE,
    `user_uuid` VARCHAR(64) NOT NULL,
    `class_uuid` VARCHAR(64) NOT NULL,
    `student_uuid` VARCHAR(64) NOT NULL,
    `date` VARCHAR(20) NOT NULL, -- Format: YYYY-MM-DD
    `status` ENUM('PRESENT', 'ABSENT', 'LATE', 'EXCUSED') DEFAULT 'PRESENT',
    `remarks` VARCHAR(255) DEFAULT '',
    `is_deleted` TINYINT(1) DEFAULT 0,
    `created_at` BIGINT NOT NULL,
    `updated_at` BIGINT NOT NULL,
    UNIQUE KEY `unique_student_date` (`student_uuid`, `date`),
    INDEX `idx_attendance_class_date` (`class_uuid`, `date`),
    INDEX `idx_attendance_user` (`user_uuid`),
    INDEX `idx_attendance_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
