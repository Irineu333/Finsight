# 002 — `is_recurring` é descartado quando `installments > 1`, e a resposta não diz isso

**Área:** mcp / transactions · **Tipo:** dados · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`RegisterTransactionUseCase` retorna pelo caminho do parcelamento **antes** de `isRecurring` ser
lido, então uma chamada que passa os dois argumentos não abre template nenhum. A tool oferece os
dois argumentos, não recusa a combinação, e responde como se a divisão fosse o pedido inteiro.

## Evidência

`feature/transactions/impl/.../RegisterTransactionUseCaseImpl.kt:23-27`

```kotlin
if (form.installments > 1) {
    return@either TransactionRegistration.Installments(
        addInstallment(form, form.installments).bind()
    )
}
```

`isRecurring` é o segundo parâmetro de `invoke` e só é consultado na linha 32, depois desse return.

- `feature/mcp/impl/.../tool/TransactionWriteTools.kt:140-141` lê os dois argumentos.
- `TransactionWriteTools.kt:174` passa os dois ao use case.
- `TransactionWriteTools.kt:210-213` responde `"Recorded as N instalments, one per invoice they
  land on."` — a frase sobre o template vive apenas no ramo `Single` (`:214-219`), então a
  repetição descartada nunca é mencionada.
- A descrição da tool promete os dois comportamentos no mesmo parágrafo
  (`TransactionWriteTools.kt:106-108`).

A própria sheet do app torna o par inalcançável, e diz por quê:

`feature/transactions/impl/.../addTransaction/AddTransactionViewModel.kt:238-249`

```kotlin
/**
 * Splitting into instalments drops the recurring mark rather than hiding it: paying
 * in instalments is already a repetition, and a mark the sheet no longer shows must
 * not survive to the submit …
 */
private fun changeInstallments(installments: Int) = input.update {
    it.copy(installments = installments, isRecurring = it.isRecurring && installments == 1)
}
```

A tela descarta a marca *porque deixa de exibi-la*. A tool nunca exibiu nada, então o mesmo descarte
é silencioso.

## Cenário de falha

`create_transaction(card_id: 1, amount: 300, installments: 3, is_recurring: true)`

Três lançamentos de parcela são gravados, nenhum template recorrente existe, e a resposta diz
`"Recorded as 3 instalments, one per invoice they land on."` O agente então informa ao usuário que
a assinatura vai se repetir todo mês. Não vai.

## Correção sugerida

Recusar a combinação em `CreateTransactionTool.call`, nomeando os dois argumentos — eles são
mutuamente exclusivos pelo raciocínio do próprio domínio, e uma recusa custa um round trip enquanto
o descarte silencioso custa uma afirmação errada ao usuário.

Alternativamente (ou além disso), tornar o descarte visível no `note` da resposta — mas a recusa é a
escolha melhor: o agente pediu algo que o domínio não modela, e deve aprender isso em vez de inferir.
