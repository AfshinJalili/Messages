# Spam-filter evaluation corpora

These fixtures are used by `SpamFilterAccuracyTest` (host JVM). Labeled public
corpora are **downloaded at test time** into the Gradle cache / CI cache and
checksum-verified — they are not committed to this repository.

## Must-not-flag (vendored)

`must-not-flag.json` is project-authored OTP, bank, and receipt templates
(including the mandatory `لغو11` line), plus a friend concert invite with no
link. Every row must not score `BLOCK` (OTP/bank stay `ALLOW`; the invite may
be `NO_VERDICT`). The last rows are synthetic one-time codes that also carry
promo words, emojis and links: PSSC masks every digit as `&number`, so only
these rows show the code exemption working. No invented emoji-ham fixtures —
real ham protection is `must-not-flag.json` plus PSSC/UCI ham. No private
inbox text.

## PSSC v0.3 — Persian SMS Spam Collection

- Authors: Salari & Shayegan, 2023
- DOI: https://doi.org/10.5281/zenodo.7832188
- Zenodo record: https://zenodo.org/records/7832188
- File: `PSSCv0.3.sqlite3` (md5 `bd7f112cff47d5930085336276cd1aa7`)
- Citation: Salari & Shayegan (2023), Persian SMS Spam Collection (PSSC) v0.3.

Downloaded from Zenodo on each test run (cached). The test fails clearly if
the md5 does not match. Do not vendor the SQLite file into the repo.

PSSC masks links (`&link`), emojis (`&emoji`) and digits (`&number`). The test
writes a link back in place of `&link` on spam and ham alike; the other masks
stay, so PSSC never exercises promo emojis or one-time codes.

## UCI SMS Spam Collection v.1

- Authors: Almeida & Hidalgo, 2011
- DOI: https://doi.org/10.24432/C5CC84
- License: CC BY 4.0 (UCI page); keep the authors' bundled `readme` terms in mind
- Zip: https://archive.ics.uci.edu/static/public/228/sms+spam+collection.zip
  (sha256 `1587ea43e58e82b14ff1f5425c88e17f8496bfcdb67a583dbff9eefaf9963ce3`)

English supplement. The filter is Persian-keyword-first; English recall is not
a merge gate. Downloaded and checksum-verified at test time (cached).
