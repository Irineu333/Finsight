# Tasks — ledger-single-source

> Ordem forçada pela colisão de nome (design D1): o rename (§7) é o **último** passo e só é
> possível depois que a perna legada morrer (§6). Cada grupo compila e é verificável sozinho.

## 1. Correções e fundações independentes

- [ ] 1.1 Corrigir `AdjustInvoiceUseCase:74`, que atualiza o valor legado sem rota de razão — como `invoiceOwed` já lê o razão, editar um ajuste de fatura **já exibe número divergente hoje**. Espelhar o tratamento de `AdjustBalanceUseCase:76-85`. Teste que reproduz a divergência antes do fix.
- [ ] 1.2 Adicionar `AccountType.isMonetary` (`ASSET`/`LIABILITY`) ao lado de `isDebitNatured`, com KDoc explicando a distinção monetária/contrapartida (design D2). Teste cobrindo os cinco tipos.
- [ ] 1.3 Fundir as duas derivações parciais (`deriveOperationLabel` + `deriveTransactionType`) numa função **total** sobre `{EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT}`, com `EQUITY` avaliado **antes** do `else` (design D3).
- [ ] 1.4 Teste que fixa o bug do `EQUITY`: um ajuste (`ASSET` + `EQUITY`) deriva `ADJUSTMENT` e **não** `TRANSFER`. Deve falhar contra o `deriveOperationLabel` atual antes do fix de 1.3.

## 2. Razão legível como objeto

- [ ] 2.1 Expor `invoiceId` no modelo de domínio `Entry` (hoje só em `EntryEntity`), tornando-a uma perna completa.
- [ ] 2.2 Adicionar ao `EntryDao` a leitura de entries por operação, hidratadas com sua `Account`.
- [ ] 2.3 Estender `IEntryRepository` com leitura/observação de `Entry` (hoje só expõe agregados `Double`), e implementar em `EntryRepository`.
- [ ] 2.4 Atualizar os fakes de `IEntryRepository` nos testes existentes para a nova superfície.
- [ ] 2.5 Fazer o agregado carregar suas entries hidratadas no `OperationMapper`, disponíveis a todo consumidor.
- [ ] 2.6 Teste de SQL real cobrindo a hidratação (entries + `Account` + `invoiceId`), no padrão de `EntryCategoryQueryTest`.

## 3. Rede de segurança antes de virar os leitores

> Design D9: a paridade dos leitores legados hoje é verificada **em device**, não por teste.
> Cada teste abaixo captura os números **atuais** (produzidos pelo caminho legado) e deve
> continuar passando, inalterado, depois da troca em §4.

- [ ] 3.1 Teste de caracterização de `AccountUi` (saldo, abertura, receita, despesa, ajuste, pagamento de fatura) com dataset representativo.
- [ ] 3.2 Teste de caracterização de `ViewCategoryViewModel` (hoje soma `amount` cru via `getAllTransactions`).
- [ ] 3.3 Teste de caracterização do progresso de orçamento (`BudgetsViewModel` / `ViewBudgetViewModel`).
- [ ] 3.4 Teste de caracterização da forma in-memory do `CalculateBalanceUseCase` (lista de transações).

## 4. Virar os leitores para o razão

- [ ] 4.1 Virar `ViewCategoryViewModel` para o razão (`Σ entries` da conta da categoria); 3.2 passa inalterado.
- [ ] 4.2 Virar o progresso de orçamento para o razão; 3.3 passa inalterado.
- [ ] 4.3 Remover a forma in-memory do `CalculateBalanceUseCase` (CAP-2), deixando só a do razão; 3.4 passa inalterado.
- [ ] 4.4 Virar `AccountUi` para o razão, eliminando as cinco somas do construtor secundário — a terceira cópia da matemática de saldo; 3.1 passa inalterado.
- [ ] 4.5 Renomear `initialBalance` → `openingBalance` e unificar as **três** implementações independentes (`AccountUi:25`, `TransactionsViewModel:73`, `CalculateReportStatsUseCase:41`) em `balanceUpTo` (design D8).
- [ ] 4.6 Fechar o CAP-4: teste que diverge de propósito a data da operação e a da perna e demonstra a divergência de saldo na fronteira do mês; então garantir a invariante **na escrita** em vez de convencioná-la.

## 5. Modelos de UI planos e mappers

- [ ] 5.1 Colapsar `OperationPerspective` (sealed `Account`/`Card`) em `TransactionPerspective(accountId, invoiceId? = null)`, com o cartão entrando via `CreditCard.accountId` (design D6).
- [ ] 5.2 Converter `OperationUi` em DTO plano (id + valores resolvidos), movendo resolução de perspectiva, derivação de rótulo e inversão de sinal para um mapper — dissolvendo o `requireNotNull` do `by lazy` (design D5).
- [ ] 5.3 Converter `AccountUi` em DTO plano, sem `account: Account` e sem cálculo em construtor.
- [ ] 5.4 Trocar `isEditable` para `entries.count { it.account.type.isMonetary } == 1`, com teste cobrindo as cinco formas da tabela de bijeção (design D2).
- [ ] 5.5 Mover `Transaction.Type` para o papel de vocabulário de entrada da UI, deixando de ser estado do domínio (design D4). **Resolver a questão em aberto do design**: `core/model` (classificação de fronteira) ou `core/ui` (vocabulário de form)?
- [ ] 5.6 Remover as dependências de domínio de `core/ui/model`, tornando a regra de camada "Domain ← UI" verificável por dependência em vez de convencional.

## 6. Fim do double-write e migração v9

- [ ] 6.1 Parar a escrita legada em `OperationRepository.createOperation`/`updateOperation`, mantendo a atomicidade do `useWriterConnection { immediateTransaction { … } }`.
- [ ] 6.2 Remover as rotas de escrita que hoje passam ao largo do razão: `EditTransactionViewModel:125` e os fallbacks de `operationId == null` em `AdjustBalanceUseCase:71,84`.
- [ ] 6.3 Escrever `MIGRATION_8_9` na ordem obrigatória (design D10): `DROP TABLE transactions` → `ALTER TABLE operations RENAME TO transactions` → deleção defensiva da conta fantasma `EQUITY:'Saldo Inicial'`.
- [ ] 6.4 `Migration8To9Test`: dados representativos v8 → v9 preservando saldos, fatura e patrimônio; incluir o caso da conta fantasma referenciada (a FK `NO_ACTION` deve abortar — queremos descobrir no teste, não no device).
- [ ] 6.5 Exportar o schema `9.json` e registrar a migração em `getRoomDatabase`.
- [ ] 6.6 Remover o modelo legado por completo: `Transaction` (perna), `TransactionEntity`, `TransactionDao`, `ITransactionRepository`, `TransactionRepository`, `TransactionMapper`, `signedCents()`, `Transaction.Target`, `Operation.Kind` e `SystemAccount.INITIAL_BALANCE`.

## 7. Rename: `Operation` → `Transaction`

> Mecânico e quase todo por refactor de IDE. Manter em commits **separados** de qualquer
> mudança de comportamento, para o review poder confiar no diff (design, risco de churn).

- [ ] 7.1 Renomear o agregado de domínio `Operation` → `Transaction` (dono de `List<Entry>`), com `OperationRecurring`/`OperationInstallment` acompanhando.
- [ ] 7.2 Renomear `OperationEntity` → `TransactionEntity` e `OperationDao` → `TransactionDao` (nomes agora livres), alinhando com a tabela renomeada em 6.3.
- [ ] 7.3 Renomear `IOperationRepository`/`OperationRepository`/`OperationMapper` e os bindings Koin correspondentes.
- [ ] 7.4 Renomear `OperationLabel` → `TransactionLabel` e `OperationUi` → `TransactionUi`.
- [ ] 7.5 Renomear os arquivos de UI da feature (`ViewOperationModal`, `ViewOperation*` MVI) e o pacote `viewTransaction`, hoje incoerentes entre si.

## 8. Verificação

- [ ] 8.1 `./gradlew allTests` e `./gradlew check` verdes (o gate que ficou aberto no `balanced-ledger`).
- [ ] 8.2 Varredura de resíduo: nenhuma ocorrência de `Operation`, `signedImpact`, `signedCents`, `INITIAL_BALANCE` ou `initialBalance` fora de histórico/arquivo.
- [ ] 8.3 Paridade em device: saldos, faturas, patrimônio, gasto por categoria e relatórios conferidos contra um backup pré-migração — o risco #1 desta change é número mudar em silêncio.
- [ ] 8.4 Registrar quais CAPS do `balanced-ledger` foram fechadas (CAP-1 resto, CAP-2, CAP-4, CAP-5, CAP-6, CAP-7) e quais permanecem (CAP-3), para o arquivamento das duas changes.
