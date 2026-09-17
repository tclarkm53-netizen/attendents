package com.example.data.model

data class ReportCardTheme(
    val id: String,
    val name: String,
    val primaryColorArgb: Int,
    val secondaryColorArgb: Int,
    val badgeColorArgb: Int
)

val REPORT_THEMES = listOf(
    ReportCardTheme(
        id = "ROYAL_BLUE",
        name = "রাজকীয় নীল (Royal Blue)",
        primaryColorArgb = android.graphics.Color.rgb(13, 71, 161),
        secondaryColorArgb = android.graphics.Color.rgb(227, 242, 253),
        badgeColorArgb = android.graphics.Color.rgb(21, 101, 192)
    ),
    ReportCardTheme(
        id = "EMERALD_GREEN",
        name = "পান্না সবুজ (Emerald Green)",
        primaryColorArgb = android.graphics.Color.rgb(27, 94, 32),
        secondaryColorArgb = android.graphics.Color.rgb(232, 245, 233),
        badgeColorArgb = android.graphics.Color.rgb(46, 125, 50)
    ),
    ReportCardTheme(
        id = "CRIMSON_RUBY",
        name = "রক্তিম আভিজাত্য (Crimson Ruby)",
        primaryColorArgb = android.graphics.Color.rgb(136, 14, 79),
        secondaryColorArgb = android.graphics.Color.rgb(252, 228, 236),
        badgeColorArgb = android.graphics.Color.rgb(173, 20, 87)
    ),
    ReportCardTheme(
        id = "SLATE_CHARCOAL",
        name = "মডার্ন স্লেট (Modern Slate)",
        primaryColorArgb = android.graphics.Color.rgb(38, 50, 56),
        secondaryColorArgb = android.graphics.Color.rgb(236, 239, 241),
        badgeColorArgb = android.graphics.Color.rgb(55, 71, 79)
    ),
    ReportCardTheme(
        id = "GOLDEN_AMBER",
        name = "গোল্ডেন ক্লাসিক (Golden Classic)",
        primaryColorArgb = android.graphics.Color.rgb(230, 81, 0),
        secondaryColorArgb = android.graphics.Color.rgb(255, 248, 225),
        badgeColorArgb = android.graphics.Color.rgb(239, 108, 0)
    )
)

data class ReportCardDesignConfig(
    val customInstitution: String = "",
    val customReportTitle: String = "শিক্ষার্থী উপস্থিতি ও মূল্যায়ন রিপোর্ট কার্ড",
    val customSubtitle: String = "মাসিক মূল্যায়ন বিবরণী",
    val customRemarks: String = "উপস্থিতির হার সন্তোষজনক। নিয়মিত অধ্যবসায় কাম্য।",
    val theme: ReportCardTheme = REPORT_THEMES[0],
    val borderStyle: String = "DOUBLE", // "DOUBLE", "ROUNDED", "MINIMAL"
    val showSignatures: Boolean = true,
    val showPercentages: Boolean = true,
    val showGuardianPhone: Boolean = true,
    val showAttendanceTable: Boolean = true
)
