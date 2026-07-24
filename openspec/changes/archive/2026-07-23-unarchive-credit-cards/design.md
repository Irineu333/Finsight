## Context

O ciclo de vida de cartão mora em `account-lifecycle`. Diferente da categoria (decisão D4 daquele domínio, que carrega o próprio `isArchived`), o cartão é uma **fachada sobre uma conta** do plano de contas (`chart-of-accounts`), do tipo `LIABILITY`, e lê o seu estado de arquivamento dessa conta pelo vínculo `accountId` (decisão D21). Arquivar um cartão fecha a conta; desarquivar precisa **reabri-la**.

Estado atual relevante (levantado por dois agentes de exploração):
- `core/ledger` `AccountDao` tem `close(id)` (`UPDATE accounts SET isArchived = 1`) mas **nenhum inverso** — o comentário no DAO diz "reopening is a single flag away", nunca implementado. É o primitivo que falta.
- `isArchived` da conta é **flag de visibilidade**, nunca filtro de soma: `EntryDao.balanceOf`/`netWorthCents` somam entries independentemente dele. Arquivar não toca faturas nem entries. Um cartão arquivado é garantidamente saldo-zero (o guard `AccountError.HAS_BALANCE` de `ArchiveAccountUseCase` já o exige). Logo reabrir é inócuo: não há nada a reconciliar. Veredito confirmado — desarquivar é flip puro e simétrico.
- `CreditCardDao` já expõe `observeAllCreditCardsIncludingClosed()` (traz o `isArchived` da conta via JOIN); `CreditCardsViewModel` observa só `observeAllCreditCards()` (ativos).
- `ArchiveCreditCardUseCase` resolve a conta do cartão e delega a `ArchiveAccountUseCase` (accounts feature) por causa do guard de saldo. Registro em `di/UseCaseModule.kt`.
- **Não existe** `ViewCreditCardModal`: a tela de cartões é um `HorizontalPager` com `CardActions` inline (retirar/editar) no cartão visível. Não há detalhe de cartão hoje. O precedente `ViewCategoryModal` estende **`AdaptiveModal`** (slots `DetailContent()`/`DetailActions()`, canal `events`, `interceptAbsence`) e é aberto por `LocalDetailPaneController.show(...)` — **não** por `ModalBottomSheet`/`ModalManager`. É esse o padrão a espelhar.
- `OutlinedActionButton` já foi extraído para `core/ui` pela mudança de categorias — o botão de ação a reusar.
- `UnarchiveCategoryUseCase` é o precedente de forma (`Either<Throwable, Unit>` via `catch`), mas mora na fachada de categoria; o de cartão mora na conta.
- **O `CreditCard` de domínio carrega `isArchived`, `limit`, `closingDay`, `dueDay`, `iconKey`, `accountId` — mas não cor nem saldo.** Não há cor por cartão (o visual deriva de `iconKey` + tema). O `CreditCardUi` (`core/ui`) **não** carrega `isArchived` e é montado só de cartões ativos — logo a lista e o detalhe não podem usá-lo. Mas o `CreditCard` de domínio **também não** cruza a fronteira ViewModel → UI (decisão D11): os ViewModels o mapeiam para um UI model plano do feature (`ArchivedCreditCardUi`).
- `ArchiveCreditCardUseCase` recebe o `CreditCard` e lê `creditCard.accountId`; o `unarchive` faz o mesmo. O módulo de use cases chama-se `useCaseModules` (plural).

## Goals / Non-Goals

**Goals:**
- Desarquivar cartão ponta a ponta (DAO do razão → repo → use case → UI), simétrico ao arquivar, reabrindo a conta.
- Uma tela dedicada de cartões arquivados, alcançável da tela de cartões, sem sobrecarregá-la.
- Um `ViewCreditCardModal` decente — onde o desarquivar mora — sem botão inline em lista.

**Non-Goals:**
- Desarquivar/reabrir **conta comum** de forma genérica (accounts feature) — fora de escopo; só o caminho de cartão é aberto aqui, ainda que reuse o mesmo primitivo `AccountDao.reopen`.
- Redesenhar a tela de cartões ativa (pager, `CardActions` inline permanecem) ou substituir o fluxo de arquivar.
- Qualquer migração de banco. O flag `accounts.isArchived` já existe.
- Reabrir faturas, reconciliar saldo ou tocar em entries — o veredito é que nada disso é necessário.

## Decisions

### D1 — O primitivo que falta é `AccountDao.reopen`, no razão
Desarquivar cartão começa no `:core:ledger`: `reopen(id)` = `UPDATE accounts SET isArchived = 0 WHERE id = :id`, o inverso exato de `close(id)`, ao lado dele no `AccountDao`. É onde o estado mora e onde `close` já vive; qualquer outro lugar seria cópia do estado que a spec proíbe.
- *Alternativa:* dar ao cartão um `isArchived` próprio e um flip de fachada como categoria. Rejeitada — viola "o estado de arquivamento de cartão reside exclusivamente no plano de contas, sem cópia na fachada".

### D2 — `UnarchiveCreditCardUseCase` separado, mesma forma de `ArchiveCreditCardUseCase`
Mesma **forma**: `Either<Throwable, Unit>` via `catch`, recebe o `CreditCard` e lê `creditCard.accountId`. Mantém a simetria e "um use case por ação nomeada". A **fiação** difere (D3): archive depende de `IAccountRepository`+`ArchiveAccountUseCase`; unarchive depende só de `ICreditCardRepository`.
- *Alternativa:* um `SetCreditCardArchivedUseCase(archived: Boolean)`. Rejeitada — a spec exige use cases distintos e nomeados; um booleano genérico reintroduz o "use case que faz coisa diferente do seu nome".

### D3 — Desarquivar não passa por `ArchiveAccountUseCase`; vai direto ao repo → `reopen`
Arquivar delega a `ArchiveAccountUseCase` **porque** há um guard (saldo zero). Desarquivar não tem guard — é inócuo por invariante — então introduzir um `UnarchiveAccountUseCase` na accounts feature só para envolver um `UPDATE` seria cerimônia sem regra. `ICreditCardRepository.unarchive(accountId)` chama `AccountDao.reopen(accountId)` diretamente; o use case apenas o embrulha em `catch`, passando `creditCard.accountId`. A `CreditCardRepository` **já tem o `AccountDao` injetado** (opera cartão+conta juntas), então não há nova dependência. A assinatura recebe `accountId` (não o `card.id`) para não forçar um `getCreditCardById` redundante — o chamador já tem o `CreditCard`.
- *Alternativa:* `UnarchiveAccountUseCase` simétrico a `ArchiveAccountUseCase`. Rejeitada por escopo e por ausência de regra a hospedar; se um dia contas comuns precisarem desarquivar, extrai-se então.

### D4 — `ArchivedCreditCardsRoute` é destino **interno** ao `creditcards/impl`
Só se navega a ela de dentro do próprio feature (da `CreditCardsScreen`). Pela convenção — "o `api` declara apenas rotas externamente navegáveis; destinos internos vivem no `impl`" — a rota `@Serializable` mora no `impl` e entra no `creditCardsGraph` existente, não no `api`. `ArchivedCreditCardsRoute` implementa o marcador `NavRoute` de `:core:navigation`.
- *Alternativa:* declará-la no `api`. Rejeitada — nenhum outro feature navega até ela; exportá-la alargaria a superfície pública sem consumidor.

### D5 — `ArchivedCreditCardsScreen`: lista vertical simples, não pager
ViewModel observa `observeAllCreditCardsIncludingClosed()` e filtra `isArchived`, mapeando cada cartão para `ArchivedCreditCardUi` (D11). UiState carrega a lista já resolvida. A tela é uma `LazyColumn` sem `HorizontalPager` — arquivado quer lista, não carrossel. O `CreditCardCard` ativo é pesado/invoice-centric e não serve. Topbar transparente com ícone de voltar (`navigateUp`). Sem itens → empty-state discreto ("nenhum cartão arquivado"), não um CTA.

**Ajuste pós-review (tarefa 9.2):** o item da lista é um **`ArchivedCreditCardCard`** — ícone + nome + indicador **textual** "Arquivado" (cor/alpha não pode ser o único diferenciador, paridade com `category_card_archived`), com o limite em destaque e os dias de fechamento/vencimento nas pontas `Start`/`End`. A primeira versão era um `ArchivedCreditCardRow` enxuto de um `SpaceBetween`, mas um terceiro atributo flutuava para o centro e quebrava a simetria; o card foi adotado e o row removido.

### D6 — `ViewCreditCardModal` novo (`AdaptiveModal`), detalhe decente, archived-only
Toca-se numa linha da lista → `LocalDetailPaneController.show(ViewCreditCardModal(id))` (sem botão inline, decisão do usuário). É um `AdaptiveModal` (slots `DetailContent()`/`DetailActions()`), espelhando `ViewCategoryModal`. O modal:
- Resolve o VM e coleta o estado **uma vez**, com `collectAsStateWithLifecycle()`.
- Observa o cartão por id (`observeCreditCardById`, que já traz `isArchived`); usa `interceptAbsence` para dismiss quando o cartão some.
- Mostra um detalhe **enxuto porém decente**: identidade (nome + ícone via `AppIcon.fromKey(iconKey)` — **sem cor**, que o cartão não possui), atributos (limite, dia de fechamento, dia de vencimento) e a **quantidade de faturas** (D13).
- Ação: `OutlinedActionButton` "Desarquivar" (ícone `Icons.Default.Unarchive`), disparando `ViewCreditCardAction.Unarchive` → `UnarchiveCreditCardUseCase`. Ação direta, sem modal de confirmação — reversível e inócua (mesma razão do D1 de categorias). Ao desarquivar com sucesso o modal **se fecha sozinho** (D12).
- *Escopo cortado (decisão do usuário):* o modal é **archived-only**. Como só é alcançado pela lista de arquivados, o ramo não-arquivado seria código morto e puxaria `RetireAction`/`DeleteCreditCardModal`/`ArchiveCreditCardModal`/`Form`, dobrando a superfície. `DetailActions()` renderiza só o Desarquivar; fica um `when(isArchived)` como costura, sem implementar retirar/editar. A oferta de retirar cartão ativo permanece nas `CardActions` inline do pager, onde já está.

### D7 — Entrada discreta: overflow na topbar da `CreditCardsScreen`
Um `IconButton` de overflow (`⋯`) nas `actions` da top bar, com um `DropdownMenuItem` "Cartões arquivados" que faz `LocalNavController.current.navigate(ArchivedCreditCardsRoute)`. Não compete com o pager nem com as `CardActions` do cartão; some quando não há nada a fazer ali.
- *Alternativa:* um link no rodapé da lista ou no empty-state. Rejeitada — o pedido é oferecer a partir da própria tela sem lhe acrescentar peso visual; o overflow é o gesto mais discreto e não some quando há cartões.

### D8 — Sem confirmação, sem aviso, sem toque em fatura
Coerente com o veredito: desarquivar é `SET isArchived = 0` e nada mais. Nenhum `ReopenInvoiceUseCase`, nenhuma checagem de saldo, nenhum hook de escrita. O único efeito colateral protetor que o flag governa (`closedLegBlockingChange` tornando imutáveis lançamentos que tocam conta arquivada) é auto-corrigido pela reabertura: voltar o flag a 0 re-permite edição normal numa conta que segue saldo-zero.

### D9 — Strings em `core/resources`
**Novas:** `credit_cards_archived_title` (título da tela), `credit_cards_archived_empty` (vazio da tela), `credit_cards_view_archived` (item do overflow — lê apenas "Arquivados", pois o contexto já é a tela de cartões; tarefa 9.1), `credit_cards_unarchive` (botão), `credit_card_archived` (indicador de arquivado no card), `credit_card_invoices_label` (rótulo da quantidade de faturas; D13). **Reusadas** no detalhe: `credit_card_form_limit_label`, `credit_card_ui_closes_on`, `credit_card_ui_due_on`. Em `values/` (pt) e `values-en/`.
- *Ajuste pós-review (tarefa 9.3):* a string `credit_card_balance` foi **removida** — o detalhe passou a mostrar a quantidade de faturas, não o saldo (D13).

### D10 — Sem migração de banco
Apenas um `UPDATE` no flag existente `accounts.isArchived`. `AppDatabase` e migrações não mudam.

### D11 — UI model `ArchivedCreditCardUi`: o domínio não cruza a fronteira ViewModel → UI (ajuste pós-review, tarefa 9.5)
A primeira versão renderizava a lista e o detalhe direto do `CreditCard` de **domínio** — Composables e `UiState`s carregavam o modelo de domínio, cruzando a fronteira ViewModel → UI, o que viola a regra de camada (Domain ← UI). Introduz-se um **UI model plano** do feature, `ArchivedCreditCardUi` (`cardId`, `iconKey`, `name`, `limit`, `closingDay`, `dueDay`), com o mapper `CreditCard.toArchivedUi()`. Os ViewModels mapeiam para ele: `ArchivedCreditCardsUiState.Content` carrega `List<ArchivedCreditCardUi>` e `ViewCreditCardUiState.Content` carrega `ArchivedCreditCardUi` + `isArchived` + `invoiceCount`. O `CreditCard` de domínio fica **só** nos ViewModels e no mapper.

### D12 — Fechar o detalhe após desarquivar (ajuste pós-review, tarefa 9.4)
Como a visualização é **archived-only** e só alcançada pela lista de arquivados, um cartão desarquivado não tem mais o que mostrar ali. `ViewCreditCardViewModel.unarchive()` emite `ViewCreditCardEvent.Dismiss` no sucesso (`onRight`), fechando o modal/painel sozinho. O `CreditCard` a desarquivar é resolvido por id (`getCreditCardById(cardId)`) no momento da ação — nenhum estado de domínio é retido em estado observável, nem há efeito colateral escondido no operador de flow (a versão intermediária retinha o cartão dentro do transform do `combine`; o transform voltou a ser puro — tarefa 9.6).

### D13 — Detalhe mostra a quantidade de faturas, não o saldo (ajuste pós-review, tarefa 9.3)
Um cartão arquivado é garantidamente saldo-zero (invariante do arquivamento), então exibir o saldo no detalhe seria sempre "zero" — um dado inútil. O detalhe mostra a **quantidade de faturas** (`credit_card_invoices_label`): `ViewCreditCardViewModel` observa `IInvoiceRepository.observeInvoicesByCreditCard` em vez de ler o saldo do razão, e `UiState.Content.invoiceCount` substitui o campo `balance`. Com isso o `IEntryRepository` deixa de ser dependência do VM.
