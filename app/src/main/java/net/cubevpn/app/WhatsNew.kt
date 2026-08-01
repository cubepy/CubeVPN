package net.cubevpn.app

/**
 * A short, hand-written "what's new" highlight list per release, keyed by the versionCode it
 * shipped in (see build.gradle.kts's versionCodeFromName — major*1_000_000 + minor*1_000 + patch).
 * Shown once, the first time the app is opened after updating past that versionCode.
 *
 * Add one entry per release worth announcing. Skipping a release (or several) is fine — a user
 * who updates from a much older version sees every entry they missed, newest first.
 */
data class ChangelogEntry(val versionCode: Int, val en: List<String>, val fa: List<String>)

object WhatsNew {

    val entries = listOf(
        ChangelogEntry(
            versionCode = 1_002_003, // v1.2.3
            en = listOf(
                "Two new accent colors — Aurora and Ember — pick one in Settings > Theme",
                "A more vivid, multi-color home screen",
                "A home-screen widget to connect or disconnect without opening the app",
                "Auto-reconnect after an unexpected drop (opt-in, in Settings)",
                "Updates now download and install from inside the app, with a notification when one's available",
                "A TON (Gram) wallet option under Support Us"
            ),
            fa = listOf(
                "دو رنگ جدید — آبی اقیانوسی و نارنجی غروب — از تنظیمات > پوسته انتخاب کنید",
                "صفحه‌ی اصلی رنگارنگ‌تر و چشم‌نوازتر",
                "ویجت صفحه‌ی هوم برای وصل یا قطع کردن بدون باز کردن اپ",
                "اتصال مجدد خودکار بعد از قطعی ناگهانی (اختیاری، در تنظیمات)",
                "بروزرسانی‌ها حالا از داخل خود اپ دانلود و نصب می‌شن، به‌همراه اعلان وقتی نسخه‌ی جدید موجوده",
                "اضافه شدن کیف‌پول TON (گرم) به بخش حمایت از ما"
            )
        )
    )

    /** Entries introduced after [lastSeenCode] and no later than [currentCode], oldest first. */
    fun pending(lastSeenCode: Int, currentCode: Int): List<ChangelogEntry> =
        entries.filter { it.versionCode in (lastSeenCode + 1)..currentCode }
}
