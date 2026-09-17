<?php
/**
 * User Registration API Endpoint
 * Method: POST
 * Body: { "uuid": "optional-uuid", "name": "...", "email": "...", "password": "...", "institution": "..." }
 */

require_once __DIR__ . '/../db.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(false, 'Method not allowed. Use POST.', null, 405);
}

$input = getJsonInput();

$name = trim($input['name'] ?? '');
$email = strtolower(trim($input['email'] ?? ''));
$password = trim($input['password'] ?? '');
$institution = trim($input['institution'] ?? '');
$uuid = trim($input['uuid'] ?? '');

if (empty($name) || empty($email) || empty($password)) {
    sendResponse(false, 'Name, email, and password are required.', null, 400);
}

if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    sendResponse(false, 'Invalid email format.', null, 400);
}

if (strlen($password) < 6) {
    sendResponse(false, 'Password must be at least 6 characters long.', null, 400);
}

if (empty($uuid)) {
    $uuid = bin2hex(random_bytes(16));
}

try {
    // Check if email already registered
    $checkStmt = $pdo->prepare("SELECT id, uuid, name, email, institution FROM users WHERE email = ? LIMIT 1");
    $checkStmt->execute([$email]);
    $existing = $checkStmt->fetch();

    if ($existing) {
        sendResponse(false, 'An account with this email already exists. Please login.', null, 409);
    }

    $passwordHash = password_hash($password, PASSWORD_BCRYPT);

    $insertStmt = $pdo->prepare("
        INSERT INTO users (uuid, name, email, password_hash, institution)
        VALUES (?, ?, ?, ?, ?)
    ");
    $insertStmt->execute([$uuid, $name, $email, $passwordHash, $institution]);

    // Simple auth token generator
    $token = base64_encode($uuid . ':' . time() . ':' . bin2hex(random_bytes(8)));

    sendResponse(true, 'Registration successful!', [
        'user' => [
            'uuid' => $uuid,
            'name' => $name,
            'email' => $email,
            'institution' => $institution,
            'token' => $token
        ]
    ], 201);

} catch (PDOException $e) {
    sendResponse(false, 'Database error during registration: ' . $e->getMessage(), null, 500);
}
