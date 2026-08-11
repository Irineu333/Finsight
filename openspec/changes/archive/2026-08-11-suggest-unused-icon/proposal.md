## Why

O formulário de nova conta sempre abre com o mesmo ícone pré-selecionado — `AppIcon.WALLET`, fixo em `AccountFormViewModel` (`feature/accounts/impl/.../accountForm/AccountFormViewModel.kt:53`). Quem cria contas sem trocar o ícone acaba com uma lista onde todas as contas são visualmente idênticas, e o ícone deixa de servir para o que existe: distinguir uma conta da outra de relance, na lista de contas, no dashboard e nos seletores de lançamento.

A pré-seleção é uma conveniência, e uma conveniência que produz colisão por padrão é pior do que nenhuma. O catálogo de contas já oferece onze ícones (`FeatureIconCatalog.accounts`); basta escolher a partir do que ainda não está em uso.

## What Changes

- Ao **criar** uma conta, o ícone pré-selecionado passa a ser o primeiro do catálogo de contas que nenhuma conta aberta já usa, em vez da constante `WALLET`.
- Quando todo o catálogo já estiver em uso, a pré-seleção volta ao padrão atual (`WALLET`) — a sugestão é conveniência, não garantia de unicidade.
- Contas **arquivadas** não contam como uso: elas não aparecem ao lado das ativas, então o ícone delas pode ser reaproveitado.
- A **edição** de conta não muda: o formulário continua abrindo com o ícone que a conta já tem, mesmo que outra conta use o mesmo.
- O usuário continua livre para escolher qualquer ícone do catálogo, inclusive um já usado. Nada é bloqueado, nada é escondido do seletor.
- A escolha é uma **derivação do domínio** com dono único (um use case da feature de contas), não uma regra reimplementada dentro do ViewModel.
- Fora de escopo: cartões e categorias mantêm suas constantes fixas (`CARD`, `CATEGORY`). A regra fica desenhada para não impedir a extensão depois, mas nenhum comportamento deles muda aqui.

## Capabilities

### New Capabilities
- `account-icon-suggestion`: qual ícone um formulário de conta nova pré-seleciona — a derivação a partir dos ícones em uso, o recorte de "em uso", o comportamento no esgotamento do catálogo e a fronteira com a edição.

### Modified Capabilities
<!-- Nenhuma. A criação e a edição de conta seguem com o mesmo contrato de domínio;
     o que muda é apenas o valor inicial de um campo do formulário, comportamento que
     nenhuma spec existente descreve hoje. -->

## Impact

- **`feature/accounts/api`** — nova interface de use case para a sugestão, consumível pelo `impl`.
- **`feature/accounts/impl`** — implementação do use case, registro no `AccountsModule` (Koin) e consumo em `AccountFormViewModel`, que passa a ter o ícone inicial resolvido de forma assíncrona (hoje ele é síncrono no construtor).
- **`IAccountRepository`** — leitura das contas abertas para apurar os ícones em uso; usa o que já existe (`getAllAccounts`), sem nova consulta ao banco.
- **`FeatureIconCatalog.accounts`** — passa a ter a sua **ordem** como parte do comportamento observável: é ela que define qual ícone livre é sugerido primeiro.
- Sem migração de banco, sem mudança de modelo, sem string nova.
