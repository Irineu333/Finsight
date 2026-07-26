## Why

Recorrência é a única fachada que ainda não fala a língua de retirada do resto do app. Enquanto conta, cartão e categoria já **arquivam** o que tem história e **recusam** apagá-lo, uma recorrência pode ser apagada sem trava alguma **no domínio** — a única barreira é a tela, que só oferece Excluir depois de Parar, invertendo a relação do resto do app: aqui o arquivamento é pré-requisito do apagar, em vez de alternativa a ele. E apagar destrói, via `recurring_occurrences.recurringId` CASCADE, o registro dos ciclos confirmados, além de deixar `budgets.recurringId` apontando para um id morto numa tabela que não tem foreign key nenhuma. A ironia fecha o círculo: `RetireError.HAS_RECURRING` existe porque uma recorrência impede apagar uma categoria, mas a recorrência em si não tem guarda.

O vocabulário acompanha o descompasso — "parar"/"reativar"/"inativa" no lugar de arquivar/desarquivar/arquivada —, o botão "Parar" usa `Icons.Default.Delete`, e a tela é a última com **dois** seletores de filtro na topbar (status × tipo, nove estados) quando categorias já resolveu isso com um só.

## What Changes

- **"Parar" passa a ser "Arquivar".** `StopRecurringUseCase` → `ArchiveRecurringUseCase` e `ReactivateRecurringUseCase` → `UnarchiveRecurringUseCase`; o domínio e a UI passam a falar `isArchived`. Desarquivar é ação direta, sem modal de confirmação — reversível e inócua, como nas demais fachadas.
- **Apagar recorrência em uso é recusado.** Novo `ResolveRecurringRetirabilityUseCase` como dono único da decisão arquivar-vs-apagar, consumido por `DeleteRecurringUseCase` e pela visualização. Uma recorrência é apagável apenas se **nenhuma transação a nomeia** e **nenhum orçamento aponta para ela**.
- **A oferta de retirada passa a ser a que o domínio executa**, reusando `RetireAction`/`retireActionOf`/`OutlinedActionButton` já compartilhados — o que também corrige o ícone de lixeira no botão de arquivar.
- **Um seletor no lugar de dois:** `RecurringFilter { ACTIVE, EXPENSE, INCOME, ARCHIVED }` funde `RecurringFilter` e `RecurringStatusFilter` num único dropdown na topbar, como em Categorias. Junto vêm os dois empty-states distintos (banco vazio × filtro vazio) e o FAB deixa de sumir quando o filtro não retorna nada.
- **`ConfirmRecurringUseCase` passa a ser atômico.** Hoje a transação e a ocorrência são escritas em duas transações de banco independentes; a divergência faz o mês reaparecer como pendente e permite **confirmar o mesmo ciclo duas vezes**, duplicando o lançamento no razão.
- **BREAKING (interno, sem impacto em dados):** os use cases renomeados e a substituição dos dois enums de filtro por um. Nenhuma migração de banco — a coluna `recurring.isActive` permanece, e o `RecurringMapper` faz a inversão para `Recurring.isArchived`.

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova: o ciclo de vida de retirada de fachada já mora em account-lifecycle. -->

### Modified Capabilities
- `account-lifecycle`: recorrência entra como **quarta fachada** do ciclo de vida de retirada — o *Purpose* hoje nomeia apenas conta, cartão e categoria e precisa ser reescrito. Somam-se os requisitos de que uma recorrência em uso é arquivada e não apagada, de que a arquivada permanece acessível por filtro na própria tela e pode ser desarquivada, e a reconciliação de que arquivar uma recorrência **também interrompe a geração de ocorrências** — porque, para ela, estar em circulação *é* gerar.
- `balanced-ledger`: soma-se o requisito de que confirmar um ciclo de recorrência é uma **única unidade de trabalho** — a transação e a ocorrência que a registra persistem juntas ou não persistem —, porque é a escrita parcial que hoje torna alcançável um lançamento duplicado no razão.

## Impact

- **`core/model`** — `Recurring.isActive` → `isArchived`; novos `RecurringRetirability` e o erro tipado próprio de retirada de recorrência (com `toUiText()`), sem tocar `RetireError`/`CategoryRetirability`.
- **`core/database`** — `RecurringMapper` inverte o flag (única tradução); `RecurringDao` ganha as consultas do guard; `RecurringOccurrenceRepository` ganha o handle do banco para a escrita atômica; KDoc registrando por que a coluna se chama `isActive`.
- **`feature/recurring/api`** — `IRecurringRepository`/`IRecurringOccurrenceRepository` ganham o que o guard e a escrita atômica exigem.
- **`feature/recurring/impl`** — `ArchiveRecurringUseCase`/`UnarchiveRecurringUseCase` (renomeados), `ResolveRecurringRetirabilityUseCase` (novo), `DeleteRecurringUseCase` com guarda, `ConfirmRecurringUseCase` atômico, `ViewRecurringModal` consumindo `RetireAction`, reescrita de `RecurringUiState`/`RecurringAction`/`RecurringViewModel`/`RecurringScreen`, modais renomeados, registros no `RecurringModule`.
- **`feature/budgets/api`** — `IBudgetRepository` ganha a consulta "existe orçamento apontando para esta recorrência".
- **`core/ui`** — `RecurringCard` (em `feature/recurring/impl`) troca o badge "Inativa"/`Warning` por "Arquivada" com ícone `Archive`; `RetireAction` e `OutlinedActionButton` são consumidos sem alteração.
- **`core/analytics`** — os eventos `StopRecurring`/`ReactivateRecurring` são renomeados. Os **nomes** enviados ao Firebase (`stop_recurring`, `reactivate_recurring`) mudam junto, quebrando funis existentes — consequência aceita e registrada.
- **`core/resources`** — strings de arquivar/desarquivar recorrência, os motivos de recusa, rótulos do seletor e vazios por filtro; aposentadoria das strings de "inativa"/"parar"; e correção de `retire_error_has_recurring` e `account_error_has_recurring`, que hoje mandam "encerre-as antes de excluir" — um caminho que nunca desbloqueou nada, porque `countByCategory`/`countByAccount`/`countByCreditCard` contam qualquer template independentemente do flag. Os demais `retire_error_*` de categoria ficam com o seu texto intacto — mas as oito chaves que faltavam em `values-en` (as três `retire_error_*` e cinco de categoria) são acrescentadas, fechando a paridade entre os dois idiomas.
- Sem migração de banco.

**Fora de escopo** (dívida registrada, tarefas próprias): renomear a coluna `recurring.isActive` para `isArchived`; `AccountsViewModel` que deriva `hasMovement` só de entries e oferece Excluir que `DeleteAccountUseCaseImpl` recusa por `hasRecurringForAccount`; o `?: 0.0` de `CalculateBudgetProgressUseCase` que zera silenciosamente um limite PERCENTAGE irresolvível; extração de um `ArchivedBadge` compartilhado (hoje inline no `CategoryCard`); `getRecurringById`/`observeRecurringById` que varrem a tabela inteira; e a reparação de bancos que **já** divergiram antes da correção de atomicidade.
