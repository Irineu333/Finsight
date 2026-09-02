---
area: creditcards
severity: medium
type: navigation
version: 1.10.0
---

# O extrato da fatura abre na fatura cujo id é igual ao do cartão, porque o parâmetro ausente cai no anterior do mesmo tipo

## Cenário

**DADO** o primeiro cartão criado no app (`credit_cards.id = 1`), cuja primeira fatura também
recebeu `invoices.id = 1` e já foi paga, e cuja fatura aberta é a de `id = 2`
**QUANDO** o usuário toca no cartão na tela de cartões — sem nomear fatura nenhuma
**ENTÃO** o extrato às vezes abre na fatura **paga** de `id = 1`, com os lançamentos, o total e
os comandos daquele status
**DEVERIA** abrir na fatura aberta, que é o que `setInitialInvoice()` foi escrito para fazer

## Mecânica

`InvoiceTransactionsScreen` pede o view model com `parametersOf(creditCardId, invoiceId)`, e
`invoiceId` é `null` quando ninguém nomeou fatura. A definição Koin lê os dois do mesmo saco:

```kotlin
creditCardId = it.get(),
initialInvoiceId = it.getOrNull(),
```

Os dois são `Long`, e `ParametersHolder` resolve **por tipo**. Em Koin 4.1.1, `parametersOf`
cria o holder com `useIndexedValues = null`, e nesse modo `getOrNull(clazz)` é
`getIndexedValue(clazz) ?: getFirstValue(clazz)`. Com `_values = [1L, null]`:

- `it.get<Long>()` → `getIndexedValue` devolve `_values[0] = 1L` e avança o índice → `creditCardId = 1`
- `it.getOrNull<Long>()` → `getIndexedValue` olha `_values[1] = null`, e `Long::class.isInstance(null)`
  é falso, então cai no **fallback** `getFirstValue`, que é
  `_values.firstOrNull { Long::class.isInstance(it) }` → **`1L` outra vez**

Ou seja: `initialInvoiceId` recebe o **id do cartão**. Quando a fatura *é* nomeada o caminho é
o indexado e funciona — o defeito é exatamente o caso "sem fatura nomeada", que é como se entra
pela tela de cartões.

A partir daí, o `onEach` de `invoicesFlow` faz `invoices.indexOfFirst { it.id == initialInvoiceId }`
e seleciona a fatura cujo id casa com o do cartão. Como `credit_cards.id` e `invoices.id` são
autoincrementos independentes começando em 1, o primeiro cartão e a primeira fatura colidem.

**O parâmetro errado é incondicional; o sintoma é que oscila.** Duas coisas escrevem
`selectedInvoiceIndex` no arranque: `setInitialInvoice()`, disparado no `init` como corrotina,
que faz `indexOfFirst { it.status.isOpen }` (o certo); e o `onEach` acima, que aplica o
`initialInvoiceId` na primeira emissão da lista. Quem escrever por último manda, e nenhuma das
duas espera pela outra — por isso a fatura errada aparece uma vez e não na seguinte. O defeito
em si não depende disso: o `initialInvoiceId` chega com um valor que não é id de fatura em
**toda** abertura pela lista de cartões.

**Por que a fatura errada é justamente a mais antiga.** `AddCreditCardUseCase.invoke()` insere o
cartão e só então abre a primeira fatura, de modo que no primeiro cartão de qualquer instalação
`card.id == invoice.id == 1` — e essa fatura 1 é sempre a mais velha dele, que o
`ORDER BY openingMonth DESC` põe na última página do pager.

## Evidência

- `feature/creditcards/impl/.../invoiceTransactions/InvoiceTransactionsScreen.kt:112,115` —
  `invoiceId: Long? = null` e `parametersOf(creditCardId, invoiceId)`
- `feature/creditcards/impl/.../creditCards/CreditCardsScreen.kt` — `onCardClick`, que navega
  com `InvoiceTransactionsRoute(creditCardUi.cardId)`: só o cartão, o `invoiceId` fica no default
- `feature/creditcards/api/.../CreditCardsRoutes.kt` — `InvoiceTransactionsRoute(val creditCardId: Long, val invoiceId: Long? = null)`;
  e `feature/creditcards/impl/.../navigation/CreditCardsGraph.kt`, que repassa `route.invoiceId`
  intacto — até aqui o `null` está correto
- `feature/creditcards/impl/.../usecase/AddCreditCardUseCase.kt` — `invoke()` insere o cartão e
  abre a fatura em seguida: é o que faz `card.id == invoice.id` no primeiro cartão
- contraste: nenhuma outra definição Koin do projeto lê dois parâmetros do mesmo tipo com o
  segundo anulável — conferido em `feature/*/impl/.../di/*.kt`
- `feature/creditcards/impl/.../di/CreditCardsModule.kt:259-260` — `creditCardId = it.get()` e
  `initialInvoiceId = it.getOrNull()`, os dois `Long`
- `org/koin/core/parameter/ParametersHolder.kt` (koin-core 4.1.1, lido do jar de fontes) —
  `getOrNull(clazz)`: `null -> getIndexedValue<T>(clazz) ?: getFirstValue<T>(clazz)`;
  `getFirstValue` é `_values.firstOrNull { clazz.isInstance(it) }`; e `parametersOf` constrói
  com `useIndexedValues = null`
- `feature/creditcards/impl/.../invoiceTransactions/InvoiceTransactionsViewModel.kt` — o
  `onEach` de `invoicesFlow` com `invoices.indexOfFirst { it.id == initialInvoiceId }`, e
  `setInitialInvoice()`, chamado do `init`, com `indexOfFirst { it.status.isOpen }` — as duas
  escritas que correm
- `core/database/.../dao/InvoiceDao.kt` — `observeInvoicesByCreditCard()` e
  `getAllInvoicesByCreditCard()`, ambas `ORDER BY openingMonth DESC`: as duas listas concordam,
  então a ordenação não é o defeito

Observado no aparelho (AVD `finsight_e2e`, `emulator-5554`, os sete checks da §2.2 conferidos
por serial), no fluxo `creditcards_lifecycle` da suíte Maestro:

- o passo falha em `invoice_expenses_amount` esperando `[-][$]33[.,]00`; a captura da falha
  mostra o extrato em "September, 2026", **Paid**, com `Expenses −$120.00` e
  `Advance Payments +$120.00` — a fatura anterior, não a que tem os $33,00
- o banco extraído do aparelho logo depois (`run-as … cat databases/finsight.db*`) tem
  exatamente duas faturas do cartão `id = 1`: `id 1 | 2026-08 | 2026-09 | PAID` e
  `id 2 | 2026-09 | 2026-10 | OPEN`. A tela abriu na `id 1`; a única `OPEN` é a `id 2`
- o mesmo fluxo, rodado sozinho no mesmo aparelho e no mesmo dia, **passou** por essa asserção
  e falhou adiante — a corrida, não o calendário

## Consequência

O usuário chega a uma fatura que não escolheu, e os comandos que a tela oferece são os do
status **daquela** fatura: reabrir uma paga, ou pagar/fechar a errada, está a um toque, e nada
na tela sinaliza que houve troca. Atinge o primeiro cartão de qualquer instalação — o par
`(cartão 1, fatura 1)` é o que todo mundo cria primeiro — e some sozinho quando a fatura de
`id = 1` deixa de pertencer àquele cartão, o que torna o relato difícil de reproduzir.

Como efeito colateral, é o defeito por trás do vermelho intermitente de `creditcards_lifecycle`,
que até aqui só estava registrado como dependência de calendário
(`the-credit-card-flow-depends-on-where-its-forty-five-day-jump-lands`, seção "Não confirmado").

## Sugestão

Duas partes, e a segunda vale mesmo que a primeira resolva o sintoma:

1. Tirar a ambiguidade da injeção — `parameterArrayOf` em vez de `parametersOf` (que fixa
   `useIndexedValues = true` e elimina o fallback por tipo), ou destruturar por posição
   (`viewModel { (cardId: Long, invoiceId: Long?) -> … }`), ou envelopar o id opcional num tipo
   próprio que não colida com `Long`.
2. Fazer as duas escritas de `selectedInvoiceIndex` deixarem de correr: hoje `setInitialInvoice()`
   e o `onEach` decidem a mesma coisa por caminhos independentes. Uma seleção só, resolvida na
   primeira lista que chega — e por identidade, não por posição, que é o que
   `the-selected-invoice-is-a-position-in-a-list-that-moves-underneath-it` já pede.

Não vinculante.
