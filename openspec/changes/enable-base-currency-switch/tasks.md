# Tasks — `enable-base-currency-switch`

> **Ordenação de segurança (a regra que governa a ordem dos grupos).** A janela de risco
> desta change não é a troca da base — é o **par fora do eixo da base**. Enquanto toda
> linha do acervo estiver denominada contra a base em vigor, a leitura ingênua de hoje
> (`ratesAsOf` chaveado só por `currency`) continua exata. No instante em que existir uma
> observação EUR/USD sob base BRL, essa mesma leitura devolve "a taxa do EUR" e o redutor
> a multiplica como se fosse reais por euro: **figura errada, sem erro e sem marca** — a
> classe de defeito que esta change inteira existe para fechar. Por isso os dois únicos
> caminhos que criam par fora do eixo — a remoção da guarda do `HarvestExchangeRateUseCase`
> (D7) e o formulário de duas pontas (D8) — vivem no **grupo 6**, depois de o resolvedor de
> três níveis estar costurado no repositório (grupo 5). Não é preferência de ordenação: é a
> única ordem em que o intervalo entre "existe par livre" e "a leitura sabe lê-lo" tem
> comprimento zero.
>
> **O setter, ao contrário, é seguro cedo.** Ele entra no grupo 2, junto do par explícito,
> e é inofensivo até o grupo 7 por uma razão mecânica e não por disciplina: `SettingsScreen`
> não oferece a troca, e a única forma de alcançá-lo seria produção nova que ninguém
> escreve. Colocá-lo tarde custaria uma **segunda** quebra larga — os 13 arquivos de teste
> que implementam `IBaseCurrencyRepository` seriam tocados duas vezes —, e a única
> alternativa seria um corpo default que lança, que aqui não se paga porque não há como
> removê-lo depois sem tocar os mesmos 13.
>
> **Uma quebra larga, e uma só.** O grupo 2 é indivisível e termina com **a produção
> compilando e a suíte de testes vermelha**: `ExchangeRate.counterCurrency` entra **sem
> default** (é a mesma decisão que `Account.currency` tomou em D28 — uma linha que não diz
> o seu par é o defeito), `IExchangeRateRepository` ganha `rateBetween` **abstrato** e
> `IBaseCurrencyRepository` ganha o setter **abstrato**. Os três de uma vez, de propósito:
> a união dos arquivos afetados é **18**, e fazê-lo em três passos os tocaria três vezes.
> O grupo 3 é o fan-out mecânico que os fecha, doze subagentes, arquivos disjuntos — e é
> ele a prova de que ninguém ficou atrás, do mesmo modo que a remoção dos corpos que lançam
> foi na change anterior.
>
> **Dimensionamento apurado contra o código** (os artefatos erram alguns números; as tarefas
> usam os reais):
> - `IExchangeRateRepository`: **7** arquivos de produção o consomem (`ConsolidateMoneyUseCase`,
>   `SuggestCrossCurrencyAmountUseCase`, `ObserveConsolidationChangesUseCase`,
>   `HarvestExchangeRateUseCase`, `ExchangeRatesViewModel`, `ExchangeRateFormViewModel`,
>   `ExchangeRateRepository`) e **12** arquivos de teste o implementam como fake — **13**
>   declarações, porque `ViewBudgetViewModelTest` tem duas.
> - `IBaseCurrencyRepository`: **13** arquivos de teste o implementam (14 declarações, pela
>   mesma razão) e o `BaseCurrencyReachTest` fixa por nome uma lista de **18** arquivos de
>   produção autorizados a nomeá-lo. Essa lista **muda nesta change nos dois sentidos**:
>   `ExchangeRateRepository` entra (D4) e `HarvestExchangeRateUseCase` e
>   `SuggestCrossCurrencyAmountUseCase` **saem** — depois de D7 e do corolário de D4,
>   nenhum dos dois precisa saber qual é a base. É por isso que a entrada e as duas saídas
>   estão em grupos diferentes: as três tarefas escreveriam o mesmo arquivo.
> - `ExchangeRate(` é construído em **15** arquivos — **3** de produção
>   (`HarvestExchangeRateUseCase`, `ExchangeRateFormViewModel`, `ExchangeRateMapper`) e **12**
>   de teste (13 ocorrências; `SuggestCrossCurrencyAmountUseCaseTest` tem duas).
>   `ExchangeRateEntity(` é construído num **único** sítio fora da própria declaração: o mapper.
> - `MigrationSchemaEquivalenceTest` tem hoje **dois** `@Test`, não um: a cadeia a partir de
>   v7 (`MIGRATION_7_10` + `migration1011()`) e a partir de v10 (`migration1011()`). **As duas**
>   quebram no instante em que `AppDatabase` vira 12, e nasce uma terceira a partir de v11.
>   As fixtures `V7Schema.kt` e `V10Schema.kt` existem; `V11Schema.kt` não.
> - Os schemas exportados vivem em `core/database/schemas/com.neoutils.finsight.database.AppDatabase/`
>   e são hoje **três**: `7.json`, `10.json`, `11.json`.
>
> **Divergências entre os artefatos e o código, registradas e resolvidas em favor do código:**
> - O `proposal.md` cita **seis KDocs** que afirmam que a troca não é oferecida. São seis
>   sítios, mas **não os seis listados**. Confirmados: `IBaseCurrencyRepository` (o KDoc *e* o
>   bloco de comentário final), `BaseCurrencyRepository`, `ExchangeRate`, `ExchangeRateEntity`.
>   O quinto **não é o `SettingsViewModel`** — ali a afirmação é um comentário de linha
>   (`SettingsViewModel.kt:15`); o KDoc que diz *"v1 offers no way to change it"* é o do
>   **`SettingsScreen`** (`:55-68`). E o sexto **não é o `ObserveConsolidationChangesUseCase`**,
>   cujo texto ("nem trocar a moeda base escreve entry") continua verdadeiro e fica *mais*
>   pertinente, não menos: o sexto é **`feature/settings/api/.../SettingsGraph.kt:14`**,
>   *"v1 does not offer changing the base currency"*, que o proposal não lista.
>   Acrescente-se `BaseCurrencySwitchDerivationTest`, cuja justificativa inteira some.
> - O `proposal.md` chama a migração de `MIGRATION_11_12`. As migrações parametrizadas deste
>   projeto são **funções**, não `val` (`fun migration1011(relabelCurrency: String? = null)`),
>   e esta recebe um parâmetro obrigatório — logo `fun migration1112(baseCurrency: String)`.
> - O caminho pelo qual a base chega à migração já existe e tem nome: `fun interface
>   LegacyRelabel`, declarada em `core/model` e bindada em `ModelModule`, entregue a
>   `getRoomDatabase(relabelCurrency = ...)`. `SeededBaseCurrency` é o mesmo movimento, e é
>   isso que torna a tarefa 1.1 pequena.
>
> **Ressalva que vale para a change inteira.** `ConsolidateMoneyUseCase`, `MoneyByCurrency`,
> `DisplayAmount`, `ConsolidatedAmount`, `ObserveConsolidationChangesUseCase` e o
> `:core:ledger` **não são tocados por nenhuma tarefa abaixo**. Se uma tarefa se vir
> precisando alterá-los, ela está errada — a re-expressão inteira é leitura, e a leitura
> tem dono no `ExchangeRateRepository` (D4). O único arquivo de `core/model` que muda de
> comportamento é o `HarvestExchangeRateUseCase`, e o que ele ganha é uma **remoção**.

---

## 1. Preparações que não dependem de nada

Barreira de entrada: nenhuma — as duas tarefas partem da árvore como está.
Paralelo: 1.1 e 1.2, dois subagentes, arquivos disjuntos e módulos disjuntos.
Barreira de saída: o projeto compila e `./gradlew allTests` continua verde. **Nenhuma
das duas altera comportamento**: o binding novo de 1.1 fica inerte porque ninguém o
resolve ainda, e a fixture de 1.2 é um `internal val` sem consumidor. É por isso que este
grupo pode correr antes da quebra do grupo 2 em vez de depois dela.

- [ ] 1.1 (paralelo) Declarar `fun interface SeededBaseCurrency { fun code(): String }` em `core/model/.../domain/model/`, ao lado de `LegacyRelabel` e pelo mesmo argumento literal do KDoc dela — *"o módulo de baixo recebe o que não pode nomear"*: `core/database` não alcança `Settings`, não alcança `IBaseCurrencyRepository` e não deve alcançar, então recebe um código puro. Bindá-la em `feature/settings/impl/.../di/SettingsModule.kt`, que é onde `IBaseCurrencyRepository` já é bindado e portanto o único lugar em que a preferência é visível — `single<SeededBaseCurrency> { SeededBaseCurrency { get<IBaseCurrencyRepository>().observe().value } }`. Verificação: **não há ciclo de DI** — `BaseCurrencyRepository` depende só de `Settings`, nunca de `AppDatabase`, e é isso que permite que a migração a leia. Nada consome o binding ainda. Realiza D10.
- [ ] 1.2 (paralelo) Criar `core/database/src/jvmTest/.../V11Schema.kt`, verbatim do `11.json` exportado, no molde exato de `V10Schema.kt` e `V7Schema.kt` (`internal val V11_SCHEMA: List<String>`, com o comentário de cabeçalho registrando que é história congelada). Verificação: uma fixture que não é o schema antigo real não prova nada sobre a migração real — as declarações têm de vir do `11.json`, não do que a entidade diz hoje. Sem consumidor até 3.11.

---

## 2. O par explícito no dado — a quebra larga, e a única

Barreira de entrada: 1.1 (a migração precisa do `SeededBaseCurrency` para resolver o
preenchimento) e 1.2 não é dependência deste grupo.

**Tarefa única, e a indivisibilidade é de compilação e não de gosto.** Room recusa uma
entidade cujo schema divergiu sem que a versão subisse — a coluna, o `version = 12`, a
migração e o `12.json` são um passo só. A esse passo estão presos, pela mesma cadeia de
tipos, o `ExchangeRate` de domínio (a coluna sem contraparte no modelo seria a mentira
que a change existe para matar) e o `ExchangeRateMapper` (único construtor de
`ExchangeRateEntity` fora da declaração). E as duas assinaturas novas entram junto por
economia de quebra, não por parentesco: os arquivos que elas quebram são um subconjunto
dos que a coluna já quebra.

Barreira de saída, dita por inteiro: **toda a produção compila**; `12.json` existe e
declara `counterCurrency` `NOT NULL` e o único `(currency, counterCurrency, date, source)`;
e **20 arquivos de teste estão vermelhos** — os 18 da união acima mais os dois de migração.
O grupo 3 os fecha, e é a passagem dele que autoriza o grupo 4.

- [ ] 2.1 (barreira global; depende de 1.1) O par explícito, em um passo:
  - **`core/database/.../entity/ExchangeRateEntity.kt`** — `counterCurrency: String` **sem default**, entre `currency` e `date`; o índice único passa de `(currency, date, source)` para `(currency, counterCurrency, date, source)` e o índice de leitura de `(currency, date)` para `(currency, counterCurrency, date)`. O KDoc perde as frases *"It does not name the base currency"* e *"Fixing the direction matters…"* — as duas deixam de ser verdade neste commit — e passa a dizer o que a linha diz sozinha: *uma unidade de [currency] vale [rate] de [counterCurrency]*, e por que a direção **não** é canonicalizada (D2: inverter para gravar produz um número que ninguém observou, que é o mesmo defeito que gravar a forma exibida, aplicado à entrada).
  - **`core/database/.../AppDatabase.kt`** — `version = 11` → `12`. Nenhuma entidade nova.
  - **`core/database/.../Database.kt`** — `fun migration1112(baseCurrency: String)`, no molde de `migration1011`: `ALTER TABLE exchange_rates ADD COLUMN counterCurrency TEXT NOT NULL DEFAULT ''`; `UPDATE exchange_rates SET counterCurrency = '<base>'`; `DROP INDEX index_exchange_rates_currency_date_source` e `index_exchange_rates_currency_date`; `CREATE UNIQUE INDEX index_exchange_rates_currency_counterCurrency_date_source` e `CREATE INDEX index_exchange_rates_currency_counterCurrency_date` — os nomes canônicos que o Room gera, porque é contra eles que a checagem de identity hash compara. O `require(baseCurrency.matches(Regex("[A-Z]{3}")))` é obrigatório pela mesma razão que na relabel: `execSQL` não faz binding, o código é interpolado, e o módulo recusa depender de o chamador ter validado. O KDoc registra que o preenchimento é **exato e não aproximado** — toda linha existente foi medida contra a base em vigor, que nunca teve como mudar —, a mesma qualidade que a moeda do limite de orçamento teve na `MIGRATION_10_11`. Fechar com os três guards de sempre (`verifyLedgerBalanced`, `verifyNoOrphanDimensions`, `verifyForeignKeys`, `stage = "v11 → v12"`).
  - **`core/database/.../di/DatabaseModule.kt`** e a assinatura de `getRoomDatabase` — a base chega como `baseCurrency: String` ao lado do `relabelCurrency` que já existe, resolvida por `get<SeededBaseCurrency>().code()`. Registrar `migration1112(baseCurrency)` no `addMigrations`, **depois** de `migration1011(relabelCurrency)`.
  - **`core/model/.../domain/model/ExchangeRate.kt`** — `counterCurrency: String` **sem default**, pela mesma razão que `Account.currency` perdeu o dele em D28: um default aqui é a denominação implícita voltando pela porta do construtor. O KDoc perde *"against the user's base currency"*.
  - **`core/model/.../domain/repository/IExchangeRateRepository.kt`** — ganha `suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate?`, **abstrato**, com o KDoc dizendo que a precedência declarada (direta ▸ inversa ▸ um pivô) é responsabilidade do impl e que `ratesAsOf` é o caso particular *"todas contra a base"*. As assinaturas que o redutor consome (`rateAsOf`, `ratesAsOf`, `observeAll`, `save`, `remove`) **não mudam de forma** — é isso que mantém `ConsolidateMoneyUseCase` intacto.
  - **`core/model/.../domain/repository/IBaseCurrencyRepository.kt`** — ganha `suspend fun set(code: String)`, **abstrato**; o bloco de comentário final ("There is no setter, and its absence is the design") é **apagado**, não editado, e o KDoc da interface perde *"v1 offers no screen that changes it"* e passa a dizer por que o setter voltou a ser honesto: toda linha do acervo diz contra o que foi medida, e nenhuma muda de significado quando a preferência muda.
  - **`feature/settings/impl/.../database/repository/BaseCurrencyRepository.kt`** — implementa `set` com as duas linhas de D5 (`settings.putString(KEY, code)` + `_currency.value = code`) e nada mais: nenhuma re-expressão, nenhuma migração, nenhuma linha do acervo tocada. O parágrafo *"There is no write path"* sai. **Não existe `SwitchBaseCurrencyUseCase`** e nenhuma tarefa desta change o cria (D5).
  - **`feature/settings/impl/.../database/mapper/ExchangeRateMapper.kt`** — mapeia a coluna nos dois sentidos.
  - **Os dois sítios de construção de produção restantes**, provisoriamente contra a base: `HarvestExchangeRateUseCase` nomeia o par que já calcula (a ponta base como contraparte) e `ExchangeRateFormViewModel` passa `counterCurrency = base`. Os dois são refinados no grupo 6 — aqui só param de não compilar, e o comportamento observável é byte a byte o de hoje.

  Verificação: `./gradlew :app:android:assembleDebug` e `:app:desktop:run` compilam; `12.json` declara a coluna e o índice único novo; nenhum valor gravado muda. Realiza D1, D2, D5 e D10.

---

## 3. O fan-out mecânico que fecha a quebra

Barreira de entrada: 2.1 inteira. Barreira de saída: **`./gradlew allTests` verde** — e é
esta barreira, e só ela, que autoriza o grupo 4.

**Doze subagentes, arquivos disjuntos, nenhuma tarefa dependendo de outra.** O trabalho é
mecânico e idêntico em toda parte: nomear a contraparte em cada `ExchangeRate(`
(`counterCurrency = "BRL"` onde a base do fixture é BRL — a contraparte que sempre esteve
implícita), responder `rateBetween` em cada fake de `IExchangeRateRepository` (`= null` nos
fakes vazios; delegando ao mapa nos fakes que têm taxas) e responder `set` em cada fake de
`IBaseCurrencyRepository` (escrevendo no `MutableStateFlow` que o fake já tem). **Nenhum
teste muda de asserção** — se um passar a afirmar coisa diferente, o corte está errado.

- [ ] 3.1 (paralelo) `core/model/src/commonTest/` — `ConsolidateMoneyUseCaseTest.kt` (o `FakeRates` e o `FakeBaseCurrency` que os outros dois arquivos reaproveitam), `HarvestExchangeRateUseCaseTest.kt` e `SuggestCrossCurrencyAmountUseCaseTest.kt` (duas construções). O `FakeBaseCurrency` daqui é o único do módulo, então `set` entra nele uma vez.
- [ ] 3.2 (paralelo) `app/shared/src/jvmTest/` — `ForeignAccountGateTest.kt` e `SingleCurrencyGateTest.kt` (uma construção cada). Conferir se `AppModulesTest` alcança o binding novo de 1.1: se ele verifica resolubilidade por tipo, `SeededBaseCurrency` entra na lista; se não, o arquivo não é tocado.
- [ ] 3.3 (paralelo) `feature/settings/impl/src/commonTest/` — `ExchangeRatesViewModelTest.kt` (fake de taxas + fake de base + construção), `ui/modal/exchangeRateForm/ExchangeRateFormViewModelTest.kt` (idem), `BaseCurrencySwitchDerivationTest.kt` (**só o mínimo para compilar** — a reescrita dele é 7.2, e antecipá-la aqui o faria testar um resolvedor que ainda não existe) e `BaseCurrencyRepositoryTest.kt`, que ganha o caso do setter: escrever emite no flow e persiste no `Settings`, e reabrir o repositório lê o valor escrito e **não** re-semeia pelo locale.
- [ ] 3.4 (paralelo) `feature/budgets/` — `impl/.../ui/modal/viewBudget/ViewBudgetViewModelTest.kt` (**dois** objetos anônimos de cada interface), `impl/.../database/repository/BudgetClosedCategoryTest.kt` e `api/.../domain/usecase/CalculateBudgetProgressUseCaseTest.kt`. Dois módulos, mas disjuntos de todas as outras tarefas do grupo.
- [ ] 3.5 (paralelo) `feature/categories/impl/src/commonTest/` — `ui/modal/viewCategory/ViewCategoryViewModelTest.kt` e `domain/usecase/CalculateCategorySpendingUseCaseImplTest.kt`.
- [ ] 3.6 (paralelo) `feature/dashboard/impl/src/commonTest/.../DashboardMoneyFixtures.kt` — `FakeExchangeRateRepository` e `FakeBaseCurrencyRepository`.
- [ ] 3.7 (paralelo) `feature/transactions/impl/src/commonTest/.../FakeLedger.kt` — `NoExchangeRates` e `FakeBaseCurrency`.
- [ ] 3.8 (paralelo) `feature/report/impl/src/commonTest/.../ReportViewerViewModelCharacterizationTest.kt` — os dois objetos anônimos.
- [ ] 3.9 (paralelo) `feature/accounts/impl/src/commonTest/.../AccountFormCurrencyRowTest.kt` — `StubBaseCurrency`. E `feature/creditcards/impl/src/jvmTest/.../FixedBaseCurrency.kt` — o único fake do módulo. Dois arquivos, dois módulos, nenhum outro os toca.
- [ ] 3.10 (paralelo) `core/database/src/jvmTest/.../MigrationSchemaEquivalenceTest.kt` — os **dois** `@Test` existentes passam a encadear `migration1112(baseCurrency = "BRL")` ao fim (o de v7 e o de v10; os dois quebram sozinhos no instante em que `AppDatabase` vira 12, e não é só acrescentar um caso), e nasce o terceiro: semear `V11_SCHEMA` (fixture de 1.2), `PRAGMA user_version = 11`, abrir com `.addMigrations(migration1112("BRL"))`. É este teste que roda a validação de schema do próprio Room — a checagem de identity hash que, sem ele, falharia no aparelho do usuário e não aqui.
- [ ] 3.11 (paralelo; depende de 1.2 para a fixture) `core/database/src/jvmTest/.../Migration11To12Test.kt`, novo, no molde de `Migration10To11Test.kt` e usando `MigrationTestHelpers.getColumns`: a coluna existe e é `NOT NULL`; **toda** linha pré-existente recebe exatamente a base passada, e uma base que não seja `BRL` prova que o parâmetro é o parâmetro e não um literal escondido; **nenhum valor de `rate`, `date`, `currency` ou `source` muda**; o índice único novo existe e o velho não; e uma segunda linha do mesmo `(currency, date, source)` com contraparte diferente passa a ser inserível, que é o que o índice novo abre. Um código que não seja ISO 4217 é recusado pelo `require` antes de qualquer `execSQL`.
- [ ] 3.12 (paralelo) Passada de conferência de que a quebra fechou por inteiro e não por acaso: nenhuma fonte de teste constrói `ExchangeRate(` sem nomear a contraparte, e nenhum fake de `IExchangeRateRepository`/`IBaseCurrencyRepository` sobrou sem os membros novos. É verificação, não edição — se ela encontrar arquivo, é porque uma das onze acima não fechou, e a correção é lá.

---

## 4. As três peças da resolução, ainda sem costura

Barreira de entrada: grupo 3 verde (o par existe no dado e nada está vermelho).
Paralelo: 4.1, 4.2 e 4.3, três subagentes, arquivos e módulos disjuntos.

**O que torna 4.1 e 4.2 independentes é uma decisão de desenho, e ela é deliberada:** o
resolvedor de 4.2 é definido sobre `List<ExchangeRate>` — domínio puro —, e **não** sobre o
que o DAO devolve. Assim a precedência de D3 é testável sem banco, o DAO continua sendo o
dono único da política de data, e a costura entre os dois é 5.1 e só ela. Definir o
resolvedor sobre o retorno do DAO os acorrentaria e não compraria nada.

Barreira de saída: compila e `allTests` verde; **nenhum comportamento observável mudou** —
as consultas novas do DAO não têm chamador e o resolvedor não tem consumidor. O grupo
inteiro é aditivo.

- [ ] 4.1 (paralelo) `core/database/.../dao/ExchangeRateDao.kt` deixa de responder *"a taxa da moeda"* e passa a responder *"as observações que tocam a moeda"*, com a política de data **intacta e ainda como query**: (a) `ratesAsOf` particiona o `NOT EXISTS` por `(e.currency, e.counterCurrency)` em vez de só por `e.currency` — hoje o resultado é o mesmo porque toda linha tem a mesma contraparte, e passa a divergir no instante em que o grupo 6 criar a primeira observação fora do eixo; (b) uma consulta que devolve, em ou antes da data, **uma linha por par** já resolvida pela política (a matéria-prima da triangulação); (c) uma consulta por par exato `(from, to)` para o nível direto. `getByCurrency`, `insert`, `update`, `delete`, `deleteById` e `observeAll` não mudam de assinatura — `observeAll` continua sendo o gatilho que `ObserveConsolidationChangesUseCase` funde, e é por isso que aquele arquivo não é tocado. Verificação: teste jvm sobre a DAO no molde das suítes de query existentes — a do usuário vence a derivada na mesma data, a última data em ou antes vence a anterior, e os dois sentidos do mesmo par coexistem como duas linhas.
- [ ] 4.2 (paralelo) O resolvedor puro de D3, `internal` a `feature/settings/impl` (arquivo novo em `.../database/repository/`, ao lado do repositório que o consumirá), sobre `List<ExchangeRate>`: **direta ▸ inversa (`1/r`) ▸ uma triangulação por pivô**, parando no primeiro nível que responde; dentro de cada nível vale a política que já governa o acervo, e nas duas pernas do pivô os níveis 1 e 2 valem outra vez. **Um salto, nunca dois** — encadear duas triangulações compõe três arredondamentos num número que tela nenhuma explica, e é sempre sintoma de acervo em que falta a observação óbvia; ali a resposta certa é *não há taxa*, exatamente como D9 da change anterior já decidiu para uma taxa ausente. **Desempate determinístico e total:** vence o pivô cujas duas pernas tenham as datas mais recentes; empate pelo código ISO crescente — o segundo critério é arbitrário de propósito, e o que importa nele é ser total, não ser justo. Teste no mesmo arquivo de tarefa: os cinco cenários da spec (direta vence pivô; inversa vence pivô; a triangulação resolve o que a troca de base deixou implícito; dois saltos **não** são compostos; dois pivôs possíveis dão sempre o mesmo), mais o caso em que nada resolve e a resposta é `null` — e `null` **MUST NOT** virar `1`. Realiza D3.
- [ ] 4.3 (paralelo) As chaves de string que os grupos 6 e 7 consomem, em `core/resources/.../values/strings.xml` **e** `core/resources/.../values-en/strings.xml`, na mesma tarefa: o cabeçalho de grupo da listagem, a linha auto-descritiva (`1 %1$s = %2$s %3$s`, que substitui `exchange_rates_quote` e torna `exchange_rates_base_hint` — *"Cotações em %1$s"* — obsoleta nos dois arquivos), os rótulos das duas pontas do formulário e o seu texto de ajuda por par (substituindo `exchange_rate_form_rate_helper`), o título do seletor de contraparte, e o título do seletor da moeda base em Configurações. **Esta tarefa é a dona da faixa, e existe para que as três tarefas do grupo 6 possam correr juntas sem disputar `strings.xml`** — que é o único arquivo que elas teriam em comum. Os prefixos são disjuntos (`exchange_rates_*`, `exchange_rate_form_*`, `settings_base_currency_*`) e cada um cai na sua secção do arquivo, que já é organizado assim. Uma chave que faltar depois entra pelos dois arquivos na tarefa que a descobrir — a regra do projeto é que uma chave presente num só é bug, e ela não tem exceção.

---

## 5. A costura: o repositório passa a resolver

Barreira de entrada: 4.1 e 4.2 (a peça de leitura e a peça de decisão).

**Tarefa única, e não por falta de trabalho a paralelizar: os três arquivos que ela toca
formam um só corte.** O `ExchangeRateRepository` ganhar `IBaseCurrencyRepository` obriga a
mudar o `SettingsModule` (a construção) e o `BaseCurrencyReachTest` (que fixa por **nome**
quem pode alcançar a base). Uma segunda tarefa que também escrevesse o reach test não seria
irmã independente — seria um conflito.

Barreira de saída: `allTests` verde e **`IExchangeRateRepository` volta a ser verdade por
construção** em vez de por acidente. A partir daqui, uma observação fora do eixo da base é
lida corretamente — e é isso, e nada além disso, que autoriza o grupo 6 a criá-las.

- [ ] 5.1 (barreira; depende de 4.1 e 4.2) `feature/settings/impl/.../database/repository/ExchangeRateRepository.kt` passa a ser o dono da precedência: ganha `IBaseCurrencyRepository` no construtor, implementa `rateBetween(from, to, date)` sobre o resolvedor de 4.2 alimentado pelas consultas de 4.1, e reexprime `rateAsOf(currency, date)` e `ratesAsOf(date)` como os casos particulares *"contra a base em vigor"* — **as assinaturas não mudam**, e é por isso que `ConsolidateMoneyUseCase`, as ViewModels e toda tela que exibe figura não são tocados por nenhuma linha. Bindar a dependência nova em `SettingsModule.kt`. Acrescentar `ExchangeRateRepository.kt` à lista de `allowed` do `app/shared/src/jvmTest/.../BaseCurrencyReachTest.kt`, com o comentário dizendo por que ela é legítima: é o **único** ponto que pode saber ao mesmo tempo o que está gravado e qual preferência está em vigor, e a dependência substitui a suposição implícita que existia antes — não denomina figura alguma. Verificação, sobre um banco real: com a base em BRL e o acervo só com `(USD,BRL)` e `(EUR,BRL)`, trocar a base para USD faz `ratesAsOf` devolver o EUR por triangulação sobre o BRL e o BRL pela inversa, **sem que nenhuma linha seja criada ou alterada** — que é o cenário homônimo da spec. Realiza D4.

---

## 6. Fora do eixo da base, e as telas que o mostram

Barreira de entrada: **5.1, e a razão é a ordenação de segurança do cabeçalho.** As três
tarefas deste grupo criam, cada uma pelo seu caminho, observações cujo par não toca a base.
Antes de 5.1, uma dessas linhas seria devolvida por `ratesAsOf` como se fosse a taxa daquela
moeda contra a base, e o redutor a aplicaria — toda figura consolidada silenciosamente
errada, sem erro e sem marca. Depois de 5.1 o intervalo tem comprimento zero. É por isso
que D7 não pode vir antes: não é que ela seja arriscada em si, é que ela é o primeiro
produtor automático de par fora do eixo, e um produtor não pode preceder o leitor.

Paralelo: 6.1, 6.2 e 6.3, três subagentes. Arquivos disjuntos, inclusive `strings.xml` —
nenhuma delas acrescenta chave, porque 4.3 já as declarou nos dois arquivos.
Barreira de saída: `allTests` verde; existe par fora do eixo, ele é resolvido pela
precedência declarada, e a tela o mostra na direção em que foi observado.

- [ ] 6.1 (paralelo) A guarda sai, e os dois use cases deixam de nomear a base. Em `core/model/.../domain/usecase/HarvestExchangeRateUseCase.kt`, remover o `when (base) { ... else -> return null }` e a dependência de `IBaseCurrencyRepository` inteira: a taxa colhida passa a ser a observação do par das duas pontas da operação, na direção em que ela aconteceu. A guarda nunca foi regra de domínio — era a consequência de a linha não conseguir dizer sobre que par falava —, então isto é escopo que **deixa de ser artificialmente removido**, e o KDoc troca *"A leg in neither currency teaches nothing"* pelo motivo de a frase ter deixado de valer. Em `core/model/.../domain/usecase/SuggestCrossCurrencyAmountUseCase.kt`, substituir os três ramos por uma chamada a `rateBetween(from, to, on)` e remover `IBaseCurrencyRepository`: entre duas não-base ele deixa de ser cego **sem ganhar código próprio**, porque consome o mesmo resolvedor que o redutor consome; o parágrafo *"Between two non-base currencies it answers nothing"* sai, e `impliedRate` não muda. Remover as **duas** entradas correspondentes da lista de `allowed` do `BaseCurrencyReachTest`. Nos testes: `HarvestExchangeRateUseCaseTest` perde `a crossing between two non-base currencies teaches nothing` e ganha o seu oposto — o cruzamento USD↔EUR sob base BRL registra a observação do par e ela passa a poder servir de caminho —, e `SuggestCrossCurrencyAmountUseCaseTest` perde `between two non-base currencies it says nothing at all` pela mesma razão. Realiza D7 e o corolário de D4.
- [ ] 6.2 (paralelo) O formulário escolhe as duas pontas (D8). `ExchangeRateFormUiState` passa a carregar `from`/`to` (com a base em vigor pré-selecionada numa delas) e o catálogo **inteiro** nas duas — o filtro `it.code != base` deixa de fazer sentido em ambas, porque precificar a própria base contra outra moeda é observação legítima cuja inversa alimenta a leitura por D3; `canSubmit` passa a ser `rate != null && from != to`, que é a única restrição que sobra. `ExchangeRateFormAction` ganha a seleção da segunda ponta; `ExchangeRateFormViewModel` grava o par como escolhido e **MUST NOT** ordenar as pontas nem inverter o quociente (D2); `ExchangeRateFormModal` ganha a segunda linha de seletor, consumindo o `CurrencyPickerModal` de `core/designsystem` como está, e o texto de ajuda passa a nomear as duas moedas. Editar uma observação existente abre o formulário **na direção em que ela foi feita** (cenário homônimo da spec). Consequência aceita e registrada no KDoc: é possível cadastrar um par inerte — EUR/JPY sob base BRL sem ponte —, e barrá-lo exigiria que o formulário soubesse resolver caminhos, que é conhecimento de 5.1, para prevenir um dado inofensivo. `ExchangeRateFormViewModelTest` cobre as duas pontas, o `from ≠ to` e a não-canonicalização.
- [ ] 6.3 (paralelo) A listagem agrupa pela moeda precificada (D9). `ExchangeRatesUiState` passa a expor grupos em vez de lista plana, chaveados pela moeda **precificada** — a que a linha responde *quanto vale* —, com os grupos ordenados pela observação mais recente de cada moeda, que é a extensão natural do `ORDER BY date DESC` que o DAO já faz. `ExchangeRatesViewModel` monta os grupos e mantém intacta a regra dos 30 dias (`OUTDATED_AFTER_DAYS`, que continua sendo opinião sobre volatilidade declarada uma vez). `ExchangeRatesScreen` passa a renderizar cada linha **descrevendo-se por inteiro** — `1 USD = 5,50 BRL · 14/03 · você` —, de modo que o significado não dependa do cabeçalho. Uma linha **MUST NOT** ser exibida invertida em relação à observação que a originou: esta tela é também o ponto de edição, e editar uma linha invertida abriria a correção de um número que ninguém observou. Consequência aceita: depois de uma troca o mesmo par aparece em dois grupos, um por sentido, e é isso mesmo — são duas observações distintas. `ExchangeRatesViewModelTest` cobre o agrupamento, a ordem dos grupos e o mesmo par nos dois sentidos.

---

## 7. A troca é oferecida, e a prosa que a proibia morre

Barreira de entrada: grupo 6 verde. Só aqui a troca ganha porta de entrada — o setter
existe desde 2.1, e o que faltava era o resolvedor por baixo dela.
Paralelo: 7.1, 7.2, 7.3 e 7.4, quatro subagentes, arquivos disjuntos.
Barreira de saída, e é a final da change: `./gradlew allTests` verde; nenhuma fonte de
produção afirma que a troca não é oferecida; e o teste de 7.4 prova, sobre banco real, que
trocar a base re-exprime toda figura sem criar, alterar ou remover uma linha do acervo.

- [ ] 7.1 (paralelo) A linha da moeda base vira clicável (D6). `SettingsUiState` ganha o que a linha precisa para se apresentar como ação; `SettingsViewModel` ganha a ação de troca, que chama `IBaseCurrencyRepository.set` e nada mais — **não existe `SwitchBaseCurrencyUseCase`**, e criá-lo seria dar dono a uma operação que não faz nada (D5); `SettingsScreen` abre o `CurrencyPickerModal` de `core/designsystem`, consumido como está e já usado por quatro features, com o **catálogo inteiro**: sem confirmação, sem cálculo de cobertura, sem bloquear moeda que o acervo não alcança e sem exigir cadastro de taxa no fluxo (D6). Trocar para uma moeda que o acervo não alcança faz as figuras degradarem em termos por moeda, que é comportamento **já definido e já testado** — a troca só o alcança por outra porta. A prosa dos dois arquivos morre aqui: o KDoc de `SettingsScreen` (`:55-68`) perde *"read-only here on purpose"* e *"v1 offers no way to change it"*, e o comentário de `SettingsViewModel` (`:15-16`) perde *"even though v1 offers no way to change it"* — o flow deixa de ser preparação para o futuro e passa a ser o mecanismo em uso. Um teste de ViewModel cobre que a troca escreve a preferência e que **nenhuma outra escrita acontece**.
- [ ] 7.2 (paralelo) `feature/settings/impl/src/commonTest/.../BaseCurrencySwitchDerivationTest.kt` deixa de ser substituto. A sua justificativa inteira — *"a derivação é escrita aqui em vez de embarcada como produção, porque embarcá-la seria embarcar uma feature que a v1 não oferece"* — some, porque a produção agora a tem. Reescrevê-lo sobre o **resolvedor real** (4.2/5.1) em vez de sobre aritmética de teste: a inversa e a triangulação passam a ser exercidas pelo código que embarca, com os mesmos números; o *round trip* continua; e `no stored row changes` **permanece e ganha peso** — passa a ser afirmado contra uma troca de base de verdade, que é exatamente a implementação que ele existe para barrar (a que reescreveria o acervo, e que seria migração).
- [ ] 7.3 (paralelo) A prosa residual, nos dois arquivos que nenhuma outra tarefa toca: `feature/settings/api/.../SettingsGraph.kt:14` — *"There is no separate base-currency screen. v1 does not offer changing the base currency"* — passa a descrever o que existe (a troca é um modal sobre a tela de Configurações, e continua **não** havendo tela própria, o que é a metade da frase que sobrevive); e `core/model/.../domain/usecase/ObserveConsolidationChangesUseCase.kt`, cujo texto **não afirma** que a troca não é oferecida e portanto não estava errado — o que muda é que a frase *"nem trocar a moeda base escreve entry"* deixa de ser hipótese e passa a descrever um caminho que o usuário percorre, e o KDoc passa a dizer isso. **Nenhuma linha de código destes dois arquivos muda**; a reatividade já foi paga pela change anterior.
- [ ] 7.4 (paralelo) O gate de fim, em `app/shared/src/jvmTest/`, novo arquivo no molde de `CrossCurrencyEndToEndTest`: banco real, contas em três moedas, acervo cadastrado contra a base semeada; trocar a base e afirmar, na ordem, os cinco cenários que a spec exige e que só um teste de ponta a ponta alcança — a troca acontece sem migração e sem reprocessamento; **nenhuma linha do acervo foi criada, alterada ou removida**; as figuras de períodos passados passam a ser expressas na base nova por inversa e triangulação, retroativamente; trocar para uma moeda que o acervo não alcança não é impedido e degrada em termos por moeda; e **voltar à base anterior devolve exatamente as figuras de antes**, porque nada foi convertido, gravado ou perdido. É a prova de que a re-expressão inteira é leitura.
