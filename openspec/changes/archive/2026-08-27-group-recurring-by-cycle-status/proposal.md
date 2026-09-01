## Why

A tela de recorrentes é uma pilha sem hierarquia: ela lista **templates**, ordenados por data
de criação, e a única pergunta que o usuário leva até ela — *o que ainda falta lançar este
mês?* — é a única que ela não responde. A palavra "pendente" não existe na tela, embora exista
no dashboard, de onde parte o "ver todas" que traz o usuário até aqui.

Os quatro estados que interessam — **pendente**, **a lançar**, **lançado**, **ignorado** — não
são propriedades do template. São propriedades do **ciclo dele num mês**, e todos os quatro já
são deriváveis do que o domínio tem hoje: `Recurring.generatesCycleIn`, as ocorrências do mês
e a data de hoje. Nada disso chega à lista, embora o `combine` da tela já observe as
ocorrências para alimentar o card.

## What Changes

- **BREAKING (spec)** — a lista deixa de ser de templates e passa a ser dos **ciclos** deles no
  mês selecionado. O seletor de mês do card, que hoje governa só o card, passa a governar a
  tela inteira.
- A lista ganha **seções por status do ciclo**, com contagem própria, nesta ordem: pendente, a
  lançar, lançado, ignorado. Seção sem itens não é renderizada.
- A partição ganha **um dono único no domínio**, em `feature/recurring/api`.
  `GetPendingRecurringUseCase` passa a ser uma pergunta feita a esse dono em vez de um segundo
  predicado, e `GetRecurringMonthOverviewUseCase` passa a consumir a partição em vez de
  recalcular `handled`/`total`/`skipped`.
- O corte que separa *pendente* de *a lançar* passa a comparar **datas**, não dias do mês. A
  comparação atual só é correta enquanto o mês é o corrente, e um mês selecionável a quebra.
- A linha de um ciclo **lançado** passa a vir do **ledger**, inteira — valor, título e
  categoria da transação, não do template. Confirmar um ciclo permite sobrescrever os cinco, e
  o template deixa de ser fonte de verdade sobre o que de fato foi lançado.
- As recorrências **arquivadas saem da lista** e ganham um destino próprio. Elas não geram
  ciclo em mês algum, logo não têm status de ciclo e não cabem em seção nenhuma.
- O seletor de recorte perde `ARCHIVED` e deixa de misturar dois eixos: sobra o eixo de
  natureza (todas / despesas / receitas), transversal às seções.
- O card do mês **perde o rodapé de contadores**. As contagens das seções o tornam eco — e
  nomeiam *quais*, não só quantos. A linha de templates sem conta permanece no card, porque
  nenhuma seção a conta.

**Fora do escopo, por decisão explícita:** confirmar ou ignorar a partir da linha. Esta change
é sobre leitura, como a anterior foi. O débito que isso cria está declarado no design.

## Capabilities

### New Capabilities

- `recurring-cycle-status`: a partição de um mês de recorrentes em quatro estados de ciclo, seu
  dono único no domínio, e como a tela a apresenta em seções — a ordem, as contagens, a
  ordenação interna e o que cada tipo de linha afirma.
- `recurring-archive`: o destino que mantém as recorrências arquivadas alcançáveis e
  reversíveis depois que elas deixam a lista mensal.

### Modified Capabilities

- `recurring-month-overview`: o requisito *"O seletor de mês governa o resumo e o filtro governa
  a lista"* é contradito na sua parte final — a lista passa a ser recortada por mês. E o
  requisito *"O contador é onde um ciclo ignorado é representável"* muda de dono: a
  representabilidade passa para a seção, e o contador sai do card. Dois requisitos vizinhos são
  arrastados por essas duas mudanças e vêm junto: *"A metade lançada é lida do razão"*, cujo
  cenário de transação apagada afirmava o contador recuando, e *"O resumo permanece quando o
  recorte da lista está vazio"*, cujo cenário esvaziava a lista pelo recorte de arquivadas.
- `recurring-list-row`: a linha de um ciclo lançado passa a ser lida do ledger, o que restringe
  a marca de valor irresolvível às seções sem fato; e os cenários que falam do *recorte de
  arquivadas* deixam de ser sobre esta tela.
- `account-lifecycle`: o requisito *"Recorrência arquivada pode ser desarquivada"* descreve o
  acesso à arquivada como *"recorte de um seletor único, como em categoria"*. A exigência não
  muda — arquivar continua reversível, e é ela que obriga o destino a existir —, mas a forma
  do acesso passa a ser um destino próprio, porque o recorte que ele nomeia deixa de existir.

## Impact

**Domínio** (`feature/recurring/api`) — dono novo da partição; `GetPendingRecurringUseCase`
passa a derivar dele; `GetRecurringMonthOverviewUseCase` deixa de recalcular os contadores.

**Tela** (`feature/recurring/impl`) — `RecurringUiState`, `RecurringViewModel`,
`RecurringScreen`, `RecurringFilter`, `RecurringAction`, `RecurringSummaryFactory`; rota interna
nova para o arquivo.

**Ledger** (`core/ledger`) — `ITransactionRepository` ganha uma leitura por ids. Hoje só há
`getTransactionById`, que numa lista é o N+1 que a change anterior pagou para eliminar, e
`observeAllTransactions`, que relê o ledger inteiro.

**Reuso** (`core/ui`) — a seção de lançados usa `TransactionUi` e `TransactionCard`, pelo mesmo
caminho que `feature/creditcards` já usa em três telas. `TransactionUi.title` e `Recurring.label`
chamam a mesma função de nomeação, então a identidade não ganha um segundo vocabulário.

**Consumidor externo** (`feature/dashboard`) — o card de pendências consome
`GetPendingRecurringUseCase` e herda o predicado corrigido.

**Sem migração de banco.** A change depende da FK `CASCADE` de
`recurring_occurrences.transactionId`, que está no schema v14 implantado: não existe ciclo
confirmado apontando para transação removida — apagar a transação apaga a ocorrência, e o ciclo
volta a aparecer como pendente por si só.

**Uma issue aberta é tocada, mas não fechada.**
`the-upcoming-recurring-window-stops-at-the-end-of-the-month` pede exatamente a troca de
comparação de dias por comparação de datas, que esta change faz. Ela não fecha por isso: a
janela `daysAhead` do dashboard atravessa a virada do mês, e uma partição de **um** mês não
responde por dois. A peça de que aquela correção precisa fica pronta; o consumo dela não está
neste escopo.

**Strings** — as chaves novas entram nos dois arquivos, `values/strings.xml` (pt) e
`values-en/strings.xml` (en), na mesma mudança.
