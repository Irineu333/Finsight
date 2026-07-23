## Context

O ciclo de vida de cartão mora em `account-lifecycle`. Diferente da categoria (decisão D4 daquele domínio, que carrega o próprio `isArchived`), o cartão é uma **fachada sobre uma conta** do plano de contas (`chart-of-accounts`), do tipo `LIABILITY`, e lê o seu estado de arquivamento dessa conta pelo vínculo `accountId` (decisão D21). Arquivar um cartão fecha a conta; desarquivar precisa **reabri-la**.

Estado atual relevante (levantado por dois agentes de exploração):
- `core/ledger` `AccountDao` tem `close(id)` (`UPDATE accounts SET isArchived = 1`) mas **nenhum inverso** — o comentário no DAO diz "reopening is a single flag away", nunca implementado. É o primitivo que falta.
- `isArchived` da conta é **flag de visibilidade**, nunca filtro de soma: `EntryDao.balanceOf`/`netWorthCents` somam entries independentemente dele. Arquivar não toca faturas nem entries. Um cartão arquivado é garantidamente saldo-zero (o guard `AccountError.HAS_BALANCE` de `ArchiveAccountUseCase` já o exige). Logo reabrir é inócuo: não há nada a reconciliar. Veredito confirmado — desarquivar é flip puro e simétrico.
- `CreditCardDao` já expõe `observeAllCreditCardsIncludingClosed()` (traz o `isArchived` da conta via JOIN); `CreditCardsViewModel` observa só `observeAllCreditCards()` (ativos).
- `ArchiveCreditCardUseCase` resolve a conta do cartão e delega a `ArchiveAccountUseCase` (accounts feature) por causa do guard de saldo. Registro em `di/UseCaseModule.kt`.
- **Não existe** `ViewCreditCardModal`: a tela de cartões é um `HorizontalPager` com `CardActions` inline (retirar/editar) no cartão visível. Não há detalhe de cartão hoje.
- `OutlinedActionButton` já foi extraído para `core/ui` pela mudança de categorias — o botão de ação a reusar.
- `UnarchiveCategoryUseCase` é o precedente de forma (`Either<Throwable, Unit>` via `catch`), mas mora na fachada de categoria; o de cartão mora na conta.

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

### D2 — `UnarchiveCreditCardUseCase` separado, espelhando `ArchiveCreditCardUseCase`
Mesma forma: `Either<Throwable, Unit>` via `catch`, resolve o `accountId` do cartão e reabre. Mantém a simetria e "um use case por ação nomeada".
- *Alternativa:* um `SetCreditCardArchivedUseCase(archived: Boolean)`. Rejeitada — a spec exige use cases distintos e nomeados; um booleano genérico reintroduz o "use case que faz coisa diferente do seu nome".

### D3 — Desarquivar não passa por `ArchiveAccountUseCase`; vai direto ao repo → `reopen`
Arquivar delega a `ArchiveAccountUseCase` **porque** há um guard (saldo zero). Desarquivar não tem guard — é inócuo por invariante — então introduzir um `UnarchiveAccountUseCase` na accounts feature só para envolver um `UPDATE` seria cerimônia sem regra. `ICreditCardRepository.unarchive(id)` resolve o `accountId` e chama `AccountDao.reopen`; o use case apenas o embrulha em `catch`. A `CreditCardRepository` já opera cartão+conta juntas, então tem o `AccountDao` à mão.
- *Alternativa:* `UnarchiveAccountUseCase` simétrico a `ArchiveAccountUseCase`. Rejeitada por escopo e por ausência de regra a hospedar; se um dia contas comuns precisarem desarquivar, extrai-se então.

### D4 — `ArchivedCreditCardsRoute` é destino **interno** ao `creditcards/impl`
Só se navega a ela de dentro do próprio feature (da `CreditCardsScreen`). Pela convenção — "o `api` declara apenas rotas externamente navegáveis; destinos internos vivem no `impl`" — a rota `@Serializable` mora no `impl` e entra no `creditCardsGraph` existente, não no `api`. `ArchivedCreditCardsRoute` implementa o marcador `NavRoute` de `:core:navigation`.
- *Alternativa:* declará-la no `api`. Rejeitada — nenhum outro feature navega até ela; exportá-la alargaria a superfície pública sem consumidor.

### D5 — `ArchivedCreditCardsScreen`: lista vertical simples, não pager
ViewModel observa `observeAllCreditCardsIncludingClosed()` e filtra `isArchived`. UiState carrega a lista já resolvida. A tela é uma `LazyColumn` de linhas de cartão (identidade + esmaecido de arquivado), sem `HorizontalPager` — arquivado quer lista, não carrossel. Sem itens → um empty-state discreto ("nenhum cartão arquivado"), não um CTA. Topbar transparente (`containerColor = colorScheme.background`), como as demais telas do app.

### D6 — `ViewCreditCardModal` novo, detalhe decente, é onde o desarquivar mora
Toca-se numa linha da lista → abre o modal (sem botão inline, decisão do usuário). O modal:
- Resolve o VM e coleta o estado **uma vez**, com `collectAsStateWithLifecycle()` (o padrão do feature; evita a dupla-resolução que a mudança de categorias teve de corrigir no `ViewCategoryModal`).
- Observa o cartão por id, então ao desarquivar o `isArchived` vira falso e o item some da lista reativamente — sem `dismiss` manual.
- Mostra um detalhe **enxuto porém decente**: identidade (nome, ícone/bandeira, cor), atributos (limite, dia de fechamento, dia de vencimento) e saldo (garantidamente zero).
- Ação: `OutlinedActionButton` "Desarquivar" (ícone `Icons.Default.Unarchive`), disparando `ViewCreditCardAction.Unarchive` → `UnarchiveCreditCardUseCase`. Ação direta, sem modal de confirmação — reversível e inócua (mesma razão do D1 de categorias).
- *Reuso futuro:* o modal nasce **genérico quanto ao estado** (oferece desarquivar se arquivado; retirar/editar se não) para satisfazer os cenários da spec, mas neste escopo só é **alcançado** pela lista de arquivados. Fica pronto para virar o "ver detalhe do cartão" ativo depois, sem redesenho.

### D7 — Entrada discreta: overflow na topbar da `CreditCardsScreen`
Um `IconButton` de overflow (`⋯`) nas `actions` da top bar, com um `DropdownMenuItem` "Cartões arquivados" que faz `LocalNavController.current.navigate(ArchivedCreditCardsRoute)`. Não compete com o pager nem com as `CardActions` do cartão; some quando não há nada a fazer ali.
- *Alternativa:* um link no rodapé da lista ou no empty-state. Rejeitada — o pedido é oferecer a partir da própria tela sem lhe acrescentar peso visual; o overflow é o gesto mais discreto e não some quando há cartões.

### D8 — Sem confirmação, sem aviso, sem toque em fatura
Coerente com o veredito: desarquivar é `SET isArchived = 0` e nada mais. Nenhum `ReopenInvoiceUseCase`, nenhuma checagem de saldo, nenhum hook de escrita. O único efeito colateral protetor que o flag governa (`closedLegBlockingChange` tornando imutáveis lançamentos que tocam conta arquivada) é auto-corrigido pela reabertura: voltar o flag a 0 re-permite edição normal numa conta que segue saldo-zero.

### D9 — Strings novas em `core/resources`
`credit_cards_archived_title` (título da tela), `credit_cards_archived_empty` (vazio da tela), `credit_cards_view_archived` (item do overflow), `credit_cards_unarchive` (botão), e rótulos do detalhe (`credit_card_limit`, `credit_card_closing_day`, `credit_card_due_day`, `credit_card_balance`) — reusando os que já existirem. Em `values/` (pt) e `values-en/`.

### D10 — Sem migração de banco
Apenas um `UPDATE` no flag existente `accounts.isArchived`. `AppDatabase` e migrações não mudam.
