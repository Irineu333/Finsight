## 1. O dono da partição, no domínio

- [ ] 1.1 Criar o modelo da partição em `feature/recurring/api` — os quatro estados de ciclo e o
  agrupamento que os carrega, com KDoc dizendo que o estado é derivado e nunca persistido
- [ ] 1.2 Criar `GetRecurringCyclesUseCase(month, today)` em `feature/recurring/api`, construído
  sobre `GetUnhandledRecurringUseCase` e sobre as ocorrências do mês (D2)
- [ ] 1.3 Implementar o corte entre pendente e a lançar por **data**, via
  `YearMonth.safeOnDay(dayOfMonth)` comparado com hoje — nunca dia contra dia (D3)
- [ ] 1.4 Reescrever `GetPendingRecurringUseCase` como pergunta feita ao dono da partição, sem
  predicado próprio, preservando a assinatura que o dashboard consome
- [ ] 1.5 Fazer `GetRecurringMonthOverviewUseCase` consumir a partição em vez de recalcular
  `handled`, `total` e `skipped`
- [ ] 1.6 Registrar o novo caso de uso no `RecurringModule` (Koin, `factory {}`)
- [ ] 1.7 Testar a partição: os quatro estados mutuamente exclusivos, mês passado (nada em "a
  lançar"), mês futuro (nada em "pendente"), dia 31 em mês de 30 dias, e mês anterior ao
  `originMonth` (nenhum ciclo)
- [ ] 1.8 Atualizar `GetPendingRecurringUseCaseTest` e `GetRecurringMonthOverviewUseCaseTest`
  para o novo caminho, mantendo os cenários que já cobriam

## 2. A leitura das transações lançadas

- [ ] 2.1 Adicionar a leitura por conjunto de ids a `ITransactionRepository` (`core/ledger`),
  com KDoc dizendo por que ela existe: nem `getTransactionById` por linha, nem o ledger inteiro
  (D8)
- [ ] 2.2 Implementar a consulta no DAO e no repositório de transações
- [ ] 2.3 Testar que a leitura devolve as transações pedidas em uma consulta, e que um id
  inexistente é ausência e não erro

## 3. O estado da tela

- [ ] 3.1 Reescrever `RecurringUiState.Content` para carregar as seções por estado de ciclo, com
  a contagem de cada uma, no lugar da lista única
- [ ] 3.2 Reduzir `RecurringFilter` ao eixo de natureza — `ALL`, `EXPENSE`, `INCOME` — removendo
  `ARCHIVED` e renomeando `ACTIVE` (D12)
- [ ] 3.3 Fazer o mês selecionado governar a lista no `RecurringViewModel`, e não apenas o
  resumo (D1)
- [ ] 3.4 Resolver os ciclos lançados no view model via `toTransactionUi(lookup)`, a partir das
  transações lidas em 2.1, mantendo a resolução de moedas dos demais em uma consulta por emissão
- [ ] 3.5 Ordenar cada seção pela data efetiva do ciclo, crescente (D6)
- [ ] 3.6 Remover `handled`, `total` e `skipped` de `RecurringMonthSummary` e da
  `RecurringSummaryFactory`, preservando `undenominated`
- [ ] 3.7 Atualizar `RecurringViewModelTest` e `RecurringSummaryFactoryTest`; verificar se
  `HoldsNothingTest` ainda descreve o que a tela faz

## 4. A lista em seções

- [ ] 4.1 Renderizar as seções na ordem pendente, a lançar, lançado, ignorado, com cabeçalho e
  contagem, sem `stickyHeader` (D4, D5)
- [ ] 4.2 Não renderizar seção sem itens — cabeçalho e contagem incluídos
- [ ] 4.3 Renderizar a linha dos ciclos lançados com `TransactionCard`, e conferir que a figura
  sai como magnitude para despesa e receita (D7)
- [ ] 4.4 Manter `RecurringCard` nas seções de template, com a marca de valor irresolvível
  restrita a elas (D9)
- [ ] 4.5 Renderizar a linha do ciclo ignorado com o valor do template, sem tratamento visual
  próprio (D10)
- [ ] 4.6 Exibir o vazio de mês como item abaixo do resumo, nunca como ramo que ocupa a tela
  (D14)
- [ ] 4.7 Remover o rodapé de contadores do `RecurringMonthCard` e o divisor acima dele,
  mantendo a linha de templates sem conta (D13)
- [ ] 4.8 Publicar as `testTag` das seções e conferir onde `recurring_card_amount` continua
  válido depois da divisão em seções

## 5. O destino das arquivadas

- [ ] 5.1 Declarar a rota interna do arquivo no `impl` e registrá-la no `recurringGraph()`
- [ ] 5.2 Construir a tela do arquivo: lista plana, sem resumo, sem seletor de mês e sem seções
- [ ] 5.3 Não afirmar "arquivada" em cada linha do arquivo, já que ali todas estão
- [ ] 5.4 Oferecer desarquivar pelo mesmo caminho de hoje (`UnarchiveRecurringModal`)
- [ ] 5.5 Dar entrada ao arquivo a partir da tela de recorrentes, e remover a opção de arquivadas
  do seletor de recorte
- [ ] 5.6 Testar que uma recorrência arquivada some da lista mensal em qualquer mês e permanece
  no arquivo

## 6. Strings

- [ ] 6.1 Adicionar as chaves das quatro seções e das contagens em
  `core/resources/.../values/strings.xml` (pt)
- [ ] 6.2 Adicionar as mesmas chaves em `values-en/strings.xml` (en), na mesma mudança
- [ ] 6.3 Adicionar as chaves do arquivo — título da tela, vazio, entrada — nos dois arquivos
- [ ] 6.4 Remover as chaves que ficaram sem consumidor (contador do resumo, recorte de
  arquivadas), conferindo antes que nenhuma outra tela as usa

## 7. Verificação

- [ ] 7.1 Rodar `./gradlew jvmTest` e ler a saída
- [ ] 7.2 Exercitar a tela no app: mês passado, mês corrente e mês futuro, conferindo quais
  seções aparecem em cada um
- [ ] 7.3 Conferir um ciclo confirmado com valor e título sobrescritos: a seção "lançado" precisa
  exibir o que o razão registrou
- [ ] 7.4 Conferir um ciclo lançado cujo template perdeu a conta: figura do razão, e não a marca
  de irresolvível
- [ ] 7.5 Conferir o card de pendências do dashboard depois da mudança do predicado, na virada
  do mês
- [ ] 7.6 Levantar quais dos cinco pontos Maestro que afirmam `recurring_card_amount` caem em
  qual seção, e atualizar os fluxos afetados
- [ ] 7.7 Conferir a densidade da primeira tela e o painel estreito da janela larga, com as
  seções renderizadas
