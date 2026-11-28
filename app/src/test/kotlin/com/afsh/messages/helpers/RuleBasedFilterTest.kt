package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persian marketing filter scoring through the public [RuleBasedFilter.explain] seam.
 * Samples mirror real bank/OTP/receipt templates (with لغو11) and promo SMS.
 */
class RuleBasedFilterTest {

    private fun explain(sender: String, body: String) =
        RuleBasedFilter.explain(SpamPatterns.DEFAULT, sender, body)

    /** A link: the companion that lets one marketing word or emoji go to Spam. */
    private val LINK = "\nexample.ir/x"

    @Test
    fun `OTP with لغو11 is allowed as a one-time code`() {
        val body = "کد تایید شما: 482913\nبانک ملت\nلغو11"
        val result = explain("BANKMELLAT", body)
        assertEquals(FilterVerdict.ALLOW, result.verdict)
        assertEquals(FilterRule.ONE_TIME_CODE, result.rule)
    }

    @Test
    fun `OTP with spaced لغو ۱۱ is allowed`() {
        val body = "رمز یک‌بار مصرف: ۴۵۶۷۸۹\nلغو ۱۱"
        val result = explain("BANK", body)
        assertEquals(FilterVerdict.ALLOW, result.verdict)
        assertEquals(FilterRule.ONE_TIME_CODE, result.rule)
    }

    @Test
    fun `bank withdrawal with لغو11 is allowed as transactional`() {
        val body = "برداشت از کارت *1234 به مبلغ 250,000 ریال\nموجودی: 1,200,000\nلغو11"
        val result = explain("1800", body)
        assertEquals(FilterVerdict.ALLOW, result.verdict)
        assertEquals(FilterRule.TRANSACTIONAL, result.rule)
    }

    @Test
    fun `bank deposit with لغو ۱۱ is allowed as transactional`() {
        val body = "واریز 500000 ریال به حساب شما انجام شد. موجودی 3200000 ریال\nلغو ۱۱"
        val result = explain("9035", body)
        assertEquals(FilterVerdict.ALLOW, result.verdict)
        assertEquals(FilterRule.TRANSACTIONAL, result.rule)
    }

    @Test
    fun `payment receipt with لغو11 is allowed as transactional`() {
        val body = "رسید پرداخت موفق\nتراکنش 998877\nمبلغ 45000 ریال\nلغو11"
        val result = explain("PAYAPP", body)
        assertEquals(FilterVerdict.ALLOW, result.verdict)
        assertEquals(FilterRule.TRANSACTIONAL, result.rule)
    }

    @Test
    fun `لغو alone is not a spam signal`() {
        val result = explain("SHOP", "برای لغو11 همین پیام را بفرستید")
        assertNotEquals(FilterVerdict.BLOCK, result.verdict)
    }

    @Test
    fun `two marketing words block from a numeric sender`() {
        val body = "جشنواره تخفیف ویژه آغاز شد"
        val result = explain("09121234567", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    @Test
    fun `one marketing word plus link blocks`() {
        val body = "رایگان ثبت کنید https://promo.ir/deal"
        val result = explain("09120000000", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    @Test
    fun `one marketing word from a named sender stays in inbox`() {
        // Carriers, banks and government notices come from names and mention gifts or festivals.
        assertEquals(FilterVerdict.NO_VERDICT, explain("HAMRAH_AVAL", "جشنواره تابستانی ما شروع شد").verdict)
        assertEquals(FilterVerdict.NO_VERDICT, explain("BANKMELLAT", "دستور پرداخت شما با موفقیت اجرا شد").verdict)
        assertEquals(FilterVerdict.NO_VERDICT, explain("IRANCELL", "مشترک گرامی، ۱۰۰ پیامک هدیه به شما تعلق گرفت").verdict)
    }

    @Test
    fun `one marketing word plus strong promo emoji blocks`() {
        val body = "جایزه ویژه برای شما 🎉"
        val result = explain("09123334444", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    @Test
    fun `lone marketing word from numeric sender stays in inbox`() {
        val body = "تخفیف روی سفارش شما اعمال شد"
        val result = explain("09125556666", body)
        assertEquals(FilterVerdict.NO_VERDICT, result.verdict)
    }

    @Test
    fun `lone strong emoji from numeric sender stays in inbox`() {
        val result = explain("09125556666", "پیتزای امروز 🍕")
        assertEquals(FilterVerdict.NO_VERDICT, result.verdict)
    }

    @Test
    fun `checkmark or pin with bare link is blocked`() {
        val check = explain("09120000003", "✅8آذر\nIu4.ir/e")
        assertEquals(FilterVerdict.BLOCK, check.verdict)
        val pin = explain("09120000004", "📍اسپیناس\nIu4.ir/e")
        assertEquals(FilterVerdict.BLOCK, pin.verdict)
    }

    @Test
    fun `checkmark and pin together are blocked`() {
        val result = explain("09120000005", "✅8آذر\n📍اسپیناس پالاس")
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    @Test
    fun `friend concert invite without link stays in inbox`() {
        val result = explain("09121234567", "بریم کنسرت؟ ساعت ۸")
        assertEquals(FilterVerdict.NO_VERDICT, result.verdict)
    }

    @Test
    fun `Talischi concert promo blast is blocked`() {
        val body = """
            کنسرت علیرضا طلیسچی😍

            ✅8آذر

            📍اسپیناس پالاس

            خرید👇
            Iu4.ir/e

            لغو 11
        """.trimIndent()
        val result = explain("30005", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
        assertTrue(result.matches.any { it.group == SpamPatternGroup.MARKETING })
        assertTrue(result.matches.any { it.group == SpamPatternGroup.LINKS })
        assertTrue(result.matches.any { it.group == SpamPatternGroup.PROMO_EMOJI })
    }

    @Test
    fun `event word plus bare short link is blocked`() {
        val body = "کنسرت جدید\nIu4.ir/e"
        val result = explain("09120000001", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    @Test
    fun `خرید pointer with link is blocked`() {
        val body = "بلیت موجود\nخرید👇\nbit.ly/x"
        val result = explain("09120000002", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
    }

    @Test
    fun `Arabic Yeh and Persian digits normalize before matching`() {
        // ي = Arabic Yeh; ۴۵۶۷۸۹ Persian digits in an OTP that must still allow.
        val body = "رمز يك‌بار مصرف: ۴۵۶۷۸۹\nلغو ۱۱"
        val result = explain("BANK", body)
        assertEquals(FilterVerdict.ALLOW, result.verdict)
        assertEquals(FilterRule.ONE_TIME_CODE, result.rule)
    }

    @Test
    fun `weak smileys never block even with a marketing word from numeric sender`() {
        val body = "تخفیف 😊❤"
        val result = explain("09125556666", body)
        assertEquals(FilterVerdict.NO_VERDICT, result.verdict)
    }

    @Test
    fun `urgency and CTA promo pack is blocked`() {
        val body = "فرصت محدود · بسته ویژه · همین حالا بخر"
        val result = explain("09127778888", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    @Test
    fun `winner phrase with named sender is blocked`() {
        val body = "شما برنده جایزه شدید"
        val result = explain("*8000", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    @Test
    fun `bare بسته alone is not a marketing signal`() {
        val body = "بسته اینترنت شما فعال شد\nلغو11"
        val result = explain("900", body)
        assertNotEquals(FilterVerdict.BLOCK, result.verdict)
    }

    @Test
    fun `مجانی with link is blocked`() {
        val body = "گوشی مجانی برای شما www.gift.ir/win"
        val result = explain("09121112222", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    @Test
    fun `قرعه کشی with party emoji is blocked`() {
        val body = "در قرعه‌کشی بزرگ شرکت کنید 🎊"
        val result = explain("09123334444", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    @Test
    fun `defaults have no unsubscribe or opt-out group`() {
        assertTrue(SpamPatternGroup.entries.none { it.key == "unsubscribe" || it.key == "opt_out" })
        assertTrue(SpamPatterns.DEFAULT.groups.keys.none { it.key == "unsubscribe" || it.key == "opt_out" })
    }

    @Test
    fun `plain filter labels are ordinary Persian words and promo emojis`() {
        val words = plainFilterLabels(SpamPatterns.DEFAULT[SpamPatternGroup.MARKETING])
        assertTrue(words.contains("تخفیف"))
        assertTrue(words.contains("جشنواره"))
        assertTrue(words.contains("کنسرت"))
        assertTrue(words.none { it.contains("\\") || it.contains("(?:") })
        val emojis = plainFilterLabels(SpamPatterns.DEFAULT[SpamPatternGroup.PROMO_EMOJI])
        assertTrue(emojis.contains("🎉"))
        assertTrue(emojis.contains("🍕"))
        assertTrue(emojis.contains("🔥"))
        assertTrue(emojis.contains("✅"))
        assertTrue(emojis.contains("📍"))
    }

    @Test
    fun `tatweel stretching a word still matches it, and the highlight covers the stretched word`() {
        val body = "تـخـفـیـف ویـژه$LINK"
        val result = explain("SHOP", body)
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        val words = result.matches.filter { it.group == SpamPatternGroup.MARKETING }.map { body.substring(it.range) }
        assertTrue(words.toString(), "تـخـفـیـف ویـژه" in words)
    }

    @Test
    fun `diacritics and direction marks inside a word still match it`() {
        // Fatha on the first letter, an RLM and a ZWJ inside the word.
        val result = explain("SHOP", "جَشن\u200Fوا\u200Dره تابستانی$LINK")
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    private fun withMarketing(pattern: String) = SpamPatterns.DEFAULT.with(SpamPatternGroup.MARKETING, listOf(pattern))

    @Test
    fun `a user pattern typed with ZWNJ, Persian digits or Arabic Yeh matches like the body it was copied from`() {
        val cases = listOf(
            "قرعه\u200cکشی" to "در قرعه\u200cکشی بزرگ شرکت کنید",
            "ثبت\u200cنام" to "همین امروز ثبت\u200cنام کنید",
            "لغو۱۱" to "برای لغو11 بفرستید",
            "هديه" to "یک هدیه برای شما",
        )
        cases.forEach { (pattern, body) ->
            val result = RuleBasedFilter.explain(withMarketing(pattern), "SHOP", body + LINK)
            assertEquals("$pattern in $body", FilterVerdict.BLOCK, result.verdict)
        }
    }

    @Test
    fun `a pattern is checked as the filter reads it`() {
        // The filter drops tatweel, which leaves a bare quantifier.
        assertTrue(SpamPatterns.problem("\u0640+") is PatternProblem.Invalid)
        assertEquals(null, SpamPatterns.problem("قرعه\u200cکشی"))
    }

    @Test
    fun `a pattern that can also match empty text still fires on its words`() {
        // An empty first branch wins at every position, so `(?:ویژه)?|حراج` can only ever find «ویژه».
        listOf("حراج|" to "فروش حراج", "(?:ویژه)?|حراج" to "فروش ویژه").forEach { (pattern, body) ->
            val result = RuleBasedFilter.explain(withMarketing(pattern), "SHOP", body + LINK)
            assertEquals(pattern, FilterVerdict.BLOCK, result.verdict)
        }
    }

    @Test
    fun `a pattern that matches empty text can't be saved`() {
        assertEquals(PatternProblem.MatchesEmpty, SpamPatterns.problem("حراج|"))
        assertEquals(PatternProblem.MatchesEmpty, SpamPatterns.problem("(?:ویژه)?|حراج"))
        assertEquals(null, SpamPatterns.problem("حراجی?"))
    }

    @Test
    fun `one phrase is one signal even when a shorter pattern matches inside it`() {
        // «کد تخفیف» matches both `کد\s*تخفیف` and `تخفیف`: one marketing phrase, not two.
        val result = explain("09121234567", "سلام، کد تخفیف اسنپ رو برات فرستادم")
        assertEquals(FilterVerdict.NO_VERDICT, result.verdict)
    }

    @Test
    fun `a shorter pattern still counts where it stands alone`() {
        val result = explain("09121234567", "کد تخفیف برای شما\nتخفیف تا ۵۰ درصد")
        assertEquals(FilterVerdict.BLOCK, result.verdict)
    }

    @Test
    fun `real one-time codes stay codes`() {
        listOf(
            "کد تایید شما:\n482913",
            "Your verification code is 554433",
            "G-482913 is your Google verification code.",
            "Your verification code for Google account is 123456",
            "Your one-time password: 7781",
            "Your OTP is 4821. Do not share it.",
            "123456 کد ورود شما به دیجی\u200cکالا است",
            "کد ورود: 123456\n\n@digikala.com #123456",
        ).forEach { body ->
            assertEquals(body, FilterRule.ONE_TIME_CODE, explain("SHOP", body).rule)
        }
    }

    @Test
    fun `a code word inside another word is not a code word`() {
        assertNotEquals(FilterRule.ONE_TIME_CODE, explain("SHOP", "Someone timed it: 1500 tickets").rule)
        assertNotEquals(FilterRule.ONE_TIME_CODE, explain("SHOP", "Barcode is ready, order 12345").rule)
    }

    @Test
    fun `a number in another sentence is not the code`() {
        assertNotEquals(FilterRule.ONE_TIME_CODE, explain("SHOP", "Your code is LUCKY… 2025").rule)
        assertNotEquals(FilterRule.ONE_TIME_CODE, explain("SHOP", "Your code is LUCKY. Win the lottery 2025!").rule)
    }

    @Test
    fun `a number far from the code word is not the code`() {
        val body = "Your code is ready to use at any of our stores across the country from 2025"
        assertNotEquals(FilterRule.ONE_TIME_CODE, explain("SHOP", body).rule)
    }

    @Test
    fun `a date is not a code, so a discount code doesn't pass as a one-time code`() {
        val result = explain("SHOP", "تخفیف ویژه ۵۰ درصد! کد شما: OFF50 تا ۱۴۰۳/۱۲/۲۹$LINK")
        assertEquals(FilterVerdict.BLOCK, result.verdict)
        assertEquals(FilterRule.MARKETING, result.rule)
    }

    /** The links the filter finds next to one marketing word from a number, where a link decides. */
    private fun links(text: String): List<String> {
        val body = "حراج $text"
        return explain("09120000000", body).matches.filter { it.group == SpamPatternGroup.LINKS }.map { body.substring(it.range) }
    }

    @Test
    fun `a bare host with several labels or a country suffix is a link`() {
        assertEquals(listOf("shop.digikala.com"), links("shop.digikala.com"))
        assertEquals(listOf("m.snapp.ir/x"), links("m.snapp.ir/x"))
        assertEquals(listOf("example.co.uk"), links("example.co.uk"))
    }

    @Test
    fun `a sentence's closing period is not part of the link`() {
        assertEquals(listOf("digikala.com"), links("Visit digikala.com."))
    }

    @Test
    fun `a missing space after a period is not a link`() {
        assertEquals(emptyList<String>(), links("see you later.Me too"))
    }
}
