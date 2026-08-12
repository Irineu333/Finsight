## Why

A modal de detalhe de uma transação é uma lista plana `rótulo → valor` para uma coisa que
tem **duas pontas**. Ela herdou o formato da lista, e com ele a decisão mais cara de todas:
o valor exibido é **uma perna só**, escolhida por desempate de moeda base
(`Transaction.figureLegUnder`). Numa transferência de R$ 550,00 para US$ 100,00, os
US$ 100,00 **não aparecem em lugar nenhum** — o usuário precisa multiplicar pela taxa para
saber o que chegou. O mesmo vale para o pagamento de uma fatura em moeda estrangeira.

Esse desempate existe porque uma linha de lista *tem* que ser um número. A modal não tem
essa restrição, e é aí que está a alavanca: não é preciso escolher uma ponta.

Junto disso, a modal apresenta hoje a **direção de uma perna** (`Despesa`/`Receita`) onde a
spec manda apresentar a **natureza** — e o faz sem perspectiva em 6 dos 7 pontos que a
abrem, caindo em `primaryEntry`:

- `presentation-mapping` §*Natureza e direção são vocabulários distintos*: "Uma apresentação
  **sem** perspectiva SHALL usar a natureza: a direção de uma perna escolhida arbitrariamente
  não é uma propriedade da transação e MUST NOT ser apresentada como se fosse."
- `presentation-mapping` §*Uma tela declara a perspectiva que tem*: "Dentro de uma mesma
  superfície, a perna lida SHALL ter uma única definição. Filtro, item e detalhe MUST NOT
  derivá-la cada um por conta própria."

O extrato de fatura viola as duas ao mesmo tempo: `InvoiceTransactionsViewModel` mapeia a
lista sob a perspectiva do cartão (o pagamento **entra**), e `InvoiceTransactionsScreen`
abre o detalhe sem perspectiva, que lê `primaryEntry` e diz **"Despesa"**. Mesma superfície,
duas leituras opostas da mesma transação.

E há um defeito de conteúdo: num pagamento de fatura a modal exibe `Origem: Cartão de
Crédito`, porque a condição é `direction.isExpense && isCardTarget`. O dinheiro saiu da
**conta**; a linha foi escrita para distinguir compra no débito de compra no crédito e é
aplicada a uma operação onde essa pergunta não existe.

## What Changes

- **A modal passa a exibir um card por perna monetária** (`ASSET`/`LIABILITY`), sempre —
  inclusive quando há apenas uma. Cada card responde de uma vez *qual* conta ou cartão,
  *quanto*, e *o que aconteceu com aquele dinheiro*.
- **O verbo de cada card é derivado de `(AccountType, sinal da perna)`**, com um único
  override: transação com perna `EQUITY` é ajuste. Não consulta `TransactionLabel` —
  é uma afirmação sobre o razão, e por isso não tem como divergir da fachada.
- **A taxa praticada deixa de ser uma linha e vira o conector entre os dois cards**, porque
  ela *é* uma relação entre eles. A ordem dos cards é a perna primária primeiro, que é a
  mesma direção em que `appliedRate` já divide — a seta e a taxa concordam por construção.
- **A fatura (com o seu status) e a parcela passam a viver dentro do card do passivo**, em
  vez de linhas irmãs da conta e do cartão. São atributos daquela perna.
- **O header passa a exibir a natureza** — `Despesa`, `Receita`, `Transferência`,
  `Pagamento`, `Ajuste` —, e a linha de título é omitida quando a transação não tem título
  próprio nem categoria, em vez de cair no literal `"Untitled"` não localizado.
- **Cor e ícone viram função pura de `TransactionLabel`**, total sobre os cinco valores.
- **Somem as linhas** `Valor`, `Taxa praticada`, `Origem`, `Conta`, `Conta origem`,
  `Conta destino`, `Cartão`, `Fatura`, `Parcela`. Restam como linhas de contexto apenas
  `Data` e `Recorrência`.
- **A modal de ajuste passa a usar o mesmo card**, com o verbo `Ajustou` e sinal explícito.
- **BREAKING** — `TransactionsEntry.viewTransactionModal` perde o parâmetro `perspective`:
  com as duas pernas na tela não há perna a escolher, e o header passa a ler a natureza.
  O único ponto que o passava é `AccountsScreen`.
- **O alcance da moeda base encolhe em um site**: `ViewTransactionViewModel` deixa de ler
  `IBaseCurrencyRepository`, e a entrada correspondente sai de `BaseCurrencyReachTest`.

## Capabilities

### New Capabilities
- `transaction-detail`: a composição da modal de detalhe de uma operação — um card por perna
  monetária, o verbo derivado, o que vive dentro de um card, o conector de taxa, o header
  pela natureza e as linhas de contexto que restam.

### Modified Capabilities
- `money-display`: a superfície de **item** deixa de incluir a modal. Nasce a superfície de
  **operação** — a que apresenta a transação inteira em vez de uma perna dela — com a sua
  própria regra de sinal: módulo em todo card, porque o verbo entrega a direção, e sinal
  explícito só no ajuste, cujo verbo a retém.
- `presentation-mapping`: a modal de detalhe é uma apresentação **sem perspectiva** e que
  **não lê perna nenhuma**. O requisito da perna neutra e o da perspectiva declarada
  governam o mapeamento *de uma perna*; a superfície de operação não faz esse mapeamento,
  e a exigência de que filtro, item e detalhe concordem sobre a perna passa a ser satisfeita
  por dissolução — o detalhe mostra as duas.

## Impact

**Código**
- `feature/transactions/impl` — `ViewTransactionModal`, `ViewTransactionUiState`,
  `ViewTransactionViewModel`, `ViewAdjustmentModal`, `ViewAdjustmentUiState`,
  `ViewAdjustmentViewModel`, `TransactionsEntryImpl`
- `feature/transactions/api` — `TransactionsEntry.viewTransactionModal` (assinatura)
- `feature/accounts/impl` — `AccountsScreen` (único call site com perspectiva)
- `core/ui` — novo componente do card de perna e o seu modelo de UI; `DetailRow`, hoje
  duplicado entre as duas modais, passa a ter um dono
- `core/resources` — novas chaves (verbos, rótulos de natureza) em `values` e `values-en`

**Testes**
- `app/shared` — `BaseCurrencyReachTest` perde a entrada de `ViewTransactionViewModel`; o
  teste assere igualdade nos dois sentidos, então a remoção é obrigatória e não opcional
- `feature/transactions/impl` — `ViewTransactionViewModelTest`, `ViewTransactionAppliedRateTest`,
  `ViewTransactionGatesTest`, `ViewAdjustmentViewModelTest`

**Não muda**
- O razão, os repositórios e o caminho de escrita
- O mapeamento de item das listas (`itemDisplayAmount`, `toTransactionUi`) e as suas regras
  de sinal, que continuam valendo onde uma linha é uma perna
- Os portões de edição e exclusão (`isEditable`, `isRemovable`, `isChangeable`)
