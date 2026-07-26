> Ordem deliberada: a atomicidade (D7) vem primeiro por ser independente e por proteger o razão;
> o vocabulário e a retirabilidade vêm antes da UI, para que a tela nasça consumindo o dono único
> em vez de re-derivar a regra.

## 1. Atomicidade da confirmação (D7)

- [ ] 1.1 `IRecurringOccurrenceRepository`: expor uma operação que grave **transação + ocorrência** como uma unidade de trabalho, recebendo o `TransactionIntent` e os dados da ocorrência (o `transactionId` só existe depois da escrita da transação).
- [ ] 1.2 `RecurringOccurrenceRepository`: receber o handle do banco e `ITransactionRepository`; implementar a operação dentro de um único `useWriterConnection { immediateTransaction { … } }`, chamando `createTransaction` por dentro (o pool do Room é reentrante e a escrita interna vira `SAVEPOINT`). KDoc explicando a unidade de trabalho, no espírito do já existente em `RecurringRepository.delete`.
- [ ] 1.3 `ConfirmRecurringUseCase`: substituir o par `catch { createTransaction }.flatMap { catch { save(occurrence) } }` pela chamada única. Manter `GetOrCreateInvoiceForMonthUseCase` **fora** da transação (D7) e comentar por quê.
- [ ] 1.4 Garantir que nenhuma troca de dispatcher ocorra entre as duas escritas — a reentrância do pool depende do elemento de contexto de corrotina.
- [ ] 1.5 Atualizar o registro no `RecurringModule` (Koin) para as novas dependências do `RecurringOccurrenceRepository`.

## 2. Vocabulário: `isActive` → `isArchived` (D1, D9)

- [ ] 2.1 `Recurring`: trocar `isActive: Boolean = true` por `isArchived: Boolean = false`; ajustar `hasUsableSource` e demais derivações se referenciarem o flag.
- [ ] 2.2 `RecurringMapper`: inverter nos dois sentidos (`isArchived = !entity.isActive` / `isActive = !recurring.isArchived`) — a **única** tradução do flag.
- [ ] 2.3 `RecurringEntity`: KDoc no campo `isActive` registrando que é o inverso de `Recurring.isArchived`, por que o nome diverge, e que o renome exige migração (dívida com dono).
- [ ] 2.4 Renomear `StopRecurringUseCase` → `ArchiveRecurringUseCase` e `ReactivateRecurringUseCase` → `UnarchiveRecurringUseCase`, escrevendo `isArchived = true/false`. KDoc do desarquivar como "reversível e inócuo".
- [ ] 2.5 Renomear os modais e ViewModels correspondentes (`stopRecurring` → `archiveRecurring`, `reactivateRecurring` → `unarchiveRecurring`) e os eventos de analytics.
- [ ] 2.6 `SaveRecurringUseCase`: trocar o parâmetro `isActive: Boolean = true` por `isArchived: Boolean = false`.
- [ ] 2.7 `GetPendingRecurringUseCase`: trocar o filtro `recurring.isActive` por `!recurring.isArchived`.
- [ ] 2.8 `DashboardComponentsBuilder`: mesma troca no filtro de recorrências próximas.
- [ ] 2.9 Atualizar os registros no `RecurringModule` (Koin) para os use cases e ViewModels renomeados.

## 3. Domínio: retirabilidade de recorrência (D2, D3, D4)

- [ ] 3.1 `core/model`: criar o enum de motivos de recusa próprio de recorrência (razões: gerou lançamentos, orçamento aponta para ela) com `val message` em inglês para log e `toUiText()` via `UiText.Res`, no padrão de `RetireError` — **sem** alterar `RetireError`.
- [ ] 3.2 `core/model`: criar `RecurringRetirability { Deletable | MustArchive(reason) }`, espelhando `CategoryRetirability` e com KDoc explicando por que não compartilha o tipo (D4).
- [ ] 3.3 `RecurringDao`: consulta que responde se alguma transação nomeia a recorrência (`SELECT EXISTS(... FROM transactions WHERE recurringId = :id)`), ao lado de `detachTransactions` — a escrita/leitura de `transactions` mora aqui porque um DAO do razão não pode nomear metadado de fachada.
- [ ] 3.4 `BudgetDao`: consulta que responde se algum orçamento aponta para a recorrência, espelhando `countByCategory`.
- [ ] 3.5 `IBudgetRepository` + `BudgetRepository`: expor "existe orçamento para esta recorrência".
- [ ] 3.6 `IRecurringRepository` + `RecurringRepository`: expor "existe transação para esta recorrência".
- [ ] 3.7 Criar `ResolveRecurringRetirabilityUseCase` — dono único, resolvendo os dois guards na ordem e devolvendo `RecurringRetirability`.
- [ ] 3.8 `DeleteRecurringUseCase`: consumir o resolver; `MustArchive` vira recusa com a exceção tipada, `Deletable` segue para `repository.delete`.
- [ ] 3.9 Registrar o resolver no `RecurringModule` (Koin).

## 4. UI: visualização da recorrência (D3, D9)

- [ ] 4.1 `ViewRecurringUiState`: carregar a `RetireAction` resolvida (via `retireActionOf`) em vez de a tela decidir.
- [ ] 4.2 `ViewRecurringViewModel`: consumir `ResolveRecurringRetirabilityUseCase`; injetar `UnarchiveRecurringUseCase`; tratar a ação de desarquivar com `onLeft { crashlytics.recordException(it) }`.
- [ ] 4.3 `ViewRecurringAction`: adicionar a ação de desarquivar.
- [ ] 4.4 `ViewRecurringModal.Actions`: tornar as ofertas mutuamente exclusivas por `isArchived` — arquivada renderiza **apenas** Desarquivar (ícone `Icons.Default.Unarchive`, sem modal de confirmação); não arquivada renderiza a `RetireAction` resolvida, abrindo o modal de arquivar ou o de excluir. Consumir `OutlinedActionButton` em vez do botão local.
- [ ] 4.5 Remover o `Icons.Default.Delete` do botão de arquivar — o ícone passa a vir da `RetireAction`.
- [ ] 4.6 Usar `Throwable.toRetireUiMessage()`-equivalente para a recusa de exclusão, exibindo o motivo pelo `modalManager.showError`.

## 5. Tela de recorrências: estado (D6)

- [ ] 5.1 Criar `RecurringFilter { ACTIVE, EXPENSE, INCOME, ARCHIVED }` no pacote da tela, com KDoc registrando a mistura deliberada dos dois eixos; remover `RecurringStatusFilter` e o `RecurringFilter` antigo.
- [ ] 5.2 `RecurringUiState`: substituir `selectedFilter`/`selectedStatusFilter` pelo filtro único; `Empty` passa a significar **banco sem recorrência alguma** e carrega o filtro para o FAB; filtro sem resultado vira `Content` com lista vazia.
- [ ] 5.3 `RecurringAction`: substituir as duas ações de seleção por uma só.
- [ ] 5.4 `RecurringViewModel`: um único `MutableStateFlow<RecurringFilter>` (default `ACTIVE`); derivar a lista — `ACTIVE` = não arquivadas (sem seccionar, D6), `EXPENSE`/`INCOME` = não arquivadas do tipo, `ARCHIVED` = arquivadas de ambos os tipos. Remover a ordenação por `isActive` (o recorte já separa) e manter `createdAt`.

## 6. Tela de recorrências: UI (D6)

- [ ] 6.1 `RecurringScreen`: substituir os dois `DropdownMenu` da topbar por um só, iterando `RecurringFilter.entries` com `Check` no selecionado — mesmo lugar e forma do seletor de `CategoriesScreen`.
- [ ] 6.2 FAB deixa de depender de `Content`: passa a ser exibido também quando o filtro não retorna nada.
- [ ] 6.3 Dois empty-states distintos: o grande (com CTA) apenas para banco vazio; um compacto (texto + ícone, sem botão) para filtro sem resultado.
- [ ] 6.4 `RecurringCard`: trocar o badge "Inativa"/`Warning` por "Arquivada" com `Icons.Default.Archive`, alinhado ao `CategoryCard` — cor não pode ser o único diferenciador.

## 7. Strings (`core/resources`)

- [ ] 7.1 Arquivar/desarquivar recorrência: rótulos de ação, títulos e mensagens dos modais. A mensagem de arquivar preserva a promessa atual ("os lançamentos já gerados continuarão vinculados").
- [ ] 7.2 Reescrever a mensagem do modal de exclusão: ela agora só aparece para recorrência sem uso e SHALL dizer que não há histórico a perder, no lugar do genérico "não pode ser desfeita".
- [ ] 7.3 Motivos de recusa de exclusão de recorrência (um por razão do enum de 3.1).
- [ ] 7.4 Rótulos do seletor (`Ativas`, `Arquivadas`; reusar os de despesa/receita) e o texto do filtro vazio.
- [ ] 7.5 Adicionar as mesmas chaves em `values-en`.

## 8. Testes

- [ ] 8.1 `ResolveRecurringRetirabilityUseCase`: cada guard dispara o seu motivo; sem dependentes → `Deletable`; recorrência com **apenas** ciclo pulado → `Deletable`.
- [ ] 8.2 `DeleteRecurringUseCase`: recusa com o erro tipado quando em uso; delega a remoção quando apagável.
- [ ] 8.3 `UnarchiveRecurringUseCase` e `ArchiveRecurringUseCase`: escrevem o flag correto e retornam `Right(Unit)`.
- [ ] 8.4 `RecurringMapper`: inversão do flag nos **dois** sentidos.
- [ ] 8.5 `ViewRecurringViewModel`: arquivada oferece desarquivar (e não arquivar/apagar); não arquivada oferece a `RetireAction` resolvida (e não desarquivar).
- [ ] 8.6 `RecurringViewModel`: `ACTIVE` não inclui arquivadas; `ARCHIVED` lista só arquivadas; `Empty` só quando não há recorrência alguma.
- [ ] 8.7 `GetPendingRecurringUseCase`: recorrência arquivada não é apresentada como pendente.
- [ ] 8.8 Confirmação atômica: falha ao registrar a ocorrência não deixa a transação gravada; confirmar o mesmo ciclo duas vezes não grava segundo lançamento.
- [ ] 8.9 Ajustar os fakes existentes (`RecurringRepositoryTest`, fakes de orçamento) às novas assinaturas.

## 9. Validação

- [ ] 9.1 `openspec validate archive-and-redesign-recurring --strict`.
- [ ] 9.2 `./gradlew :app:shared:testDebugUnitTest` verde.
- [ ] 9.3 `./gradlew allTests` verde (inclui os testes de migração de `core/database`, que nomeiam a coluna `isActive`).
- [ ] 9.4 Ao sincronizar/arquivar: reescrever o *Purpose* de `account-lifecycle`, que hoje nomeia apenas conta, cartão e categoria, para incluir recorrência como quarta fachada.
- [ ] 9.5 Conferir na tela: arquivar → some das pendências e das listagens ativas → aparece em Arquivadas com badge → abrir → Desarquivar → volta a pendências e a Ativas.
- [ ] 9.6 Conferir a trava: recorrência com lançamento gerado oferece **Arquivar**; recorrência recém-criada oferece **Excluir** e apaga direto, sem exigir arquivar antes.
