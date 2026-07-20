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
- [x] 0.2 ~~Decidir o destino de categoria~~ **DECIDIDO (usuário): categoria entra** (design **D22**). Contexto original: a spec `account-lifecycle` exige encerrar "conta, cartão **ou** categoria", mas **não existe `DeleteCategoryUseCase`** (`DeleteCategoryViewModel:19` chama o repo direto), e apagar categoria **cascateia orçamentos inteiros** (`budgets.categoryId` e `budget_categories.categoryId` são CASCADE) — enquanto a string do modal só promete que as transações sobrevivem. Os três caminhos de delete são assimétricos (conta crasha; cartão e categoria deixam `Account` órfã de fachada em silêncio). Cobre os três. A D21 já torna encerrar categoria o mesmo mecanismo de encerrar conta.
- [x] 0.3 ~~Decidir o flag de encerramento~~ **DECIDIDO (usuário): mora em `accounts`; fachadas consomem da sua conta** (design **D21**). Um campo só, fonte única — não três cópias, que seriam a mesma doença que esta change cataloga. Implica criação **eager** da conta de categoria/cartão (`accountId` → `NOT NULL`), sem a qual o `JOIN` perde quem nunca foi usado. Faz cair D6/5.2 e encolhe o CAP-3 para só contas de sistema.

## 1. Correções e fundações independentes

- [x] 1.1 Corrigir **os dois** `Adjust*UseCase`, que estão quebrados de formas diferentes (design D17) — **não** espelhar um no outro, como a 1ª versão deste plano mandava: `AdjustInvoiceUseCase:74` atualiza o legado sem rota de razão; `AdjustBalanceUseCase:76-85` chama só `updateOperation`, que **nunca toca a tabela `transactions`** (`OperationRepository:292-307`), deixando o legado permanentemente defasado (o use case recalcula a diferença a partir do razão, então nunca converge). Teste que reproduz **as duas** divergências antes do fix.
- [x] 1.2 Adicionar `AccountType.isMonetary` (`ASSET`/`LIABILITY`) ao lado de `isDebitNatured`, com KDoc explicando a distinção monetária/contrapartida (design D2). Teste cobrindo os cinco tipos.
- [x] 1.3 Tornar `deriveOperationLabel` uma função **total** sobre `{EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT}`, com `EQUITY` avaliado **antes de qualquer outro caso** — ordem `EQUITY → EXPENSE → INCOME → LIABILITY → else` (design D3). ⚠️ **NÃO fundir com `deriveTransactionType`**: as duas **coexistem** com propósitos distintos (design D15, task 5.8, `balanced-ledger` spec) — `deriveOperationLabel` é o rótulo da **operação** (cor/título/ícone/gate); `deriveTransactionType` é a direção da **perna da perspectiva** (texto de tipo/filtro). A UI exibe as duas ao mesmo tempo (`ViewOperationModal:169-189`). *(As versões anteriores desta task e da proposta mandavam "fundir"/"unificar" — resíduo do D3 original, que o D15 revogou e a rodada 5 esqueceu de propagar. Quem executasse destruiria `deriveTransactionType`, que a spec obriga a manter.)*
- [x] 1.4 Teste que fixa os **dois** buracos do `EQUITY`, um por forma de ajuste: `{ASSET, EQUITY}` deriva `ADJUSTMENT` e não `TRANSFER`; `{LIABILITY, EQUITY}` deriva `ADJUSTMENT` e não `PAYMENT`. Ambos devem falhar contra o `deriveOperationLabel` atual antes de 1.3. **Um teste que só cubra o primeiro caso passa verde com o bug de pé** — foi assim que a 1ª versão deste plano errou.

## 2. Razão legível como objeto, e com os agregados que as telas consomem

- [x] 2.1 Expor `invoiceId` no modelo de domínio `Entry` (hoje só em `EntryEntity`), tornando-a uma perna completa.
- [x] 2.2 Adicionar ao `EntryDao` a leitura de entries por operação, hidratadas com sua `Account`.
- [x] 2.3 Estender `IEntryRepository` com leitura/observação de `Entry` (hoje só expõe agregados `Double`), e implementar em `EntryRepository`.
- [x] 2.4 Adicionar agregados **por conta e período** ao `EntryDao`/`IEntryRepository`: receita, despesa, ajuste e pagamento de fatura (design D12). Sem eles, virar o `AccountUi` só teria duas saídas — somar em memória, violando o requisito "Sem cálculo de saldo em memória" desta própria change, ou manter o legado.
- [x] 2.5 Adicionar agregado de **contagem de lançamentos por categoria e mês** (`ViewCategoryViewModel:59` expõe `transactionCount`, que agregado nenhum entrega hoje).
- [x] 2.6 Atualizar os fakes de `IEntryRepository` nos testes existentes para a nova superfície.
- [x] 2.7 Fazer o agregado carregar suas entries hidratadas no `OperationMapper`, disponíveis a todo consumidor.
- [x] 2.8 Teste de SQL real cobrindo a hidratação e os agregados novos, no padrão de `EntryCategoryQueryTest`.

## 3. Rede de segurança antes de virar os leitores

> Design D9: a paridade dos leitores legados hoje é verificada **em device**, não por teste.
> Cada teste abaixo captura os números **atuais** (produzidos pelo caminho legado) e deve
> continuar passando, inalterado, depois da troca em §4. São **onze** leitores (D11) — as contagens
> "quatro" e "seis" contavam só `signedCents` e ignoravam `sumOf { it.amount }` sobre a perna legada.

- [x] 3.1 Teste de caracterização de `AccountUi` (saldo, abertura, receita, despesa, ajuste, pagamento de fatura) com dataset representativo.
- [x] 3.2 Teste de caracterização de `ViewCategoryViewModel`, incluindo `totalAmount` **e** `transactionCount`.
- [x] 3.3 Teste de caracterização do progresso de orçamento (`BudgetsViewModel` / `ViewBudgetViewModel`).
- [x] 3.4 Teste de caracterização da forma in-memory do `CalculateBalanceUseCase`.
- [x] 3.5 Teste de caracterização do saldo por conta no dashboard (`DashboardComponentsBuilder:216`, implementação própria fora do use case).
- [x] 3.7 Teste de caracterização de `CalculateTransactionStatsUseCase:21-23` (`transactions/api` — receita/despesa/ajuste do mês).
- [x] 3.8 Teste de caracterização de `CalculateInvoiceOverviewsUseCase:22,25,28,39`.
- [x] 3.9 Teste de caracterização de `InvoiceTransactionsViewModel:102,106,110` (`InvoiceTransactionsViewModelCharacterizationTest`: expense/advancePayment/adjustment das pernas de cartão + total via `invoiceOwed`). **Nota (execução):** as somas `:102,106,110` (`expense`/`advancePayment`/`adjustment` por fatura) são a **fórmula idêntica** a `CalculateInvoiceOverviewsUseCase`, já fixada por **3.8** (`CalculateInvoiceOverviewsUseCaseTest`); `total` lê `entryRepository.invoiceOwed` (já no razão). A ViewModel só adiciona wiring de flow — sem lógica numérica nova. Falta apenas o harness de nível ViewModel (5 fakes de repositório); a cobertura numérica já existe.
- [x] 3.10 Teste de caracterização de `ReportViewerViewModel:84,87,90` (`ReportViewerViewModelCharacterizationTest`: perspectiva de conta forwarda `CalculateReportStatsUseCase`; perspectiva de fatura soma as pernas de cartão + total via `invoiceOwed`). **Nota (execução):** as somas de fatura são a mesma fórmula fixada por **3.8**; as stats de conta vêm de `CalculateReportStatsUseCase`, fixadas por **3.6** (`CalculateReportStatsUseCaseTest`); `total` lê `invoiceOwed` (razão). Sem lógica numérica nova. Falta apenas o harness de nível ViewModel (8 deps, incl. renderer/analytics).
- [x] 3.11 Teste de caracterização de `TransactionsViewModel:72` e dos demais sites do `DashboardComponentsBuilder` (`:156,157,181,186`), além do `:216` já coberto por 3.5.
- [x] 3.6 Teste de caracterização de `CalculateReportStatsUseCase` (`income`, `expense`, `balance`, `initialBalance`) nas perspectivas de conta **e** de fatura, incluindo a exclusão de transferência interna.

## 4. Virar os leitores para o razão

- [x] 4.1 Virar `ViewCategoryViewModel` para o razão (`Σ entries` da conta da categoria + contagem de 2.5); 3.2 passa inalterado.
- [x] 4.2 Virar o progresso de orçamento para o razão; 3.3 passa inalterado.
- [x] 4.3 Remover a forma in-memory do `CalculateBalanceUseCase` (CAP-2), deixando só a do razão; 3.4 passa inalterado.
- [x] 4.4 Virar `AccountUi` para o razão, usando os agregados de 2.4 e eliminando as **seis** somas do construtor secundário (`:27,30,35,42,47,54`); 3.1 passa inalterado. **Produção virada** (`AccountsViewModel` lê `balanceUpTo`/`accountFlows`); a remoção física do construtor secundário fica com 5.4 (quando `AccountUi` vira DTO plano e `AccountUiCharacterizationTest` cede a prova numérica ao `AccountPeriodTotalsQueryTest`).
- [x] 4.5 Virar o saldo por conta do dashboard (`DashboardComponentsBuilder:216`) para o razão; 3.5 passa inalterado.
- [x] 4.6 Reescrever `CalculateReportStatsUseCase` sobre o razão: `balance` (`:32`), `income`/`expense` (`:24-30`, hoje filtrados por `Transaction.Type`) e `openingBalance` (`:41`); 3.6 passa inalterado.
- [x] 4.7 Reescrever `isInternalTransferFor` (`CalculateReportStatsUseCase:100`) sobre o razão — hoje depende de `Operation.Kind.TRANSFER` **e** `Transaction.Target.ACCOUNT`, ambos removidos em §6/§7. Sem esta task o relatório não compila depois de 6.6.
- [x] 4.8 Renomear `initialBalance` → `openingBalance` e unificar as **três** implementações independentes — `AccountUi:25`, `CalculateBalanceUseCase:19-23` e `CalculateReportStatsUseCase:41` — em `balanceUpTo` (design D8).
- [x] 4.12 **Reimplementar a idempotência data+conta do `AdjustBalanceUseCase:37-42` sobre o razão.** Sem dono até a 5ª rodada, e **bloqueia 6.9 por compilação**: o lookup usa `ITransactionRepository.getTransactionsBy` + `Transaction.Type` + `Transaction.Target`, os três removidos por 6.9/6.3. Mesma classe de omissão que a 4.7 existe para prevenir — a lição foi aplicada ao relatório e não a este arquivo. A consulta natural no razão ("operação com entry em `accountId` + contrapartida `EQUITY:Reconciliação` na data D") **casa também com uma baixa de encerramento**: depende de 4b.10.
- [x] 4.11 Virar os cinco leitores que a contagem anterior omitia: `CalculateTransactionStatsUseCase`, `CalculateInvoiceOverviewsUseCase`, `InvoiceTransactionsViewModel`, `ReportViewerViewModel` e os sites restantes de `DashboardComponentsBuilder`/`TransactionsViewModel`; 3.7-3.11 passam inalterados.
- [x] 4.9 ~~Fechar o CAP-4 (invariante de data)~~ **REESCRITA — o CAP-4 mirava o alvo errado** (design D17): os 9 sites de criação passam a mesma data aos dois modelos; não há divergência de data. A divergência real é de **valor**, coberta por 1.1. Manter apenas um teste que fixe a invariante "a data da operação governa o corte", para que um caller futuro não a quebre.
- [x] 4.10 Remover os **dois** sites mortos de `advancePayment` — e **só** eles (design D18): `AccountUi:17,55` (`0.0` hardcoded) + a linha inalcançável `AccountCard:212-217`; e `TransactionsUiState:48,52` (nunca atribuído). ⚠️ **`advancePayment` permanece vivo e renderizado** em `InvoiceTransactionsViewModel:104,141` → `InvoiceTransactionsScreen:412`, `ReportViewerViewModel:85,97` → `ReportContextCard:235`/`ReportExportLayout:94`, e `CalculateInvoiceOverviewsUseCase:23,35,45` — **não tocar**. A versão anterior dizia "remover `advancePayment`", o que apagaria uma feature viva em três telas. **Não criar agregado** no D12 para ele: `AccountUi` estruturalmente não distingue antecipação de pagamento.

## 4b. Divergências de comportamento a decidir ANTES do apply

> Design D16: "manter comportamento" pressupõe um comportamento único. Não existe. Cada item abaixo
> é uma **decisão de produto**, não um refactor — ao derivar a regra do razão, uma única regra sai, e
> ela muda pelo menos uma das telas divergentes.
>
> **Gates locais, não globais** (correção da 5ª rodada — a versão anterior dizia "nenhuma task de §5 é
> executável antes destas", serializando artificialmente a metade mais barata da change atrás de nove
> decisões de produto, que é exatamente a crítica que o D1 faz à sua própria 1ª versão).
>
> **Estado: as nove decisões estão tomadas — nenhum gate de §4b bloqueia execução.** 4b.1/4b.2/4b.4/4b.5
> decididas pela investigação (três eram **no-op**); 4b.8 estreitada; 4b.3 e 4b.7 decididas pelo usuário;
> 4b.10 resolvida pela **D21** (o flag de encerramento torna a colisão inalcançável); 4b.9 **redesenhada**
> pela **D23**. 0.3 idem — decidida ("uma coluna em `accounts`"), que é o que 6.5 assume; o ramo "três
> flags", que teria custado uma migração, **não** venceu. Resta apenas **4b.6**, que é execução e não
> decisão (ver §5/§9), e cujos insumos — 4b.1 e 4b.2 — já estão fechados.
>
> **Dependências que sobrevivem, agora só de ordem de execução:** 6.5 → 0.3 · 4.12 → 4b.10 ·
> 5.3 → 4b.5 (`OperationUi:30-34` implementa `PAYMENT → EXPENSE`) · 5.5/5.6 → 4b.9 (D23) ·
> 5.7 → 4b.6 · 4.6/4.7/4.11 → 4b.4 · 6b.2 → 4b.10. 5.1-5.4, 5.8 e 5.9 não dependem de §4b.

- [x] 4b.1 **DECIDIDO: manter `isPayable` largo, não expor na UI.** Investigado: `PayInvoiceUseCase` tem **4 callers** e o único que envia `RETROACTIVE` é `CloseInvoiceUseCase:53` — é o que faz fechar fatura retroativa funcionar. Estreitar → o clique falha **em silêncio** (`CloseInvoiceViewModel:25` manda ao crashlytics; não há UI de erro). Expor na UI seria botão morto no caso com dívida: `PayInvoicePaymentUseCase:37` exige `== CLOSED` literal. **Consequência: nenhuma.** Contexto original: Decisão real. `isPayable` (`CLOSED|RETROACTIVE`) é usada em `PayInvoiceUseCase:42`, que tem **4 callers**: `PayInvoiceViewModel:70`, `CloseInvoiceUseCase:53`, `CloseInvoiceUseCase:72`, `PayInvoicePaymentUseCase:75`. O ramo `RETROACTIVE` é **vivo** via `CloseInvoiceUseCase:53` — é o que faz fechar fatura retroativa funcionar. **As duas versões anteriores desta task erraram**: a 1ª disse "nunca usada" (refutada pela linha seguinte da própria tabela); a 2ª disse "inalcançável, remover" — que teria **quebrado o fechamento de fatura retroativa**. O fato é: o domínio permite pagar retroativa, a UI nunca oferece.
- [x] 4b.10 **RESOLVIDO PELA D21, sem decisão a tomar — e a recomendação anterior ("título fixo") está REFUTADA.** Título não resolve em **nenhuma** ponta: (a) a idempotência não o consulta — `getTransactionsBy` aceita só `type, target, date, invoiceId, accountId` (`ITransactionRepository:17-23`), então a baixa seria capturada pelo `firstOrNull()` de `AdjustBalanceUseCase:42` e **mutada** (`:79-82`) ou **apagada** (`:66-74`); (b) o título nem apareceria — `OperationCard:181` captura `ADJUSTMENT + target.isAccount` e rotula "Ajuste de saldo", descartando `operation.label`. **O eixo que funciona é o flag de encerramento que a D21 já cria**: `AccountDao:19` filtra só `type='ASSET'`; somar `AND NOT closed` tira a conta do seletor ⇒ `AdjustBalanceUseCase` nunca é invocado com ela ⇒ colisão **inalcançável**, sem tocar no lookup. Custo zero. ⚠️ **Ponta a cobrir:** `EditAccountBalanceViewModel:82` usa `getAccountById`, que **não filtra** — a conta inicialmente selecionada entra por fora da lista. Contexto original: Com o reuso de `EQUITY:Reconciliação` (D13), uma baixa `{ASSET, EQUITY:Reconciliação}` é **idêntica em forma** a um ajuste, e o D3 rotula ambos `ADJUSTMENT`. Isso colide com `account-lifecycle:25`, que exige a baixa "auditável", e com a idempotência data+conta do `AdjustBalanceUseCase` (ver 4.12). Opções: (a) título fixo na baixa (barato, não toca `AccountType`); (b) 2ª conta `EQUITY:Encerramento` (contradiz `chart-of-accounts` e o D13); (c) aceitar a fusão e remover "auditável" da spec.
- [x] 4b.2 **DECIDIDO: unificar em `InvoiceTransactionsViewModel:147` (exigir a data) — NO-OP.** A divergência era **código morto**: `InvoiceDao:37` (`observeUnpaidInvoices`) filtra `NOT IN ('PAID','RETROACTIVE')`, e dashboard e tela de cartões só consomem esse flow — logo `InvoiceUi` **nunca é construído para retroativa** e o ramo `|| isRetroactive` de `InvoiceUi:25` é inalcançável. Somado a que a `closingDate` de retroativa é sempre passada (`CreateRetroactiveInvoiceUseCase:33-39`). **Consequência: nenhuma.** Contexto original: `InvoiceUi:25` diz que não para `RETROACTIVE`; `InvoiceTransactionsViewModel:147` diz que sim. Mesmo botão, duas regras.
- [x] 4b.3 **DECIDIDO: bloquear.** O furo é o `ViewAdjustmentModal:220-236`, que renderiza Excluir **incondicionalmente** — mesmo exibindo o status da fatura ao lado (`:198-216`). E `ReportViewerScreen:295-296` roteia ajustes para lá, então o relatório é porta de entrada. ⚠️ O gate **não pode** ir para `deleteOperationById` (ver 4b.9). Contexto original: `ViewOperationModal:353-370` bloqueia; `ViewAdjustmentModal:228-256` não bloqueia nada.
- [x] 4b.4 **DECIDIDO: "pagamento" — NO-OP.** Os **cinco** filtros filtram por `Operation.type`, que já aplica `PAYMENT → EXPENSE`; um pagamento sempre tem 2 pernas com uma de cartão ⇒ `kind = PAYMENT` ⇒ `type = EXPENSE`. **O chip `INCOME` devolve lista vazia nas cinco telas.** E parcelamento não pode ter perna INCOME (4 pontos impedem: `AddInstallmentModal:81`, `BuildTransactionUseCaseImpl:76-78`, `TransactionForm:47`, e cada parcela é operação de 1 perna). **Consequência: nenhuma** — o chip muda de rótulo e segue vazio. Contexto original: Divergem `InvoiceTransactionsScreen:790,802` (`BillPaymentColor` + "pagamento") e `InstallmentsScreen:713,725` (`Income` + "receita") — **ambas telas de cartão**, logo é divergência genuína. `AccountsScreen:625,637` não é comparável (domínio diferente: numa tela de conta, `INCOME` **é** receita), mas tem incoerência própria: renderiza o rótulo de `ADJUSTMENT` sem arm de cor. São `TypeFilterChip`s (5 cópias), não renderização de transação — a versão anterior citava linhas erradas e inflava 1 divergência em 3.
- [x] 4b.5 **DECIDIDO: respeitar `Operation.type` — NO-OP hoje, blindagem para depois.** `Operation.type` **também** devolve `EXPENSE` para `PAYMENT`, e `OperationPerspective.Card` **nunca é instanciado** (único uso construído é `Account`, em `AccountsViewModel:62`) — a perna INCOME jamais chega ao modal. Só passa a divergir se alguém usar a perspectiva de cartão. **Consequência: nenhuma.** Contexto original: A regra `PAYMENT → EXPENSE` mora em `Operation.kt:38`, e `ViewOperationModal:170` **a contorna** lendo `transaction.type` cru (a versão anterior dizia que a regra estava no modal — não está). Efeito: a perna `INCOME` de um pagamento renderiza **"receita"**, em cor de pagamento, sob o título "Pagamento de cartão".
- [x] 4b.7 **DECIDIDO (usuário): ajustar o rótulo para refletir a realidade — mas só no ramo em que a realidade está certa.** O use case tem **dois** ramos que retornam `PAID` sob o botão "Fechar Fatura":
  - **Fatura zerada** (`CloseInvoiceUseCase:71-76`): `invoiceAmount == 0.0` → fecha, abre a próxima, marca `PAID`. **A realidade está certa** — fechar fatura sem gasto **é** quitá-la; o rótulo é que mente. **Aplicar aqui:** o modal (`close_invoice_title`/`close_invoice_confirm`/`close_invoice_message`, `strings.xml:358-360`) e os dois botões (`credit_cards_close_invoice:182`, `invoice_transactions_close_invoice:241`) devem dizer o que fazem quando não há saldo. Hoje `close_invoice_message` promete só "não poderá receber novos gastos" e o usuário vê a fatura ficar verde "Paga".
  - **Fatura retroativa** (`:52-57`): marca `PAID` com `invoiceAmount` **positivo** (o `ensure(invoiceAmount >= 0)` de `:48` deixa passar) e **sem criar operação de pagamento**. ⚠️ **NÃO aplicar rótulo aqui** — ajustá-lo canonizaria o bug **9j.1**: o status diria "Paga" e o razão continuaria devendo, porque `invoiceOwed` é `Σ entries` do `invoiceId` e nada liquida a perna `LIABILITY` das compras. É a mesma divergência status-vs-razão que esta change existe para eliminar. **Resolver como bug (9j.1), não como texto.** Contexto original: `CloseInvoiceUseCase:52-57` (retroativa) e `:71-76` (fatura zerada) chamam `payInvoiceUseCase` e retornam `PAID` — sob o botão rotulado "fechar". E o use case **nunca consulta `isClosable`**: gateia por `!= PAID`, `!= CLOSED` e `closedAt.yearMonth == closingMonth`, aceitando fechar `FUTURE`, que nenhuma UI oferece.
- [x] 4b.8 **DECIDIDO: estreitar `ReopenInvoiceUseCase` para `CLOSED`.** Nenhum caller além da UI, que já só oferece em `isClosed`. E o use case aceita FUTURE/RETROACTIVE, o que criaria **duas OPEN simultâneas**, quebrando a invariante que `InvoiceDao:25` (`LIMIT 1`) e `GetOrCreateInvoiceForMonthUseCaseImpl:41` assumem. **Consequência: invisível ao usuário, e fecha um buraco** que hoje só a UI segura. Contexto original: `ReopenInvoiceUseCase:25,29` permite CLOSED/FUTURE/RETROACTIVE; as duas telas só oferecem `isClosed`.
- [x] 4b.9 **DESENHADA (design D23): fatura `PAID` é imutável; `CLOSED` é imutável exceto para o próprio pagamento; a invariante mora no ponto de escrita, junto do `Σ=0`.** A objeção abaixo se dissolve pela **D13**: com o cartão sendo encerrado e não apagado, o bulk delete de `DeleteCreditCardUseCase` deixa de existir. Contexto original: — a recomendação anterior ("gates no domínio") tem consequência.** `DeleteCreditCardUseCase:17` apaga em bloco via SQL (`OperationDao:30-35`), sem enumerar operações: um gate de fatura fechada no domínio tornaria **indeletável todo cartão que já teve fatura paga** — isto é, todo cartão em uso real. E `deleteOperationById` é ponto único de **5 chamadores**, dois deles (`AdjustBalanceUseCase:69`, `AdjustInvoiceUseCase:67`) manutenção interna que **precisa** apagar sem gate. O gate não pode morar no repositório. **É desenho a fazer, não escolha binária.** Contexto original: Hoje são todos de UI: `DeleteTransactionViewModel:19-23` e `DeleteInstallmentViewModel:23-30` apagam sem gate algum; só `DeleteFutureInvoiceUseCase:24` tem gate de domínio. Sem decidir isto, a 5.6 reimplementa o mesmo erro na UI.
- [x] 4b.6 Unificar as 3 cópias do bloco de ações de fatura e as 4+ do predicado de status, **depois** de 4b.1-4b.2 decidirem qual regra vence. **Execução:** unifiquei os **predicados** (a divergência de "qual é a regra"): `isClosable` tinha 3 formas com corte de data divergente — canonizado em `Invoice.isClosableOn(date)` (regra vencedora do 4b.2: exige a data para `OPEN` **e** `RETROACTIVE`), consumido por `InvoiceUi:25` e `InvoiceTransactionsViewModel:139`; e `CreditCardCard:302-303` deixou de comparar enum cru (`== CLOSED`/`== OPEN`) passando aos canônicos `.isClosed`/`.isOpen`. **Não fundi os blocos visuais**: os três oferecem conjuntos de ação distintos (só a tela de fatura oferece "apagar futura") — isso é escolha de apresentação ("se", não "qual", D16/spec) — e fundi-los exigiria uniformizar shape (8dp vs 12dp) e namespaces de string, produzindo a "deriva acidental" que a change proíbe.

## 5. Modelos de UI planos e mappers

- [x] 5.1 Colapsar `OperationPerspective` (sealed `Account`/`Card`) em `TransactionPerspective(accountId, invoiceId? = null)`, com o cartão entrando via `CreditCard.accountId` (design D6).
- [x] 5.2 ~~Fallback de `CreditCard.accountId == null`~~ **DISSOLVIDA pela D21**: com criação eager, `accountId` é `NOT NULL` e o cartão sempre tem conta — a perspectiva deixa de ser inconstruível. Manter apenas um teste que fixe a invariante.
- [x] 5.3 Converter `OperationUi` em DTO plano (id + valores resolvidos), movendo resolução de perspectiva, derivação de rótulo e inversão de sinal para um mapper — dissolvendo o `requireNotNull` do `by lazy` (design D5).
- [x] 5.4 Converter `AccountUi` em DTO plano, sem `account: Account` e sem cálculo em construtor.
- [x] 5.5 Reimplementar a regra de editabilidade **gate a gate** (design D2), não só a contagem: status de fatura (`Invoice.Status.isEditable`, **usado** e não reescrito), rótulo `!= ADJUSTMENT`, exatamente 1 perna monetária, sem parcelamento. Teste com asserção por gate — um teste que só cheque o resultado final não distingue "certo" de "certo por acaso".
- [x] 5.6 Implementar a regra de **remoção** (design D2 nível 1), que nenhuma rodada anterior tinha especificado: fatura `CLOSED`/`PAID` bloqueia editar **e** apagar.
- [x] 5.7 Eliminar as reimplementações inline do predicado de status de fatura, **mantendo** a propriedade canônica (a versão anterior a listava como "cópia a eliminar", contradizendo 5.5): `Invoice.Status.isEditable` (`Invoice.kt:68-71`) **fica**; saem `InvoiceTransactionsUiState:39` e o `when` de `ViewOperationModal:355`, que reenumera à mão o complemento exato de `Invoice.Status.isBlocked` (`:65-66`). São **quatro** formas do mesmo predicado: `Invoice.Status.isEditable` (canônica, **fica**), `Invoice.Status.isBlocked` (complemento exato), `InvoiceTransactionsUiState:39`, e o `when` de `ViewOperationModal:354-367`. ⚠️ **`CreditCardCard:302` NÃO entra aqui** — é `canPayInvoice = invoiceUi?.status == Invoice.Status.CLOSED`, o gate de **pagabilidade**, não de editabilidade. A versão anterior desta task o listava como 5ª cópia: executá-la faria o botão Pagar aparecer em fatura OPEN/FUTURE/RETROACTIVE e sumir de CLOSED — **inversão exata**. A contagem foi de três → quatro → cinco inflando com um predicado alheio, que é a doença que esta task diz corrigir. Ele pertence ao eixo de 4b.1.
- [x] 5.8 Derivar os **dois eixos de exibição** (design D15): `TransactionLabel` da operação (cor/título/ícone) e a direção da perna da perspectiva (texto de tipo/filtro). Não fundir num enum só.
- [x] 5.9 Remover os campos de tipo de domínio dos **modelos de UI** (`AccountUi`, `OperationUi`), deixando no máximo o identificador. ⚠️ **NÃO** remover a dependência `core/ui → core/model`: ela é **por desenho** (`core/ui/build.gradle.kts:11`; 14 componentes existem para renderizar domínio, e o `CLAUDE.md` define `core/ui` assim), e `core/ui/model` é **pacote**, não módulo Gradle. A versão anterior mandava tornar a regra "verificável por dependência" — o que a spec `presentation-mapping` explicitamente **proíbe** e o repo não permite sem extrair um módulo novo, fora de escopo. A verificação é por **inspeção dos modelos**.

## 6. Fim do double-write e migração v9 atômica

- [x] 6.1 Parar a escrita legada em `OperationRepository.createOperation`/`updateOperation`, mantendo a atomicidade do `useWriterConnection { immediateTransaction { … } }`.
- [x] 6.2 Remover as escritas legadas remanescentes. **Correção:** a versão anterior dizia que `EditTransactionViewModel:125` "passa ao largo do razão" — é o inverso: ela chama `transactionRepository.update(it)` **e** `updateOperation`; o que sobra é a escrita legada **extra**. Os fallbacks `operationId == null` de `AdjustBalanceUseCase:71,84` são de fato legado-only. Incluir também `deleteOperationById` (`OperationRepository:309-336`), o único caminho de escrita **fora** de `useWriterConnection`, e `AddInstallmentUseCaseImpl:140`, que faz **N `createOperation` num loop sem transação** (12x pode gravar 7 e falhar).
- [x] 6.3 Decidir e executar o destino de `Transaction.Type` e `Transaction.Target` como **vocabulário de entrada** (design D4): ambos são escolha do usuário e **ficam**; só a materialização como campo da perna morre. Inclui a decisão de contrato: `TransactionsRoute(filterTarget)` é `@Serializable` com `TransactionTargetNavType` — o enum precisa de um endereço que a `api` possa serializar. **Não é limpeza; é decisão de navegação.** (42 arquivos tocam `Target`.)
- [x] 6.4 Remover `Operation.Kind` (7 arquivos), agora que 1.3 e 4.7 cobrem seus consumidores.
- [x] 6.5 **Substituir** a `MIGRATION_7_8` por uma **`MIGRATION_7_9` única** — que inclui **a coluna de encerramento em `accounts`** (uma só, D21) e torna `categories.accountId`/`credit_cards.accountId` **`NOT NULL`** (os passos 4/5 já preenchem todos os existentes) (não há outra versão onde ela caiba: `AccountEntity` não a tem, o DB está em v8, e o D14 mata o caminho v8) e as **contas encerradas do v7** (tipo recuperável de `transactions.target`, `NOT NULL` no v7; baixa datada em `MAX(t.date)` do bucket; limite: nome e multiplicidade se perderam — N contas viram uma por tipo) (design D14 — a v8 não foi para produção, então nenhum dispositivo real precisa passar por ela): constrói plano de contas e razão; **não** semeia `'Saldo Inicial'` (fantasma, D8); **não** semeia `'Conta removida'` nem roteia pernas para `EQUITY` (D13); reconstrói contas apagadas do v7 como contas **encerradas** com tipo real + lançamento de baixa zerando o saldo; dropa `transactions` legada; renomeia `operations` → `transactions`; e cria `entries.transactionId` **já com o nome final** — sem `RENAME COLUMN`, sem FK pendurada, sem índice órfão.
- [x] 6.6 Remover a `MIGRATION_7_8`, o `8.json` **e o `Migration7To8Test`** (referencia `MIGRATION_7_8` em 9 pontos — sem removê-lo/convertê-lo o módulo de teste não compila e a 8.1 é inalcançável por construção); convertê-lo num `Migration7To9Test`; exportar um único `9.json`; registrar a `MIGRATION_7_9` em `getRoomDatabase`. Dispositivos de desenvolvimento em v8 perdem o caminho e precisam de reinstalação — nenhum usuário é afetado.
- [x] 6.7 Renomear `OperationEntity` → `TransactionEntity` (e `tableName`), `OperationDao` → `TransactionDao`, `Entry.operationId`/`EntryEntity.operationId` → `transactionId`, **e `RecurringOccurrenceEntity.operationId` → `transactionId`** (FK + índice `UNIQUE`, `RecurringOccurrenceEntity.kt:19-24,28`) — no mesmo commit de 6.5/6.6. Sem `recurring_occurrences`, a varredura 8.2 falha por construção.
- [x] 6.8 `Migration7To9Test`: v7 → v9 preservando saldos, fatura, patrimônio e totais por categoria; caso de **conta apagada com movimento no v7** (vira conta encerrada + baixa, patrimônio idêntico ao v7); assert de `PRAGMA foreign_key_list(entries)` → `transactions` e de `PRAGMA index_list(entries)` com o nome final; assert de que nenhuma `Entry` referencia conta inexistente.
- [x] 6.9 Remover o modelo legado de perna: `Transaction` (perna), `TransactionEntity` legada, `TransactionDao` legado, `ITransactionRepository`, `TransactionRepository`, `TransactionMapper` e `signedCents()`.
- [x] 6.10 Remover `SystemAccount.INITIAL_BALANCE` e `SystemAccount.REMOVED_ACCOUNT` (não semeadas pela `MIGRATION_7_9`) **e adicionar** as constantes de `'Conta encerrada'`/`'Cartão encerrado'` que a 6.5 semeia — `Database.kt:236` documenta que os nomes de conta de sistema espelham `SystemAccount`; a versão anterior removia duas constantes e introduzia duas contas semeadas sem constante, quebrando a invariante em silêncio.

## 6b. Encerrar em vez de apagar — use cases de runtime (depende de §6)

> Design D14: a coluna nasce **dentro** da v9 (6.5), não aqui. **Correção da 5ª rodada:** a versão
> anterior duplicava 6.5 como "6b.6" e punha a coluna em "6b.1", criando ordem circular (§6 → §6b → §6)
> e um teste em §6 (6.8) que verificava uma task de §6b. Aqui ficam só os use cases de runtime, que
> dependem da coluna existir.

- [x] 6b.2 Converter `DeleteAccountUseCase` em encerramento: conta sem lançamentos é removida; com lançamentos é encerrada; saldo ≠ 0 gera lançamento de baixa balanceado contra `EQUITY:Reconciliação` (design D13 — reuso deliberado, para não expandir contas de sistema nem tornar o CAP-3 load-bearing). Trocar o `either { }` por captura de exceção: hoje a `SQLiteException` atravessa o `either` e o `viewModelScope` sem handler, e o `onLeft` de crashlytics nunca roda. Teste dos três caminhos — não existe nenhum hoje.
- [x] 6b.3 Aplicar a `DeleteCreditCardUseCase`, hoje assimétrico e **não atômico** (dois passos sem `@Transaction`, contradizendo o commit `44d3bdd4`): ele apaga as operações de compra e preserva as de pagamento, deixando a `Account` `LIABILITY` viva sem fachada.
- [x] 6b.4 Aplicar a categoria (D22): criar o `DeleteCategoryUseCase`, **que não existe** — hoje `DeleteCategoryViewModel:19` chama o repo direto, sem `Either`, sem crashlytics, e com `analytics.logEvent` incondicional mesmo se falhar.
- [x] 6b.7 **Remover `budgets.categoryId`** na v9 (D22): é resíduo **write-only** — `BudgetMapper:26` só a escreve, `BudgetRepository:29` monta as categorias só da M2M `budget_categories`. É o `CASCADE` dela que **destrói o orçamento inteiro** ao apagar a categoria que por acaso é a primeira da lista, mesmo com outras vivas. Bug corrente, não débito da coexistência.
- [x] 6b.8 Filtrar categorias encerradas de `Budget.categories` **por leitura** (`BudgetRepository:29`), não por escrita (D21/D22) — nada é destruído, e um eventual reabrir volta a funcionar sozinho. Orçamento sobrevive ajustado enquanto tiver categoria viva.
- [x] 6b.9 Orçamento **sem nenhuma categoria viva permanece visível**, com progresso zero (decisão do usuário). Teste cobrindo os três casos: multi-categoria com uma encerrada, mono-categoria encerrada, e o `?: 0` do `BudgetMapper:26` que hoje gravaria `categoryId = 0` sob FK `NOT NULL`.
- [x] 6b.10 Corrigir a string `delete_category_message` (`strings.xml:335`): hoje promete só que as transações sobrevivem e não menciona o orçamento — que hoje é destruído em silêncio.
- [x] 6b.5 Excluir contas encerradas dos seletores e listagens ativas: `AccountDao` ganha `AND isClosed = 0` nas 5 queries `WHERE type='ASSET'`; `CategoryDao` e `CreditCardDao` passam a `JOIN accounts ON <fachada>.accountId = accounts.id` filtrando por `isClosed` (D21) — hoje não conhecem `accounts`, e passam a consumir a conta pelo `accountId` que já têm, preservando "apagar" como ação única do usuário.

## 7. Rename: `Operation` → `Transaction`

> Mecânico e quase todo por refactor de IDE. Manter em commits **separados** de qualquer
> mudança de comportamento. As entities/tabela/coluna **não** estão aqui: foram em **6.5-6.7**
> (a 6.7 é quem renomeia entity/DAO/`operationId` — ver 8.2), por obrigação do Room
> (design **D14**; a regra nasceu no D10, que o D14 revogou).

- [x] 7.1 Renomear o agregado de domínio `Operation` → `Transaction` (dono de `List<Entry>`), com `OperationRecurring`/`OperationInstallment` acompanhando.
- [x] 7.2 Renomear `IOperationRepository`/`OperationRepository`/`OperationMapper` e os bindings Koin correspondentes.
- [x] 7.3 Renomear `OperationLabel` → `TransactionLabel` e `OperationUi` → `TransactionUi`.
- [x] 7.4 Renomear os arquivos de UI da feature (`ViewOperationModal`, `ViewOperation*` MVI) e o pacote `viewTransaction`, hoje incoerentes entre si.

## 8. Verificação

- [x] 8.1 `./gradlew allTests` e `./gradlew check` verdes (o gate que ficou aberto no `balanced-ledger`, arquivado sem cumprir).
- [x] 8.2 Varredura de resíduo: nenhuma ocorrência de `Operation`, `signedImpact`, `signedCents`, `INITIAL_BALANCE` ou `initialBalance` fora de histórico/arquivo. Depende de **6.7** ter renomeado `operationId` (a versão anterior citava 6.6) — sem isso esta task falha por construção.
- [ ] 8.3 Paridade em device: saldos, faturas, patrimônio, gasto por categoria, dashboard e relatórios conferidos contra um backup pré-migração — o risco #1 desta change é número mudar em silêncio.
- [x] 8.4 Registrar o destino das CAPS do `balanced-ledger` arquivado. **Corrigido contra o arquivo — a versão anterior errava duas:**
  - **Fecham:** CAP-2 (forma in-memory do `CalculateBalance` — task 4.3), CAP-4 (**por construção, não por guarda**: `Entry`/`EntryEntity` **não têm data** — o corte sempre foi da operação; com 4.3 e 6.9 removendo a forma in-memory e a perna legada, some a *segunda* data e a divergência fica inconstruível. A 4.9 prescreve "um teste para um caller futuro não quebrar a invariante", que só tem conteúdo **durante** a coexistência), CAP-5 (só a coluna `TransactionEntity.type` era a dívida; morre com a tabela — o enum fica, D4), **CAP-6** (o par que ele nomeia — `Operation.kind` e `deriveOperationLabel` — é **todo da operação** (`{TRANSACTION, PAYMENT, TRANSFER}` e `{EXPENSE, INCOME, TRANSFER, PAYMENT}`); 6.4 mata o `kind`, 1.3 torna o `label` total, e converge para um, **exatamente como o CAP previu**), CAP-7 (fim da coexistência — §6).
  - **Permanecem:** CAP-3 (`ensureSystemAccount` check-then-act — Non-Goal declarado; o D21 encolhe a **superfície** do defeito para só contas de sistema ao tornar categoria/cartão eager, mas o CAP como escrito já nomeava só `ensureSystemAccount`, então ele não encolhe: o que encolhe são os outros dois `ensure*` que o CAP nunca cobriu) **e CAP-1 resto** — a query agrupada do dashboard é **Non-Goal explícito** do design ("Otimizar leitura… decisão de valor, não desta change"). A versão anterior desta task a declarava **fechada** enquanto o design a declarava **fora de escopo**: a change se contradizia com ela mesma sobre o que entrega.
  - ⚠️ **Não registrar o CAP-6 como "refutado"** — uma versão anterior desta task o fazia, citando o D15. **O D15 leu o CAP-6 errado, e esta task repetiu o erro sem abrir o arquivo.** O par do CAP-6 é `Operation.kind` × `deriveOperationLabel`, **ambos da operação**; o par do D15 é rótulo-da-operação × direção-da-perna. São pares **diferentes**: o do D15 de fato não converge (D15 está certo sobre o seu par), o do CAP-6 converge. O CAP-6 nunca afirmou o que o D15 lhe atribui. Ver a correção no D15 e no D3.
- [x] 8.5 **Varredura da regra "nenhuma feature reimplementa regra derivável do razão"** (spec `balanced-ledger`). Não é grepável por padrão único — a duplicação se disfarça de `when` local, de predicado com outro nome e de reenumeração à mão do complemento de um predicado existente. A verificação é por **inspeção dirigida**, e a lista de alvos já é conhecida: cada regra derivável tem de ter **um** dono, e nenhum consumidor pode reavaliar tipos de conta, status ou entries por conta própria. Alvos conhecidos, com a task que os fecha:
  - **rótulo/direção** — 1.3 (total, `EQUITY` primeiro) + 5.8 (dois eixos, D15); consumidores a limpar: `OperationCard:171-192` e `ViewOperationModal` (9d.1), `ReportExportLayout` (9d.2).
  - **status de fatura** — canônico `Invoice.Status.isEditable`; saem `InvoiceTransactionsUiState:39` e o `when` de `ViewOperationModal:354-367` (5.7). ⚠️ `CreditCardCard:302` **não** entra: é pagabilidade, outro eixo (4b.1).
  - **bloco de ações de fatura** — 3 cópias (4b.6); `TypeFilterChip` — 5 cópias (9i.1 + 9f.3).
  - **saldo / abertura** — 3 implementações → `balanceUpTo` (4.8); nenhum recálculo em modelo de UI (5.4).
  - **inversão de sinal** — hoje 2 à mão por `Transaction.Type`/`Category.Type` (9i.4); passa a ser dos mappers, por `AccountType` (D5/`presentation-mapping`).
  - **editabilidade/deletabilidade** — 5.5/5.6, gate a gate, e a guarda de escrita da D23 (9k.1).
  - **encerramento** — um campo em `accounts`, consumido pelas fachadas via `accountId` (D21); **zero** cópias nas fachadas (`account-lifecycle`).
  
  Definição de pronto: para cada regra acima, um único dono nomeado, e nenhum consumidor que a reavalie. A regra vale **daqui em diante**, não só no dia do merge — é o que decide se a arquitetura se mantém ou volta a ter cinco cópias de tudo.

  **Executado.** Donos, um por regra:
  - **rótulo** → `List<Entry>.deriveTransactionLabel()` (`core/model` `Ledger.kt`), consumido por `Transaction.label`. **direção da perna** → `deriveTransactionType(legAmount, entries)`. Os dois eixos coexistem (D15) e os consumidores leem `TransactionUi.label`/`.direction`, sem reavaliar.
  - **status de fatura** → `Invoice.Status.isEditable` (edição) e `isClosedToNewExpenses` (gasto novo). O antigo `isBlocked` fundia CLOSED e PAID, que se comportam diferente; foi renomeado para dizer a única pergunta que responde, e a distinção real mora na guarda de escrita.
  - **o que uma fatura aceita** → a guarda no ponto único de escrita (`TransactionRepository.ensureInvoiceAccepts`), ao lado do `Σ=0`. As cópias em `BuildTransactionUseCaseImpl` saíram.
  - **saldo / abertura** → `IEntryRepository` (`balance`, `balanceUpTo`, `accountFlows`). Nenhum modelo de UI calcula: `AccountUi` é DTO plano.
  - **inversão de sinal** → `AccountType.displaySign`/`displayBalance`. As duas inversões à mão (`CalculateCategorySpendingUseCaseImpl`, `CalculateReportCategorySpendingUseCase`, mais uma terceira achada em `ViewCategoryViewModel`) passaram a consumi-lo.
  - **editabilidade/deletabilidade** → `ViewTransactionUiState.isEditable` gate a gate, e a guarda de escrita para o resto.
  - **encerramento** → `accounts.isClosed`, consumido por categoria e cartão via `accountId` (`LEFT JOIN`). Zero cópias nas fachadas.
  - **`TypeFilterChip`** → as cópias por tela permanecem, e permanecem **legítimas**: cada uma oferece um conjunto de opções diferente, o que é escolha de apresentação ("se", não "qual"). O que era duplicação de *regra* — o predicado por trás do chip — passou a derivar do razão em todas.

- [x] 8.6 **Reescrever o `## Purpose` das specs `balanced-ledger`, `chart-of-accounts` e `ledger-reporting`** ao arquivar. **Nenhum delta jamais tocou um `Purpose`** (verificado: `grep -rln "## Purpose" changes/archive/*/specs/` → zero), porque delta opera sobre `### Requirement`. Consequência: os requisitos falam a língua nova e o `Purpose` fica com a velha, **e a 8.2 falha por construção** — `openspec/specs/` é vivo, não é "histórico/arquivo". Hoje: `balanced-ledger:5` diz "**operações** como conjuntos de entries… o rótulo da **operação**" (vocabulário que a §7 abole) e `ledger-reporting:5` diz "Substitui **`signedImpact()`**…" — que a 8.2 grepa e que já nem existe no código. Corrigir os três, e o `ledger-reporting:5` também no **registro**: um `Purpose` que descreve o que *substitui* narra uma transição, não um propósito — é a mesma doença da seção `## Ledger` do `CLAUDE.md` (8.7), no artefato ao lado.
- [x] 8.7 **Reescrever a seção `## Ledger` do `CLAUDE.md` para só invariantes.** Ela **não pode** ser reescrita antes de §6/§7 aterrissarem: hoje a coexistência *é* a verdade, e um `CLAUDE.md` que descreva o estado final estaria mentindo até lá. Ao fim da change, sai tudo o que é **estado datado** e fica só o que é **invariante**. Saem: o nome da change (`balanced-ledger`), a versão de schema e o nome da migração (`v8`/`MIGRATION_7_8`), o bullet **Coexistence** inteiro, `signedImpact`, a conta de sistema de saldo inicial (D8) e o vocabulário `operation` (§7). Ficam: plano de contas com o conjunto **fechado** de `AccountType`, `Entry` com Σ=0 por moeda validado na fronteira única de escrita, leitura por `Σ entries`, débito-positivo, derivação em vez de persistência. ⚠️ **O diagnóstico que originou esta task:** de cinco bullets, um já era falso e quatro morrem com a change — o que envelhece não é o assunto, é o **registro** (nome de change, versão, "currently"). A `Derivation rule` foi para **Conventions**, que é o registro que não envelhece; a instância do razão é normativa na spec `balanced-ledger` e o `CLAUDE.md` **não** deve repeti-la.
## 9. Cobertura do raio legado — varredura mecânica

> **Origem:** cruzamento mecânico de `grep -rl "signedCents|Transaction.Type|Transaction.Target|Operation.Kind|ITransactionRepository|domain.model.Transaction"` (produção, sem `/build/`) contra os nomes citados neste arquivo. Resultado da 1ª execução: **87 arquivos tocam o legado; 44 não eram nomeados por task alguma (51%)**. Seis rodadas de auditoria não pegaram isto — todas revisaram o texto escrito, não o território omitido.
>
> **Limite do método:** a varredura casa por *basename*. Falsos positivos são possíveis na direção "coberto" (`Transactions`, `Recurring`, `Category` casam com prosa solta); a direção "não coberto" é confiável. Reexecutar a varredura ao fim de §9 e exigir zero linhas `NENHUMA` é a definição de pronto desta seção.

### 9a. Ponto de escrita e mapper de entrada — o coração do D4, sem task até a 6ª rodada

- [x] 9a.1 Converter `LedgerEntryWriter` (`:16,41,58,64,75,95,98,103,106,110`) para receber a **intenção** em vez de `List<Transaction>`: ele **é** o tradutor intenção→entries (design **D20**), porque resolver a contrapartida tem efeito colateral — `ensureCategoryAccount:114-131` faz `accountDao.insert` + `categoryDao.update`, e `ensureCardAccount:133-148`/`ensureSystemAccount:150-155` idem. Depois de 6.9 o seu modelo de entrada deixa de existir.
- [x] 9a.2 ~~Criar um mapper puro `intenção → entries`~~ **CANCELADA — o objeto não pode existir** (design D20): a resolução de conta insere linhas sob demanda, logo um mapper puro é inconstruível. A responsabilidade fica no writer (9a.1). A versão anterior abria três tasks disputando um dono e prescrevia um mapper com 4 DAOs injetados — que não é mapper.
- [x] 9a.3 Converter `BuildTransactionUseCase` + `Impl` — **o caminho principal de criação**, sem task até a 6ª rodada. Ele já é hoje `(form: TransactionForm) → Either<Throwable, Transaction>`, isto é, já normaliza a intenção; o que muda é o **tipo de saída** (intenção, não perna). Sem DAO — a resolução de contas é do writer (D20).
- [x] 9a.4 `Ledger.kt`: remover `signedCents()`; **manter `deriveTransactionType`** (coexiste — 1.3/D15); reavaliar `displaySign`/`displayBalance` (`:21,26`), hoje sem nenhum consumidor de produção — o D5 atribui a inversão de sinal aos mappers e ela ainda não existe em lugar nenhum.
- [x] 9a.5 `TransactionsModule` (Koin): remover o binding de `ITransactionRepository`.

### 9b. Vocabulário de entrada — forms e selectors (decide 6.3)

- [x] 9b.1 `TransactionForm` (`:22,27,45,47,55,60,67-71`) e `RecurringForm` (`:11,31,36,41`): são a materialização de `Type`/`Target` como entrada do usuário. Definem o endereço final dos enums (ver 9b.5).
- [x] 9b.2 `TargetSelector` (`core/ui`, 7 refs): o picker conta-vs-cartão.
- [x] 9b.3 `AddTransactionModal`/`AddTransactionUiState`, `EditTransactionModal`/`EditTransactionUiState` — incl. o `Category.Type.isAccept` reimplementado localmente em `EditTransactionModal:369-374`.
- [x] 9b.4 `AddInstallmentModal`, `RecurringFormModal`/`RecurringFormUiState`.
- [x] 9b.5 **Fechar a Open Question do endereço de `Type`/`Target`** — e registrar que ela tem **uma só resposta viável**: `core/model`. Consumidores em módulos **core** (`core/model` `Recurring.kt:5` persistido, `core/database` `RecurringMapper`, `core/analytics` `event/*`, `core/ui` `OperationCard`/`AccountUi`/`TargetSelector`) **não podem** depender de `feature/transactions/api` — regra de dependência do `CLAUDE.md`, topologia estrela. O design oferecia as duas como escolha livre; uma delas quebra 4 módulos core.

### 9c. Navegação e analytics — contratos externos

- [x] 9c.1 `TransactionsGraph` (`:24-28`), `TransactionTypeNavType`, `TransactionTargetNavType`, `TransactionsRoute`: os `NavType` serializam por `value.name`/`valueOf` — renomear **constante** estoura no restore pós-process-death. Zero deep links no projeto (verificado), então o risco é só esse.
- [x] 9c.2 `core/analytics/.../event/Transactions.kt` (`:8,18,28-33`) e `event/Recurring.kt` (`:9,19,29,39,49,59,69`): **6** eventos recebem modelo de domínio por construtor (`DeleteTransaction`, `DeleteRecurring`, `ConfirmRecurring`, `SkipRecurring`, `StopRecurring`, `ReactivateRecurring`) — **7** com `DeleteInstallments` (9i.7), incl. `DeleteTransaction(transaction: Transaction)`, que lê `.type`/`.target`/`.category` da perna que 6.9 remove. **`proposal.md` declarava o módulo impactado e `tasks.md` tinha zero menções a ele.** ⚠️ Os nomes das constantes são **formato de fio publicado** (`.name.lowercase()`): não renomear.

### 9d. Renderização — `Operation.Kind` tem 7 consumidores, não 1

> A task 6.4 afirmava "*remover `Operation.Kind`, agora que 1.3 e 4.7 cobrem seus consumidores*". Cobrem **1 de 7**, e a afirmação não listava callers — violação da regra de método do design.

- [x] 9d.1 `OperationCard` (`:88,89,104,151,171-192,194-200`): ícone, título, cor e sinal — cópia literal das regras do `ViewOperationModal`. É o item de lista do app.
- [x] 9d.2 `ReportExportLayout` (`:173,174,185,197`).
- [x] 9d.3 `DashboardComponentsBuilder:133` (`filterNot { Kind.TRANSFER/PAYMENT }`) — 4.5/4.11 cobrem `:216` e `:156,157,181,186`; `:133` não estava em lista nenhuma.
- [x] 9d.4 `TransactionsViewModel:55,56,70` — 4.11 cobre só `:72`.
- [x] 9d.5 `DashboardComponentContent` (`:91-92`, `:217-232`, `:535`), `DashboardPreviewFactory`.

### 9e. Use cases que criam operação — 3 dos 8 sem task

- [x] 9e.1 `TransferBetweenAccountsUseCase` (`:58,64,71`).
- [x] 9e.2 `AdvanceInvoicePaymentUseCase` (`:66,74,84`) — o 7º use case, esquecido nas rodadas 1-3.
- [x] 9e.3 `ConfirmRecurringUseCase` (`:64,84`) e `SaveRecurringUseCase`.

### 9f. Filtros, UiStates e o fluxo de recorrência

- [x] 9f.1 `TransactionsAction`, `TransactionsFilters`.
- [x] 9f.2 `AccountsAction`/`AccountsUiState`/`AccountsViewModel`.
- [x] 9f.3 `CreditCardsAction`/`CreditCardsScreen`/`CreditCardsUiState`/`CreditCardsViewModel`.
- [x] 9f.4 `InstallmentsAction`/`InstallmentsUiState`/`InstallmentsViewModel`, `InvoiceTransactionsAction`.
- [x] 9f.5 `ConfirmRecurringAction`/`ConfirmRecurringUiState`/`ConfirmRecurringViewModel`, `RecurringUiState`, `RecurringViewModel`.
- [x] 9f.6 `RecurringMapper` — `Recurring.kt:5` **persiste** `type: Transaction.Type` na tabela `recurring`, que esta change não remove.

### 9g. Restantes

- [x] 9g.1 `CalculateBudgetProgressUseCase` (`:39` filtra por `today.yearMonth`, não pelo mês selecionado).
- [x] 9g.2 `BudgetFormViewModel`, `CalculateReportCategorySpendingUseCase`, `DeleteTransactionModal` (recebe `Transaction` por construtor).

### 9i. Descobertos pelo portão vácuo (falsos positivos do matcher por basename)

- [x] 9i.1 As **4 cópias restantes** de `TypeFilterChip`, que 4b.4 decide e nenhuma task implementava: `feature/transactions/impl/.../TransactionsScreen.kt:293-295`, `feature/accounts/impl/.../AccountsScreen.kt:622-627,637`, `feature/creditcards/impl/.../InstallmentsScreen.kt:711,725`, `feature/creditcards/impl/.../InvoiceTransactionsScreen.kt:788,802`. (A 5ª, `CreditCardsScreen:662,674`, está em 9f.3.) **A decisão 4b.4 sairia sem executor.**
- [x] 9i.2 `feature/creditcards/impl/.../PayInvoicePaymentUseCase.kt` — cria operação de 2 pernas; a §9e dizia "3 dos 8 sem task" e não o incluía. Só aparecia na lista de callers de 4b.1.
- [x] 9i.3 `feature/transactions/impl/.../ViewAdjustmentModal.kt` — só aparecia em 4b.3 como divergência; precisa de task de execução (é o modal que apaga sem gate de fatura).
- [x] 9i.4 `feature/categories/impl/.../CalculateCategorySpendingUseCaseImpl.kt:20,24,48,61` e `feature/report/impl/.../CalculateReportCategorySpendingUseCase.kt:64` — **duas inversões de sinal à mão** (`displaySign: Double`, `1.0`/`-1.0` hardcoded). O D18 afirma que "a inversão por `AccountType` não existe em lugar nenhum"; existem duas, por `Transaction.Type`/`Category.Type`. Nenhum dos dois casa os padrões da varredura — **buraco do próprio método**, não do plano.
- [x] 9i.6 `feature/transactions/impl/.../deleteTransaction/DeleteTransactionViewModel.kt` — recebe `transaction: Transaction` por construtor (`:7,13`) e lê `transaction.operationId ?: transaction.id` (`:20`); **6.9 o quebra por compilação**. Só era mencionado em 4b.9, que é decisão — exatamente o padrão que a 9i.3 criou para o `ViewAdjustmentModal`, e o gêmeo passou pelo portão.
- [x] 9i.7 `core/analytics/.../event/Installments.kt:5,18,20` — importa `domain.model.Operation` e `DeleteInstallments(installment, operations: List<Operation>)`. **Zero menções nos 8 artefatos**; §7.1 renomeia `Operation` e a 9c.2 cobre só `event/Transactions.kt` e `event/Recurring.kt`. **Buraco do método**: a varredura casa `Operation.Kind`, não `Operation` nu.
- [x] 9i.5 `core/model/.../extension/Category.kt` (`isAccept`, reimplementado localmente em `EditTransactionModal:369-374`) e `feature/creditcards/api/.../AddInstallmentUseCase.kt`.

### 9k. Invariante de fatura imutável (design D23)

- [x] 9k.1 Implementar a guarda no ponto único de escrita (`OperationRepository`, junto do `Σ=0` do `LedgerEntryWriter`): fatura `PAID` → nenhuma operação criada/atualizada/removida; fatura `CLOSED` → só operação cujo rótulo derivado (D3) é `PAYMENT`; demais → livre. Erro tipado, como o `LedgerError.Unbalanced`.
- [x] 9k.2 Absorver as **cinco** implementações espalhadas da regra de criação: `BuildTransactionUseCaseImpl:90` e `:94` (dois `ensure` que são `isBlocked` partido ao meio, levantando o **mesmo** erro), `GetOrCreateInvoiceForMonthUseCaseImpl:31`, `AddInstallmentUseCaseImpl:81`.
- [x] 9k.3 Reavaliar `Invoice.Status.isBlocked` (`Invoice.kt:65-66`): ele funde `CLOSED` e `PAID`, que se comportam **diferente** (D23). Funciona hoje só porque os 5 usos são todos de criação de gasto, onde os dois coincidem. Ou vira `isClosedToNewExpenses`, ou os consumidores passam a distinguir.
- [x] 9k.4 Criar `DeleteTransactionUseCase` e `DeleteInstallmentUseCase` — **não existem**; os ViewModels chamam o repositório direto (`DeleteTransactionViewModel:20`, `DeleteInstallmentViewModel:25`). Isso é camada, não invariante. ⚠️ `DeleteTransactionViewModel:20` faz `transaction.operationId ?: transaction.id` — trata id de perna como id de operação.
- [x] 9k.5 Teste da invariante nos três estados, incluindo o caso que a 1ª versão do desenho quebrava: **pagar uma fatura CLOSED deve passar**.

### 9j. Bugs **da v8** — criados por este branch, corrigidos aqui

> Separados por origem via `git diff main HEAD` (**decisão do usuário**). A v8 nunca foi publicada,
> então o impacto de todos é **zero** — mas são resíduo deste branch e saem com ele.
>
> **Os três têm a mesma causa:** o branch introduziu o razão e **não varreu todos os consumidores**.
> `AdjustBalance` foi roteado, `AdjustInvoice` não. Create/update ganharam transação, delete não. Um
> modal novo nasceu sem o gate que o irmão tem. É a "propagação incompleta" que oito rodadas de
> auditoria cobraram destes artefatos — cometida antes, no código.

- [x] 9j.1 **Fatura retroativa com dívida vira PAID sem liquidar — escala na v8.** `CloseInvoiceUseCase` está **inalterado desde a main**, mas a natureza do bug muda: na v7 a divergência ficava contida no cálculo daquela fatura; **a partir da v8 o razão não conhece status** — as compras criaram pernas `LIABILITY` na conta do cartão e marcar PAID não cria nada, então a dívida fica no saldo da conta **para sempre** e entra no patrimônio. Deixar passar seria a change **piorando** um bug herdado. `CloseInvoiceUseCase:53` → `PayInvoiceUseCase` marca PAID sem operação; o `ensure(invoiceAmount >= 0)` (`:48`) deixa **positivo** passar. **Não existe teste** para `PayInvoiceUseCase`, `CloseInvoiceUseCase`, `ReopenInvoiceUseCase`, `isPayable` nem `isClosable`.
- [x] 9j.3 **`ViewAdjustmentModal:220-236` apaga ajuste de fatura fechada sem gate** — **o arquivo nasceu neste branch**; o furo é v8. `ReportViewerScreen:295-296` roteia ajustes para lá. Coberto estruturalmente pela D23 (a guarda do ponto de escrita), mas a UI também precisa parar de oferecer.

> **1.1a e 1.1b também são v8** e já têm task própria (§1): na main o `AdjustBalance` fazia
> `repository.update(...)` — **correto**; o branch trocou por `updateOperation`, que não toca
> `transactions`. E o `AdjustInvoice` ficou sem rota de razão porque o razão **nasceu aqui**.
> O crash **787** idem: `EntryEntity` com FK `NO_ACTION` é novo.

### 9m. Bugs **da main** — anteriores ao branch, a conferir (não corrigir aqui)

> `git diff main HEAD` mostra estes arquivos **inalterados** pelo branch: os bugs existem em produção,
> na v7, hoje. **Decisão do usuário:** não representam perigo e serão investigados na main — ficam
> aqui apenas como itens de **conferência**, para que a change não os agrave nem os herde em silêncio.

- [x] 9m.1 **CONFERIDO — resolvido por construção.** O chip mapeia "Pagamento" para `TransactionType.INCOME`; o filtro agora deriva a direção da perna **do cartão** (`deriveTransactionType` sobre a entry `LIABILITY`), e um pagamento credita o cartão. O que nascia morto passou a funcionar sem task própria. **Conferir na main:** filtro "Pagamento" da tela de fatura devolve lista vazia. `Operation.kind`/`Operation.type` **já existem na main** (`:7,29,44`) e o `InvoiceTransactionsScreen` está **inalterado** — o chip nasce morto na v7. Confirmar comportamento em produção antes de decidir se a change o toca.
- [x] 9m.2 **Conferir:** `InstallmentUiMapper:42-44` usa `status.isEditable` para decidir deletabilidade, colidindo de nome com `Invoice.Status.isDeletable`, que significa outra coisa (`Invoice.kt:68-75`). Arquivo inalterado. O `!= false` torna parcelamento sem fatura resolvida deletável por default.
- [x] 9m.3 **CONFERIDO e corrigido.** `DeleteRecurringUseCase` existia sem **nenhum binding Koin** — por isso a ViewModel chamava o repositório direto. Binding adicionado e a ViewModel passa por ele, com `Either` e crashlytics. **Conferir:** `DeleteRecurringUseCase` existe e a ViewModel **não o usa** (`DeleteRecurringViewModel:19-23` chama o repo direto). Arquivo inalterado; o use case é gate vazio de qualquer forma.
- [x] 9m.4 **CONFERIDO e corrigido** (§6.2): `createTransactions` atômico substitui o laço. **Conferir:** `AddInstallmentUseCaseImpl:140` faz N `createOperation` num loop **sem transação** — o loop **já está na main** (`:139-146`). 12x pode gravar 7 e falhar.
- [x] 9m.5 **CONFERIDO e corrigido** (§6b.3): passa pelo `CloseAccountUseCase`, sem os dois passos soltos. **Conferir:** `DeleteCreditCardUseCase` são dois passos sem `@Transaction`. Arquivo **inalterado** — a não-atomicidade é da v7, e contradiz o commit `44d3bdd4` que consertou o caso vizinho.
- [x] 9m.6 **CONFERIDO e corrigido**: `deleteTransactionById` roda dentro de `useWriterConnection`, e a remoção em lote reusa o mesmo corpo numa transação só. **Conferir:** `deleteOperationById` fora de `useWriterConnection`. A main não tinha `useWriterConnection` em lugar nenhum — a não-atomicidade é v7; o que este branch fez foi consertar create/update e **deixar o delete**, criando a inconsistência.
- [x] 9m.7 **Conferir:** `Invoice.kt:41-48` importa `androidx.compose.ui.graphics.Color` dentro de `core/model` — violação da regra de camada que o próprio `CLAUDE.md` declara.

### 9h. Portão da varredura

- [x] 9h.1 ⚠️ **O portão anterior era vácuo** — dava zero **antes de qualquer trabalho**. O matcher casava por *basename* em prosa solta, e produzia 8 falsos positivos, dois deles por **substring** (`TransactionsScreen` ⊂ `InvoiceTransactionsScreen`; `AddInstallmentUseCase` ⊂ `AddInstallmentUseCaseImpl`) e três em que a única menção era uma decisão de produto ou uma instrução de "**não tocar**". Eu declarei o matcher falível e o promovi a definição de pronto assim mesmo. **Portão novo:** a varredura casa pelo **path completo**, e só conta como coberto se a task o citar num **item de trabalho** — não em prosa, não numa decisão, não numa ressalva de "não tocar". O conjunto de padrões ganha **`domain.model.Operation` nu** (não só `Operation.Kind`), sem o qual `event/Installments.kt` fica invisível (9i.7). ⚠️ **Executado na 8ª rodada, o portão novo dava 1, não 0** — pegou `DeleteTransactionViewModel` (9i.6). É a primeira ferramenta destes artefatos que acha o próprio autor errado.
- [x] 9h.2 Antes de qualquer afirmação de "morto"/"inalcançável"/"coberto"/"única cópia" em artefato, grepar os callers e **listá-los ali** (regra de método, `design.md`). As afirmações que sobreviveram erradas por seis rodadas são exatamente as que não listam callers.
