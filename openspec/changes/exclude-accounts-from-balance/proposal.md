## Why

O widget **Saldo em Contas** (`component_total_balance`, `strings.xml:773`) soma toda conta `ASSET` do plano, sem exceção e sem configuração. O perímetro é literal no SQL — `WHERE a.type = :type` (`EntryDao.kt:225-232`) — e o widget é hoje o único do dashboard que não oferece nenhuma opção de conteúdo (`DashboardComponentType.kt:15-20`; o modal de opções não tem sequer um ramo para ele, `DashboardComponentOptionsModal.kt:166-194`).

Nem toda conta do usuário pertence ao número que ele quer ver de relance. Uma reserva de longo prazo, uma conta compartilhada com outra pessoa, uma poupança que ele não considera dinheiro disponível — todas entram no total e o inflam em relação ao que ele de fato administra no mês. E a inconsistência é visível na própria tela: o card **Contas** já permite esconder uma conta da listagem (`excluded_account_ids`, `DashboardComponentsBuilder.kt:285-292`), mas essa mesma conta continua somando no total logo acima.

Falta ao usuário poder dizer quais contas compõem o total — e falta ao razão uma forma de responder por um subconjunto de contas sem que alguém some conta a conta fora dele.

## What Changes

- O widget **Saldo em Contas** ganha configuração de conteúdo: uma lista de contas com alternância, na qual desmarcar uma conta a retira do total. Mesmo vocabulário e mesma forma de tela que o card de Contas já usa.
- O conjunto excluído é gravado na preferência **daquele widget**, como qualquer outra opção de widget do dashboard (`excluded_account_ids` no mapa de config). Não é propriedade da conta, não vai ao banco, não tem migração.
- **A leitura do razão passa a admitir um conjunto de contas a excluir**, na mesma consulta parametrizada que já existe. O razão continua sem saber **por que** algumas contas ficaram de fora: ele responde "Σ das entries destas contas até este mês, por moeda", e nada mais.
- Excluir tudo exibe **zero**, não esconde o widget. O usuário configurou isso deliberadamente, e um widget que desaparece quando se mexe na sua própria configuração é confuso.
- Dashboards já montados **não mudam**: o padrão é o conjunto vazio, e conjunto vazio é o comportamento de hoje, byte por byte.
- Um id de conta que não exista mais na preferência é **ignorado em silêncio** — é o que o card de Contas já faz, e nada há a corrigir num id que não casa com linha alguma.
- Fora de escopo, deliberadamente:
  - **Os widgets de fluxo do mês** (receitas/despesas de contas, de cartões e o neutro). Eles varrem `ASSET` por outra leitura (`assetMonthFlowsByCurrency`) e continuam inalterados. O perímetro do saldo e o do fluxo passam a poder divergir, e isso é aceito e registrado no design.
  - **Sincronizar com o card de Contas.** As duas exclusões permanecem independentes: são dois widgets, cada um com a sua preferência.
  - **Cartões.** A leitura é de `ASSET`; passivo nunca esteve nesse total.
  - **Contas arquivadas.** Verificado: `ArchiveAccountUseCaseImpl.kt:37` recusa arquivar conta permanente com saldo diferente de zero, então uma conta arquivada soma zero. Ela entra na leitura, mas não move o número — inconsistência de vocabulário, não de valor, e corrigi-la aqui seria mudança sem efeito prático.

**Sem alteração de schema.** Nenhuma tabela, nenhuma coluna, nenhuma migração de banco. A preferência já é um `Map<String, String>` serializado em `Settings` com `ignoreUnknownKeys = true` e `config` com padrão vazio (`DashboardPreferencesRepository.kt:16-19, 54-59`), de modo que uma preferência gravada antes desta change lê como conjunto vazio sem ramo de compatibilidade.

## Capabilities

### New Capabilities
<!-- Nenhuma. O perímetro configurável de um widget de saldo pertence a
     `dashboard-balance-widgets`, que já governa o que um widget de dinheiro soma e como
     ele o nomeia; e a forma da leitura pertence a `ledger-reporting`, que já governa o
     saldo acumulado por natureza. -->

### Modified Capabilities
- `dashboard-balance-widgets`: distinguir os **dois eixos** de perímetro de um widget de saldo — a natureza de conta, que é identidade do widget e não se configura, e o **conjunto de contas dentro dessa natureza**, que passa a ser configurável. Inclui o que a regra de honestidade de rótulo exige de um perímetro autoral do usuário.
- `ledger-reporting`: o saldo acumulado até um mês passa a admitir um conjunto de contas a excluir, expresso por **identidade de conta** e não por fachada, na mesma consulta parametrizada — sem consulta paralela e sem subtração de dois totais.

## Impact

- **`core/ledger`** — `EntryDao.balanceUpToMonthByType` ganha o parâmetro de exclusão na mesma `@Query`; `IEntryRepository.balanceUpToByCurrency` / `naturalBalanceUpToByCurrency` e `CalculateBalanceUseCase.invoke` propagam-no com padrão vazio. Os dois chamadores de `forAccount` (`AdjustBalanceUseCase`, `EditAccountBalanceViewModel`) não são tocados — é a outra leitura.
- **Raio de alcance da leitura abrangente: um único consumidor de produção**, `DashboardComponentsBuilder.totalBalance` (`:182`). Verificado por busca em `feature/` e `core/`.
- **`feature/dashboard/impl`** — novo `TotalBalanceConfig`, entrada no `defaultConfig` de `TOTAL_BALANCE`, ramo `TOTAL_BALANCE` no `DashboardComponentOptionsModal` e `totalBalance` passando a receber o `config` que hoje ignora.
- **Testes** — os stubs de `IEntryRepository` espalhados pela suíte acompanham a assinatura nova; um padrão no parâmetro mantém a mudança mecânica.
- **Strings** — um par de chaves novo (pt + en) para rotular a seção de contas do total.
