package com.afsh.messages.helpers

import org.json.JSONArray
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
 * The corpora carry no sender, and the filter reads one (a named sender is a companion). Each
 * corpus is scored once per [SenderKind] with that one sender on spam and ham alike, so the
 * sender never tells the filter the label. Thresholds are in the companion object. Precision is
 * gated tightly; recall is a floor so the filter cannot silently go inert. UCI English recall is
 * reported but not gated.
 *
 * PSSC masks links as `&link`, emojis as `&emoji` and digits as `&number`. A link is written back
 * ([PSSC_LINK]) on spam and ham alike, so the link companion is measured. Emojis and digits can't
 * be: the promo-emoji signal and the one-time-code exemption are covered by the unit tests and the
 * synthetic rows of must-not-flag.json instead.
 */
class SpamFilterAccuracyTest {

    /** What every corpus message is scored as coming from. */
    enum class SenderKind(val sender: String) {
        NUMBER("09120000000"),
        NAME("SENDER"),
    }

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

    data class Fixture(val sender: String, val body: String)

    @Test
    fun mustNotFlagOtpAndBank() {
        val failures = mustNotFlag.mapNotNull { sample ->
            val result = RuleBasedFilter.explain(SpamPatterns.DEFAULT, sample.sender, sample.body)
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

    @Test
    fun psscAccuracyFromNumbersMeetsFloor() = assertPssc(SenderKind.NUMBER, PSSC_NUMBER)

    @Test
    fun psscAccuracyFromNamesMeetsFloor() = assertPssc(SenderKind.NAME, PSSC_NAME)

    @Test
    fun uciAccuracyFromNumbersReported() = assertUci(SenderKind.NUMBER)

    @Test
    fun uciAccuracyFromNamesReported() = assertUci(SenderKind.NAME)

    private fun assertPssc(kind: SenderKind, gate: Gate) {
        val counts = score(psscSamples, kind)
        report("PSSC", kind, psscSamples, counts)
        assertTrue(
            "PSSC ($kind) precision ${pct(counts.precision)} < floor ${pct(gate.precision)}",
            counts.precision + 1e-9 >= gate.precision,
        )
        assertTrue(
            "PSSC ($kind) recall ${pct(counts.recall)} < floor ${pct(gate.recall)}",
            counts.recall + 1e-9 >= gate.recall,
        )
        assertTrue(
            "PSSC ($kind) FPR ${pct(counts.fpr)} > ceiling ${pct(gate.fpr)}",
            counts.fpr <= gate.fpr + 1e-9,
        )
    }

    private fun assertUci(kind: SenderKind) {
        val counts = score(uciSamples, kind)
        report("UCI", kind, uciSamples, counts)
        // English is a supplement; gate only that the suite ran and precision is not catastrophic.
        assertTrue("UCI suite empty", uciSamples.isNotEmpty())
        assertTrue(
            "UCI ($kind) precision ${pct(counts.precision)} < soft floor ${pct(UCI_PRECISION_SOFT_FLOOR)}",
            counts.precision + 1e-9 >= UCI_PRECISION_SOFT_FLOOR,
        )
    }

    private fun report(corpus: String, kind: SenderKind, samples: List<Sample>, counts: Counts) {
        println(
            "$corpus ($kind): n=${samples.size} P=${pct(counts.precision)} R=${pct(counts.recall)} " +
                "F1=${pct(counts.f1)} FPR=${pct(counts.fpr)} " +
                "tp=${counts.tp} fp=${counts.fp} tn=${counts.tn} fn=${counts.fn}",
        )
        if (counts.fp > 0 || counts.fn > 0) {
            println(sampleErrors(samples, kind, limit = 8))
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

    private fun score(samples: List<Sample>, kind: SenderKind): Counts {
        var tp = 0
        var fp = 0
        var tn = 0
        var fn = 0
        samples.forEach { sample ->
            val blocked = RuleBasedFilter.explain(
                SpamPatterns.DEFAULT,
                kind.sender,
                sample.body,
            ).verdict == FilterVerdict.BLOCK
            when {
                sample.spam && blocked -> tp++
                sample.spam && !blocked -> fn++
                !sample.spam && blocked -> fp++
                else -> tn++
            }
        }
        return Counts(tp, fp, tn, fn)
    }

    private fun sampleErrors(samples: List<Sample>, kind: SenderKind, limit: Int): String {
        val lines = mutableListOf<String>()
        for (sample in samples) {
            if (lines.size >= limit) break
            val result = RuleBasedFilter.explain(SpamPatterns.DEFAULT, kind.sender, sample.body)
            val blocked = result.verdict == FilterVerdict.BLOCK
            if (blocked != sample.spam) {
                val kind = if (sample.spam) "FN" else "FP"
                lines += "$kind [${sample.source}] ${sample.body.take(100).replace('\n', ' ')}"
            }
        }
        return lines.joinToString("\n")
    }

    private fun pct(v: Double) = "%.1f%%".format(v * 100.0)

    /** Floors on precision and recall, and a ceiling on the false-positive rate. */
    data class Gate(val precision: Double, val recall: Double, val fpr: Double)

    companion object {
        /**
         * Measured 2026-11-28 with one sender on spam and ham alike and PSSC's `&link` written back
         * (PSSC v0.3 n=4324):
         * - number: P=95.1% R=43.6% F1=59.8% FPR=2.2% (tp=929 fp=48 tn=2145 fn=1202)
         * - name: P=92.7% R=54.4% F1=68.6% FPR=4.1% (tp=1159 fp=91 tn=2102 fn=972)
         * UCI v.1 n=5574, either sender: P=100.0% R=0.0% FPR=0.0% (tp=0 fp=0 tn=4827 fn=747;
         * English recall is not a gate — the filter is Persian-keyword-first).
         * Floors = measured − 2pp (precision/recall); FPR ceiling = measured + 2pp.
         * UCI soft precision floor = 95%.
         */
        val PSSC_NUMBER = Gate(precision = 0.931, recall = 0.416, fpr = 0.042)
        val PSSC_NAME = Gate(precision = 0.907, recall = 0.524, fpr = 0.061)
        const val UCI_PRECISION_SOFT_FLOOR = 0.95

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
            val arr = JSONArray(text)
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(Fixture(sender = obj.getString("sender"), body = obj.getString("body")))
                }
            }
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
