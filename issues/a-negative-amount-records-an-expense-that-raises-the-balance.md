---
area: transactions
severity: high
type: data
---

# Valor negativo é aceito e grava uma despesa que aumenta o saldo

## Cenário

**DADO** o formulário de nova transação, tipo despesa, categoria "Mercado"
**QUANDO** o usuário cola `-50` no campo de valor — o campo passa a exibir `-R$ 0,50` — e
confirma
**ENTÃO** a transação é gravada, o saldo da conta **sobe** R$ 0,50 e "Mercado" recebe uma
despesa de −R$ 0,50
**DEVERIA** recusar o valor, como a transferência já recusa (`ensure(amount > 0.0)`)

## Mecânica

A validação exige apenas que o valor não seja zero:
`ensure(form.amount.moneyToDouble() != 0.0)`, nunca `> 0`. E o sinal chega intacto ao
domínio, porque as duas peças que passam pelo texto o preservam **de propósito**:
`MoneyInputTransformation.transformInput()` lê `isNegative = text.startsWith("-")` e reescreve
`"-$formatted"`; `String.moneyToDouble()` faz o mesmo. O `keyboardOptions` do campo é só dica
de IME, não filtro.

No razão o sinal se inverte de novo: `LedgerEntryWriter.ledgerAmount()` faz
`EXPENSE -> -cents`, logo `-(-5000) = +5000` na conta `ASSET`. `Σ = 0` continua verdadeiro —
a perna nominal `EXPENSE` fica com `-5000` —, então nada no boundary rejeita.

Digitar `-` primeiro **não** funciona: `digitsOnly.isEmpty()` limpa o campo. O caminho é colar,
ou levar o cursor ao início de um valor já digitado.

## Evidência

- `feature/transactions/impl/.../usecase/ValidateTransactionFormUseCaseImpl.kt` — `invoke()`:
  `ensure(form.amount.moneyToDouble() != 0.0)`
- `core/common/.../util/MoneyInputTransformation.kt` — `transformInput()` e `formatMoney()`,
  que preservam e reescrevem o sinal
- `core/common/.../extension/MoneyFormatter.kt` — `String.moneyToDouble()`, `isNegative`
- `core/ledger/.../repository/LedgerEntryWriter.kt` — `TransactionLeg.ledgerAmount()`,
  `EXPENSE -> -cents`
- contraste: `feature/accounts/impl/.../usecase/TransferBetweenAccountsUseCase.kt` —
  `ensure(amount > 0.0)`, a mesma regra escrita onde ela existe

## Consequência

Uma despesa que credita a conta, e uma perna nominal negativa que segue para todo consumidor
da dimensão — gasto por categoria, orçamento e relatório. No detalhamento por categoria a
magnitude negativa entra na mesma escala das demais: `ComparativeMagnitudes.shareOf()` é
`magnitude / total` sem `coerceIn`, e `CategorySpendingCard` alimenta o
`LinearProgressIndicator` com `share / 100` cru, de modo que um item passa de 100% enquanto
outro fica negativo.

## Sugestão

Exigir `> 0` na validação do formulário e parar de preservar o sinal na transformação de
entrada — as duas, porque cada uma sozinha deixa a outra ser o caminho. Não vinculante.
