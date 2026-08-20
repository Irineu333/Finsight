# On Linux, the desktop build writes the archive into a world-readable `/tmp`

**Severity:** medium-low — the whole ledger readable by any local user, on one of the
packaged targets

## What

`JvmBackupFileService.createPrivateFile`
(`feature/backup/impl/src/jvmMain/kotlin/com/neoutils/finsight/backup/service/JvmBackupFileService.kt:104-108`)
builds both temporaries with `java.io.File`:

```kotlin
val directory = File(System.getProperty("java.io.tmpdir"), PRIVATE_DIRECTORY).apply { mkdirs() }
return File.createTempFile(CANDIDATE_PREFIX, CANDIDATE_SUFFIX, directory)
```

`File.createTempFile` and `mkdirs` create with the process umask — measured on this machine,
`rwxr-xr-x` for the directory and `rw-r--r--` for the file. `java.nio.file.Files.createTempFile`
and `createTempDirectory` create with `0600`/`0700` instead, which is precisely the difference
they exist for.

On macOS `java.io.tmpdir` is per-user and the point is moot. On Linux it is `/tmp`, shared by
every account on the machine — and `app/desktop/build.gradle.kts:41` packages
`TargetFormat.Deb` alongside Dmg, Exe and Msi.

Both directions pass through here: the capture (`newCapturePath`, `:63`) and the candidate
(`copyInChosenFile`, `:37`).

## What it costs

The user's complete financial archive, in the clear, readable by any local account. The
capture lives only until the save dialog is answered; the candidate lives for as long as the
confirmation sheet stays open, which is however long the person takes to decide.

A secondary sharp edge: `mkdirs()` succeeds silently over a `/tmp/finsight-backup` that
another user created first, with whatever permissions they chose.

## The fix

`Files.createTempDirectory` and `Files.createTempFile`, which set `0700`/`0600` on POSIX and
are no worse anywhere else.
