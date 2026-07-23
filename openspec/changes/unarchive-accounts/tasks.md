> Ordem: primeiro os guards e o caminho de domínio (prováveis e testáveis antes de qualquer UI), depois a apresentação (dono da retirada), o modal de detalhe, a tela de arquivadas e a entrada; testes e validação por último.
>
> `AccountDao.reopen(id)` já existe (adicionado pelo desarquivar de cartão) — nada em `core/ledger`. `RecordingAccountDao` já implementa `reopen`.

## 1. Guard — arquivar a conta padrão é recusado (D2)

- [ ] 1.1 `core/model` (erro de conta): adicionar `AccountError.CANNOT_ARCHIVE_DEFAULT` com `message` em inglês e branch em `toUiText()` (string nova `account_error_cannot_archive_default`), simétrico a `CANNOT_DELETE_DEFAULT`.
- [ ] 1.2 `ArchiveAccountUseCaseImpl`: no topo do `invoke`, `if (account.isDefault) return AccountException(AccountError.CANNOT_ARCHIVE_DEFAULT).left()`, **antes** do guard de saldo. KDoc: simétrico ao guard de `DeleteAccountUseCaseImpl`; vale só para conta, pois a `LIABILITY` de cartão nunca é padrão.

## 2. Apresentação — oferta de retirada de três casos (D3)

- [ ] 2.1 `core/ui` `RetireAction`: introduzir o terceiro caso ("retirada indisponível — é a conta padrão"). Preferir um terceiro membro do enum carregando orientação em vez de `label`/`icon` de ação; se sujar os consumidores existentes, envolver num tipo próprio. Não usar `null`.
- [ ] 2.2 `retireActionOf`: nova assinatura `retireActionOf(mustPreserve: Boolean, isDefault: Boolean = false)` — o default `false` mantém `CreditCardUi` e as categorias intactos. Retorna o caso "indisponível" quando `isDefault`.
- [ ] 2.3 `AccountUi.retireAction`: passar `isDefault` (adicionar o campo `isDefault` ao `AccountUi`, alimentado pelo ViewModel a partir do `Account`).
- [ ] 2.4 Corrigir os `when(retireAction)` que deixam de ser exaustivos: `CreditCardsScreen`, `InvoiceTransactionsScreen`, `AccountsScreen`, `ViewCategory*` — o compilador aponta cada um.

## 3. Dados e domínio — desarquivar conta (D4)

- [ ] 3.1 `IAccountRepository` (accounts/api): adicionar `suspend fun reopen(accountId: Long)` com KDoc "inverso de `close`; só reabre o flag, sem tocar entries".
- [ ] 3.2 `AccountRepository` (impl): `reopen(accountId)` → `dao.reopen(accountId)`.
- [ ] 3.3 `UnarchiveAccountUseCase` (api, seguindo o padrão de `ArchiveAccountUseCase`): interface `suspend operator fun invoke(account: Account): Either<Throwable, Unit>`. Impl `UnarchiveAccountUseCaseImpl(repository: IAccountRepository)`: `catch { repository.reopen(account.id) }`. Sem guard, sem confirmação (KDoc: reversível e inócuo; saldo zero garantido pelo arquivar; volta comum, nunca padrão).
- [ ] 3.4 Patchar os fakes de `IAccountRepository` que a nova `reopen` quebra (grep por implementações da interface nos testes).
- [ ] 3.5 Registrar `UnarchiveAccountUseCase` no módulo de use cases de accounts (`factory { ... }`), ao lado de `ArchiveAccountUseCase`.

## 4. Modal de detalhe da conta + Desarquivar (D7) — archived-only

- [ ] 4.1 Criar `ViewAccountUiState` (Loading/Error/Content(account)), `ViewAccountAction` (`data object Unarchive`), `ViewAccountEvent` (Dismiss) — espelhando os arquivos de `viewCategory/`/`viewCreditCard/`.
- [ ] 4.2 Criar `ViewAccountViewModel(accountId, accountRepository, unarchiveAccount, crashlytics)`: observa `observeAccountById(accountId)`, `interceptAbsence`/dismiss quando a conta some; `onAction(Unarchive)` chama o use case com `onLeft { crashlytics.recordException(it) }` e emite `Event.Dismiss` no `onRight`.
- [ ] 4.3 Criar `ViewAccountModal(accountId) : AdaptiveModal`: resolve o VM, coleta `uiState` uma vez. Detalhe enxuto — identidade (ícone `AppIcon.fromKey` + nome) e tipo; sem saldo (arquivada é sempre zero). `DetailActions()` renderiza **só** o `OutlinedActionButton` **Desarquivar**. Sem confirmação.
- [ ] 4.4 Registrar `ViewAccountViewModel` no `accountsModule` (`viewModel { ... }`).

## 5. Tela de contas arquivadas (D6)

- [ ] 5.1 Criar `ArchivedAccountsUiState` (Content(list)/Empty/Loading), `ArchivedAccountUi` (plano: id, nome, iconKey, tipo) e mapper `Account.toArchivedUi()` (o domínio não cruza a fronteira ViewModel → UI).
- [ ] 5.2 Criar `ArchivedAccountsViewModel(accountRepository)`: `observeAllAccountsIncludingClosed().map { it.filter(Account::isArchived) }` mapeado para `ArchivedAccountUi`.
- [ ] 5.3 Criar um card enxuto novo (`ArchivedAccountCard`) — ícone + nome + indicação **textual** de arquivada (string própria; cor/alpha não pode ser o único diferenciador, paridade com `category_card_archived`).
- [ ] 5.4 Criar `ArchivedAccountsScreen(onNavigateBack)`: `LazyColumn` de `ArchivedAccountCard`, topbar transparente com voltar → `navigateUp`; linha toca → `detailController.show(ViewAccountModal(account.id))`. Empty-state discreto quando não há arquivadas.
- [ ] 5.5 `ArchivedAccountsRoute` (`@Serializable`, implementa `NavRoute`) **no impl** — destino interno ao feature.
- [ ] 5.6 `NavGraphBuilder.accountsGraph()`: adicionar `composable<ArchivedAccountsRoute>` dentro do `navigation<AccountsGraph>` existente, `onNavigateBack = navController::navigateUp`.
- [ ] 5.7 Registrar `ArchivedAccountsViewModel` no `accountsModule` (`viewModel { ... }`).

## 6. Entrada na tela de contas (D6)

- [ ] 6.1 `AccountsScreen`: no slot `actions` (que já contém o `MonthSelector`), adicionar **ao lado** um `IconButton` de overflow (`⋮`) + `DropdownMenu`/`DropdownMenuItem` "Arquivadas" → `navController.navigate(ArchivedAccountsRoute)`. Não altera o pager nem o `MonthSelector`.
- [ ] 6.2 `AccountsScreen`: remover o `enabled = !account.isDefault` inline da área de retirada; renderizar a partir do terceiro caso do `retireAction` (botão de ação para DELETE/ARCHIVE; orientação para o caso "conta padrão").

## 7. Strings

- [ ] 7.1 `core/resources` (`values/` pt e `values-en/`): `account_error_cannot_archive_default`, `accounts_archived_title`, `accounts_archived_empty`, `accounts_view_archived` (overflow), `account_unarchive` (botão), `account_archived` (indicador do card), `retire_action_unavailable_default` (orientação do terceiro caso). Aguardar a geração dos acessores de `Res`.

## 8. Testes

- [ ] 8.1 `ArchiveAccountUseCase`: arquivar a conta padrão retorna `Left(AccountException(CANNOT_ARCHIVE_DEFAULT))` e não escreve; conta não-padrão zerada arquiva normalmente (regressão).
- [ ] 8.2 `retireActionOf`: `isDefault = true` → caso "indisponível"; `mustPreserve` decide DELETE/ARCHIVE quando não é padrão (estender `RetireActionTest`).
- [ ] 8.3 `UnarchiveAccountUseCase`: `invoke(account)` chama `repository.reopen(account.id)` e retorna `Right(Unit)`.
- [ ] 8.4 `AccountRepository.reopen`: chama `AccountDao.reopen(accountId)` com o id recebido (fake DAO, verify).
- [ ] 8.5 `ArchivedAccountsViewModel`: lista só contas arquivadas; Empty quando não há nenhuma.
- [ ] 8.6 `ViewAccountViewModel`: ação `Unarchive` invoca o use case e emite `Dismiss` no sucesso.

## 9. Validação

- [ ] 9.1 `openspec validate unarchive-accounts --strict`.
- [ ] 9.2 `./gradlew :app:shared:testDebugUnitTest` verde (inclui os fakes patchados de 3.4).
- [ ] 9.3 Conferir na tela: eleger outra padrão → arquivar a antiga → some da tela ativa → overflow "Arquivadas" → abrir conta → Desarquivar → reaparece nas contas e nos seletores; tentar arquivar a padrão vigente → não é oferecido, e o domínio recusaria com `CANNOT_ARCHIVE_DEFAULT`.
