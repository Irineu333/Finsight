## Context

A tela de transações recorta a lista por categoria com um `TransactionsFilters.category:
Category?`, e o predicado é uma linha só no `TransactionsViewModel`:

```kotlin
private fun List<Transaction>.filter(category: Category?): List<Transaction> {
    if (category == null) return this
    return filter { it.nominalDimensionId == category.dimensionId }
}
```

`Category?` tem exatamente dois estados e os dois já estão tomados: `null` significa "não
recorta" e um valor significa "esta categoria". Não sobra onde dizer "sem categoria" — é por
isso que a capacidade não existe, e não por falta de dado: `Transaction.nominalDimensionId`
já é `null` justamente nesse caso.

O eixo, porém, já está modelado no domínio. `SpendingSubject` (`core/model`) é o tipo-soma
`Categorized | Uncategorized` que o detalhamento usa como chave, criado exatamente para que
o não classificado possa ser um valor e não uma ausência. O que falta é o filtro passar a
selecionar sobre ele.

Uma armadilha governa o resto do desenho: `nominalDimensionId == null` **não** significa "sem
categoria". Ele é `null` também quando não há perna nominal alguma — `nominalLeg()` é
`firstOrNull { it.account.type.isNominal }`, e transferência (duas pernas `ASSET`),
pagamento de fatura (`ASSET` + `LIABILITY`) e ajuste (`EQUITY`) não têm nenhuma. Recortar
por `nominalDimensionId == null` traria os três, e a lista responderia com transferências a
uma pergunta feita sobre o total de gastos sem categoria.

## Goals / Non-Goals

**Goals:**
- Tornar o não classificado um valor selecionável do filtro de categoria da tela de
  transações, com a mesma definição que o razão usa para compor o total sem classificação.
- Ter **um** dono para essa definição no domínio, consumível por qualquer superfície que
  venha a filtrar pelo mesmo eixo.
- Não mover nenhuma linha do resumo, nem alterar o recorte de nenhuma outra combinação de
  filtros existente.

**Non-Goals:**
- Estender o eixo aos outros filtros de categoria do app (parcelamentos, transações de
  fatura, cartões). O tipo passa a existir para eles; adotá-lo é outra mudança.
- Navegar do detalhamento para a lista já recortada. É o próximo passo natural, e depende
  desta; não faz parte dela.
- Qualquer alteração no razão, nas leituras por dimensão ou no detalhamento.

## Decisions

### D1 — O filtro guarda `SpendingSubject?`, não `Category?` mais um booleano

`TransactionsFilters.category: Category?` vira `subject: SpendingSubject?`, com `null` como
o estado neutro que não recorta. `TransactionsAction.SelectCategory` e
`TransactionsUiState.selectedCategory` acompanham.

Ficam três estados num campo só: neutro, uma categoria, o não classificado. A alternativa
óbvia — manter `Category?` e acrescentar `uncategorizedOnly: Boolean` — foi recusada porque
cria um estado impossível de ler (`category = Mercado` **e** `uncategorizedOnly = true`) que
alguém teria de resolver por convenção em cada consumidor. É a mesma razão pela qual a tela
já suprime o chip de conta/cartão quando o escopo decidiu: um eixo, um controle.

O estado neutro fica **fora** do tipo-soma (`SpendingSubject?`) em vez de ser um terceiro
valor dele. "Não estou recortando" não é um valor do eixo analítico — o detalhamento não tem
linha "todas" —, e enfiá-lo ali obrigaria o `SpendingBreakdown` a lidar com uma chave que
nunca ocorre.

### D2 — A pertinência ao eixo é uma derivação em `core/model`, com um dono só

Nasce `fun Transaction.matches(subject: SpendingSubject): Boolean` em `core/model`, ao lado
de `SpendingSubject`:

- `Categorized(c)` → `nominalDimensionId == c.dimensionId`
- `Uncategorized` → `hasNominalLeg && nominalDimensionId == null`

`core/model` é o lugar: ele já depende de `:core:ledger` e é onde o eixo está declarado. Em
`:core:ledger` não caberia — o razão não conhece `SpendingSubject`, que é modelo de facade.
E dentro da feature também não: seria a definição de "sem categoria" escrita pela terceira
vez no app (a consulta agregada do razão, o detalhamento, e agora o filtro), com duas delas
sem nada que as force a concordar.

O caso da **dimensão órfã** sai de graça, sem ramo próprio: uma perna nominal com dimensão
que não resolve para categoria alguma tem `nominalDimensionId != null`, logo não satisfaz
`Uncategorized` — e não satisfaz `Categorized(c)` de nenhuma categoria existente, porque
nenhuma tem aquele `dimensionId`. Ela simplesmente não aparece em recorte algum do eixo, que
é o que a regra pede.

### D3 — `Transaction.hasNominalLeg` entra no razão, junto com os seus irmãos

`Transaction` já expõe `hasLiabilityLeg` e `hasAssetLeg`; falta o terceiro fato da mesma
família. `val hasNominalLeg: Boolean get() = entries.any { it.account.type.isNominal }`.

É a peça que separa "não classificado" de "fora do eixo", e ela é do razão: fala de tipos de
conta e de nada mais. Deixá-la implícita em `core/model` (por exemplo testando
`entries.any { ... }` de lá) espalharia conhecimento do plano de contas para fora de quem o
define.

### D4 — O valor entra no fim do menu, separado das categorias

O item "sem categoria" é o último do menu suspenso, depois das categorias, com um separador.
É a mesma posição que o detalhamento fixa para a linha equivalente
(`uncategorized-spending-breakdown`), pela mesma razão: quem lê a lista de categorias não
deve encontrar no meio dela algo que não é uma. Pô-lo logo abaixo de "Todas" o aproximaria
visualmente do estado neutro, com o qual ele não tem parentesco — um recorta, o outro não.

O chip selecionado usa `FilterChipDefaults.filterChipColors()` sem cor de natureza, ao
contrário de uma categoria selecionada, que herda `IncomeColor`/`ExpenseColor` do
`Category.type`. O não classificado não declara natureza: emprestar-lhe uma seria inventar
estado.

### D5 — O rótulo reusa `category_spending_uncategorized`

A chave já existe nos dois idiomas ("Sem categoria" / "Uncategorized") e nomeia exatamente
este valor do eixo no detalhamento. Criar uma segunda chave para o mesmo conceito é o começo
de duas telas chamarem a mesma coisa por dois nomes — e o app tem uma regra explícita de que
o rótulo do não classificado é resolvido na apresentação a partir do valor do eixo, não uma
por superfície. Se algum dia o filtro precisar de texto próprio, a divergência será uma
decisão declarada, não um acidente de tradução.

### D6 — O parâmetro morto `category` do ViewModel é removido

`TransactionsViewModel` recebe `category: Category?`, `TransactionsScreen` chama
`parametersOf(categoryLabel, null, target)` e `TransactionsRoute` não carrega categoria
alguma: o parâmetro é sempre `null`. Ele sai, junto com o `getOrNull()` correspondente no
`transactionsModule`.

Repropô-lo como `SpendingSubject?` para uma futura navegação vinda do detalhamento foi
recusado: essa navegação está fora de escopo, e um parâmetro de rota mantido "para quando
precisar" é exatamente o que produziu o parâmetro morto que esta mudança está removendo.

## Risks / Trade-offs

- **O recorte exclui transferências, pagamentos e ajustes, e alguém pode ler isso como bug**
  ("lancei sem categoria e não apareceu") → é a decisão central e está escrita como requisito
  com cenário próprio. O usuário não *escolhe* deixar uma transferência sem categoria: a tela
  de lançamento nem oferece categoria para ela. Não há, portanto, expectativa a frustrar.
- **Renomear `category` → `subject` toca estado, ação e UiState de uma vez** → mudança
  mecânica, guiada pelo compilador (tipo-soma novo, nenhuma conversão implícita possível). O
  `TransactionsViewModelCharacterizationTest` existente é a rede: ele deve continuar passando
  sem alteração de comportamento nos recortes por categoria.
- **`matches` em `core/model` e a consulta agregada do razão continuam sendo duas
  implementações do mesmo critério** (uma em memória, uma em SQL) → não são unificáveis sem
  levar o eixo para dentro do razão, o que a arquitetura proíbe. Mitigação: teste que fixa a
  concordância nos casos que a distinguem — perna nominal sem dimensão entra, ausência de
  perna nominal não entra.
- **Fluxos Maestro que abrem o filtro por posição** → os itens são alcançados por `id:`, mas
  um item novo no fim do menu pode alterar rolagem. Verificar a suíte antes de dar a mudança
  por concluída.
