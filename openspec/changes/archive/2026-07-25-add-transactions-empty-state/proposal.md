## Why

Hoje a tela de transações sem nada a listar é uma tela em branco: o resumo e os chips ficam
no topo e abaixo deles não há nada — nenhuma palavra que diga se o mês está vazio, se o filtro
cortou tudo ou se os dados ainda estão chegando. Pior, o estado inicial do `stateIn` é
byte-a-byte igual a "mês sem transações", então o branco aparece por um instante em **todo**
carregamento, inclusive quando há dados. É a única lista relevante do app sem estado vazio —
categorias, orçamentos, recorrentes, cartões e parcelamentos já têm o seu.

## What Changes

- A tela de transações passa a exibir uma mensagem no lugar da lista vazia, **abaixo** do
  resumo e dos chips, que continuam visíveis — são eles o caminho de saída do vazio.
- O vazio passa a ter **duas leituras distintas**, porque as saídas são distintas:
  - **sem nenhuma transação registrada** — não há filtro a afrouxar; a mensagem convida a
    registrar a primeira, apontando o botão de adicionar já existente no chrome;
  - **há transações, mas nenhuma cabe no recorte atual** (mês, escopo ou filtros) — a
    mensagem é discreta e oferece *limpar os filtros* quando houver algum filtro ativo.
- O estado inicial deixa de se passar por vazio: enquanto a primeira emissão do repositório
  não chega, a tela não afirma nada — nem lista, nem mensagem de vazio.
- Nova ação `TransactionsAction.ClearFilters`, que devolve os filtros de lista ao neutro
  (categoria, natureza, alvo, recorrentes, parcelados) **sem** tocar mês nem escopo, que
  governam também o resumo.

Sem breaking changes: nenhuma assinatura pública de `feature/transactions/api` muda.

## Capabilities

### New Capabilities
- `transactions-empty-state`: o que a tela de transações afirma quando não há o que listar —
  as duas leituras do vazio e a saída que cada uma oferece, a proibição de afirmar vazio antes
  da primeira leitura, e a permanência dos controles que governam o recorte.

### Modified Capabilities
<!-- Nenhuma. O escopo continua governando resumo e lista exatamente como em
     `transaction-scope`; esta mudança só decide o que ocupa o lugar da lista quando ela
     não tem itens, sem alterar nenhum recorte nem nenhuma linha do resumo. -->

## Impact

- `feature/transactions/impl` — `TransactionsUiState` (fase de carregamento e distinção entre
  os dois vazios), `TransactionsViewModel` (a distinção deriva da lista pré-filtro, já em mãos
  no `combine`; nova ação), `TransactionsScreen` (o item de vazio na `LazyColumn`),
  `TransactionsAction`.
- `core/resources` — novas strings (`values` e `values-en`).
- Testes: `feature/transactions/impl/src/commonTest` — transições de estado do ViewModel.
- Sem impacto no razão, no banco, em migrações ou em qualquer outra feature.
