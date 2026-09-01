---
area: transversal
severity: high
type: crash
---

# Uma leitura que falha mata o processo no Android em vez de virar estado de tela

## Invariante

Toda falha de leitura chega à tela como estado, e nenhuma delas termina o processo.

Hoje é falso em **todos os pipelines de `stateIn`**: não existe um único `Flow.catch` no
projeto fora de `feature/support` — a única ocorrência é
`FirebaseSupportRepository.observeIssues()`. Nenhum dos `UiState` afetados declara um
`Error` alcançável por falha de leitura: `BudgetsUiState`, por exemplo, tem
`Loading`/`Empty`/`Content` e mais nada.

## Mecânica

O bloco de transformação do `combine` roda dentro do fluxo *upstream*, então uma suspensão
que lança ali propaga pela coleta. `SharingStarted` não intercepta nada, e `viewModelScope`
usa `SupervisorJob` — justamente porque o pai recusa a falha, ela vai para
`handleCoroutineException`; sem `CoroutineExceptionHandler` no contexto, cai no
`uncaughtExceptionHandler` da thread, que no Android é o `KillApplicationHandler` do
`RuntimeInit`.

Os candidatos a lançar são as chamadas suspensas de Room feitas *dentro* dos blocos —
`BudgetsViewModel` chama `calculateBudgetProgressUseCase(...)` ali, que por sua vez lê
`entryRepository.dimensionBalancesInMonthByCurrency(...)`.

*O sintoma é dependente de plataforma: em Desktop/JVM o mesmo caminho apenas imprime o
stack trace e o `StateFlow` congela no `initialValue`.*

## Evidência

- `feature/budgets/impl/.../budgets/BudgetsViewModel.kt` — `uiState`, `combine { … }` com
  `calculateBudgetProgressUseCase(...)` dentro e `.stateIn(...)` sem `.catch`
- `feature/budgets/impl/.../budgets/BudgetsUiState.kt` — `Loading`/`Empty`/`Content`, sem `Error`
- `feature/dashboard/impl/.../dashboard/DashboardViewModel.kt` — `viewingState`, o `combine`
  de nove fontes que executa `buildDashboardViewingUseCase(...)`
- `feature/support/impl/.../repository/FirebaseSupportRepository.kt` — o **único** `.catch`
  do projeto, o que torna a ausência um padrão e não um esquecimento
- `grep -rn "\.catch" --include='*.kt' feature core app` — uma ocorrência

## Consequência

Uma falha transitória de leitura fecha o app sem mensagem, em telas de consulta que não
estavam escrevendo nada — e o usuário não tem como distinguir isso de um travamento.

## Sugestão

Um `.catch` antes de cada `stateIn`, emitindo um estado de erro da própria tela. Fechar isso
pede decidir o que cada `UiState` mostra quando não pôde ler; um estado de erro compartilhado
resolveria todos de uma vez. Não vinculante.
