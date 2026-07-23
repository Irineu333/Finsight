## Why

Hoje um cartão de crédito arquivado é **invisível** e **irreversível** pela interface: `observeAllCreditCards()` só traz cartões ativos e não existe nenhum caminho para trazer de volta um cartão arquivado. Arquivar por engano — ou arquivar um cartão que voltou a ser usado — é uma via de mão única. Categorias ganharam desarquivar recentemente; cartão é a última fachada arquivável sem simétrico. A tela de cartões, além disso, já acumula funções demais (pager, ações inline, filtros de lançamento) e não é lugar para mais uma lista.

## What Changes

- **Desarquivar cartão.** Nova operação simétrica ao arquivar, ponta a ponta. Como o estado de arquivamento de um cartão mora na sua conta do plano de contas (`chart-of-accounts`) e não numa coluna da fachada, o desarquivamento **reabre a conta** (`accounts.isArchived = 0`) — o primitivo inverso de `close()`, que hoje não existe no `AccountDao`. Ação direta, sem modal de confirmação: é reversível e inócua, garantidamente sobre uma conta de saldo zero (o arquivamento já exige isso).
- **Nova tela de cartões arquivados.** Rota própria (`ArchivedCreditCardsRoute`, **interna** ao `creditcards/impl` — só se chega a ela de dentro do próprio feature), uma **lista vertical simples** de cartões arquivados — não um pager. A tela de cartões atual não é sobrecarregada; os arquivados ganham lugar próprio. Cartões arquivados continuam **fora** da tela ativa e de qualquer seletor de lançamento.
- **Entrada discreta na tela de cartões.** Um item no **overflow (`⋯`) da topbar** de `CreditCardsScreen` navega para a tela de arquivados. Não compete com o pager nem com as ações do cartão.
- **Novo `ViewCreditCardModal`.** Cartão não tem modal de detalhe hoje; nasce um, espelhando `ViewCategoryModal`. Exibe um detalhe **decente** do cartão (identidade — nome, ícone/bandeira, cor; atributos — limite, dia de fechamento, dia de vencimento; saldo) e é onde vive o botão **Desarquivar**. Sem botão inline na lista: toca-se na linha, abre o modal. O modal observa o cartão, então ao desarquivar ele some da lista de arquivados sozinho.

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova: o ciclo de vida de cartão já mora em account-lifecycle. -->

### Modified Capabilities
- `account-lifecycle`: adiciona o desarquivamento de **cartão** como operação suportada — simétrica ao arquivar e, diferentemente do de categoria, **reabrindo a conta** no plano de contas em vez de um flag de fachada. Reconcilia o requisito atual: cartão arquivado some das listagens ativas e dos seletores, mas passa a ser **acessível** por uma listagem dedicada de arquivadas (tela própria) de onde pode ser desarquivado.

## Impact

- **`core/ledger`** — `AccountDao.reopen(id)` (`UPDATE accounts SET isArchived = 0`), o inverso de `close(id)` que faltava.
- **`feature/creditcards/api`** — `ICreditCardRepository.unarchive(accountId)`.
- **`feature/creditcards/impl`** — `CreditCardRepository.unarchive(accountId)` → `AccountDao.reopen` (o `accountDao` já está injetado); novo `UnarchiveCreditCardUseCase` (recebe o `CreditCard`, mesma forma de `ArchiveCreditCardUseCase`, outra fiação — direto ao repo); rota interna `ArchivedCreditCardsRoute`; nova `ArchivedCreditCardsScreen` + ViewModel (observa `observeAllCreditCardsIncludingClosed()`, filtra arquivados, renderiza do `CreditCard` de domínio) + um `ArchivedCreditCardRow` enxuto novo; novo `ViewCreditCardModal` (`AdaptiveModal`) + ViewModel/UiState/Action/Event (detalhe + desarquivar, saldo via `IEntryRepository`); slot `actions`/overflow na topbar de `CreditCardsScreen`; `creditCardsGraph()` registra a nova tela; registros no `creditCardsModule` / `useCaseModules`.
- **Testes** — patchar os fakes que as novas assinaturas quebram: 5 de `ICreditCardRepository` e 2 de `AccountDao` (`FakeAccountDao`, `RecordingAccountDao`).
- **`core/resources`** — novas strings: título da tela de arquivados, vazio da tela, item do overflow, botão desarquivar, indicador textual de arquivado no row, rótulo de saldo. Reusa os rótulos de limite/fechamento/vencimento já existentes.
- Sem migração de banco: apenas um `UPDATE` no flag existente `accounts.isArchived`.
