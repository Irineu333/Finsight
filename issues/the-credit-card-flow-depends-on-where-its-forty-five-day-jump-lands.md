---
area: creditcards
severity: medium
type: test
---

# O fluxo E2E de cartões depende do dia do mês em que a suíte é rodada

## Cenário

**DADO** `creditcards_lifecycle`, que cria um cartão com `closingDay=10` e relança o app
`JUMP_DAYS: 45` dias à frente para poder fechar a fatura
**QUANDO** a suíte é rodada num dia em que hoje + 45 cai **no** dia de fechamento
**ENTÃO** o fluxo falha em `scrollUntilVisible: dashboard_component_balance_stats_credit_card`
(linha 517 do `.yaml`), depois do salto
**DEVERIA** o fluxo assertar o mesmo estado qualquer que seja o dia em que roda — a data em
que a suíte é executada não é uma entrada declarada do teste

## Mecânica

`JUMP_DAYS` é uma constante de 45 dias somada a *hoje*, então o dia do mês em que o salto
aterrissa **varia com o dia em que a suíte roda** — e o cartão do fluxo tem `closingDay=10`,
que é a borda que decide quantas faturas existem e qual está aberta depois do salto. A janela
de compra é `[abertura, fechamento)` com o fechamento **exclusivo** (spec
`invoice-purchase-window`), de modo que aterrissar no dia 10 e aterrissar no dia 9 são dois
ciclos diferentes, não uma diferença de um dia.

O comentário do próprio fluxo enuncia a intenção — *"Past the invoice's closing date from any
day the suite runs on: a card created today closes between 1 and ~40 days out"* — e é
justamente essa universalidade que a constante não entrega.

## Evidência

- Rodado em **26/08/2026** (salto → **10/10**, o dia de fechamento): `creditcards_lifecycle`
  **falha** em `dashboard_component_balance_stats_credit_card`, na asserção de
  `[$]33[.,]00` que vem depois do salto. Report `.maestro/report/2026-08-26_213938/`
- Rodado em **24/08/2026** (salto → **08/10**, antes do fechamento): o mesmo fluxo passou,
  305 comandos `COMPLETED`. Report `.maestro/report/2026-08-24_200958/`
- Mesma máquina, mesmo aparelho (AVD `finsight_e2e`, `emulator-5556`, os sete checks da
  §2.2 conferidos por serial), com `JUMP_DAYS` baixado para **44** (salto → 09/10): o fluxo
  falha **mais cedo e noutro passo**, em `invoice_expenses_amount` `[-][$]33[.,]00` — o
  estado depois do salto é outro, que é o ponto
- A captura do passo que falha mostra o dashboard em *October, 2026* e a hierarquia
  daquele instante não contém componente de cartão algum: o widget é `hideWhenEmpty` e o
  mês alvo não tem fluxo de `LIABILITY` a exibir

## Consequência

A suíte tem um vermelho que aparece e some conforme o calendário, e o modo como ele aparece
— um fluxo de cartões falhando numa asserção de dashboard — convida a ser lido como
regressão de quem estiver mexendo no app naquele dia. Foi o que aconteceu: o vermelho foi
encontrado durante a verificação de uma change que não toca cartões, e só um run do `HEAD`
com as mudanças guardadas separou uma coisa da outra.

## A pergunta seguinte, respondida

**Há defeito de app por trás, e ele está registrado em
`an-absent-injected-parameter-falls-back-to-the-previous-one-of-its-type`.** Num run de
29/08/2026 (salto → 13/10) o fluxo falhou em `invoice_expenses_amount` esperando `[-][$]33[.,]00`;
a captura mostra o extrato aberto na fatura **paga** de setembro (−$120,00 / +$120,00 / $0,00), e
o banco extraído do aparelho tem só duas faturas do cartão `id = 1`: `id 1 | 2026-08 | PAID` e
`id 2 | 2026-09 | OPEN`. A tela abriu na `id 1` porque `initialInvoiceId` recebeu o id do
*cartão*.

Isso **não** dissolve este arquivo: continuam sendo dois defeitos distintos. O de injeção decide
*qual fatura* o extrato abre; a constante `JUMP_DAYS` continua decidindo *quantas faturas
existem* e qual está aberta depois do salto, e foi ela que produziu a falha de 26/08 num passo
completamente diferente (`dashboard_component_balance_stats_credit_card`). Corrigir a injeção
deixa este fluxo verde na maioria dos dias sem tornar a data de execução uma entrada declarada.

*Segue não determinado: se o app deveria ou não exibir a despesa de $33,00 no mês em que o salto
aterrissa quando esse dia é o de fechamento.*

## Sugestão

Fixar o dia em que o salto aterrissa em vez do número de dias — o fluxo já controla o
relógio pelo `clockOffsetDays`, então o salto pode ser calculado para um dia do mês
declarado, longe da borda de fechamento. Antes disso, responder a pergunta acima. Não
vinculante.
