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
 * Thresholds below were measured on redesign-groundwork with the current defaults
 * (see companion object). Precision is gated tightly; recall is a floor so the
 * filter cannot silently go inert. UCI English recall is reported but not gated.
 */
class SpamFilterAccuracyTest {

    data class Sample(val sender: String, val body: String, val spam: Boolean, val source: String)
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
    fun psscAccuracyMeetsFloor() {
        val counts = score(psscSamples)
        println(
            "PSSC: n=${psscSamples.size} P=${pct(counts.precision)} R=${pct(counts.recall)} " +
                "F1=${pct(counts.f1)} FPR=${pct(counts.fpr)} " +
                "tp=${counts.tp} fp=${counts.fp} tn=${counts.tn} fn=${counts.fn}",
        )
        if (counts.fp > 0 || counts.fn > 0) {
            println(sampleErrors(psscSamples, limit = 8))
        }
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
        println(
            "UCI: n=${uciSamples.size} P=${pct(counts.precision)} R=${pct(counts.recall)} " +
                "F1=${pct(counts.f1)} FPR=${pct(counts.fpr)} " +
                "tp=${counts.tp} fp=${counts.fp} tn=${counts.tn} fn=${counts.fn}",
        )
        // English is a supplement; gate only that the suite ran and precision is not catastrophic.
        assertTrue("UCI suite empty", uciSamples.isNotEmpty())
        assertTrue(
            "UCI precision ${pct(counts.precision)} < soft floor ${pct(UCI_PRECISION_SOFT_FLOOR)}",
            counts.precision + 1e-9 >= UCI_PRECISION_SOFT_FLOOR,
        )
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
            val blocked = RuleBasedFilter.explain(
                SpamPatterns.DEFAULT,
                sample.sender,
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

    private fun sampleErrors(samples: List<Sample>, limit: Int): String {
        val lines = mutableListOf<String>()
        for (sample in samples) {
            if (lines.size >= limit) break
            val result = RuleBasedFilter.explain(SpamPatterns.DEFAULT, sample.sender, sample.body)
            val blocked = result.verdict == FilterVerdict.BLOCK
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
         * Measured 2026-11-18 after Persian promo-blast signals (`RuleBasedFilter` weights +
         * `SpamPatterns.DEFAULT`): PSSC v0.3 n=4324 → P=98.6% R=54.4% F1=70.1% FPR=0.7%
         * (tp=1159 fp=16 tn=2177 fn=972). Prior baseline was P=98.8% R=49.3% FPR=0.6%.
         * UCI v.1 n=5574 → P=100.0% R=0.0% FPR=0.0% (tp=0 fp=0 tn=4827 fn=747; English
         * recall is not a gate — the filter is Persian-keyword-first).
         * Floors = measured − 2pp (precision/recall); FPR ceiling = measured + 2pp.
         * UCI soft precision floor = measured − 5pp.
         */
        const val PSSC_PRECISION_FLOOR = 0.966
        const val PSSC_RECALL_FLOOR = 0.524
        const val PSSC_FPR_CEILING = 0.027
        const val UCI_PRECISION_SOFT_FLOOR = 0.95

        lateinit var mustNotFlag: List<Sample>
        lateinit var psscSamples: List<Sample>
        lateinit var uciSamples: List<Sample>

        @JvmStatic
        @BeforeClass
        fun loadCorpora() {
            mustNotFlag = loadMustNotFlag()
            psscSamples = loadPssc()
            uciSamples = loadUci()
        }

        private fun loadMustNotFlag(): List<Sample> {
            val loader = checkNotNull(SpamFilterAccuracyTest::class.java.classLoader)
            val stream = loader.getResourceAsStream("spam-corpora/must-not-flag.json")
                ?: error("missing spam-corpora/must-not-flag.json")
            val text = stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            val arr = JSONArray(text)
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        Sample(
                            sender = obj.getString("sender"),
                            body = obj.getString("body"),
                            spam = false,
                            source = "must-not-flag",
                        ),
                    )
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
                            out += Sample(
                                sender = if (spam) "PSSCSPAM" else "09120000000",
                                body = body,
                                spam = spam,
                                source = "pssc",
                            )
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
                Sample(
                    sender = if (spam) "UCIPROMO" else "447700000000",
                    body = body,
                    spam = spam,
                    source = "uci",
                )
            }.also { check(it.size >= 5000) { "UCI too small: ${it.size}" } }
        }
    }
}
