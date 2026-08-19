# 024 — A edição ainda descarta dois argumentos em silêncio, e recusa o cartão carregado como se fosse dado

**Área:** mcp · **Tipo:** correção · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-19, `feature/local-mcp-server`, por uma revisão adversarial da correção da
[016](archive/016-update-transaction-drops-the-category-silently.md)

## O que está errado

A [016](archive/016-update-transaction-drops-the-category-silently.md) auditou o que
`TransactionForm.from` descarta e instalou três recusas. Ninguém auditou o que a **própria tool**
descarta depois disso, nem revisou a redação da primeira recusa para o caso em que o cartão não foi
dado pela chamada.

### 1. `invoice_month` aceito e ignorado quando o lançamento não está num cartão

`invoiceDueMonth` não aparece na normalização de `TransactionForm.from`
(`core/model/.../form/TransactionForm.kt:80-84`) — quem o ignora é
`BuildTransactionUseCaseImpl.kt:34-52`, no ramo da conta. Então a recusa que a 016 instalou não o
alcança, e a resposta segue dizendo *"Edited. Everything the call did not name kept the value it
had."* para um mês de fatura que a chamada nomeou e que não foi a lugar nenhum. Vale também quando a
mesma chamada move o lançamento para uma conta com `account_id` + `invoice_month`.

### 2. `title: ""` aceito e ignorado

`ToolSupport.kt:105-106` devolve `null` para uma string em branco, então um título explicitamente
vazio é lido como *não disse nada* e o título antigo permanece — sob a mesma frase de sucesso. O
`confirm_recurring` documenta um título vazio como a forma de apagar um (`RecurringOperationTools.kt`
KDoc), então a superfície diz as duas coisas.

### 3. A recusa do cartão nomeia `card_id` mesmo quando a chamada não o deu

`TransactionWriteTools.kt:437-438` resolve `card` como `namedCard ?: storedCard.takeIf {
namedAccount == null }`, e a guarda de `:453` dispara sobre esse valor. Num lançamento que **já
está** num cartão, `update_transaction(id, type: "income")` responde:

> `A card takes expenses only: with type income, give account_id and not card_id.`

A metade acionável está certa — dar `account_id` é o que resolve. Mas mandar não dar um `card_id`
que a chamada não deu é a forma que a própria 016 corrigiu na terceira recusa, onde a categoria
carregada é descrita como consequência e não como argumento errado. A primeira recusa não recebeu o
mesmo tratamento.

## Por que baixa

Nenhum dos três grava número errado no ledger. O 1 e o 2 fazem a resposta afirmar mais do que
aconteceu — o defeito de faixa média da 016 — mas sobre campos que não mudam saldo, e nenhum deles
descarta algo que o lançamento já tinha.

## Observação sobre cobertura

Todo teste de `update_transaction` da suíte roda sobre `world.groceriesId`, que é um lançamento de
**conta**. Nenhum edita um lançamento que está num cartão, e é por isso que 1 e 3 não apareceram.
