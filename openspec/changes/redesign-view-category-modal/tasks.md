## 1. Razão — a série mensal (D1)

- [ ] 1.1 Adicionar em `EntryDao` a consulta que agrupa as entries de uma dimensão por
      `substr(o.date, 1, 7)` e por moeda, com a projeção correspondente (mês, moeda, total),
      filtrando pelo corte superior (D11)
- [ ] 1.2 Declarar o membro em `IEntryRepository` no vocabulário do razão — dimensão e mês,
      nunca categoria — recebendo o corte superior como parâmetro e devolvendo por moeda, com
      KDoc explicando por que mês sem movimento não vira linha zero e por que o corte não é
      derivado de relógio
- [ ] 1.3 Implementar em `EntryRepository`, convertendo centavos pela mesma via das demais
      leituras, sem sinal próprio
- [ ] 1.4 Atualizar os ~26 fakes de `IEntryRepository` nos testes; os que não exercitam a
      série lançam `NotImplementedError`, como já fazem para outras leituras
- [ ] 1.5 Teste de razão: a série de uma dimensão em duas moedas devolve uma linha por
      (mês, moeda), e mês sem entry não aparece
- [ ] 1.6 Teste de razão: o total de um mês pela série coincide com
      `dimensionBalanceInMonthByCurrency` daquele mês
- [ ] 1.7 Teste de razão: com entries em meses posteriores ao corte, a série devolve os meses
      até o corte inclusive e nenhum posterior

## 2. Domínio — janela, média e variação (D2, D3, D4, D5, D6)

- [ ] 2.1 Definir em `feature/categories/api` o modelo de resultado do panorama: figura do
      mês corrente, figuras da janela (média e total) com o número de meses, variação, e a
      variante de estado da categoria
- [ ] 2.2 Declarar `CalculateCategoryOverviewUseCase` em `feature/categories/api`
- [ ] 2.3 Ler a série com corte superior no **mês corrente**, de modo que nenhum lançamento
      futuro alcance figura alguma (D11)
- [ ] 2.4 Implementar em `feature/categories/impl` o cálculo da janela:
      `min(12, meses fechados desde o primeiro lançamento)`, com o primeiro lançamento tirado
      da própria série — já cortada, portanto nunca um mês futuro
- [ ] 2.5 Somar e dividir os meses da janela **por moeda**, antes de qualquer conversão, e
      consolidar cada figura uma única vez com `on` = último dia da janela
- [ ] 2.6 Consolidar a figura do mês corrente com `on` = último dia do mês corrente
- [ ] 2.7 Calcular a variação por `comparativeMagnitudes` sobre `{mês corrente, média}` com
      data única, devolvendo ausência — nunca `0%` — quando a média é zero, quando não há mês
      fechado com lançamento, ou quando `magnitudeOf` devolve `null`
- [ ] 2.8 Resolver a variante de estado: arquivada, sem lançamento algum, sem mês fechado com
      lançamento, ou ativa
- [ ] 2.9 Para a categoria arquivada, produzir o total histórico e o intervalo de datas, do
      primeiro ao **último lançamento** — nunca a data do arquivamento, que não existe (D10)
- [ ] 2.10 Registrar o caso de uso no módulo Koin de categories
- [ ] 2.11 Testes: janela cheia (12), janela encurtada (categoria jovem), mês sem lançamento
      contando como zero no divisor, e `média × meses = total` exato
- [ ] 2.12 Testes: o mês corrente não entra na janela; um mês em duas moedas não é reduzido
      pelo razão
- [ ] 2.13 Testes: cada um dos três casos de ausência de variação, e nenhum deles produzindo
      `0%`
- [ ] 2.14 Testes: cada variante de estado devolve a figura de destaque correta
- [ ] 2.15 Testes de lançamento futuro: parcelas em meses posteriores não entram no mês
      corrente, não entram na janela, não contam no número de meses declarado, e não estendem
      o intervalo da categoria arquivada

## 3. Strings

- [ ] 3.1 Adicionar em `values/strings.xml` (pt) as chaves: rótulo da média com nº de meses,
      rótulo do total com nº de meses, mês parcial com dia e total de dias, acima/abaixo da
      média, e os três motivos de ausência de variação
- [ ] 3.2 Adicionar as mesmas chaves em `values-en/strings.xml`
- [ ] 3.3 Adicionar a string do comando que abre os lançamentos da categoria, nos dois idiomas
- [ ] 3.4 Adicionar a string do estado vazio da categoria sem lançamento, nos dois idiomas
- [ ] 3.5 Adicionar a string do intervalo de datas da categoria arquivada, nos dois idiomas
- [ ] 3.6 Conferir que nenhuma chave nova existe em apenas um dos dois arquivos

## 4. Rota de transações com categoria (D8)

- [ ] 4.1 Adicionar `filterCategoryId: Long?` a `TransactionsRoute`, seguindo a nomenclatura
      de `filterLabel`/`filterTarget`
- [ ] 4.2 Propagar o parâmetro de `transactionsGraph()` até `TransactionsScreen` e ao
      `ViewModel`, pela mesma via dos filtros já existentes
- [ ] 4.3 No `TransactionsViewModel`, resolver o id para `SpendingSubject.Categorized` como
      estado inicial de `TransactionsFilters.subject`, aceitando categoria arquivada
- [ ] 4.4 Um id que não resolve para categoria alguma deixa a lista no estado neutro, sem
      recorte e sem lista vazia
- [ ] 4.5 Teste: a lista abre recortada e o controle exibe a categoria como selecionada
- [ ] 4.6 Teste: escolher o estado neutro desfaz o recorte inicial
- [ ] 4.7 Teste: categoria arquivada como valor inicial aparece selecionada mesmo não sendo
      oferecida no menu
- [ ] 4.8 Teste: id que não resolve abre no estado neutro

## 5. Modal de categoria (D6, D7)

- [ ] 5.1 Remover `MonthSelector`, `selectedYearMonth` e as ações `NextMonth`/`PreviousMonth`
      de `ViewCategoryAction`, `ViewCategoryUiState` e `ViewCategoryViewModel`
- [ ] 5.2 Reescrever `ViewCategoryUiState.Content` sobre o resultado do caso de uso, com a
      variante de estado explícita
- [ ] 5.3 Fazer o `ViewModel` observar o caso de uso, mantendo
      `observeConsolidationChanges()` como gatilho de releitura
- [ ] 5.4 Renderizar a figura de destaque conforme a variante, com o mês corrente anunciado
      como parcial (dia e total de dias)
- [ ] 5.5 Renderizar a variação com significante textual e indicador de direção, **sem** as
      cores de receita e despesa; e renderizar o motivo quando ela não existe
- [ ] 5.6 Renderizar as duas figuras da janela com o nº de meses declarado no rótulo
- [ ] 5.7 Renderizar o estado vazio da categoria sem lançamento, sem figura zerada em destaque
- [ ] 5.8 Adicionar o comando que navega para `TransactionsRoute(filterCategoryId = …)`,
      visível sem rolagem
- [ ] 5.9 Manter as ações no rodapé fixado; `DetailActions()` não muda de lugar
- [ ] 5.10 Rever os `testTag`: `view_category_total_amount` passa a nomear o total da janela;
      decidir e aplicar as tags do mês corrente, da variação e do comando de lançamentos
- [ ] 5.11 Atualizar `ViewCategoryViewModelTest` para o novo estado, sem deslocamento de mês

## 6. E2E (Maestro)

- [ ] 6.1 Reescrever em `.maestro/flows/categories/lifecycle.yaml` as asserções sobre
      `view_category_total_amount`, que passa a afirmar o total da janela
- [ ] 6.2 Substituir a asserção de `view_category_transaction_count`, que deixa de ser mensal
- [ ] 6.3 Acrescentar asserção sobre o comando que abre os lançamentos da categoria
- [ ] 6.4 Conferir que todo elemento novo alcançado por `id:` está sob um `testTag` cuja raiz
      de composição chama `Modifier.exposeTestTags()`

## 7. Verificação

- [ ] 7.1 `./gradlew jvmTest` verde, com a saída lida
- [ ] 7.2 `./gradlew :app:android:assembleDebug` compila
- [ ] 7.3 Exercitar no app: categoria ativa com histórico, categoria jovem (janela encurtada),
      categoria sem lançamento, categoria arquivada
- [ ] 7.4 Exercitar o caso multi-moeda: categoria com gastos em duas moedas, com e sem taxa
      registrada, conferindo que a variação some em vez de virar `0%`
- [ ] 7.5 Exercitar o caso das parcelas: compra parcelada numa categoria, conferindo que
      apenas a parcela do mês corrente aparece nas figuras; depois arquivar a categoria com
      parcelas pendentes e conferir que o intervalo não termina no futuro
- [ ] 7.5 Rodar a suíte Maestro conforme `.maestro/README.md` §2, reportando em que
      dispositivo a corrida aconteceu
