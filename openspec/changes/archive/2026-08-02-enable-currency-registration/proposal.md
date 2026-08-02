## Why

O app decide, embarcado, quais moedas existem: uma lista curada de 22 códigos que nenhum
usuário pode estender. Quem tem conta numa moeda fora dela não tem recurso algum — e,
pior, a primeira execução o coloca em USD, porque a resolução do locale reduz ao catálogo
e cai no último recurso quando não o encontra. O app não conhece o mundo, e fingir que
conhece produz um estado que só uma release nova conserta.

A saída não é uma lista maior. É o app parar de ser a autoridade sobre quais moedas
existem: **semear as principais e deixar o usuário cadastrar o resto**. As duas metades
são uma coisa só — encolher sem cadastrar piora a primeira execução, cadastrar sem
encolher deixa de pé um catálogo que ninguém precisa manter.

## What Changes

- **BREAKING** — `object CurrencyCatalog` deixa de existir. O conjunto de moedas oferecidas
  passa a ser uma **tabela**, fonte única, lida por repositório. Não há mais "embarcadas" e
  "do usuário" como dois conjuntos: há linhas.
- A semente encolhe de 22 para **6** moedas — BRL, USD, EUR, GBP, CHF, CNY — por um critério
  declarado: o mercado de origem do app, mais as moedas em que uma figura é legível por
  alguém de fora dele.
- Uma **semeadura** insere, numa operação só: a semente, toda moeda já em uso por uma
  conta existente, e a moeda do locale do dispositivo. Ela acontece quando a tabela passa
  a existir — pela migração num banco que já existe, pela criação do esquema numa
  instalação nova, que não roda migração alguma. Nenhum usuário atual perde a moeda que já
  usa, e o auto-registro da moeda do dispositivo deixa de ser um mecanismo para ser mais
  uma linha do mesmo `INSERT`.
- O usuário passa a **cadastrar, editar, arquivar e apagar** moedas. A plataforma sugere
  símbolo e nome para qualquer código ISO que ele digite.
- O **nome de uma moeda deixa de ser chave de string**. A linha guarda nome apenas quando
  o usuário o escreveu; nos demais casos a plataforma nomeia, no idioma corrente. As 44
  chaves `currency_name_*` de `strings.xml` são removidas.
- **Apagar** é recusado com motivo acionável quando uma conta ou um orçamento nomeia a
  moeda, e **leva junto** as linhas do acervo de taxas que a nomeiam.
- **Arquivar** entra como a saída para o que não pode ser apagado, no mesmo formato que
  categoria e conta já têm: some da oferta, o histórico permanece, é reversível.
- A moeda base **não pode ser arquivada**, e a linha do último recurso é garantida pela
  semente.

## Capabilities

### New Capabilities
- `currency-registry`: o conjunto de moedas que o app oferece — semeado, estendido pelo
  usuário, arquivável e apagável. Quem nomeia uma moeda, o que a semente é e por quê, e o
  que apagar e arquivar significam para um código que não é entidade referenciada por
  chave estrangeira.

### Modified Capabilities
- `currency-consolidation`: o requisito do catálogo curado muda de sentido — o conjunto
  oferecido deixa de ser opinião embarcada do app e passa a ser dado, e a premissa de duas
  casas decimais passa a ser aplicada no cadastro em vez de na curadoria. O requisito do
  acervo de taxas ganha o que acontece com uma observação cuja moeda é apagada, e o da
  moeda base ganha a proibição de arquivá-la.

## Impact

- **`:core:model`** — `CurrencyCatalog` removido; `CurrencyInfo` passa a vir de repositório;
  contrato `ICurrencyRepository` ao lado de `IBaseCurrencyRepository`;
  `legacyRelabelCurrency` deixa de consultar uma lista embarcada.
- **`:core:database`** — entidade `currencies`, DAO, a semeadura e os seus dois gatilhos
  (migração e criação do esquema). Ordem em relação à migração de relabel precisa ser
  verificada, não presumida.
- **`:core:designsystem`** — `LocalCurrencyCatalog`, irmão de `LocalCurrencyFormatter` e
  provido pelo mesmo `FormattingLocalsHost`; `CurrencyPickerModal` inalterado, continua
  renderizando o que recebe.
- **`:core:ui`** — os quatro composables que chamam `CurrencyCatalog.symbolOf` passam a ler
  o composition local.
- **`:core:resources`** — remoção das 44 chaves `currency_name_*`; chaves novas do
  formulário, do arquivamento e das recusas.
- **`:core:common`** — nome e símbolo sugeridos pela plataforma, `expect`/`actual` nas três.
- **`feature/settings/impl`** — dono da tela e do formulário de moedas, ao lado da moeda
  base e do acervo de taxas; `BaseCurrencyRepository` deixa de reduzir ao catálogo
  embarcado.
- **`feature/accounts/impl`, `feature/creditcards/impl`, `feature/budgets/impl`** — os
  `selectableCurrencies` dos formulários passam a vir do repositório.
- **`:core:ledger`** — nada. Ele persiste o código e continua não conhecendo o conjunto,
  que é o que torna esta mudança contida.
- **`app/shared` (testes)** — `SingleCurrencyInertiaTest` ganha as entradas do formulário de
  moeda e do repositório, cada uma com o motivo de não ser uma porta a mais para denominar
  conta.
