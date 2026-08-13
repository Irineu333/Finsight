## Why

O detalhamento por categoria já trata o não classificado como um valor legítimo do eixo
analítico — ele tem linha, fatia e percentual (`uncategorized-spending-breakdown`). Mas
quem lê "R$ 300,00 sem categoria" não tem como perguntar *quais lançamentos são esses*: a
tela de transações só oferece filtrar por uma categoria existente, e o total sem
classificação continua sendo um número sem caminho para os itens que o compõem.

Pior, o buraco não é apenas de navegação: hoje é impossível **encontrar** o que ficou por
classificar. A única forma de achar esses lançamentos é varrer a lista inteira do mês
verificando quais não exibem ícone de categoria.

## What Changes

- O filtro de categoria da tela de transações passa a selecionar sobre o **eixo analítico**
  (`SpendingSubject`), não sobre `Category`: "Todas", uma categoria, ou "Sem categoria".
- "Sem categoria" recorta a lista para os lançamentos que **têm perna nominal e nela não
  carregam dimensão** — a mesma definição que o razão usa para compor o total sem
  classificação. Transferência, pagamento de fatura e ajuste **não** entram: eles não têm
  perna nominal, logo não estão no eixo, e chamá-los de "sem categoria" faria o recorte
  discordar do número que ele existe para explicar.
- O chip exibe o rótulo traduzido do não classificado quando esse valor está selecionado, e
  se distingue visualmente de uma categoria real (sem cor de natureza).
- "Limpar filtros" volta o eixo para "Todas", como com qualquer categoria.
- Uma dimensão órfã (que não resolve para categoria alguma) **não** é lavada em "sem
  categoria" — a mesma regra já escrita para o detalhamento.

Fora de escopo, deliberadamente: os demais filtros de categoria do app (parcelamentos,
transações de fatura, cartões). O tipo do eixo é compartilhado, então cada um pode adotá-lo
depois sem redefinir a regra.

## Capabilities

### New Capabilities
- `uncategorized-transaction-filter`: o não classificado como valor selecionável do filtro
  de categoria numa lista de lançamentos — o que ele recorta, o que ele deliberadamente não
  recorta (o que não está no eixo), como o controle se apresenta e como é limpo.

### Modified Capabilities
<!-- Nenhuma. O recorte governa apenas a lista, então `transaction-scope` continua valendo
     sem alteração (o resumo não se move), e `uncategorized-spending-breakdown` continua
     governando apenas o detalhamento. -->

## Impact

- `feature/transactions/impl` — `TransactionsFilters`, `TransactionsAction.SelectCategory`,
  `TransactionsUiState.selectedCategory`, o predicado de recorte no `TransactionsViewModel`
  e o `CategoryFilterChip` do `TransactionsScreen`.
- `core/model` — `SpendingSubject` passa a ser consumido também como valor de filtro; nenhum
  campo novo.
- `core/ledger` — nenhuma mudança. O critério ("tem perna nominal, sem dimensão") já é
  derivável de `Transaction`; falta apenas nomeá-lo.
- `core/resources` — nenhuma chave nova: o rótulo reusa `category_spending_uncategorized`,
  que já existe em pt e en e nomeia este mesmo valor do eixo no detalhamento (design D5).
- Ponta solta encontrada e a resolver junto: o parâmetro `category` do
  `TransactionsViewModel` é sempre `null` — `TransactionsScreen` passa
  `parametersOf(categoryLabel, null, target)` e nenhuma rota carrega categoria.
