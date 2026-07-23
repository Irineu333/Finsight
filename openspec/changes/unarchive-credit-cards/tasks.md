> Ordem: o primitivo do razão e o caminho de domínio primeiro (provável e testável antes de qualquer UI); depois o modal de detalhe, a tela de arquivados, a entrada; testes e validação por último.
>
> Verificado contra o código por dois planos independentes + conferência manual. Correções já incorporadas: `unarchive` recebe `accountId` (não `card.id`); o modal estende `AdaptiveModal` (não `ModalBottomSheet`) e é aberto via `LocalDetailPaneController`; cartão não tem cor (só `iconKey`) e o saldo vem de `IEntryRepository.balance`; a lista e o modal renderizam do `CreditCard` de domínio (o `CreditCardUi` não carrega `isArchived`); o modal é **archived-only** (decisão do usuário). O módulo de use cases chama-se `useCaseModules`.

## 1. Razão — o primitivo `reopen` (D1)

- [x] 1.1 `core/ledger` `AccountDao`: `@Query("UPDATE accounts SET isArchived = 0 WHERE id = :id") suspend fun reopen(id: Long)`, ao lado de `close(id)`, com KDoc de "inverso de `close`; só reabre o flag, sem tocar entries".
- [x] 1.2 Patchar os fakes de `AccountDao` que a nova assinatura quebra: `FakeAccountDao` (`feature/transactions/impl/.../LedgerEntryWriterTest.kt:307`) e `RecordingAccountDao` (`feature/accounts/impl/.../RetireAccountGuardsTest.kt:219`).

## 2. Dados e domínio — desarquivar cartão (D2, D3)

- [x] 2.1 `ICreditCardRepository` (creditcards/api): adicionar `suspend fun unarchive(accountId: Long)` com KDoc deixando explícito que reabre a **conta** do cartão pelo `accountId`.
- [x] 2.2 `CreditCardRepository` (impl): `unarchive(accountId)` → `accountDao.reopen(accountId)` (o `accountDao` já está injetado no repo). Sem round-trip extra para resolver o cartão.
- [x] 2.3 Criar `UnarchiveCreditCardUseCase(repository: ICreditCardRepository)`: `operator fun invoke(creditCard: CreditCard): Either<Throwable, Unit> = catch { repository.unarchive(creditCard.accountId) }`. Mesma **forma** de `ArchiveCreditCardUseCase` (`Either`+`catch`, recebe `CreditCard`, lê `accountId`), mas **outra fiação**: archive passa por `IAccountRepository`+`ArchiveAccountUseCase` (por causa do guard de saldo); unarchive não tem guard e vai direto ao repo. KDoc: "reversível e inócuo; sem guard, sem confirmação".
- [x] 2.4 Patchar os 5 fakes de `ICreditCardRepository` que a nova assinatura quebra: `InvoiceTransactionsViewModelCharacterizationTest.kt:114`, `TransactionRepositoryEntriesTest.kt:207`, `ReportViewerViewModelCharacterizationTest.kt:244`, `CalculateReportStatsUseCaseTest.kt:121`, `RecurringRepositoryTest.kt:76`.
- [x] 2.5 Registrar `UnarchiveCreditCardUseCase` no `useCaseModules` (`di/UseCaseModule.kt`), `factory { UnarchiveCreditCardUseCase(get()) }`, ao lado de `ArchiveCreditCardUseCase`.

## 3. Modal de detalhe do cartão + Desarquivar (D6, D8) — archived-only

- [x] 3.1 Criar `ViewCreditCardUiState` (Loading/Error/Content(card, balance)), `ViewCreditCardAction` (`data object Unarchive`), `ViewCreditCardEvent` (Dismiss) — espelhando os 5 arquivos de `viewCategory/`.
- [x] 3.2 Criar `ViewCreditCardViewModel(cardId, creditCardRepository, entryRepository, unarchiveCreditCard, crashlytics)`: observa `observeCreditCardById(cardId)` (traz `isArchived`), busca saldo via `entryRepository.balance(card.accountId)`, `interceptAbsence` para dismiss quando o cartão some; `onAction(Unarchive)` chama o use case com `onLeft { crashlytics.recordException(it) }`.
- [x] 3.3 Criar `ViewCreditCardModal(cardId) : AdaptiveModal`: resolve o VM e coleta `uiState` **uma vez** com `collectAsStateWithLifecycle()`. `DetailContent()` renderiza um detalhe enxuto porém decente — identidade (ícone `AppIcon.fromKey(iconKey)` + nome; **sem cor**, que o cartão não tem), atributos (limite, dia de fechamento, dia de vencimento), saldo. `DetailActions()` renderiza **só** o `OutlinedActionButton` **Desarquivar** (`Icons.Default.Unarchive`). Sem confirmação. Deixar o `when(isArchived)` como costura, mas **não** implementar retirar/editar (escopo cortado).
- [x] 3.4 Registrar `ViewCreditCardViewModel` no `creditCardsModule` (`viewModel { ... }`).

## 4. Tela de cartões arquivados (D4, D5)

- [x] 4.1 Criar `ArchivedCreditCardsUiState` (Content(list)/Empty/Loading) e `ArchivedCreditCardsViewModel(creditCardRepository)`: `observeAllCreditCardsIncludingClosed().map { it.filter(CreditCard::isArchived) }`. Renderiza do `CreditCard` de domínio (o `CreditCardUi` não tem `isArchived`).
- [x] 4.2 Criar um row enxuto novo (`ArchivedCreditCardRow`) no pacote da tela — ícone + nome + indicação **textual** de arquivado (string própria; cor/alpha não pode ser o único diferenciador, paridade com `category_card_archived`). O `CreditCardCard` é pesado/invoice-centric e não serve.
- [x] 4.3 Criar `ArchivedCreditCardsScreen(onNavigateBack)`: `LazyColumn` de `ArchivedCreditCardRow`, topbar transparente (`containerColor = colorScheme.background`) com ícone de voltar → `navigateUp`; linha toca → `detailController.show(ViewCreditCardModal(card.id))`. Empty-state discreto (string `credit_cards_archived_empty`) quando não há arquivados.
- [x] 4.4 `ArchivedCreditCardsRoute` (`@Serializable`, implementa `NavRoute` de `:core:navigation`) **no impl** — destino interno ao feature.
- [x] 4.5 `NavGraphBuilder.creditCardsGraph()`: adicionar `composable<ArchivedCreditCardsRoute>` dentro do `navigation<CreditCardsGraph>` existente, passando `onNavigateBack = navController::navigateUp`.
- [x] 4.6 Registrar `ArchivedCreditCardsViewModel` no `creditCardsModule` (`viewModel { ... }`).

## 5. Entrada na tela de cartões (D7)

- [x] 5.1 `CreditCardsScreen`: criar o slot `actions =` no `TopAppBar` (hoje inexistente) com um `IconButton` de overflow (`⋯`) + `DropdownMenu`/`DropdownMenuItem` "Cartões arquivados" → `navController.navigate(ArchivedCreditCardsRoute)`. Não altera o pager nem as `CardActions` inline.

## 6. Strings (D9)

- [x] 6.1 `core/resources` (`values/` pt e `values-en/`): novas — `credit_cards_archived_title`, `credit_cards_archived_empty`, `credit_cards_view_archived` (overflow), `credit_cards_unarchive` (botão), `credit_card_archived` (indicador do row), `credit_card_balance` (rótulo). **Reusar** o que já existe para o detalhe: `credit_card_form_limit_label`, `credit_card_ui_closes_on`, `credit_card_ui_due_on`. Aguardar a geração dos acessores de `Res`.

## 7. Testes

- [x] 7.1 `UnarchiveCreditCardUseCase`: `invoke(card)` chama `repository.unarchive(card.accountId)` e retorna `Right(Unit)`.
- [x] 7.2 `CreditCardRepository.unarchive`: chama `AccountDao.reopen(accountId)` com o id recebido (fake `AccountDao`, verify).
- [x] 7.3 `ArchivedCreditCardsViewModel`: lista só cartões arquivados; Empty quando não há nenhum.
- [x] 7.4 `ViewCreditCardViewModel`: ação `Unarchive` invoca o use case; cartão arquivado oferece desarquivar; saldo é buscado de `entryRepository.balance`.

## 8. Validação

- [x] 8.1 `openspec validate unarchive-credit-cards --strict`.
- [x] 8.2 `./gradlew :app:shared:testDebugUnitTest` verde (inclui os fakes patchados de 1.2 e 2.4).
- [x] 8.3 Conferir na tela: arquivar cartão → some da tela ativa → overflow "Cartões arquivados" → abrir cartão → Desarquivar → reaparece na tela de cartões e nos seletores de lançamento; voltar (system-back) funciona.

## 9. Ajustes pós-revisão

- [x] 9.1 Item do overflow na `CreditCardsScreen` passa a ler **"Arquivados"** (`credit_cards_view_archived`) — o contexto já é a tela de cartões, então o qualificador "cartões" era redundante.
- [x] 9.2 Listagem de arquivados renderiza **cards** (`ArchivedCreditCardCard`: ícone + nome + indicador "Arquivado", limite em destaque e dias de fechamento/vencimento nas pontas Start/End) em vez do row enxuto — um terceiro atributo num `SpaceBetween` flutuava para o centro e quebrava a simetria. Remove `ArchivedCreditCardRow`.
- [x] 9.3 O detalhe do cartão mostra a **quantidade de faturas** (`credit_card_invoices_label`) no lugar do saldo, que é sempre zero para um cartão arquivado. `ViewCreditCardViewModel` observa `IInvoiceRepository.observeInvoicesByCreditCard` em vez de ler o saldo do razão; `UiState.Content.invoiceCount` substitui `balance`. Remove a string `credit_card_balance`.
- [x] 9.4 **Fechar o detalhe após desarquivar:** como a visualização é archived-only e só alcançada pela lista de arquivados, `unarchive()` emite `ViewCreditCardEvent.Dismiss` no sucesso (`onRight`), fechando o modal/painel sozinho.
- [x] 9.5 **Defeito de arquitetura — vazamento de domínio para a UI:** a listagem e o detalhe renderizavam do `CreditCard` de domínio (Composables e `UiState`s carregavam o modelo, cruzando a fronteira ViewModel → UI). Introduz `ArchivedCreditCardUi` (UI model plano) + mapper `CreditCard.toArchivedUi()`; os ViewModels mapeiam para ele. `ArchivedCreditCardsUiState.Content` carrega `List<ArchivedCreditCardUi>`; `ViewCreditCardUiState.Content` carrega `ArchivedCreditCardUi` + `isArchived` + `invoiceCount`. O `CreditCard` de domínio fica só nos ViewModels e no mapper.
- [x] 9.6 **Remover o side-effect no operador de flow:** a primeira versão do 9.5 retinha o cartão com `currentCard.value = creditCard` **dentro do transform do `combine`** — efeito colateral escondido num operador de flow, frágil sob múltiplos coletores. O transform volta a ser puro; `unarchive()` resolve o `CreditCard` por id (`getCreditCardById(cardId)`) no momento da ação, sem estado de domínio retido em estado observável.
