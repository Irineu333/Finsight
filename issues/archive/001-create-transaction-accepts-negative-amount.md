# 001 — valor negativo aceito na superfície de escrita, e registrado na direção oposta

**Área:** mcp · **Tipo:** dados · **Criticidade:** alta · **Status:** corrigida em 2026-08-18
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`
**Reconferido em:** 2026-08-18, @ `32310927a` — o achado é maior do que o registrado: são **cinco**
tools que alcançam o ledger, não uma, e a mais curta delas não passa por validador algum. O nome do
arquivo guarda o caso que originou o achado; `create_transaction` é a porta mais curta de descrever,
não a única.

## O que está errado

`create_transaction` lê `amount` sem nenhuma checagem de sinal. Um valor negativo chega intacto ao
ledger, onde `EXPENSE -> -cents` o transforma em um **crédito** na conta: o lançamento que o usuário
pediu para registrar como gasto **aumenta** o saldo e **reduz** o total de despesas do mês.
`update_transaction`, no mesmo arquivo, recusa exatamente isso e diz por quê.

O mecanismo é do domínio, não da tool — por isso ele reaparece em toda tool que escreve um valor
por esse caminho (ver *Toda a superfície que aceita o sinal*, abaixo).

## Evidência

| Onde | O que faz |
|---|---|
| `feature/mcp/impl/.../tool/TransactionWriteTools.kt:137` | `arguments.requiredMoney("amount")` — sem guarda |
| `feature/mcp/impl/.../tool/WriteSupport.kt:156-164` | `money()` aceita qualquer double, sinal incluído |
| `feature/mcp/impl/.../tool/WriteSupport.kt:167` | `asFormAmount()` → `(-40.0 * 100).roundToLong()` = `"-4000"` |
| `core/common/.../extension/MoneyFormatter.kt:3-8` | `moneyToDouble()` respeita o `-` inicial |
| `feature/transactions/impl/.../ValidateTransactionFormUseCaseImpl.kt:35` | `ensure(form.amount.moneyToDouble() != 0.0)` — só o zero é recusado |
| `feature/transactions/impl/.../BuildTransactionUseCaseImpl.kt:46,70` | perna com `amount = form.amount.moneyToDouble()` |
| `core/ledger/.../LedgerEntryWriter.kt:281-288` | `EXPENSE -> -cents`, logo `-(-4000) = +4000` na perna `ASSET` |
| `feature/mcp/impl/.../tool/TransactionWriteTools.kt:326-347` | `update_transaction` recusa `amount <= 0.0`, com um comentário que nomeia essa falha |

O próprio schema da tool já enuncia a regra que não é aplicada
(`TransactionWriteTools.kt:111-115`: *"Always positive: `type` says the direction."*).

## Cenário de falha

`create_transaction(type: "expense", amount: -40, account_id: 1, title: "x")`

1. O formulário é válido (`amount != 0.0`), então nada recusa.
2. O writer lança `+4000` na conta `ASSET` e `-4000` no nominal de `EXPENSE`.
3. `Σ = 0` se mantém, então o write boundary aceita.
4. O saldo **sobe** 40; o total de despesas do mês **cai** 40; o total da própria categoria fica
   contaminado por uma perna nominal negativa (orçamento e relatório leem a mesma soma).
5. A resposta informa `nature: "expense"`, `amount: 40` — `deriveTransactionLabel` vê uma conta
   `EXPENSE` entre as entries (`core/ledger/.../extension/Ledger.kt:56-65`) e `itemDisplayAmount`
   devolve a magnitude. Nada no payload diz que a direção inverteu.

## Toda a superfície que aceita o sinal

Reconferido tool a tool em 2026-08-18. São três grupos, e só o primeiro é este defeito.

**Chega ao ledger e inverte a direção** — o mecanismo da tabela acima, por cinco portas:

| Tool | Onde lê | Por que passa |
|---|---|---|
| `create_transaction` | `TransactionWriteTools.kt:137` | `ValidateTransactionFormUseCaseImpl.kt:35` só recusa zero |
| `create_installment` | `InstallmentWriteTools.kt:78` | `AddInstallmentUseCaseImpl` → `BuildTransactionUseCaseImpl.kt:30` → o mesmo validador |
| `confirm_recurring` | `RecurringOperationTools.kt:161` | **nenhum validador**: `ConfirmRecurringUseCaseImpl.kt:72` faz `cycleAmount = amount ?: recurring.amount` e o entrega direto a `TransactionLeg` (`:108-110`, `:131-133`) |
| `create_recurring` | `RecurringWriteTools.kt:97` | `RecurringForm.kt:40` só recusa zero; o negativo dorme no template e alcança o ledger no primeiro `confirm_recurring` |
| `update_recurring` | `RecurringWriteTools.kt:196` | mesmo form, mesmo caminho |

`confirm_recurring` é a mais curta das cinco, e a única que não passa nem por `TransactionForm`: o
argumento opcional `amount` (*"What this cycle was actually worth, when it differs from the
template's"*) vai do JSON à perna do lançamento sem uma checagem no meio.

**Grava um número negativo que o ledger não vê** — errado, de outra ordem:

| Tool | Onde lê | Por que passa |
|---|---|---|
| `update_card` | `CardWriteTools.kt:141` | `UpdateCreditCardUseCaseImpl.kt:28-41` valida **só o nome**; o `limit` entra pelo `copy` |
| `create_budget` / `update_budget` | `BudgetWriteTools.kt:99`, `:173` | `BudgetLimit.kt:45-49` repassa `amount` em `FIXED` sem checar sinal |

**Já protegidas** — nenhuma correção devida:

| Tool | Guarda |
|---|---|
| `transfer` | `TransferBetweenAccountsUseCaseImpl.kt:40-46`, nas duas pontas |
| `pay_invoice`, `advance_invoice_payment` | `AdvanceInvoicePaymentUseCaseImpl.kt:44,48` |
| `update_installment` | `UpdateInstallmentUseCaseImpl.kt:31` |
| `create_card` | `CreditCardForm.kt:33-35` (`limitValue < 0`) |
| `update_transaction` | a guarda própria da tool, `TransactionWriteTools.kt:333-347` |

**Onde o negativo é legítimo e não deve ser recusado** — registrado para que a correção não os alcance
por descuido:

- `adjust_balance` (`target_balance`, `AccountOperationTools.kt:73`) — o argumento é um *saldo*, e uma
  conta pode estar negativa.
- `adjust_invoice` (`target`, `InvoiceOperationTools.kt:456`) — o use case posta a **diferença**
  (`AdjustInvoiceUseCaseImpl.kt:56`), sem inverter sinal nenhum.

## Correção sugerida

Recusar `amount` não positivo em `CreateTransactionTool.call`, nas mesmas palavras que
`UpdateTransactionTool` já usa. A duplicação lá é deliberada — o comentário em
`TransactionWriteTools.kt:329-332` explica que o domínio recusa o zero e não isto, *porque nenhuma
tela tem campo capaz de expressá-lo* — e um agente tem.

Mas uma guarda por tool não fecha as cinco: `confirm_recurring` não tem formulário onde pô-la, e
repetir a mesma checagem em cinco lugares é a duplicação que a regra de derivação existe para
impedir. **O dono é o domínio** — o `ensure(amount > 0.0)` que `TransferBetweenAccountsUseCase`,
`AdvanceInvoicePaymentUseCase` e `UpdateInstallmentUseCase` já aplicam, aplicado também em:

- `ValidateTransactionFormUseCase` — fecha `create_transaction` e `create_installment`;
- `RecurringForm.toRecurring` — fecha `create_recurring` e `update_recurring`;
- `ConfirmRecurringUseCase` — fecha `confirm_recurring`, que não passa pelos dois anteriores.

Aí a tela e o agente passam a ser recusados pela mesma regra, que é o que o item 16 da auditoria de
julho pede. `TransactionType.ADJUSTMENT` não é objeção: ele nunca entra por `TransactionForm` — é
derivado de uma perna `EQUITY` (`Ledger.kt:105`) e a superfície MCP nem o oferece
(`TransactionWriteTools.kt:50-53` mapeia só `expense` e `income`).

Um `positiveMoney()` ao lado de `requiredMoney` continua valendo como segunda linha, para que a
recusa chegue nomeada ao agente antes de virar exceção de domínio — mas ele **não** cobre
`update_card`, `create_budget` e `update_budget`, cujas regras (*"um limite de cartão não é
negativo"*, *"um orçamento não é negativo"*) pertencem ao domínio de cada um.

## Observações

A lacuna de domínio por trás disso já está registrada como item 16 de
`docs/auditoria-bugs-2026-07.md` ("Valor negativo aceito no formulário de transação"), onde foi
**rebaixada para média** sob o argumento de que o sinal fica visível no campo e não há caminho
acidental até ele. A superfície MCP remove as duas atenuantes: um agente escreve `-40` num argumento
JSON, ninguém vê campo nenhum, e a tool responde "Recorded."

`confirm_recurring`, `update_card`, `create_budget` e `update_budget` foram levantados na
reconferência de 2026-08-18 e **não constam da revisão original**. Os três últimos não corrompem
número nenhum do ledger — por isso ficam aqui em vez de virarem issues próprias —, mas são a mesma
omissão: um valor com sinal aceito porque nenhuma tela jamais o ofereceu.

## Correção aplicada

A guarda ficou no domínio, como esta issue pedia, e não uma por tool. `AmountZero` e `AMOUNT_ZERO`
foram **ampliados** em vez de ganharem um irmão — zero e negativo são uma regra só, e dois erros para
uma regra é a deriva que o KDoc de `RecurringForm` diz existir para impedir. Hoje se chamam
`AmountNotPositive` e `AMOUNT_NOT_POSITIVE`, e dizem *"Amount must be greater than zero."*

As cinco portas para o ledger fecham em três pontos:

| Onde | Fecha |
|---|---|
| `ValidateTransactionFormUseCaseImpl` | `create_transaction`, `create_installment` |
| `RecurringForm.toRecurring` | `create_recurring`, `update_recurring` |
| `ConfirmRecurringUseCaseImpl` | `confirm_recurring` |

A guarda de `confirm_recurring` fica **antes** de `getOrCreateInvoiceForMonthUseCase`: essa resolução
abre fatura como efeito colateral deliberado fora da unidade de trabalho (design D7), e recusar
depois dela deixaria fatura órfã para um ciclo que nunca postou. `ConfirmRecurringAmountTest` fixa
essa ordem com um contador que também lança se for alcançado.

Fora do ledger, `budgetLimit` passou a recusar o valor **resolvido** abaixo de zero — uma checagem só,
que alcança tanto o `FIXED` que recebe o número quanto o `PERCENTAGE` que o deriva de uma fração
negativa. O erro novo é `BudgetError.NEGATIVE_LIMIT`, com chave nos dois idiomas.

### Onde esta issue estava errada

`update_card` **já estava protegido**, e a issue afirmava o contrário. A leitura tinha parado em
`UpdateCreditCardUseCaseImpl`, que de fato valida só o nome — mas o `init` de `CreditCard`
(`core/model/.../domain/model/CreditCard.kt:40-42`) recusa `limit < 0`, e `update_card` expressa a
edição como `card.copy(limit = ...)` (`CardWriteTools.kt:146-152`), que roda o construtor primário.
A recusa vira `Left` antes mesmo da checagem de nome. Uma guarda no use case seria código inalcançável.
O que ficou no lugar dela foram testes que fixam o comportamento existente — negativo recusado com
`NEGATIVE_LIMIT` e nada chegando ao repositório, zero aceito, positivo gravado.

`adjust_balance` e `adjust_invoice` não foram tocados: `AdjustBalanceUseCaseImpl` posta
`TransactionLeg` direto, sem passar por `TransactionForm`, então o saldo-alvo negativo segue legítimo.
