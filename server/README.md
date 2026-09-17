# Student Attendance System - Server (PHP + MySQL)

এই ফোল্ডারে স্টুডেন্ট অ্যাটেন্ডেন্স সিস্টেমের সম্পূর্ণ ব্যাকএন্ড সার্ভার কোড (PHP & MySQL) রয়েছে।

---

## 📁 ফাইল স্ট্রাকচার (File Structure)

```
/server
  ├── schema.sql           # MySQL ডাটাবেস টেবিল স্ট্রাকচার (Users, Classes, Students, Attendance)
  ├── db.php               # PDO ডাটাবেস সংযোগ এবং JSON রেসপন্স হেল্পার
  ├── README.md            # ইন্সটলেশন ও কনফিগারেশন নির্দেশিকা
  └── api/
      ├── register.php     # ইউজার রেজিষ্ট্রেশন API (POST)
      ├── login.php        # ইউজার লগইন API (POST)
      ├── sync.php         # বাইডিরেকশনাল সিংক API (Offline-to-Online Zero Data Loss) (POST)
      └── report.php       # ক্লাস ও স্টুডেন্ট রিপোর্ট API (GET)
```

---

## 🚀 সেটআপ ও রান করার নিয়ম (How to Setup)

### পদ্ধতি ১: XAMPP / WampServer / LAMP দিয়ে লোকালহোস্টে চালানো
1. **ডাটাবেস তৈরি করুন:**
   - ব্রাউজারে `http://localhost/phpmyadmin` খুলুন।
   - SQL ট্যাবে গিয়ে `schema.sql` ফাইলের সমস্ত কোড পেস্ট করে **Go / Execute** বাটনে ক্লিক করুন।
   - এটি `attendance_db` নামের ডাটাবেস এবং প্রয়োজনীয় সব টেবিল তৈরি করবে।

2. **সার্ভার কোড রাখুন:**
   - XAMPP হলে: `C:\xampp\htdocs\server` ফোল্ডারে এই ফাইলগুলো কপি করুন।
   - WAMP হলে: `C:\wamp64\www\server` ফোল্ডারে কপি করুন।
   - Apache এবং MySQL স্টার্ট করুন।

3. **কনফিগারেশন (`db.php`):**
   ডিফল্ট ইউজার `root`, পাসওয়ার্ড ফাঁকা (`''`), পোর্ট `3306`। প্রয়োজনে `db.php` তে পরিবর্তন করতে পারেন:
   ```php
   $db_host = 'localhost';
   $db_name = 'attendance_db';
   $db_user = 'root';
   $db_pass = '';
   ```

---

## 📱 অ্যান্ড্রয়েড অ্যাপের সাথে কানেক্ট করার নিয়ম

অ্যান্ড্রয়েড অ্যাপের ভিতরে **Settings** পেজে গিয়ে যে কোনো সময় সার্ভার URL পরিবর্তন করা যায়।
- **Android Studio Emulator ব্যবহার করলে:** `http://10.0.2.2/server/api`
- **একই ওয়াইফাই/লোকাল নেটওয়ার্কের রিয়েল ফোন ব্যবহার করলে:** `http://<আপনার_কম্পিউটারের_IP_ঠিকানা>/server/api` (যেমন: `http://192.168.1.5/server/api`)
- **অনলাইন হোস্টিং/VPS ব্যবহার করলে:** `https://yourdomain.com/server/api`

---

## 🔄 ডুয়েল-সিংক আর্কিটেকচার (How Sync Prevents Missing/Duplicate Data)
1. প্রতিটি ক্লাস, স্টুডেন্ট ও অ্যাটেন্ডেন্সের জন্য ক্লায়েন্ট-সাইডে একটি ইউনিক `UUID` তৈরি করা হয়।
2. সার্ভার `UNIQUE KEY` এবং MySQL `ON DUPLICATE KEY UPDATE` ব্যবহার করে।
3. ফলে একই ডাটা বার বার পাঠালেও কোনো ডুব্লিকেট এন্ট্রি তৈরি হবে না।
4. অফলাইনে কাজ করে নেটওয়ার্কে আসা মাত্র অ্যাপ স্বয়ংক্রিয়ভাবে সিংক করে এবং সার্ভারের নতুন ডেটাও অ্যাপে আপডেট করে নেয়।
