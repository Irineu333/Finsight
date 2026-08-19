# 017 — `create_installment` abre até doze faturas e só então recusa o valor

**Área:** creditcards / mcp · **Tipo:** dados · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-18, por uma revisão adversarial dos dez commits desta sessão

## O que está errado

É a mesma pergunta de ordem que a [001](archive/001-create-transaction-accepts-negative-amount.md)
respondeu para o `confirm_recurring`, não respondida para o `create_installment`.

`AddInstallmentUseCaseImpl` resolve as faturas **antes** de validar o formulário:

| Linha | O que faz |
|---|---|
| `AddInstallmentUseCaseImpl.kt:47` | `getOrCreateInvoiceForMonthUseCase(...)` — cria e persiste a primeira fatura |
| `:110` | mais uma por mês faltante, até `installments` faturas |
| `:123` | `buildTransactionUseCase(form)` — a **primeira** chamada que roda `ValidateTransactionFormUseCase`, onde vive a guarda de valor positivo |

## Cenário de falha

`create_installment(amount: -300, installments: 12, card_id: 1)`

Doze faturas são abertas no cartão, o `buildTransaction` recusa com `AmountNotPositive`, nada entra
no ledger — e a estrutura de faturas fica para trás, para uma compra que nunca foi lançada.

`create_transaction` com `installments > 1` chega ao mesmo lugar: `RegisterTransactionUseCaseImpl.kt:23-27`
retorna pelo ramo do parcelamento antes do `buildTransaction`.

O caso do zero é anterior a esta sessão. O negativo é entrada que a 001 passou a rotear para cá.

## Correção sugerida

Validar antes de resolver fatura nenhuma, como o `ConfirmRecurringUseCase` já faz — a fatura criada e
não usada é o dano que o design D7 aceita quando a escrita **acontece**, não quando ela é recusada.

O teste tem de contar faturas criadas, não só observar a recusa: uma recusa que deixa estrutura para
trás passa em qualquer asserção que olhe apenas o resultado.

## Observação

O commit `ba310879e` argumenta explicitamente essa ordem para o `confirm_recurring` e nomeia
`create_installment` como uma das cinco portas que fecha. A ordem foi considerada num dos dois
lugares que precisavam dela.
