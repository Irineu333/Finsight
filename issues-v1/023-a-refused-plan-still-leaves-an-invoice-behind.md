# 023 — Uma parcela bloqueada no meio do plano deixa a primeira fatura aberta para trás

**Área:** creditcards · **Tipo:** dados · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-19, `feature/local-mcp-server`, por uma revisão adversarial da correção da
[017](archive/017-installment-opens-invoices-before-refusing.md)

## O que está errado

A [017](archive/017-installment-opens-invoices-before-refusing.md) fechou a recusa **do formulário**:
um valor não positivo passou a ser recusado antes de qualquer fatura. A recusa que vem **depois** da
construção continua deixando estrutura para trás, e é o mesmo dano — menor, porque é uma fatura e
não doze.

`AddInstallmentUseCaseImpl.kt:56` constrói o lançamento, e construir resolve a primeira fatura
(`BuildTransactionUseCaseImpl.kt:62`). Só então `getSlots` (`:90-97`) percorre os meses seguintes e
recusa se algum estiver fechado.

## Cenário de falha

Cartão com fatura aberta para 2026-08 e fatura **fechada** para 2026-10; três parcelas a partir de
2026-09. Medido pela revisão:

```
antes  = [(2026-10, CLOSED), (2026-08, OPEN)]
result = InstallmentException: Installment 2 landed on a CLOSED invoice
depois = [(2026-10, CLOSED), (2026-09, FUTURE), (2026-08, OPEN)]
transações = 0
```

A fatura de 2026-09 foi aberta e ficou. **Não é regressão** — antes da 017 a linha `:47` fazia o
mesmo —, mas o arquivo da 017 discute só a leitura redundante e não menciona este caso.

Mesma forma para uma escrita que falha: `registerTransactions` desfaz a linha do parcelamento
(`:156-160`) e não desfaz as N faturas que `getInvoices` abriu (`:119-122`). Este segundo caso é
raciocínio sobre o código, não medição — a revisão não conseguiu fazer o escritor real falhar.

## Observação, do mesmo mecanismo

`InstallmentError.BlockedInvoice(installment = 1)` é inalcançável: o slot 1 sempre encontra a fatura
que `getOrCreateInvoiceForMonthUseCase` acabou de validar, e essa validação recusa uma fatura fechada
primeiro (`GetOrCreateInvoiceForMonthUseCaseImpl.kt:38-44`). A primeira parcela é recusada por
`InvoiceException(BlockedInvoice)`, sem dizer qual parcela; as demais, por
`InstallmentException(BlockedInvoice(installment = N))`, dizendo. Pré-existente e inalterado pela 017.
