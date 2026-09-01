package com.neoutils.finsight.domain.ledger

/**
 * Marks the one value that silences [TransactionRemovalPrelude].
 *
 * Withholding is legitimate and rare, and this marker is what keeps it rare: writing
 * [RemovalAnnouncement.Withheld] is refused by the compiler unless the call site also
 * writes `@OptIn` for it. Two statements instead of one, the second of them visible above
 * the code that removes — which is not something anybody produces by absent-mindedness.
 * Announcing asks for neither, and so does saying nothing at all.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Withholding the announcement silences whoever asked to act while the rows " +
        "are still there. Either announce, or opt in and say at the call site why this " +
        "removal has already answered the prelude on its own terms.",
)
@Retention(AnnotationRetention.BINARY)
annotation class WithheldAnnouncement

/**
 * Whether a removal announces itself to [TransactionRemovalPrelude].
 *
 * This is the single point where a caller of `ITransactionRepository` has a voice about
 * that port, and its voice is narrow on purpose: it says **whether** the announcement is
 * made, never *what it means*, which stays the prelude's. The ledger still learns nothing
 * about why anyone would withhold one.
 *
 * Silence is [Announced]: every removal that does not carry this argument speaks. The
 * asymmetry is the consequence of each mistake — an announcement made where none was
 * wanted costs a listener some work it did not need, while one withheld by forgetting is
 * a listener that never hears, at exactly the moment it was there for.
 *
 * A `Boolean` is rejected for the same reason a plain string is rejected for a dimension
 * kind: `false` is expressible by accident, says nothing at the call site about which of
 * the two removals it asked for, and leaves "withholding is deliberate" resting on whoever
 * reviews the diff. Here the value has a name and [WithheldAnnouncement] makes the
 * compiler ask for it a second time.
 */
sealed interface RemovalAnnouncement {

    /** Spoken, as it is wherever this is not said otherwise. */
    data object Announced : RemovalAnnouncement

    /**
     * Not spoken — the caller has settled with that listener on its own terms, either by
     * having already put to somebody the question the listener would have raised, or by
     * knowing this removal is not one the listener is there for. Both are the same thing
     * the port cannot know for itself.
     */
    @WithheldAnnouncement
    data object Withheld : RemovalAnnouncement
}
