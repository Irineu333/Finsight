> Ordem: primeiro os guards e o caminho de domínio (prováveis e testáveis antes de qualquer UI), depois a apresentação (wrapper só-conta), o modal de detalhe, a tela de arquivadas e a entrada; testes e validação por último.
>
> Verificado contra o código por três auditorias independentes (domínio+apresentação, UI+navegação, riscos+DI). Correções já incorporadas: erro novo usa `UiText.Res` traduzido (o irmão `CANNOT_DELETE_DEFAULT` usa `Raw`, inconsistente); a oferta da retirada é um wrapper **só-conta** (`AccountRetireOffer`), deixando `RetireAction`/`retireActionOf`, cartão e categoria **intocados**; `UnarchiveAccountUseCase` é classe concreta única no impl (padrão dos dois desarquivares), sem interface no api; DI num só `accountsModule`; o modal espelha **só** `ViewCreditCardModal`; são **7** fakes de `IAccountRepository` a patchar (4 fora de accounts).
>
> `AccountDao.reopen(id)` já existe (`AccountDao.kt:79`, adicionado pelo desarquivar de cartão) — nada em `core/ledger`. `RecordingAccountDao` já implementa `reopen`. `observeAllAccountsIncludingClosed()` e `Account.isArchived` já existem.

## 1. Guard — arquivar a conta padrão é recusado (D2)

- [x] 1.1 `core/model` `AccountError.kt`: adicionar `CANNOT_ARCHIVE_DEFAULT(message = "...")` ao lado de `CANNOT_DELETE_DEFAULT` (`:17`) e um branch em `toUiText()` (`:50`) usando **`UiText.Res(Res.string.account_error_cannot_archive_default)`** — traduzido, não `UiText.Raw` (o padrão bom, seguido por todos os erros menos o irmão). String em 1.2/7.1.
- [x] 1.2 `ArchiveAccountUseCaseImpl` (`:28`): no topo do `invoke`, `if (account.isDefault) return AccountException(AccountError.CANNOT_ARCHIVE_DEFAULT).left()`, **antes** do guard de saldo (posição idêntica ao guard de `isDefault` em `DeleteAccountUseCaseImpl.kt:20`). KDoc: vale só para conta, pois a `LIABILITY` de cartão nunca é padrão.

## 2. Apresentação — oferta de retirada só-conta (D3)

- [x] 2.1 `core/ui`: criar `AccountRetireOffer` — `sealed interface { data class Retire(val action: RetireAction); data object UnavailableDefault }` — e `accountRetireOfferOf(hasMovement: Boolean, isDefault: Boolean): AccountRetireOffer` = `if (isDefault) UnavailableDefault else Retire(retireActionOf(hasMovement))`. **Não tocar** `RetireAction` nem `retireActionOf`.
- [x] 2.2 `AccountUi` (`AccountUi.kt`): adicionar `val isDefault: Boolean = false`; trocar `retireAction get() = retireActionOf(hasMovement)` por `retireOffer get() = accountRetireOfferOf(hasMovement, isDefault)`.
- [x] 2.3 `AccountsViewModel` (`:103`, onde `hasMovement` já é calculado): alimentar `isDefault = account.isDefault` na construção do `AccountUi` (o domínio `Account` já tem `isDefault`).

## 3. Dados e domínio — desarquivar conta (D4)

- [x] 3.1 `IAccountRepository` (accounts/api): adicionar `suspend fun reopen(accountId: Long)` com KDoc "inverso de `close`; só reabre o flag, sem tocar entries".
- [x] 3.2 `AccountRepository` (impl): `override suspend fun reopen(accountId: Long) = dao.reopen(accountId)`, ao lado de `delete`/`update`.
- [x] 3.3 Criar `UnarchiveAccountUseCase` como **classe concreta única no impl** (sem interface no api — padrão de `UnarchiveCreditCardUseCase`/`UnarchiveCategoryUseCase`): `class UnarchiveAccountUseCase(private val repository: IAccountRepository) { suspend operator fun invoke(account: Account): Either<Throwable, Unit> = catch { repository.reopen(account.id) } }`. KDoc: reversível e inócuo; sem guard, sem confirmação; volta comum, nunca padrão.
- [x] 3.4 Patchar os **7** fakes de `IAccountRepository` (adicionar `override suspend fun reopen(accountId: Long) { }` ou registro conforme o fake): `RecordingAccountRepository` (`RetireAccountGuardsTest.kt:168`), `FakeAccountRepository` (`ArchiveCreditCardUseCaseTest.kt:66`), `LedgerAccountRepository` (`InvoiceWriteGuardTest.kt:354`), `FakeAccountRepository` (`TransactionRepositoryEntriesTest.kt:254`), `FakeAccountRepository` (`CalculateReportStatsUseCaseTest.kt:104`), `object : IAccountRepository` (`ReportViewerViewModelCharacterizationTest.kt:227`), `object : IAccountRepository` (`RecurringRepositoryTest.kt:59`). 4 fora do módulo accounts.
- [x] 3.5 Registrar `UnarchiveAccountUseCase` no **único** `AccountsModule.kt` (`factory { UnarchiveAccountUseCase(get()) }`), ao lado do `factory<ArchiveAccountUseCase>` (`:61`).

## 4. Modal de detalhe da conta + Desarquivar (D7) — archived-only

- [x] 4.1 Espelhando os 5 arquivos de `ui/modal/viewCreditCard/` (**não** `viewCategory`, que não é archived-only): criar `ViewAccountUiState` (Loading/Error/Content(account)), `ViewAccountAction` (`data object Unarchive`), `ViewAccountEvent` (`data object Dismiss`).
- [x] 4.2 Criar `ViewAccountViewModel(accountId, accountRepository, unarchiveAccount, crashlytics)`: observa `observeAccountById(accountId)` com `interceptAbsence`; `onAction(Unarchive)` chama o use case, `onLeft { crashlytics.recordException(it) }` e emite `Event.Dismiss` no `onRight` (o dismiss vem daqui, não do `onDisappeared` — a conta não some no `reopen`).
- [x] 4.3 Criar `ViewAccountModal(accountId) : AdaptiveModal`: resolve o VM via `koinViewModel { parametersOf(accountId) }`, coleta `uiState` uma vez, coleta `events` num `LaunchedEffect` → `detailController.dismiss()`. `DetailContent()`: identidade (ícone `AppIcon.fromKey` + nome) e tipo; sem saldo. `DetailActions()`: só `OutlinedActionButton` **Desarquivar**. Sem confirmação.
- [x] 4.4 Registrar `ViewAccountViewModel` no `AccountsModule.kt` (`viewModel { }`, após `:133`).

## 5. Tela de contas arquivadas (D6)

- [x] 5.1 Criar `ArchivedAccountUi` (plano: id, nome, iconKey, tipo) e mapper `Account.toArchivedUi()` (o domínio não cruza a fronteira ViewModel → UI), espelhando `ArchivedCreditCardUi.kt`.
- [x] 5.2 Criar `ArchivedAccountsUiState` (Loading/Empty/Content(list)) e `ArchivedAccountsViewModel(accountRepository)`: `observeAllAccountsIncludingClosed().map { it.filter(Account::isArchived) }` → UiState, espelhando `ArchivedCreditCardsViewModel.kt:16`.
- [x] 5.3 Criar `ArchivedAccountCard` — ícone + nome + indicação **textual** de arquivada (`Icons.Default.Archive` + string própria; cor/alpha não pode ser o único diferenciador, paridade com `ArchivedCreditCardCard.kt:76` / `category_card_archived`).
- [x] 5.4 Criar `ArchivedAccountsScreen(onNavigateBack)`: `Scaffold` + `TopAppBar` (voltar → `navigateUp`, `containerColor = colorScheme.background` **opaca**, como o precedente — não "transparente"); `LazyColumn` de `ArchivedAccountCard`; linha toca → `detailController.show(ViewAccountModal(account.id))`; Empty-state discreto quando não há arquivadas.
- [x] 5.5 `ArchivedAccountsRoute` (`@Serializable data object`, implementa `NavRoute`) **no impl** (`AccountsGraph.kt`) — destino interno ao feature.
- [x] 5.6 `NavGraphBuilder.accountsGraph()`: adicionar `composable<ArchivedAccountsRoute>` dentro do `navigation<AccountsGraph>` existente, `onNavigateBack = navController::navigateUp`. Importar `LocalNavController` (ainda não importado em `AccountsGraph.kt`).
- [x] 5.7 Registrar `ArchivedAccountsViewModel` no `AccountsModule.kt` (`viewModel { }`).

## 6. Entrada na tela de contas (D6)

- [x] 6.1 `AccountsScreen`/`AccountsContent`: no slot `actions` (que já contém `MonthSelector`, `:144`), adicionar **ao lado** um `IconButton` de overflow (`⋮`, `Icons.Default.MoreVert`) + `DropdownMenu`/`DropdownMenuItem` "Arquivadas" → `navController.navigate(ArchivedAccountsRoute)`. Hospedar `var expanded by remember { mutableStateOf(false) }` no lambda `actions`. Adicionar `LocalNavController.current` (ainda não usado em `AccountsContent`). Não altera o pager nem o `MonthSelector`.
- [x] 6.2 `AccountsScreen` `AccountActions` (`:361`): remover o `enabled = !account.isDefault` (`:371`) e o `when(retireAction)` (`:374`); passar a `when(account.retireOffer)`: `Retire(action)` → `OutlinedActionButton` com `action.label`/`.icon` + modal Delete/Archive; `UnavailableDefault` → orientação (string) no lugar do botão. Cuidar da degradação do `Row` de 2 botões `weight(1f)` (retirar+editar) quando o slot vira texto.

## 7. Strings

- [x] 7.1 `core/resources` (`values/` pt e `values-en/`): `account_error_cannot_archive_default`, `accounts_archived_title`, `accounts_archived_empty`, `accounts_view_archived` (overflow), `account_unarchive` (botão), `account_archived` (indicador do card), `retire_action_unavailable_default` (orientação — lê como orientação, "escolha outra conta como padrão antes", não rótulo de botão), e **content-descriptions** do `⋮` e do voltar (paridade de acessibilidade com o cartão; evitar lint de string hardcoded). Aguardar a geração dos acessores de `Res`.

## 8. Testes

- [x] 8.1 `ArchiveAccountUseCase` (novo caso em `RetireAccountGuardsTest`, espelhando "deleting the default account is refused", `:52`): arquivar a conta padrão retorna `Left(AccountException(CANNOT_ARCHIVE_DEFAULT))` e não escreve; conta não-padrão zerada arquiva normalmente (regressão). Patchar `RecordingAccountRepository` aqui (3.4).
- [x] 8.2 `accountRetireOfferOf` (novo teste): `isDefault = true` → `UnavailableDefault`; `isDefault = false` → `Retire(ARCHIVE|DELETE)` conforme `hasMovement`. `RetireActionTest` existente **não muda** (`retireActionOf` intocado).
- [x] 8.3 `UnarchiveAccountUseCase`: `invoke(account)` chama `repository.reopen(account.id)` e retorna `Right(Unit)`.
- [x] 8.4 `AccountRepository.reopen`: chama `AccountDao.reopen(accountId)` com o id recebido (fake DAO, verify).
- [x] 8.5 `ArchivedAccountsViewModel`: lista só contas arquivadas; Empty quando não há nenhuma.
- [x] 8.6 `ViewAccountViewModel`: ação `Unarchive` invoca o use case e emite `Dismiss` no sucesso.

## 9. Validação

- [x] 9.1 `openspec validate unarchive-accounts --strict`.
- [x] 9.2 `./gradlew :app:shared:testDebugUnitTest` verde (inclui os 7 fakes patchados de 3.4, 4 fora de accounts).
- [x] 9.3 Conferir na tela: eleger outra padrão → arquivar a antiga → some da tela ativa → overflow "Arquivadas" → abrir conta → Desarquivar → reaparece nas contas e nos seletores; a padrão vigente não oferece retirar (botão desabilitado), e o domínio recusaria com `CANNOT_ARCHIVE_DEFAULT`.

## 10. Ajuste pós-implementação (UX da conta padrão)

- [x] 10.1 Feedback do usuário: o parágrafo de orientação (`retire_action_unavailable_default`) embaixo do card, na conta padrão inicialmente selecionada, ficou mal posicionado. Voltar ao padrão anterior: **exibir o botão de retirar desabilitado** (como o antigo `enabled = !isDefault`), em vez do texto de orientação.
- [x] 10.2 `AccountRetireOffer.UnavailableDefault` passa a carregar a `RetireAction` (rótulo/ícone para o botão desabilitado); `RetireAction`/`retireActionOf` e as telas de cartão/categoria seguem intocados. `AccountActions` volta ao `Row` de dois botões `weight(1f)`, com o retirar `enabled = retireOffer is Retire`.
- [x] 10.3 Remover a string órfã `retire_action_unavailable_default` (pt/en) e o import. Atualizar `AccountRetireOfferTest`. `:core:ui:jvmTest` e `:feature:accounts:impl:jvmTest` verdes; `:feature:accounts:impl:compileDebugKotlinAndroid` ok.
- Nota: a orientação proativa na tela sai; a proteção segue no domínio (`CANNOT_ARCHIVE_DEFAULT`) e o status é comunicado pelo badge "Padrão" + botão desabilitado. A frase do spec sobre "apresentar a orientação" pode ser suavizada antes de arquivar (pendente de decisão).
