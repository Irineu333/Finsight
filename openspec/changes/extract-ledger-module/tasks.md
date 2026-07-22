# Ordem de execução

O princípio que governa a ordem: **descontaminar no lugar → migrar o schema → purificar a
API de escrita → mover uma vez, já puro.** A extração do módulo é a penúltima fase, não a
primeira: as pré-condições para mover cada classe *são* as limpezas, então mover antes é
impossível por construção.

Restrição de release: a v10 é escrita em dois estágios (grupos 4 e 5). **Nenhuma versão
entre o grupo 4 e o grupo 5 pode ser publicada** — um v10 parcial num dispositivo obrigaria
um v11.

## 1. Confirmar as premissas antes de mover qualquer coisa

- [x] 1.1 Decidido: `Dimension.kind` é o enum `DimensionKind` de `:core:ledger`, persistido pelo nome, cujo único dado é a regra de pouso (`landsOn: Set<AccountType>`) — a opacidade protegida é comportamental, não textual (`design.md`, D7)
- [x] 1.2 Decidido: a regra de pouso é dado do razão e invariante do tipo — propriedade da entrada do enum, nunca declarada pela feature ao emitir a dimensão nem estado por linha (`design.md`, D8)
- [x] 1.3 Decidido: nasce o convention plugin `finsight.room.library` (KMP + Room + KSP por target), aplicado a `:core:ledger` e `:core:database`; a garantia de compilação de D1 exige o `LedgerDatabase` interno de verificação em `:core:ledger` (`design.md`, D9)
- [x] 1.4 Constatado e decidido: as duas contas nominais são invisíveis por construção — todo predicado de UI já filtra `type = 'ASSET'`/`isMonetary`, como já acontece com a de reconciliação; seus nomes são chaves em `SystemAccount`, jamais renderizados (`design.md`, D10)
- [x] 1.5 Decidido: o veto de fatura fechada vai para uma porta opaca keyed por dimensão, no ponto único de escrita; a contabilidade de parcelamento no delete vai para o use case de creditcards, por nunca ter sido invariante (`design.md`, D11)
- [x] 1.6 Decidido: as FKs de parcelamento e recorrência caem no rebuild de `transactions`, e a anulação que elas davam ganha dono explícito nos caminhos de remoção das fachadas (`design.md`, D12)
- [x] 1.7 Spike do Room num módulo descartável, fora da árvore de produção, respondendo: (a) um `@Dao` em módulo sem `@Database` gera implementação utilizável a partir de um `@Database` noutro módulo, ou não gera nada? (b) a string SQL de um `@Query` é validada contra o schema de qual `@Database` — o `LedgerDatabase` interno resolve mesmo a garantia? (c) o `AppDatabase` monta, migra e exporta schema com entities de outro módulo, em Android, JVM e iOS? (d) uma entity de fachada consegue declarar FK para uma entity do razão em outro módulo — necessário para `recurring_occurrences → transactions` e para `invoices`/`categories → dimensions`? (e) injetar o supertipo `RoomDatabase` basta para o razão abrir transação, sem conhecer `AppDatabase` (`TransactionRepository.kt:289,342`)?
- [x] 1.8 Registrar o resultado do spike em `design.md`. Se algum item cair pelo critério de D1 (Room não gera implementação utilizável; conflito irreconciliável entre `LedgerDatabase` e `AppDatabase`; migração incapaz de referenciar o schema do razão; falha em algum target): adotar a direção (a), ajustar D1/D9 e a spec `ledger-module-boundary`, e escrever o teste que lê as strings SQL do razão — sem ele o fallback não é adotado. Os grupos 2 a 7 valem nas duas direções; só o grupo 8 muda
- [x] 1.9 Apagar o módulo do spike

## 2. Convenção de build e arcabouço de verificação

- [x] 2.1 Criar o convention plugin `finsight.room.library` em `build-logic` (`configureKotlinMultiplatform()` + plugins `ksp`/`room` + `schemaDirectory` + `room-compiler` em cada configuração KSP por target) e migrar `:core:database` para ele
- [ ] 2.2 Confirmar que `9.json` não muda e que `allTests`, `:app:android:assembleDebug` e o link iOS seguem verdes
- [x] 2.3 Escrever o helper de verificação de `Σ = 0` por transação e por moeda, executável dentro de uma migração e em teste
- [x] 2.4 Estender `MigrationLedgerReadParityTest` com plumbing para capturar as figuras exibidas **keyed por id de fachada** (saldo por conta, devido por fatura-id, total por categoria-id, patrimônio), computando o "antes" por SQL cru sobre o banco v9 — a paridade precisa ser independente do mecanismo, porque é o mecanismo que muda
- [x] 2.5 Rodar o arcabouço sobre a cadeia v7→v9 existente, onde deve passar trivialmente

## 3. Anulações explícitas que hoje vêm de graça das FKs

- [x] 3.1 Fazer o caminho de remoção de parcelamento anular `transactions.installmentId`/`installmentNumber` na mesma transação (D12) — redundante enquanto a FK vive, o que torna a mudança aditiva
- [x] 3.2 Idem para recorrência: `transactions.recurringId`/`recurringCycle`
- [x] 3.3 Teste de característica: remover parcelamento ou recorrência não deixa referência pendurada, com e sem a FK

## 4. v10, primeira metade — dimensões e fatura

- [x] 4.1 Criar `:core:ledger` sob `finsight.room.library` contendo **apenas** `DimensionEntity(id, kind)`, `DimensionKind(landsOn)` (D7/D8), `DimensionDao` e o type converter; fazer `:core:database` depender dele e listar a entity no `AppDatabase` — exercita a direção invertida de D1 numa superfície de dois arquivos, em vez de descobrir problemas movendo vinte
- [x] 4.2 Migração (passos 1 a 4 de `design.md`): verificar `Σ = 0` antes de tocar em nada; criar `dimensions`; emitir dimensão `INVOICE` por fatura e preencher `invoices.dimensionId`; **rebuild de `entries`** trocando `invoiceId` por `dimensionId` (FK → `dimensions`, `SET NULL`), com os índices recriados
- [x] 4.3 Emitir a dimensão na criação de fatura e **remover a linha de `dimensions` na remoção da fatura**, na mesma transação — é o que substitui o `SET NULL` que hoje vem do FK `entries.invoiceId → invoices`
- [x] 4.4 Implementar no writer a validação de pouso via `account.type in kind.landsOn` — uniforme, sem `when` por kind — com erro tipado, no mesmo ponto da validação de soma zero
- [x] 4.5 Converter `invoiceNaturalBalance`, `invoicePeriodTotals` e `categoryTotalsForInvoices` para agregar por dimensão, renomeadas para vocabulário de razão; converter `ensureInvoiceAcceptsRemoval` (`TransactionRepository.kt:238-241`) para mapear entries → fatura via dimensão
- [x] 4.6 Colapsar `TransactionDao.observeBy` (`TransactionDao.kt:52-65`): o `JOIN credit_cards` é redundante com o filtro por conta — "transação do cartão X" é "transação com perna na conta `LIABILITY` de X`". A assinatura passa a `(date, dimensionId, accountId)`, e quem resolvia `creditCardId` passa `creditCard.accountId`
- [x] 4.7 Rodar `allTests` e os testes de migração; confirmar paridade do devido por fatura e do breakdown por fatura, keyed por id, sobre um banco v9 representativo

## 5. v10, segunda metade — categoria como dimensão ⚠

A fatia genuinamente arriscada. É grande de propósito: schema, writer e leituras de
categoria são a mesma verdade, e parti-los criaria colunas de transição. O que a torna
verificável está em 5.10 e 5.11.

- [ ] 5.1 Migração (passos 5 a 12 de `design.md`), na ordem exata: snapshots `_cat_map` e `_uncat` **antes de qualquer drop**; dimensões `CATEGORY` com offset; garantir as duas nominais; reescrever as pernas de conta de categoria pelo snapshot; reescrever as pernas em `UNCATEGORIZED_*`; rebuild de `categories` sem `accountId`; rebuild de `transactions` sem `categoryId` e sem as FKs de parcelamento/recorrência (D12); remoção guardada das contas, com contagem residual que aborta
- [ ] 5.2 Verificação final da migração (passo 13): `Σ = 0` de novo, nenhuma entry com dimensão órfã, `PRAGMA foreign_key_check` limpo
- [ ] 5.3 `Category` perde `accountId` e ganha `dimensionId`; `type` vira estado primário, com a exceção ao Derivation Rule documentada no ponto de uso; `isArchived` passa a ser próprio
- [ ] 5.4 Emitir a dimensão na criação da categoria e **remover a linha de `dimensions` na remoção da categoria**, na mesma transação
- [ ] 5.5 Writer: `contraAccountId` (`LedgerEntryWriter.kt:127-138`) passa a postar na nominal do `Category.type` com a dimensão da categoria; "sem categoria" vira nominal com dimensão nula; o `ensureSystemAccount(UNCATEGORIZED_*)` morre
- [ ] 5.6 Remover `SystemAccount.UNCATEGORIZED_EXPENSE` e `UNCATEGORIZED_INCOME` apenas do código de produção — o SQL da v7→v9 (`Database.kt:317-320`) permanece intocado, e a v10 localiza essas contas por literal inline
- [ ] 5.7 Converter as leituras por conta de categoria para dimensão: `categoryTotalsWithSiblingLeg`, o uso de `balanceInMonth` para gasto de categoria, e `entryCountInMonth`; renomeá-las para vocabulário de razão
- [ ] 5.8 Criar a variante por dimensão de `hasEntries` e converter `DeleteCategoryUseCase.kt:33` e `ViewCategoryViewModel.kt:64` para ela — é o gate apagar-vs-arquivar que `account-lifecycle` exige, e sem `category.accountId` ele desaparece
- [ ] 5.9 Adicionar a leitura do total sem classificação (entries sem dimensão na conta nominal), pelo mesmo mecanismo; converter budgets, report e dashboard
- [ ] 5.10 **Portão:** teste de que a migração aborta integralmente quando uma transação semeada não balanceia — escrito junto com a reescrita, para que teste algo que existe
- [ ] 5.11 **Portão:** paridade por figura contra os snapshots, keyed por id — saldo por conta, devido por fatura, patrimônio e total por categoria idênticos antes e depois; `MigrationSchemaEquivalenceTest` provando que migração e entities produzem o mesmo schema
- [ ] 5.12 Rodar o app Desktop com um banco v9 migrado e conferir os quatro números manualmente

## 6. `Transaction` perde o grafo de fachada

Depois do grupo 5 de propósito: as features escrevem a própria hidratação **uma vez**, contra
o schema final. Fazê-lo antes obrigaria cada consumidor a trocar de chave duas vezes.

- [ ] 6.1 Remover de `Transaction` (`Transaction.kt:18-23`) os campos `category`, `sourceAccount`, `targetCreditCard`, `targetInvoice`, `installment` e `recurring`, mantendo os escalares de parcelamento e recorrência
- [ ] 6.2 Encolher `TransactionMapper` para linha + entries; remover de `TransactionRepository` os flows de hidratação (`TransactionRepository.kt:60-82,98-120`) e as dependências `ICategoryRepository`, `ICreditCardRepository`, `IInstallmentRepository`, `RecurringDao` e `RecurringMapper`
- [ ] 6.3 Cada feature hidrata a própria fachada a partir das entries: cartão pela perna `LIABILITY` + `creditCard.accountId`; fatura pela dimensão da perna `LIABILITY`; categoria pela dimensão da perna nominal; parcelamento e recorrência pelos escalares. Consumidores: `InstallmentUiMapper.kt:16-69`, `InstallmentsViewModel`, `CreditCardsViewModel`, `InvoiceTransactionsViewModel`, `ViewTransactionUiState.kt:39-76`, `EditTransactionViewModel.kt:48-95`, `ViewAdjustmentUiState`, `ReportViewerViewModel.kt:166`, `TransactionsViewModel`, `DashboardPreviewFactory`
- [ ] 6.4 Preservar a derivação de editabilidade e o gate visual de fatura fechada, que hoje saem de `targetInvoice`/`installment` — o status da fatura passa a vir da fachada hidratada pela feature
- [ ] 6.5 `allTests` verde consumidor a consumidor; smoke manual dos modais de ver e editar transação e da tela de parcelas

## 7. Intenção de escrita por identidade, e o que sobra de fachada no repositório

- [ ] 7.1 Trocar `TransactionLeg` para `(type, amount, accountId: Long, dimensionId: Long?)`, removendo `Account`, `CreditCard`, `Invoice`, `Category` e a propriedade `target`; remover o enum `TransactionTarget`
- [ ] 7.2 Remover `CategoryDao` e `CreditCardDao` de `LedgerEntryWriter`, deixando `EntryDao` + `AccountDao`; `ensureSystemAccount` fica, porque `accounts` é tabela do razão
- [ ] 7.3 Mover a resolução fachada → identidade para cada chamador: `AddInstallmentUseCase`, `PayInvoicePaymentUseCase`, `AdjustInvoiceUseCase`, `AdvanceInvoicePaymentUseCase`, `DeleteFutureInvoiceUseCase`, `TransferBetweenAccountsUseCase`, `AdjustBalanceUseCase`, `ConfirmRecurringUseCase`, `BuildTransactionUseCaseImpl` e os ViewModels de transações
- [ ] 7.4 Re-expressar `settlesACard()` (`TransactionRepository.kt:278-279`) por natureza de conta, resolvida a partir dos `accountId` do intent
- [ ] 7.5 Extrair o veto de fatura fechada (`ensureInvoicesAccept`, `TransactionRepository.kt:218-241,327-338`) para a porta opaca keyed por dimensão declarada em `:core:ledger` e implementada por creditcards (D11); `InvoiceWriteGuardTest` continua provando a invariante nos dois sentidos
- [ ] 7.6 Mover a contabilidade de parcelamento de `removeRow` (`TransactionRepository.kt:370-402`) para o use case de deleção de creditcards, mantendo a atomicidade na mesma transação de escrita (D11)
- [ ] 7.7 Trocar `AppDatabase` por `RoomDatabase` no construtor de `TransactionRepository` (`:45,289`), conforme confirmado em 1.7e
- [ ] 7.8 Registrar em `design.md` que `closedLegBlockingChange` segue correta sem par por dimensão — filtra por `type.isPermanent`, e categoria nunca foi permanente
- [ ] 7.9 Verificar por grep que `LedgerEntryWriter`, `EntryRepository` e `TransactionRepository` não importam `Category`, `CreditCard`, `Invoice` nem `Installment` — é a pré-condição exata do grupo 8
- [ ] 7.10 Reescrever `LedgerEntryWriterTest` em vocabulário de identidade, cobrindo soma zero, pouso e fechamento no mesmo ponto

## 8. Mover o razão para `:core:ledger`

- [ ] 8.1 Mover, mantendo os pacotes Kotlin inalterados para que o diff não cause churn de import: `AccountEntity`, `TransactionEntity`, `EntryEntity`, `AccountDao`, `EntryDao`, `TransactionDao`; `Account`, `AccountType`, `Entry`, `Transaction`, `SystemAccount`, `Currency`/`BASE_CURRENCY`, `extension/Ledger.kt`, `LedgerError` e as exceções do razão; `IEntryRepository`, `ITransactionRepository`, `CalculateBalanceUseCase`; `EntryRepository`, `LedgerEntryWriter`, `TransactionRepository`; os converters que as entities do razão usam
- [ ] 8.2 Declarar o `LedgerDatabase` interno de verificação em `:core:ledger`, listando só as entities do razão (D9)
- [ ] 8.3 Fazer `:core:database` montar o `AppDatabase` com as entities importadas; as migrações ficam onde estão
- [ ] 8.4 Mover os testes de query (`EntryCategoryQueryTest`, `InvoiceAndCardQueryTest`, `AccountPeriodTotalsQueryTest`, `ReportStatsQueryTest`, `BalanceUpToMonthQueryTest`) e os do writer e repositórios para `:core:ledger`, rodando sobre o `LedgerDatabase`; os de migração ficam em `:core:database`
- [ ] 8.5 Expor o módulo Koin do razão em `:core:ledger` e agregá-lo em `:app:shared`, removendo essas ligações de `TransactionsModule`; exportar `:core:ledger` no framework iOS
- [ ] 8.6 Acrescentar `api(projects.core.ledger)` a `feature:transactions:api` — linha temporária, de vida de uma fatia, para que as oito consumidoras compilem sem mudar seus `build.gradle.kts` e o move seja verificável isolado
- [ ] 8.7 Teste-sentinela de D9: acrescentar localmente um `@Query` com `JOIN invoices` ao `EntryDao` e confirmar que `:core:ledger` **não compila**; remover em seguida, sem commitar
- [ ] 8.8 `allTests`, `assembleDebug`, `:app:desktop:run` e link iOS verdes, com zero mudança de comportamento

## 9. Trocar as features de dependência

- [ ] 9.1 Substituir `projects.feature.transactions.api` por `projects.core.ledger` em `accounts:impl`, `creditcards:impl`, `categories:impl`, `budgets:impl`, `report:impl`, `dashboard:impl`, `recurring:impl` e `shell:impl`, mantendo transactions apenas onde a tela é de fato usada — um commit verde por troca
- [ ] 9.2 Mover a leitura do razão para dentro de `CalculateBudgetProgressUseCase`, na `api` de budgets, removendo o repasse do número já calculado pelo `impl` — agora legal, porque `:core:*` é acessível a uma `api`
- [ ] 9.3 Remover a linha temporária `api(projects.core.ledger)` de `feature:transactions:api` e confirmar que ela expõe apenas rotas, `TransactionsEntry` e nav types

## 10. Verificação final

- [ ] 10.1 Auditar as assinaturas públicas de `:core:ledger` e confirmar que nenhuma nomeia fatura, cartão, categoria, orçamento ou relatório
- [ ] 10.2 Auditar o SQL de `:core:ledger` e confirmar que todo JOIN é entre tabelas do razão
- [ ] 10.3 Confirmar por grep que nenhuma feature depende de `feature:transactions:api` para ler ou escrever no razão
- [ ] 10.4 Rodar `./gradlew allTests`
- [ ] 10.5 Executar o app em Android e Desktop com um banco migrado de v9 e conferir visualmente saldos, faturas, gastos por categoria e relatórios
- [ ] 10.6 Atualizar `CLAUDE.md` e `feature/README.md` com o novo módulo, a direção da dependência e as exceções documentadas: `Category.type`, as colunas de parcelamento/recorrência e as FKs removidas
