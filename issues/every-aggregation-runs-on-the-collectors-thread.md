---
area: transversal
severity: low
type: performance
---

# Toda agregação roda na thread do coletor

## Invariante

O trabalho que percorre N transações ou N contas roda fora da main thread.

Hoje é falso em todo o app: `grep -rn "flowOn" --include='*.kt' feature core app` devolve
**zero** ocorrências. O bloco de transformação de um `combine` executa no contexto do
coletor, e o coletor é a corrotina de *sharing* de `stateIn(viewModelScope, …)`, ou seja
`Dispatchers.Main.immediate`. As chamadas de Room saltam internamente para o dispatcher do
banco; o trabalho Kotlin puro que vem depois delas, não.

## Mecânica

O caso mais caro é o dashboard: `DashboardViewModel.viewingState` combina **nove** fontes e
executa `buildDashboardViewingUseCase(...)` inteiro dentro do bloco. Qualquer emissão de
qualquer uma das nove reexecuta o construtor completo — incluindo a ordenação da lista
inteira de transações antes do `take(n)` — na main thread.

## Evidência

- `feature/dashboard/impl/.../dashboard/DashboardViewModel.kt` — `viewingState`: o `combine`
  de nove fontes chamando `buildDashboardViewingUseCase(...)`, com `.stateIn(viewModelScope, …)`
- `feature/dashboard/impl/.../DashboardComponentsBuilder.kt` — `.sortedByDescending { it.date }`
  sobre a lista completa de transações
- `feature/report/impl/.../viewer/ReportViewerViewModel.kt` —
  `.sortedByDescending { it.date }.groupBy { it.date }`
- `feature/budgets/impl/.../budgets/BudgetsViewModel.kt` — `calculateBudgetProgressUseCase(...)`
  dentro do `combine`

## Consequência

Frames perdidos proporcionais ao tamanho do razão, em toda escrita — que é justamente quando
a lista está maior. O custo cresce com o uso do app e não aparece em base de teste pequena.

## Sugestão

`.flowOn(Dispatchers.Default)` antes do `stateIn` nos pipelines que fazem trabalho de
agregação. Não vinculante.
