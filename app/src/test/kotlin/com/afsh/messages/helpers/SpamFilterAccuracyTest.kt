package com.afsh.messages.helpers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.sql.DriverManager

/**
 * Host-JVM accuracy of [RuleBasedFilter] over PSSC (Persian) + UCI (English), plus a
 * hard-fail project-authored OTP/bank must-not-flag set.
 *
 * Mapping: BLOCK = spam prediction; ALLOW and NO_VERDICT = ham (stays in inbox).
 *
 * The filter reads only the body, so the corpora's missing senders can't hint the label (an
 * earlier gate gave spam a named sender and ham a number). Thresholds are in the companion
 * object. Precision is gated tightly; recall is a floor so the filter cannot silently go inert.
 * UCI English recall is reported but not gated.
 *
 * PSSC masks links as `&link`, emojis as `&emoji` and digits as `&number`. A link is written back
 * ([PSSC_LINK]) on spam and ham alike, so the link companion is measured. Emojis and digits can't
 * be: the promo-emoji signal and the one-time-code exemption are covered by the unit tests and the
 * synthetic rows of must-not-flag.json instead.
 */
class SpamFilterAccuracyTest {

    data class Sample(val body: String, val spam: Boolean, val source: String)
    data class Counts(
        val tp: Int,
        val fp: Int,
        val tn: Int,
        val fn: Int,
    ) {
        val precision: Double get() = if (tp + fp == 0) 1.0 else tp.toDouble() / (tp + fp)
        val recall: Double get() = if (tp + fn == 0) 1.0 else tp.toDouble() / (tp + fn)
        val fpr: Double get() = if (fp + tn == 0) 0.0 else fp.toDouble() / (fp + tn)
        val f1: Double get() {
            val p = precision
            val r = recall
            return if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
        }
    }

    /** A must-not-flag row. [sender] only names the row in a failure; the filter reads the body. */
    data class Fixture(val sender: String, val body: String)

    @Test
    fun mustNotFlagOtpAndBank() {
        val failures = mustNotFlag.mapNotNull { sample ->
            val result = RuleBasedFilter.explain(SpamPatterns.DEFAULT, sample.body)
            if (result.verdict == FilterVerdict.BLOCK) {
                "${sample.sender}: verdict=${result.verdict} rule=${result.rule} body=${sample.body.take(80)}"
            } else {
                null
            }
        }
        assertTrue(
            "must-not-flag set must not be BLOCK:\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    /**
     * The synthetic code rows carry promo words, emojis and links, so the rules would block them:
     * only the one-time-code exemption keeps them out of Spam, which is what [mustNotFlagOtpAndBank]
     * checks. PSSC can't, since it masks digits.
     */
    @Test
    fun mustNotFlagLeansOnTheCodeExemption() {
        val withoutCodeWords = SpamPatterns.DEFAULT.with(SpamPatternGroup.CODE_WORDS, emptyList())
        val needExemption = mustNotFlag.count { RuleBasedFilter.explain(withoutCodeWords, it.body).verdict == FilterVerdict.BLOCK }
        assertTrue(
            "only $needExemption must-not-flag rows need the code exemption, expected $SYNTHETIC_CODE_ROWS",
            needExemption >= SYNTHETIC_CODE_ROWS,
        )
    }

    @Test
    fun psscAccuracyMeetsFloor() {
        val counts = score(psscSamples)
        report("PSSC", psscSamples, counts)
        assertTrue(
            "PSSC precision ${pct(counts.precision)} < floor ${pct(PSSC_PRECISION_FLOOR)}",
            counts.precision + 1e-9 >= PSSC_PRECISION_FLOOR,
        )
        assertTrue(
            "PSSC recall ${pct(counts.recall)} < floor ${pct(PSSC_RECALL_FLOOR)}",
            counts.recall + 1e-9 >= PSSC_RECALL_FLOOR,
        )
        assertTrue(
            "PSSC FPR ${pct(counts.fpr)} > ceiling ${pct(PSSC_FPR_CEILING)}",
            counts.fpr <= PSSC_FPR_CEILING + 1e-9,
        )
    }

    @Test
    fun uciAccuracyReported() {
        val counts = score(uciSamples)
        report("UCI", uciSamples, counts)
        // English is a supplement; gate only that the suite ran and precision is not catastrophic.
        assertTrue("UCI suite empty", uciSamples.isNotEmpty())
        assertTrue(
            "UCI precision ${pct(counts.precision)} < soft floor ${pct(UCI_PRECISION_SOFT_FLOOR)}",
            counts.precision + 1e-9 >= UCI_PRECISION_SOFT_FLOOR,
        )
    }

    private fun report(corpus: String, samples: List<Sample>, counts: Counts) {
        println(
            "$corpus: n=${samples.size} P=${pct(counts.precision)} R=${pct(counts.recall)} " +
                "F1=${pct(counts.f1)} FPR=${pct(counts.fpr)} " +
                "tp=${counts.tp} fp=${counts.fp} tn=${counts.tn} fn=${counts.fn}",
        )
        if (counts.fp > 0 || counts.fn > 0) {
            println(sampleErrors(samples, limit = 8))
        }
    }

    @Test
    fun corpusChecksumsMatchPublished() {
        val pssc = SpamCorpusDownloader.psscSqlite()
        assertEquals(SpamCorpusDownloader.PSSC_MD5, SpamCorpusDownloader.md5(pssc.path))
        val uci = SpamCorpusDownloader.uciCollection()
        assertEquals(
            SpamCorpusDownloader.UCI_COLLECTION_SHA256,
            SpamCorpusDownloader.sha256(uci.path),
        )
    }

    private fun score(samples: List<Sample>): Counts {
        var tp = 0
        var fp = 0
        var tn = 0
        var fn = 0
        samples.forEach { sample ->
            val blocked = RuleBasedFilter.explain(SpamPatterns.DEFAULT, sample.body).verdict == FilterVerdict.BLOCK
            when {
                sample.spam && blocked -> tp++
                sample.spam && !blocked -> fn++
                !sample.spam && blocked -> fp++
                else -> tn++
            }
        }
        return Counts(tp, fp, tn, fn)
    }

    private fun sampleErrors(samples: List<Sample>, limit: Int): String {
        val lines = mutableListOf<String>()
        for (sample in samples) {
            if (lines.size >= limit) break
            val blocked = RuleBasedFilter.explain(SpamPatterns.DEFAULT, sample.body).verdict == FilterVerdict.BLOCK
            if (blocked != sample.spam) {
                val kind = if (sample.spam) "FN" else "FP"
                lines += "$kind [${sample.source}] ${sample.body.take(100).replace('\n', ' ')}"
            }
        }
        return lines.joinToString("\n")
    }

    private fun pct(v: Double) = "%.1f%%".format(v * 100.0)

    companion object {
        /**
         * Measured 2026-11-28 on the body alone, with PSSC's `&link` written back (PSSC v0.3
         * n=4324): P=95.1% R=43.6% F1=59.8% FPR=2.2% (tp=929 fp=48 tn=2145 fn=1202).
         * UCI v.1 n=5574: P=100.0% R=0.0% FPR=0.0% (tp=0 fp=0 tn=4827 fn=747; English recall is
         * not a gate — the filter is Persian-keyword-first).
         * Floors = measured − 2pp (precision/recall); FPR ceiling = measured + 2pp.
         * UCI soft precision floor = 95%.
         */
        const val PSSC_PRECISION_FLOOR = 0.931
        const val PSSC_RECALL_FLOOR = 0.416
        const val PSSC_FPR_CEILING = 0.042
        const val UCI_PRECISION_SOFT_FLOOR = 0.95

        /** must-not-flag.json rows with a one-time code the rules would otherwise block. */
        const val SYNTHETIC_CODE_ROWS = 5
        const val MUST_NOT_FLAG_MIN_ROWS = 20

        /** What PSSC's `&link` mask is read as. */
        const val PSSC_LINK = "https://example.ir/x"

        lateinit var mustNotFlag: List<Fixture>
        lateinit var psscSamples: List<Sample>
        lateinit var uciSamples: List<Sample>

        @JvmStatic
        @BeforeClass
        fun loadCorpora() {
            mustNotFlag = loadMustNotFlag()
            psscSamples = loadPssc()
            uciSamples = loadUci()
        }

        private fun loadMustNotFlag(): List<Fixture> {
            val loader = checkNotNull(SpamFilterAccuracyTest::class.java.classLoader)
            val stream = loader.getResourceAsStream("spam-corpora/must-not-flag.json")
                ?: error("missing spam-corpora/must-not-flag.json")
            val text = stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            // Not org.json: unit tests get Android's stub, whose JSONArray is always empty.
            return Json.parseToJsonElement(text).jsonArray.map { row ->
                val obj = row.jsonObject
                Fixture(sender = obj.getValue("sender").jsonPrimitive.content, body = obj.getValue("body").jsonPrimitive.content)
            }.also { check(it.size >= MUST_NOT_FLAG_MIN_ROWS) { "must-not-flag too small: ${it.size}" } }
        }

        private fun loadPssc(): List<Sample> {
            Class.forName("org.sqlite.JDBC")
            val file = SpamCorpusDownloader.psscSqlite()
            DriverManager.getConnection("jdbc:sqlite:${file.path}").use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT text, label FROM SMS").use { rs ->
                        val out = mutableListOf<Sample>()
                        while (rs.next()) {
                            val label = rs.getString("label")?.trim()?.lowercase()
                            val body = rs.getString("text") ?: continue
                            val spam = when (label) {
                                "spam" -> true
                                "ham" -> false
                                else -> continue
                            }
                            out += Sample(body = body.replace("&link", PSSC_LINK), spam = spam, source = "pssc")
                        }
                        check(out.size >= 4000) { "PSSC too small: ${out.size}" }
                        return out
                    }
                }
            }
        }

        private fun loadUci(): List<Sample> {
            val file = SpamCorpusDownloader.uciCollection()
            return Files.readAllLines(file.path, StandardCharsets.UTF_8).mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val label = line.substring(0, tab).trim().lowercase()
                val body = line.substring(tab + 1)
                val spam = when (label) {
                    "spam" -> true
                    "ham" -> false
                    else -> return@mapNotNull null
                }
                Sample(body = body, spam = spam, source = "uci")
            }.also { check(it.size >= 5000) { "UCI too small: ${it.size}" } }
        }
    }
}
