---
name: room-database
description: >
  Room database expert for KMP. Always trigger when writing or reviewing Room entities, DAOs,
  migrations, type converters, or repository implementations. Enforces critical correctness
  rules that AI models commonly get wrong: foreign key enforcement, N+1 with @Relation,
  Flow re-emission, @Transaction scope, cascade strategies, index placement, and migration safety.
user-invocable: false
---

# Room Database — Critical Correctness Rules

This skill exists to prevent the mistakes AI models make most often with Room.
These are not style preferences — they are correctness and data integrity issues.

---

## 1. PRAGMA foreign_keys = ON — Always

**SQLite disables foreign key enforcement by default.** Without this, `@ForeignKey` constraints
are defined but never enforced. Inserts and deletes violate referential integrity silently.

```kotlin
// WRONG — FK constraints exist but are never enforced
Room.databaseBuilder(context, AppDatabase::class.java, "db").build()

// CORRECT — enable on every connection open
fun buildDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        })
        .build()
}
```

---

## 2. @Relation always requires @Transaction

**Without `@Transaction`, Room executes 1 + N queries** — one for the parent, one per row
for each child collection. With `@Transaction`, Room batches children into a single IN query.

```kotlin
// WRONG — N+1 queries
@Query("SELECT * FROM accounts")
suspend fun getAccountsWithTransactions(): List<AccountWithTransactions>

// CORRECT — 2 queries total
@Transaction
@Query("SELECT * FROM accounts")
suspend fun getAccountsWithTransactions(): List<AccountWithTransactions>
```

This applies to every DAO method that returns a type containing `@Relation` fields — no exceptions.

### Alternative: multimap return types (Room 2.4+)

For performance-critical queries, prefer a single JOIN over `@Relation`. Room 2.4+ supports
multimap return types that execute one SQL query instead of the parent + batched-child pattern:

```kotlin
// Single JOIN query — no intermediate POJO required
@Query("SELECT * FROM accounts JOIN transactions ON accounts.id = transactions.account_id")
fun observeAccountsWithTransactions(): Flow<Map<AccountEntity, List<TransactionEntity>>>
```

Use `@Relation` when the intermediate POJO is needed elsewhere. Use multimap when you only
need the data and want the best read performance.

---

## 3. Flow needs distinctUntilChanged()

**Room re-emits on any write to the observed table**, even when the result set didn't change.
Without `distinctUntilChanged()`, collectors recompose/reprocess on unrelated writes.

```kotlin
// WRONG — re-emits on every table write, even unrelated ones
fun observeTransactions(accountId: Long): Flow<List<Transaction>> =
    dao.observeByAccount(accountId)

// CORRECT
fun observeTransactions(accountId: Long): Flow<List<Transaction>> =
    dao.observeByAccount(accountId).distinctUntilChanged()
```

Apply in the repository implementation, not in the DAO.

---

## 4. Never use OnConflictStrategy.REPLACE on parents with CASCADE children

`OnConflictStrategy.REPLACE` is implemented as `DELETE` + `INSERT` at the SQLite level.
If the parent has `onDelete = ForeignKey.CASCADE`, **every "upsert" silently deletes all
children** before re-inserting the parent. This passes initial testing because new inserts
have no children yet — the bug only manifests after the relationship is established.

```kotlin
// WRONG — CASCADE fires on the internal DELETE, destroying all children
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertAccount(account: AccountEntity)

// CORRECT — Room 2.5+: generates INSERT ... ON CONFLICT(pk) DO UPDATE SET ...
// No DELETE occurs, no cascade fires
@Upsert
suspend fun upsertAccount(account: AccountEntity)

// CORRECT for older Room — explicit SQL upsert
@Query("""
    INSERT INTO accounts (id, name, balance)
    VALUES (:id, :name, :balance)
    ON CONFLICT(id) DO UPDATE SET name=excluded.name, balance=excluded.balance
""")
suspend fun upsertAccount(id: Long, name: String, balance: Double)
```

---

## 5. @ForeignKey cascade strategy is not one-size-fits-all

Choose based on what the child record means without its parent:

| Strategy | When to use |
|----------|-------------|
| `CASCADE` | Child has no meaning without parent (transaction → account) |
| `RESTRICT` | Prevent deletion if children exist (protect important links) |
| `SET_NULL` | Child can exist without parent (optional relationship) |
| `NO_ACTION` | Handled manually in code — rare, avoid |

```kotlin
// Transaction has no meaning without its Account — CASCADE is correct
@Entity(
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["account_id"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
    )],
    indices = [Index("account_id")] // ← always index FK columns
)
data class TransactionEntity(...)
```

---

## 6. Always index foreign key columns

**Room does not add indexes to FK columns automatically.** Without an index, every FK lookup
performs a full table scan.

```kotlin
// WRONG — FK without index = full table scan on every join/delete
@Entity(foreignKeys = [ForeignKey(entity = AccountEntity::class, ...)])
data class TransactionEntity(
    val account_id: Long,
    // no index declared
)

// CORRECT
@Entity(
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"], ...)],
    indices = [Index("account_id")]
)
data class TransactionEntity(val account_id: Long)
```

---

## 7. @Transaction on Flow has no atomicity benefit

`@Transaction` guarantees atomicity for `suspend` functions. For `Flow`, it only prevents
intermediate reads during the initial emission — subsequent re-emissions are not covered.
Don't rely on it for Flow consistency.

```kotlin
// This @Transaction does NOT protect against concurrent modifications between re-emissions
@Transaction
@Query("SELECT * FROM accounts")
fun observeAccountsWithTransactions(): Flow<List<AccountWithTransactions>>
// Use for @Relation (N+1 prevention only), not for atomicity in reactive flows
```

---

## 8. Use withTransaction for multi-DAO operations in coroutines

For suspend functions that span multiple DAOs, prefer `withTransaction` over `@Transaction` on
an abstract DAO method. `withTransaction` binds all suspended calls to a single connection and
rolls back automatically on exception **or coroutine cancellation** — the abstract `@Transaction`
method does not guarantee rollback on cancellation.

```kotlin
// WRONG — @Transaction on abstract method does not protect against coroutine cancellation
@Transaction
open suspend fun transfer(from: Long, to: Long, amount: Double) {
    accountDao.debit(from, amount)
    accountDao.credit(to, amount)
}

// CORRECT — withTransaction rolls back on exception and cancellation
suspend fun transfer(from: Long, to: Long, amount: Double) {
    database.withTransaction {
        accountDao.debit(from, amount)
        accountDao.credit(to, amount)
    }
}
```

Also: exceptions caught and **swallowed** inside `withTransaction` cause the transaction to
**commit**, not roll back. Always let exceptions propagate out of the block for rollback.

```kotlin
// WRONG — exception is caught, block completes "successfully", Room commits partial state
database.withTransaction {
    try {
        dao.insertParent(parent)
        dao.insertChildren(children)
    } catch (e: Exception) {
        Log.e(TAG, "failed", e) // commit happens here — data is inconsistent
    }
}

// CORRECT — let the exception propagate; caller handles it
database.withTransaction {
    dao.insertParent(parent)
    dao.insertChildren(children)
}
```

---

## 9. Never use fallbackToDestructiveMigration in production

This silently deletes all user data when no migration path is found. Acceptable only in
development or pre-launch. In production, always provide explicit migrations.

```kotlin
// NEVER in production
.fallbackToDestructiveMigration()

// If you truly must — restrict to specific versions only
.fallbackToDestructiveMigrationFrom(1) // only wipes v1 installs
```

---

## 10. Column rename/drop requires table recreation

SQLite's `ALTER TABLE` only supports `ADD COLUMN` and `RENAME TABLE`. Renaming or dropping
a column requires creating a new table, copying data, and dropping the old one.

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE transactions_new (id INTEGER PRIMARY KEY, amount REAL NOT NULL, account_id INTEGER NOT NULL)")
        db.execSQL("INSERT INTO transactions_new SELECT id, amount, account_id FROM transactions")
        db.execSQL("DROP TABLE transactions")
        db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
    }
}
```

AutoMigration handles `ADD COLUMN` only. Use manual migration for everything else.

---

## 11. @Embedded vs @Relation — choose intentionally

| | @Embedded | @Relation |
|--|-----------|-----------|
| Storage | Same table (denormalized) | Separate table (normalized) |
| Read speed | Fast — no JOIN | Slower — JOIN required |
| Write consistency | Risk of duplication | Single source of truth |
| Use when | Value object, always fetched together, no independent lifecycle | Entity with independent identity, shared across parents |

```kotlin
// @Embedded — correct for a value object (no independent identity)
@Entity
data class TransactionEntity(
    @PrimaryKey val id: Long,
    val amount: Double,
    @Embedded val category: CategorySnapshot, // snapshot, not a live reference
)

// @Relation — correct for a real entity with its own identity
data class AccountWithTransactions(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "account_id")
    val transactions: List<TransactionEntity>,
)
```

---

## 12. Type converters: rules and anti-patterns

### Use String for dates, not Long

Storing `LocalDate` or `LocalDateTime` as `Long` (epoch millis) makes SQL queries on dates
unreadable and error-prone. Use ISO-8601 strings — SQLite's date functions understand them.

```kotlin
// WRONG — opaque, hard to query, timezone-sensitive
@TypeConverter fun fromDate(date: LocalDate?): Long? = date?.toEpochDay()

// CORRECT — human-readable, SQLite-compatible, timezone-safe
@TypeConverter fun fromDate(date: LocalDate?): String? = date?.toString() // "2026-03-03"
@TypeConverter fun toDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }
```

### Never serialize collections as JSON blobs

Storing `List<T>` as a JSON string makes the column completely opaque to SQL. You cannot
`WHERE item IN (jsonColumn)` — any filter on individual elements requires fetching the full
table and deserializing in Kotlin.

```kotlin
// WRONG — column is opaque to SQL; individual elements are unqueryable
@TypeConverter fun fromList(list: List<String>): String = gson.toJson(list)
```

The correct approach is normalization: create a separate entity with a FK to the parent.
Use TypeConverters only for scalar value types with no independent queryability.

### Scope at @Database level

Always declare `@TypeConverters` at the `@Database` class level — not on individual entities
or fields — to ensure consistent conversion across all DAOs and entities.

### Enums do not need type converters

Room stores enums as their `.name` string automatically since Room 2.3. Adding a manual
converter for enums shadows the built-in and can cause inconsistency if the converter format
differs.
