# 025 — `confirm_recurring` grava no razão um lançamento na direção errada

**Área:** recurring / mcp / ledger · **Tipo:** dados · **Criticidade:** ALTA · **Status:** aberto
**Verificado em:** 2026-08-19, `feature/local-mcp-server`, por uma revisão adversarial em quatro
lentes do commit `e58abf948`

## O que está errado

A regra "uma categoria classifica uma direção só" foi fechada nas cinco tools que montam um
formulário — [004](archive/004-transaction-form-drops-arguments-silently.md),
[016](archive/016-update-transaction-drops-the-category-silently.md),
[020](archive/020-create-installment-drops-the-category-silently.md),
[021](archive/021-update-recurring-stores-an-incoherent-template.md). `confirm_recurring` não monta
formulário nenhum, e é a **única escrita da superfície que chega ao razão sem um** — foi o recorte
que a escondeu de quatro rodadas de correção.

`feature/mcp/impl/.../tool/RecurringOperationTools.kt` não contém uma única ocorrência de
`isAccept`: a categoria é resolvida por identidade (`:133-138`) e entregue ao use case (`:158`).

## Evidência

A cadeia, inteira:

| Passo | Arquivo | O que faz |
|---|---|---|
| 1 | `RecurringOperationTools.kt:133-138` | resolve `category_id` só por identidade |
| 2 | `ConfirmRecurringUseCaseImpl.kt:121,145` | `contra = contraLegFor(recurring.type, category)` |
| 3 | `core/model/.../extension/Category.kt:39-43` | a natureza da contra-perna vem **da categoria**: `ContraLeg(category?.type?.accountType ?: …)` |
| 4 | `core/ledger/.../LedgerEntryWriter.kt` | `Σ = 0` fecha, e `DimensionKind.CATEGORY.landsOn` aceita `{INCOME, EXPENSE}` — nada refuta |
| 5 | `core/ledger/.../extension/Ledger.kt:56-64` | `deriveTransactionLabel` vê `INCOME in types` e rotula **receita** |

Com um template de despesa e uma categoria de receita, as pernas ficam `{ASSET −, INCOME +}`: o
dinheiro **sai** da conta e o lançamento é lido como **receita**.

Medido sobre o servidor real, com o razão real:

```
confirm_recurring {"id":1,"date":"2026-03-10","category_id":99}
isError = false
{"transaction":{"nature":"income","title":"Netflix","account":"Nubank",
  "category":"Salário","category_id":99,…},
 "note":"Confirmed for 2026-03. The template is unchanged…"}
entries: Nubank/ASSET = -3990 (dim=null), Receitas/INCOME = +3990 (dim=999)
label = INCOME
```

Nenhum dado legado é necessário: funciona numa instalação limpa, hoje.

## Por que ALTA

É o critério da faixa, literalmente: grava no razão algo diferente do que foi pedido e responde que
deu certo. O saldo da conta cai, o mês reporta uma receita que não houve, a categoria de receita
acumula um valor que saiu do bolso, e nada na resposta permite notar. É a primeira ALTA desde a
[001](archive/001-create-transaction-accepts-negative-amount.md).

## O que torna o achado mais grave do que parece

`ConfirmRecurringUseCaseImpl.kt:75-76` diz de si mesmo:

> *"The one write of the app that reaches the ledger without a form to hold the rule, so the rule is
> held here"*

A regra de valor positivo está lá, pela [001](archive/001-create-transaction-accepts-negative-amount.md).
A de direção não. O comentário descreve a responsabilidade correta e o código a cumpre pela metade.

## Correção sugerida

Duas camadas, como nas outras cinco:

- **O dono:** `ConfirmRecurringUseCase` recusa a combinação, com um `RecurringError` próprio. É onde
  o comentário acima já promete que a regra mora, e é o que protege a sheet de confirmação junto com
  a tool. **Não** em `contraLegFor`: ele é uma derivação pura, e filtrar ali seria voltar ao descarte
  silencioso que esta família inteira recusa.
- **A tool:** a recusa que nomeia os argumentos, como as outras cinco fazem, para o agente saber o
  que mudar.

O teste tem de olhar as **entradas do razão**, não a recusa: uma asserção sobre o resultado passa
enquanto as pernas estiverem invertidas, porque `Σ = 0` continua valendo.

## Relacionado

A sheet de confirmação re-oferece a categoria incoerente:
`ConfirmRecurringViewModel.offeredCategories` (`:263-274`) devolve a seleção à lista quando ela não
está entre as filtradas — a KDoc explica isso como continuidade para uma categoria **arquivada**, e o
ramo não distingue os dois casos. `OfferedCategoriesTest.kt:57-73` cobre a arquivada-mas-coerente e
não cobre a incoerente.
