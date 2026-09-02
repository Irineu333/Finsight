---
area: creditcards
severity: high
type: data
---

# "Excluir fatura" apaga as transações dela, e a confirmação não diz isso

## Cenário

**DADO** uma fatura `RETROACTIVE` de um mês passado, criada para lançar compras esquecidas,
com oito compras registradas
**QUANDO** o usuário toca em "Excluir Fatura" e confirma o texto *"Tem certeza que deseja
excluir esta fatura futura? Esta ação não pode ser desfeita."*
**ENTÃO** as oito transações são apagadas do razão junto com a fatura, sem que nada em
tela tenha mencionado transações
**DEVERIA** dizer quantos lançamentos serão apagados — ou recusar a exclusão de uma fatura
com movimento — e não chamar de "futura" uma fatura retroativa

## Mecânica

`DeleteFutureInvoiceUseCase` percorre todas as transações que carregam a dimensão da
fatura e chama `deleteTransactionById` em cada uma antes de apagar a fatura. A guarda que
o precede é `Invoice.Status.isDeletable`, que admite `FUTURE` **e** `RETROACTIVE` — e uma
fatura retroativa é `isEditable`, ou seja, aceita gasto novo por construção. É a única das
duas que costuma ter conteúdo, e é a que o texto não descreve.

A confirmação é uma string fixa, sem parâmetro: não há onde a contagem entrar.

## Evidência

- `feature/creditcards/impl/.../usecase/DeleteFutureInvoiceUseCase.kt` — o
  `forEach { transactionRepository.deleteTransactionById(it.id) }` antes de `deleteById`
- `core/model/.../model/Invoice.kt` — `Status.isDeletable` = `FUTURE || RETROACTIVE`;
  `Status.isEditable` também inclui `RETROACTIVE`
- `feature/creditcards/impl/.../invoiceTransactions/InvoiceTransactionsScreen.kt` — a ação
  é oferecida sob `summary.status.isDeletable`
- `core/resources/.../values/strings.xml` e `values-en/strings.xml` —
  `delete_future_invoice_message`, sem placeholder

## Consequência

Perda irreversível de lançamentos reais, autorizada por um texto que descreve outra coisa.
Se algum dos apagados era uma parcela, o reconciliador de parcelamento reescreve a série
por tabela — o que produz o estado descrito em
`installment-shares-keep-the-numbering-of-a-count-that-changed`.

## Sugestão

Contar as transações antes e ou recusar quando houver movimento, ou parametrizar a
mensagem com a contagem e usar um texto neutro. O laço também poderia virar
`deleteTransactionsByIds`, para que seja uma unidade de trabalho só. Não vinculante.
