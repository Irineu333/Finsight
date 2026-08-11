# Tasks

## 1. The ledger read admits a set of accounts to exclude

- [x] 1.1 `EntryDao.balanceUpToMonthByType` gains `excludedAccountIds` in the **same** `@Query`
      (`AND e.accountId NOT IN (:excludedAccountIds)`), keeping the `GROUP BY e.currency`.
- [x] 1.2 `IEntryRepository.balanceUpToByCurrency` / `naturalBalanceUpToByCurrency` gain the
      parameter with an empty default, documented as identities and never a motive.
- [x] 1.3 `EntryRepository` passes the set through to the DAO, without a branch for the empty set.
- [x] 1.4 `CalculateBalanceUseCase.invoke(target)` propagates the parameter with an empty default;
      `forAccount` is untouched.
- [x] 1.5 Every `IEntryRepository` stub in the suite follows the new signature.

## 2. The widget gets a configurable perimeter

- [x] 2.1 `TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS`, in the same format `AccountsOverviewConfig`
      uses, plus a single parsing helper so the comma-separated id set is read in one place.
- [x] 2.2 `DashboardComponentType.TOTAL_BALANCE.defaultConfig` declares the key with an empty value.
- [x] 2.3 `DashboardComponentsBuilder.totalBalance` receives the `config` it ignores today and
      hands the excluded set to the read.
- [x] 2.4 `DashboardComponentOptionsModal` gains a `TOTAL_BALANCE` content branch listing the
      accounts with a toggle each.
- [x] 2.5 A new string key for the section label, in `values` (pt) and `values-en` (en).

## 3. Tests

- [x] 3.1 DAO/repository: the empty set is byte-for-byte the read of today; an excluded account's
      entries do not participate; an orphan id excludes nothing; the per-currency grouping survives.
- [x] 3.2 Dashboard builder: the widget honours the configured exclusion, and every account
      excluded yields zero rather than a hidden widget.

## 4. Verification

- [x] 4.1 `./gradlew testDebugUnitTest jvmTest --continue` green, output read. `allTests` also
      links the Kotlin/Native test binaries, which fail on this machine with
      `ld: framework 'FirebaseCore' not found` — reproduced identically on a stashed clean
      tree, so it is the environment (iOS pods absent) and not this change.
