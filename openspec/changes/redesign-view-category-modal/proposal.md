## Why

O detalhe de uma categoria é o **único lugar do app com números por categoria** — a lista
mostra apenas ícone e nome —, e hoje ele responde a uma pergunta que quase ninguém faz.
Todos os seus números são do mês selecionado num seletor no topo, e o rótulo "Total gasto"
lê-se como histórico enquanto mostra um mês. Quem abre uma categoria quer saber se está
gastando mais do que o normal; o modal atual só sabe dizer quanto gastou num mês, e exige
navegar mês a mês para que "o normal" apareça.

## What Changes

- **BREAKING (UI):** o seletor de mês sai do modal. O detalhe deixa de ser navegável no
  tempo, e as ações `NextMonth`/`PreviousMonth` deixam de existir.
- O modal passa a informar **três figuras**, todas sobre uma janela declarada:
  - **gasto do mês corrente**, explicitamente rotulado como parcial ("dia 24 de 31");
  - **média mensal dos últimos 12 meses fechados**, com a janela dita no rótulo;
  - **total dos últimos 12 meses** — não o histórico completo.
- O gasto do mês ganha uma **comparação contra a média**, em pontos percentuais, e não
  contra o mês anterior: um mês atípico não deve virar a régua de todos os outros.
- A comparação SHALL usar **significante textual e seta**, nunca verde/vermelho — as duas
  cores já significam receita e despesa no app, e reaproveitá-las para "gastou menos"
  colide com a convenção.
- Uma **categoria arquivada** troca a figura de destaque: o mês corrente não diz nada sobre
  ela, e o total histórico passa a ser o número principal.
- Um **atalho para a lista de transações**, já filtrada por aquela categoria, substitui a
  navegação temporal removida — "quanto gastei em março" continua alcançável, num só lugar
  do app em vez de dois.
- O razão ganha uma leitura de **série mensal por dimensão**: um total por (mês, moeda) numa
  consulta. Ela é a base das três figuras e substitui as duas leituras mensais de hoje.

Explicitamente **fora** deste escopo, por serem tela e não modal: gráfico de tendência,
fatia da categoria no mês, barra de orçamento e lista de lançamentos embutida.

## Capabilities

### New Capabilities

- `category-spending-overview`: o que o detalhe de uma categoria informa — quais figuras,
  sobre qual janela, como a janela é declarada, como o mês parcial se anuncia, contra o que
  a variação compara, como ela se expressa sem usar as cores de natureza, e o que cada
  estado da categoria (arquivada, sem movimento, sem histórico suficiente) mostra no lugar.

### Modified Capabilities

- `ledger-reporting`: passa a exigir a leitura **agregada por mês** de uma dimensão — um
  total por (mês, moeda) numa consulta, no vocabulário do razão, sem nomear categoria. Sem
  ela, uma janela de 12 meses custaria 12 leituras e a média não teria de onde sair.
- `uncategorized-transaction-filter`: o valor do eixo analítico passa a poder chegar
  **pré-selecionado por navegação**, vindo de outra superfície. Hoje o filtro só é escolhido
  dentro da própria tela.

## Impact

- **`core/ledger`**: um membro novo em `IEntryRepository` + a consulta em `EntryDao`. Os
  ~26 arquivos de teste que implementam fakes do repositório precisam acompanhá-lo.
- **`core/model`**: a janela, a média e a variação são regras deriváveis do domínio, e por
  isso têm um dono só, acima do razão — o razão não consolida e não conhece taxa.
- **`feature/categories/impl`**: `ViewCategoryModal`, seu `UiState`, `Action` e `ViewModel`.
- **`feature/transactions/api`**: `TransactionsRoute` ganha o filtro de categoria; a
  maquinaria de filtro já existe na tela e só precisa receber o estado inicial.
- **`core/resources`**: chaves novas em `values/strings.xml` e `values-en/strings.xml`.
- **`.maestro/flows/categories/lifecycle.yaml`**: as asserções sobre
  `view_category_total_amount` e `view_category_transaction_count` mudam de significado.
