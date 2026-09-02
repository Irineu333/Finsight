---
area: recurring
severity: high
type: crash
---

# Recorrência com título só de espaços derruba toda tela que a nomeia

## Cenário

**DADO** uma recorrência gravada antes de `c48076142` com o título digitado só com
espaços (`"   "`) e sem categoria — aceita porque a regra da época exigia
`title.isNotEmpty()`
**QUANDO** o usuário abre a tela de recorrências
**ENTÃO** `Recurring.label` lança `IllegalStateException` durante a composição da lista,
e a tela não abre
**DEVERIA** nomear a recorrência, ou não ter gravado a linha

**DADO** a mesma recorrência, e uma transação que ela gerou
**QUANDO** o usuário abre o detalhe dessa transação
**ENTÃO** a exceção sai antes de qualquer leitura de nome: `TransactionRecurring` avalia
o `label` no construtor, e quem o constrói é a resolução de facades de toda transação que
carrega `recurringId` e `recurringCycle`
**DEVERIA** abrir o detalhe

## Mecânica

A regra que decide quando uma recorrência tem nome trocou de lado no meio do caminho.
Até `c48076142` o dono dela — `RecurringForm.toRecurring()` — exigia `isNotEmpty` e
gravava sem aparar, enquanto toda leitura já descartava branco (`displayTitleOrNull`,
com `isNotBlank`). Um título de espaços satisfazia o dono e falhava em quem o lia.

`c48076142` alinhou os dois lados — o dono passou a exigir `isNotBlank` e a gravar
`title.trim()` — e no mesmo commit trocou o que a leitura faz com a ausência:
`Recurring.label` era `?: "Untitled"` e virou `?: error(...)`, afirmando o invariante que
o dono garante.

O alinhamento vale só para o que se grava daqui em diante. A linha que a regra antiga
deixou passar continua onde estava, e agora encontra uma leitura que a recusa em vez de
mascará-la.

## Evidência

- `RecurringForm.toRecurring()` — hoje `isNotBlank` + `title.trim()`; era `isNotEmpty`
  sem aparar desde `5013e7055` até `c48076142`
- `RecurringFormViewModel.submit()` — `form.title.ifEmpty { null }`: `"   "` não é vazio
  e segue adiante intacto
- `SaveRecurringUseCase.invoke()` — repassa com `title.orEmpty()`, sem aparar
- `displayTitleOrNull()` — `takeIf { it.isNotBlank() }`, o único lado que nunca mudou
- `Recurring.label` — `?: error("A recurring has a title or a category…")`
- `TransactionRecurring.label` — `val` de construtor, não `get()`: lança ao construir
- `LedgerTransactionFacadeResolver.resolve()` — constrói `TransactionRecurring` para toda
  transação com `recurringId` e `recurringCycle`
- `RecurringScreen` — renderiza `recurring.label` direto num `Text` da lista
- nenhuma migração aparia títulos: não há `TRIM` em `core/database/.../Migration*.kt`

## Consequência

Não se sai pela UI. A recorrência ofensora só se apaga pela lista, que é justamente a
tela que não abre; e as transações que ela já gerou não abrem o detalhe. Sem editar o
banco, a linha fica lá derrubando o que a nomeia.

O defeito de código está provado pelas âncoras acima. *O que não dá para verificar daqui
é se algum banco real carrega uma linha assim: exige que alguém tenha digitado só espaços
no título e não escolhido categoria, antes de `c48076142`.*

*Hipótese, não verificada: escapando do `mapLatest` de `ViewTransactionViewModel` sem
`catch`, a exceção alcança o `viewModelScope` e derruba o processo em vez de virar
estado — o padrão que `a-failed-read-kills-the-android-process-instead-of-reaching-a-state`
registra.*

## Sugestão

Uma linha na próxima migração, sobre a tabela e a coluna que `RecurringEntity` declara:

```sql
UPDATE recurring SET title = NULL WHERE title IS NOT NULL AND TRIM(title) = ''
```

Não vinculante — quem corrige decide.
