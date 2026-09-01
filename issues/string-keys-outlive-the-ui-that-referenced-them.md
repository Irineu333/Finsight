---
area: resources
severity: low
type: i18n
---

# Chaves de string sobrevivem à UI que as referenciava

## Invariante

Toda chave declarada nos `strings.xml` é referenciada por algum `Res.string.<chave>` no
código Kotlin.

Hoje é falso para **14 chaves** — 28 linhas somando os dois idiomas. Duas são deliberadas e
documentadas; as outras **12** são resíduo de componentes apagados e formulários refeitos, e
ninguém removeu a chave junto com o último uso.

## Mecânica

A chave e o uso vivem em módulos diferentes: a declaração em `:core:resources`, a referência
no `feature/*/impl`. Apagar um componente não quebra nada em `:core:resources` — o acessor
gerado continua compilando, sem uso —, então a remoção do último `stringResource(...)` passa
despercebida e a chave fica.

Nada força a relação: nenhum teste, nenhuma task de build e nenhum lint comparam as chaves
declaradas com as referenciadas.

## Evidência

A varredura, com `/build/` excluído dos dois lados (os acessores gerados referenciam **toda**
chave e mascaram o resultado se entrarem):

- 816 chaves declaradas em cada idioma, `comm` vazio nos dois sentidos — *a regra "toda chave
  existe nos dois" vale hoje*
- 802 chaves referenciadas no Kotlin de produção; a diferença são as 14 abaixo, e não há
  referência a chave inexistente

Órfãs de componente apagado — o `InvoiceSummaryCard`, removido em `8a80de89b` junto com os
5 `import` e as 5 chamadas, sem tocar nos `strings.xml`:

- `invoice_summary_card_invoice`, `invoice_summary_card_expenses`,
  `invoice_summary_card_adjustments`, `invoice_summary_card_advance_payments`,
  `invoice_summary_card_edit_invoice`

Órfãs de formulário ou tela refeitos:

- `transfer_amount_label` — último uso removido em `84dc6e572`
- `view_budget_title`, `view_category_title`, `view_recurring_title`,
  `view_transaction_title` — os quatro modais de detalhe passaram a exibir o nome da própria
  entidade no cabeçalho, e o título genérico ficou sem dono
- `dashboard_installments`
- `accounts_advance_payments` — o pior caso: sobrou até o `import` em
  `core/ui/.../ui/component/AccountCard.kt:31`, sem nenhum uso no arquivo

Deliberadas, e **não** são o defeito — o KDoc de
`feature/budgets/impl/.../ui/screen/budgets/BudgetCard.kt` declara que o lugar do período
"é mantido (`budgets_period_monthly` / `budgets_period_weekly` existem para ele) e nada é
desenhado até o domínio ter mais de um período":

- `budgets_period_monthly`, `budgets_period_weekly`

## Consequência

Nada quebra e nenhum usuário vê diferença — o custo é de manutenção e de confiança. Quem
traduz mantém 24 linhas que não aparecem em lugar nenhum. Quem procura por onde uma tela diz
"Fatura" acha `invoice_summary_card_invoice` e conclui que existe um card apagado há dois
meses. E `AccountCard.kt` carrega um `import` que só existe para não ser usado.

## Sugestão

Remover as 12 dos dois arquivos, mais o `import` morto, e manter as duas que o KDoc
justifica. O que fecha o invariante de vez é um teste no espírito dos que já existem em
`app/shared/src/jvmTest` — que leem as fontes de produção como texto — comparando as chaves
declaradas com as referenciadas e listando as exceções deliberadas por nome: assim a próxima
órfã aparece no commit que a cria, e não numa varredura seis meses depois. Não vinculante.
