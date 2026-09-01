---
area: report
severity: medium
type: data
---

# O relatório conta o pagamento de fatura como despesa; as outras duas leituras não

## Cenário

**DADO** um mês com um único movimento: R$ 3.000 pagos da conta corrente para a fatura do
cartão
**QUANDO** o usuário abre o relatório do período com o escopo padrão — todas as contas
**ENTÃO** o relatório mostra "Despesas R$ 3.000", enquanto a tela da conta mostra despesas
R$ 0 (e R$ 3.000 como quitação) e o resumo do mês do dashboard também mostra R$ 0
**DEVERIA** as três leituras responderem a mesma coisa, porque é o mesmo fato

## Mecânica

Três consultas classificam o mesmo lançamento, e só uma delas ignora a contraparte
`LIABILITY`:

- `accountPeriodTotals` carrega as flags `eq` **e** `li`, e manda `eq = 0 AND li = 1` para
  `settlement`, fora de `income`/`expense`;
- `assetMonthTotals` exige contraparte nominal (`EXPENSE`/`INCOME`) ou `EQUITY` no `WHERE`,
  o que deixa o pagamento inteiramente de fora;
- `scopeStats` tem só `eq`. Sua única exclusão é a de transferência interna, por
  `COUNT(DISTINCT accountId) >= 2` sobre pernas `ASSET` — e um pagamento de fatura tem **uma**
  perna `ASSET`, então a condição é falsa e a perna entra em `expense` pelo sinal.

O domínio já decidiu duas vezes que quitar dívida não é despesa. A terceira leitura é a que
diverge, e é a que o relatório usa.

## Evidência

- `core/ledger/.../dao/EntryDao.kt` — `scopeStats()`: `expense` só condicionado a `eq = 0` e
  `amount < 0`; a exclusão de transferência é o `COUNT(DISTINCT x.accountId) >= 2`
- mesmo arquivo — `accountPeriodTotals()`: `settlement` = `eq = 0 AND li = 1`, e `expense`
  exige `li = 0`
- mesmo arquivo — `assetMonthTotals()`: o `AND (EXISTS … EQUITY OR EXISTS … ('EXPENSE','INCOME'))`
- `feature/report/impl/.../usecase/CalculateReportStatsUseCase.kt` — o único consumidor de
  `scopeStatsByCurrency(...)`
- `core/ledger/src/jvmTest/.../ReportStatsQueryTest.kt` — nenhum caso com contraparte `LIABILITY`

## Consequência

O total de despesas do relatório é maior que o de qualquer outra tela sempre que houve
pagamento de fatura no período, e o relatório é justamente o documento que se exporta. A
divergência não é de arredondamento: é o mesmo lançamento respondendo coisas diferentes
conforme quem pergunta, o que a regra de dono único existe para impedir.

## Sugestão

Dar a `scopeStats` a mesma flag `li` que `accountPeriodTotals` já tem, e decidir ali — uma
vez — o que o app chama de despesa. Não vinculante.
