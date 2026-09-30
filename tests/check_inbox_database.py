"""Run with python3 tests/check_inbox_database.py; synthetic data only."""
import json
import re
import sqlite3
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / "app/src/main/kotlin/org/fossify/messages"
version = int(re.search(r"version = (\d+)", (source / "databases/MessagesDatabase.kt").read_text())[1])
schema = json.loads((ROOT / f"app/schemas/org.fossify.messages.databases.MessagesDatabase/{version}.json").read_text())["database"]
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
    rows = [dict(defaults, **values(i)) for i in range(1, count + 1)]
    db.executemany(f"INSERT INTO {table} ({columns}) VALUES ({','.join('?' for _ in fields)})",
                   [tuple(row[f["columnName"]] for f in fields) for row in rows])

insert_rows("conversations", 1500, lambda i: {"thread_id": i, "snippet": "provider fallback"})
insert_rows("messages", 30000, lambda i: {"id": i, "thread_id": (i - 1) % 1500 + 1, "date": i, "body": str(i)})
db.execute("INSERT INTO recycle_bin_messages (id, deleted_ts) VALUES (30000, 1)")
dao_source = (source / "interfaces/ConversationsDao.kt").read_text()

def resolve_query_constants(sql):
    """Expand the small SQL constants used by the shipped Room DAO query."""
    def replace(match):
        name = match[1]
        declaration = re.search(rf"private const val {name} = (.*?)(?:\n\n|\n@Dao)", dao_source, re.S)
        assert declaration, f"Missing DAO SQL constant: {name}"
        return resolve_query_constants("".join(re.findall(r'"([^"]*)"', declaration[1])))

    return re.sub(r"\$(\w+)", replace, sql)

queries = re.findall(r'@Query\("(SELECT .*?)"\)', dao_source)
query = resolve_query_constants(next(q for q in queries if "FROM conversations WHERE archived = 0" in q))
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
print("PASS: indexed inbox lookup and recycle-bin exclusion")

# Exercise the shipped migration twice; adding an index must preserve every row.
before = {e["tableName"]: db.execute(f'SELECT * FROM "{e["tableName"]}" ORDER BY rowid').fetchall() for e in schema["entities"]}
db.execute("DROP INDEX index_messages_thread_id_date")
migration_source = (source / "databases/MessagesDatabase.kt").read_text()
migration = re.search(r'private val MIGRATION_11_12.*?db.execSQL\("(.*?)"\)', migration_source, re.S)[1]
for _ in range(2):
    db.execute(migration)
for table, expected in before.items():
    assert db.execute(f'SELECT * FROM "{table}" ORDER BY rowid').fetchall() == expected
print("PASS: migration preserves all rows and can be retried")

# Run the actual queries used by Starred and message search against a recycled hit.
dao = (source / "interfaces/MessagesDao.kt").read_text()
for method, params in [("getMessagesWithIds", {"ids": 30000}), ("getMessagesWithText", {"text": "30000"})]:
    query = re.search(r'@Query\("([^"\n]+)"\)\s+fun ' + method + r'\(', dao)[1]
    assert not db.execute(query, params).fetchall(), f"{method} exposes recycled messages"
    active = {"ids": 29999} if method == "getMessagesWithIds" else {"text": "29999"}
    assert len(db.execute(query, active).fetchall()) == 1, f"{method} hides active messages"
print("PASS: starred and search results exclude recycled messages")

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

# Exercise the shipped literal Persian/Arabic search SQL with synthetic messages.
thread_query = re.search(r'@Query\("""(SELECT messages\.\* FROM messages.*?)"""\)\s+fun searchThreadMessages', dao, re.S)[1]
db.execute("UPDATE messages SET body = 'كي 100%_\\ twice کی' WHERE id IN (1, 2, 30000)")
db.execute("UPDATE messages SET body = 'کی 100XX twice کی' WHERE id = 1501")
params = {"threadId": 1, "pattern": r"%کی 100\%\_\\%"}
hits = db.execute(thread_query, params).fetchall()
assert len(hits) == 1 and hits[0][0] == 1, "Search must normalize letters, escape wildcards and stay in its thread"
assert not db.execute(thread_query, dict(params, threadId=1500)).fetchall(), "Search exposes recycled text"
print("PASS: literal Persian/Arabic thread search and recycle-bin exclusion")
