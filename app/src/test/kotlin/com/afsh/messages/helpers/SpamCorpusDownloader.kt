package com.afsh.messages.helpers

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Downloads labeled SMS corpora at test time into a cache directory and verifies
 * published checksums. Failures are thrown with a clear message — never silent.
 */
object SpamCorpusDownloader {

    data class CorpusFile(val path: Path, val bytes: Long)

    const val PSSC_URL =
        "https://zenodo.org/api/records/7832188/files/PSSCv0.3.sqlite3/content"
    const val PSSC_MD5 = "bd7f112cff47d5930085336276cd1aa7"
    const val PSSC_SHA256 =
        "aee2d102dd56b247392c59def334cd01b3ceea3e6f3586257796726c956ea48f"

    const val UCI_ZIP_URL =
        "https://archive.ics.uci.edu/static/public/228/sms+spam+collection.zip"
    const val UCI_ZIP_SHA256 =
        "1587ea43e58e82b14ff1f5425c88e17f8496bfcdb67a583dbff9eefaf9963ce3"
    const val UCI_COLLECTION_SHA256 =
        "7d039a24a6083ed9ef0f806ebad56bbb976e3aeb8de05669173bfdc4996c239d"

    fun cacheDir(): Path {
        val override = System.getenv("SPAM_CORPORA_CACHE")
        val base = if (!override.isNullOrBlank()) {
            Path.of(override)
        } else {
            Path.of(System.getProperty("user.home"), ".cache", "afsh-messages", "spam-corpora")
        }
        Files.createDirectories(base)
        return base
    }

    fun psscSqlite(): CorpusFile {
        val dest = cacheDir().resolve("PSSCv0.3.sqlite3")
        downloadIfNeeded(PSSC_URL, dest, expectedMd5 = PSSC_MD5, expectedSha256 = PSSC_SHA256)
        return CorpusFile(dest, Files.size(dest))
    }

    /** Returns the path to the extracted `SMSSpamCollection` TSV. */
    fun uciCollection(): CorpusFile {
        val zip = cacheDir().resolve("uci-sms-spam-collection.zip")
        downloadIfNeeded(UCI_ZIP_URL, zip, expectedSha256 = UCI_ZIP_SHA256)
        val extracted = cacheDir().resolve("SMSSpamCollection")
        if (!Files.exists(extracted) || sha256(extracted) != UCI_COLLECTION_SHA256) {
            unzipEntry(zip, "SMSSpamCollection", extracted)
            val got = sha256(extracted)
            check(got == UCI_COLLECTION_SHA256) {
                "UCI SMSSpamCollection checksum mismatch: expected $UCI_COLLECTION_SHA256 got $got"
            }
        }
        return CorpusFile(extracted, Files.size(extracted))
    }

    private fun downloadIfNeeded(
        url: String,
        dest: Path,
        expectedMd5: String? = null,
        expectedSha256: String? = null,
    ) {
        if (Files.exists(dest)) {
            verify(dest, expectedMd5, expectedSha256)
            return
        }
        val tmp = dest.resolveSibling("${dest.fileName}.partial")
        try {
            URI(url).toURL().openStream().use { input ->
                Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
            }
            verify(tmp, expectedMd5, expectedSha256)
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(tmp) }
            throw IOException("Failed to download $url → $dest: ${e.message}", e)
        }
    }

    private fun verify(path: Path, expectedMd5: String?, expectedSha256: String?) {
        if (expectedMd5 != null) {
            val got = md5(path)
            check(got == expectedMd5) {
                "Checksum mismatch for ${path.fileName}: expected md5 $expectedMd5 got $got. " +
                    "Delete $path and re-run, or refuse to use a tampered corpus."
            }
        }
        if (expectedSha256 != null) {
            val got = sha256(path)
            check(got == expectedSha256) {
                "Checksum mismatch for ${path.fileName}: expected sha256 $expectedSha256 got $got. " +
                    "Delete $path and re-run, or refuse to use a tampered corpus."
            }
        }
    }

    private fun unzipEntry(zip: Path, entryName: String, dest: Path) {
        ZipInputStream(Files.newInputStream(zip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == entryName || entry.name.endsWith("/$entryName")) {
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING)
                    return
                }
                entry = zis.nextEntry
            }
        }
        error("Zip $zip does not contain $entryName")
    }

    fun md5(path: Path): String = digest(path, "MD5")
    fun sha256(path: Path): String = digest(path, "SHA-256")

    private fun digest(path: Path, algo: String): String =
        Files.newInputStream(path).use { digest(it, algo) }

    private fun digest(input: InputStream, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
