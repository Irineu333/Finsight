---
area: creditcards
severity: medium
type: data
---

# Excluir uma parcela deixa o parcelamento em "06 / 5", com a barra passando de 100%

## Cenário

**DADO** uma compra parcelada em 6×, com as parcelas 1 a 6 gravadas
**QUANDO** o usuário exclui a parcela 1 — pela tela de transações, ou apagando a fatura
futura que a contém
**ENTÃO** a lista de parcelamentos passa a exibir "06 / 5", com a barra de progresso em 120%
**DEVERIA** ou renumerar as parcelas restantes, ou preservar o total original e exibir
"de 6"

## Mecânica

`InstallmentRemovalReconciler.onRemoved()` corrige a cópia com
`count = installmentDao.countTransactions(installmentId)` — o número de linhas que sobraram
— mas não toca no `installmentNumber` das que ficaram. A escala muda de um lado só.

A UI então cruza as duas: `currentNumber` sai de `installmentNumber` (escala antiga, até 6)
e `totalCount` sai de `installment.count` (escala nova, 5). O `progress` divide um pelo
outro.

O reconciliador foi escrito exatamente para impedir o inverso disso — o KDoc fala em
*"'3 of 6' over five rows, a progress bar over a six that is gone"* — e o teste que o cobre
apaga a transação nº 1 e afirma `count == 5`, deixando os números 2..6 intactos. O estado
inconsistente é produzido por um teste verde: o defeito está na leitura, não na escrita.

## Evidência

- `feature/creditcards/impl/.../ledger/InstallmentRemovalReconciler.kt` — `onRemoved()`:
  `updateInstallment(count = remaining, ...)`, sem renumerar
- `feature/creditcards/impl/.../mapper/InstallmentUiMapper.kt` — `toUi()`: `currentNumber`,
  `totalCount = installment.count`, `progress = currentNumber.toFloat() / installment.count`
- `feature/creditcards/impl/.../installments/InstallmentsScreen.kt` — renderiza
  `ui.currentNumber` / `ui.totalCount` e `LinearProgressIndicator(progress = { ui.progress })`
- `feature/creditcards/impl/src/jvmTest/.../InstallmentRemovalReconcilerTest.kt` —
  `removing one share leaves the installment describing what is left`
- os dois caminhos de remoção: `feature/transactions/impl/.../DeleteTransactionUseCaseImpl.kt`
  e `feature/creditcards/impl/.../usecase/DeleteFutureInvoiceUseCase.kt`

## Consequência

O parcelamento exibe um número que não corresponde a nada — nem à compra original, nem ao
que restou — e a barra pode estourar.

## Sugestão

Decidir uma escala só. O caminho mais barato é derivar `totalCount` do maior
`installmentNumber` restante, ou renumerar no reconciliador. Note que o KDoc do
reconciliador defende manter `totalAmount` como cópia por um motivo real (o arredondamento
por parcela): a decisão sobre `count` precisa ser tomada junto com essa. Não vinculante.
