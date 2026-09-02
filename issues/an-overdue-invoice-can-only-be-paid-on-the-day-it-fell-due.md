---
area: creditcards
severity: medium
type: data
---

# Fatura vencida não aceita a data real do pagamento

## Cenário

**DADO** uma fatura `CLOSED` que venceu em 20/08, e hoje é 21/08 — pagamento em atraso, o
caso comum
**QUANDO** o usuário abre "Pagar fatura"
**ENTÃO** o campo de data vem preenchido com 20/08, o calendário não oferece 21/08, e o
lançamento sai datado de 20/08
**DEVERIA** aceitar a data real do pagamento — sinalizando o atraso, se for o caso — já que
a data do lançamento é o que decide o saldo da conta pagadora por dia e por mês

## Mecânica

`PayInvoiceUseCase` recusa `paidAt > invoice.dueDate`: no domínio, pagamento após o
vencimento não existe. A tela se ajusta a isso em vez de expor o conflito —
`maxDate = invoice.dueDate.coerceAtMost(currentDate)`, que para uma fatura vencida colapsa
em `dueDate`, e é esse valor que preenche o campo, limita o calendário e habilita o botão.
Nada em tela diz que a data foi recuada.

O caso de uso tem quatro guardas, e a que sobra — `paidAt <= currentDate` — já impede
pagamento no futuro por conta própria. A que limita ao vencimento é a única que não
protege nada e custa a data verdadeira.

## Evidência

- `feature/creditcards/impl/.../usecase/PayInvoiceUseCase.kt` — as quatro `ensure`, entre
  elas `paidAt <= invoice.dueDate` e `paidAt <= currentDate`
- `feature/creditcards/impl/.../payInvoice/PayInvoiceModal.kt` — `maxDate`, o valor inicial
  por `coerceIn`, `isValidInvoicePayment()` com `parsedDate in minDate..maxDate`, e o mesmo
  `maxDate` repassado ao `DatePickerModal`
- `PayInvoiceUseCaseTest` — `paying after it fell due is refused` fixa a regra do domínio;
  nenhum teste cobre a fatura vencida na tela

## Consequência

O extrato da conta pagadora mostra o dinheiro saindo num dia em que não saiu; num pagamento
que cruza a virada do mês, no mês errado. Não há contorno: a data correta é inexprimível.

## Sugestão

Remover `ensure(paidAt <= invoice.dueDate)` — `paidAt <= currentDate` já cobre o futuro e
`paidAt >= closingDate` o passado — e mover "venceu em X" para informação na tela em vez de
restrição de entrada. Não vinculante.
