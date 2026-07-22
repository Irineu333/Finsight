## 1. Resolver as questões abertas do design

- [x] 1.1 Decidido: `Dimension.kind` é o enum `DimensionKind` de `:core:ledger`, persistido pelo nome, cujo único dado é a regra de pouso (`landsOn: Set<AccountType>`) — a opacidade protegida é comportamental, não textual (`design.md`, D7)
- [x] 1.2 Decidido: a regra de pouso é dado do razão e invariante do tipo — propriedade da entrada do enum, nunca declarada pela feature ao emitir a dimensão nem estado por linha (`design.md`, D8)
- [x] 1.3 Decidido: nasce o convention plugin `finsight.room.library` (KMP + Room + KSP por target), aplicado a `:core:ledger` e `:core:database`; a garantia de compilação de D1 exige o `LedgerDatabase` interno de verificação em `:core:ledger` (`design.md`, D9)
- [x] 1.4 Constatado e decidido: as duas contas nominais são invisíveis por construção — todo predicado de UI já filtra `type = 'ASSET'`/`isMonetary`, como já acontece com a de reconciliação; seus nomes são chaves em `SystemAccount`, jamais renderizados (`design.md`, D10)

## 2. Criar `:core:ledger` e mover o razão sem mudar comportamento

- [ ] 2.0 Confirmar empiricamente, num módulo descartável, o comportamento do Room que D9 pressupõe: (a) um `@Dao` em módulo sem nenhum `@Database` gera implementação, ou não gera nada? (b) a string SQL de um `@Query` é validada contra o schema de qual `@Database`? (c) o `AppDatabase` consegue montar e migrar entities declaradas em outro módulo, exportando schema, em todos os targets? Se o `LedgerDatabase` for condição de compilar — e não apenas de verificar — a ordem deste grupo muda e D1/D9 precisam ser reescritos antes de prosseguir
- [ ] 2.0a Se algum resultado de 2.0 for crítico pelo critério do fallback em D1 (Room não gera implementação utilizável; conflito irreconciliável de schema/código gerado entre `LedgerDatabase` e `AppDatabase`; migração incapaz de referenciar o schema do razão; falha em algum target): registrar em `design.md` qual premissa caiu e com que erro, adotar a direção (a) `core:ledger → core:database`, e ajustar D1, D9 e a spec `ledger-module-boundary` para refletir a direção efetiva
- [ ] 2.0b Se o fallback for adotado: escrever o teste que lê as strings SQL dos `@Query` do razão e falha se alguma referenciar tabela fora do razão — a garantia da separação passa a ser dele, e o fallback não é adotado sem esse teste
- [ ] 2.1 Criar o convention plugin `finsight.room.library` em `build-logic` (`configureKotlinMultiplatform()` + plugins `ksp`/`room` + `schemaDirectory` + `room-compiler` em cada configuração KSP por target) e migrar `:core:database` para ele, sem mudança de comportamento
- [ ] 2.2 Criar o módulo `:core:ledger` (`settings.gradle.kts` + `build.gradle.kts`) sob `finsight.room.library`, dependendo apenas de Room, `:core:model` e das libs de base — sem nenhum `feature:*` e sem `:core:database`
- [ ] 2.3 Mover `AccountEntity`, `TransactionEntity`, `EntryEntity`, `AccountDao`, `EntryDao` e `TransactionDao` de `:core:database` para `:core:ledger`
- [ ] 2.4 Declarar o `LedgerDatabase` interno de verificação em `:core:ledger`, listando só as entities do razão — é ele que faz um `@Query` referenciando tabela de fachada falhar na compilação do próprio módulo (D9), e é o banco dos testes de query movidos
- [ ] 2.5 Fazer `:core:database` depender de `:core:ledger` e montar o `AppDatabase` com as entities importadas; confirmar que `:core:ledger` não referencia `:core:database`
- [ ] 2.6 Mover `Account`, `AccountType`, `Entry`, `Transaction`, `SystemAccount`, `Currency`/`BASE_CURRENCY` e `extension/Ledger.kt` de `:core:model` para `:core:ledger`
- [ ] 2.7 Mover `LedgerError` e as exceções do razão de `:core:model` para `:core:ledger`
- [ ] 2.8 Mover `IEntryRepository`, `ITransactionRepository` e `CalculateBalanceUseCase` de `feature:transactions:api` para `:core:ledger`
- [ ] 2.9 Mover `EntryRepository`, `LedgerEntryWriter` e `TransactionRepository` de `feature:transactions:impl` para `:core:ledger`
- [ ] 2.10 Mover os testes de query do razão (`EntryCategoryQueryTest`, `InvoiceAndCardQueryTest`, `AccountPeriodTotalsQueryTest`, `ReportStatsQueryTest`, `BalanceUpToMonthQueryTest`) e os testes do writer/repositórios para `:core:ledger`, rodando sobre o `LedgerDatabase`
- [ ] 2.11 Expor o módulo Koin do razão em `:core:ledger` e agregá-lo em `:app:shared`, removendo essas ligações de `TransactionsModule`
- [ ] 2.12 Exportar `:core:ledger` no framework iOS em `app/ios/build.gradle.kts`
- [ ] 2.13 Rodar `./gradlew allTests` e confirmar verde sem nenhuma mudança de comportamento

## 3. Trocar as features de dependência

- [ ] 3.1 Substituir `projects.feature.transactions.api` por `projects.core.ledger` nos `build.gradle.kts` de `accounts:impl`, `creditcards:impl`, `categories:impl`, `budgets:impl`, `report:impl`, `dashboard:impl`, `recurring:impl` e `shell:impl`, mantendo a dependência de transactions apenas onde a tela for de fato usada
- [ ] 3.2 Mover a leitura do razão em `CalculateBudgetProgressUseCase` de volta para a `api` de budgets, removendo o repasse do número já calculado pelo `impl`
- [ ] 3.3 Confirmar que `feature:transactions:api` expõe apenas rotas, `TransactionsEntry` e nav types
- [ ] 3.4 Rodar `./gradlew allTests` e confirmar verde

## 4. Intenção de escrita por identidade

- [ ] 4.1 Trocar `TransactionLeg` para `accountId: Long` + `dimensionId: Long?`, removendo `Account`, `CreditCard`, `Invoice`, `Category` e a propriedade `target`
- [ ] 4.2 Remover o enum `TransactionTarget` e todos os seus usos
- [ ] 4.3 Remover `CategoryDao` e `CreditCardDao` de `LedgerEntryWriter`, deixando `EntryDao` + `AccountDao`
- [ ] 4.4 Mover a resolução fachada → identidade para cada feature chamadora (`AddInstallmentUseCase`, `PayInvoicePaymentUseCase`, `AdjustInvoiceUseCase`, `AdvanceInvoicePaymentUseCase`, `DeleteFutureInvoiceUseCase`, `TransferBetweenAccountsUseCase`, `AdjustBalanceUseCase`, `ConfirmRecurringUseCase` e os ViewModels de transações)
- [ ] 4.5 Remover de `Transaction` os campos `category`, `sourceAccount`, `targetCreditCard`, `targetInvoice`, `installment` e `recurring`, e hidratar cada fachada na feature dona a partir das entries
- [ ] 4.6 Rodar `./gradlew allTests` e confirmar verde

## 5. Dimensões e migração da fatura (v10, primeira metade)

- [ ] 5.1 Criar a entity `dimensions(id, kind)` e seu DAO em `:core:ledger`, com o enum `DimensionKind(val landsOn: Set<AccountType>)` persistido pelo nome (D7/D8) e o type converter correspondente
- [ ] 5.2 Adicionar `entries.dimensionId` (FK → `dimensions`, `ON DELETE SET NULL`) e o índice correspondente
- [ ] 5.3 Adicionar `invoices.dimensionId` e emitir uma dimensão `INVOICE` por fatura existente
- [ ] 5.4 Implementar no writer a validação de pouso via `account.type in kind.landsOn` — uniforme, sem `when` por kind — com erro tipado, no mesmo ponto da validação de soma zero
- [ ] 5.5 Escrever o helper de verificação de `Σ = 0` por transação e por moeda, usável dentro da migração e nos testes
- [ ] 5.6 Migrar `entries.invoiceId` → `dimensionId` via `invoices.dimensionId` e remover a coluna `invoiceId`
- [ ] 5.7 Converter `invoiceNaturalBalance`, `invoicePeriodTotals` e `categoryTotalsForInvoices` para agregar por dimensão, renomeando-as para vocabulário de razão
- [ ] 5.8 Rodar `./gradlew allTests` e confirmar verde

## 6. Portão de verificação

- [ ] 6.1 Escrever o teste de migração que verifica `Σ = 0` por transação e por moeda antes e depois da reescrita, e que a migração aborta integralmente quando alguma transação não balanceia
- [ ] 6.2 Estender `MigrationLedgerReadParityTest` para comparar antes/depois cada figura exibida: saldo por conta, devido por fatura, patrimônio líquido e total por categoria
- [ ] 6.3 Confirmar que ambos passam antes de iniciar a conversão de categoria

## 7. Categoria como dimensão (v10, segunda metade)

- [ ] 7.1 Semear as duas contas nominais (`EXPENSE` e `INCOME`) com nomes-chave em `SystemAccount` e garantir sua existência sob demanda — invisíveis por construção (D10): nenhuma listagem sai de `WHERE type = 'ASSET'` e o patrimônio líquido soma só `ASSET`/`LIABILITY`, então nenhum predicado de UI muda
- [ ] 7.2 Adicionar `categories.dimensionId` e emitir uma dimensão `CATEGORY` por categoria existente
- [ ] 7.3 Adicionar `categories.isArchived`, preenchendo a partir de `accounts.isArchived` pelo `accountId` antigo
- [ ] 7.4 Reescrever cada perna cujo `accountId` é conta de categoria: `accountId` ← nominal do tipo correspondente, `dimensionId` ← dimensão da categoria
- [ ] 7.5 Reescrever as pernas em `UNCATEGORIZED_EXPENSE`/`_INCOME`: `accountId` ← nominal, `dimensionId` ← `NULL`
- [ ] 7.6 Remover `categories.accountId`, `transactions.categoryId`, e do plano de contas as contas de categoria e as `UNCATEGORIZED_*` (já sem entries)
- [ ] 7.7 Remover `SystemAccount.UNCATEGORIZED_EXPENSE` e `UNCATEGORIZED_INCOME` do código e do SQL de migração legado
- [ ] 7.8 Fazer `Category.type` ser o dono da natureza e o que escolhe a conta nominal no writer, documentando a exceção ao Derivation Rule no ponto de uso
- [ ] 7.9 Passar a ler o arquivamento de categoria da própria fachada, removendo o vínculo com `Account.isArchived`
- [ ] 7.10 Rodar a migração v9 → v10 completa e confirmar que os testes do grupo 6 passam

## 8. Ajustar as leituras que operavam por conta de categoria

- [ ] 8.1 Converter `categoryTotalsWithSiblingLeg` e `balanceInMonth` (uso de categoria) para agregar por dimensão, renomeando para vocabulário de razão
- [ ] 8.2 Criar a variante por dimensão de `entryCountInMonth`, que é usada por categoria
- [ ] 8.3 Registrar por escrito, em `design.md`, que `hasEntries` segue por conta (é regra de conta permanente, não se aplica a categoria) e que `closedLegBlockingChange` segue correta sem par (filtra por `type.isPermanent`, e categoria nunca foi permanente)
- [ ] 8.4 Adicionar a leitura do total sem classificação (entries sem dimensão na conta nominal), pelo mesmo mecanismo
- [ ] 8.5 Renomear `accountPeriodTotals` e `reportStats` para vocabulário de razão, mantendo-as no razão

## 9. Verificação final

- [ ] 9.1 Auditar as assinaturas públicas de `:core:ledger` e confirmar que nenhuma nomeia fatura, cartão, categoria, orçamento ou relatório
- [ ] 9.2 Auditar o SQL de `:core:ledger` e confirmar que todo JOIN é entre tabelas do razão
- [ ] 9.3 Confirmar que nenhuma feature depende de `feature:transactions:api` para ler ou escrever no razão
- [ ] 9.4 Rodar `./gradlew allTests` e confirmar verde
- [ ] 9.5 Executar o app em Android e Desktop com um banco migrado de v9 e conferir visualmente saldos, faturas, gastos por categoria e relatórios
- [ ] 9.6 Atualizar `CLAUDE.md` e `feature/README.md` com o novo módulo e a direção invertida da dependência
