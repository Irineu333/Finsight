## 1. Dívidas que este trabalho encosta (D10)

- [x] 1.1 `currencyOf(recurring)` passou a ter uma casa pública única, como extensão sobre
      `IAccountRepository`, com o KDoc de D17/D29 preservado — **em `feature/accounts/api`,
      não em `feature/recurring/api`**. A casa prevista pela proposta é inalcançável: a
      extensão tem `IAccountRepository` como receptor, ele mora em `feature/accounts/api`,
      e a regra 1 do `feature/README.md` (*api não depende de api*) proíbe a `api` de
      recorrentes de enxergá-lo. O módulo do receptor é a casa legal mais próxima, e é
      vista por todo `impl` que precisa da regra.
      A versão publicada **delega** a regra do cartão em vez de inliná-la, de modo que
      "a moeda de um cartão" tem um dono só (tarefa 1.5)
- [x] 1.2 `RecurringCurrency.kt` do `impl` foi apagado, e a sobrecarga de cartão que o
      substituíra (`RecurringCardCurrency.kt`) também: a versão nula subiu para
      `feature/accounts/api` e os modais de formulário e de confirmação consomem aquela.
      **A justificativa que a mantivera embaixo era falsa e fica registrada como tal:** ela
      alegava que a versão nula ao lado de `IAccountRepository` tornaria ambíguas as
      chamadas de `feature/creditcards/impl`, que declarava um `currencyOf(CreditCard)`
      **não-nulo** no mesmo pacote — e a saída não foi conviver com a ambiguidade, foi
      remover a colisão renomeando a não-nula pelo que ela acrescenta (tarefa 1.5)
- [x] 1.3 A cópia inline de `currencyOf` em `DashboardComponentsBuilder` foi apagada e os
      seus dois chamadores passaram a consumir a da `api`; `List<Recurring>.moneyByCurrency()`
      dobra sobre a versão única, sem reimplementar a regra
- [x] 1.4 Suíte do dashboard e de recorrentes verdes, sem mudança de expectativa em teste
      algum — a mudança de casa é comportamento idêntico
- [x] 1.5 **"A moeda de um cartão" passou a ter um dono só.** `IAccountRepository.currencyOf(CreditCard)`
      nasceu em `feature/accounts/api` como a única leitura de `getAccountById(accountId)?.currency`;
      `currencyOf(Recurring)` delega a ela, a cópia de `feature/recurring/impl` foi apagada, e a
      não-nula de `feature/creditcards/impl` virou `requireCurrencyOf` — um invólucro que já não
      duplica a regra, só declara que ali a ausência é invariante quebrada e não figura a omitir
      (9 pontos de chamada renomeados). Mais **cinco** leituras inline da mesma regra que a issue
      não enumerava foram encontradas por grep e convertidas: `InvoicePaymentViewModel`,
      `WriteInvoicePaymentUseCase`, `DashboardComponentsBuilder`, `AddTransactionViewModel` e
      `EditTransactionViewModel`
- [x] 1.6 **A elevação de centavos sobre `CurrencyScoped` também.** `toMoney` foi publicada em
      `core/ledger` (`CurrencyScopedMoney.kt`), que é o "one path" que o KDoc da interface já
      prometia; a `private` de `EntryRepository` e a cópia de `RecurringOccurrenceRepository` —
      esta criada por esta change — foram apagadas, com o seu segundo `CENTS_PER_UNIT`

## 2. Razão — a leitura do que os ciclos confirmados lançaram (D6, D7)

- [x] 2.1 **Open Question respondida:** Room persiste `RecurringOccurrence.Status` pelo
      suporte nativo a enum, como TEXT com o `name` do membro — o schema v14 declara
      `status TEXT NOT NULL` e `Migration7To10Test` insere o literal `'CONFIRMED'`. É esse
      o literal do `WHERE`
- [x] 2.2 **Open Question respondida:** a foreign key `transactionId` com
      `onDelete = CASCADE` **está** no schema v14 implantado
      (`schemas/…/14.json`, `recurring_occurrences`), junto com o índice único sobre a
      coluna. O comportamento auto-corretivo de D6 vale, e o teste 2.8 o exercita
- [x] 2.3 A consulta agregada foi escrita em **`RecurringOccurrenceDao`**, e não em
      `RecurringDao`: ela parte de `recurring_occurrences`, que é a tabela daquele DAO, e
      é o repositório de ocorrências que a implementa. O argumento de D6 — um DAO da
      fachada pode ler `transactions`, um do razão não — vale igual para os dois. Ela
      junta `transactions` pela foreign key de `transactionId`, `entries` e `accounts`,
      filtra por `yearMonth` e por ocorrência confirmada, e agrupa por moeda com uma
      coluna por natureza, na forma que `assetMonthTotals`/`liabilityMonthTotals` já usam
- [x] 2.4 `IRecurringOccurrenceRepository.settledIn(month)` declarado, devolvendo
      `RecurringSettledMoney` (despesa e receita, cada uma por moeda), com o KDoc do corte
      pela ocorrência (D7) e de por que a leitura não toca `transactions.recurringId`
- [x] 2.5 Implementado em `RecurringOccurrenceRepository`, convertendo centavos na mesma
      via das demais leituras e tomando a magnitude do que a perna nominal registrou
- [x] 2.6 Fakes de `IRecurringOccurrenceRepository` atualizados; os que não exercitam a
      leitura lançam `NotImplementedError`. Nasceu um `FakeRecurringOccurrenceRepository`
      compartilhado em `RecurringFakes.kt`
- [x] 2.7 Teste: um ciclo confirmado com valor sobrescrito soma o valor **da transação**
      (865), não o do template (940)
- [x] 2.8 Teste: apagar a transação de um ciclo confirmado remove a ocorrência por
      cascade, e a leitura deixa de somar aquele valor
- [x] 2.9 Teste: uma ocorrência ignorada não entra em nenhuma das duas naturezas
- [x] 2.10 Teste: dois ciclos confirmados em moedas diferentes devolvem um termo por
      moeda. **Registrado junto:** o agregado tem uma linha por moeda com uma coluna por
      natureza, então um mês só de receita devolve `{BRL: 0}` de despesa e não a figura
      vazia — a mesma forma dos demais fluxos mensais da casa, e o redutor descarta o
      termo zero antes de qualquer superfície
- [x] 2.11 Teste: o corte é o mês da **ocorrência** — um ciclo de julho não entra em agosto

## 3. Domínio — o dono da leitura do mês (D8)

- [x] 3.1 `RecurringMonthOverview` definido em `feature/recurring/api`: as duas metades
      cruas por moeda, as contagens de tratados, total e ignorados, e o número de
      templates deixados fora da soma
- [x] 3.2 `GetRecurringMonthOverviewUseCase` compõe as duas metades: a projeção consumindo
      `GetUnhandledRecurringUseCase` — o mês inteiro, sem o corte por dia — e o fato
      consumindo a leitura da tarefa 2
- [x] 3.3 A denominação de cada template chega **como parâmetro** (`currencyOf`), não como
      chamada: o use case mora na `api`, que não pode nomear `IAccountRepository` (mesma
      regra da tarefa 1.1). É também o que faz a resolução única por emissão da tarefa 5.4
      ser compartilhada em vez de repetida. Os templates sem moeda são contados, não
      descartados em silêncio
- [x] 3.4 Use case registrado no `RecurringModule`
- [x] 3.5 Teste: um template não tratado com dia de ciclo futuro (28, no dia 10) entra na
      projeção do mês
- [x] 3.6 Teste: um template com ocorrência no mês — confirmada ou ignorada — não entra na
      projeção
- [x] 3.7 Teste: um template arquivado não entra na projeção, e o dinheiro que ele
      confirmou antes de ser arquivado permanece no fato do mês
- [x] 3.8 Teste: um template sem conta resolvível não entra na soma e é contado como fora
      dela
- [x] 3.9 Teste: o contador conta o ciclo ignorado como tratado, e o declara separadamente

## 4. Apresentação — as figuras consolidadas (D9, D13, D14)

- [x] 4.1 `RecurringSummaryFactory` criado em `feature/recurring/impl`, nos moldes de
      `BalanceOverviewFactory`: `internal suspend fun` recebendo `ConsolidateMoneyUseCase`
      como parâmetro e devolvendo as quatro figuras já consolidadas mais as contagens
- [x] 4.2 As quatro figuras saem com política de magnitude, com o KDoc registrando que a
      ausência de sinal decorre de o card não exibir total, e que a regra se inverte se um
      total for acrescentado
- [x] 4.3 Todas as quatro figuras — o fato inclusive — são consolidadas em
      `month.lastDay`, as taxas do mês selecionado e não as de hoje
- [x] 4.4 Teste: um mês multimoeda sem taxa sai em dois termos e marcado como aproximado
- [x] 4.5 Teste: um mês sem movimento devolve zero denominado pelo redutor, com um termo, e
      não figura ausente
- [x] 4.6 Teste: as quatro figuras carregam política de magnitude

## 5. Estado da tela (D9, D10)

- [x] 5.1 `RecurringUiState.Content` ganhou `selectedYearMonth` e `RecurringAction.SelectMonth`
- [x] 5.2 `Content` carrega `RecurringMonthSummary` — figuras consolidadas e contagens já
      resolvidas, sem modelo de domínio como campo
- [x] 5.3 O `combine` de `RecurringViewModel` passou a cinco fontes: templates, ocorrências,
      filtro, mês e `ObserveConsolidationChangesUseCase`. **A quinta é a composta, não a do
      razão sozinha:** ela já contém `observeLedgerChanges()` (que é o que D9 exige, e sem
      o qual as figuras congelariam enquanto o razão anda) e ainda carrega a taxa e a moeda
      base, que também movem estas figuras e não escrevem entry alguma. É também o que
      `ConsolidatedFiguresReactTest` exige de todo view model que reduz uma figura
- [x] 5.4 As moedas dos templates são resolvidas em **uma consulta por emissão**, num mapa
      compartilhado entre a lista e o resumo. O mapa sozinho evitaria dobrar a conta sem
      reduzi-la — e como o percurso passou do subconjunto filtrado para a lista inteira,
      sob o filtro `ACTIVE` uma base com arquivadas pagaria **mais** consultas do que
      pagava antes. Então `currenciesOf()` lê a carta de contas uma vez e resolve contra
      ela em memória, pela mesma regra (`Recurring.currencyBy`, que recebe a leitura da
      conta como parâmetro em vez de crescer uma segunda cópia)
- [x] 5.5 Teste de view model: trocar o filtro não altera nenhuma das quatro figuras nem o
      contador
- [x] 5.6 Teste de view model: trocar o mês altera as figuras e o contador e não altera a
      lista
- [x] 5.7 Teste de view model: base sem recorrência alguma continua emitindo o estado que
      oferece a criação da primeira, sem panorama
- [x] 5.8 **A saída apontada pela issue estava errada e o registro é este:** ela indicava
      `getAllAccountsIncludingClosed()`, que é `WHERE type = 'ASSET'` — a conta que um
      cartão projeta é `LIABILITY`, então resolver contra a fachada tiraria a moeda de todo
      template de cartão e a lista inteira leria `***`. A leitura certa é
      `getAllLedgerAccounts()`, a carta inteira. `FakeAccountRepository` passou a distinguir
      as três leituras como o DAO distingue — sem isso o fake devolvia a mesma lista às três
      e nenhum teste podia pegar a troca
- [x] 5.9 Teste de view model: um template de **cartão** é denominado pela conta
      `LIABILITY` que o cartão projeta. Nenhum teste cobria isso — os demais usam templates
      que nomeiam conta direto —, e é exatamente a regressão que a fachada teria causado em
      silêncio; verificado que o teste falha quando o view model lê a fachada e passa quando
      lê a carta

## 6. Strings (nos dois idiomas)

- [x] 6.1 `recurring_summary_settled` e `recurring_summary_forecast` nos dois arquivos
- [x] 6.2 `recurring_summary_fixed_expense` e `recurring_summary_fixed_income` — distintas
      de `recurring_expense`/`recurring_income`, porque declaram o que está dentro do
      número e não o tipo de um lançamento
- [x] 6.3 `recurring_summary_counter` e o plural `recurring_summary_skipped`. **O plural de
      "pendentes" não entrou, e a decisão fica registrada:** *pendente* tem definição no
      domínio — é o não tratado **cujo dia já chegou** (`GetPendingRecurringUseCase`) — e o
      bloco de projeção é o mês inteiro, que o próprio spec proíbe de ser regido pelo corte
      por dia. Chamar de "pendentes" o que o card mostra contradiria o vocabulário da casa
      no exato ponto em que ele importa
- [x] 6.4 Plural `recurring_summary_undenominated`, com copy própria — nomeia a conta que
      falta, e não a taxa que falta (D13)
- [x] 6.5 `recurring_card_monthly_amount` removida dos dois arquivos, junto com o seu único
      consumidor
- [x] 6.6 `recurring_summary_expand` e `recurring_summary_collapse` nos dois arquivos, como
      `contentDescription` da seta do bloco dobrável: para que lado ela aponta não pode ser a
      única coisa que diz o estado
- [x] 6.7 Paridade conferida: 807 `<string>` mais 4 `<plurals>` em cada arquivo, nenhuma
      chave exclusiva de um lado (conferido por diff das chaves, não por contagem)

## 7. A linha da lista (D1, D2, D3, D4, D5)

- [x] 7.1 `RecurringCard` reescrito como grade 2×2: esquerda com rótulo e origem, direita
      com figura e dia; padding 12dp, raio 12dp, chip de 40dp com raio 8dp
- [x] 7.2 O badge de tipo virou o glifo de direção a 16dp, com `contentDescription`
      reusando `recurring_expense`/`recurring_income`
- [x] 7.3 O badge de arquivada virou glifo antes do rótulo, também com descrição
- [x] 7.4 `weight(1f, fill = false)` no rótulo, e nunca na figura
- [x] 7.5 A marca de valor irresolvível é renderizada por `formatOrUnresolved` — o dono
      único da decisão — mantendo a tag `recurring_card_amount` **no `Text`**
- [x] 7.6 A origem inutilizável é afirmada por glifo (`LinkOff`, tom `Warning`) **e** texto
      (`recurring_source_unusable`), e não pela troca de `onSurfaceVariant` por `outline`
- [x] 7.7 A legenda "Valor mensal" e o nome da categoria saíram da linha
- [x] 7.8 Altura constante em toda variante, **por aritmética e não por medição**: a coluna
      da direita governa (`titleMedium` 24dp + 4dp de vão + `labelMedium` 16dp = 44dp,
      acima dos 40dp do chip) e os seus dois textos existem sempre — a figura vira `***`
      mas não desaparece, e o dia é sempre uma linha. Rótulo e origem são `maxLines = 1`
      com elipse, então nem o rótulo longo nem o nome de cartão longo mudam a altura. O
      vão entre as duas linhas é o mesmo nos dois lados (`ROW_LINE_GAP`), para a linha ler
      como uma grade e não como duas pilhas encostadas
- [x] 7.9 A origem **arquivada** volta a ser nomeada. `hasUsableSource` é falso tanto para a
      removida quanto para a arquivada, e o ramo falso de `SourceLine` não chegava a ler um
      nome — duas "Aluguel" em bancos arquivados diferentes liam idêntico, perdendo
      exatamente a distinção pela qual a linha existe. O glifo `LinkOff` e o tom `Warning`
      seguem afirmando que ela não posta; `recurring_source_unusable` passa a falar só pela
      origem que sumiu, que é a única sem nome a dar (a foreign key é `SET_NULL`). A regra
      saiu para `Recurring.sourceName()`, e `SourceNameTest` a fixa

## 8. O card de resumo (D11, D12, D14, D15)

- [x] 8.1 `feature:settings:api` declarado em `feature/recurring/impl/build.gradle.kts`
- [x] 8.2 O card compõe chip de mês, dois blocos rotulados com duas figuras cada, e o
      rodapé com o contador
- [x] 8.3 O chip de período foi copiado de `SummaryCard`, sobre o `MonthPickerDropdownMenu`
      público de `core/designsystem` (D12)
- [x] 8.4 **Um** `ConsolidationBadge` para o card inteiro, com `onSeeRates` navegando para
      `ExchangeRatesRoute`
- [x] 8.5 A menção a ciclos ignorados e a de templates fora da soma só aparecem quando há —
      anotação condicional é ausência, não zero
- [x] 8.6 As figuras do resumo têm ids próprios
      (`recurring_summary_{settled,forecast}_{expense,income}`), distintos de
      `recurring_card_amount`
- [x] 8.7 Fato e projeção se distinguem pela legenda do bloco, pela ordem e por um degrau
      de tamanho (18sp bold → 16sp semibold), mantendo a cor de natureza nas quatro figuras
- [x] 8.8 Cada metade virou bloco dobrável, e a que não tem movimento abre dobrada (D15). O
      rótulo permanece nos dois estados — o que se dobra é a figura, e um card que escondesse
      a palavra junto teria encolhido por um motivo invisível. O estado inicial é rederivado
      só quando aquele bloco cruza entre ter e não ter movimento (`rememberSaveable(holdsNothing)`),
      sem o que o bloco recém-aberto se dobraria de novo à primeira troca de mês
- [x] 8.9 O predicado subiu para `ConsolidatedAmount.isZero`, ao lado da figura, e
      `BalanceOverviewFactory.orNullIfZero()` passou a consumi-lo em vez da sua própria
      leitura: duas superfícies perguntavam "isto afirma movimento?" à mão, e a resposta
      certa é sobre **todos** os termos, não sobre o primeiro
- [x] 8.10 Teste: `HoldsNothingTest` fixa a leitura por todos os termos —
      `R$ 0,00 + US$ 50,00` é um mês com dinheiro dentro, e um bloco assim não abre dobrado

## 9. A estrutura da tela (D11)

- [x] 9.1 O card e o vazio de recorte são itens do `LazyColumn`, com chave, no lugar dos
      ramos exclusivos em `fillMaxSize`
- [x] 9.2 O vazio de base continua ocupando a tela, com a oferta de criar a primeira
      recorrência intacta
- [x] 9.3 O recorte de arquivadas numa base sem arquivadas exibe o card e a mensagem de
      recorte vazio abaixo dele — por construção: o item do card é incondicional dentro de
      `Content`, e o do vazio vem depois dele

## 10. Verificação

- [x] 10.1 `./gradlew jvmTest --rerun-tasks` verde: 353 tasks executadas, **1489 testes em
      249 classes, nenhuma falha**, `EveryFigureCanExplainItselfTest` incluído (23 classes em
      `:app:shared`). Rerodado depois de cada correção de verificação — o
      `contentDescription` do glifo de origem, os dois KDoc, e as dívidas das tarefas 1.5,
      1.6 e 5.4. O teste a mais em relação à contagem anterior é o 5.9
- [x] 10.2 As cinco asserções de `recurring_card_amount` continuam válidas por leitura do
      código: a tag permanece num nó de `Text`, o texto continua sem sinal (política de
      magnitude) e as figuras do resumo têm ids próprios, de modo que o
      `assertNotVisible` por id **e** texto não pode casar com elas
- [x] 10.3 Executado, **2/2 verdes** — `recurring_lifecycle` (2m44s) e
      `recurring_from_transaction` (38s). Aparelho: AVD `finsight_e2e`, serial
      `emulator-5556`, conferida linha a linha antes do run — API 36, `wm size`
      1080x2400, `wm density` 420, `ro.product.locale` en-US, `am get-config` com
      `-en-rUS-` e `-nokeys-`, IME `LatinIME`, `show_ime_with_hard_keyboard` 0 —, com o
      alvo fixado por `ANDROID_SERIAL` **e** `--device` porque havia um segundo aparelho
      ligado (§2.2.1), e o APK de debug reinstalado antes.
      Foi o recorte `--include-tags recurring`, rodado **pelo workspace** — o `config.yaml`
      vale e as animações continuam desligadas, que é a forma que a §2.3 nomeia como a
      certa para um subconjunto.
      **Registrado junto:** matar um run pela metade deixa o driver do Maestro morto no
      aparelho, e todo fluxo seguinte morre em `UNAVAILABLE` em ~70ms. `rm
      ~/.maestro/sessions` (§2.4) não basta; o conserto é `adb uninstall dev.mobile.maestro`
      e `dev.mobile.maestro.test`, que o Maestro reinstala sozinho no run seguinte
- [x] 10.6 **A suíte inteira foi rodada depois**, no mesmo aparelho e com os sete checks
      refeitos por serial, porque as dívidas pagas na verificação (tarefas 1.5, 1.6 e 5.4)
      tocam cartões, transações, dashboard e o razão — áreas que o recorte `recurring` não
      atravessa. Resultado: **14/15**. Os dois fluxos de recorrentes seguem verdes
      (`recurring_lifecycle` 2m46s, `recurring_from_transaction` 41s), e o vermelho é
      `creditcards_lifecycle`.
      **Não é desta change, e a prova é um run e não um argumento:** com as mudanças
      guardadas (`git stash`) e o APK reinstalado a partir do `HEAD`, o mesmo fluxo falha
      no mesmo passo com o mesmo erro. Está no backlog como
      `the-credit-card-flow-depends-on-where-its-forty-five-day-jump-lands` — o mesmo fluxo
      passou no run de 24/08 (salto → 08/10) e falha no de hoje (salto → 10/10, o dia de
      fechamento), e baixar `JUMP_DAYS` para 44 move a falha para outro passo.
      Os dois fluxos de recorrentes foram rodados **uma última vez** depois da limpeza que
      fez `currencyOf(Recurring)` delegar a `currencyOf(CreditCard)`, para que a travessia
      responda pelo APK que está no branch e não por um anterior: **2/2 verdes**
      (`recurring_lifecycle` 2m48s, `recurring_from_transaction` 41s)
- [x] 10.4 Medido, e a coluna direita **aguenta**. A janela foi posta em 2205x1080px a
      420dpi = **840x411dp**, o breakpoint `LARGE` exato: é ali que o shell reserva o painel
      de detalhe e a coluna da lista fica no mínimo que ela pode ter. Medido na captura,
      o painel ocupa 402dp (confere com `DetailPaneWidth`) e a rail 96dp, deixando **342dp**
      de coluna — mais estreita que os 411dp do aparelho de referência em retrato. O card
      de resumo cabe inteiro (chip, os dois blocos rotulados, o contador), e a linha 2×2
      mantém as duas colunas: o rótulo é quem cede (`weight(1f, fill = false)`), a figura e
      o dia saem íntegros. Comparado com a mesma tela em retrato, nada foi espremido para
      fora.
      **O que a medição achou não foi a coluna, foi o FAB.** Numa janela de 411dp de altura
      a lista enche a viewport, e o botão fica sobre a coluna direita da última linha, que
      passa a ler `$` seguido do botão. Não há rolagem que o libere: o `contentPadding` do
      `LazyColumn` reserva 16dp abaixo do último item contra os 56dp do FAB. **Não é desta
      change** — o `contentPadding` é idêntico em `main`, e `TransactionsScreen` e
      `CategoriesScreen` declaram o mesmo —, mas o redesenho mudou *o que* fica escondido:
      a figura foi para a borda direita, exatamente sob o botão. Está no backlog como
      `the-fab-covers-the-last-rows-figure-when-the-list-reaches-the-bottom`
- [x] 10.5 **Open Question decidida pela variante completa.** A compacta trocaria o rodapé
      por uma linha ao lado do chip, e é ali que mora a única leitura do card em que um
      ciclo ignorado é representável — espremê-la ao lado do seletor de mês é o oposto do
      que este trabalho está fazendo. Se a altura incomodar na prática, a troca continua
      sendo de umas poucas linhas
