## Why

A tela de transações ganhou estado vazio, mas as outras três listas de lançamentos do app
continuam terminando em branco: **contas** (lançamentos da conta selecionada no mês),
**cartões** (lançamentos da fatura aberta do cartão selecionado) e **faturas** (lançamentos da
fatura escolhida no pager). Nas três, quando nada sobra, a tela mostra o cartão do topo, os
chips de filtro e depois nada — sem dizer se a conta é nova, se o mês está vazio, se o filtro
cortou tudo ou se os dados ainda não chegaram. Pior na tela de faturas: o `initialValue` do
`stateIn` é uma `InvoiceTransactionsUiState()` de campos vazios, indistinguível de "li e não há
nada", então o branco também aparece durante o carregamento de toda fatura que **tem**
lançamentos.

## What Changes

- As três telas passam a exibir uma mensagem no lugar da lista vazia, **abaixo** do cartão do
  topo (pager de contas / de cartões / de faturas) e dos chips, que continuam visíveis — são
  eles o caminho de saída do vazio.
- O vazio passa a ter **duas leituras** em cada tela, porque as saídas são distintas:
  - **vazio de origem** — o recorte-raiz da tela não tem nenhum lançamento: a conta nunca
    movimentou, a fatura aberta do cartão está sem lançamentos, a fatura selecionada está sem
    lançamentos. Nenhum filtro pode revelar algo; a mensagem apenas constata;
  - **vazio de recorte** — há lançamentos no recorte-raiz, mas nenhum sobrevive ao mês (contas)
    ou aos filtros ativos. A mensagem aponta o recorte como causa e oferece *limpar os filtros*
    quando houver algum ativo.
- Nenhuma das três telas ganha convite a "registrar o primeiro lançamento": nenhuma delas
  oferece esse comando (o FAB de contas cria conta, o de cartões cria cartão, a de faturas não
  tem FAB). A mensagem de origem descreve, não instrui a tocar um botão inexistente.
- `InvoiceTransactionsUiState` deixa de se passar por vazia enquanto carrega: ganha a mesma
  fase de carregamento que as outras já têm.
- Novas ações `ClearFilters` em `AccountsAction`, `CreditCardsAction` e
  `InvoiceTransactionsAction`, devolvendo ao neutro apenas os filtros de lista — sem tocar mês
  (contas), conta, cartão ou fatura selecionados, que governam também os números do topo.
- O layout do vazio — ícone, título, corpo e ação opcional — passa a ser **um** componente
  compartilhado em `:core:designsystem`, adotado pelas três telas e pela de transações, que
  hoje o carrega como cópia privada.

Sem breaking changes: nenhuma assinatura pública de `feature/accounts/api` ou
`feature/creditcards/api` muda.

## Capabilities

### New Capabilities
- `transaction-list-empty-states`: o que as listas de lançamentos de contas, cartões e faturas
  afirmam quando não há o que listar — as duas leituras do vazio em cada uma, a proibição de
  afirmar vazio antes da primeira leitura, a permanência dos controles do recorte e quando a
  ação de limpar filtros é oferecida. Generaliza para essas três telas as regras que
  `transactions-empty-state` já fixa para a tela de transações; aquele spec não muda.

### Modified Capabilities
<!-- Nenhuma. `transactions-empty-state` continua valendo palavra por palavra: a tela de
     transações passa a usar o componente compartilhado, o que é detalhe de implementação e
     não altera nenhum requisito. -->

## Impact

- `feature/accounts/impl` — `AccountsUiState` (fase de lista dentro de `Content`),
  `AccountsViewModel` (derivação das duas leituras, `canClearFilters`, nova ação),
  `AccountsScreen`, `AccountsAction`.
- `feature/creditcards/impl` — `CreditCardsUiState`/`ViewModel`/`Screen`/`Action` e
  `InvoiceTransactionsUiState`/`ViewModel`/`Screen`/`Action` (esta também ganha a fase de
  carregamento).
- `core/designsystem` — novo componente de mensagem de estado vazio.
- `feature/transactions/impl` — passa a usar o componente compartilhado no lugar do
  `TransactionsEmptyState` privado; nenhum requisito seu muda.
- `core/resources` — novas strings (`values` e `values-en`).
- Testes: `commonTest` de `accounts/impl` e `creditcards/impl`.
- Sem impacto no razão, no banco, em migrações ou em qualquer outra feature.
