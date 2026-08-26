## 1. Dívidas que este trabalho encosta (D10)

- [ ] 1.1 Mover `currencyOf(recurring)` e `currencyOf(creditCard)` de
      `feature/recurring/impl` para `feature/recurring/api`, como extensão pública sobre
      `IAccountRepository`, com o KDoc que já existe (D17/D29) preservado
- [ ] 1.2 Apagar `RecurringCurrency.kt` do `impl` e apontar os seus chamadores para a versão
      da `api`
- [ ] 1.3 Apagar a cópia inline de `currencyOf` em `DashboardComponentsBuilder` e consumir a
      da `api`; verificar que `List<Recurring>.moneyByCurrency()` passa a dobrar sobre a
      versão única, sem reimplementar a regra
- [ ] 1.4 Rodar a suíte do dashboard e a de recorrentes: a mudança de casa é comportamento
      idêntico e nenhum teste deve mudar de expectativa

## 2. Razão — a leitura do que os ciclos confirmados lançaram (D6, D7)

- [ ] 2.1 Conferir como o Room persiste `RecurringOccurrence.Status` — não há converter em
      `Converters.kt` — e fixar o literal que o `WHERE` da consulta vai usar (Open Question)
- [ ] 2.2 Conferir nas migrações se a foreign key `transactionId` com `onDelete = CASCADE` de
      `RecurringOccurrenceEntity` está no schema v14 implantado; se não estiver, registrar o
      caso e reconciliar antes de seguir (Open Question)
- [ ] 2.3 Adicionar em `RecurringDao` a consulta que parte de `recurring_occurrences`, junta
      `transactions` pela foreign key de `transactionId`, `entries` e `accounts`, filtra por
      `yearMonth` e por ocorrência confirmada, e agrupa por tipo de conta nominal e por moeda
- [ ] 2.4 Declarar o membro correspondente em `IRecurringOccurrenceRepository`, no vocabulário
      do mês e da natureza, devolvendo por moeda, com KDoc explicando por que o corte é a
      ocorrência e não a data da transação (D7) e por que a leitura não toca
      `transactions.recurringId`
- [ ] 2.5 Implementar em `RecurringOccurrenceRepository`, convertendo centavos pela mesma via
      das demais leituras e tomando a magnitude do que a perna nominal registrou
- [ ] 2.6 Atualizar os fakes de `IRecurringOccurrenceRepository` nos testes; os que não
      exercitam a leitura lançam `NotImplementedError`
- [ ] 2.7 Teste: um ciclo confirmado com valor sobrescrito soma o valor **da transação**, não
      o do template
- [ ] 2.8 Teste: apagar a transação de um ciclo confirmado remove a ocorrência por cascade, e
      a leitura deixa de somar aquele valor
- [ ] 2.9 Teste: uma ocorrência ignorada não entra em nenhuma das duas naturezas
- [ ] 2.10 Teste: dois ciclos confirmados em moedas diferentes devolvem uma linha por
      (natureza, moeda)

## 3. Domínio — o dono da leitura do mês (D8)

- [ ] 3.1 Definir em `feature/recurring/api` o modelo de resultado do panorama do mês: as duas
      metades cruas por moeda (lançado e não lançado, cada uma em despesa e receita), as
      contagens de tratados, total e ignorados, e o número de templates deixados fora da soma
- [ ] 3.2 Criar o use case que compõe as duas metades: a projeção consumindo
      `GetUnhandledRecurringUseCase` — o mês inteiro, sem o corte por dia — e o fato
      consumindo a leitura da tarefa 2
- [ ] 3.3 Denominar cada template da projeção pela `currencyOf` da tarefa 1, contando os que
      não têm moeda em vez de descartá-los em silêncio
- [ ] 3.4 Registrar o use case no `RecurringModule`
- [ ] 3.5 Teste: um template não tratado com dia de ciclo futuro entra na projeção do mês
- [ ] 3.6 Teste: um template com ocorrência no mês — confirmada ou ignorada — não entra na
      projeção
- [ ] 3.7 Teste: um template arquivado não entra na projeção, e o dinheiro que ele confirmou
      antes de ser arquivado permanece no fato do mês
- [ ] 3.8 Teste: um template sem conta resolvível não entra na soma e é contado como fora dela
- [ ] 3.9 Teste: o contador conta o ciclo ignorado como tratado, e o declara separadamente

## 4. Apresentação — as figuras consolidadas (D9, D13, D14)

- [ ] 4.1 Criar `RecurringSummaryFactory` em `feature/recurring/impl`, nos moldes de
      `BalanceOverviewFactory`: `internal suspend fun` recebendo `ConsolidateMoneyUseCase`
      como parâmetro e devolvendo as quatro figuras já consolidadas mais as contagens
- [ ] 4.2 Consolidar cada figura com política de magnitude, com KDoc registrando que a
      ausência de sinal decorre de o card não exibir total, e que a regra se inverte se um
      total for acrescentado (D4 do spec de resumo)
- [ ] 4.3 Consolidar as figuras do fato com as taxas do mês selecionado, e não as de hoje
- [ ] 4.4 Teste: as quatro figuras de um mês multimoeda sem taxa saem em termos e marcadas
      como aproximadas
- [ ] 4.5 Teste: um mês sem movimento devolve zero denominado pelo redutor nas duas figuras
      do fato, e não figura ausente

## 5. Estado da tela (D9, D10)

- [ ] 5.1 Acrescentar o mês selecionado a `RecurringUiState.Content` e a ação que o troca
- [ ] 5.2 Acrescentar o panorama consolidado ao `Content`, carregando figuras e contagens já
      resolvidas — sem modelo de domínio como campo
- [ ] 5.3 Reescrever o `combine` do `RecurringViewModel` para as cinco fontes: templates,
      ocorrências, filtro, mês e `observeLedgerChanges()`, com KDoc explicando por que a
      última não é opcional
- [ ] 5.4 Resolver as moedas dos templates **uma vez por emissão**, num mapa compartilhado
      entre a lista e o resumo, eliminando a chamada item a item de hoje
- [ ] 5.5 Teste de view model: trocar o filtro não altera nenhuma das quatro figuras nem o
      contador
- [ ] 5.6 Teste de view model: trocar o mês altera as figuras e o contador e não altera a
      lista
- [ ] 5.7 Teste de view model: base sem recorrência alguma continua emitindo o estado que
      oferece a criação da primeira, sem panorama

## 6. Strings (nos dois idiomas)

- [ ] 6.1 Acrescentar as chaves dos dois blocos do resumo (lançado no mês, ainda não lançado)
      em `values/strings.xml` e `values-en/strings.xml`
- [ ] 6.2 Acrescentar as chaves dos rótulos das quatro figuras — despesas fixas e receitas
      fixas — distintas das de tipo de lançamento que já existem, porque declaram o que está
      dentro do número
- [ ] 6.3 Acrescentar a chave do contador de ciclos tratados, e os plurais de ciclos ignorados
      e de pendentes
- [ ] 6.4 Acrescentar o plural que declara quantos templates ficaram fora da soma, sem reusar
      a copy de figura aproximada por falta de taxa (D13)
- [ ] 6.5 Remover `recurring_card_monthly_amount` dos dois arquivos, junto com o seu único
      consumidor
- [ ] 6.6 Conferir a paridade de chaves entre os dois arquivos ao final

## 7. A linha da lista (D1, D2, D3, D4, D5)

- [ ] 7.1 Reescrever `RecurringCard` como grade 2×2: esquerda com rótulo e origem, direita com
      figura e dia; padding 12dp, raio 12dp, chip de 40dp com raio 8dp
- [ ] 7.2 Substituir o badge de tipo pelo glifo de direção a 16dp, com `contentDescription`
      reusando `recurring_expense`/`recurring_income`
- [ ] 7.3 Substituir o badge de arquivada pelo glifo antes do rótulo, também com
      `contentDescription`
- [ ] 7.4 Dar `weight(1f, fill = false)` ao rótulo e nunca à figura
- [ ] 7.5 Renderizar a marca de valor irresolvível quando o template não tem moeda, mantendo a
      tag `recurring_card_amount` **no `Text`** e nunca num container
- [ ] 7.6 Afirmar a origem inutilizável por glifo e texto, e não só pela troca de tom — o bug
      de acessibilidade que a tela carrega hoje
- [ ] 7.7 Remover a legenda "Valor mensal" e o nome da categoria da linha
- [ ] 7.8 Conferir que todas as variantes — com categoria, sem categoria, arquivada, sem
      denominação, rótulo longo, conta e cartão — têm a mesma altura

## 8. O card de resumo (D11, D12, D14)

- [ ] 8.1 Declarar `feature:settings:api` em `feature/recurring/impl/build.gradle.kts`, sem o
      que `EveryFigureCanExplainItselfTest` quebra o build
- [ ] 8.2 Compor o card: chip de mês, dois blocos rotulados com duas figuras cada, e o rodapé
      com o contador
- [ ] 8.3 Copiar o chip de período dos ~25 linhas de `SummaryCard`, sobre o
      `MonthPickerDropdownMenu` público de `core/designsystem` (D12)
- [ ] 8.4 Exibir **um** `ConsolidationBadge` para o card inteiro, com `onSeeRates` levando ao
      arquivo de taxas
- [ ] 8.5 Exibir a menção a ciclos ignorados e a de templates fora da soma apenas quando
      houver — anotação condicional é ausente, não zero
- [ ] 8.6 Dar ids próprios às figuras do resumo, distintos de `recurring_card_amount`
- [ ] 8.7 Diferenciar fato de projeção pela legenda do bloco, pela ordem e por um degrau de
      tamanho, mantendo a cor de natureza nas quatro figuras

## 9. A estrutura da tela (D11)

- [ ] 9.1 Mover o card e o vazio de recorte para dentro do `LazyColumn`, como itens com chave,
      no lugar dos ramos exclusivos em `fillMaxSize`
- [ ] 9.2 Manter o vazio de base ocupando a tela, com a oferta de criar a primeira recorrência
      intacta
- [ ] 9.3 Conferir que o recorte de arquivadas numa base sem arquivadas exibe o card e a
      mensagem de recorte vazio abaixo dele

## 10. Verificação

- [ ] 10.1 `./gradlew jvmTest` verde, com atenção a `EveryFigureCanExplainItselfTest`
- [ ] 10.2 Conferir que as cinco asserções de `recurring_card_amount` nos fluxos de
      recorrentes continuam válidas — a figura permanece sem sinal e a tag permanece num nó de
      texto
- [ ] 10.3 Rodar `.maestro/flows/recurring/` no AVD que o `.maestro/README.md` §2 exige,
      relatando em qual dispositivo a execução aconteceu
- [ ] 10.4 Conferir a tela no painel estreito de janela larga, onde a coluna direita da grade
      2×2 é a que mais sofre
- [ ] 10.5 Decidir, com o card na tela, entre a variante completa e a compacta (Open Question)
