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
- [x] 4b.10 **Fechado de verdade, e não onde a 1ª versão dizia.** A 1ª versão nomeava a ponta aberta (`EditAccountBalanceViewModel:82` relê pelo `AccountDao.getAccountById` **sem filtro**, então uma conta encerrada ainda chega ao `AdjustBalanceUseCase`) **e se marcava concluída assim mesmo** — um buraco conhecido com o checkbox marcado é pior que um não descoberto, porque o checkbox é o que se lê. A correção não é filtrar na ViewModel: encerramento é invariante de conta, e agora é recusado na **fronteira de escrita** (`LedgerEntryWriter`, `LedgerError.ClosedAccount`), onde toda perna de toda escrita passa — mesma forma da guarda de fatura (D23). Coberto por teste.
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
- [x] 6b.5 Excluir contas encerradas dos seletores e listagens ativas: `AccountDao` ganha `AND isClosed = 0` nas 5 queries `WHERE type='ASSET'`; `CategoryDao` e `CreditCardDao` passam a `JOIN accounts ON <fachada>.accountId = accounts.id` filtrando por `isClosed` (D21) — hoje não conhecem `accounts`, e passam a consumir a conta pelo `accountId` que já têm, preservando "apagar" como ação única do usuário. **Executado como especificado (D21):** categoria e cartão são criados junto com sua conta, numa transação só; `accountId` é `NOT NULL` no domínio, na entidade e no schema v9. `LedgerEntryWriter` passa a consultar em vez de inserir sob demanda, encolhendo o CAP-3 para só contas de sistema. ⚠️ **Uma 1ª versão desta task resolveu por `LEFT JOIN` + `accountId` nulável**, alegando conflito entre o `9.json` gerado das entidades e o `NOT NULL` da migração. O conflito era real, mas a saída estava errada: cobria o sintoma citado pela D21 e perdia as duas entregas dela, reintroduzindo o ramo `if (account == null)` que esta change existe para eliminar. Corrigido.

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

- [x] 8.1 `./gradlew jvmTest testDebugUnitTest` verdes. ⚠️ **`check` NÃO está verde**: o link de teste iOS falha com `ld: framework 'FirebaseCore' not found`, falha de ambiente **pré-existente** (verificada contra a árvore limpa). Os nomes de teste com vírgula, ilegais em Kotlin/Native, foram corrigidos, e `kotlin.daemon.jvmargs` subiu de 3G para 6G porque o compilador nativo estourava heap — **ajuste não previsto pela change**. Uma versão anterior desta task afirmava "`check` verde", o que era falso.
- [x] 8.2 Varredura de resíduo: zero ocorrências de `Operation`, `signedImpact`, `signedCents`, `INITIAL_BALANCE` e `initialBalance` nos `.kt` e em `openspec/specs/`. ⚠️ **A 1ª versão desta task era falsa**: declarava a varredura completa enquanto `specs/ledger-reporting/spec.md:10,18` ainda continha `signedImpact()` — exatamente o furo que a 8.6 prevê ao dizer que `openspec/specs/` é vivo. A 8.6 corrigiu os `## Purpose` e não os corpos dos requisitos. Corrigido. **Não coberto por task**: ~26 chaves de string foram renomeadas (`view_operation_*`→`view_transaction_*`, `operation_card_*`→`transaction_card_*`, `*initial_balance`→`*opening_balance`).
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

  ⚠️ **A 1ª versão desta seção declarava a varredura executada e foi falsificada por auditoria adversarial em três pontos** — um deles sendo o próprio componente que ela nomeia como dono. É o defeito mais caro do registro: uma seção escrita para impedir afirmação falsa contendo afirmação falsa. O que estava errado, e o que foi feito:
  - `TransactionRepository.deriveIntentLabel` retornava `TransactionLabel` decidindo por `target`, enquanto o dono declarado (`deriveTransactionLabel`) decide por `AccountType` — **dois donos da mesma regra**, um deles dentro da guarda de escrita. Corrigido: virou `settlesACard(): Boolean`, que responde uma pergunta booleana e não veste o tipo canônico. A razão de não poder consumir o dono está no KDoc: no momento da escrita as entries ainda não existem.
  - A guarda de fatura estava **duplicada** (uma cópia em `ensureInvoiceAccepts`, outra inline em `deleteTransactionById`) e, pior, `deleteTransactionsByIds` — o caminho que apagar parcelamento toma — chegava à remoção **sem passar por guarda nenhuma**. Buraco de correção, não de arrumação, introduzido por mim ao extrair `removeRow`. Um dono agora (`ensureInvoicesAccept`), com teste que fica vermelho sem ele.
  - A justificativa das 5 cópias de `TypeFilterChip` dizia que "a regra agora deriva do razão em todas". Era falso: `AccountsScreen` renderizava o rótulo `ADJUSTMENT` sem braço de cor correspondente — exatamente a divergência que o D16 catalogou. Corrigido.

  **Débito conhecido, deixado em aberto por esta change:**
  - `CalculateReportStatsUseCase:52-73` soma entries **em memória** sobre a lista carregada e calcula ali seu próprio saldo de abertura. Não é regra diferente — é `Σ entries` com a mesma convenção —, mas é um segundo lugar onde a aritmética mora, e `ledger-reporting` diz "sem cálculo de saldo em memória". Fechar exige agregado SQL com escopo de conjunto de contas e exclusão de transferência interna, que não existe no `EntryDao`. **Não fechado.**
  - `TransactionsViewModel:70-73` soma `payment` em memória, embora o agregado `invoicePayment` da D12 exista. **Não fechado.**
  - `InvoiceUi`/`CreditCardUi` seguram modelo de domínio, e `InvoiceUi` calcula `isClosable` em propriedade sobre `Clock.System.now()`. A task 5.9 restringiu a planificação a `AccountUi`/`TransactionUi` **sem dizer**; `presentation-mapping` pede mais. **Não fechado.**
  - `deleteTransactionsByCreditCard` sobrevive em `ITransactionRepository` sem caller de produção. **Resíduo.**

  **Executado.** Donos, um por regra:
  - **rótulo** → `List<Entry>.deriveTransactionLabel()` (`core/model` `Ledger.kt`), consumido por `Transaction.label`. **direção da perna** → `deriveTransactionType(legAmount, entries)`. Os dois eixos coexistem (D15) e os consumidores leem `TransactionUi.label`/`.direction`, sem reavaliar.
  - **status de fatura** → `Invoice.Status.isEditable` (edição) e `isClosedToNewExpenses` (gasto novo). O antigo `isBlocked` fundia CLOSED e PAID, que se comportam diferente; foi renomeado para dizer a única pergunta que responde, e a distinção real mora na guarda de escrita.
  - **o que uma fatura aceita** → a guarda no ponto único de escrita (`TransactionRepository.ensureInvoiceAccepts`), ao lado do `Σ=0`. As cópias em `BuildTransactionUseCaseImpl` saíram.
  - **saldo / abertura** → `IEntryRepository` (`balance`, `balanceUpTo`, `accountFlows`). Nenhum modelo de UI calcula: `AccountUi` é DTO plano.
  - **inversão de sinal** → `AccountType.displaySign`/`displayBalance`. As duas inversões à mão (`CalculateCategorySpendingUseCaseImpl`, `CalculateReportCategorySpendingUseCase`, mais uma terceira achada em `ViewCategoryViewModel`) passaram a consumi-lo.
  - **editabilidade/deletabilidade** → `ViewTransactionUiState.isEditable` gate a gate, e a guarda de escrita para o resto.
  - **encerramento** → `accounts.isClosed`, consumido por categoria e cartão via `accountId` (`JOIN` simples — a variante `LEFT JOIN` foi a 1ª tentativa, rejeitada junto com a criação eager, ver 6b.5), e recusado na fronteira de escrita para qualquer lançamento novo (`LedgerError.ClosedAccount`). Zero cópias nas fachadas.
  - **`TypeFilterChip`** → as cópias por tela permanecem, e permanecem **legítimas**: cada uma oferece um conjunto de opções diferente, o que é escolha de apresentação ("se", não "qual"). O que era duplicação de *regra* — o predicado por trás do chip — passou a derivar do razão em todas.

- [x] 8.6 **Reescrever o `## Purpose` das specs `balanced-ledger`, `chart-of-accounts` e `ledger-reporting`** ao arquivar. **Nenhum delta jamais tocou um `Purpose`** (verificado: `grep -rln "## Purpose" changes/archive/*/specs/` → zero), porque delta opera sobre `### Requirement`. Consequência: os requisitos falam a língua nova e o `Purpose` fica com a velha, **e a 8.2 falha por construção** — `openspec/specs/` é vivo, não é "histórico/arquivo". Hoje: `balanced-ledger:5` diz "**operações** como conjuntos de entries… o rótulo da **operação**" (vocabulário que a §7 abole) e `ledger-reporting:5` diz "Substitui **`signedImpact()`**…" — que a 8.2 grepa e que já nem existe no código. Corrigir os três, e o `ledger-reporting:5` também no **registro**: um `Purpose` que descreve o que *substitui* narra uma transição, não um propósito — é a mesma doença da seção `## Ledger` do `CLAUDE.md` (8.7), no artefato ao lado.
- [x] 8.7 **Reescrever a seção `## Ledger` do `CLAUDE.md` para só invariantes.** Ela **não pode** ser reescrita antes de §6/§7 aterrissarem: hoje a coexistência *é* a verdade, e um `CLAUDE.md` que descreva o estado final estaria mentindo até lá. Ao fim da change, sai tudo o que é **estado datado** e fica só o que é **invariante**. Saem: o nome da change (`balanced-ledger`), a versão de schema e o nome da migração (`v8`/`MIGRATION_7_8`), o bullet **Coexistence** inteiro, `signedImpact`, a conta de sistema de saldo inicial (D8) e o vocabulário `operation` (§7). Ficam: plano de contas com o conjunto **fechado** de `AccountType`, `Entry` com Σ=0 por moeda validado na fronteira única de escrita, leitura por `Σ entries`, débito-positivo, derivação em vez de persistência. ⚠️ **O diagnóstico que originou esta task:** de cinco bullets, um já era falso e quatro morrem com a change — o que envelhece não é o assunto, é o **registro** (nome de change, versão, "currently"). A `Derivation rule` foi para **Conventions**, que é o registro que não envelhece; a instância do razão é normativa na spec `balanced-ledger` e o `CLAUDE.md` **não** deve repeti-la.
- [x] 8.12 **Divergência entre a regra de encerramento e o que a UI promete, levantada pelo usuário.** A pergunta era simples — "não se exclui conta ou cartão com transações, certo? por que a UI diz *excluir* nos dois casos?" — e expôs duas coisas distintas.

  **O botão dizer "excluir" é deliberado e fica** (6b.5): o usuário não deve precisar aprender vocabulário contábil, e do lado dele a coisa some da tela. Encerrar-vs-remover é decisão do razão, não dele.

  **Mas duas mensagens estavam erradas, e uma era mentira que esta change criou:**
  - `delete_credit_card_message` prometia que "as faturas e transações associadas **também serão excluídas**". Era verdade na main, onde `DeleteCreditCardUseCase` chamava `deleteTransactionOperationsByCreditCard`. A 6b.3 removeu esse comportamento e **não tocou a string** — passou a prometer destruição de histórico que não acontece mais. A 6b.10 mandava corrigir a string de **categoria**; corrigi só ela e não olhei as irmãs. É a mesma propagação incompleta que as auditorias já tinham nomeado.
  - `delete_account_message` dizia só que as transações sobrevivem, sem dizer que a **conta** também sobrevive, encerrada.

  Ambas reescritas, nos dois idiomas, dizendo o que de fato acontece.

  ⚠️ **Lacuna real, não fechada:** `Account.isClosed` é escrito e **nunca lido por nenhuma UI** — verificado (`grep isClosed` em `feature/*/impl` e `core/ui` só encontra `Invoice.Status.isClosed`, que é outra coisa). Não há selo de "encerrada", não há lista de encerradas, e **não há como reabrir**. Do ponto de vista do usuário, encerrar é indistinguível de apagar e é irreversível. Isso contradiz a 6b.8, que justifica filtrar por leitura dizendo que "um eventual reabrir volta a funcionar sozinho" — o mecanismo suporta, a interface não oferece. É trabalho de UI fora do escopo desta change, mas fica registrado como consequência dela, não como acaso.

- [x] 8.11 **Bug de runtime achado pelo usuário: ajuste de saldo não refletia em tempo real na tela de contas.**

  **Causa, e é consequência direta da §4.** Os agregados do razão (`balanceUpTo`, `accountFlows`, `balanceInMonth`, `entryCountInMonth`) são `suspend` — leitura única, não fluxo. Ao virar os leitores para eles, uma tela que antes recalculava somando a lista de transações observada passou a depender de nada que mudasse quando o razão muda. `AccountsViewModel.accountsWithDomain` combinava só `accounts` + `selectedMonth`: um ajuste não altera a tabela `accounts` nem o mês, então o `combine` nunca reexecutava e os cards ficavam com o número velho. A lista de transações da mesma tela atualizava, porque essa sim vem de um `Flow` — o que torna o sintoma confuso: parte da tela viva, parte congelada.

  **Alcance verificado, não presumido.** Varridos todos os consumidores de `IEntryRepository` em ViewModel: `BudgetsViewModel`, `ViewBudgetViewModel`, `DashboardViewModel` e `InvoiceTransactionsViewModel` já incluíam `observeAllTransactions()` no `combine` e estavam corretos por acidente feliz. Quebrados: `AccountsViewModel` e `ViewCategoryViewModel`.

  **Correção:** `IEntryRepository.observeLedgerChanges()` — sinal explícito de "o razão mudou", sobre um `COUNT(*)` que o Room reexecuta a cada escrita na tabela. Depender de `observeAllTransactions()` funcionaria, mas faria a tela de categoria carregar a lista inteira de transações só para saber que precisa recalcular. Os dois ViewModels passam a observá-lo, com o motivo escrito no ponto de uso. Teste de reatividade que fica vermelho sem o sinal.

  ⚠️ **Padrão a vigiar:** todo agregado do razão é `suspend`. Qualquer tela nova que os leia dentro de um `combine` precisa de uma fonte reativa junto, ou nasce congelada. Isto não estava dito em lugar nenhum e produziu dois bugs.

- [x] 8.10 **Bug de runtime achado pelo usuário: registrar transação no cartão falhava.** Sintomas: o modal não fechava nem mostrava erro, a transação não aparecia na lista, mas a fatura crescia.

  **Causa, e é regressão desta change.** `IAccountRepository.getAllAccounts()`/`observeAllAccounts()` são a **fachada de contas** — `AccountDao` filtra `WHERE type = 'ASSET'`, porque categoria, cartão e reconciliação não devem vazar para a lista de contas do usuário. Mas o `TransactionRepository` usava essa fachada para hidratar as entries, e `toDomainEntries` descarta silenciosamente toda entry cuja conta não está no mapa. Uma compra no cartão não tem perna `ASSET` nenhuma: tem `LIABILITY` (o cartão) e `EXPENSE` (a categoria). As duas eram descartadas → `entries` vazia → `TransactionMapper.toDomain` devolve `null` → `createTransaction` estoura no `!!`. A fatura crescia porque `invoiceOwed` é `SUM` em SQL sobre `entries` e nunca toca esse mapa.

  Antes da §6 isso era latente: o mapper só devolvia `null` com as **pernas legadas** vazias, e elas eram hidratadas por outro caminho. Ao trocar a fonte para `entries`, converti uma lacuna latente em falha dura.

  **Correção:** `getAllLedgerAccounts()`/`observeAllLedgerAccounts()` — o plano de contas inteiro — passam a ser a fonte da hidratação, com a distinção documentada nos dois lados. A fachada continua sendo a fachada.

  ⚠️ **Por que a suíte não pegou:** o `FakeAccountRepository` devolvia **todas** as contas em `getAllAccounts()`, onde a produção devolve só `ASSET`. O teste de hidratação existia, exercitava uma perna `EXPENSE`, e passava porque o dublê era mais permissivo que o real. O dublê agora espelha a fachada, e com isso o teste que já existia passou a pegar o bug sozinho — além do teste novo de compra no cartão. **Lição registrada: um dublê mais permissivo que a produção não é um dublê, é um ponto cego.**

- [x] 8.9 **Comparação contra a implementação rival, e o que ela revelou.** Dois avaliadores independentes compararam este branch com `refactor/improve-double-entry`, uma implementação separada da mesma change. Ambos escolheram este, pelo mesmo motivo decisivo, por métodos diferentes — e ambos acharam um defeito que os **dois** branches tinham.

  **O que decidiu:** a `MIGRATION_7_9` do rival não completa em banco nenhum. Ela renomeia `transactions` → `legacy_transactions`, e o SQLite leva os índices da v7 junto, com os nomes originais; o `CREATE INDEX index_transactions_categoryId` seguinte colide, e a tabela antiga só é dropada doze statements depois. Reproduzido aqui com `sqlite3` puro sobre um v7 construído do `7.json`, com **zero linhas**: `index index_transactions_categoryId already exists`. É independente de dado — toda atualização 7→9 falharia. A suíte do rival passa porque o fixture v7 dele não cria índice nenhum: um fixture que não é o schema antigo não prova nada sobre a migração real, que é exatamente por que a 6.8 aqui monta a v7 a partir do `7.json` e por que o `MigrationSchemaEquivalenceTest` abre o banco pelo Room.

  **Defeito que os dois tinham, corrigido aqui:** `updateTransaction` só checava a fatura de **destino**. Como `rewriteEntries` apaga as entries antigas de qualquer forma, editar uma compra de uma fatura **paga** para outra removia dinheiro da história liquidada sem passar por guarda — "um número que muda em silêncio", o defeito que o design nomeia. Agora as duas pontas da edição passam pela guarda, e editar nunca conta como o pagamento que liquida (5.6). Teste que fica vermelho sem a correção.

  **Também adotado da avaliação:** `deleteTransactionsByCreditCard` foi removido — `DELETE` cru que cascateava entries por fora de toda guarda, sem caller de produção.

  **Não adotado, e por quê:** mover a guarda de fatura para dentro do `LedgerEntryWriter`, como o rival faz. O writer resolve contas e escreve entries; a guarda precisa consultar `IInvoiceRepository`, que o writer não conhece. Ficaria mais perto da fronteira ao custo de dar ao writer uma dependência de repositório — troca que não vale. Fica registrado que as duas invariantes de fronteira moram em camadas diferentes.

- [x] 8.8 **Ajustes não previstos pela change, e débito conhecido.** Auditoria adversarial de fidelidade e de honestidade apontou que estes existiam sem registro. Ficam registrados aqui porque um revisor os encontraria e não saberia se foram deliberados.

  **Ajustes fora do previsto:**
  - `gradle.properties`: `kotlin.daemon.jvmargs` 3G → 6G, porque o compilador Kotlin/Native estourava heap ao linkar os testes iOS.
  - ~26 chaves de string renomeadas junto com a §7 (`view_operation_*`, `operation_card_*`, `*initial_balance`). Nenhuma task cobria chaves de string.
  - `kotlinx.coroutinesTest` adicionado a 6 módulos e `turbine` a 2, para os testes novos.
  - **Nova aresta de produção**: `feature/categories/impl` → `feature/accounts/api`, para consumir o `CloseAccountUseCase`. Legal pela regra de dependência (impl → qualquer api), mas é mudança de topologia sem task.
  - `CloseInvoiceUseCase` deixou de abrir a fatura seguinte ao fechar uma **retroativa** — evita duas OPEN no mesmo cartão, invariante que `InvoiceDao:25` (`LIMIT 1`) assume. É correção real, mas não está na 9j.1 nem em task nenhuma; só no comentário do código.
  - `LedgerError.toUiText()` e as exceções do razão **não tinham consumidor de produção**: a guarda de fatura lançava e o erro morria em crashlytics, com o modal recusando fechar sem dizer por quê. Introduzi esse modo de falha silenciosa ao adicionar a guarda (9k.1) — a mesma doença que a 6b.2 existe para corrigir. Agora `AddTransactionViewModel`/`EditTransactionViewModel` têm canal de eventos e snackbar, no padrão do `AddInstallmentViewModel`. As três strings `ledger_error_*` também só existiam em `values/`, não em `values-en/`: paridade restaurada.

  **Cobertura que não foi transferida por inteiro:**
  - `AccountUiCharacterizationTest` foi apagado em `7e983491` e o registro diz que ele "cede a prova numérica ao `AccountPeriodTotalsQueryTest`". São 4 das 6 asserções: as duas de **saldo** (`openingBalance`, `balance`) não têm contrapartida ali, e `EntryDao.balanceUpToMonth` não tinha teste em nível nenhum. Corrigido com `BalanceUpToMonthQueryTest`, que cobre o corte de mês, o zero antes de qualquer movimento, a conta sem entries e o total de ASSET.
  - Task 5.7 diz que `InvoiceTransactionsUiState:39` "sai". Ela continua lá, agora consumindo `status.isEditable` em vez de reenumerar. O ponto (nenhuma reimplementação) está cumprido; a letra da task não.

- [x] 8.17 **Sincronizar as specs com o que a §8.13–8.16 mudou (pergunta do usuário).** Quatro afirmações da `account-lifecycle` tinham deixado de ser verdade, três delas por mudanças que eu mesmo fiz e não propaguei — o mesmo padrão, agora no artefato normativo em vez do código.

  - *"O usuário MUST NOT precisar distinguir apagar de encerrar: a ação continua sendo uma só"* — **revogado pelo usuário na 8.13**. Apagar e encerrar são ações distintas, com use cases distintos, cada uma recusando o caso da outra. A spec passa a exigir isso, e a exigir que a interface ofereça a ação certa pelo nome sem ser a salvaguarda.
  - *"Uma tentativa de remover conta com lançamentos SHALL ser convertida em encerramento"* — **falso desde a 8.13**: é recusada, não convertida.
  - *"Encerrar conta cujo saldo não seja zero SHALL ser recusado"*, sem qualificação — **falso desde a 8.15**: vale só para conta **monetária**. Categoria é conta de fluxo, cujo saldo nunca volta a zero; exigir zero ali tornava impossível encerrar categoria alguma, que foi exatamente o bug reportado.

  ⚠️ **Correção posterior (usuário):** eu também fizera o domínio **recusar** encerrar conta sem lançamentos. O usuário apontou que isso deveria ser permitido no domínio, ainda que a interface não ofereça — e está certo: eu confundi "não é a ação apropriada" com "é inválido". A premissa dele era que o use case impede o **uso incorreto**, e o exemplo era apagar conta com transações, que quebra integridade referencial. Encerrar conta vazia não quebra nada; recusá-la era o domínio impondo preferência de apresentação, e usando um guarda para compensar a falta do "reabrir". Guarda e erro `NO_TRANSACTIONS` removidos; a spec passa a dizer que o domínio recusa só o que violaria invariante. Some também a falha numa corrida inofensiva — o último lançamento removido entre a tela abrir e o usuário confirmar.
  - O requisito de integridade trazia um diagnóstico datado ("hoje ela alcança, porque...") de um defeito já corrigido, e não dizia nada sobre **ordem** de remoção — a lacuna por onde entrou o bug da 8.16. Agora exige remoção atômica da fachada com a sua conta, na ordem que a referência impõe.

  Acrescentados os cenários que faltavam: apagar com lançamentos recusado, encerrar sem lançamentos recusado, encerrar categoria usada independe do saldo, fachada e conta removidas juntas na ordem certa, fachada encerrada continua nomeada no histórico, e a interface oferece a ação que vai acontecer.

  Na `balanced-ledger`: o cenário do lançamento de baixa continua válido, mas a origem mudou — a baixa só existe no dado **migrado**, não em runtime; e a lista de regras deriváveis ganhou "qual ação de retirada uma tela oferece".

- [x] 8.16 **Excluir categoria sem transação nenhuma continuava falhando — e o erro genérico era o sintoma.** O usuário voltou dizendo que não conseguia excluir categoria vazia, com a mensagem genérica "não foi possível concluir a ação".

  **A mensagem ser genérica era a pista:** `toUiMessage` só reconhece `AccountException`; qualquer outra coisa cai no `else`. O que estava sendo lançado era `SQLiteException: FOREIGN KEY constraint failed`. `DeleteCategoryUseCase` chamava `DeleteAccountUseCase`, que apagava a **conta** enquanto a linha da **categoria** ainda a referenciava (`categories.accountId`, `NO_ACTION`). A ordem estava invertida.

  ⚠️ **A auditoria de fidelidade tinha apontado exatamente isto** ("o caminho DELETED apaga a conta antes da fachada, com FK `NO_ACTION`, e não abrange transação"), classificado como *betrayal latente*. Eu reconheci no relatório e **não corrigi**. Sexta reincidência do padrão — e a primeira em que o defeito já estava escrito, com endereço, num documento que eu mesmo produzi.

  **Correção:** a remoção do par passa a ser do repositório da fachada, simétrica ao `insert` que já criava os dois numa transação — fachada primeiro, conta depois, tudo num `immediateTransaction`. Os use cases voltam a só guardar a precondição. Teste que fica vermelho se a ordem for invertida (com FK real, em Room in-memory).

  **A modal de erro foi refeita**, com as três críticas do usuário procedendo: título que repetia a mensagem (removido — a razão *é* o conteúdo), ícone desalinhado (a coluna agora centraliza, com o ícone num container circular do `errorContainer`), e mensagens genéricas que não diziam nada (reescritas para dizer o que fazer). A modal que recusou continua aberta atrás, de propósito.

- [x] 8.15 **Bug de runtime achado pelo usuário: excluir e encerrar categoria não funcionavam.** Nem uma nem outra, e o modal não fechava nem mostrava erro. Dois defeitos meus, um de desenho e um de propagação.

  **Desenho.** Apliquei a regra de saldo zero da 8.14 a **todo** tipo de conta. Uma categoria é conta `INCOME`/`EXPENSE` — conta de **fluxo**, cujo saldo é o acumulado de gastos e nunca é zero depois de usada. Então encerrar categoria usada falhava sempre com `HAS_BALANCE`, e excluir falhava com `HAS_TRANSACTIONS`: nenhum dos dois caminhos existia. A regra vale só para conta **monetária** (`AccountType.isMonetary`), onde saldo ≠ 0 significa dinheiro parado em algum lugar; num fluxo não há o que resolver. Corrigido e coberto por teste.

  **Propagação.** O erro morria em `crashlytics.recordException` e a folha simplesmente não fechava — o mesmo defeito que a 8.8 registra ter corrigido **para os modais de transação**, e que eu não levei para os outros. Quinta reincidência do padrão "corrigi a instância, não varri a classe".

  Desta vez a correção é estrutural em vez de por modal: `ModalManager.showError(uiText)` abre uma **modal de erro** (`ErrorModal`) sobre a que falhou. Qualquer modal chama um método e o usuário vê — sem canal de eventos por ViewModel, e sem que um modal esquecido volte a ficar mudo. A modal que recusou fica aberta atrás de propósito: os motivos são acionáveis (um saldo a resolver, uma categoria em uso), então fechar o erro devolve o usuário à ação.

  **Escolha de forma (usuário):** modal de erro, não snackbar. Uma 1ª versão usava snackbar. Ao trocar, os **oito** modais do app convergiram para o mesmo mecanismo — inclusive `AddInstallmentModal` e `TransferBetweenAccountsModal`, que já traziam da main um canal de eventos e um snackbar próprios. Os quatro `*Event.kt` que existiam só para carregar `ShowError` foram removidos. ⚠️ Esses dois últimos são **anteriores a esta change**: converti para o app não ficar com dois padrões de erro, mas é mudança fora do escopo original e pode ser revertida sem prejuízo ao resto.

- [x] 8.14 **Encerrar deixa de gerar baixa automática; passa a exigir saldo zero (decisão do usuário, revoga a D13 nesse ponto).** A D13 e a spec `account-lifecycle` prescreviam que encerrar com saldo ≠ 0 gerasse um lançamento de baixa contra reconciliação. O usuário recusou: é "mágica" que pode contrariar a expectativa dele.

  **Ele está certo, e a justificativa da spec não se sustentava.** Ela dizia "o saldo MUST NOT desaparecer sem lançamento" — argumento herdado do problema de **apagar**, onde o dinheiro sumia do patrimônio sem registro. Ao **encerrar**, as entries ficam: nada some. A baixa não registrava uma saída, ela **inventava** uma — e pior, substituía a única informação que só o usuário tem (para onde o dinheiro foi) por uma reconciliação genérica, num lançamento que aparece no histórico dele como se ele o tivesse feito.

  O problema real que a baixa resolvia era o oposto: conta encerrada **com** saldo deixaria dinheiro no patrimônio sem conta visível — um número que não fecha. Exigir saldo zero fecha os dois casos sem inventar nada: o usuário resolve antes, transferindo, gastando ou ajustando, e cada um desses caminhos registra a intenção real.

  - `CloseAccountUseCase` recusa saldo ≠ 0 (`AccountError.HAS_BALANCE`) e **não escreve nada**; a criação da baixa saiu, junto com a dependência de `ITransactionRepository`.
  - A UI impede antes, dizendo quanto falta resolver e o que fazer — sem ser a salvaguarda.
  - Teste que prova a recusa **e** que nenhum lançamento é criado.
  - A spec `account-lifecycle` foi reescrita: o requisito "Encerramento com saldo gera lançamento de baixa" virou "Encerramento exige saldo zero", com o raciocínio acima registrado.

  ⚠️ **A baixa automática permanece na `MIGRATION_7_9`, e é legítima ali por não haver alternativa:** ela reconstrói contas **já apagadas** no v7, cujo dinheiro já havia deixado os livros, sem usuário a quem perguntar. Ali a baixa registra um fato passado; em runtime ela inventaria um. A distinção está escrita na spec.

- [x] 8.13 **Excluir e encerrar como ações separadas, do use case à tela.** Escopo acrescentado pelo usuário, sob três premissas dele: são ações diferentes e pedem use cases diferentes; **todo use case impede o próprio uso incorreto**; e a UI também impede — não como salvaguarda, mas para não induzir expectativa errada.

  **Domínio.** `DeleteAccountUseCase` e `CloseAccountUseCase` são pares disjuntos, cada um recusando o caso do outro com erro tipado:
  - excluir conta com movimentação → `AccountError.HAS_TRANSACTIONS`. Não é dica para a UI: `entries.accountId` é `NO ACTION`, então remover a linha falharia na FK ou deixaria o histórico órfão.
  - encerrar conta sem movimentação → `AccountError.NO_TRANSACTIONS`. Encerrar existe *porque* excluir é impossível; encerrar o que nunca se moveu só esconderia a conta sem preservar nada — e, sem reabrir (8.12), fora de alcance.
  - `DeleteCreditCardUseCase`/`CloseCreditCardUseCase` e `DeleteCategoryUseCase`/`CloseCategoryUseCase` compõem os de conta, cada fachada guardando o seu. O par de conta é o dono único da regra.

  **Apresentação.** `RetireAction` + `retireActionOf(hasMovement)` em `core/ui` decide **qual palavra** a tela oferece, com um dono só para conta e cartão e teste próprio. O **fato** vem do razão (`IEntryRepository.hasEntries`); o **desfecho** é do use case. Telas oferecem "Excluir" **ou** "Encerrar" e abrem modais dedicadas — uma promessa por modal.

  **Também nesta task:** atalhos somem para conta/cartão encerrados nos dois modais de detalhe (que divergiam entre si), e cartão encerrado passa a exibir o nome em vez de "Excluído" — isto último era um bug: o caminho de leitura resolvia categorias e cartões pelas **fachadas**, filtradas por `isClosed` desde a 6b.5, então um encerrado virava `null`. **Mesma classe do bug da 8.10**, em dois pontos que eu não varri na ocasião. Corrigido com `*IncludingClosed()`.

  ⚠️ **Duas versões anteriores desta task foram rejeitadas, e o registro fica.**
  - A 1ª expôs `CloseAccountUseCase.outcomeFor(account)` — API de *previsão* pendurada num comando — e uma modal única que trocava de texto. Além do uso errado de use case, `outcomeFor` e `invoke` passaram a **decidir a mesma coisa em dois lugares dentro da classe dona da regra**: a duplicação exata que esta change existe para eliminar, cometida no dono. E a task afirmava que a UI "pergunta à regra em vez de redecidir" — falso, ela consumia uma segunda cópia.
  - A 2ª separou a apresentação corretamente, mas manteve `DeleteAccountUseCase` **encerrando em silêncio** quando não podia excluir: um use case fazendo coisa diferente do próprio nome, com a UI como única barreira. É o que a 3ª premissa do usuário corrige.

  ⚠️ **Ainda registrado:** o teste de regressão da 8.10 (`a card purchase hydrates…`) nunca entrou no commit — script com `replace` sem asserção, falha silenciosa, e eu o relatei como existente. Recolocado, mais um para conta encerrada.

  ⚠️ **Uma 3ª correção, também apontada pelo usuário: ícone de encerrar diferente entre conta e cartão.** A causa não era o ícone: cada tela rederivava a apresentação da mesma ação, com o seu próprio `when` e o seu próprio par de strings. Divergir era questão de tempo. `RetireAction` passou a carregar o **rótulo e o ícone**, e as telas só o consomem — as strings por feature (`accounts_delete`, `credit_cards_delete`, `view_category_delete`, `invoice_transactions_delete_card`) saíram.

  Ao varrer, apareceram **dois pontos que eu não tinha atualizado e que o guarda estrito quebrou**: `InvoiceTransactionsScreen:196` e `ViewCategoryModal:172` abriam a modal de excluir incondicionalmente, então excluir cartão ou categoria **com** movimentação passava a falhar. A categoria ganhou o par que faltava (`CloseCategoryUseCase`/`CloseCategoryModal`), fechando as três fachadas no mesmo desenho. **Quarta reincidência do mesmo padrão nesta change: corrigir a instância e não varrer a classe.**

  **Continua aberto:** não há como reabrir uma conta encerrada, nem lista de encerradas (8.12).

- [x] 8.18 **`isPermanent` no lugar de `isMonetary` para o guarda de saldo, e "encerrar" vira "arquivar" (decisões do usuário).** Surgiram de uma pergunta dele: como apps de partidas dobradas resolvem categorias nunca zerarem o saldo.

  **Não resolvem — a distinção já existe na contabilidade, e eu a tinha reinventado mal.** Contas **permanentes** (reais: `ASSET`, `LIABILITY`, `EQUITY`) têm saldo que representa o que existe agora e atravessa períodos. Contas **temporárias** (nominais: `INCOME`, `EXPENSE`) têm saldo que é total de período, zerado só por lançamento de encerramento de exercício contra o patrimônio — que este app não realiza, como praticamente nenhum app pessoal. O guarda usava `isMonetary` (`ASSET`/`LIABILITY`), que acerta o caso alcançável mas erra o conjunto: `EQUITY` é permanente e ficava de fora. `AccountType.isPermanent` passa a ser o predicado do guarda, com a razão contábil escrita na spec — antes ela dizia "conta de fluxo" sem nomear o motivo, e leria como remendo para destravar categoria, que foi exatamente como surgiu. `isMonetary` permanece onde o sentido é mesmo "perna que carrega dinheiro" (`monetaryEntries`, `primaryEntry`, gate de editabilidade).

  **"Encerrar" → "arquivar"** em domínio, UI, strings, schema e specs. `CloseAccountUseCase` → `ArchiveAccountUseCase` (e os pares de cartão e categoria), `RetireAction.CLOSE` → `ARCHIVE`, `accounts.isClosed` → `isArchived` — seguro renomear a coluna porque a v9 não foi para produção. ⚠️ `Invoice.Status.isClosed`, `isClosedToNewExpenses` e `CloseInvoiceUseCase` são **outro conceito** e ficam: fatura fechada não é fatura arquivada. O rename por regex atingiu `Invoice.Status.isClosed` mesmo assim (o padrão `val isClosed: Boolean` casa nos dois) e foi revertido — é a terceira vez nesta change que um rename mecânico vaza para um homônimo, e a única defesa que funcionou foi compilar e ler o erro.

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

## 10. Auditoria de excluir/arquivar — conta, cartão, categoria

> Varredura dos casos de borda de **excluir** e **arquivar** nas três entidades, feita depois
> da §9. Confirmado primeiro o que **não** quebrou: `entries.accountId` é `NO_ACTION`
> (`EntryEntity:27`), os três delete use cases barram por `hasEntries`, e as fachadas saem
> junto com o `Account` na mesma transação. **O razão não desbalanceia por exclusão.** Os
> achados são de guarda incompleta, erro engolido e assimetria entre as três — não de `Σ≠0`.

### 10a. Assimetrias entre as três entidades

- [x] 10a.1 **Cartão engolia o erro e fingia sucesso.** `DeleteCreditCardViewModel:27-35` e `ArchiveCreditCardViewModel:38-46` tinham o `dismissAll()` **fora** da cadeia do `Either`: a sheet fechava em erro e em sucesso, e o `toUiMessage()` (`:42-45`) era **código morto** — nenhum `showError`. Conta (`DeleteAccountViewModel:28-34`) e categoria (`DeleteCategoryViewModel:26-33`) já faziam o oposto. Falha silenciosa em app de finanças é o pior modo de falha; alinhado aos outros dois.
- [x] 10a.2 **Categoria arquivada aceitava escrita.** `LedgerEntryWriter.orRejectIfClosed()` cobria só `realAccountId` (`:109-115`); `contraAccountId` → `categoryAccountId` (`:136`) escapava. O KDoc de `:98-102` **já declarava** a invariante como "checada onde toda perna de toda escrita passa" — metade das pernas não passava. Editar o valor de uma transação antiga gravava entries novos numa categoria arquivada. Teste em `LedgerEntryWriterTest`. ⚠️ **Efeito colateral aceito:** editar transação de categoria arquivada agora falha. É a invariante valendo; se incomodar, a saída é a tela oferecer trocar a categoria — **não** relaxar a invariante.
- [x] 10a.3 **Nome único ignorava arquivados, nas três.** Os três `Validate*NameUseCase` varriam listas que o DAO já filtra por `isArchived = 0` (`CategoryDao:9-11`, `AccountDao:20-21`, `CreditCardDao:11-12`), então dava para criar homônimo de um arquivado. Arquivar preserva o nome e o histórico continua renderizando — dois "Mercado" lado a lado, um deles cinza, não é um nome. Trocados para as versões *including closed*; contas ganharam `getAllAccountsIncludingClosed()`.
- [x] 10a.4 **Atalho de navegação para conta/cartão arquivado na recorrência.** `ViewRecurringModal:200-231` navegava para `AccountsRoute`/`CreditCardsRoute` de item arquivado — telas que não o listam. `ViewTransactionModal:225-285` **já tinha resolvido** o mesmo caso (`onClick = if (isArchived) null else {...}`); a recorrência não seguiu. Mesma forma aplicada, mesmo comentário.

### 10b. Recorrência órfã — o desenho, e o que a auditoria errou

> ⚠️ **A 1ª auditoria errou o mecanismo.** Alegou que a recorrência "sumia da lista" por
> `INNER JOIN` e que confirmar "falhava silenciosamente". Não há JOIN: `RecurringDao` é
> `SELECT * FROM recurring` puro e o repositório usa `map`, não `mapNotNull`. **O usuário
> derrubou o achado testando.** O que existia era pior e tinha outra causa — abaixo.
>
> **Princípio:** a recorrência é um **template de intenção**. Se ela é confirmável é
> **derivável** do estado das contas que referencia — não precisa de flag persistida, como
> `deriveTransactionLabel`. E nunca substituir em silêncio.
>
> **Assimetria com razão de domínio:** categoria nula é **legítima** (`UNCATEGORIZED_EXPENSE`
> é conta de sistema — "sem categoria" é estado válido do razão). Conta ou cartão nulo **não
> é**: não existe "alguma conta" com significado, e conta-vs-cartão é a natureza do
> lançamento, não um detalhe.

- [x] 10b.1 **Hidratação usava as listas só-ativas.** `RecurringRepository:26-42` montava os mapas com `observeAllCategories()`/`observeAllAccounts()`/`observeAllCreditCards()`. Consequência: **arquivar** — não excluir — já fazia o vínculo sumir da tela **com o FK intacto**. Era isto que o usuário via. Trocado para as três lookups *including closed*; precisou de `observeAllAccountsIncludingClosed()` novo.
- [x] 10b.2 **Dois fallbacks silenciosos no confirmar.** `ConfirmRecurringViewModel:80` fazia `account ?: conta padrão ?: primeira` — a recorrência era postada **em outra conta**, sem aviso. E `:43-47` calculava `initialTarget = ACCOUNT` quando `creditCard == null`, então **recorrência de fatura virava lançamento em conta** — a troca de semântica mais grave das três. Ambos mortos; a pré-seleção passa a filtrar arquivados (`initialAccount`/`initialCreditCard`). Como o modal já desabilitava Confirmar com seleção nula, o efeito sai de graça: fonte inválida → botão desabilitado até o usuário escolher.
- [x] 10b.3 **Regra derivada, dono único.** `Recurring.hasUsableSource` (`core/model`), não persistida. Consumida pela tela: badge "Precisa de atenção" e nome da fonte em `outline` quando arquivada, seguindo `Category.displayColor`. Os dois `onLeft` mudos de confirmar/pular ganharam `showError`.
- [x] 10b.4 **Guarda na exclusão, para o órfão não nascer.** As três FKs de `RecurringEntity:14-33` são `SET_NULL`: excluir **stripa o vínculo em vez de falhar**. Como recorrência nunca confirmada não gera entry, `hasEntries` é `false` e a exclusão passava. `AccountError.HAS_RECURRING` + `RecurringDao.countByAccount/countByCreditCard`; `DeleteAccountUseCaseImpl` e `DeleteCreditCardUseCase` recusam no mesmo lugar do `hasEntries`. Teste em `RetireAccountGuardsTest`.
  - **Julgamento (decisão do usuário):** recorrência quebrada **continua ativa** com estado visível, em vez de auto-pausar. Auto-pausar é mais limpo, mas é outra mudança de estado sem o usuário pedir — o mesmo pecado, de outra cor.
  - **Aceito:** a guarda conta recorrências **inativas** também (ainda referenciam a conta e podem ser reativadas), então uma recorrência esquecida bloqueia a exclusão.
  - **Aceito:** a string é **neutra** ("Há recorrências que dependem disto...") porque a mesma `AccountError` serve conta e cartão — `DeleteCreditCardUseCase` já usava `AccountError.HAS_TRANSACTIONS` antes. Texto por entidade pediria um `CreditCardError` próprio.
