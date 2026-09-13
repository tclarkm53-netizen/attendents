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

/**
 * Available draggable & reorderable widgets in the report card design
 */
enum class ReportWidgetType(
    val id: String,
    val titleBn: String,
    val subtitleBn: String,
    val defaultEnabled: Boolean = true
) {
    HEADER(
        id = "HEADER",
        titleBn = "প্রতিষ্ঠানের হেডার ও মনোগ্রাম",
        subtitleBn = "প্রতিষ্ঠানের নাম, মূল শিরোনাম ও উপ-শিরোনাম"
    ),
    STUDENT_INFO(
        id = "STUDENT_INFO",
        titleBn = "শিক্ষার্থীর পরিচিতি বক্স",
        subtitleBn = "নাম, রোল, শ্রেণি, শাখা ও অভিভাবক মোবাইল"
    ),
    ATTENDANCE_METRICS(
        id = "ATTENDANCE_METRICS",
        titleBn = "মূল উপস্থিতি পরিসংখ্যান গ্রিড",
        subtitleBn = "মোট ক্লাস, উপস্থিতি, অনুপস্থিতি ও বিলম্বের হাইলাইটস"
    ),
    PERCENTAGE_BADGE(
        id = "PERCENTAGE_BADGE",
        titleBn = "শতকরা হার ও পারফর্মেন্স রেটিং",
        subtitleBn = "উপস্থিতির শতকরা হার (%) এবং এক্সেলেন্ট/গুড রেটিং ব্যাজ"
    ),
    ATTENDANCE_TABLE(
        id = "ATTENDANCE_TABLE",
        titleBn = "দৈনিক হাজিরা বিস্তারিত তালিকা",
        subtitleBn = "তারিখভিত্তিক স্ট্যাটাস ও নোটের সংক্ষিপ্ত চার্ট"
    ),
    TEACHER_REMARKS(
        id = "TEACHER_REMARKS",
        titleBn = "শিক্ষক ও অধ্যক্ষের মূল্যায়ন মন্তব্য",
        subtitleBn = "অভিভাবকের উদ্দেশে শিক্ষক ও অধ্যক্ষের মূল্যায়ন নোটিশ"
    ),
    SIGNATURES(
        id = "SIGNATURES",
        titleBn = "স্বাক্ষর ব্লক (Signatures)",
        subtitleBn = "অভিভাবক, শ্রেণি শিক্ষক ও প্রধান শিক্ষকের স্বাক্ষর লাইন"
    ),
    FOOTER_INFO(
        id = "FOOTER_INFO",
        titleBn = "ফুটার সিল ও ভেরিফিকেশন কোড",
        subtitleBn = "অফিসিয়াল সিল, জেনারেটেড তারিখ ও সিকিউরিটি ভেরিফিকেশন"
    )
}

val DEFAULT_WIDGET_ORDER = listOf(
    ReportWidgetType.HEADER,
    ReportWidgetType.STUDENT_INFO,
    ReportWidgetType.ATTENDANCE_METRICS,
    ReportWidgetType.PERCENTAGE_BADGE,
    ReportWidgetType.ATTENDANCE_TABLE,
    ReportWidgetType.TEACHER_REMARKS,
    ReportWidgetType.SIGNATURES,
    ReportWidgetType.FOOTER_INFO
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
    val showAttendanceTable: Boolean = true,
    // Drag & drop / reordering support
    val widgetOrder: List<ReportWidgetType> = DEFAULT_WIDGET_ORDER,
    val enabledWidgets: Set<ReportWidgetType> = ReportWidgetType.values().toSet(),
    val headerAlignment: String = "CENTER" // "CENTER", "LEFT"
) {
    fun isWidgetEnabled(type: ReportWidgetType): Boolean {
        return enabledWidgets.contains(type)
    }

    fun moveWidgetUp(type: ReportWidgetType): ReportCardDesignConfig {
        val index = widgetOrder.indexOf(type)
        if (index <= 0) return this
        val newOrder = widgetOrder.toMutableList()
        val temp = newOrder[index - 1]
        newOrder[index - 1] = newOrder[index]
        newOrder[index] = temp
        return this.copy(widgetOrder = newOrder)
    }

    fun moveWidgetDown(type: ReportWidgetType): ReportCardDesignConfig {
        val index = widgetOrder.indexOf(type)
        if (index < 0 || index >= widgetOrder.size - 1) return this
        val newOrder = widgetOrder.toMutableList()
        val temp = newOrder[index + 1]
        newOrder[index + 1] = newOrder[index]
        newOrder[index] = temp
        return this.copy(widgetOrder = newOrder)
    }

    fun toggleWidget(type: ReportWidgetType, enabled: Boolean): ReportCardDesignConfig {
        val newSet = enabledWidgets.toMutableSet()
        if (enabled) {
            newSet.add(type)
        } else {
            newSet.remove(type)
        }
        return this.copy(
            enabledWidgets = newSet,
            showSignatures = if (type == ReportWidgetType.SIGNATURES) enabled else showSignatures,
            showPercentages = if (type == ReportWidgetType.PERCENTAGE_BADGE) enabled else showPercentages,
            showAttendanceTable = if (type == ReportWidgetType.ATTENDANCE_TABLE) enabled else showAttendanceTable
        )
    }
}

