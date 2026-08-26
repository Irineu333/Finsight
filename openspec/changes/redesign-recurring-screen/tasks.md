## 1. Dívidas que este trabalho encosta (D10)

- [x] 1.1 `currencyOf(recurring)` passou a ter um dono só, como extensão pública sobre
      `IAccountRepository`, com o KDoc de D17/D29 preservado — **em `feature/accounts/api`,
      não em `feature/recurring/api`**. A casa prevista pela proposta é inalcançável: a
      extensão tem `IAccountRepository` como receptor, ele mora em `feature/accounts/api`,
      e a regra 1 do `feature/README.md` (*api não depende de api*) proíbe a `api` de
      recorrentes de enxergá-lo. O módulo do receptor é a casa legal mais próxima, e é
      vista pelos três `impl` que precisam da regra
- [x] 1.2 `RecurringCurrency.kt` do `impl` foi apagado; no lugar dele nasceu
      `CardCurrency.kt`, com **apenas** a sobrecarga de cartão que os modais de formulário
      e de confirmação consomem. Ela não subiu junto: `feature/creditcards/impl` declara
      um `currencyOf(CreditCard): String` **não-nulo** no mesmo pacote, e publicar a
      versão nula ao lado de `IAccountRepository` tornaria as chamadas de lá ambíguas
- [x] 1.3 A cópia inline de `currencyOf` em `DashboardComponentsBuilder` foi apagada e os
      seus dois chamadores passaram a consumir a da `api`; `List<Recurring>.moneyByCurrency()`
      dobra sobre a versão única, sem reimplementar a regra
- [x] 1.4 Suíte do dashboard e de recorrentes verdes, sem mudança de expectativa em teste
      algum — a mudança de casa é comportamento idêntico

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
- [x] 5.4 As moedas dos templates são resolvidas **uma vez por emissão**, num mapa
      compartilhado entre a lista e o resumo, eliminando a chamada item a item
- [x] 5.5 Teste de view model: trocar o filtro não altera nenhuma das quatro figuras nem o
      contador
- [x] 5.6 Teste de view model: trocar o mês altera as figuras e o contador e não altera a
      lista
- [x] 5.7 Teste de view model: base sem recorrência alguma continua emitindo o estado que
      oferece a criação da primeira, sem panorama

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
- [x] 6.6 Paridade conferida: 809 chaves em cada arquivo, nenhuma exclusiva de um lado

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
      da direita governa (`titleMedium` 24dp + 2dp + `labelMedium` 16dp = 42dp, acima dos
      40dp do chip) e os seus dois textos existem sempre — a figura vira `***` mas não
      desaparece, e o dia é sempre uma linha. Rótulo e origem são `maxLines = 1` com
      elipse, então nem o rótulo longo nem o nome de cartão longo mudam a altura

## 8. O card de resumo (D11, D12, D14)

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

## 9. A estrutura da tela (D11)

- [x] 9.1 O card e o vazio de recorte são itens do `LazyColumn`, com chave, no lugar dos
      ramos exclusivos em `fillMaxSize`
- [x] 9.2 O vazio de base continua ocupando a tela, com a oferta de criar a primeira
      recorrência intacta
- [x] 9.3 O recorte de arquivadas numa base sem arquivadas exibe o card e a mensagem de
      recorte vazio abaixo dele — por construção: o item do card é incondicional dentro de
      `Content`, e o do vazio vem depois dele

## 10. Verificação

- [x] 10.1 `./gradlew jvmTest` verde, `EveryFigureCanExplainItselfTest` incluído
      (`:app:shared:jvmTest --rerun-tasks`: 23 classes, nenhuma falha)
- [x] 10.2 As cinco asserções de `recurring_card_amount` continuam válidas por leitura do
      código: a tag permanece num nó de `Text`, o texto continua sem sinal (política de
      magnitude) e as figuras do resumo têm ids próprios, de modo que o
      `assertNotVisible` por id **e** texto não pode casar com elas
- [ ] 10.3 **Não executado:** `.maestro/flows/recurring/` exige o AVD do
      `.maestro/README.md` §2 e `adb devices` não lista dispositivo algum nesta máquina.
      Fica pendente, com o que asseverar já registrado em 10.2
- [ ] 10.4 **Não executado:** conferir a tela no painel estreito de janela larga. É medição
      na tela, e a coluna direita da grade 2×2 é a que mais sofre
- [x] 10.5 **Open Question decidida pela variante completa.** A compacta trocaria o rodapé
      por uma linha ao lado do chip, e é ali que mora a única leitura do card em que um
      ciclo ignorado é representável — espremê-la ao lado do seletor de mês é o oposto do
      que este trabalho está fazendo. Se a altura incomodar na prática, a troca continua
      sendo de umas poucas linhas
