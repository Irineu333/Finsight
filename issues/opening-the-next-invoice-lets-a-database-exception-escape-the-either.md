---
area: creditcards
severity: high
type: crash
confirmed: no
---

# Abrir a fatura seguinte deixa uma exceção do banco escapar do `Either`

## Cenário

**DADO** uma fatura `OPEN` fechável hoje
**QUANDO** o fechamento tenta abrir o ciclo seguinte e o `INSERT` viola um dos índices
únicos de `invoices` — por dois toques rápidos no botão, que não desabilita, ou por
qualquer estado em que a sucessora já exista
**ENTÃO** a `SQLiteConstraintException` sobe pelo `either {}` até o `viewModelScope.launch`
e derruba o app
**DEVERIA** voltar como `Left`, como todo o resto da fronteira

## Mecânica

`OpenInvoiceUseCase` termina com `invoiceRepository.insert(invoice)` **cru**, como última
expressão do `either {}`. Arrow não intercepta `throw` — só `Raise` —, e o próprio projeto
já documenta isso depois de o ter sofrido uma vez, no KDoc de `PayInvoicePaymentUseCase`.
O irmão `CreateInvoiceUseCase` faz `catch { invoiceRepository.insert(invoice) }.bind()`: a
divergência entre os dois é a evidência de qual é o certo.

Há um segundo defeito no mesmo caminho: `CloseInvoiceUseCase` chama `openInvoiceUseCase(...)`
**sem `.bind()`**, então um `Left` legítimo é descartado em silêncio.

Não há guarda de submissão em nenhuma tela da feature — nenhuma ocorrência de
`isSubmitting`, `isLoading` ou equivalente em `creditcards/impl/src/commonMain`.

## Evidência

- `feature/creditcards/impl/.../usecase/OpenInvoiceUseCase.kt` — `insert(invoice)` como
  última expressão do `either {}`, sem `catch`
- `feature/creditcards/impl/.../usecase/CreateInvoiceUseCase.kt` — o contraste:
  `catch { invoiceRepository.insert(invoice) }.bind()`
- `feature/creditcards/impl/.../usecase/CloseInvoiceUseCase.kt` — `openInvoiceUseCase(...)`
  sem `.bind()`
- `feature/creditcards/impl/.../usecase/PayInvoicePaymentUseCase.kt` — o KDoc que estabelece
  que `either {}` não pega `throw`
- `core/database/.../entity/InvoiceEntity.kt` — os três índices únicos por
  `(creditCardId, <mês>)`
- `feature/creditcards/impl/.../closeInvoice/CloseInvoiceModal.kt` — botão sem `enabled`;
  `CloseInvoiceViewModel.closeInvoice()` é um `viewModelScope.launch` incondicional

## Consequência

Crash. E quando o `Left` é apenas descartado, a fatura fica `CLOSED` sem sucessora `OPEN`:
a partir daí `CreateInvoiceUseCase` recusa tudo com `NoOpenInvoice` e o cartão não aceita
mais compras.

## O que falta para confirmar

O `insert` cru, o `.bind()` ausente, os índices únicos e a falta de guarda de submissão são
fato lido no disco. **O entrelaçamento das duas corrotinas é hipótese**: confirmá-lo pede um
teste instrumentado com duas chamadas concorrentes ao caso de uso sobre o `AppDatabase` real.
O defeito de código independe disso — o `insert` desprotegido é um caminho de exceção aberto
qualquer que seja o gatilho.

## Sugestão

Embrulhar o `insert` em `catch {}` e dar tratamento explícito ao resultado em
`CloseInvoiceUseCase`; desabilitar o botão enquanto a operação está em voo. Não vinculante.
