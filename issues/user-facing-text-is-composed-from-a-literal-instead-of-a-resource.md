---
area: transversal
severity: low
type: i18n
---

# Texto que chega ao usuário é montado a partir de literal, não de resource

## Invariante

Todo texto que chega ao usuário — na tela ou por leitor de tela — vem de
`Res.string`/`UiText.Res`, e portanto existe nos dois idiomas.

Hoje é falso em **dois lugares**: um `contentDescription` em inglês fixo e uma preposição
portuguesa concatenada dentro de um valor. Cada um vaza o idioma errado para metade dos
usuários, e nenhum dos dois tem chave nos `strings.xml`.

## Mecânica

O mesmo defeito por dois caminhos.

**O `contentDescription`.** `InvoiceTransactionsScreen` escreve `"Menu"` direto no `Icon` do
`IconButton` de mais opções. A tela gêmea já faz certo — `AccountsScreen` usa
`stringResource(Res.string.accounts_more_options_content_description)`, chave que existe nos
dois arquivos. O dono da regra e a chave estavam disponíveis; a tela de faturas não os usou.

**A preposição.** `ViewBudgetModal` monta o valor da linha "Percentual" com `buildString` e
emenda o rótulo da recorrência com um `" de "` cru. Em inglês o modal renderiza
`30% de Salary` — metade traduzida, metade não. Não há chave para essa junção.

Distinto de `icon-buttons-are-clickable-without-any-accessible-label`: lá o rótulo está
**ausente**; aqui ele **existe** e está no idioma errado, então a correção de um não alcança
o outro.

## Evidência

- `feature/creditcards/impl/.../ui/screen/invoiceTransactions/InvoiceTransactionsScreen.kt:183`
  — `contentDescription = "Menu",`
- `feature/budgets/impl/.../ui/modal/viewBudget/ViewBudgetModal.kt:200`
  — `budgetProgress.recurringLabel?.let { append(" de $it") }`
- contraste, o padrão correto: `feature/accounts/impl/.../ui/screen/accounts/AccountsScreen.kt:184`
  — `contentDescription = stringResource(Res.string.accounts_more_options_content_description),`

Que são só esses dois: `grep -rn --include='*.kt' 'contentDescription\s*=\s*"' core feature app`,
excluído `/build/`, devolve **uma única ocorrência** — a de `InvoiceTransactionsScreen`.

## Consequência

Quem usa o app em português e depende de leitor de tela ouve `"Menu"` em inglês no único
botão que abre as ações da fatura — editar cartão, fechar, reabrir, excluir. Quem usa em
inglês lê `30% de Salary` no detalhe de um orçamento percentual.

Nenhum dos dois é pego pela regra de que "uma chave presente em só um arquivo é um bug",
porque aqui não há chave nenhuma para comparar: é o buraco que a comparação mecânica dos
dois `strings.xml` não enxerga.

## Sugestão

Para o `contentDescription`, reusar `accounts_more_options_content_description` ou criar a
irmã da fatura no mesmo padrão. Para a preposição, uma chave com dois placeholders — o
percentual e o nome da recorrência — de modo que a ordem das partes seja decisão da tradução,
não da concatenação. Ambas nos dois `strings.xml`. Não vinculante.
