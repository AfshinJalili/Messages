"""Run with python3 tests/check_inbox_database.py; synthetic data only."""
import json
import re
import sqlite3
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / "app/src/main/kotlin/com/afsh/messages"
version = int(re.search(r"version = (\d+)", (source / "databases/MessagesDatabase.kt").read_text())[1])
schema = json.loads((ROOT / f"app/schemas/com.afsh.messages.databases.MessagesDatabase/{version}.json").read_text())["database"]
db = sqlite3.connect(":memory:")
for entity in schema["entities"]:
    db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for index in entity.get("indices", []):
        db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))

def insert_rows(table, count, values):
    entity = next(e for e in schema["entities"] if e["tableName"] == table)
    fields = entity["fields"]
    columns = ",".join('"' + f["columnName"] + '"' for f in fields)
    defaults = {f["columnName"]: ("" if f["affinity"] == "TEXT" else 0) for f in fields}
    # Nullable preview columns stay NULL unless the seeder sets them.
    for field in fields:
        if not field.get("notNull", False) and field["columnName"] not in ("preview_snippet", "preview_date"):
            continue
        if not field.get("notNull", False):
            defaults[field["columnName"]] = None
    rows = [dict(defaults, **values(i)) for i in range(1, count + 1)]
    db.executemany(f"INSERT INTO {table} ({columns}) VALUES ({','.join('?' for _ in fields)})",
                   [tuple(row[f["columnName"]] for f in fields) for row in rows])

insert_rows(
    "conversations",
    1500,
    lambda i: {
        "thread_id": i,
        "snippet": "provider fallback",
        "preview_snippet": str((i - 1) % 1500 + 1 + 27000) if i != 1500 else "28500",
        "preview_date": None,
        "all_spam": 0,
    },
)
insert_rows("messages", 30000, lambda i: {"id": i, "thread_id": (i - 1) % 1500 + 1, "date": i, "body": str(i)})
db.execute("INSERT INTO recycle_bin_messages (id, is_mms, deleted_ts) VALUES (30000, 0, 1)")
# Thread 1500's latest non-recycled message is 28500; denormalized preview already matches.
dao_source = (source / "interfaces/ConversationsDao.kt").read_text()
dao = (source / "interfaces/MessagesDao.kt").read_text()

def resolve_query_constants(sql):
    """Expand the small SQL constants (declared in either DAO file) used by the shipped Room DAO queries."""
    def replace(match):
        name = match[1]
        declaration = None
        for text in (dao_source, dao):
            declaration = declaration or re.search(rf"(?:private|internal) const val {name} =\s*(.*?)(?:\n\n|\n@Dao)", text, re.S)
        assert declaration, f"Missing DAO SQL constant: {name}"
        return resolve_query_constants("".join(re.findall(r'"([^"]*)"', declaration[1])))

    return re.sub(r"\$(\w+)", replace, sql)

def dao_query(method):
    """The SQL of MessagesDao.[method], one-line or triple-quoted, with its constants expanded."""
    for match in re.finditer(r'@Query\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)\s+fun (\w+)\(', dao, re.S):
        if match[3] == method:
            return resolve_query_constants(match[1] or match[2])
    raise AssertionError(f"MessagesDao.{method} missing")

queries = re.findall(r'@Query\("(SELECT .*?)"\)', dao_source)
query = resolve_query_constants(next(q for q in queries if "FROM conversations WHERE archived = 0" in q))
assert "FROM messages" not in query.upper().replace("FROM CONVERSATIONS", ""), "Inbox must not subquery messages"
assert "spam_messages" not in query, "Inbox must not subquery spam_messages"
assert "recycle_bin_messages" not in query, "Inbox must not subquery recycle_bin_messages"
start = time.perf_counter()
cursor = db.execute(query)
columns = [column[0] for column in cursor.description]
rows = cursor.fetchall()
elapsed = time.perf_counter() - start
plan = [row[3] for row in db.execute("EXPLAIN QUERY PLAN " + query)]
assert len(rows) == 1500
assert next(row[columns.index("new_snippet")] for row in rows if row[columns.index("thread_id")] == 1500) == "28500", "Recycled latest message must not become the preview"
print(f"Inbox: {len(rows)} conversations / 30000 messages in {elapsed:.3f}s")
print("\n".join(plan))
assert not any("SCAN messages" in step for step in plan), "Inbox scans all messages for every conversation"
assert not any("CORRELATED" in step for step in plan), "Inbox must not use correlated subqueries"
print("PASS: denormalized inbox lookup without correlated subqueries")

# Hide an all-spam thread via the denormalized flag.
db.execute("UPDATE conversations SET all_spam = 1 WHERE thread_id = 1")
hidden = db.execute(query).fetchall()
assert len(hidden) == 1499
assert all(row[columns.index("thread_id")] != 1 for row in hidden)
print("PASS: all_spam hides threads from the inbox query")

# Exercise the shipped migration twice; adding preview columns must preserve every prior row.
# Build a v14-shaped DB, then apply MIGRATION_14_15 SQL from source.
v14 = json.loads((ROOT / "app/schemas/com.afsh.messages.databases.MessagesDatabase/14.json").read_text())["database"]
mig_db = sqlite3.connect(":memory:")
for entity in v14["entities"]:
    mig_db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for index in entity.get("indices", []):
        mig_db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
mig_db.execute(
    "INSERT INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count, message_count) "
    "VALUES (9, 'kept', 1, 1, 'Kept', '', 0, '555', 0, 0, 0, 0, 2)"
)
mig_db.execute("INSERT INTO messages (id, body, type, participants, date, read, thread_id, is_mms, attachment, sender_phone_number, sender_name, sender_photo_uri, subscription_id, status, is_scheduled) "
               "VALUES (1, 'visible', 1, '[]', 10, 1, 9, 0, NULL, '', '', '', -1, -1, 0)")
mig_db.execute("INSERT INTO messages (id, body, type, participants, date, read, thread_id, is_mms, attachment, sender_phone_number, sender_name, sender_photo_uri, subscription_id, status, is_scheduled) "
               "VALUES (2, 'spam-body', 1, '[]', 20, 1, 9, 0, NULL, '', '', '', -1, -1, 0)")
mig_db.execute("INSERT INTO spam_messages (id, thread_id, reason, marked_ts) VALUES (2, 9, 1, 1)")
before = mig_db.execute("SELECT thread_id, snippet FROM conversations").fetchall()
migration_source = (source / "databases/MessagesDatabase.kt").read_text()
block = re.search(
    r"private val MIGRATION_14_15.*?override fun migrate\(db: SupportSQLiteDatabase\) \{(.*?)^\s{8}\}",
    migration_source,
    re.S | re.M,
)
assert block, "MIGRATION_14_15 missing"
alters = re.findall(r'db\.execSQL\("(ALTER TABLE.*?)"\)', block[1])
assert len(alters) == 3, alters
update = re.search(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)', block[1], re.S)
assert update, "MIGRATION_14_15 UPDATE missing"
update_sql = "\n".join(line for line in update[1].splitlines() if line.strip())
for sql in alters:
    mig_db.execute(sql)
mig_db.execute(update_sql)
# Retry the UPDATE (ALTER would fail on a second pass).
mig_db.execute(update_sql)
assert mig_db.execute("SELECT thread_id, snippet FROM conversations").fetchall() == before
preview = mig_db.execute("SELECT preview_snippet, preview_date, all_spam FROM conversations WHERE thread_id = 9").fetchone()
assert preview[0] == "visible", preview
assert preview[1] == 10, preview
assert preview[2] == 0, preview
print("PASS: migration 14→15 backfills preview columns and preserves rows")

# Also keep the older index migration check against the current schema's messages index.
db.execute("DROP INDEX IF EXISTS index_messages_thread_id_date")
migration_11_12 = re.search(r'private val MIGRATION_11_12.*?db.execSQL\("(.*?)"\)', migration_source, re.S)[1]
for _ in range(2):
    db.execute(migration_11_12)
print("PASS: migration 11→12 index can be retried")

# Run the actual queries used by Starred and message search against a recycled hit.
for method, params in [("getMessagesWithIds", {"ids": 30000}), ("getMessagesWithText", {"text": "30000"})]:
    query = dao_query(method)
    assert not db.execute(query, params).fetchall(), f"{method} exposes recycled messages"
    active = {"ids": 29999} if method == "getMessagesWithIds" else {"text": "29999"}
    assert len(db.execute(query, active).fetchall()) == 1, f"{method} hides active messages"
print("PASS: starred and search results exclude recycled messages")

# The Recycle bin cards count each conversation's recycled messages and date the latest deletion.
summaries = dao_query("getRecycleBinSummaries")
db.execute("INSERT INTO recycle_bin_messages (id, is_mms, deleted_ts) VALUES (28500, 0, 5)")
rows = {thread: (count, deleted) for thread, count, deleted in db.execute(summaries).fetchall()}
assert rows == {(30000 - 1) % 1500 + 1: (2, 5)}, rows
db.execute("DELETE FROM recycle_bin_messages WHERE id = 28500")
print("PASS: recycle bin summaries group by conversation")

# Partial reads preserve unseen rows and do not alias SMS/MMS IDs.
read_query = re.search(r'@Query\("(UPDATE messages SET read = 1 WHERE id = :id AND is_mms = :isMMS)"\)', dao)[1]
db.execute("UPDATE messages SET read = 0, is_mms = 0 WHERE id IN (1, 2)")
db.execute(read_query, {"id": 1, "isMMS": 1})
assert db.execute("SELECT read FROM messages WHERE id = 1").fetchone()[0] == 0
db.execute(read_query, {"id": 1, "isMMS": 0})
assert db.execute("SELECT read FROM messages WHERE id IN (1, 2) ORDER BY id").fetchall() == [(1,), (0,)]
count_query = re.search(r'@Query\("(UPDATE conversations SET read = \(:count = 0\), unread_count = :count WHERE thread_id = :threadId)"\)',
                        (source / "interfaces/ConversationsDao.kt").read_text())[1]
for count in (12, 3, 0):
    db.execute(count_query, {"threadId": 1, "count": count})
    assert db.execute("SELECT read, unread_count FROM conversations WHERE thread_id = 1").fetchone() == (int(count == 0), count)
print("PASS: partial read updates preserve unseen rows and provider identity; counts reach zero")

# Has Room cached Telephony's newest message? (ConversationRepository.holdsNewestMessage)
latest_query = re.search(r'@Query\("([^"\n]+)"\)\s+fun getThreadLatestDate\(', dao)[1]
snippet_query = re.search(r'@Query\("([^"\n]+)"\)\s+fun hasMessageForSnippet\(', dao)[1]
thread = 990001
db.execute(
    "INSERT INTO messages (id, body, type, status, participants, date, read, thread_id, is_mms, attachment,"
    " sender_phone_number, sender_name, sender_photo_uri, subscription_id, is_scheduled)"
    " VALUES (990001, 'Win' || char(10) || 'now', 1, 0, '[]', 20, 1, ?, 0, NULL, '', '', '', 0, 0),"
    " (990002, 'tomorrow', 1, 0, '[]', 900, 1, ?, 0, NULL, '', '', '', 0, 1)",
    (thread, thread),
)
assert db.execute(latest_query, {"threadId": thread}).fetchone()[0] == 20, "a scheduled message counted as cached"
def names(snippet, date=20):
    return db.execute(snippet_query, {"threadId": thread, "date": date, "snippet": snippet}).fetchone()[0] == 1
assert names("Win now"), "a provider snippet with a space for the line break missed the cached SMS"
assert names("Win"), "a truncated provider snippet missed the cached SMS"
assert names(""), "an empty snippet must not mark Room stale"
assert not names("reply"), "a same-second message Room lacks looked cached"
assert not names("tomorrow", date=900), "a scheduled message looked like a Telephony one"
db.execute("DELETE FROM messages WHERE thread_id = ?", (thread,))
print("PASS: the newest cached message ignores scheduled rows and matches provider snippets")

# SMS and MMS ids are separate Telephony sequences that overlap: a cached message and a recycle-bin
# entry are keyed by id and transport, and every recycle-bin join matches on both.
for name, text in (("MessagesDao", dao), ("ConversationsDao", dao_source)):
    for match in re.finditer(r"messages\.id = recycle_bin_messages\.id", text):
        assert text[match.end():].startswith(" AND messages.is_mms = recycle_bin_messages.is_mms"), \
            f"{name} joins the recycle bin on id alone"
assert "recycle_bin_messages ON" not in dao_source.replace("$JOIN_RECYCLE_BIN", ""), "ConversationsDao must use JOIN_RECYCLE_BIN"
MESSAGE_COLUMNS = ("id, body, type, status, participants, date, read, thread_id, is_mms, attachment,"
                   " sender_phone_number, sender_name, sender_photo_uri, subscription_id, is_scheduled")
def cache(conn, message_id, thread, is_mms, body, scheduled=0):
    conn.execute(f"INSERT OR REPLACE INTO messages ({MESSAGE_COLUMNS}) VALUES (?, ?, 2, -1, '[]', 1, 1, ?, ?, NULL, '', '', '', 0, ?)",
                 (message_id, body, thread, is_mms, scheduled))
def run(method, **params):
    return db.execute(dao_query(method), {k: int(v) if isinstance(v, bool) else v for k, v in params.items()})
def bodies(method, **params):
    return sorted(row[1] for row in run(method, **params).fetchall() if row[0] == shared)
shared, sms_thread, mms_thread = 990042, 990101, 990102
cache(db, shared, sms_thread, 0, "sms")
cache(db, shared, mms_thread, 1, "mms")
cache(db, shared, mms_thread, 1, "mms")  # insertOrUpdate of the MMS again: the SMS must survive.
assert db.execute("SELECT body FROM messages WHERE id = ? ORDER BY is_mms", (shared,)).fetchall() == [("sms",), ("mms",)]
assert run("getThreadIdForMessage", id=shared, isMms=False).fetchone()[0] == sms_thread
assert run("getThreadIdForMessage", id=shared, isMms=True).fetchone()[0] == mms_thread
db.execute("INSERT INTO recycle_bin_messages (id, is_mms, deleted_ts) VALUES (?, 0, 7)", (shared,))
assert bodies("getThreadMessagesFromRecycleBin", threadId=sms_thread) == ["sms"]
assert bodies("getThreadMessagesFromRecycleBin", threadId=mms_thread) == [], "binning an SMS binned the MMS with its id"
assert bodies("getNonRecycledThreadMessages", threadId=mms_thread) == ["mms"]
assert bodies("getAllRecycleBinMessages") == ["sms"]
assert bodies("getOldRecycleBinMessages", timestamp=8) == ["sms"], "purging would erase the MMS sharing a binned SMS's id"
assert bodies("getMessagesWithIds", ids=shared) == ["mms"]
assert bodies("getMessagesWithText", text="mms") == ["mms"]
rows = {thread: (count, deleted) for thread, count, deleted in run("getRecycleBinSummaries").fetchall()}
assert rows.get(sms_thread) == (1, 7) and mms_thread not in rows, rows
run("updateType", id=shared, isMms=True, type=5)
run("updateStatus", id=shared, isMms=True, status=0)
assert db.execute("SELECT type, status FROM messages WHERE id = ? AND is_mms = 0", (shared,)).fetchone() == (2, -1), \
    "a status update for the MMS changed the SMS"
run("deleteThreadMessagesFromRecycleBin", threadId=mms_thread)
run("deleteFromRecycleBin", id=shared, isMms=True)
assert db.execute("SELECT is_mms FROM recycle_bin_messages WHERE id = ?", (shared,)).fetchall() == [(0,)], \
    "restoring the MMS restored the SMS"
run("deleteFromMessages", id=shared, isMms=True)
assert db.execute("SELECT body FROM messages WHERE id = ?", (shared,)).fetchall() == [("sms",)], "deleting the MMS deleted the SMS"
cache(db, shared, sms_thread, 1, "scheduled", scheduled=1)
run("deleteScheduledMessage", id=shared)
assert db.execute("SELECT body FROM messages WHERE id = ?", (shared,)).fetchall() == [("sms",)], \
    "dropping a scheduled message deleted a Telephony one"
db.execute("DELETE FROM messages WHERE id = ?", (shared,))
db.execute("DELETE FROM recycle_bin_messages WHERE id = ?", (shared,))
print("PASS: SMS and MMS sharing an id stay apart in the cache and the recycle bin")

# A due scheduled message is found by id alone: reconcile and deletion move scheduled messages to
# another thread without re-arming their alarm (ScheduledMessageSender).
due = 990077
cache(db, due, 990201, 1, "scheduled", scheduled=1)
cache(db, due, 990202, 1, "scheduled", scheduled=1)  # moved to the real thread
cache(db, due, 990202, 0, "telephony sms")
found = run("getScheduledMessage", id=due).fetchall()
assert [(row[1], row[7]) for row in found] == [("scheduled", 990202)], found
run("deleteScheduledMessage", id=due)
assert not run("getScheduledMessage", id=due).fetchall(), "a Telephony row looked scheduled"
db.execute("DELETE FROM messages WHERE id = ?", (due,))
print("PASS: a due scheduled message is found by id in the thread it moved to")

# Migration 15→16 rebuilds both tables on (id, is_mms), keeps every row, and gives each bin entry
# its message's transport. Built from the shipped v15 schema and the migration SQL in source.
def build(version):
    conn = sqlite3.connect(":memory:")
    shape = json.loads((ROOT / f"app/schemas/com.afsh.messages.databases.MessagesDatabase/{version}.json").read_text())["database"]
    for entity in shape["entities"]:
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            conn.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return conn
def shape_of(conn, table):
    columns = sorted((name, kind, notnull, pk) for _, name, kind, notnull, _, pk in conn.execute(f"PRAGMA table_info({table})"))
    indices = sorted((name, unique, tuple(c[2] for c in conn.execute(f"PRAGMA index_info({name})")))
                     for _, name, unique, origin, _ in conn.execute(f"PRAGMA index_list({table})") if origin == "c")
    return columns, indices
block = re.search(r"private val MIGRATION_15_16.*?override fun migrate\(db: SupportSQLiteDatabase\) \{(.*?)^\s{8}\}", migration_source, re.S | re.M)
assert block, "MIGRATION_15_16 missing"
assert ".addMigrations(MIGRATION_15_16)" in migration_source, "MIGRATION_15_16 is not registered"
statements = re.findall(r'db\.execSQL\("(.*?)"\)', block[1])
assert statements, "MIGRATION_15_16 runs no SQL"
v15_db = build(15)
cache(v15_db, 1, 9, 0, "binned sms")
cache(v15_db, 2, 9, 1, "binned mms")
cache(v15_db, 3, 9, 0, "kept sms")
cache(v15_db, 4, 8, 1, "scheduled mms", scheduled=1)
v15_db.executemany("INSERT INTO recycle_bin_messages (id, deleted_ts) VALUES (?, ?)", [(1, 11), (2, 12), (77, 13)])
v15_db.execute("INSERT INTO spam_messages (id, thread_id, reason, marked_ts) VALUES (3, 9, 1, 1)")
v15_db.execute("INSERT INTO drafts (thread_id, body, date) VALUES (9, 'draft', 1)")
messages_before = sorted(v15_db.execute(f"SELECT {MESSAGE_COLUMNS} FROM messages").fetchall())
for sql in statements:
    v15_db.execute(sql)
assert sorted(v15_db.execute(f"SELECT {MESSAGE_COLUMNS} FROM messages").fetchall()) == messages_before, "migration lost or changed messages"
assert sorted(v15_db.execute("SELECT id, is_mms, deleted_ts FROM recycle_bin_messages").fetchall()) == [(1, 0, 11), (2, 1, 12), (77, 0, 13)]
assert v15_db.execute("SELECT COUNT(*) FROM spam_messages").fetchone()[0] == 1
assert v15_db.execute("SELECT body FROM drafts").fetchone()[0] == "draft"
v16_db = build(16)
for table in ("messages", "recycle_bin_messages"):
    assert shape_of(v15_db, table) == shape_of(v16_db, table), f"migrated {table} differs from the v16 schema"
cache(v15_db, 1, 10, 1, "mms sharing an id")
assert v15_db.execute("SELECT COUNT(*) FROM messages WHERE id = 1").fetchone()[0] == 2
print("PASS: migration 15→16 keys messages and the recycle bin on id and transport, keeping every row")
