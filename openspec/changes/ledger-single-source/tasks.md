# Tasks — ledger-single-source

> Ordem forçada pela colisão de nome (design D1): o rename (§7) é o **último** passo e só é
> possível depois que a perna legada morrer (§6). Mas a cadeia **não é uma linha reta**: §1 e §2
> são independentes entre si, e §5 só depende de §4 no ponto do `AccountUi`.
>
> **Exceção à regra "grupos separados":** os renames de entity/tabela/coluna vão **dentro** de §6,
> junto da migração — separá-los quebra o Room em runtime (design **D14**; a regra nasceu no D10, que o D14 revogou).

## 0. Encerrar em vez de apagar — decisões e trabalho sem schema

> Design D13/D14. **Correção de ordem:** a versão anterior dizia que §0 "precede tudo". Falso — a
> coluna de encerramento é mudança de schema (`AccountEntity` não a tem; o banco está em `version = 8`),
> e como o D14 elimina o caminho v8 **não existe uma `MIGRATION_8_9` onde ela caiba**. Ela nasce na v9.
> Logo §0 se parte: as decisões vêm antes; o schema e os use cases vêm **depois** de §6 (ver §6b).
> Era exatamente o acoplamento schema-runtime que o D10 existe para prevenir.

- [x] 0.1 ~~Verificar se apagar conta com lançamentos lança violação~~ **CONFIRMADO pelo usuário em runtime**: `SQLiteException 787 FOREIGN KEY constraint failed`. Causa: `entries.accountId` FK `NO_ACTION` (`EntryEntity:24-27`) + FK ligada em runtime (`AppDatabase_Impl.onOpen`, D19). Não há teste de `DeleteAccountUseCase`.
- [ ] 0.2 Decidir o destino de **categoria**: a spec `account-lifecycle` exige encerrar "conta, cartão **ou** categoria", mas **não existe `DeleteCategoryUseCase`** (`DeleteCategoryViewModel:19` chama o repo direto), e apagar categoria **cascateia orçamentos inteiros** (`budgets.categoryId` e `budget_categories.categoryId` são CASCADE) — enquanto a string do modal só promete que as transações sobrevivem. Os três caminhos de delete são assimétricos (conta crasha; cartão e categoria deixam `Account` órfã de fachada em silêncio). Decidir se a change cobre os três ou reduz a spec a conta+cartão.
- [ ] 0.3 Decidir o **flag de encerramento**: `AccountDao` lista de `accounts` (5 queries com `WHERE type='ASSET'`), mas `CategoryDao` e `CreditCardDao` listam das **fachadas** e nunca tocam `accounts` — um flag em `accounts` é **invisível** para as telas de categoria e cartão. São três flags, ou dois JOINs novos. Depende de 0.2.

## 1. Correções e fundações independentes

- [ ] 1.1 Corrigir **os dois** `Adjust*UseCase`, que estão quebrados de formas diferentes (design D17) — **não** espelhar um no outro, como a 1ª versão deste plano mandava: `AdjustInvoiceUseCase:74` atualiza o legado sem rota de razão; `AdjustBalanceUseCase:76-85` chama só `updateOperation`, que **nunca toca a tabela `transactions`** (`OperationRepository:292-307`), deixando o legado permanentemente defasado (o use case recalcula a diferença a partir do razão, então nunca converge). Teste que reproduz **as duas** divergências antes do fix.
- [ ] 1.2 Adicionar `AccountType.isMonetary` (`ASSET`/`LIABILITY`) ao lado de `isDebitNatured`, com KDoc explicando a distinção monetária/contrapartida (design D2). Teste cobrindo os cinco tipos.
- [ ] 1.3 Tornar `deriveOperationLabel` uma função **total** sobre `{EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT}`, com `EQUITY` avaliado **antes de qualquer outro caso** — ordem `EQUITY → EXPENSE → INCOME → LIABILITY → else` (design D3). ⚠️ **NÃO fundir com `deriveTransactionType`**: as duas **coexistem** com propósitos distintos (design D15, task 5.8, `balanced-ledger` spec) — `deriveOperationLabel` é o rótulo da **operação** (cor/título/ícone/gate); `deriveTransactionType` é a direção da **perna da perspectiva** (texto de tipo/filtro). A UI exibe as duas ao mesmo tempo (`ViewOperationModal:169-189`). *(As versões anteriores desta task e da proposta mandavam "fundir"/"unificar" — resíduo do D3 original, que o D15 revogou e a rodada 5 esqueceu de propagar. Quem executasse destruiria `deriveTransactionType`, que a spec obriga a manter.)*
- [ ] 1.4 Teste que fixa os **dois** buracos do `EQUITY`, um por forma de ajuste: `{ASSET, EQUITY}` deriva `ADJUSTMENT` e não `TRANSFER`; `{LIABILITY, EQUITY}` deriva `ADJUSTMENT` e não `PAYMENT`. Ambos devem falhar contra o `deriveOperationLabel` atual antes de 1.3. **Um teste que só cubra o primeiro caso passa verde com o bug de pé** — foi assim que a 1ª versão deste plano errou.

## 2. Razão legível como objeto, e com os agregados que as telas consomem

- [ ] 2.1 Expor `invoiceId` no modelo de domínio `Entry` (hoje só em `EntryEntity`), tornando-a uma perna completa.
- [ ] 2.2 Adicionar ao `EntryDao` a leitura de entries por operação, hidratadas com sua `Account`.
- [ ] 2.3 Estender `IEntryRepository` com leitura/observação de `Entry` (hoje só expõe agregados `Double`), e implementar em `EntryRepository`.
- [ ] 2.4 Adicionar agregados **por conta e período** ao `EntryDao`/`IEntryRepository`: receita, despesa, ajuste e pagamento de fatura (design D12). Sem eles, virar o `AccountUi` só teria duas saídas — somar em memória, violando o requisito "Sem cálculo de saldo em memória" desta própria change, ou manter o legado.
- [ ] 2.5 Adicionar agregado de **contagem de lançamentos por categoria e mês** (`ViewCategoryViewModel:59` expõe `transactionCount`, que agregado nenhum entrega hoje).
- [ ] 2.6 Atualizar os fakes de `IEntryRepository` nos testes existentes para a nova superfície.
- [ ] 2.7 Fazer o agregado carregar suas entries hidratadas no `OperationMapper`, disponíveis a todo consumidor.
- [ ] 2.8 Teste de SQL real cobrindo a hidratação e os agregados novos, no padrão de `EntryCategoryQueryTest`.

## 3. Rede de segurança antes de virar os leitores

> Design D9: a paridade dos leitores legados hoje é verificada **em device**, não por teste.
> Cada teste abaixo captura os números **atuais** (produzidos pelo caminho legado) e deve
> continuar passando, inalterado, depois da troca em §4. São **onze** leitores (D11) — as contagens
> "quatro" e "seis" contavam só `signedCents` e ignoravam `sumOf { it.amount }` sobre a perna legada.

- [ ] 3.1 Teste de caracterização de `AccountUi` (saldo, abertura, receita, despesa, ajuste, pagamento de fatura) com dataset representativo.
- [ ] 3.2 Teste de caracterização de `ViewCategoryViewModel`, incluindo `totalAmount` **e** `transactionCount`.
- [ ] 3.3 Teste de caracterização do progresso de orçamento (`BudgetsViewModel` / `ViewBudgetViewModel`).
- [ ] 3.4 Teste de caracterização da forma in-memory do `CalculateBalanceUseCase`.
- [ ] 3.5 Teste de caracterização do saldo por conta no dashboard (`DashboardComponentsBuilder:216`, implementação própria fora do use case).
- [ ] 3.7 Teste de caracterização de `CalculateTransactionStatsUseCase:21-23` (`transactions/api` — receita/despesa/ajuste do mês).
- [ ] 3.8 Teste de caracterização de `CalculateInvoiceOverviewsUseCase:22,25,28,39`.
- [ ] 3.9 Teste de caracterização de `InvoiceTransactionsViewModel:102,106,110`.
- [ ] 3.10 Teste de caracterização de `ReportViewerViewModel:84,87,90`.
- [ ] 3.11 Teste de caracterização de `TransactionsViewModel:72` e dos demais sites do `DashboardComponentsBuilder` (`:156,157,181,186`), além do `:216` já coberto por 3.5.
- [ ] 3.6 Teste de caracterização de `CalculateReportStatsUseCase` (`income`, `expense`, `balance`, `initialBalance`) nas perspectivas de conta **e** de fatura, incluindo a exclusão de transferência interna.

## 4. Virar os leitores para o razão

- [ ] 4.1 Virar `ViewCategoryViewModel` para o razão (`Σ entries` da conta da categoria + contagem de 2.5); 3.2 passa inalterado.
- [ ] 4.2 Virar o progresso de orçamento para o razão; 3.3 passa inalterado.
- [ ] 4.3 Remover a forma in-memory do `CalculateBalanceUseCase` (CAP-2), deixando só a do razão; 3.4 passa inalterado.
- [ ] 4.4 Virar `AccountUi` para o razão, usando os agregados de 2.4 e eliminando as **seis** somas do construtor secundário (`:27,30,35,42,47,54`); 3.1 passa inalterado.
- [ ] 4.5 Virar o saldo por conta do dashboard (`DashboardComponentsBuilder:216`) para o razão; 3.5 passa inalterado.
- [ ] 4.6 Reescrever `CalculateReportStatsUseCase` sobre o razão: `balance` (`:32`), `income`/`expense` (`:24-30`, hoje filtrados por `Transaction.Type`) e `openingBalance` (`:41`); 3.6 passa inalterado.
- [ ] 4.7 Reescrever `isInternalTransferFor` (`CalculateReportStatsUseCase:100`) sobre o razão — hoje depende de `Operation.Kind.TRANSFER` **e** `Transaction.Target.ACCOUNT`, ambos removidos em §6/§7. Sem esta task o relatório não compila depois de 6.6.
- [ ] 4.8 Renomear `initialBalance` → `openingBalance` e unificar as **três** implementações independentes — `AccountUi:25`, `CalculateBalanceUseCase:19-23` e `CalculateReportStatsUseCase:41` — em `balanceUpTo` (design D8).
- [ ] 4.12 **Reimplementar a idempotência data+conta do `AdjustBalanceUseCase:37-42` sobre o razão.** Sem dono até a 5ª rodada, e **bloqueia 6.9 por compilação**: o lookup usa `ITransactionRepository.getTransactionsBy` + `Transaction.Type` + `Transaction.Target`, os três removidos por 6.9/6.3. Mesma classe de omissão que a 4.7 existe para prevenir — a lição foi aplicada ao relatório e não a este arquivo. A consulta natural no razão ("operação com entry em `accountId` + contrapartida `EQUITY:Reconciliação` na data D") **casa também com uma baixa de encerramento**: depende de 4b.10.
- [ ] 4.11 Virar os cinco leitores que a contagem anterior omitia: `CalculateTransactionStatsUseCase`, `CalculateInvoiceOverviewsUseCase`, `InvoiceTransactionsViewModel`, `ReportViewerViewModel` e os sites restantes de `DashboardComponentsBuilder`/`TransactionsViewModel`; 3.7-3.11 passam inalterados.
- [ ] 4.9 ~~Fechar o CAP-4 (invariante de data)~~ **REESCRITA — o CAP-4 mirava o alvo errado** (design D17): os 9 sites de criação passam a mesma data aos dois modelos; não há divergência de data. A divergência real é de **valor**, coberta por 1.1. Manter apenas um teste que fixe a invariante "a data da operação governa o corte", para que um caller futuro não a quebre.
- [ ] 4.10 Remover os **dois** sites mortos de `advancePayment` — e **só** eles (design D18): `AccountUi:17,55` (`0.0` hardcoded) + a linha inalcançável `AccountCard:212-217`; e `TransactionsUiState:48,52` (nunca atribuído). ⚠️ **`advancePayment` permanece vivo e renderizado** em `InvoiceTransactionsViewModel:104,141` → `InvoiceTransactionsScreen:412`, `ReportViewerViewModel:85,97` → `ReportContextCard:235`/`ReportExportLayout:94`, e `CalculateInvoiceOverviewsUseCase:23,35,45` — **não tocar**. A versão anterior dizia "remover `advancePayment`", o que apagaria uma feature viva em três telas. **Não criar agregado** no D12 para ele: `AccountUi` estruturalmente não distingue antecipação de pagamento.

## 4b. Divergências de comportamento a decidir ANTES do apply

> Design D16: "manter comportamento" pressupõe um comportamento único. Não existe. Cada item abaixo
> é uma **decisão de produto**, não um refactor — ao derivar a regra do razão, uma única regra sai, e
> ela muda pelo menos uma das telas divergentes.
>
> **Gates locais, não globais** (correção da 5ª rodada — a versão anterior dizia "nenhuma task de §5 é
> executável antes destas", serializando artificialmente a metade mais barata da change atrás de nove
> decisões de produto, que é exatamente a crítica que o D1 faz à sua própria 1ª versão):
> **6.5 depende de 0.3** (a mais cara de errar: é schema — 6.5 já pré-decidiu "uma coluna em `accounts`", que é um dos ramos que 0.3 declara aberto; se 0.3 responder "três flags", pelo argumento do D14 não há outra migração onde caibam) · **4.12 depende de 4b.10** · 5.3 depende de 4b.5 (`OperationUi:30-34` implementa `PAYMENT → EXPENSE`) ·
> 5.5 depende de 4b.3/4b.9 · 5.6 depende de 4b.3/4b.9 · 5.7 depende de 4b.6 · 5.8 depende de 4b.5 ·
> **4.6/4.7/4.11 dependem de 4b.4** (gate que faltava onde importa) · **6b.2 depende de 4b.10**.
> 5.1-5.4 e 5.9 não dependem de §4b.

- [ ] 4b.1 **Fatura retroativa é pagável?** Decisão real. `isPayable` (`CLOSED|RETROACTIVE`) é usada em `PayInvoiceUseCase:42`, que tem **4 callers**: `PayInvoiceViewModel:70`, `CloseInvoiceUseCase:53`, `CloseInvoiceUseCase:72`, `PayInvoicePaymentUseCase:75`. O ramo `RETROACTIVE` é **vivo** via `CloseInvoiceUseCase:53` — é o que faz fechar fatura retroativa funcionar. **As duas versões anteriores desta task erraram**: a 1ª disse "nunca usada" (refutada pela linha seguinte da própria tabela); a 2ª disse "inalcançável, remover" — que teria **quebrado o fechamento de fatura retroativa**. O fato é: o domínio permite pagar retroativa, a UI nunca oferece.
- [ ] 4b.10 **A baixa de encerramento deve ser distinguível de um ajuste de saldo?** Com o reuso de `EQUITY:Reconciliação` (D13), uma baixa `{ASSET, EQUITY:Reconciliação}` é **idêntica em forma** a um ajuste, e o D3 rotula ambos `ADJUSTMENT`. Isso colide com `account-lifecycle:25`, que exige a baixa "auditável", e com a idempotência data+conta do `AdjustBalanceUseCase` (ver 4.12). Opções: (a) título fixo na baixa (barato, não toca `AccountType`); (b) 2ª conta `EQUITY:Encerramento` (contradiz `chart-of-accounts` e o D13); (c) aceitar a fusão e remover "auditável" da spec.
- [ ] 4b.2 **"Fechar fatura" exige que a data de fechamento tenha chegado?** `InvoiceUi:25` diz que não para `RETROACTIVE`; `InvoiceTransactionsViewModel:147` diz que sim. Mesmo botão, duas regras.
- [ ] 4b.3 **Ajuste em fatura fechada pode ser apagado?** `ViewOperationModal:353-370` bloqueia; `ViewAdjustmentModal:228-256` não bloqueia nada.
- [ ] 4b.4 **`INCOME` numa fatura é "pagamento" ou "receita"?** Divergem `InvoiceTransactionsScreen:790,802` (`BillPaymentColor` + "pagamento") e `InstallmentsScreen:713,725` (`Income` + "receita") — **ambas telas de cartão**, logo é divergência genuína. `AccountsScreen:625,637` não é comparável (domínio diferente: numa tela de conta, `INCOME` **é** receita), mas tem incoerência própria: renderiza o rótulo de `ADJUSTMENT` sem arm de cor. São `TypeFilterChip`s (5 cópias), não renderização de transação — a versão anterior citava linhas erradas e inflava 1 divergência em 3.
- [ ] 4b.5 **O texto de tipo deve respeitar `Operation.type`?** A regra `PAYMENT → EXPENSE` mora em `Operation.kt:38`, e `ViewOperationModal:170` **a contorna** lendo `transaction.type` cru (a versão anterior dizia que a regra estava no modal — não está). Efeito: a perna `INCOME` de um pagamento renderiza **"receita"**, em cor de pagamento, sob o título "Pagamento de cartão".
- [ ] 4b.7 **"Fechar fatura" pode pagar?** `CloseInvoiceUseCase:52-57` (retroativa) e `:71-76` (fatura zerada) chamam `payInvoiceUseCase` e retornam `PAID` — sob o botão rotulado "fechar". E o use case **nunca consulta `isClosable`**: gateia por `!= PAID`, `!= CLOSED` e `closedAt.yearMonth == closingMonth`, aceitando fechar `FUTURE`, que nenhuma UI oferece.
- [ ] 4b.8 **Reabrir: o domínio é mais largo que a UI.** `ReopenInvoiceUseCase:25,29` permite CLOSED/FUTURE/RETROACTIVE; as duas telas só oferecem `isClosed`.
- [ ] 4b.9 **Os gates de remoção devem existir no domínio?** Hoje são todos de UI: `DeleteTransactionViewModel:19-23` e `DeleteInstallmentViewModel:23-30` apagam sem gate algum; só `DeleteFutureInvoiceUseCase:24` tem gate de domínio. Sem decidir isto, a 5.6 reimplementa o mesmo erro na UI.
- [ ] 4b.6 Unificar as 3 cópias do bloco de ações de fatura e as 4+ do predicado de status, **depois** de 4b.1-4b.2 decidirem qual regra vence.

## 5. Modelos de UI planos e mappers

- [ ] 5.1 Colapsar `OperationPerspective` (sealed `Account`/`Card`) em `TransactionPerspective(accountId, invoiceId? = null)`, com o cartão entrando via `CreditCard.accountId` (design D6).
- [ ] 5.2 Tratar o fallback de `CreditCard.accountId == null` (a coluna é nullable, `CreditCard.kt:19`: conta criada sob demanda + FK `SET_NULL`): um cartão sem conta de razão resolve para **vazio**, não para NPE. Teste cobrindo cartão recém-criado sem operação.
- [ ] 5.3 Converter `OperationUi` em DTO plano (id + valores resolvidos), movendo resolução de perspectiva, derivação de rótulo e inversão de sinal para um mapper — dissolvendo o `requireNotNull` do `by lazy` (design D5).
- [ ] 5.4 Converter `AccountUi` em DTO plano, sem `account: Account` e sem cálculo em construtor.
- [ ] 5.5 Reimplementar a regra de editabilidade **gate a gate** (design D2), não só a contagem: status de fatura (`Invoice.Status.isEditable`, **usado** e não reescrito), rótulo `!= ADJUSTMENT`, exatamente 1 perna monetária, sem parcelamento. Teste com asserção por gate — um teste que só cheque o resultado final não distingue "certo" de "certo por acaso".
- [ ] 5.6 Implementar a regra de **remoção** (design D2 nível 1), que nenhuma rodada anterior tinha especificado: fatura `CLOSED`/`PAID` bloqueia editar **e** apagar.
- [ ] 5.7 Eliminar as reimplementações inline do predicado de status de fatura, **mantendo** a propriedade canônica (a versão anterior a listava como "cópia a eliminar", contradizendo 5.5): `Invoice.Status.isEditable` (`Invoice.kt:68-71`) **fica**; saem `InvoiceTransactionsUiState:39` e o `when` de `ViewOperationModal:355`, que reenumera à mão o complemento exato de `Invoice.Status.isBlocked` (`:65-66`). São **quatro** formas do mesmo predicado: `Invoice.Status.isEditable` (canônica, **fica**), `Invoice.Status.isBlocked` (complemento exato), `InvoiceTransactionsUiState:39`, e o `when` de `ViewOperationModal:354-367`. ⚠️ **`CreditCardCard:302` NÃO entra aqui** — é `canPayInvoice = invoiceUi?.status == Invoice.Status.CLOSED`, o gate de **pagabilidade**, não de editabilidade. A versão anterior desta task o listava como 5ª cópia: executá-la faria o botão Pagar aparecer em fatura OPEN/FUTURE/RETROACTIVE e sumir de CLOSED — **inversão exata**. A contagem foi de três → quatro → cinco inflando com um predicado alheio, que é a doença que esta task diz corrigir. Ele pertence ao eixo de 4b.1.
- [ ] 5.8 Derivar os **dois eixos de exibição** (design D15): `TransactionLabel` da operação (cor/título/ícone) e a direção da perna da perspectiva (texto de tipo/filtro). Não fundir num enum só.
- [ ] 5.9 Remover os campos de tipo de domínio dos **modelos de UI** (`AccountUi`, `OperationUi`), deixando no máximo o identificador. ⚠️ **NÃO** remover a dependência `core/ui → core/model`: ela é **por desenho** (`core/ui/build.gradle.kts:11`; 14 componentes existem para renderizar domínio, e o `CLAUDE.md` define `core/ui` assim), e `core/ui/model` é **pacote**, não módulo Gradle. A versão anterior mandava tornar a regra "verificável por dependência" — o que a spec `presentation-mapping` explicitamente **proíbe** e o repo não permite sem extrair um módulo novo, fora de escopo. A verificação é por **inspeção dos modelos**.

## 6. Fim do double-write e migração v9 atômica

- [ ] 6.1 Parar a escrita legada em `OperationRepository.createOperation`/`updateOperation`, mantendo a atomicidade do `useWriterConnection { immediateTransaction { … } }`.
- [ ] 6.2 Remover as escritas legadas remanescentes. **Correção:** a versão anterior dizia que `EditTransactionViewModel:125` "passa ao largo do razão" — é o inverso: ela chama `transactionRepository.update(it)` **e** `updateOperation`; o que sobra é a escrita legada **extra**. Os fallbacks `operationId == null` de `AdjustBalanceUseCase:71,84` são de fato legado-only. Incluir também `deleteOperationById` (`OperationRepository:309-336`), o único caminho de escrita **fora** de `useWriterConnection`, e `AddInstallmentUseCaseImpl:140`, que faz **N `createOperation` num loop sem transação** (12x pode gravar 7 e falhar).
- [ ] 6.3 Decidir e executar o destino de `Transaction.Type` e `Transaction.Target` como **vocabulário de entrada** (design D4): ambos são escolha do usuário e **ficam**; só a materialização como campo da perna morre. Inclui a decisão de contrato: `TransactionsRoute(filterTarget)` é `@Serializable` com `TransactionTargetNavType` — o enum precisa de um endereço que a `api` possa serializar. **Não é limpeza; é decisão de navegação.** (42 arquivos tocam `Target`.)
- [ ] 6.4 Remover `Operation.Kind` (7 arquivos), agora que 1.3 e 4.7 cobrem seus consumidores.
- [ ] 6.5 **Substituir** a `MIGRATION_7_8` por uma **`MIGRATION_7_9` única** — que inclui **a coluna de encerramento em `accounts`** (não há outra versão onde ela caiba: `AccountEntity` não a tem, o DB está em v8, e o D14 mata o caminho v8) e as **contas encerradas do v7** (tipo recuperável de `transactions.target`, `NOT NULL` no v7; baixa datada em `MAX(t.date)` do bucket; limite: nome e multiplicidade se perderam — N contas viram uma por tipo) (design D14 — a v8 não foi para produção, então nenhum dispositivo real precisa passar por ela): constrói plano de contas e razão; **não** semeia `'Saldo Inicial'` (fantasma, D8); **não** semeia `'Conta removida'` nem roteia pernas para `EQUITY` (D13); reconstrói contas apagadas do v7 como contas **encerradas** com tipo real + lançamento de baixa zerando o saldo; dropa `transactions` legada; renomeia `operations` → `transactions`; e cria `entries.transactionId` **já com o nome final** — sem `RENAME COLUMN`, sem FK pendurada, sem índice órfão.
- [ ] 6.6 Remover a `MIGRATION_7_8`, o `8.json` **e o `Migration7To8Test`** (referencia `MIGRATION_7_8` em 9 pontos — sem removê-lo/convertê-lo o módulo de teste não compila e a 8.1 é inalcançável por construção); convertê-lo num `Migration7To9Test`; exportar um único `9.json`; registrar a `MIGRATION_7_9` em `getRoomDatabase`. Dispositivos de desenvolvimento em v8 perdem o caminho e precisam de reinstalação — nenhum usuário é afetado.
- [ ] 6.7 Renomear `OperationEntity` → `TransactionEntity` (e `tableName`), `OperationDao` → `TransactionDao`, `Entry.operationId`/`EntryEntity.operationId` → `transactionId`, **e `RecurringOccurrenceEntity.operationId` → `transactionId`** (FK + índice `UNIQUE`, `RecurringOccurrenceEntity.kt:19-24,28`) — no mesmo commit de 6.5/6.6. Sem `recurring_occurrences`, a varredura 8.2 falha por construção.
- [ ] 6.8 `Migration7To9Test`: v7 → v9 preservando saldos, fatura, patrimônio e totais por categoria; caso de **conta apagada com movimento no v7** (vira conta encerrada + baixa, patrimônio idêntico ao v7); assert de `PRAGMA foreign_key_list(entries)` → `transactions` e de `PRAGMA index_list(entries)` com o nome final; assert de que nenhuma `Entry` referencia conta inexistente.
- [ ] 6.9 Remover o modelo legado de perna: `Transaction` (perna), `TransactionEntity` legada, `TransactionDao` legado, `ITransactionRepository`, `TransactionRepository`, `TransactionMapper` e `signedCents()`.
- [ ] 6.10 Remover `SystemAccount.INITIAL_BALANCE` e `SystemAccount.REMOVED_ACCOUNT` (não semeadas pela `MIGRATION_7_9`) **e adicionar** as constantes de `'Conta encerrada'`/`'Cartão encerrado'` que a 6.5 semeia — `Database.kt:236` documenta que os nomes de conta de sistema espelham `SystemAccount`; a versão anterior removia duas constantes e introduzia duas contas semeadas sem constante, quebrando a invariante em silêncio.

## 6b. Encerrar em vez de apagar — use cases de runtime (depende de §6)

> Design D14: a coluna nasce **dentro** da v9 (6.5), não aqui. **Correção da 5ª rodada:** a versão
> anterior duplicava 6.5 como "6b.6" e punha a coluna em "6b.1", criando ordem circular (§6 → §6b → §6)
> e um teste em §6 (6.8) que verificava uma task de §6b. Aqui ficam só os use cases de runtime, que
> dependem da coluna existir.

- [ ] 6b.2 Converter `DeleteAccountUseCase` em encerramento: conta sem lançamentos é removida; com lançamentos é encerrada; saldo ≠ 0 gera lançamento de baixa balanceado contra `EQUITY:Reconciliação` (design D13 — reuso deliberado, para não expandir contas de sistema nem tornar o CAP-3 load-bearing). Trocar o `either { }` por captura de exceção: hoje a `SQLiteException` atravessa o `either` e o `viewModelScope` sem handler, e o `onLeft` de crashlytics nunca roda. Teste dos três caminhos — não existe nenhum hoje.
- [ ] 6b.3 Aplicar a `DeleteCreditCardUseCase`, hoje assimétrico e **não atômico** (dois passos sem `@Transaction`, contradizendo o commit `44d3bdd4`): ele apaga as operações de compra e preserva as de pagamento, deixando a `Account` `LIABILITY` viva sem fachada.
- [ ] 6b.4 Aplicar a categoria conforme 0.2 (exige criar o `DeleteCategoryUseCase`, que não existe).
- [ ] 6b.5 Excluir contas encerradas dos seletores e listagens ativas conforme 0.3, preservando "apagar" como ação única do usuário.

## 7. Rename: `Operation` → `Transaction`

> Mecânico e quase todo por refactor de IDE. Manter em commits **separados** de qualquer
> mudança de comportamento. As entities/tabela/coluna **não** estão aqui: foram em 6.5-6.6,
> por obrigação do Room (design D10).

- [ ] 7.1 Renomear o agregado de domínio `Operation` → `Transaction` (dono de `List<Entry>`), com `OperationRecurring`/`OperationInstallment` acompanhando.
- [ ] 7.2 Renomear `IOperationRepository`/`OperationRepository`/`OperationMapper` e os bindings Koin correspondentes.
- [ ] 7.3 Renomear `OperationLabel` → `TransactionLabel` e `OperationUi` → `TransactionUi`.
- [ ] 7.4 Renomear os arquivos de UI da feature (`ViewOperationModal`, `ViewOperation*` MVI) e o pacote `viewTransaction`, hoje incoerentes entre si.

## 8. Verificação

- [ ] 8.1 `./gradlew allTests` e `./gradlew check` verdes (o gate que ficou aberto no `balanced-ledger`, arquivado sem cumprir).
- [ ] 8.2 Varredura de resíduo: nenhuma ocorrência de `Operation`, `signedImpact`, `signedCents`, `INITIAL_BALANCE` ou `initialBalance` fora de histórico/arquivo. Depende de **6.7** ter renomeado `operationId` (a versão anterior citava 6.6) — sem isso esta task falha por construção.
- [ ] 8.3 Paridade em device: saldos, faturas, patrimônio, gasto por categoria, dashboard e relatórios conferidos contra um backup pré-migração — o risco #1 desta change é número mudar em silêncio.
- [ ] 8.4 Registrar quais CAPS do `balanced-ledger` foram fechadas (CAP-1 resto, CAP-2, CAP-4, CAP-5, CAP-6, CAP-7) e quais permanecem (CAP-3).

## 9. Cobertura do raio legado — varredura mecânica

> **Origem:** cruzamento mecânico de `grep -rl "signedCents|Transaction.Type|Transaction.Target|Operation.Kind|ITransactionRepository|domain.model.Transaction"` (produção, sem `/build/`) contra os nomes citados neste arquivo. Resultado da 1ª execução: **87 arquivos tocam o legado; 44 não eram nomeados por task alguma (51%)**. Seis rodadas de auditoria não pegaram isto — todas revisaram o texto escrito, não o território omitido.
>
> **Limite do método:** a varredura casa por *basename*. Falsos positivos são possíveis na direção "coberto" (`Transactions`, `Recurring`, `Category` casam com prosa solta); a direção "não coberto" é confiável. Reexecutar a varredura ao fim de §9 e exigir zero linhas `NENHUMA` é a definição de pronto desta seção.

### 9a. Ponto de escrita e mapper de entrada — o coração do D4, sem task até a 6ª rodada

- [ ] 9a.1 Converter `LedgerEntryWriter` (`:16,41,58,64,75,95,98,103,106,110`) para receber a **intenção** em vez de `List<Transaction>`: ele **é** o tradutor intenção→entries (design **D20**), porque resolver a contrapartida tem efeito colateral — `ensureCategoryAccount:114-131` faz `accountDao.insert` + `categoryDao.update`, e `ensureCardAccount:133-148`/`ensureSystemAccount:150-155` idem. Depois de 6.9 o seu modelo de entrada deixa de existir.
- [ ] 9a.2 ~~Criar um mapper puro `intenção → entries`~~ **CANCELADA — o objeto não pode existir** (design D20): a resolução de conta insere linhas sob demanda, logo um mapper puro é inconstruível. A responsabilidade fica no writer (9a.1). A versão anterior abria três tasks disputando um dono e prescrevia um mapper com 4 DAOs injetados — que não é mapper.
- [ ] 9a.3 Converter `BuildTransactionUseCase` + `Impl` — **o caminho principal de criação**, sem task até a 6ª rodada. Ele já é hoje `(form: TransactionForm) → Either<Throwable, Transaction>`, isto é, já normaliza a intenção; o que muda é o **tipo de saída** (intenção, não perna). Sem DAO — a resolução de contas é do writer (D20).
- [ ] 9a.4 `Ledger.kt`: remover `signedCents()`; **manter `deriveTransactionType`** (coexiste — 1.3/D15); reavaliar `displaySign`/`displayBalance` (`:21,26`), hoje sem nenhum consumidor de produção — o D5 atribui a inversão de sinal aos mappers e ela ainda não existe em lugar nenhum.
- [ ] 9a.5 `TransactionsModule` (Koin): remover o binding de `ITransactionRepository`.

### 9b. Vocabulário de entrada — forms e selectors (decide 6.3)

- [ ] 9b.1 `TransactionForm` (`:22,27,45,47,55,60,67-71`) e `RecurringForm` (`:11,31,36,41`): são a materialização de `Type`/`Target` como entrada do usuário. Definem o endereço final dos enums (ver 9b.5).
- [ ] 9b.2 `TargetSelector` (`core/ui`, 7 refs): o picker conta-vs-cartão.
- [ ] 9b.3 `AddTransactionModal`/`AddTransactionUiState`, `EditTransactionModal`/`EditTransactionUiState` — incl. o `Category.Type.isAccept` reimplementado localmente em `EditTransactionModal:369-374`.
- [ ] 9b.4 `AddInstallmentModal`, `RecurringFormModal`/`RecurringFormUiState`.
- [ ] 9b.5 **Fechar a Open Question do endereço de `Type`/`Target`** — e registrar que ela tem **uma só resposta viável**: `core/model`. Consumidores em módulos **core** (`core/model` `Recurring.kt:5` persistido, `core/database` `RecurringMapper`, `core/analytics` `event/*`, `core/ui` `OperationCard`/`AccountUi`/`TargetSelector`) **não podem** depender de `feature/transactions/api` — regra de dependência do `CLAUDE.md`, topologia estrela. O design oferecia as duas como escolha livre; uma delas quebra 4 módulos core.

### 9c. Navegação e analytics — contratos externos

- [ ] 9c.1 `TransactionsGraph` (`:24-28`), `TransactionTypeNavType`, `TransactionTargetNavType`, `TransactionsRoute`: os `NavType` serializam por `value.name`/`valueOf` — renomear **constante** estoura no restore pós-process-death. Zero deep links no projeto (verificado), então o risco é só esse.
- [ ] 9c.2 `core/analytics/.../event/Transactions.kt` (`:8,18,28-33`) e `event/Recurring.kt` (`:9,19,29,39,49,59,69`): **6** eventos recebem modelo de domínio por construtor (`DeleteTransaction`, `DeleteRecurring`, `ConfirmRecurring`, `SkipRecurring`, `StopRecurring`, `ReactivateRecurring`) — **7** com `DeleteInstallments` (9i.7), incl. `DeleteTransaction(transaction: Transaction)`, que lê `.type`/`.target`/`.category` da perna que 6.9 remove. **`proposal.md` declarava o módulo impactado e `tasks.md` tinha zero menções a ele.** ⚠️ Os nomes das constantes são **formato de fio publicado** (`.name.lowercase()`): não renomear.

### 9d. Renderização — `Operation.Kind` tem 7 consumidores, não 1

> A task 6.4 afirmava "*remover `Operation.Kind`, agora que 1.3 e 4.7 cobrem seus consumidores*". Cobrem **1 de 7**, e a afirmação não listava callers — violação da regra de método do design.

- [ ] 9d.1 `OperationCard` (`:88,89,104,151,171-192,194-200`): ícone, título, cor e sinal — cópia literal das regras do `ViewOperationModal`. É o item de lista do app.
- [ ] 9d.2 `ReportExportLayout` (`:173,174,185,197`).
- [ ] 9d.3 `DashboardComponentsBuilder:133` (`filterNot { Kind.TRANSFER/PAYMENT }`) — 4.5/4.11 cobrem `:216` e `:156,157,181,186`; `:133` não estava em lista nenhuma.
- [ ] 9d.4 `TransactionsViewModel:55,56,70` — 4.11 cobre só `:72`.
- [ ] 9d.5 `DashboardComponentContent` (`:91-92`, `:217-232`, `:535`), `DashboardPreviewFactory`.

### 9e. Use cases que criam operação — 3 dos 8 sem task

- [ ] 9e.1 `TransferBetweenAccountsUseCase` (`:58,64,71`).
- [ ] 9e.2 `AdvanceInvoicePaymentUseCase` (`:66,74,84`) — o 7º use case, esquecido nas rodadas 1-3.
- [ ] 9e.3 `ConfirmRecurringUseCase` (`:64,84`) e `SaveRecurringUseCase`.

### 9f. Filtros, UiStates e o fluxo de recorrência

- [ ] 9f.1 `TransactionsAction`, `TransactionsFilters`.
- [ ] 9f.2 `AccountsAction`/`AccountsUiState`/`AccountsViewModel`.
- [ ] 9f.3 `CreditCardsAction`/`CreditCardsScreen`/`CreditCardsUiState`/`CreditCardsViewModel`.
- [ ] 9f.4 `InstallmentsAction`/`InstallmentsUiState`/`InstallmentsViewModel`, `InvoiceTransactionsAction`.
- [ ] 9f.5 `ConfirmRecurringAction`/`ConfirmRecurringUiState`/`ConfirmRecurringViewModel`, `RecurringUiState`, `RecurringViewModel`.
- [ ] 9f.6 `RecurringMapper` — `Recurring.kt:5` **persiste** `type: Transaction.Type` na tabela `recurring`, que esta change não remove.

### 9g. Restantes

- [ ] 9g.1 `CalculateBudgetProgressUseCase` (`:39` filtra por `today.yearMonth`, não pelo mês selecionado).
- [ ] 9g.2 `BudgetFormViewModel`, `CalculateReportCategorySpendingUseCase`, `DeleteTransactionModal` (recebe `Transaction` por construtor).

### 9i. Descobertos pelo portão vácuo (falsos positivos do matcher por basename)

- [ ] 9i.1 As **4 cópias restantes** de `TypeFilterChip`, que 4b.4 decide e nenhuma task implementava: `feature/transactions/impl/.../TransactionsScreen.kt:293-295`, `feature/accounts/impl/.../AccountsScreen.kt:622-627,637`, `feature/creditcards/impl/.../InstallmentsScreen.kt:711,725`, `feature/creditcards/impl/.../InvoiceTransactionsScreen.kt:788,802`. (A 5ª, `CreditCardsScreen:662,674`, está em 9f.3.) **A decisão 4b.4 sairia sem executor.**
- [ ] 9i.2 `feature/creditcards/impl/.../PayInvoicePaymentUseCase.kt` — cria operação de 2 pernas; a §9e dizia "3 dos 8 sem task" e não o incluía. Só aparecia na lista de callers de 4b.1.
- [ ] 9i.3 `feature/transactions/impl/.../ViewAdjustmentModal.kt` — só aparecia em 4b.3 como divergência; precisa de task de execução (é o modal que apaga sem gate de fatura).
- [ ] 9i.4 `feature/categories/impl/.../CalculateCategorySpendingUseCaseImpl.kt:20,24,48,61` e `feature/report/impl/.../CalculateReportCategorySpendingUseCase.kt:64` — **duas inversões de sinal à mão** (`displaySign: Double`, `1.0`/`-1.0` hardcoded). O D18 afirma que "a inversão por `AccountType` não existe em lugar nenhum"; existem duas, por `Transaction.Type`/`Category.Type`. Nenhum dos dois casa os padrões da varredura — **buraco do próprio método**, não do plano.
- [ ] 9i.6 `feature/transactions/impl/.../deleteTransaction/DeleteTransactionViewModel.kt` — recebe `transaction: Transaction` por construtor (`:7,13`) e lê `transaction.operationId ?: transaction.id` (`:20`); **6.9 o quebra por compilação**. Só era mencionado em 4b.9, que é decisão — exatamente o padrão que a 9i.3 criou para o `ViewAdjustmentModal`, e o gêmeo passou pelo portão.
- [ ] 9i.7 `core/analytics/.../event/Installments.kt:5,18,20` — importa `domain.model.Operation` e `DeleteInstallments(installment, operations: List<Operation>)`. **Zero menções nos 8 artefatos**; §7.1 renomeia `Operation` e a 9c.2 cobre só `event/Transactions.kt` e `event/Recurring.kt`. **Buraco do método**: a varredura casa `Operation.Kind`, não `Operation` nu.
- [ ] 9i.5 `core/model/.../extension/Category.kt` (`isAccept`, reimplementado localmente em `EditTransactionModal:369-374`) e `feature/creditcards/api/.../AddInstallmentUseCase.kt`.

### 9h. Portão da varredura

- [ ] 9h.1 ⚠️ **O portão anterior era vácuo** — dava zero **antes de qualquer trabalho**. O matcher casava por *basename* em prosa solta, e produzia 8 falsos positivos, dois deles por **substring** (`TransactionsScreen` ⊂ `InvoiceTransactionsScreen`; `AddInstallmentUseCase` ⊂ `AddInstallmentUseCaseImpl`) e três em que a única menção era uma decisão de produto ou uma instrução de "**não tocar**". Eu declarei o matcher falível e o promovi a definição de pronto assim mesmo. **Portão novo:** a varredura casa pelo **path completo**, e só conta como coberto se a task o citar num **item de trabalho** — não em prosa, não numa decisão, não numa ressalva de "não tocar". O conjunto de padrões ganha **`domain.model.Operation` nu** (não só `Operation.Kind`), sem o qual `event/Installments.kt` fica invisível (9i.7). ⚠️ **Executado na 8ª rodada, o portão novo dava 1, não 0** — pegou `DeleteTransactionViewModel` (9i.6). É a primeira ferramenta destes artefatos que acha o próprio autor errado.
- [ ] 9h.2 Antes de qualquer afirmação de "morto"/"inalcançável"/"coberto"/"única cópia" em artefato, grepar os callers e **listá-los ali** (regra de método, `design.md`). As afirmações que sobreviveram erradas por seis rodadas são exatamente as que não listam callers.
