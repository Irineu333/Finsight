## Context

A modal de detalhe é hoje uma `Column` de `DetailRow(label, value)` — `ViewTransactionModal.kt:122-391`.
O estado que a alimenta (`ViewTransactionUiState.Content`) resolve **uma** figura escalar,
`amount`, através de `Transaction.figureLegUnder(perspective?.accountId, baseCurrency)`
(`ViewTransactionUiState.kt:98-107`), que numa operação entre moedas escolhe entre as duas
pontas pela moeda base.

Esse desempate é legítimo — a spec `presentation-mapping` o descreve e o justifica — mas ele
existe para superfícies que **precisam dizer um número só**. A modal foi colocada nesse grupo
porque nasceu com o formato da lista, não porque a restrição se aplique a ela.

Três defeitos vivem em cima disso:

1. A ponta que não foi escolhida **não aparece em lugar nenhum**. Numa transferência de
   R$ 550,00 para US$ 100,00, os dólares só existem implicitamente, na linha da taxa.
2. O cabeçalho exibe `direction`, derivado de `perspectiveEntry`, que sem perspectiva cai em
   `primaryEntry` (`ViewTransactionUiState.kt:73-82`). Seis dos sete pontos que abrem a modal
   não passam perspectiva. `presentation-mapping` §*Natureza e direção* já proíbe isso.
   O extrato de fatura é o caso agudo: `InvoiceTransactionsViewModel.kt:143-148` mapeia a
   lista sob a perspectiva do cartão (o pagamento **entra**) e `InvoiceTransactionsScreen.kt:411`
   abre o detalhe sem perspectiva, que diz **"Despesa"**.
3. `ViewTransactionModal.kt:261-267` exibe `Origem: Cartão de Crédito` num pagamento de fatura,
   porque a condição é `direction.isExpense && isCardTarget`. O dinheiro saiu da conta.

Ao lado disso, `ViewAdjustmentModal` mantém uma cópia própria de `DetailRow` e a mesma
composição em linhas.

## Goals / Non-Goals

**Goals:**
- Exibir as duas figuras exatas de uma operação que atravessa moedas, sem conversão e sem desempate.
- Colocar cada fato ao lado da perna a que ele pertence, em vez de em linhas irmãs.
- Trazer o cabeçalho para conformidade com `presentation-mapping` §*Natureza e direção*.
- Dar um dono único à composição, consumida pelo detalhe de transação e pelo de ajuste.
- Encolher o alcance de `IBaseCurrencyRepository` em um site, declaradamente.

**Non-Goals:**
- Mudar o razão, os repositórios ou qualquer caminho de escrita.
- Mudar o mapeamento de item das listas. `itemDisplayAmount` e `toTransactionUi` continuam
  como estão: onde uma linha é uma perna, a regra de item continua sendo a certa.
- Mudar os portões de edição e exclusão (`isEditable`, `isRemovable`, `isChangeable`) ou as
  mensagens de bloqueio.
- Mudar o desempate por moeda base em si. Ele continua existindo, com o mesmo dono, para as
  listas, o dashboard e o relatório — apenas deixa de ter o detalhe como consumidor.
- Introduzir ênfase visual da conta de origem no card correspondente. É hipótese, não requisito.

## Decisions

### D1 — Um card por perna monetária, não por moeda

O critério é `entries.filter { it.account.type.isMonetary }`, que o razão já expõe como
`Transaction.monetaryEntries`. Isso dá 1 card para gasto, receita, compra em cartão e ajuste;
2 para transferência e pagamento de fatura.

*Alternativa considerada — dois cards apenas quando as pernas estão em moedas diferentes.* Foi
a formulação inicial e foi recusada: a mesma operação mudaria de forma conforme o câmbio das
contas envolvidas, e o layout de card único ainda precisaria dizer "de onde/para onde" de
algum jeito, provavelmente voltando às duas linhas `rótulo → valor` que este redesign existe
para eliminar. Com o critério por perna, o card de duas moedas serve inalterado ao caso
monomoeda — ele apenas não exibe taxa no conector.

*Alternativa considerada — um card também para a perna nominal (a categoria).* Recusada: a
categoria não carrega dinheiro que o usuário reconheça como seu, e um card de R$ 250,00
"em Mercado" ao lado do card da conta sugeriria uma segunda saída.

### D2 — O verbo deriva de `(AccountType, sinal)`, e não da natureza

```
transação tem perna EQUITY  → ajustou
ASSET,     amount < 0       → saiu de
ASSET,     amount > 0       → entrou em
LIABILITY, amount < 0       → lançou em
LIABILITY, amount > 0       → abateu de
```

O override de `EQUITY` é o mesmo teste que `deriveTransactionType` já faz (`Ledger.kt:105`).

Deliberadamente **não consulta `TransactionLabel`**. Derivar o verbo dos mesmos fatos de que a
natureza é derivada é o que impede os dois de divergirem — se um dia `deriveTransactionLabel`
ganhar uma forma nova, o verbo a acompanha sem ninguém lembrar de atualizá-lo.

Verificação de totalidade sobre as sete formas do razão: gasto em conta (`ASSET −`), receita
(`ASSET +`), compra em cartão (`LIABILITY −`), pagamento (`ASSET −` + `LIABILITY +`),
transferência (`ASSET −` + `ASSET +`), ajuste de conta e ajuste de fatura (override). Não
sobra caso.

### D3 — Módulo em todo card, sinal explícito só no ajuste

O verbo entrega a direção, então o sinal é redundância. O ajuste é a exceção porque "ajustou"
a retém — que é *exatamente* o princípio que `itemDisplayAmount` já aplica
(`ItemDisplayAmount.kt:44`), com uma evidência diferente: lá o rótulo, aqui o verbo.

Isso significa **dois produtores da política de sinal** — `itemDisplayAmount` para as listas e
o mapper de perna para o detalhe. Não é duplicação: as duas superfícies têm evidências
distintas do sentido do movimento, e a política é a mesma pergunta feita sobre evidências
distintas. O que garante que não divirjam é `money-display` §*O ajuste lê igual em toda
superfície*, que continua valendo e continua sendo o mesmo valor do razão nos dois lados.

O caso que fecha o argumento é o pagamento de fatura: com sinal, ele leria `− R$ 550,00`
seguido de `+ US$ 100,00`, sugerindo que algo se perdeu entre as duas pernas — quando as duas
são benignas (saiu dinheiro, sumiu dívida).

### D4 — O cabeçalho lê a natureza, e a perspectiva morre

Cabeçalho linha 1: `TransactionLabel`, cinco valores, total. Linha 2: `displayTitleOf`, omitida
quando não há título próprio nem categoria.

A omissão não é cosmética. Hoje `TRANSFER`/`PAYMENT` **substituem** a linha do título
(`ViewTransactionModal.kt:205-209`), então `displayTitle` nunca é alcançado por elas. Com a
natureza subindo para a linha 1, a linha 2 passa a chamar `displayTitleOf`, e uma
transferência sem título nem categoria cai no literal `"Untitled"`, não localizado
(`DisplayTitle.kt:24`). Omitir é a resposta certa; localizar o literal seria dar nome a uma
ausência.

Cor e ícone passam a ser função total de `TransactionLabel`, eliminando o ramo `else -> direction`
de `transactionColor()` (`ViewTransactionModal.kt:552-558`) e do ícone de fallback (`:155-161`).

Com isso `perspective` fica sem leitor: ela só alimentava `direction` e `amount`. Sai de
`ViewTransactionUiState.Content`, de `ViewTransactionViewModel`, do construtor de
`ViewTransactionModal` e da assinatura em `TransactionsEntry.kt:9` — **quebra de api**, com um
único call site a ajustar (`AccountsScreen.kt:376-379`).

*Alternativa considerada — rebaixar `perspective` a ênfase visual*, destacando o card da conta
de onde o usuário veio. Recusada por ora: é hipótese, não requisito, e manter um parâmetro que
6 de 7 chamadores não passam sugere um efeito que não existe. Reintroduzi-lo depois é um
parâmetro opcional com default — barato.

### D5 — O componente mora em `core:ui`, e o mapper também

`TransactionLegCard` em `core/ui/.../ui/component/`, alimentado por um DTO plano montado por um
mapper em `core/ui/.../ui/model/`.

A forma é ditada por `presentation-mapping` §*Mappers como única fronteira*: o modelo de UI
**MUST NOT declarar campo de tipo de domínio**, e a derivação **MUST NOT ocorrer em componente
de UI**. Logo o verbo chega ao card já resolvido, como `UiText`, e não como enum de domínio:

```
TransactionLegUi(
    verb: UiText,              // resolvido pelo mapper (D2)
    name: String,              // conta ou cartão
    currencyCode: String?,     // só quando a operação toca duas moedas
    amount: DisplayAmount,     // valor + política de sinal (D3)
    invoice: ...?,             // rótulo + cor de status, quando há perna de passivo
    installment: ...?,
    onClick: (() -> Unit)?,    // ausente quando a fachada está arquivada
)
```

`DisplayAmount` é tipo de exibição, não de domínio — `money-display` §*A política de sinal tem
dono único* exige justamente que valor e política viajem juntos.

O agregado `Transaction` permanece em `ViewTransactionUiState.Content` como campo próprio,
nomeado como domínio, ao lado da lista de `TransactionLegUi` — que é o arranjo que
`presentation-mapping` §*Modelos de UI sem grafo de domínio* descreve no seu cenário
*"Agregado de domínio ao lado do modelo de exibição, não dentro dele"*. Ele continua sendo
necessário para `DeleteTransactionModal` e `EditTransactionModal`.

`InvoiceStatusColor.kt` já mora em `core:ui`, então o status colorido dentro do card não puxa
dependência nova.

`DetailRow`, hoje duplicado entre `ViewTransactionModal.kt:510` e `ViewAdjustmentModal`, sobe
junto: as linhas de contexto que restam (data, recorrência) precisam dele nos dois.

### D6 — A ordem dos cards vem da perna primária, não de uma regra nova

Primeiro card: `Transaction.primaryEntry` — a perna monetária negativa (`Transaction.kt:47-64`).
Depois, as demais.

Isso não é coincidência com a taxa: `appliedRate` já divide de `out` (a perna negativa) para
`into` (`ViewTransactionUiState.kt:135-150`). A seta do conector e o sentido do quociente
concordam **por construção**, e nenhum dos dois precisa ser afirmado separadamente. Consumir a
definição existente em vez de reimplementar "a que sai primeiro" é o que `presentation-mapping`
§*A escolha da perna neutra tem um dono* exige.

### D7 — O ajuste usa a mesma composição

`ViewAdjustmentModal` passa a montar `TransactionLegUi` pelo mesmo mapper. O verbo sai do
override de `EQUITY`, o sinal é explícito por D3, e a fatura de um ajuste de fatura entra no
card do passivo como em qualquer outra perna.

O que **não** é unificado: os portões e as mensagens de bloqueio. Um ajuste não é editável por
natureza, e `ViewAdjustmentUiState` já expressa isso; fundir os dois estados criaria um estado
com campos que metade dos casos ignora.

### D8 — O alcance da moeda base encolhe, e o teste obriga a declarar

`ViewTransactionViewModel` deixa de injetar `IBaseCurrencyRepository`. `BaseCurrencyReachTest`
lista o arquivo como permitido (`BaseCurrencyReachTest.kt:98`) e assere **igualdade nos dois
sentidos** — a entrada vira `GONE` e o teste falha até ser removida junto com o comentário que
a justifica. Isso é uma propriedade desejável do teste, não um obstáculo: ele força a redução
de alcance a ser um ato declarado.

## Risks / Trade-offs

**[Os testTags do Maestro estão na linha que morre]** → `view_transaction_amount` está no
`DetailRow` de `Valor` (`ViewTransactionModal.kt:223`) e é asserido em 5 pontos de 2 flows
vivos: `.maestro/flows/ledger/lifecycle.yaml:262,321,324` e
`.maestro/flows/creditcards/lifecycle.yaml:477,480`. Com dois cards, uma tag única deixa de
identificar um elemento. Mitigação: `view_transaction_amount` passa a marcar o valor do
**primeiro** card (a perna primária), e o segundo ganha tag própria. Nos casos monomoeda que
os flows exercitam, a perna primária é a mesma figura que a linha exibia, então as asserções
seguem válidas — mas isso **precisa ser verificado numa execução real**, não presumido.

**[O gasto simples fica mais alto]** → Um card onde havia uma linha, no caso mais comum de
todos. Mitigação: o card é compacto (verbo em texto pequeno acima, nome e valor na mesma
linha), e três linhas somem do mesmo layout (`Valor`, `Origem`, `Conta`). O saldo vertical é
próximo de neutro.

**[Quebra de api por um único consumidor]** → Remover `perspective` de `TransactionsEntry`
altera um módulo `api`, que é a superfície mais cara de mexer. Mitigação: o único call site que
passa o argumento é `AccountsScreen.kt:376-379`, e os outros seis já chamam a sobrecarga sem
ele. Se a ênfase visual (D4, alternativa) for adotada depois, volta como parâmetro opcional.

**[Transações sem título nem categoria perdem texto]** → O cabeçalho passa a ter uma linha só.
Mitigação: é o comportamento correto, e o formulário torna o caso difícil de alcançar —
`BuildTransactionError.TitleOrCategoryRequired` exige um ou outro. Transferências e pagamentos,
que legitimamente não têm nem um nem outro, passam a se anunciar pela natureza, que é mais
informativa do que o `"Untitled"` que apareceria.

**[Dois produtores da política de sinal]** → `itemDisplayAmount` e o mapper de perna podem
divergir com o tempo. Mitigação: `money-display` §*O ajuste lê igual em toda superfície* é o
invariante que amarra os dois, e o ajuste é o único caso em que ambos exibem sinal. Um teste
que abra o mesmo ajuste na lista e no detalhe cobre a divergência inteira.

**[O verbo é texto novo em duas línguas]** → Cinco chaves novas, cada uma em `values` e
`values-en`. Mitigação: uma chave presente em só um dos arquivos é um bug conhecido do projeto
e a revisão do change verifica os dois.
