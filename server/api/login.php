<?php
/**
 * User Login API Endpoint
 * Method: POST
 * Body: { "email": "...", "password": "..." }
 */

require_once __DIR__ . '/../db.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(false, 'Method not allowed. Use POST.', null, 405);
}

$input = getJsonInput();

$email = strtolower(trim($input['email'] ?? ''));
$password = trim($input['password'] ?? '');

if (empty($email) || empty($password)) {
    sendResponse(false, 'Email and password are required.', null, 400);
}

try {
    $stmt = $pdo->prepare("SELECT id, uuid, name, email, password_hash, institution FROM users WHERE email = ? LIMIT 1");
    $stmt->execute([$email]);
    $user = $stmt->fetch();

    if (!$user || !password_verify($password, $user['password_hash'])) {
        sendResponse(false, 'Invalid email or password. Please try again.', null, 401);
    }

    $token = base64_encode($user['uuid'] . ':' . time() . ':' . bin2hex(random_bytes(8)));

    sendResponse(true, 'Login successful!', [
        'user' => [
            'uuid' => $user['uuid'],
            'name' => $user['name'],
            'email' => $user['email'],
            'institution' => $user['institution'],
            'token' => $token
        ]
    ]);

} catch (PDOException $e) {
    sendResponse(false, 'Database error during login: ' . $e->getMessage(), null, 500);
}
