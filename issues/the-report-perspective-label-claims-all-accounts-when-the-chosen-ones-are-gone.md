---
area: report
severity: low
type: ux
---

# O rótulo do relatório diz "todas as contas" quando as contas escolhidas deixaram de existir

## Cenário

**DADO** um relatório aberto com perspectiva de duas contas escolhidas
**QUANDO** essas contas são excluídas definitivamente com o relatório ainda em tela
**ENTÃO** o cabeçalho passa a nomear **todas** as contas do usuário, enquanto os números
continuam vindo do escopo escolhido — que agora não existe, e portanto é zero
**DEVERIA** dizer que o escopo do relatório não existe mais, e não nomear um escopo que não é
o que foi somado

## Mecânica

O rótulo trata "nenhum nome resolvido" como se fosse "nenhum id escolhido":

```kotlin
accounts.filter { it.id in perspective.accountIds }
    .joinToString(", ") { it.name }
    .takeIf { it.isNotBlank() } ?: accounts.joinToString(", ") { it.name }
```

O fallback é correto para `accountIds` vazio, que significa legitimamente "todas" no resto do
arquivo. Ele é errado para `accountIds` **não** vazio cujos ids não resolvem — os dois casos
chegam ao `?:` indistinguíveis, porque o `filter` devolve vazio nos dois.

O filtro das transações não faz essa confusão: ele testa `perspective.accountIds.isEmpty()`
antes de comparar, então com ids mortos nenhuma transação passa e os totais ficam em zero.

*Alcançabilidade baixa: as leituras usam `getAllAccountsIncludingClosed()`, então arquivar não
dispara o caso — só a exclusão definitiva, com o relatório aberto.*

## Evidência

- `feature/report/impl/.../viewer/ReportViewerViewModel.kt` — `perspectiveLabel`, o
  `takeIf { it.isNotBlank() } ?: accounts.joinToString(...)`
- mesmo arquivo — o filtro das transações, `perspective.accountIds.isEmpty() || it.account.id in perspective.accountIds`,
  que distingue os dois casos
- `feature/report/impl/.../usecase/CalculateReportCategorySpendingUseCase.kt` —
  `perspective.accountIds.ifEmpty { … }`, a mesma distinção do lado das categorias

## Consequência

Um documento que se exporta afirmando um perímetro que não é o dos seus números.

## Sugestão

Ramificar por `perspective.accountIds.isEmpty()` antes de resolver nomes, como as duas outras
leituras já fazem. Não vinculante.
