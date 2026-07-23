> Ordem: o primitivo do razão e o caminho de domínio primeiro; depois a tela de arquivados, o modal de detalhe e a entrada; testes e validação por último.

## 1. Razão — o primitivo `reopen` (D1)

- [ ] 1.1 `core/ledger` `AccountDao`: `@Query("UPDATE accounts SET isArchived = 0 WHERE id = :id") suspend fun reopen(id: Long)`, ao lado de `close(id)`, com KDoc de "inverso de `close`; só reabre o flag, sem tocar entries".

## 2. Dados e domínio — desarquivar cartão (D2, D3)

- [ ] 2.1 `ICreditCardRepository` (creditcards/api): adicionar `suspend fun unarchive(id: Long)` com KDoc simétrico a `archive`, deixando explícito que reabre a **conta** do cartão.
- [ ] 2.2 `CreditCardRepository` (impl): `unarchive(id)` resolve o `accountId` do cartão e chama `AccountDao.reopen(accountId)`.
- [ ] 2.3 Criar `UnarchiveCreditCardUseCase` espelhando `ArchiveCreditCardUseCase` (`Either<Throwable, Unit>` via `catch`, chamando `repository.unarchive(card.id)`), com KDoc de "reversível e inócuo; sem guard, sem confirmação". Não passa por `ArchiveAccountUseCase` (não há regra a hospedar).
- [ ] 2.4 Registrar `UnarchiveCreditCardUseCase` no `useCaseModule` (Koin).

## 3. Tela de cartões arquivados (D4, D5)

- [ ] 3.1 Criar `ArchivedCreditCardsRoute` (`@Serializable`, implementa `NavRoute`) **no impl** — destino interno ao feature.
- [ ] 3.2 Criar `ArchivedCreditCardsViewModel`: observa `observeAllCreditCardsIncludingClosed()`, filtra `isArchived`, expõe UiState com a lista já resolvida (Content/Empty).
- [ ] 3.3 Criar `ArchivedCreditCardsScreen`: `LazyColumn` de linhas de cartão (identidade + esmaecido de arquivado), topbar transparente (`containerColor = colorScheme.background`); linha abre `ViewCreditCardModal`. Empty-state discreto quando não há arquivados.
- [ ] 3.4 `NavGraphBuilder.creditCardsGraph()`: registrar a `ArchivedCreditCardsRoute` no subgrafo existente.
- [ ] 3.5 Registrar `ArchivedCreditCardsViewModel` no `creditCardsModule` (Koin).

## 4. Modal de detalhe do cartão + Desarquivar (D6, D8)

- [ ] 4.1 Criar `ViewCreditCardViewModel`: observa o cartão por id; `ViewCreditCardAction.Unarchive` chama `UnarchiveCreditCardUseCase` com `onLeft { crashlytics.recordException(it) }`. UiState com o cartão e o estado de arquivamento.
- [ ] 4.2 Criar `ViewCreditCardModal` (`ModalBottomSheet`): resolve o VM e coleta `uiState` **uma vez** com `collectAsStateWithLifecycle()`. Detalhe decente porém enxuto — identidade (nome, ícone/bandeira, cor), atributos (limite, dia de fechamento, dia de vencimento), saldo.
- [ ] 4.3 Ações mutuamente exclusivas pelo estado: se arquivado → `OutlinedActionButton` **Desarquivar** (`Icons.Default.Unarchive`), disparando `ViewCreditCardAction.Unarchive`; se não arquivado → retirar/editar (para reuso futuro). Sem modal de confirmação no desarquivar. O modal observa o cartão, então ao desarquivar o item some da lista sozinho.
- [ ] 4.4 Registrar `ViewCreditCardViewModel` no `creditCardsModule` (Koin).

## 5. Entrada na tela de cartões (D7)

- [ ] 5.1 `CreditCardsScreen`: adicionar `IconButton` de overflow (`⋯`) nas `actions` da top bar com um `DropdownMenuItem` "Cartões arquivados" que faz `navigate(ArchivedCreditCardsRoute)`. Não altera o pager nem as `CardActions` inline.

## 6. Strings (D9)

- [ ] 6.1 `core/resources` (`values/` pt e `values-en/`): `credit_cards_archived_title`, `credit_cards_archived_empty`, `credit_cards_view_archived` (overflow), `credit_cards_unarchive` (botão), e rótulos do detalhe (`credit_card_limit`, `credit_card_closing_day`, `credit_card_due_day`, `credit_card_balance`) — reusar os que já existirem.

## 7. Testes

- [ ] 7.1 `UnarchiveCreditCardUseCase`: chama `repository.unarchive(id)` e retorna `Right(Unit)`.
- [ ] 7.2 `CreditCardRepository.unarchive`: resolve o `accountId` e chama `AccountDao.reopen` com ele (fake/verify).
- [ ] 7.3 `ArchivedCreditCardsViewModel`: lista só cartões arquivados; Empty quando não há nenhum.
- [ ] 7.4 `ViewCreditCardViewModel`: ação `Unarchive` invoca o use case; cartão arquivado oferece desarquivar (e não retirar), não arquivado oferece retirar (e não desarquivar).

## 8. Validação

- [ ] 8.1 `openspec validate unarchive-credit-cards --strict`.
- [ ] 8.2 `./gradlew :app:shared:testDebugUnitTest` verde.
- [ ] 8.3 Conferir na tela: arquivar cartão → some da tela ativa → overflow "Cartões arquivados" → abrir cartão → Desarquivar → reaparece na tela de cartões e nos seletores de lançamento.
