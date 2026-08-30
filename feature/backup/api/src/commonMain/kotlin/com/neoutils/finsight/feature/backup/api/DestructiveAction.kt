package com.neoutils.finsight.feature.backup.api

/**
 * Everything this app offers that destroys something, and the class each one falls in.
 *
 * **The classification is the whole of the preventive trigger's rule, and this is its one
 * owner** (design D7). The vault has a switch and nothing else: a screen decides *whether*
 * the rule applies, never *which* actions are worth a copy, so there is no setting anywhere
 * that takes an action out of a class or a class out of the coverage below.
 *
 * The inventory is deliberately complete rather than a list of the covered six. An action
 * that is not protected is written down as not protected, with the reason in its class, so
 * that "nobody classified it" and "it needs no copy" stop looking alike. A destructive
 * action added to the app is added here, and it is protected — or knowingly not — by the
 * class it is given, with no screen and no caller touched.
 *
 * A caller names the action it is about to perform and hands it to [PreventiveBackup]. It
 * does not read [classification] to decide anything: deciding is what it is asking for.
 */
enum class DestructiveAction(val classification: DestructiveClass) {

    /** The archive is replaced by a file's content, so everything in it now goes. */
    RESTORE_BACKUP(DestructiveClass.TYPED_WORK),

    /** A transaction and the entries it is made of. */
    DELETE_TRANSACTION(DestructiveClass.TYPED_WORK),

    /** An installment, and every transaction that carries a part of it. */
    DELETE_INSTALLMENT(DestructiveClass.TYPED_WORK),

    /** An invoice, and the real transactions on it when it is removed retroactively. */
    DELETE_INVOICE(DestructiveClass.TYPED_WORK),

    /** A currency, and every rate observation naming it on either end. */
    DELETE_CURRENCY(DestructiveClass.TYPED_WORK),

    /** One rate observation, which may be the correction that outranked a wrong one. */
    REMOVE_EXCHANGE_RATE(DestructiveClass.TYPED_WORK),

    DELETE_ACCOUNT(DestructiveClass.GUARDED_FACADE),

    DELETE_CREDIT_CARD(DestructiveClass.GUARDED_FACADE),

    DELETE_CATEGORY(DestructiveClass.GUARDED_FACADE),

    DELETE_BUDGET(DestructiveClass.GUARDED_FACADE),

    DELETE_RECURRING(DestructiveClass.GUARDED_FACADE),

    /**
     * A transaction rewritten in place: the entries it had are deleted and written again.
     */
    EDIT_TRANSACTION(DestructiveClass.ROUTINE_REWRITE),

    /**
     * An adjustment brought back to nothing, which removes the transaction that held it —
     * the size it had is read back out of its own ledger leg, and is derived again the
     * moment somebody adjusts the same balance once more.
     */
    CLEAR_ADJUSTMENT(DestructiveClass.DERIVED_VALUE),
}

/**
 * The classes destructive actions fall in, and which of them are worth a copy taken first.
 *
 * Coverage is a property of the class and not of the action, which is what makes a new
 * action inherit its protection from what it destroys instead of from somebody remembering
 * to register it (design D7).
 */
enum class DestructiveClass(val isCoveredByPreventiveCapture: Boolean) {

    /**
     * It removes what somebody typed, and getting it back means typing it again. This is
     * the class the preventive trigger exists for.
     */
    TYPED_WORK(isCoveredByPreventiveCapture = true),

    /**
     * A facade whose deletion the domain already refuses whenever typed work would go with
     * it — an account with entries, a category with a transaction, a budget, a recurring,
     * a card. What is left to delete is the empty shell, and a copy of the archive without
     * it protects nobody.
     */
    GUARDED_FACADE(isCoveredByPreventiveCapture = false),

    /**
     * It rewrites typed work as the ordinary way of correcting it. Editing is how this app
     * is used, and a copy per edit would be a copy per use — the cost falls on everybody
     * while the benefit is the rare edit somebody regrets.
     */
    ROUTINE_REWRITE(isCoveredByPreventiveCapture = false),

    /**
     * What disappears is derived from what stays, so the archive still holds it. There is
     * nothing in a copy that the live archive could not answer.
     */
    DERIVED_VALUE(isCoveredByPreventiveCapture = false),
}
