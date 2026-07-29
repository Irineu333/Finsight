> **A ordem tem uma alavanca só: a segunda moeda nasce exclusivamente no seletor do formulário
> de conta/cartão.** Por isso ele é a **última** tarefa do plano. Antes dele, todo mapa por moeda
> tem uma chave, toda figura é exata e nenhuma marca `≈` aparece — a janela de "grava moeda mas
> soma moedas diferentes" não é estreitada, é **inalcançável**.
>
> E isso não é sustentado por disciplina: 1.6 escreve um **teste de inércia** afirmando que
> nenhum sítio de produção constrói conta com moeda diferente do padrão. Ele vale de 1.6 até
> 9.7, e 9.8 o **inverte** em vez de removê-lo, passando a valer para sempre como "exatamente
> dois sítios escolhem moeda".
>
> **Regra de higiene, e ela é o que corrige o erro mais fácil de cometer aqui:** nenhum grupo
> muda assinatura sem adaptar, no mesmo grupo, quem a consome. O build fica verde ao fim de
> **todo** grupo, e 3.9 + 3.10 são o gate que prova, a cada um, que o usuário monomoeda vê números
> idênticos aos de hoje **e** que uma conta de moeda diferente da base é exibida na moeda dela.
> Os dois são necessários: 3.9 sozinho é cego a confundir a moeda da conta com a base, porque para
> um usuário monomoeda os dois textos coincidem.
>
> §1 é varredura preparatória e inerte, e produz o **inventário** que os grupos 6–8 consomem.
> §4 e §5 não dependem de §2 nem de §3 e podem correr em paralelo.

## 1. Varreduras preparatórias (comportamento idêntico, nada de semântica)

- [ ] 1.1 `DisplayAmount` passa a carregar `currency` **obrigatório** e a exatidão, sem default em nenhum dos 7 construtores nomeados — a spec proíbe expor um sem os outros. Atualizar os sítios de construção; onde o sítio já tem a `Account`, passar `account.currency`; onde agrega entre contas, passar **explicitamente** a moeda única que o app tem neste ponto do plano — que em 6.6 passa a ser a moeda base. Reescrever o KDoc que hoje declara que o tipo não conhece moeda, com a distinção da spec: carregar a denominação não é calcular.
- [ ] 1.2 Registrar, como saída de 1.1, a **lista dos sítios que tiveram de passar a moeda explicitamente** por agregarem entre contas. Essa lista é o backlog verificável dos grupos 6–8, e 8.6 a fecha vazia. Sem ela a varredura de superfície não tem critério de pronto.
- [ ] 1.3 `CurrencyFormatter.format`/`formatWithSign` passam a receber a moeda nos três `actual` (jvm, android, ios); o locale governa apenas separador e posição do símbolo. Decidir aqui — e registrar no KDoc — se a moeda entra pelo método ou muda o binding `single { CurrencyFormatter() }` em `CommonModule`. **A decisão precede a varredura de 8.x**; tomá-la no meio dela é retrabalho contratado. Verificável: não sobrevive sobrecarga que formate sem moeda.
- [ ] 1.4 `CurrencyFormatter.format(DisplayAmount)`: o `≈` como prefixo **mais externo** que o sinal (`≈ +R$ 1.240,00`), pela mesma porta que já concatena `+`/`-`. Este ponto único conserta a maioria dos sítios de formatação sem tocá-los. Inerte enquanto nada for aproximado. Cobrir em `DisplayAmountTest`.
- [ ] 1.5 `LedgerFixture` (`core/ledger/jvmTest`) ganha parâmetro de moeda em conta e em entry, com default `"BRL"` para que as 6 suítes construídas sobre ela compilem sem alterar asserção. É `internal` ao `core/ledger/jvmTest`, então serve às suítes de query (3.8) e **não** ao `LedgerEntryWriterTest`, que mora em `feature/transactions/impl/commonTest` e precisa dos seus próprios doubles (2.8).
- [ ] 1.6 **Teste de inércia** em `app/shared/jvmTest`, ao lado de `AppModulesTest`: nenhum sítio de produção constrói conta com moeda diferente da única que o app usa. É o que torna a janela de silêncio inalcançável durante os grupos 2–8, e 9.8 o inverte.
- [ ] 1.7 Reordenar `PayInvoiceModal` e `AdvancePaymentModal` para *quem participa → quanto → quando* (D24). Sem campo novo e sem mudança de comportamento. Isolada aqui de propósito: é a mudança de UX de maior alcance da change, atinge quem nunca verá duas moedas, e precisa ser revisável e reversível sozinha.

## 2. O tipo de conta e a fronteira de escrita

> §1 e §2 podem ser um único PR. O que **não** pode é 2.1 sem 2.2: acrescentar um membro a
> `AccountType` sem fechar `systemAccountId` deixa um `when`-expressão não exaustivo, e
> `:core:ledger` não compila.

- [ ] 2.1 `AccountType` ganha `CONVERSION` — `isDebitNatured = false`, `isMonetary = false`, `isNominal = false`, `isPermanent = true` **vacuamente** (a propriedade decide se arquivar encalha saldo, e conversão nunca é arquivada). `AccountEntity.Type` idem, com KDoc registrando o fato verificado de que **não há migração**: Room persiste o enum nativamente como `TEXT`, sem `TypeConverter`. `SystemAccount` ganha a chave de conversão. `displaySign` não muda — deriva de `isDebitNatured`.
- [ ] 2.2 Fechar os **três** `when` exaustivos sobre `AccountType` do repositório: `AccountTypeMapper:13` e `:21`, e `LedgerEntryWriter.systemAccountId:158`. Não há uso de `AccountType.entries`/`values()`.
- [ ] 2.3 Provar D2 em `LedgerTest`: `{ASSET, CONVERSION} → TRANSFER`, `{ASSET, LIABILITY, CONVERSION} → PAYMENT`, e `deriveTransactionType` de uma perna monetária de operação cruzada **não** lendo `ADJUSTMENT`. Nenhuma mudança de produção é esperada — se falhar, o *fall-through* está errado e é isso que se conserta.
- [ ] 2.4 Provar, também sem mudança de produção, que o gate de editabilidade (D19) recusa a operação cruzada por contagem de pernas monetárias, e que a idempotência de `AdjustBalanceUseCase`/`AdjustInvoiceUseCase` não casa com uma transação de pernas `CONVERSION`. São os dois comportamentos que a revisão apontou como quebráveis, e a prova de que o tipo próprio os salvou.
- [ ] 2.5 `AccountDao.getByTypeAndName` passa a `(type, name, currency)`; `ensureSystemAccount` cria por moeda; `systemAccountId` resolve por `(SystemAccount, currency)` — a natureza deixou de ser chave, porque `EQUITY` passa a ter duas contas de sistema por moeda. As contas de sistema existentes, todas `'BRL'`, viram as contas de sistema **de BRL**, sem migração.
- [ ] 2.6 `writeEntries` grava `currency` lida da `AccountEntity` que `orRejectIfClosed` já carrega, nas duas construções de `EntryEntity` e na contrapartida. `TransactionLeg` **não** ganha campo — é o que torna "poste 100 USD numa conta BRL" inexprimível. O default do modelo permanece por ora e sai em 6.6, junto do resolvedor que o substitui.
- [ ] 2.7 **Remover** `LedgerEntryWriter.validate(legs)` e as suas duas chamadas em `TransactionRepository:219` e `:240`, e os dois testes que a exercem. Consertá-la é impossível sem quebrar D5 — agrupar por moeda exigiria ler contas —, e a invariante volta a ter um ponto único de fato.
- [ ] 2.8 Completação cruzada em `writeEntries`: agrupar por moeda, e com duas ou mais lançar o oposto do resíduo de cada uma na conta `CONVERSION` daquela moeda — **última calculada, por diferença, e sem dimensão**. Sem a ausência de dimensão, `rejectIfDimensionLandsWrong` recusa todo pagamento de fatura cruzado. Monomoeda permanece byte a byte o comportamento de hoje. Guarda de D1 (resíduos não todos do mesmo sinal) como caso novo de `LedgerError`, com `toUiText()` e strings em `values/` **e** `values-en/`.
- [ ] 2.9 Testes em `LedgerEntryWriterTest`, com os seus próprios doubles cientes de moeda: transferência cruzada com 4 entries somando zero em cada moeda; pagamento de fatura cruzado com a conversão sem dimensão; resíduo de arredondamento absorvido pela perna de conversão; monomoeda sem conversão sintetizada; desbalanceamento monomoeda ainda recusado; resíduos de mesmo sinal recusados sem gravar nada.

## 3. As leituras por moeda, com os seus consumidores

> Grupo grande de propósito: mudar assinatura de `IEntryRepository` sem adaptar quem a consome
> deixaria o build vermelho por vários grupos. Ele fecha verde.

- [ ] 3.1 Tipo de saldo por moeda em `:core:ledger`, com `zero`, `of(currency, value)` e acesso ao valor de uma moeda — as conveniências existem para que 3.7 seja substituição mecânica e não 21 decisões de desenho.
- [ ] 3.2 Dar a esse tipo a **soma de dois resultados por moeda** (cada moeda com a sua, sem conversão), como implementação única e no razão. É o dono que `ledger-reporting` exige e que `dashboard-balance-widgets` consome; sem ele a operação nasce em linha dentro de `BalanceOverviewFactory:93`. Testes com moedas disjuntas e mapa vazio.
- [ ] 3.3 `EntryDao`: aplicar `GROUP BY e.currency` pelo **critério**, não por lista — *toda agregação que não filtre por uma única conta*. Permanecem escalares apenas `balanceOf`, `balanceUpToMonth` e `accountPeriodTotals`. **Toda leitura por dimensão entra**, `dimensionNaturalBalance` e `dimensionPeriodTotals` inclusive: o razão MUST NOT consultar `DimensionKind` para decidir forma de retorno.
- [ ] 3.4 Teste de regressão sobre os predicados por **literal SQL** da `EntryDao` e do `AccountDao` (`a.type = 'EQUITY'`, `IN ('ASSET','LIABILITY')`): nenhum passa a casar `CONVERSION`, e `netWorthCents` continua filtrando `ASSET`/`LIABILITY`. O compilador não os alcança, e o design os aponta como o risco real da abertura do enum.
- [ ] 3.5 `IEntryRepository` e `EntryRepository`: novas assinaturas pelo mesmo critério de 3.3. `AccountFlows` mantém a forma e ganha a moeda; `DimensionFlows`, `dimensionOwed` e as suas versões em lote passam a por moeda.
- [ ] 3.6 Decidir e registrar o destino de `netWorth()`: medido **sem consumidor de produção** — o saldo total do dashboard vem de `CalculateBalanceUseCase(accountId = null)`. Ou ganha consumidor, ou sai da interface. Verificável: não sobra assinatura sem chamador.
- [ ] 3.7 Varrer os ~21 arquivos de teste que implementam `IEntryRepository` como stub completo. Avaliar antes extrair uma base compartilhada — hoje cada um redeclara a interface inteira. Nenhuma expectativa muda: toda moeda é BRL.
- [ ] 3.8 Casos de duas moedas nas 6 suítes de query sobre `LedgerFixture` (habilitadas por 1.5): cada agregação devolve duas chaves e nenhuma soma entre elas. É a prova executável de "nenhuma agregação soma moedas".
- [ ] 3.9 **Teste de regressão monomoeda**, e ele é o gate de todos os grupos seguintes: com todas as contas na moeda base, toda figura do app é idêntica à de antes da mudança e nenhuma recebe marca de aproximação. Escrito aqui, rodado ao fim de cada grupo.
- [ ] 3.10 **Gate irmão de 3.9, e ele cobre o ponto cego dele:** teste com uma conta cuja moeda **difere da base**, provando que toda figura monomoeda carrega a moeda da sua conta ou fachada — saldo, extrato, devido de fatura, parcela — e **nunca** a base. Incluir o caso do usuário com **todas** as contas fora da base, **com taxa cadastrada**: nenhuma figura é convertida e nenhuma recebe marca, porque não havia mais de uma moeda a reconciliar (D9). Com moedas iguais a violação é invisível, porque os dois textos coincidem; 3.9 passaria com a base ligada por engano num saldo de conta. Como 1.1 instrui a passar a base nos sítios que agregam, este é o teste que separa os dois usos.
- [ ] 3.11 Adaptar os consumidores de contas, categorias e orçamentos: `CalculateBalanceUseCase` (o `accountId = null` é a porta pela qual o multimoeda entra no app), `AccountsViewModel`, `CalculateCategorySpendingUseCaseImpl` e `ViewCategoryViewModel` (incluindo o denominador de porcentagem), `CalculateBudgetProgressUseCase` (`:feature:budgets:api`), que passa a reduzir o gasto à **moeda do limite** e não à base.
- [ ] 3.12 Adaptar os consumidores de cartões: `CalculateInvoiceUseCase`, `CalculateInvoiceOverviewsUseCase`, `InvoiceTransactionsViewModel`. A redução a uma chave acontece **aqui**, com a garantia da fachada escrita onde o mapa é reduzido — não presumida no razão.
- [ ] 3.13 Adaptar `DashboardComponentsBuilder` e `BalanceOverviewFactory` para somar `ASSET + LIABILITY` e `asset.expense + liability.expense` pela soma de 3.2, nunca por soma de mapas em linha.
- [ ] 3.14 Adaptar `feature/report/impl`: `CalculateReportStatsUseCase` (escopo vazio = todas as contas, arquivadas inclusive — a figura mais cruzada do app), `CalculateReportCategorySpendingUseCase`, `ReportViewerViewModel`.
- [ ] 3.15 `TransactionsViewModel` e o escopo: o perímetro decide por pernas **monetárias**, e as de conversão, fora de qualquer perímetro, não tornam fluxo um lançamento interno. Cobrir os cenários novos de `transaction-scope`.

## 4. A tabela de taxas (independente de §2 e §3)

- [ ] 4.1 `ExchangeRateEntity(currency, date, rate, source)` e `ExchangeRateDao` em `:core:database`, com `source` distinguindo colhida-de-operação de informada-pelo-usuário; a consulta "última taxa em ou antes de `:date`" com precedência da do usuário na mesma data; a listagem observável.
- [ ] 4.2 `AppDatabase` `version = 10` → `11` com `MIGRATION_10_11` registrada em `Database.kt`: `CREATE TABLE` da tabela de taxas **e** `ALTER TABLE budgets ADD COLUMN` da moeda do limite (D13), com `DEFAULT` na constante de último recurso. O preenchimento é **exato, não aproximado**: todo banco existente está inteiramente em BRL, então a moeda que a coluna recebe é exatamente a que já denominava cada limite gravado. Nenhum valor é alterado. Bindar o DAO em `databaseModule`.
- [ ] 4.3 Exportar `schemas/…/11.json`; escrever `Migration10To11Test` no molde dos existentes e estender `MigrationSchemaEquivalenceTest`, hoje um `@Test` só cobrindo `7 → 10`.
- [ ] 4.4 Confirmar por teste que `LedgerBalanceCheck` **não muda** — já agrupa por `(transactionId, currency)` — com um banco contendo transação cruzada.

## 5. Consolidação e catálogo (depende de §4; independente de §3)

- [ ] 5.1 Repositório de taxas com a política "a última em ou antes da data" e a precedência da taxa do usuário. Testes: taxa anterior escolhida em vez da posterior, ausência de taxa, precedência.
- [ ] 5.2 Catálogo curado de moedas de duas casas decimais em `:core:model`, com a premissa de D14 em KDoc. O razão persiste só o código.
- [ ] 5.3 A figura **multitermo** como sequência de `DisplayAmount`, com o caso de um termo como o comum, sem expor operação entre dois valores.
- [ ] 5.4 A **única** redução de saldo-por-moeda a figura na base: **uma moeda passa direto, na sua própria moeda, exata** — a redução só age com duas ou mais; a partir de duas, converte o que a taxa da data permitir, deixa termo próprio por moeda sem taxa, e **deriva** a exatidão. Nada vira `1`, nada é omitido, nada zera a tela.
- [ ] 5.5 Testes da redução contra as cinco linhas da tabela de D9 — incluindo **moeda única diferente da base com taxa cadastrada, que não converte** —, mais o de que não existe forma de obter a figura sem a exatidão, mais o de fronteira: a camada não expõe soma de dois saldos por moeda (isso é do razão, 3.2) e o razão não recebeu dependência que forneça taxa.

## 6. Moeda base e a feature de configurações

- [ ] 6.1 Criar `:feature:settings:api` e `:impl` sob os plugins de convenção, com `include` em `settings.gradle.kts`, **`export` no framework iOS de `app/ios`** e a dependência em `:app:shared`. Sem o export o alvo iOS não compila.
- [ ] 6.2 Rota `@Serializable`, `SettingsGraph`, `NavGraphBuilder.settingsGraph()`, módulo Koin e entry point; agregar em `AppNavHost` e `appModules`; entrada em `AppNavCatalog` (`feature/shell/impl`) imediatamente **antes** de `Support`.
- [ ] 6.3 Moeda base como preferência **observável**, no molde de `DashboardPreferencesRepository` — toda figura consolidada reage à sua mudança.
- [ ] 6.4 Resolvedor de moeda pelo **locale do dispositivo** (`expect`/`actual` nos três alvos), reusando o mecanismo que o `CurrencyFormatter` já emprega — `NumberFormat.getCurrencyInstance()` no JVM/Android e `NSLocale.currentLocale` no iOS. Se a moeda do locale não estiver no catálogo de 5.2, recair na constante declarada como **último recurso**. Testes por plataforma, incluindo o caso de locale de moeda não oferecida.
- [ ] 6.5 Resolver a moeda base **uma vez**, na primeira execução, a partir de 6.4, e persistir. Alteração posterior do locale MUST NOT alterá-la. Cobre também o app já instalado, onde `EnsureDefaultAccountUseCase` retorna cedo por já existir conta: a base é resolvida na ausência do valor persistido, e não na criação de conta.
- [ ] 6.6 **Remover o default de moeda do modelo** (D28): `Account.currency` e `AccountEntity.currency` deixam de ter valor padrão, de modo que nenhuma conta seja construível sem que alguém decida a sua moeda — o compilador cobra. `BASE_CURRENCY` sai de `:core:ledger` e passa a ser a constante de último recurso da camada de consolidação. Remover o default do Kotlin é **neutro no schema**: um default de construtor não emite `DEFAULT` em SQL e a coluna segue `NOT NULL` — confirmar contra o `10.json` exportado.
- [ ] 6.7 Ajustar os sítios que o compilador apontar em 6.6, cada um informando a moeda explicitamente: `EnsureDefaultAccountUseCase` lê a base; as previews de dashboard e as fixtures passam a sua; e os sítios de 1.2 passam a moeda base em vez da constante.
- [ ] 6.8 Tela de configurações e tela de taxas: lista por moeda, data **sempre visível**, procedência no idioma de `CategoryCard:58-75` (`SwapHoriz` colhida / `ModeEdit` digitada) e sinalização de desatualizada aos 30 dias com cor `Warning` **e a palavra**.
- [ ] 6.9 Modal de edição de taxa: campo numérico, `DatePickerModal`, e a sugestão externa como **placeholder** — único ponto do app onde rede é permitida, sem estado de carregamento que bloqueie a modal. Teste: nenhuma leitura de figura consolidada depende de rede.
- [ ] 6.10 **Remoção** de taxa na tela de taxas (D27), com o `ExchangeRateDao` expondo o `delete`. É o corolário obrigatório de a taxa sobreviver à sua origem: sem ele, uma taxa colhida de uma operação já apagada fica sem caminho que a alcance. Teste: removida a única taxa de uma moeda, as figuras que dependiam dela voltam a exibir aquela moeda como termo próprio, em vez de convertida.
- [ ] 6.11 Strings de §6 em `values/` e `values-en/`.

## 7. A perna primária e a exibição

- [ ] 7.1 `Transaction.primaryEntry` e `Ledger.sourceLeg()` passam a nomear a perna monetária **negativa**, preservando o caso sem perna negativa (compra em cartão). Não é correção de defeito — `min` já devolve a negativa (D16) —, é remoção de dependência tácita, então o teste que prova a mudança é o de **compra em cartão** e o de duas pernas de mesmo sinal, não o de transferência cruzada. Revisar os consumidores de `primaryEntry`/`sourceLeg`.
- [ ] 7.2 `AccountUi` e `TransactionUi` passam a carregar a moeda, alimentada pelos mappers a partir da conta — sem isso o formatador não a recebe.
- [ ] 7.3 Trocar as APIs públicas de componente que recebem `Double` cru por valor já denominado: `BalanceCard`, `CreditCardCard`, a variante de dashboard de `AccountCard`, `CategorySpendingCard`, `TotalBalanceCard`.
- [ ] 7.4 `MoneyInputTransformation` recebe a moeda, e o default `CurrencyFormatter()` do construtor sai — é a segunda porta de escape para o locale do dispositivo. Passar a moeda nos **11** modais que a usam, incluindo a troca do símbolo quando a conta muda com o campo já preenchido (`TransferBetweenAccountsModal`, `ConfirmRecurringModal`).
- [ ] 7.5 Renderização multitermo como regra única (D22): termos empilhados, uma linha cada, alinhados à direita, o primeiro no estilo da superfície e os demais um degrau abaixo em `onSurfaceVariant`, com o `+` colado ao termo. Nenhuma superfície decide por conta própria. Cobrir com teste de duas e de um termo.
- [ ] 7.6 Degradação declarada (D20) em `InstallmentCounter`, no rótulo de `BudgetProgressCard` e no medidor de `CreditCardCard`: só o termo na base, com a marca e a indicação de parcela não convertida; nunca truncar nem quebrar. Teste de que nenhum termo é descartado em silêncio.
- [ ] 7.7 Eliminar as duplicatas locais que contornam `DisplayAmount`: `formatMoney` em `EditInvoiceBalanceModal:217` e `EditAccountBalanceModal:229` (ambas prependem `"-"` à mão, reimplementando a política de sinal fora do tipo) e os `parseMoneyToDouble` de `EditInvoiceBalanceModal`, `EditAccountBalanceModal` e `AdvancePaymentModal`.
- [ ] 7.8 Repetir a verificação de 3.10 **no nível de superfície**: com uma conta de moeda diferente da base, o card de conta, a lista de lançamentos, a modal de fatura e o contador de parcelas exibem o símbolo daquela conta, e o da base não aparece em nenhuma dessas figuras.
- [ ] 7.9 Rodapé de card (D21/D25) no idioma do `helperText`, renderizado só quando alguma linha do card é aproximada, no padrão condicional de `AccountCard:199-213`: explica a marca, revela a taxa usada com a sua data e navega para a tela de 6.5. Teste de que não aparece quando tudo é exato.

## 8. O relatório e o fechamento do inventário

- [ ] 8.1 Modelos do documento exportado (`ReportLayout`, `ReportSummaryItem`, `CategoryItem`, `TransactionItem`): hoje guardam **string já formatada** e recebem um formatador único. Acrescentar a marca — textual, e por isso sobrevive à ausência de cor — e a nota de rodapé por família de figura.
- [ ] 8.2 Ligar à consolidação de 5.4 os consumidores que exibem figura consolidada — dashboard, resumo de transações, relatório, orçamentos, categorias —, sem nenhuma multiplicação por taxa em tela, ViewModel ou modelo de UI. Teste de inspeção.
- [ ] 8.3 Atualizar `TransactionItemSignTest`, `TransactionPerspectiveTest`, `TransactionFormCoherenceTest` e os testes de formatação de `core/common`, `core/ui` e `report`.
- [ ] 8.4 Strings de §7 e §8 em `values/` e `values-en/`: nomes de moeda, explicação da figura aproximada, aviso de parcela não convertida.
- [ ] 8.5 **Moeda do limite de orçamento** (D13): a entidade de orçamento ganha o campo de moeda (a coluna e o preenchimento vêm de 4.2); o formulário oferece a escolha pré-selecionada — moeda única do app quando há uma só, base quando há várias — e a apresenta **travada** na edição; `CalculateBudgetProgressUseCase` reduz o gasto à moeda do limite. Migração dos orçamentos existentes: recebem a moeda base vigente, que é a que os denominava implicitamente, sem alterar valor algum.
- [ ] 8.6 Teste do orçamento no perfil que motiva a regra: todas as contas em moeda diferente da base, limite criado na moeda das contas, gasto inteiramente nela → **progresso exato, sem marca**. E o perfil oposto: contas em duas moedas → progresso aproximado, com marca.
- [ ] 8.7 Verificar que os cenários de `dashboard-balance-widgets` e `transaction-scope` estão cobertos por teste, inclusive o do pagamento de fatura cruzado permanecendo interno ao perímetro neutro.
- [ ] 8.8 **Fechar a lista de 1.2**: nenhum sítio de produção passa a moeda base por não saber qual é a moeda da figura. É o critério de pronto da varredura de superfície.

## 9. Os fluxos de dois valores e a porta

> A partir de 9.2 a segunda moeda passa a existir. Os fluxos vêm **antes** dela de propósito:
> aberto o seletor, o usuário cria uma conta em USD e tenta transferir no minuto seguinte, e
> sem 9.1 a transferência bate na guarda de resíduos e é recusada — feature meio entregue.

- [ ] 9.1 Os três fluxos de dois valores: `TransferBetweenAccountsUseCase` aceita o valor de destino; `PayInvoicePaymentUseCase` ganha um valor de entrada que **hoje não existe** (é derivado e exibido somente-leitura — o campo do devido mantém o papel, o editável é novo, abaixo dele), com `Action` e `UiState` novos; `AdvanceInvoicePaymentUseCase` ganha o par, com o teto `amount <= currentBillAmount` passando a valer sobre o campo na **moeda do cartão**. Nos três modais: segundo campo por `AnimatedVisibility`, campos nomeando a conta, taxa derivada como `supportingText`, e o `enabled` do botão cobrindo o **segundo** campo — sem isso a guarda de 2.8 fica alcançável por valor zerado.
- [ ] 9.2 Pré-preencher o segundo valor **apenas** quando a taxa conhecida é do mesmo dia; fora disso, placeholder com a data no `supportingText`. Não é conveniência: o valor digitado vira taxa colhida, e pré-preencher com cotação velha a regravaria como nova, em laço.
- [ ] 9.3 Colher a taxa de toda operação cruzada — derivada das duas pontas, na data da operação, origem de operação — a partir do caminho de escrita da feature, nunca do razão. A taxa **sobrevive** à remoção da operação que a originou (D27), então a remoção de transação não a toca. Testes: a operação gravada não possui campo de taxa; apagar a operação não altera a taxa nem as figuras do período.
- [ ] 9.4 `ConfirmRecurringUseCase` recusa com erro tipado redirecionar para conta ou cartão de outra moeda (D17); `ConfirmRecurringModal` oferece apenas os da moeda da recorrência **e diz por que a lista encolheu**. Exibir valor com a moeda correta em `RecurringFormModal`, `ViewRecurringModal` e `RecurringScreen`.
- [ ] 9.5 **Recusa de domínio incondicional** para a moeda: `UpdateAccountUseCase` e o caminho de atualização do cartão recusam qualquer alteração de moeda, **sem consultar `hasEntries` nem estado algum** (D12). A spec exige que a tentativa seja recusada pelo domínio; sem isto a regra viveria só na UI, que é a inversão que o projeto proíbe. Teste: uma atualização que altere a moeda é recusada tanto numa conta com lançamentos quanto numa sem.
- [ ] 9.6 `CurrencyPickerModal` em `core/designsystem`, irmão do `IconPickerModal`, alimentado pelo catálogo de 5.2.
- [ ] 9.7 **A porta.** Linha de moeda **sempre visível** no `AccountFormModal`, reusando inteiro o `DefaultAccountSelector:207-290` — símbolo como glifo na caixa de 52dp, subtítulos alternativos, caixa de `primary` → `onSurfaceVariant` e chevron ausente quando travada. **Seletor no formulário de criação, pré-selecionado com a moeda base; estado travado no de edição, sempre** — decidido pelo modo do formulário, não pelo estado da conta (D12). Idem no `CreditCardFormModal`, com `CreditCardRepository:57` gravando a moeda escolhida. Sufixo `· US$` no `AccountSelector`/`CreditCardSelector` quando houver mais de uma moeda.
- [ ] 9.8 **Inverter** o teste de inércia de 1.6: existem exatamente **dois** sítios de produção que escolhem a moeda de uma conta — `AccountFormModal` e `CreditCardFormModal` —, e qualquer terceiro falha o teste.
- [ ] 9.9 Strings de §9 em `values/` e `values-en/`.

## 10. Fechamento

- [ ] 10.1 Verificação de ponta a ponta: criar conta em USD, transferir de BRL, pagar de conta BRL a fatura de um cartão USD. Conferir as 4 entries somando zero por moeda, a conversão sem dimensão, o rótulo `TRANSFER` e `PAYMENT` (não `ADJUSTMENT`), a taxa colhida na data, o patrimônio com `≈` e rodapé, e o saldo de conta e a fatura exatos.
- [ ] 10.2 Rodar 3.9 uma última vez: perfil só-BRL sem marca em superfície alguma.
- [ ] 10.3 Atualizar `core/ledger/README.md` — referência normativa do módulo — com a conta de conversão, a moeda da perna vinda da conta e as leituras por moeda.
- [ ] 10.4 Atualizar `CLAUDE.md`: o conjunto de tipos de conta deixa de ser de cinco membros e as contas de sistema deixam de ser "apenas três".
- [ ] 10.5 Revisar os Non-Goals e confirmar que nenhum foi implementado por acidente: edição de transação cruzada, taxa entre duas moedas não-base, troca de moeda base, ganho cambial em tela, moeda de expoente ≠ 2.
