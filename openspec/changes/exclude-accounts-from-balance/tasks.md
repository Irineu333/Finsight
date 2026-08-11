# Tasks

## 1. The ledger read admits a set of accounts to exclude

- [ ] 1.1 `EntryDao.balanceUpToMonthByType` gains `excludedAccountIds` in the **same** `@Query`
      (`AND e.accountId NOT IN (:excludedAccountIds)`), keeping the `GROUP BY e.currency`.
- [ ] 1.2 `IEntryRepository.balanceUpToByCurrency` / `naturalBalanceUpToByCurrency` gain the
      parameter with an empty default, documented as identities and never a motive.
- [ ] 1.3 `EntryRepository` passes the set through to the DAO, without a branch for the empty set.
- [ ] 1.4 `CalculateBalanceUseCase.invoke(target)` propagates the parameter with an empty default;
      `forAccount` is untouched.
- [ ] 1.5 Every `IEntryRepository` stub in the suite follows the new signature.

## 2. The widget gets a configurable perimeter

- [ ] 2.1 `TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS`, in the same format `AccountsOverviewConfig`
      uses, plus a single parsing helper so the comma-separated id set is read in one place.
- [ ] 2.2 `DashboardComponentType.TOTAL_BALANCE.defaultConfig` declares the key with an empty value.
- [ ] 2.3 `DashboardComponentsBuilder.totalBalance` receives the `config` it ignores today and
      hands the excluded set to the read.
- [ ] 2.4 `DashboardComponentOptionsModal` gains a `TOTAL_BALANCE` content branch listing the
      accounts with a toggle each.
- [ ] 2.5 A new string key for the section label, in `values` (pt) and `values-en` (en).

## 3. Tests

- [ ] 3.1 DAO/repository: the empty set is byte-for-byte the read of today; an excluded account's
      entries do not participate; an orphan id excludes nothing; the per-currency grouping survives.
- [ ] 3.2 Dashboard builder: the widget honours the configured exclusion, and every account
      excluded yields zero rather than a hidden widget.

## 4. Verification

- [ ] 4.1 `./gradlew allTests` green, output read.
