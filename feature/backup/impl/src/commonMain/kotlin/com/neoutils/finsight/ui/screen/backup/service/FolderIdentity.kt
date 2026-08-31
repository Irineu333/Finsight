package com.neoutils.finsight.ui.screen.backup.service

/**
 * Which physical folder [BackupFolder] is pointed at, right now — the thing the vault was
 * missing wherever it reasoned about [com.neoutils.finsight.domain.vault.VaultDestination]
 * alone. Two folders are the same place exactly when this is equal; nothing else about it
 * means anything, and nothing here reconstructs the folder it names.
 *
 * **It is a fingerprint of the token each platform already remembers, and never the token
 * itself** (design D2): a tree `Uri`'s text on Android, a path's text on the desktop, a
 * bookmark's bytes on iOS. [equals] is everything this type is for, so a one-way digest
 * answers it exactly as well as the token would while a caller holding one learns nothing
 * that could open the folder — which a caller here can do, because unlike [BackupFolder]
 * itself, comparing *which* folder is not confined to one platform module.
 *
 * **iOS is the platform this is honest about.** A bookmark can be rewritten for a folder
 * that has not moved — [BackupFolder]'s own iOS implementation does exactly that when a
 * resolution comes back stale — so two fingerprints taken of the *same* folder on two
 * different occasions are not guaranteed equal there the way they are on the other two
 * platforms, where the remembered token never changes on its own. Nothing here can make
 * that stronger than the bookmark itself is, which is why the one comparison this type is
 * used for ([com.neoutils.finsight.domain.vault.VaultFolder.pointAt]) is taken across a
 * single call, before and after one explicit choice, and never across a reading — such as
 * [BackupFolder.check] — where a silent rewrite could be mistaken for a person's choice.
 */
data class FolderIdentity(private val fingerprint: String)

/**
 * Fingerprints a platform's own token — a tree `Uri`'s text, a path's text — into a
 * [FolderIdentity] nothing could turn back into that text.
 */
fun folderIdentity(token: String): FolderIdentity = FolderIdentity(fnv1a(token.encodeToByteArray()))

/** The same, for a token that is already bytes — a bookmark's, on iOS. */
fun folderIdentity(bytes: ByteArray): FolderIdentity = FolderIdentity(fnv1a(bytes))

/**
 * FNV-1a over [bytes], as a lowercase hex string.
 *
 * Not a cryptographic hash and not asked to be one: nothing here defends against somebody
 * deliberately engineering a collision, only against a caller reading a path back out of
 * what this hands them — which a one-way digest of any kind already rules out, and this one
 * costs nothing to compute on every platform this module ships to, in pure Kotlin.
 */
private fun fnv1a(bytes: ByteArray): String {
    var hash = FNV_OFFSET_BASIS
    for (byte in bytes) {
        hash = hash xor (byte.toLong() and BYTE_MASK)
        hash *= FNV_PRIME
    }
    return hash.toULong().toString(HEX_RADIX)
}

private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL // 0xcbf29ce484222325, as a signed Long
private const val FNV_PRIME = 0x100000001b3L
private const val BYTE_MASK = 0xffL
private const val HEX_RADIX = 16
