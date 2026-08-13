# Tasks

## 1. Fundamento no razão (D3)

**Para começar:** nada — este é o primeiro elo da cadeia. `matches` (grupo 2) precisa deste
fato pronto, porque é ele que separa "não classificado" de "fora do eixo"; escrevê-lo em
`core/model` espalharia conhecimento do plano de contas para fora de quem o define.
**Ao terminar:** `./gradlew :core:ledger:compileKotlinJvm` compila e nenhum consumidor existente
muda de comportamento (a propriedade é aditiva).

- [x] 1.1 Adicionar `val hasNominalLeg: Boolean get() = entries.any { it.account.type.isNominal }` em `core/ledger/src/commonMain/kotlin/com/neoutils/finsight/domain/model/Transaction.kt`, ao lado de `hasLiabilityLeg` e `hasAssetLeg`, com KDoc de uma linha no mesmo tom dos irmãos (o terceiro fato da mesma família, sem narrar a mudança).

## 2. Derivação do eixo em `core/model` (D2)

**Para começar:** o grupo 1 concluído — `Transaction.hasNominalLeg` existe e compila.
**Ao terminar:** `./gradlew :core:model:compileKotlinJvm` compila e existe **um** dono da
definição de pertinência ao eixo, consumível por qualquer superfície.

- [x] 2.1 Adicionar `fun Transaction.matches(subject: SpendingSubject): Boolean` em `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/SpendingSubject.kt`, ao lado do tipo-soma: `Categorized(c)` → `nominalDimensionId == c.dimensionId`; `Uncategorized` → `hasNominalLeg && nominalDimensionId == null`. KDoc registra por que a ausência de perna nominal fica fora do eixo e por que a dimensão órfã não cai em nenhum dos dois ramos, sem ramo próprio.

## 3. Provas do domínio

**Para começar:** os grupos 1 e 2 concluídos — `hasNominalLeg` e `matches` existem.
**Ao terminar:** `./gradlew :core:ledger:jvmTest` e `./gradlew :core:model:jvmTest` passam, e a
concordância entre `matches` e o critério que o razão usa para o total sem classificação está
fixada por teste (mitigação do risco das duas implementações, uma em memória e uma em SQL).

- [x] 3.1 Criar `core/ledger/src/commonTest/kotlin/com/neoutils/finsight/domain/model/TransactionNominalLegTest.kt` fixando `hasNominalLeg`: verdadeiro para despesa e para receita; falso para transferência (duas pernas `ASSET`), pagamento de fatura (`ASSET` + `LIABILITY`) e ajuste (`EQUITY`).
- [x] 3.2 Criar `core/model/src/commonTest/kotlin/com/neoutils/finsight/domain/model/SpendingSubjectMatchesTest.kt` cobrindo os cenários da spec para `matches`: despesa sem dimensão na perna nominal entra em `Uncategorized`; receita sem dimensão também entra; transferência, pagamento de fatura e ajuste não entram; lançamento categorizado não entra em `Uncategorized` e entra apenas em `Categorized` da sua categoria; dimensão órfã (perna nominal com `dimensionId` que não resolve para categoria alguma) não entra em recorte algum do eixo.

## 4. O filtro passa a selecionar sobre o eixo (D1, D4, D5, D6)

**Para começar:** o grupo 2 concluído — `matches` existe, senão o ViewModel não tem predicado
a consumir. É por isso que este grupo vem depois: D3 antes de D2, e D2 antes do consumo.
**Ao terminar:** `./gradlew :feature:transactions:impl:compileKotlinJvm` compila. Dentro do
grupo cada tarefa toca **um** arquivo distinto e nenhuma lê a saída de outra (todos os nomes
novos estão fixados por D1); o módulo só volta a compilar quando todas tiverem entrado — é uma
renomeação única guiada pelo compilador, sem conversão implícita possível.

- [x] 4.1 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/transactions/TransactionsFilters.kt`: trocar `val category: Category?` por `val subject: SpendingSubject? = null`, com `null` como estado neutro que não recorta.
- [x] 4.2 Em `.../transactions/TransactionsAction.kt`: `SelectCategory(val category: Category?)` passa a carregar `SpendingSubject?` (`data class SelectCategory(val subject: SpendingSubject?)`), sem segundo controle booleano ao lado.
- [x] 4.3 Em `.../transactions/TransactionsUiState.kt`: `selectedCategory: Category?` vira `selectedSubject: SpendingSubject?`; `categories` permanece como está, pois o menu continua listando as categorias existentes.
- [x] 4.4 Em `.../transactions/TransactionsViewModel.kt`: substituir `List<Transaction>.filter(category: Category?)` por um recorte que delega a `Transaction.matches(subject)` de `core/model` (nenhuma segunda definição de "sem categoria" nesta tela); ligar `SelectCategory` a `filters.copy(subject = ...)`; publicar `selectedSubject`; incluir `filters.subject != null` em `canClearFilters`; e remover o parâmetro morto `category: Category?` do construtor (D6). `ClearFilters` continua devolvendo `TransactionsFilters()`, logo o eixo volta ao neutro sem código novo.
- [x] 4.5 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/di/TransactionsModule.kt`: remover a linha `category = getOrNull()` da definição `viewModel { TransactionsViewModel(...) }`, acompanhando D6.
- [x] 4.6 Em `.../transactions/TransactionsScreen.kt`: ajustar `parametersOf(categoryLabel, target)` (dois argumentos, D6); no `CategoryFilterChip`, resolver o rótulo a partir do valor do eixo — nome da categoria para `Categorized`, `Res.string.category_spending_uncategorized` para `Uncategorized` (D5), e o rótulo neutro quando `null`; manter `FilterChipDefaults.filterChipColors()` sem cor de natureza no estado `Uncategorized` (D4), reservando `IncomeColor`/`ExpenseColor` a `Categorized`; e acrescentar o item "sem categoria" como **último** do `DropdownMenu`, depois das categorias e separado delas (D4), despachando `SelectCategory(SpendingSubject.Uncategorized)`.

## 5. Testes existentes acompanham a remoção do parâmetro morto (D6)

**Para começar:** o grupo 4 concluído — o construtor do `TransactionsViewModel` já não recebe
`category` e o `UiState` já expõe `selectedSubject`, senão os testes nem compilam.
**Ao terminar:** `./gradlew :feature:transactions:impl:jvmTest` compila e passa **sem mudança
de comportamento** nos recortes por categoria — em particular o
`TransactionsViewModelCharacterizationTest`, que é a rede desta renomeação. Cada tarefa toca um
arquivo de teste distinto.

- [x] 5.1 `feature/transactions/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/screen/transactions/TransactionsViewModelCharacterizationTest.kt`: remover `category = null` da construção do ViewModel e adaptar as asserções de `selectedCategory`/`SelectCategory` ao eixo, mantendo os mesmos resultados esperados.
- [x] 5.2 `.../commonTest/.../transactions/TransactionsEmptyStateTest.kt`: remover o parâmetro `category` do helper de construção e trocar `SelectCategory(groceries)` por `SelectCategory(SpendingSubject.Categorized(groceries))`, com `settled`/asserções lendo `selectedSubject`.
- [x] 5.3 `.../commonTest/.../transactions/TransactionScopeTest.kt`: mesma adaptação — construção sem `category` e seleção de categoria expressa como valor do eixo.
- [x] 5.4 `.../commonTest/.../transactions/TransactionScopeYieldTest.kt`: remover `category = null` da construção do ViewModel.
- [x] 5.5 `.../commonTest/.../transactions/TransactionsNatureFilterTest.kt`: remover `category = null` da construção do ViewModel.

## 6. Cobertura do recorte novo

**Para começar:** os grupos 4 e 5 concluídos — a tela já recorta pelo eixo e a suíte do módulo
está verde de novo.
**Ao terminar:** `./gradlew :feature:transactions:impl:jvmTest` passa com os cenários da spec
`uncategorized-transaction-filter` cobertos no nível do ViewModel.

- [x] 6.1 Criar `feature/transactions/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/screen/transactions/TransactionsUncategorizedFilterTest.kt` cobrindo: despesa sem categoria entra; receita sem categoria entra pelo mesmo critério; transferência, pagamento de fatura e ajuste não entram; lançamento categorizado sai; dimensão órfã não é lavada no recorte; composição com o recorte de natureza "despesa"; composição com o escopo "cartões"; o `balanceOverview` permanece o do mês completo daquele escopo ao selecionar o não classificado; `ListState.EmptyScope(canClearFilters = true)` quando o mês não tem lançamento sem categoria; e `ClearFilters` devolvendo `selectedSubject` a `null`.
- [x] 6.2 Confirmar que nenhuma chave nova de string foi criada: `category_spending_uncategorized` já existe em `core/resources/src/commonMain/composeResources/values/strings.xml` e em `values-en/strings.xml`, e é a única usada pelo controle (D5). Verificação de leitura, sem edição de arquivo.

## 7. Verificação final

**Para começar:** os grupos 1 a 6 concluídos.
**Ao terminar:** a suíte completa está verde e o efeito do item novo no fim do menu sobre os
fluxos Maestro está checado — o risco declarado no design.

- [x] 7.1 Rodar `./gradlew jvmTest` e ler a saída; a suíte inteira passa. Relatar qualquer falha com o teste e o arquivo, sem presumir que é pré-existente.
- [ ] 7.2 Rodar a suíte Maestro conferindo o risco "fluxos que abrem o filtro por posição": ler `.maestro/README.md` §2 antes, executar as sete checagens `adb` do dispositivo (AVD `pixel_6` API 36, em inglês, com teclado na tela e sem teclado de hardware), reinstalar o APK debug com `./gradlew :app:android:installDebug` e rodar `maestro test .maestro`. Relatar o resultado por fluxo e em qual dispositivo a execução aconteceu; um item novo no fim do menu pode alterar a rolagem, então qualquer fluxo que toque o filtro de categoria precisa ser confirmado, não presumido.

## 8. As outras quatro superfícies do filtro

**Para começar:** os grupos 1 a 6 concluídos — o eixo, o dono da definição (`matches`) e a
primeira superfície já existem. Este grupo é o resto do trabalho: o filtro de categoria é
oferecido em cinco telas, e um valor que existisse só numa delas leria como "aqui não há nada
sem categoria" nas outras quatro.
**Ao terminar:** `./gradlew jvmTest` sem falha nova, com as cinco superfícies recortando pelo
mesmo critério.

- [x] 8.1 `core/ui` — `TransactionUi.isUncategorized`, preenchido pelo mapper com
  `matches(SpendingSubject.Uncategorized)`. A tela de contas recorta modelo de exibição, e ali
  `categoryId == null` também vale para transferência, pagamento, ajuste e dimensão órfã.
- [x] 8.2 `feature/accounts/impl` — quarteto (filtros, ação, estado, predicado) e chip.
- [x] 8.3 `feature/creditcards/impl` — tela de cartões: quarteto e chip.
- [x] 8.4 `feature/creditcards/impl` — transações de fatura: quarteto e chip.
- [x] 8.5 `feature/creditcards/impl` — parcelamentos: os `MutableStateFlow` do eixo e o chip.
- [x] 8.6 Testes: `TransactionUiUncategorizedTest` (`core/ui`, incluindo a dimensão órfã sobre
  o modelo de exibição), `AccountsUncategorizedFilterTest`, `InstallmentsUncategorizedFilterTest`
  e um caso de fiação em `CreditCardsEmptyStateTest` e `InvoiceTransactionsEmptyStateTest`.
- [x] 8.7 `./gradlew jvmTest` — 1246 testes, 10 falhas, todas as 10 pré-existentes
  (`:app:shared`, testes de arquitetura que enxergam a cópia do repositório em
  `.claude/worktrees/main-limpo`).

## 9. Ajustes pedidos na revisão

**Para começar:** o grupo 8 concluído — as cinco superfícies já oferecem o valor.
**Ao terminar:** `./gradlew jvmTest` sem falha nova; o menu não tem separador e o valor só
aparece quando há o que ele encontre.

- [x] 9.1 Remover o `HorizontalDivider` dos cinco menus (e o import que ele trouxe).
- [x] 9.2 Oferecer o valor apenas quando a lista **já recortada pelos demais controles**
  contém algo sem classificação: `hasUncategorized` no estado das cinco telas, derivado do
  mesmo `matches` (`isUncategorized` na tela de contas, que recorta modelo de exibição).
- [x] 9.3 Manter o valor no menu enquanto for o recorte ativo
  (`mustShowUncategorizedFilter = hasUncategorized || selecionado`), para que um recorte em
  vigor nunca fique sem como ser desfeito.
- [x] 9.4 Testes da regra nova em transações, contas e parcelamentos.
- [x] 9.5 `spec.md` e `design.md` (D4) alinhados: a omissão virou requisito, com os cenários
  do mês sem nada por classificar, do filtro que retira a oferta, do valor selecionado que
  permanece e da dimensão órfã.
