---
area: creditcards
severity: low
type: data
---

# Um parcelamento recusado no meio do plano deixa as faturas que já abriu

## Cenário

**DADO** um cartão com fatura aberta para 2026-08 e fatura **fechada** para 2026-10
**QUANDO** o usuário parcela uma compra em três, a partir de 2026-09
**ENTÃO** a operação é recusada — a parcela 2 cairia numa fatura fechada — e a fatura de 2026-09
fica aberta, sem nenhum lançamento e sem ninguém a ter pedido
**DEVERIA** não deixar estrutura para trás: uma recusa não abre fatura

## Mecânica

O próprio use case enuncia a regra que ele então não consegue cumprir até o fim: *"Building is what
validates the form, and it comes before any invoice is resolved: resolving one creates and persists
it as a deliberate side effect outside the unit of work (design D7), so a refusal after it would
leave invoice structure behind for a purchase that never posted."*

A construção vem antes, e é o que fechou a recusa por valor não positivo. O que sobrou são as
recusas que só podem acontecer **depois**: `getOrCreateInvoiceForMonthUseCase` resolve — e portanto
cria — a fatura do primeiro mês, e só então `getSlots` percorre os meses seguintes e recusa se
algum estiver fechado. A fatura já criada permanece.

O mesmo mecanismo, uma escala acima, no caminho de escrita: `getInvoices(slots)` abre uma fatura por
slot que ainda não existe, e o desfazimento de `registerTransactions` remove apenas a linha do
parcelamento (`installmentRepository.deleteInstallmentById`). As N faturas abertas não são tocadas.

## Evidência

- `AddInstallmentUseCaseImpl.invoke()` — `getOrCreateInvoiceForMonthUseCase(…)` resolve a primeira
  fatura antes de `getSlots(…)`
- `AddInstallmentUseCaseImpl.getSlots()` — a recusa `InstallmentError.BlockedInvoice(installment =
  index + 1, invoice = invoice)`, que só alcança do segundo slot em diante
- `AddInstallmentUseCaseImpl.getInvoices()` — `slot.invoice ?: getOrCreateInvoiceForMonthUseCase(…)`,
  uma criação por slot
- `AddInstallmentUseCaseImpl.registerTransactions()` — `.onLeft { installmentRepository
  .deleteInstallmentById(installmentId) }`, o desfazimento que não menciona faturas

*A recusa `BlockedInvoice(installment = 1)` é inalcançável pelo mesmo mecanismo: o slot 1 sempre
encontra a fatura que `getOrCreateInvoiceForMonthUseCase` acabou de validar, e essa validação
recusa uma fatura fechada primeiro. A primeira parcela é recusada por `InvoiceException`, sem dizer
qual parcela; as demais, por `InstallmentException`, dizendo.*

*Hipótese, não verificada: o segundo caso — N faturas abertas por uma escrita que falha — é
raciocínio sobre o código; a revisão que originou o registro não conseguiu fazer o escritor real
falhar.*

## Consequência

Faturas abertas que nenhuma compra justificou. Não corrompem figura — uma fatura vazia soma zero —
mas povoam a lista do cartão com ciclos que o usuário não abriu e que ele não tem como distinguir
dos que abriu.

## Sugestão

Resolver todas as faturas do plano depois de validar todos os slots, ou fazer da abertura parte da
unidade de trabalho que o desfazimento alcança. A primeira é mais barata e ataca o caso medido; a
segunda fecha os dois. Não vinculante.
