package com.afsh.messages.testdata

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony.Sms
import android.provider.Telephony.Threads
import java.util.ArrayList
import java.util.Random
import kotlin.math.max

/**
 * Deterministic synthetic Persian SMS corpus for cold-start / scroll benchmarks.
 * Inserts through the Telephony SMS provider (requires the default SMS role).
 * Bodies are project-authored templates — not from PSSC, UCI, or any private inbox.
 */
object SyntheticInboxSeeder {

    const val DEFAULT_SEED = 42L
    const val TARGET_THREADS = 2_000
    const val TARGET_MESSAGES = 25_000
    private const val BATCH_SIZE = 100

    data class Result(
        val threads: Int,
        val messages: Int,
        val unread: Int,
        val elapsedMs: Long,
    )

    fun seed(
        context: Context,
        seed: Long = DEFAULT_SEED,
        threadCount: Int = TARGET_THREADS,
        messageCount: Int = TARGET_MESSAGES,
    ): Result {
        require(threadCount > 0 && messageCount >= threadCount) {
            "Need at least one message per thread (threads=$threadCount messages=$messageCount)"
        }
        val started = System.nanoTime()
        val rng = Random(seed)
        val shapes = messageShapes(threadCount, messageCount, rng)
        var inserted = 0
        var unread = 0
        val now = FIXED_NOW_MS
        val span = 18L * 30L * 24L * 60L * 60L * 1000L
        val ops = ArrayList<ContentProviderOperation>(BATCH_SIZE)

        fun flush() {
            if (ops.isEmpty()) return
            val results = try {
                context.contentResolver.applyBatch(Sms.CONTENT_URI.authority!!, ops)
            } catch (e: Exception) {
                throw IllegalStateException("Telephony applyBatch failed: ${e.message}", e)
            }
            check(results.size == ops.size) {
                "applyBatch returned ${results.size} results for ${ops.size} ops"
            }
            results.forEach { result ->
                check(result.uri != null) { "applyBatch row returned null uri" }
            }
            ops.clear()
        }

        shapes.forEachIndexed { threadIndex, messagesInThread ->
            val sender = senderFor(threadIndex, rng)
            val threadId = threadIdOrThrow(context, sender)
            val baseDate = now - rng.nextInt(span.toInt().coerceAtLeast(1)).toLong()
            for (i in 0 until messagesInThread) {
                val kind = kindFor(threadIndex, i, messagesInThread, rng)
                val body = bodyFor(kind, threadIndex, i, rng)
                val read = if (rng.nextDouble() < 0.12) 0 else 1
                if (read == 0) unread++
                val date = baseDate + i * (30_000L + rng.nextInt(120_000))
                val values = ContentValues().apply {
                    put(Sms.ADDRESS, sender)
                    put(Sms.SUBJECT, "")
                    put(Sms.BODY, body)
                    put(Sms.DATE, date.coerceAtMost(now))
                    put(Sms.READ, read)
                    put(Sms.THREAD_ID, threadId)
                    put(
                        Sms.TYPE,
                        if (kind == Kind.CHAT && rng.nextBoolean()) {
                            Sms.MESSAGE_TYPE_SENT
                        } else {
                            Sms.MESSAGE_TYPE_INBOX
                        },
                    )
                    put(Sms.SUBSCRIPTION_ID, -1)
                }
                ops += ContentProviderOperation.newInsert(Sms.CONTENT_URI).withValues(values).build()
                inserted++
                if (ops.size >= BATCH_SIZE) flush()
            }
        }
        flush()

        check(inserted == messageCount) {
            "Inserted $inserted messages, expected $messageCount"
        }
        return Result(
            threads = threadCount,
            messages = inserted,
            unread = unread,
            elapsedMs = (System.nanoTime() - started) / 1_000_000L,
        )
    }

    private fun threadIdOrThrow(context: Context, address: String): Long {
        val id = try {
            Threads.getOrCreateThreadId(context, address)
        } catch (e: Exception) {
            throw IllegalStateException("getOrCreateThreadId failed for $address: ${e.message}", e)
        }
        check(id > 0L) { "getOrCreateThreadId returned $id for $address" }
        return id
    }

    /**
     * Many short threads, some medium, a few very long (200–800), summing to [messageCount].
     */
    private fun messageShapes(threadCount: Int, messageCount: Int, rng: Random): IntArray {
        val shapes = IntArray(threadCount) { 1 }
        var remaining = messageCount - threadCount
        val longCount = max(8, threadCount / 200)
        val mediumCount = max(40, threadCount / 20)
        for (i in 0 until longCount) {
            val extra = 199 + rng.nextInt(601) // 200..800 total
            val add = minOf(extra - 1, remaining)
            shapes[i] += add
            remaining -= add
        }
        for (i in longCount until longCount + mediumCount) {
            val extra = 9 + rng.nextInt(41) // 10..50 total
            val add = minOf(extra - 1, remaining)
            shapes[i] += add
            remaining -= add
        }
        var i = longCount + mediumCount
        while (remaining > 0) {
            val add = minOf(1 + rng.nextInt(3), remaining)
            shapes[i % threadCount] += add
            remaining -= add
            i++
        }
        return shapes
    }

    private fun senderFor(threadIndex: Int, rng: Random): String = when (threadIndex % 17) {
        0 -> bankSenders[threadIndex % bankSenders.size]
        1 -> otpSenders[threadIndex % otpSenders.size]
        2 -> spamSenders[threadIndex % spamSenders.size]
        3 -> "*8${100 + (threadIndex % 90)}"
        4 -> "${1000 + (threadIndex % 8000)}"
        else -> {
            // Iranian-looking MSISDN; deterministic from index, not a real subscriber.
            val n = 900_000_0000L + (threadIndex * 7_919L + rng.nextInt(97)) % 99_999_999L
            "0$n"
        }
    }

    private enum class Kind { OTP, BANK, CHAT, SPAM, LONG, MIXED }

    private fun kindFor(threadIndex: Int, messageIndex: Int, threadSize: Int, rng: Random): Kind {
        if (threadSize >= 200) return if (messageIndex % 11 == 0) Kind.MIXED else Kind.LONG
        return when (threadIndex % 17) {
            0 -> Kind.BANK
            1 -> Kind.OTP
            2 -> Kind.SPAM
            3, 4 -> if (rng.nextBoolean()) Kind.SPAM else Kind.CHAT
            else -> Kind.CHAT
        }
    }

    private fun bodyFor(kind: Kind, threadIndex: Int, messageIndex: Int, rng: Random): String {
        val n = (threadIndex * 31 + messageIndex * 17 + rng.nextInt(1_000)) % 1_000_000
        val code = String.format("%06d", n % 1_000_000)
        return when (kind) {
            Kind.OTP -> otpTemplates[messageIndex % otpTemplates.size]
                .replace("{CODE}", code)
            Kind.BANK -> bankTemplates[messageIndex % bankTemplates.size]
                .replace("{AMOUNT}", "%,d".format(50_000 + n * 37))
                .replace("{BALANCE}", "%,d".format(1_000_000 + n * 91))
            Kind.SPAM -> spamTemplates[messageIndex % spamTemplates.size]
                .replace("{N}", n.toString())
            Kind.LONG -> buildString {
                append(longOpeners[messageIndex % longOpeners.size])
                append(' ')
                repeat(3 + messageIndex % 5) {
                    append(chatLines[(messageIndex + it) % chatLines.size])
                    append(' ')
                }
                if (messageIndex % 7 == 0) append("🎉🍕")
            }
            Kind.MIXED -> {
                val mixed = listOf(
                    "ok ${n}",
                    "باشه، رسیدم",
                    "See you at ${n % 12}:${n % 60}",
                    "لطفاً فایل را بفرست 📎",
                    "Thanks! مرسی",
                )
                mixed[messageIndex % mixed.size]
            }
            Kind.CHAT -> chatLines[messageIndex % chatLines.size]
                .replace("{N}", (n % 100).toString())
        }
    }

    // Fixed "now" so date spreads stay reproducible across runs.
    private const val FIXED_NOW_MS = 1763337600000L // 2025-11-17T00:00:00Z

    private val bankSenders = listOf(
        "AFSHBANK", "BANKMELLAT", "BANKSaderat", "1800", "9035", "AYANDEH",
    )
    private val otpSenders = listOf(
        "AFSHOTP", "BANK", "DIGIKALA", "SNAPP", "GOVIR", "MYIRANCELL",
    )
    private val spamSenders = listOf(
        "AFSHPROMO", "TAKHFIF", "JAYEZE", "HAMRAH_DEAL", "SHOPIR", "*8000",
    )

    private val otpTemplates = listOf(
        "کد تایید شما: {CODE}\nبانک ملت\nلغو11",
        "رمز یک‌بار مصرف: {CODE}\nلغو ۱۱",
        "کد ورود شما {CODE} است. هرگز آن را به اشتراک نگذارید.\nلغو11",
        "Your verification code is {CODE}\nلغو11",
        "کد امنیتی: {CODE}\nلغو 11",
    )

    private val bankTemplates = listOf(
        "برداشت از کارت *1234 به مبلغ {AMOUNT} ریال\nموجودی: {BALANCE}\nلغو11",
        "واریز {AMOUNT} ریال به حساب شما انجام شد. موجودی {BALANCE} ریال\nلغو ۱۱",
        "رسید پرداخت موفق\nتراکنش {AMOUNT}\nمبلغ {AMOUNT} ریال\nلغو11",
        "تراکنش خرید به مبلغ {AMOUNT} ریال\nموجودی {BALANCE}\nلغو11",
    )

    private val spamTemplates = listOf(
        "جشنواره تخفیف ویژه آغاز شد 🎉 کد {N} https://promo.example.ir/deal",
        "فرصت محدود · بسته ویژه · همین حالا بخر 🔥",
        "شما برنده جایزه شدید 🎁 www.gift.example.ir/win",
        "گوشی مجانی برای شما — ثبت‌نام کنید https://win.example.ir/{N}",
        "حراج بزرگ فروش ویژه فقط امروز 🍕💰",
        "قرعه‌کشی بزرگ؛ شانس برنده شدن شما 🎊",
        "کد تخفیف {N} را همین حالا استفاده کنید",
    )

    private val chatLines = listOf(
        "سلام، خوبی؟",
        "بله، ساعت {N} می‌رسم",
        "مرسی 🌹",
        "پس فردا هماهنگ می‌کنیم",
        "فایل را فرستادم",
        "لطفاً تأیید کن",
        "ок, got it",
        "آدرس را بفرست",
        "الان می‌خونم",
        "فردا زنگ بزن",
        "یادآوری: قبض برق",
        "کد پستی را چک کردم",
    )

    private val longOpeners = listOf(
        "گزارش روزانه:",
        "خلاصه جلسه:",
        "یادداشت:",
        "پیگیری سفارش:",
    )
}
