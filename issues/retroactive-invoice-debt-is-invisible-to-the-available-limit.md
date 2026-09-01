---
area: creditcards
severity: medium
type: data
---

# Fatura retroativa com dívida não entra no limite comprometido do cartão

## Cenário

**DADO** um cartão de limite R$ 5.000, com uma fatura `RETROACTIVE` devendo R$ 2.000 —
lançada para regularizar um mês esquecido — e uma `OPEN` devendo R$ 500
**QUANDO** o usuário olha o cartão
**ENTÃO** lê "limite disponível R$ 4.500" e a barra de uso em 10%
**DEVERIA** ler R$ 2.500 e 50%: a dívida retroativa está viva, e o próprio
`CloseInvoiceUseCase` documenta que uma fatura retroativa com saldo é dívida real, a ser
paga explicitamente

## Mecânica

`CalculateAvailableLimitUseCase` soma o que a query de faturas não pagas devolve, e essa
query é `WHERE status NOT IN ('PAID', 'RETROACTIVE')` — trata retroativa como liquidada.
`observeUnpaidInvoices()`, que alimenta o dashboard, repete a exclusão, e
`InstallmentUiMapper.toRowUi()` marca `isSettled = true` para `PAID` **e** `RETROACTIVE`.

No resto da feature `RETROACTIVE` significa o contrário: é `isEditable` (aceita gasto
novo), é `isPayable`, e `CloseInvoiceUseCase` traz um comentário explicando que uma
retroativa com saldo *"closes like any other, and is paid explicitly"* — ramo que só faz
sentido se ela puder dever. As duas leituras se contradizem, e cada lugar enumera os status
por conta própria em vez de ler um predicado.

## Evidência

- `core/database/.../dao/InvoiceDao.kt` — `getUnpaidInvoicesByCreditCard` e
  `observeUnpaidInvoices`, ambos com `NOT IN ('PAID', 'RETROACTIVE')`
- `feature/creditcards/impl/.../usecase/CalculateAvailableLimitUseCase.kt` — soma só essa
  lista
- `feature/creditcards/impl/.../usecase/CloseInvoiceUseCase.kt` — o comentário e o ramo
  `invoice.status.isRetroactive && invoiceAmount == 0.0`
- `core/model/.../model/Invoice.kt` — `Status.isPayable` e `Status.isEditable` incluem
  `RETROACTIVE`
- `feature/creditcards/impl/.../mapper/InstallmentUiMapper.kt` — `toRowUi()`, o outro lado
  da contradição
- `core/ui/.../component/CreditCardCard.kt` — renderiza `availableLimit`
- `CalculateAvailableLimitUseCaseTest` — nenhum caso com `RETROACTIVE`

## Consequência

Limite disponível superestimado e uso subestimado enquanto houver fatura retroativa aberta
— exatamente o período em que o usuário está regularizando e mais precisa do número certo.

## Sugestão

Decidir o significado de `RETROACTIVE` num lugar só, como um predicado em `Invoice.Status`
ao lado dos outros, e fazer a query, o mapper de parcela e o cálculo de limite lerem esse
predicado. Qual dos dois lados é o pretendido é decisão de produto. Não vinculante.
