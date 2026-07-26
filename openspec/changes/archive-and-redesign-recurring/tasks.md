> Ordem deliberada: a atomicidade (D7) vem primeiro por ser independente e por proteger o razão;
> o vocabulário e a retirabilidade vêm antes da UI, para que a tela nasça consumindo o dono único
> em vez de re-derivar a regra.

## 1. Atomicidade da confirmação (D7)

- [x] 1.1 `IRecurringOccurrenceRepository`: expor uma operação que grave **transação + ocorrência** como uma unidade de trabalho, recebendo o `TransactionIntent` e os dados da ocorrência (o `transactionId` só existe depois da escrita da transação) e **devolvendo a `Transaction` criada** — é o que `ConfirmRecurringUseCase` retorna.
- [x] 1.2 `RecurringOccurrenceRepository`: receber o handle do banco e `ITransactionRepository`; implementar a operação dentro de um único `useWriterConnection { immediateTransaction { … } }`, chamando `createTransaction` por dentro (o pool do Room é reentrante e a escrita interna vira `SAVEPOINT`). KDoc explicando a unidade de trabalho, no espírito do já existente em `RecurringRepository.delete`.
- [x] 1.3 Mover a checagem de reentrada (`existingOccurrence?.status != CONFIRMED`) para **dentro** dessa transação. Hoje ela é lida fora, o que a torna um TOCTOU, e o índice único `(recurringId, yearMonth)` não socorre porque `save()` é upsert: com linha existente ele faz `update` e sobrescreve em silêncio em vez de recusar.
- [x] 1.4 `ConfirmRecurringUseCase`: substituir o par `catch { createTransaction }.flatMap { catch { save(occurrence) } }` pela chamada única. Manter `GetOrCreateInvoiceForMonthUseCase` **fora** da transação (D7) e comentar por quê.
- [x] 1.5 Garantir que nenhuma troca de dispatcher ocorra entre as duas escritas — a reentrância do pool depende do elemento de contexto de corrotina.
- [x] 1.6 Atualizar o registro no `RecurringModule` (Koin) para as novas dependências do `RecurringOccurrenceRepository`.

## 2. Vocabulário: `isActive` → `isArchived` (D1, D9)

- [x] 2.1 `Recurring`: trocar `isActive: Boolean = true` por `isArchived: Boolean = false`; ajustar `hasUsableSource` e demais derivações se referenciarem o flag.
- [x] 2.2 `RecurringMapper`: inverter nos dois sentidos (`isArchived = !entity.isActive` / `isActive = !recurring.isArchived`) — a **única** tradução do flag.
- [x] 2.3 `RecurringEntity`: KDoc no campo `isActive` registrando que é o inverso de `Recurring.isArchived`, por que o nome diverge, e que o renome exige migração (dívida com dono).
- [x] 2.4 Renomear `StopRecurringUseCase` → `ArchiveRecurringUseCase` e `ReactivateRecurringUseCase` → `UnarchiveRecurringUseCase`, escrevendo `isArchived = true/false`. KDoc do desarquivar como "reversível e inócuo".
- [x] 2.5 Renomear os modais e ViewModels correspondentes (`stopRecurring` → `archiveRecurring`, `reactivateRecurring` → `unarchiveRecurring`) e os eventos de analytics.
- [x] 2.6 `SaveRecurringUseCase`: trocar o parâmetro `isActive: Boolean = true` por `isArchived: Boolean = false`.
- [x] 2.7 `RecurringFormViewModel:93` — **único call site** de 2.6: passar `isArchived = recurring?.isArchived ?: false`. Editar uma recorrência arquivada MUST preservar o flag; apagar o argumento por ser default a desarquivaria em silêncio.
- [x] 2.8 `GetPendingRecurringUseCase`: trocar o filtro `recurring.isActive` por `!recurring.isArchived`.
- [x] 2.9 `DashboardComponentsBuilder`: mesma troca no filtro de recorrências próximas.
- [x] 2.10 `BudgetFormViewModel:103` — **terceira leitura do flag, em outro módulo**: `incomeRecurrings` deixa de oferecer arquivadas **como escolha nova**, mas MUST preservar a continuidade de quem já usa. Aplicar a mesma forma de `offeredCategories` (`BudgetFormViewModel:250`, KDoc + `OfferedCategoriesTest`): abertas + a já selecionada que deixou de estar aberta. Sem isso, o `:106` — que resolve `budget.recurringId` **dentro** dessa lista — apagaria a receita base de um orçamento existente no instante em que a recorrência fosse arquivada.
- [x] 2.10a Considerar extrair a regra de continuidade numa forma única consumida pelas duas seleções do formulário (categorias e receita base), em vez de uma segunda cópia da lógica de `offeredCategories`.
- [x] 2.11 Confirmar por varredura que 2.7–2.10 esgotam as leituras do flag fora de `feature/recurring`.
- [x] 2.12 Atualizar os registros no `RecurringModule` (Koin) para os use cases e ViewModels renomeados.

## 3. Domínio: retirabilidade de recorrência (D2, D3, D4)

- [x] 3.1 `core/model`: criar o enum de motivos de recusa próprio de recorrência (razões: gerou lançamentos, orçamento aponta para ela) com `val message` em inglês para log e `toUiText()` via `UiText.Res`, no padrão de `RetireError` — **sem** alterar `RetireError`.
- [x] 3.2 `core/model`: criar `RecurringRetirability { Deletable | MustArchive(reason) }`, espelhando `CategoryRetirability` e com KDoc explicando por que não compartilha o tipo (D4).
- [x] 3.3 `RecurringDao`: consulta que responde se alguma transação nomeia a recorrência (`SELECT EXISTS(... FROM transactions WHERE recurringId = :id)`), ao lado de `detachTransactions` — a escrita/leitura de `transactions` mora aqui porque um DAO do razão não pode nomear metadado de fachada.
- [x] 3.4 `BudgetDao`: consulta que responde se algum orçamento aponta para a recorrência, espelhando `countByCategory`.
- [x] 3.5 `IBudgetRepository` + `BudgetRepository`: expor "existe orçamento para esta recorrência".
- [x] 3.6 `IRecurringRepository` + `RecurringRepository`: expor "existe transação para esta recorrência".
- [x] 3.7 Criar `ResolveRecurringRetirabilityUseCase` — dono único, resolvendo os dois guards na ordem e devolvendo `RecurringRetirability`.
- [x] 3.8 `DeleteRecurringUseCase`: consumir o resolver; `MustArchive` vira recusa com a exceção tipada, `Deletable` segue para `repository.delete`.
- [x] 3.9 `RecurringDao.detachTransactions`: manter como defesa em profundidade e acrescentar KDoc registrando que, com o guard de 3.7, o `UPDATE` já não é alcançável por construção — a remoção é recusada exatamente quando alguma transação nomeia o template.
- [x] 3.10 Registrar o resolver no `RecurringModule` (Koin).

## 4. UI: visualização da recorrência (D3, D9)

- [x] 4.1 `ViewRecurringUiState`: carregar a `RetireAction` resolvida (via `retireActionOf`) em vez de a tela decidir.
- [x] 4.2 `ViewRecurringViewModel`: consumir `ResolveRecurringRetirabilityUseCase`. Ele **não** conhece o desarquivar: como arquivar e excluir, a operação mora no ViewModel do seu próprio modal (D9).
- [x] 4.3 `UnarchiveRecurringModal`/`UnarchiveRecurringViewModel`: modal de confirmação do desarquivar, com o `dismissAll` e o evento de analytics no padrão dos irmãos. Sem `ViewRecurringAction` — as três retiradas saem da visualização por `manager.show(...)`.
- [x] 4.4 `ViewRecurringModal.Actions`: tornar as ofertas mutuamente exclusivas por `isArchived` — arquivada renderiza, **entre as ofertas de retirada**, apenas Desarquivar (ícone `Icons.Default.Unarchive`), abrindo o `UnarchiveRecurringModal`; não arquivada renderiza a `RetireAction` resolvida, abrindo o modal de arquivar ou o de excluir. Editar continua oferecido nos dois estados. Consumir `OutlinedActionButton` em vez do botão local.
- [x] 4.5 Remover o `Icons.Default.Delete` do botão de arquivar — o ícone passa a vir da `RetireAction`.
- [x] 4.6 `ViewRecurringModal:191-199`: a `DetailRow` de status deixa de dizer "Ativa/Inativa" e passa a "Ativa/Arquivada", com indicação que não dependa só da cor. Sem isto o modal mostraria "Status: Inativa" logo acima do botão "Desarquivar".
- [x] 4.7 `DeleteRecurringViewModel`: no `onLeft`, além do `crashlytics.recordException`, exibir o motivo via `modalManager.showError`. É este ViewModel — não o `ViewRecurringViewModel` — que invoca `DeleteRecurringUseCase`, e hoje ele falha em silêncio.
- [x] 4.8 Mesma exibição de motivo no ViewModel de arquivar, para que arquivar e excluir não divirjam.

## 5. Tela de recorrências: estado (D6)

- [x] 5.1 Criar `RecurringFilter { ACTIVE, EXPENSE, INCOME, ARCHIVED }` no pacote da tela, com KDoc registrando a mistura deliberada dos dois eixos; remover `RecurringStatusFilter` e o `RecurringFilter` antigo.
- [x] 5.2 `RecurringUiState`: substituir `selectedFilter`/`selectedStatusFilter` pelo filtro único; `Empty` passa a significar **banco sem recorrência alguma** e carrega o filtro para o FAB; filtro sem resultado vira `Content` com lista vazia.
- [x] 5.3 `RecurringAction`: substituir as duas ações de seleção por uma só.
- [x] 5.4 `RecurringViewModel`: um único `MutableStateFlow<RecurringFilter>` (default `ACTIVE`); derivar a lista — `ACTIVE` = não arquivadas (sem seccionar, D6), `EXPENSE`/`INCOME` = não arquivadas do tipo, `ARCHIVED` = arquivadas de ambos os tipos. Remover a ordenação por `isActive` (o recorte já separa) e manter `createdAt`.

## 6. Tela de recorrências: UI (D6)

- [x] 6.1 `RecurringScreen`: substituir os dois `DropdownMenu` da topbar por um só, iterando `RecurringFilter.entries` com `Check` no selecionado — mesmo lugar e forma do seletor de `CategoriesScreen`.
- [x] 6.2 FAB deixa de depender de `Content`: passa a ser exibido também quando o filtro não retorna nada.
- [x] 6.3 Dois empty-states distintos: o grande (com CTA) apenas para banco vazio; um compacto (texto + ícone, sem botão) para filtro sem resultado.
- [x] 6.4 `RecurringCard`: trocar o badge "Inativa"/`Warning` por "Arquivada" com `Icons.Default.Archive`, alinhado ao `CategoryCard` — cor não pode ser o único diferenciador.

## 7. Strings (`core/resources`)

- [x] 7.1 Arquivar/desarquivar recorrência: rótulos de ação, títulos e mensagens dos modais. A mensagem de arquivar preserva a promessa atual ("os lançamentos já gerados continuarão vinculados").
- [x] 7.2 Reescrever a mensagem do modal de exclusão: ela agora só aparece para recorrência sem uso e diz que não há histórico a perder, no lugar do genérico "não pode ser desfeita".
- [x] 7.3 Motivos de recusa de exclusão de recorrência (um por razão do enum de 3.1).
- [x] 7.4 `recurring_status_active`/`recurring_status_inactive` passam a "Ativa"/"Arquivada", consumidas pela `DetailRow` (4.6) e pelo badge do card (6.4).
- [x] 7.5 Rótulos do seletor (`Ativas`, `Arquivadas`; reusar os de despesa/receita) e o texto do filtro vazio.
- [x] 7.6 Corrigir `retire_error_has_recurring` e `account_error_has_recurring` (pt e en): hoje mandam "encerre-as antes de excluir" / "stop them before deleting", caminho que nunca desbloqueou nada porque `countByCategory`/`countByAccount`/`countByCreditCard` contam qualquer template independentemente do flag. Passam a nomear os caminhos reais — reapontar para outra categoria/destino ou excluir. Os counts **não** mudam: arquivamento não anula guard.
- [x] 7.7 Remover as strings órfãs: `recurring_filter_all`, `recurring_filter_status_active/inactive/all`, `view_recurring_stop`, `view_recurring_reactivate` — em `values` e `values-en`.
- [x] 7.8 Adicionar em `values-en` todas as chaves novas desta seção.
- [x] 7.9 Fechar a paridade `values`/`values-en`: acrescentar as oito chaves que só existiam em pt — as três `retire_error_*` (com o texto já corrigido em 7.6) e as cinco de categoria herdadas de `unarchive-and-redesign-categories`. Conferir por diferença de chaves entre os dois arquivos, não por leitura.

## 8. Testes

- [x] 8.1 `ResolveRecurringRetirabilityUseCase`: cada guard dispara o seu motivo; sem dependentes → `Deletable`; recorrência com **apenas** ciclo pulado → `Deletable`.
- [x] 8.2 `DeleteRecurringUseCase`: recusa com o erro tipado quando em uso; delega a remoção quando apagável.
- [x] 8.3 `UnarchiveRecurringUseCase` e `ArchiveRecurringUseCase`: escrevem o flag correto e retornam `Right(Unit)`.
- [x] 8.4 `RecurringMapper`: inversão do flag nos **dois** sentidos.
- [x] 8.5 `ViewRecurringViewModel`: a `RetireAction` resolvida chega ao estado — `DELETE` para recorrência sem uso, `ARCHIVE` para a que gerou lançamentos. Que a arquivada ofereça só desarquivar é decisão de renderização do modal; a escrita do flag está coberta em 8.3.
- [x] 8.6 `RecurringViewModel`: `ACTIVE` não inclui arquivadas; `ARCHIVED` lista só arquivadas; `Empty` só quando não há recorrência alguma.
- [x] 8.7 `GetPendingRecurringUseCase`: recorrência arquivada não é apresentada como pendente.
- [x] 8.8 Confirmação atômica: falha ao registrar a ocorrência não deixa a transação gravada; confirmar o mesmo ciclo duas vezes não grava segundo lançamento.
- [x] 8.9 `RecurringFormViewModel`: salvar uma recorrência arquivada a mantém arquivada.
- [x] 8.10 `BudgetFormViewModel`, espelhando `OfferedCategoriesTest`: recorrência arquivada não é oferecida a um orçamento novo; um orçamento que já a elegeu continua exibindo-a e consegue trocá-la; desfeita a troca, ela não volta a ser oferecível enquanto arquivada.
- [x] 8.11 Atualizar os **fakes de `IRecurringRepository`** afetados por 3.6 — são sete, em cinco módulos: `CreditCardsEmptyStateTest`, `InvoiceTransactionsFakes`, `DeleteCreditCardUseCaseTest` (creditcards), `RetireAccountGuardsTest` (accounts), `ViewBudgetViewModelTest` (budgets), `ViewCategoryViewModelTest` e `DeleteCategoryGuardsTest` (categories).
- [x] 8.12 Atualizar os **fakes de `IBudgetRepository`** afetados por 3.5: `ViewBudgetViewModelTest`, `ViewCategoryViewModelTest`, `DeleteCategoryGuardsTest`.
- [x] 8.13 `RecurringRepositoryTest` (jvmTest contra o repositório real): estender para o novo método de 3.6.
- [x] 8.14 `UnarchiveRecurringViewModel`: confirmar escreve o flag, registra o evento e fecha a folha; falha registra no crashlytics **sem** registrar evento e **sem** fechar — uma folha fechada leria como sucesso. É o único desarquivar confirmado do app (D9), então a fiação do modal é coberta em vez de presumida.

## 9. Validação

- [x] 9.1 `openspec validate archive-and-redesign-recurring --strict`.
- [x] 9.2 `./gradlew :app:shared:testDebugUnitTest` verde.
- [x] 9.4 Conferir que o sync aplicou o bloco `## MODIFIED Requirements` do delta, e reescrever o *Purpose* de `account-lifecycle`: além de nomear a quarta fachada, a premissa atual ("Partidas dobradas não admitem apagar aquilo que entries referenciam") não cobre recorrência, que entry alguma referencia — precisa abranger também a história própria da fachada.
- [x] 9.5 Conferir na tela: arquivar → some das pendências e das listagens ativas → aparece em Arquivadas com badge → abrir → Desarquivar → volta a pendências e a Ativas.
- [x] 9.6 Conferir a trava: recorrência com lançamento gerado oferece **Arquivar**; recorrência recém-criada oferece **Excluir** e apaga direto, sem exigir arquivar antes.
