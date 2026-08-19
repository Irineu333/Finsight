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

## Correção aplicada

A validação passou a vir antes de qualquer fatura, e sem uma segunda cópia da regra:
`AddInstallmentUseCaseImpl` agora chama `buildTransactionUseCase(form)` no topo do `invoke` e passa
o intent pronto adiante, em vez de construí-lo dentro de `registerTransactions`. Construir é o que
valida — `BuildTransactionUseCaseImpl` chama `ValidateTransactionFormUseCase` como primeira
instrução e só resolve a fatura depois —, então mover a chamada põe a guarda de valor positivo na
frente sem que `AddInstallmentUseCaseImpl` passe a conhecer a regra. A dona continua sendo uma só.

Os dois `ensureNotNull` do formulário (`creditCard`, `invoiceDueMonth`) ficaram antes da construção
de propósito: não têm efeito colateral, e mantê-los ali preserva a identidade dos erros —
`invoiceDueMonth` nulo segue respondendo `InstallmentError.MissingInvoice` em vez de degradar para
`BuildTransactionError.InvoiceRequired`.

O teste conta faturas, como esta issue exigia: `FakeInvoiceOpener` passou a contar chamadas e
`AddInstallmentUseCaseTest` afirma que uma recusa abre zero. Conferido que ele morde — antes da
correção, 12 parcelas produziam **13 chamadas** ao abridor e 3 parcelas produziam 4, e os dois
testes novos eram os únicos vermelhos do módulo (`12 tests completed, 2 failed`). Depois, 12/12
verdes e 172/172 no módulo.

## Onde a issue estava imprecisa

Duas coisas, nenhuma delas mudando o veredito:

- O cenário de falha escreve `create_installment(amount: -300, installments: 12, ...)`. O parâmetro
  da tool chama-se `count`, não `installments` (`InstallmentWriteTools.kt`, `required = listOf(
  "card_id", "amount", "count")`). O `installments` existe no `create_transaction`, que é o outro
  caminho que a issue cita.
- "O teste tem de contar faturas criadas" está certo, mas o teste **não** pode ser over-the-protocol:
  o mundo do módulo `mcp` não exercita este use case. `AgentWorldWrites.kt` monta um dublê
  (`WorldAddInstallment`) com uma ordem própria, porque `feature/mcp/impl` não pode depender de
  `feature/creditcards/impl` (regra impl ⊄ impl). Um teste de `create_installment` pela rede teria
  medido o dublê e passado em verde sobre o defeito. O teste vive no módulo dono do use case.

## O que ficou para trás, deliberadamente

A primeira fatura é resolvida duas vezes — uma dentro do `buildTransactionUseCase`, outra na linha
seguinte, para obter o `Invoice` de que `getSlots` precisa. É leitura redundante, não fatura a mais:
`GetOrCreateInvoiceForMonthUseCaseImpl` procura por `dueMonth` e devolve o que encontrar, criando
apenas quando não há nada. Removê-la exigiria derivar o `Invoice` do `dimensionId` do intent, o que
é pior do que a leitura extra.
