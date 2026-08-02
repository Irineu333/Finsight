 # Tarefas

Os grupos são ordenados; **dentro de um grupo, toda tarefa é independente das irmãs** —
nenhuma escreve o arquivo de outra e nenhuma consome a saída de outra —, de modo que um
subagente por tarefa consiga implementar o grupo inteiro de uma vez. A barreira de cada
grupo está escrita logo abaixo do seu título: o que precisa ser verdade para ele começar e
o que é verdade quando ele termina.

## 1. Peças novas, que ninguém ainda consome

**Entrada:** o repositório como está hoje — `CurrencyCatalog` de pé e todos os testes
passando. **Saída:** o projeto compila, `./gradlew allTests` continua passando, e nenhuma
das peças criadas aqui tem consumidor: elas existem para os grupos seguintes ligarem. É a
independência total deste grupo que o torna o ponto de partida.

- [x] 1.1 Acrescentar, em `core/resources/src/commonMain/composeResources/values/strings.xml`
  **e** em `values-en/strings.xml`, todas as chaves novas desta mudança: título e entrada da
  tela de moedas, rótulos do formulário (código, símbolo, nome), rótulo de arquivada,
  confirmação de arquivar/desarquivar, confirmação de exclusão declarando quantas observações
  do acervo vão junto, e os motivos de recusa (código já existente, moeda de casas decimais
  diferentes de duas, conta denomina a moeda, orçamento denomina a moeda, a moeda base não
  pode ser arquivada). Uma chave presente em só um dos dois arquivos é bug pelas convenções do
  projeto — as duas listas terminam com exatamente as mesmas chaves.
- [x] 1.2 Criar em `:core:common` o port do símbolo (D5/D8): `CurrencySymbols`, com
  `val symbols: Flow<Map<String, String>>`, e `LocalCurrencySymbols: (String) -> String`
  **sem default**, em arquivo novo ao lado de `extension/CurrencyFormatter.kt`. Documentar,
  no KDoc, por que o local não tem default — o mesmo motivo de `LocalCurrencyFormatter` — e
  que só `String` atravessa a fronteira, para `:core:designsystem` não precisar ver
  `:core:model`.
- [x] 1.3 Criar em `:core:common`, ao lado de `extension/LocaleCurrency.kt`, o `expect` do
  que a **plataforma** diz sobre um código — nome no idioma corrente, símbolo sugerido e
  número de casas decimais — e os três `actual`: `androidMain` e `jvmMain` sobre
  `java.util.Currency.getInstance(code)` / `getDisplayName(locale)` /
  `getDefaultFractionDigits()`, e `iosMain` sobre `NSLocale.localizedString(forCurrencyCode:)`.
  Um código que a plataforma não reconhece responde ausência, sem lançar — o pior caso degrada
  para o próprio código, como `CurrencyFormatter` já faz. Cobrir com teste em `jvmTest`, ao
  lado de `LocaleCurrencyTest`.
- [x] 1.4 Criar `CurrencyEntity(code, symbol, name: String?, isArchived)` em
  `core/database/.../database/entity/` e `CurrencyDao` em `.../database/dao/`, com o `code`
  como chave primária e as consultas que a tela e o formulário pedem (observar todas, observar
  as não arquivadas, buscar por código, inserir/atualizar, arquivar/desarquivar, apagar).
  **Não** registrar a entidade em `AppDatabase` ainda — a versão do banco só sobe junto com a
  migração, em 3.3.

## 2. A virada do tipo `CurrencyInfo`

**Entrada:** o grupo 1 concluído (as chaves novas já existem em `Res`). **Saída:** o projeto
compila, os testes passam, `CurrencyInfo` já tem a forma final (`name: String?`) e as 44
chaves `currency_name_*` não existem mais. **Este grupo tem uma tarefa só, e não é falta de
paralelismo:** um tipo não existe em duas formas ao mesmo tempo, então a mudança de forma e a
adaptação de quem o constrói e o exibe são necessariamente a mesma escrita.

- [x] 2.1 Mover `CurrencyInfo` para arquivo próprio em
  `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/`, com
  `name: String?` no lugar de `name: UiText` — `null` significa "a plataforma nomeia". No
  mesmo passo: fazer `CurrencyCatalog` construir as suas entradas com `name = null`, o que
  remove os 22 `import` de `Res.string.currency_name_*`; **apagar as 22 chaves
  `currency_name_*` de `values/strings.xml` e as 22 de `values-en/strings.xml`** (44 no
  total, nenhuma referenciada fora do catálogo); e ajustar os pontos que exibiam o nome como
  `UiText` — `AccountFormModal`, `CreditCardFormModal`, `BudgetFormModal`, `SettingsScreen` e
  `ExchangeRateFormModal` — para exibirem `name ?: code`. `CurrencyCatalogTest` continua
  passando; o catálogo ainda existe, e some no grupo 9.

## 3. Contrato, erros e a tabela no banco

**Entrada:** grupo 2 concluído — `CurrencyInfo` na forma final. **Saída:** o projeto compila
e os testes passam; `AppDatabase` está na versão 13 com a tabela `currencies` semeada, existe
`ICurrencyRepository` sem implementação, existe `CurrencyError`, e o acervo de taxas e as
contagens por moeda já sabem responder o que a exclusão vai perguntar. As quatro primeiras
tarefas escrevem módulos e arquivos distintos; nenhuma lê o resultado de outra.

- [x] 3.1 Declarar `ICurrencyRepository` em
  `core/model/.../domain/repository/`, ao lado de `IBaseCurrencyRepository`: observar o
  conjunto oferecido (não arquivadas) e o conjunto inteiro, buscar por código, gravar,
  arquivar/desarquivar e apagar. O nome exposto já sai resolvido — a linha quando ela o
  guarda, a plataforma quando não, o próprio código quando a plataforma não souber.
  Documentar no KDoc que o razão MUST NOT consultar este contrato.
- [x] 3.2 Declarar `CurrencyError` em `core/model/.../domain/error/`, no formato que os
  demais erros já têm (`val message: String` em inglês para log + `toUiText()` sobre as
  chaves de 1.1): código já existente, moeda de casas decimais diferentes de duas, conta
  denomina a moeda, orçamento denomina a moeda, a moeda base não pode ser arquivada.
- [x] 3.3 Escrever a **semeadura** (D4) em `core/database/.../database/Database.kt`,
  registrar `CurrencyEntity` e `currencyDao()` em `AppDatabase` subindo a versão para 13, e
  passá-la em `getRoomDatabase` e em `DatabaseModule`. A gravação é **uma operação só**: a
  semente (BRL, USD, EUR, GBP, CHF, CNY, com o critério de D3 registrado em comentário junto
  delas), `SELECT DISTINCT currency FROM accounts` e a moeda do locale do dispositivo. Ela
  pertence ao momento em que a tabela passa a existir, então tem **dois gatilhos sobre a
  mesma escrita** — uma função só, chamada da migração `12 → 13` num banco que já existe e
  do callback de criação do Room numa instalação nova, que não roda migração alguma e sem o
  qual o único usuário sem moeda nenhuma seria o que acabou de instalar. `INSERT OR IGNORE`
  a torna idempotente. O símbolo das linhas vindas das contas em uso é sugerido pela
  plataforma e recai no próprio código; a moeda do locale que não tenha duas casas decimais
  **não** é semeada. `core/database` não pode nomear locale nem plataforma: declarar em
  `:core:model` a porta de semeadura, no mesmo desenho de `LegacyRelabel` e
  `SeededBaseCurrency`, e recebê-la como parâmetro já resolvido. Fechar a migração com as
  três verificações que toda migração deste arquivo fecha.
- [x] 3.4 Dar ao acervo de taxas o que a exclusão de uma moeda precisa: contar e remover toda
  observação que nomeie um código **em qualquer das duas pontas** (`currency` ou
  `counterCurrency`). Acrescentar as consultas a `ExchangeRateDao`, os métodos a
  `IExchangeRateRepository` e a implementação a `ExchangeRateRepository`.
- [x] 3.5 Dar às contas e aos orçamentos a contagem por moeda que a recusa de exclusão vai
  ler: uma consulta em `core/ledger/.../dao/AccountDao.kt` que conta contas de um código e
  outra em `core/database/.../dao/BudgetDao.kt` que conta limites denominados nele. São
  perguntas sobre conta e sobre orçamento, não sobre o conjunto oferecido — o razão continua
  sem nomear a tabela `currencies`.

## 4. A ordem entre a semeadura e o relabel

**Entrada:** grupo 3 concluído. **Saída:** `legacyRelabelCurrency` não consulta tabela
alguma, e a relação entre as duas migrações está **verificada por teste, e não presumida**
(D9). **Tarefa única, e a sequência é obrigatória:** ela toca o mesmo arquivo de migração que
o grupo 3 criou, e é a única do grupo por isso.

- [x] 4.1 Fazer `legacyRelabelCurrency` (`core/model/.../domain/model/LegacyCurrencyRelabel.kt`)
  deixar de consultar `CurrencyCatalog.of` e passar a barrar pela **premissa de duas casas
  decimais**, respondida pela plataforma (a peça criada em 1.3) — não pela tabela. O relabel é
  a migração `10 → 11` e a semeadura só pode ser `12 → 13`, então num upgrade a partir da v10
  o relabel roda **antes** de a tabela existir, e nenhuma ordem conserta isso sem reescrever
  migração já publicada; a resolução de D9 é justamente ele não precisar dela. Fechar com
  teste de migração em `core/database/src/jvmTest/`, no formato dos `MigrationNToMTest` já
  existentes, cobrindo install novo e upgrade a partir de v10 e de v12, e verificando que a
  moeda que o relabel escreveu em `accounts.currency` é **recolhida pela semeadura** através
  do `SELECT DISTINCT currency FROM accounts` — que é o que faz as duas migrações se
  encaixarem sem se conhecerem.

## 5. O repositório e as regras de escrita

**Entrada:** grupos 3 e 4 concluídos — o contrato, os erros, a tabela e as consultas de
contagem existem. **Saída:** o projeto compila e os testes passam; existe uma implementação
de `ICurrencyRepository` e existem os três casos de uso que decidem o que pode ser gravado,
arquivado e apagado. As quatro tarefas escrevem arquivos novos e distintos em
`feature/settings/impl`, e todas dependem apenas das interfaces do grupo 3.

- [x] 5.1 Implementar `CurrencyRepository` em
  `feature/settings/impl/.../database/repository/`, ao lado de `BaseCurrencyRepository` e
  `ExchangeRateRepository`: lê `CurrencyDao`, e **resolve o nome a cada leitura** — o da
  linha quando ela o guarda, o da plataforma (1.3) no idioma corrente quando não, o próprio
  código quando a plataforma não souber. Persistência e resolução de nome, sem regra de
  negócio: as recusas são das tarefas seguintes.
- [x] 5.2 Escrever `SaveCurrencyUseCase` (cadastro e edição) em
  `feature/settings/impl/.../domain/usecase/`, devolvendo `Either` com `CurrencyError`:
  recusa código já existente e recusa código que a plataforma declare ter zero ou três casas
  decimais. As casas decimais nunca são um parâmetro — toda moeda gravada tem duas.
- [x] 5.3 Escrever `DeleteCurrencyUseCase` (D6): recusa com motivo quando uma **conta** ou um
  **orçamento** nomeia a moeda (usando as contagens de 3.5); quando nada a denomina, apaga a
  linha **e toda observação do acervo que a nomeie em qualquer das duas pontas, na mesma
  escrita** (3.4). Expor também a contagem de observações que serão removidas, para a
  confirmação poder dizer o número antes de a exclusão acontecer.
- [x] 5.4 Escrever `ArchiveCurrencyUseCase` (D7), no formato de `ArchiveAccountUseCase` e
  `ArchiveCategoryUseCase`: arquivar e desarquivar, com a **moeda base recusada com motivo**.
  Arquivar não remove nada, e o razão não é consultado nem alterado.

## 6. As ligações: Koin e o host do símbolo

**Entrada:** grupo 5 concluído. **Saída:** o app sobe com `LocalCurrencySymbols` provido, e
todo consumidor do grupo 7 pode ler o símbolo do composition local ou o conjunto pelo
repositório. As duas tarefas escrevem arquivos distintos (`SettingsModule.kt` e
`FormattingLocalsHost.kt`) e nenhuma depende da outra em tempo de compilação.

- [x] 6.1 Implementar `CurrencySymbols` sobre `ICurrencyRepository` em
  `feature/settings/impl`, e registrar em `SettingsModule.kt`: `single` do repositório,
  `factory` dos três casos de uso, `single` da porta de semeadura declarada em 3.3
  e `single` de `CurrencySymbols`. `AppModulesTest` continua passando.
- [x] 6.2 Fazer `FormattingLocalsHost` (`core/designsystem/.../ui/component/`) coletar
  `koinInject<CurrencySymbols>()` e prover `LocalCurrencySymbols` ao lado de
  `LocalCurrencyFormatter`. A assinatura do host não muda e nenhum segundo host nasce (D5).

## 7. Os consumidores deixam de ler o catálogo

**Entrada:** grupo 6 concluído. **Saída:** nenhum arquivo de produção fora de
`CurrencyCatalog.kt` nomeia `CurrencyCatalog`; o projeto compila e os testes passam.
**Todas as tarefas deste grupo são independentes por construção** — cada uma escreve o seu
arquivo, e cada ViewModel que ganha dependência escreve o módulo Koin da sua própria feature.
Os dois ViewModels de settings estão numa tarefa só porque compartilham `SettingsModule.kt`.

- [x] 7.1 `core/ui/.../ui/component/AccountSelector.kt`: `CurrencyCatalog.symbolOf` →
  `LocalCurrencySymbols.current`.
- [x] 7.2 `core/ui/.../ui/component/CreditCardSelector.kt`: idem.
- [x] 7.3 `core/ui/.../ui/component/CurrencyRow.kt`: idem.
- [x] 7.4 `core/ui/.../ui/component/CrossCurrencyAmountFields.kt`: idem.
- [x] 7.5 `feature/transactions/impl/.../ui/modal/viewTransaction/ViewTransactionModal.kt`:
  idem. É o **sétimo** sítio de `symbolOf`, que a contagem de seis do design não incluiu; a
  migração é a mesma.
- [x] 7.6 `feature/settings/impl/.../ui/screen/exchangeRates/ExchangeRatesScreen.kt`: idem,
  no `CurrencyGlyph` da linha.
- [x] 7.7 `feature/settings/impl/.../ui/modal/exchangeRateForm/ExchangeRateFormModal.kt`:
  idem, no prefixo do campo de valor.
- [x] 7.8 `AccountFormViewModel` (`feature/accounts/impl`) passa a receber
  `ICurrencyRepository` e a preencher `selectableCurrencies` com as moedas **não arquivadas**
  do repositório, nos dois pontos onde hoje lê `CurrencyCatalog.currencies`; registrar a
  dependência em `AccountsModule.kt`.
- [x] 7.9 `CreditCardFormViewModel` (`feature/creditcards/impl`): o mesmo, com a dependência
  registrada em `CreditCardsModule.kt`.
- [x] 7.10 `BudgetFormViewModel` (`feature/budgets/impl`): `limitCurrencyChoice` passa a
  filtrar as moedas do repositório (não arquivadas) por `currencies.inUse`, em vez de filtrar
  `CurrencyCatalog.currencies`; dependência em `BudgetsModule.kt`.
- [x] 7.11 Os dois ViewModels de settings, numa tarefa só porque dividem `SettingsModule.kt`:
  `SettingsViewModel` passa a resolver `baseCurrency` e `selectableCurrencies` pelo
  repositório (a troca da base oferece o registro inteiro, sem as arquivadas), e
  `ExchangeRateFormViewModel` passa a oferecer as não arquivadas no cadastro de uma taxa nova
  **e a apresentar a moeda arquivada que a taxa em edição já nomeia**, para que a correção
  continue possível (D7).
- [x] 7.12 `BaseCurrencyRepository` (`feature/settings/impl/.../database/repository/`) deixa
  de reduzir ao catálogo embarcado: a semente já gravou a moeda do locale, então a resolução
  passa a ser "a moeda do locale quando ela tem duas casas decimais, o último recurso quando
  não" — e o último recurso continua sendo último recurso, não padrão de produto.

## 8. A tela e o formulário de moedas

**Entrada:** grupos 5, 6 e 7 concluídos. **Saída:** o usuário chega à tela de moedas a partir
das configurações, e nela lista, cadastra, edita, arquiva e apaga. As três tarefas escrevem
conjuntos de arquivos disjuntos dentro de `feature/settings/impl` (mais a rota, na `api`).

- [x] 8.1 Declarar `CurrenciesRoute` em `feature/settings/api` (ao lado de `SettingsRoute` e
  `ExchangeRatesRoute`), registrá-la no `settingsGraph()` do `impl` e acrescentar a entrada
  para ela em `SettingsScreen.kt`, ao lado da moeda base e do acervo de taxas.
- [x] 8.2 Escrever `CurrenciesScreen`, `CurrenciesUiState` e `CurrenciesViewModel` em
  `feature/settings/impl/.../ui/screen/currencies/`: a lista do registro com as arquivadas
  identificadas, e **nada mais** — a linha não carrega ação, ela abre. Botão dentro de linha
  de lista vertical transforma cada linha numa barra de ferramentas e põe uma ação destrutiva
  a um toque errado de um scroll, e não é o que o app faz em lugar nenhum.
- [x] 8.2.1 Escrever a **visualização intermediária** em
  `feature/settings/impl/.../ui/modal/viewCurrency/`, no formato de `viewCategory` e
  `viewAccount` (`AdaptiveModal` com `DetailContent`/`DetailActions`, estado
  `Loading`/`Error`/`Content`, evento `Dismiss`): ela apresenta o que **denomina** a moeda —
  contas, orçamentos e taxas —, marca a base e a arquivada, e oferece editar mais uma ação de
  retirada só. Qual retirada é `retireActionOf` sobre a resposta de `DeleteCurrencyUseCase`,
  nunca uma segunda derivação; a base não oferece retirada alguma. As confirmações são
  `DeleteCurrencyModal` — **declarando quantas observações do acervo vão junto** — e
  `ArchiveCurrencyModal`, cada uma com o seu ViewModel, no formato de `deleteCategory` e
  `archiveCategory`. Desarquivar não pede confirmação, como em `viewCategory`.
- [x] 8.3 Escrever `CurrencyFormModal`, `CurrencyFormUiState`, `CurrencyFormAction` e
  `CurrencyFormViewModel` em `feature/settings/impl/.../ui/modal/currencyForm/`, estendendo
  `ModalBottomSheet` como os demais modais: campos de código, símbolo e nome; ao digitar um
  código que a plataforma reconheça, símbolo e nome são **sugeridos** e continuam editáveis;
  **não existe controle de casas decimais**; e as recusas de `SaveCurrencyUseCase` aparecem
  com o motivo. Uma linha semeada é editável como qualquer outra.

## 9. A remoção do catálogo e o registro dos novos ViewModels

**Entrada:** grupos 7 e 8 concluídos — nenhum consumidor resta e as telas existem. **Saída:**
não existe lista de moedas declarada em código de produção, e a tela e o formulário estão
resolvíveis pelo Koin. As duas tarefas escrevem arquivos distintos.

- [x] 9.1 Apagar `core/model/.../domain/model/CurrencyCatalog.kt` e
  `core/model/src/commonTest/.../CurrencyCatalogTest.kt`, levando `FALLBACK_CURRENCY` para
  onde a semente o declara, junto do critério de D3 — a moeda de último recurso pertence à
  semente por obrigação, e é a linha que garante que a resolução da base sempre tem resposta.
  Ajustar as referências restantes em teste (`BaseCurrencyRepositoryTest`,
  `LegacyCurrencyRelabelTest`) para deixarem de nomear o catálogo.
- [x] 9.2 Registrar em `SettingsModule.kt` os `viewModel {}` de `CurrenciesViewModel` e
  `CurrencyFormViewModel`; `AppModulesTest` continua passando.

## 10. Guardas e os cenários que a spec exige

**Entrada:** grupo 9 concluído — a mudança está inteira e o app compila. **Saída:**
`./gradlew allTests` passa, e cada cenário nomeado pela spec tem um teste que falha se ele
deixar de valer. Todas as tarefas escrevem arquivos de teste distintos e são independentes.

- [x] 10.1 Atualizar `app/shared/src/jvmTest/.../SingleCurrencyInertiaTest.kt` (D10): pôr o
  formulário de moeda e o repositório no `expected`, **cada um com o motivo escrito** — eles
  criam uma moeda, não uma conta, a mesma categoria da exceção que o formulário de orçamento
  e o de taxa já ocupam ali —, e reapontar `theOneResolver` para a expressão que de fato
  decide uma moeda depois desta mudança. Um `theOneResolver` que nomeie expressão inexistente
  passa sem guardar nada, e é isso que esta tarefa impede.
- [x] 10.2 Teste de migração partindo de um banco que tem conta numa moeda **fora da semente**
  (ARS, MXN, PEN, UYU ou ILS): a moeda existe como linha depois do upgrade, é oferecida nos
  formulários, e as figuras da conta continuam exatamente as de antes. É a cobertura da
  regressão silenciosa de quem já usa o que a semente deixou de trazer.
- [x] 10.3 Testar `DeleteCurrencyUseCase`: recusa com motivo quando uma conta nomeia a moeda,
  recusa quando um orçamento a nomeia (nada removido nos dois casos), remoção da moeda e das
  suas observações do acervo na mesma escrita, e o número de observações declarado antes. Um
  caso a mais: apagada a moeda que servia de pivô, a triangulação deixa de existir e a
  parcela volta a ser termo próprio.
- [x] 10.4 Testar `ArchiveCurrencyUseCase`: a moeda base é recusada com motivo e permanece
  oferecida; arquivar é reversível e desarquivar a devolve a todos os formulários.
- [x] 10.5 Testar o que arquivar **não** faz: a moeda arquivada some da oferta, mas a conta
  nela continua ativa, aceita lançamento e é consolidada; as suas observações permanecem no
  acervo; e uma conversão que triangula por ela continua dando o mesmo resultado de antes do
  arquivamento.
- [x] 10.6 Testar o nome (D2): a moeda que o usuário não nomeou muda de idioma quando o
  idioma muda; a que ele nomeou permanece como ele escreveu; e uma linha sem nome cujo código
  a plataforma não reconhece exibe o próprio código, sem erro.
- [x] 10.7 Testar `SaveCurrencyUseCase`: código repetido recusado com motivo e nenhuma linha
  alterada; código que a plataforma declara de zero ou três casas decimais recusado com
  motivo; código inventado (`MILHAS`) aceito com o símbolo e o nome que o usuário escreveu; e
  cadastrar não cria conta, taxa nem orçamento.
- [x] 10.8 Guarda de **fonte única**, no formato dos testes que leem as fontes do repositório:
  nenhum arquivo de produção declara uma lista de moedas, todo consumidor lê do repositório, e
  nenhuma query, escrita ou validação de `:core:ledger` nomeia a tabela `currencies`.
