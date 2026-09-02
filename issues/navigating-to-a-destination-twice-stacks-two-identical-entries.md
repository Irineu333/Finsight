---
area: transversal
severity: low
type: navigation
---

# Dois toques no mesmo card empilham duas entradas idênticas

## Invariante

Um segundo toque num destino que já está no topo não empilha uma segunda entrada dele.

Hoje é falso em toda navegação de feature: das 34 chamadas de `navigate(...)` do projeto, a
**única** que passa opções é a troca de aba do `ChromeHost` (`popUpTo` + `launchSingleTop`).
Nenhuma das demais tem `launchSingleTop`, e nenhuma tela guarda o clique enquanto a transição
roda.

## Mecânica

A animação de entrada dura alguns frames, durante os quais o card de origem continua clicável
e continua na composição. O segundo toque chama `navigate` de novo com uma rota idêntica —
`SupportIssueRoute(issueId)` com o mesmo id, `AccountsRoute(accountId)` com o mesmo id — e a
back stack passa a ter duas entradas iguais.

## Evidência

- `feature/support/impl/.../navigation/SupportGraph.kt` —
  `onOpenIssue = { issueId -> navController.navigate(SupportIssueRoute(issueId)) }`, sem opções
- `feature/dashboard/impl/.../dashboard/DashboardComponentContent.kt` — as navegações para
  `AccountsRoute(accountId = …)`, `TransactionsRoute(...)` e `ExchangeRatesRoute`, idem
- `feature/transactions/impl/.../viewTransaction/ViewTransactionModal.kt` — idem
- o único com opções: `feature/shell/impl/.../home/ChromeHost.kt` —
  `navigate(item.route) { popUpTo(...); launchSingleTop = true }`

## Consequência

O usuário precisa apertar "voltar" duas vezes para sair de uma tela em que entrou uma vez.
Em janela compacta, onde o toque abre uma rota em vez do painel de detalhe, é o caso mais
fácil de reproduzir.

## Sugestão

`launchSingleTop = true` nas navegações que abrem um destino identificável. Não vinculante.
