## Context

Dois caminhos independentes produzem o mesmo tipo de detalhamento, com perímetros diferentes:

```
       DASHBOARD (mês)                          RELATÓRIO (período + perspectiva)
  categoryTotals()                          CalculateReportCategorySpendingUseCase
  CalculateCategorySpendingUseCaseImpl:41                 :45 / :65
       │                                                  │
       │ N leituras, uma por categoria:                   │ 1 leitura agregada:
       ▼                                                  ▼
  dimensionBalanceInMonthByCurrency          totalsByDimensionByCurrency
       (month, dimensionId)                   (nominalType, range, siblings)
       │                                                  │
       │ perímetro: todas as entries                      │ Map<Long?, MoneyByCurrency>
       │ do mês com aquela dimensão                       │      ▲
       │                                                  │      └── a chave `null` já é
       ▼                                                  ▼          o não classificado
  List<CategorySpending> ◄─────────────────────  descartada em :91
       │                                          ("resolves to no category and drops out
       ▼                                            here, exactly as the uncategorized
  CategorySpendingCard (core/ui)                    bucket account used to")
```

Restrições que o desenho tem de respeitar, todas verificadas no código atual:

- `CategorySpending.category: Category` é não-nulo (`core/model/.../CategorySpending.kt:14`) e é
  consumido em três lugares de produção: `CategorySpendingCard.kt:123,141,182`,
  `DashboardComponentContent.kt:690,714` e `ReportExportLayout.kt:113,128`.
- `ConsolidateMoneyUseCase.comparativeMagnitudes` é declarado `<K : Any>`
  (`ConsolidateMoneyUseCase.kt:170`). A chave da família de figuras **não pode ser nula**.
- `core/ledger/README.md` e `CLAUDE.md`: "Uncategorized is the *absence* of a dimension, never a
  bucket account". As contas `SystemAccount.UNCATEGORIZED_EXPENSE/INCOME` já foram extintas
  (`openspec/changes/archive/2026-07-21-ledger-single-source/design.md:376`).
- `TransactionsFilters.category: Category?` usa `null` para *"sem filtro"*
  (`TransactionsFilters.kt:8`) — não há hoje como filtrar transações por "sem categoria".

## Goals / Non-Goals

**Goals:**
- Exibir o total sem classificação nos quatro detalhamentos existentes (despesa e receita, dashboard
  e relatório) e na exportação HTML.
- Fazê-lo participar do denominador, para que as fatias exibidas descrevam o período inteiro.
- Dar à regra de ordenação do detalhamento um único dono, já que ela passa a ter uma cláusula nova.
- Reduzir o custo de leitura do dashboard de N queries para uma.

**Non-Goals:**
- Filtrar a lista de transações por "sem categoria" — exigiria tornar `TransactionsFilters.category`
  tri-estado, o que é mudança de outra feature. Sem isso, a linha não é clicável nesta entrega.
- Categorização automática, sugestão de categoria ou qualquer nudge além de exibir o número.
- Orçamentos: `BudgetProgressCard` é outro widget, com outra semântica, e não entra aqui.
- Qualquer mudança na doutrina de consolidação — o não classificado usa o redutor existente, tal
  como está.

## Decisions

### D1 — O total sem classificação entra na escala comparativa

Ele é passado ao `comparativeMagnitudes` junto com as categorias, na mesma chamada, com as mesmas
taxas e a mesma data. Logo é denominador, e as porcentagens das categorias mudam para quem tem gasto
sem classificar.

**Alternativa considerada:** exibir a linha fora da escala, sem barra, como já acontece quando falta
taxa (`CategorySpendingCard.kt:175`). Rejeitada por duas razões. A primeira é que as fatias
continuariam somando 100% de um todo falso — exatamente o defeito que motiva a mudança. A segunda é
operacional: "barra ausente" já significa *"falta taxa de câmbio"* e é explicada pelo
`ConsolidationBadge` no canto do card; um segundo motivo para a mesma ausência faria o badge mentir.

### D2 — A linha é fixada por último, e isso é regra de domínio

A ordenação é `magnitude decrescente`, com `Uncategorized` sempre ao fim, e "sem magnitude" por
último dentro do seu grupo (o `NEGATIVE_INFINITY` que já existe).

Fixar a posição na UI resolveria o card e **não** resolveria a exportação HTML, que monta a sua
própria lista (`ReportExportLayout.kt:113,128`). Como a regra já é decidida no domínio hoje
(`sortedByDescending` nos dois use cases), ela continua lá — e ganha um dono só.

### D3 — A linha só existe quando é diferente de zero

Mesma regra que já descarta uma categoria sem movimento
(`CalculateCategorySpendingUseCaseImpl.kt:48`), aplicada ao balde. Um período inteiramente
classificado produz o detalhamento idêntico ao de hoje — o que é também a garantia de regressão mais
barata que esta mudança tem.

### D4 — Tipo-soma `SpendingSubject`, não `Category?`

```kotlin
sealed interface SpendingSubject {
    data class Categorized(val category: Category) : SpendingSubject
    data object Uncategorized : SpendingSubject
}
```

Não é preferência estética: `comparativeMagnitudes<K : Any>` recusa uma chave nula, e D1 exige que o
balde seja uma chave. O compilador está dizendo a coisa certa — se o não classificado participa do
todo, ele é um valor do eixo, não a ausência de um.

`data object` dá identidade e `hashCode` estáveis, o que uma chave de mapa precisa. E o `when`
exaustivo obriga cada uma das três superfícies a decidir conscientemente rótulo, ícone e clique, em
vez de descobrir por omissão.

**Alternativa considerada:** semear uma `Category` de sistema com `systemKey = UNCATEGORIZED`,
seguindo o precedente de `EnsureYieldCategoryUseCase`. Seria de longe o caminho mais barato — nenhum
tipo novo, nenhuma query nova, tudo cai na trilha existente. Rejeitada porque reintroduz pela porta
dos fundos o balde que o razão extinguiu, e porque a categoria resultante seria renomeável e
apagável pelo usuário, além de reclassificar histórico no momento da escrita.

### D5 — Uma leitura agregada mensal nova para o dashboard

O dashboard não tem por onde ver o balde: ele itera categoria a categoria. E **não dá para
reaproveitar o agregado do relatório** — `totalsByDimensionWithSiblingLeg` exige `siblingAccountIds`
(`EntryDao.kt:499`), e passar "todas as contas ASSET" derrubaria a despesa de cartão, cuja perna
irmã é a conta `LIABILITY` do cartão. Perímetros diferentes, e a diferença é silenciosa.

A query nova espelha `dimensionBalanceInMonth` (`EntryDao.kt:260-266`), trocando a dimensão fixa pelo
agrupamento e pelo filtro de natureza:

```sql
SELECT e.dimensionId AS dimensionId, e.currency AS currency,
       COALESCE(SUM(e.amount), 0) AS total
FROM entries e
JOIN transactions o ON o.id = e.transactionId
JOIN accounts a ON a.id = e.accountId
WHERE a.type = :nominalType AND substr(o.date, 1, 7) = :yearMonth
GROUP BY e.dimensionId, e.currency
```

→ `IEntryRepository.totalsByDimensionInMonthByCurrency(month, nominalType): Map<Long?, MoneyByCurrency>`

O filtro `a.type = :nominalType` é **obrigatório**, não uma otimização: sem ele, `dimensionId IS NULL`
casaria com toda perna não classificada do razão — ativo, passivo, conversão — e o número seria
lixo. De brinde, as N leituras do dashboard viram uma.

Por que o balde nominal é de fato "gasto sem categoria", conferido contra a doutrina do razão:

| Caso | Onde cai | Entra? |
|---|---|---|
| despesa comum sem categoria | perna `EXPENSE` sem dimensão | ✅ |
| despesa de cartão sem categoria | perna `EXPENSE` sem dimensão (a da fatura pousa na `LIABILITY`) | ✅ |
| resíduo de câmbio | conta `CONVERSION`, tipo próprio | ❌ |
| ajuste de saldo / reconciliação | `EQUITY` | ❌ |
| perna de ativo da mesma despesa | `ASSET` | ❌ (não duplica) |

### D6 — Um construtor único do detalhamento

`CalculateCategorySpendingUseCaseImpl.kt:60-75` e `CalculateReportCategorySpendingUseCase.kt:108-125`
são hoje a mesma trintena de linhas: mesmo `comparativeMagnitudes`, mesmo `sortedByDescending`, mesmo
`NEGATIVE_INFINITY`, mesmo `shareOf * 100`. D2 acrescenta uma cláusula a essa regra; escrevê-la duas
vezes seria a terceira duplicação, e é a terceira que estraga.

```kotlin
// core/model — domain/usecase, ao lado do próprio ConsolidateMoneyUseCase
suspend fun ConsolidateMoneyUseCase.spendingBreakdown(
    totals: Map<SpendingSubject, MoneyByCurrency>,
    displaySign: Int,
    on: LocalDate,
): List<CategorySpending>
```

Cada produtor monta o mapa do seu jeito — perímetros continuam diferentes, e é assim que devem ser —
e delega sinal, escala, ordenação, descarte de zeros e cálculo da fatia. É o que a regra de derivação
do `CLAUDE.md` pede: uma regra derivável do domínio tem exatamente um dono.

### D7 — Dimensão órfã continua sendo descartada

Se um `dimensionId` não-nulo não resolver para categoria alguma, ele **não** vira "sem categoria":
some, como hoje. Isso é impossível por construção — `hasEntriesForDimension` impede apagar categoria
com movimento, ela é arquivada — e se acontecer é falha de integridade. Lavar um bug dentro de um
balde legítimo esconde justamente o que se quer ver.

### D8 — O rótulo é resolvido na apresentação

`SpendingSubject.Uncategorized` não carrega texto. O card faz `when` e chama `stringResource`; a
exportação recebe o texto já resolvido em `ReportExportStrings`, que é o saco de strings que
`toReportLayout` já aceita (`ReportExportLayout.kt:50`) — o que mantém a exportação fora do mundo
`@Composable`, como está hoje.

### D9 — A linha não é clicável nesta entrega

`onCategoryClick(Category)` navega para o modal da categoria por id, e o balde não tem id. O destino
natural — transações filtradas por "sem categoria" — esbarra em `TransactionsFilters.category: Category?`,
onde `null` já significa "sem filtro" (`TransactionsFilters.kt:8`).

O callback do card passa a ser `onSubjectClick(SpendingSubject)`, com o ramo `Uncategorized`
inerte. A assinatura já fica pronta para o passo seguinte, e a mudança de outra feature não é
arrastada para dentro desta.

## Risks / Trade-offs

- **As porcentagens mudam para usuários existentes, sem aviso** → é o efeito pretendido (D1) e
  nenhum valor monetário se move; o `ReportViewerViewModelCharacterizationTest` captura os números
  atuais e deve ser **reafirmado linha a linha**, com o novo valor conferido à mão, nunca regenerado
  em massa — regenerar transforma o teste de caracterização em carimbo.
- **Perímetro errado no dashboard** (usar o agregado com `siblingAccountIds` e perder cartão) → D5
  cria uma leitura própria em vez de reaproveitar; teste dedicado com despesa de cartão sem
  categoria.
- **`dimensionId IS NULL` sem filtro de natureza somaria o razão inteiro** → o filtro é parte da
  assinatura e da spec (`ledger-reporting`), não uma cláusula opcional da query; teste com resíduo
  de `CONVERSION` presente.
- **Um usuário com muito gasto sem categoria vê todas as suas fatias encolherem e pode ler isso como
  bug** → mitigado pela linha ser nomeada, visível e visualmente distinta; a mitigação real é D9
  virar entrega, dando à linha um destino.
- **D6 amplia o diff além do mínimo necessário** → é a única decisão de escopo maior aqui, e o
  ganho é que a cláusula nova de ordenação tem um dono só; se precisar encolher, ela é a primeira a
  sair, ao custo de duplicar a regra nos dois use cases.
- **Um período multi-moeda em que o balde não tem taxa apaga as barras de todas as linhas** →
  comportamento já documentado em `CategorySpending.percentage` e explicado pelo
  `ConsolidationBadge`; não é regressão, mas passa a ser alcançável por um caminho novo, e merece
  teste.

## Migration Plan

Nada é persistido: todo número aqui é derivado das entries. **Sem migração de banco, sem alteração
de esquema, sem backfill.** A reversão é o `revert` do commit, e nenhum dado do usuário fica
inconsistente no caminho.

## Resolved Questions

### Q1 — O par ícone/cor da linha sem classificação — **fechado**

`colorScheme.onSurfaceVariant`, não `outline`. O parecer do `ux-ui-designer`: `outline` é
dimensionado para bordas e traços, e o seu contraste contra `surfaceContainer` fica perto do limite
não-textual (~3:1); `onSurfaceVariant` é o token de *conteúdo secundário legível*, com contraste
garantido nos dois temas, que é exatamente o papel semântico pretendido — "existe, mas é
secundário", não "decorativo". `surfaceVariant` está descartado por ser token de superfície, e
`outlineVariant` por ser feito para quase desaparecer.

Aplicado em três lugares:

- fundo do `CategoryIconBox`: `onSurfaceVariant.copy(alpha = 0.12f)` — mais baixo que os `0.2f` das
  categorias reais, porque aqui a intenção é neutralidade e não uma cor de identidade. O alfa virou
  parâmetro do componente (`containerAlpha`, com o `0.2f` de antes como padrão) em vez de uma
  segunda cópia da caixa;
- ícone: `Icons.Outlined.Category`, tintado com `onSurfaceVariant` puro. `HelpOutline` e
  `QuestionMark` comunicam erro, e não classificar é estado legítimo; `MoreHoriz` já significa
  *overflow* e prometeria um toque que a linha não tem; `Label` sugere justamente a etiqueta que
  falta. Sendo `Outlined` ao lado dos `Filled` das categorias reais, o próprio peso do traço já é um
  segundo sinal de diferença;
- barra: `color = onSurfaceVariant` sobre o mesmo `trackColor = surfaceContainerHighest` das demais.

O separador acima da linha está aprovado, com `colorScheme.outlineVariant` — o token certo para
divisores — na espessura padrão, com o mesmo `padding(horizontal = 16.dp)` dos itens (para ler como
separador de conteúdo, não como borda estrutural do card) mais `padding(vertical = 4.dp)`, de modo
que o intervalo antes da linha fique um pouco maior que entre duas categorias. Só o espaçamento
maior, sem a linha, foi rejeitado: com uma ou duas categorias no card ele passa despercebido.

A ausência de `clickable` basta para dizer que a linha não é um caminho — no Material a presença do
*ripple* já é essa linguagem. Nada de *chevron*, opacidade de desabilitado ou `Role.Button`: o dado
é válido, apenas não navegável nesta entrega (D9).

### Q2 — `testTag` próprio para o Maestro — **não, nesta entrega**

A linha não ganha tag própria. O valor dela já é alcançável pelo `category_spending_amount` que toda
linha do card publica e que `flows/categories/lifecycle.yaml` e `flows/budgets/lifecycle.yaml` já
leem; nenhum fluxo do `.maestro/` exercita hoje um período com movimento sem classificação, e uma
tag que fluxo algum lê é peso morto. Quando um fluxo precisar isolar a linha, a tag é uma linha de
código — e terá de ser acompanhada da raiz de composição que chama `Modifier.exposeTestTags()`.
