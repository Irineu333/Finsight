## Why

Uma despesa que se repete todo mês é descoberta **no momento em que é lançada** — o usuário está digitando "Aluguel", 12/08, R$ 2.400, e é ali que ele sabe que isso volta em setembro. Hoje o app o obriga a lançar a transação, sair da tela, ir até Recorrentes, e redigitar os mesmos seis campos num segundo formulário (`RecurringFormModal`) que pede exatamente as mesmas informações que ele acabou de fornecer. O custo de registrar a recorrência é pagar o formulário duas vezes, e o resultado previsível é que ela não é registrada.

O modelo já suporta o que falta. `TransactionIntent` carrega `recurringId` e `recurringCycle` como campos comuns (`core/model` — `TransactionIntent.kt:21-22`), `transactions` guarda as duas colunas (`RecurringDao.kt:26-32`), e `IRecurringOccurrenceRepository.confirmCycle` já escreve transação e ocorrência como uma unidade de trabalho. O que não existe é o caminho inverso: uma transação que **cria** o modelo do qual ela própria é o primeiro ciclo.

## What Changes

- O formulário de **adicionar transação** ganha um botão discreto no campo de data, à esquerda do seletor de calendário, que marca a transação como recorrente. Sem campo novo, sem etapa nova: o dia da repetição é o dia da própria data lançada.
- Ao salvar com a opção ligada, o app cria a recorrência **e** lança a transação como o seu **ciclo 1** — com `recurringId` e `recurringCycle` preenchidos e a ocorrência do mês gravada como `CONFIRMED`. A transação exibe o vínculo com a recorrência como qualquer outra nascida de uma confirmação.
- O template é **ancorado na data da transação** (`createdAt`), não no relógio: uma transação lançada com data retroativa produz uma recorrência cujo ciclo 1 é o mês daquela data.
- **O mês da transação não volta a aparecer como pendente.** Sem a ocorrência, `GetPendingRecurringUseCase` (`:21-25`) reofereceria o mesmo mês para confirmação e o usuário lançaria a mesma despesa duas vezes no ledger. Fechar essa porta é o núcleo da mudança, não um acabamento.
- Criar a recorrência e lançar o ciclo 1 são **uma única unidade de trabalho**: se a escrita da transação for recusada (fatura fechada, conta arquivada, `Σ ≠ 0`), nenhum template é deixado para trás.
- A opção é **mutuamente exclusiva com parcelamento**: parcelar já é repetir, e as duas juntas descreveriam duas repetições sobre o mesmo lançamento.
- A recorrência criada assim é **indistinguível** de uma criada pelo formulário de recorrências: aparece na lista, é editável, arquivável, e volta a ser removível quando a transação que a originou é excluída (`detachTransactions`).

## Capabilities

### New Capabilities
- `transaction-as-recurring`: como uma transação em lançamento pode dar origem à recorrência da qual ela é o primeiro ciclo, o que essa criação escreve, e o que ela garante sobre o mês corrente.

### Modified Capabilities
<!-- Nenhuma spec viva descreve a criação de recorrências; `recurring-confirmation` descreve a confirmação de um ciclo de template já existente e não é alterada. -->

## Impact

- `core/model` — `RecurringForm` ganha a construção validada `toRecurring(createdAt)`, e a tradução única `TransactionForm.asRecurringOn(date)`. Nenhum modelo novo.
- `feature/recurring/api` — `IRecurringRepository.createWithFirstCycle(...)`; `StartRecurringFromTransactionUseCase` (classe concreta, como `GetPendingRecurringUseCase` já é).
- `feature/recurring/impl` — `RecurringRepository` implementa o novo método e passa a receber `IRecurringOccurrenceRepository`; `SaveRecurringUseCase` passa a delegar a validação a `RecurringForm.toRecurring`; registro no `RecurringModule`.
- `feature/transactions/impl` — `AddTransactionViewModel`, `AddTransactionUiState`, `AddTransactionAction`, `AddTransactionModal`. A dependência de `feature.recurring.api` já está declarada (`build.gradle.kts:22`).
- `core/resources` — chaves novas em `values/strings.xml` (pt) e `values-en/strings.xml` (en) para o rótulo de acessibilidade do botão e para o texto de apoio "repete todo dia N".
- Testes: atomicidade contra banco real em `jvmTest` (espelhando `ConfirmCycleAtomicityTest`), unidade do caso de uso e do view model, e um fluxo `.maestro`.
- **Nenhuma migração de banco**, nenhuma coluna nova, nenhuma mudança no ledger nem em `ConfirmRecurringUseCase`.
