---
area: creditcards
severity: low
type: i18n
---

# Um erro de parcelamento fala português no log e esquece qual parcela

## Invariante

O `message` de um erro é inglês, porque é para log; o `toUiText()` é traduzido, porque é para
a tela. As duas metades não trocam de papel, e nenhuma perde o detalhe que o erro carrega.

Hoje é falso em `InstallmentError`, nas duas metades. São os **únicos dois `message` em
português** entre os 16 arquivos `*Error.kt` do projeto — todos os outros, de `AccountError`
a `LedgerError`, escrevem em inglês.

## Mecânica

`InstallmentError.MinInstallment` declara `"O número de parcelas deve ser superior a 1"` e
`BlockedInvoice` declara `"Parcela $installment coincidiu com uma fatura ${invoice.status}"`.
O segundo é o mais revelador: é a única `data class` do tipo, existe para carregar
`installment` e `invoice`, e a interpolação prova que alguém quis o detalhe.

Do outro lado, `InstallmentError.toUiText()` mapeia `is BlockedInvoice` para
`installment_error_blocked_invoice`, que diz *"Uma das parcelas coincide com uma fatura
bloqueada."* — sem o número e sem o status. Os dois campos que o erro se deu ao trabalho de
carregar morrem na tradução, e o detalhe só sobrevive na string em português que nunca
chega a uma tela.

## Evidência

- `core/model/.../domain/error/InstallmentError.kt` — `MinInstallment` e `BlockedInvoice`,
  os dois `message`
- mesmo arquivo — `toUiText()`, o ramo `is InstallmentError.BlockedInvoice`, que ignora os
  dois campos
- `core/resources/.../values/strings.xml` e `values-en/strings.xml` —
  `installment_error_blocked_invoice`, sem placeholder em nenhum dos dois
- `grep` por `message = "` nos 16 arquivos `*Error.kt` — só estes dois fogem do inglês
- `core/common/.../util/UiText.kt` — `UiText.ResWithArgs`, o veículo que o detalhe pediria

## Consequência

O log mistura idiomas onde a convenção existe justamente para não misturar. E quem parcela
uma compra em 12 e esbarra numa fatura paga no meio do caminho não descobre em qual —
precisa abrir as faturas uma a uma para achar a que bloqueia.

## Sugestão

Traduzir os dois `message` para inglês é mecânico. O detalhe perdido custa pouco mais do que
parece: `UiText.ResWithArgs` já existe e já é renderizado por `stringUiText`, então falta só
dar `%1$d` e `%2$s` às duas chaves e trocar o ramo. O que decidir é como o `Invoice.Status`
vira texto — ele é um enum, e a tradução dele não está resolvida aqui. Não vinculante.
