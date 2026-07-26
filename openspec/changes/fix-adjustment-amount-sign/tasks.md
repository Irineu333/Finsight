> **Ordem de execução.** Os grupos abaixo estão na ordem em que devem ser feitos, e o
> agrupamento em commits está indicado. Cada commit compila e passa nos testes — nenhum
> exige exceção. O vale de compilação (entre a troca de tipo e o último consumidor
> convertido) dura 4 arquivos e está inteiro dentro do grupo 2.
>
> **Não use `./gradlew allTests` como laço.** Os alvos KMP incluem iOS, e o link Native
> domina o custo. Use `:<módulo>:jvmTest`, que roda o mesmo `commonTest`. `allTests` é o
> encerramento, uma vez.

## 1. Preparar o terreno — commit `Refactor(Transactions): read the neutral leg from the ledger's own definition`

- [x] 1.1 Em `TransactionUiMapper.kt:33-34`: substituir a escolha da perna neutra reimplementada por `Transaction.primaryEntry` (`core/ledger/.../Transaction.kt:50`), preservando o caminho com perspectiva (`accountId`) e o retorno `null` quando não há perna. É no-op — o predicado é idêntico — e sai da frente do vale de compilação.
- [x] 1.2 Atualizar o KDoc do mapper (`:14-17`) para **apontar ao dono** da perna neutra em vez de repetir o critério.
- [x] 1.3 `./gradlew :core:ui:jvmTest` — `TransactionPerspectiveTest` é a trava desta tarefa.

## 2. O tipo de exibição — commit `Feat(Common): give a displayed amount a sign policy of its own`

- [x] 2.1 Criar `DisplayAmount` em `core/common/.../extension/`: `value: Double` + política fechada `MAGNITUDE`, `NATURAL`, `NEUTRAL`, `EXPLICIT_SIGN`, `FORCED_POSITIVE`, `FORCED_NEGATIVE`, `OWED`, com um construtor nomeado por política. Marcar `@Immutable`. Sem operação entre dois valores e sem moeda (design D7); transformar o próprio valor (módulo, negação, limite em zero) é permitido.
- [x] 2.2 Portar o KDoc por caso do `SignDisplay` (`SummaryCard.kt:426-452`), que é a implementação de referência — em especial `NEUTRAL` (não move nada nesta perspectiva) e `OWED` (magnitude devida, zero quando não se deve).
- [x] 2.3 Adicionar a formatação como **extensão** sobre `CurrencyFormatter` (`fun CurrencyFormatter.format(amount: DisplayAmount): String`) em `commonMain` — **não** como membro do `expect class`, sob pena de três `actual` a mais e de quebra visível só no `iosSimulatorArm64Test`. Semântica fechada aqui, não descoberta na verificação manual: `FORCED_POSITIVE`/`FORCED_NEGATIVE`/`EXPLICIT_SIGN` **concatenam** o sinal sobre `format(value.absoluteValue)`, como os sete sítios fazem hoje; `MAGNITUDE`/`NATURAL`/`NEUTRAL`/`OWED` **delegam** a `format(...)`. Assim a absorção é no-op de texto demonstrável e o risco de locale desaparece.
- [x] 2.4 Testar a formatação por política em `core/common`. **Não asserte literal de moeda** — `NumberFormat.getCurrencyInstance()` usa o locale default da JVM e o teste quebraria em outra máquina. Asserte relações: `"+" + f.format(100.0)` para `EXPLICIT_SIGN`; `f.format(100.0)` para `NATURAL` (sem `+`); `f.format(0.0)` para `EXPLICIT_SIGN` de zero e para `OWED` de saldo credor.
- [x] 2.5 `./gradlew :core:common:jvmTest`.

## 3. A correção do defeito — commit `Fix(Transactions, Report): show an adjustment with the sign the ledger recorded`

> Abre e fecha o vale de compilação. Não é divisível: 3.2 abre, 3.7 fecha.

- [x] 3.1 Criar `itemDisplayAmount(label: TransactionLabel, legAmountCents: Long, hasPerspective: Boolean): DisplayAmount` em `:core:ui`, dona da tabela de item do design D5 — `EXPLICIT_SIGN` para ajuste e para transferência **com** perspectiva; `MAGNITUDE` para transferência **sem** perspectiva, gasto, receita e pagamento. **Sem ela, a modal (3.8) reimplementaria a tabela e a change criaria o oitavo sítio que D8 existe para eliminar.**
- [x] 3.2 Trocar `TransactionUi.amount: Double` por `DisplayAmount`. *(vale abre — 3 arquivos vermelhos)*
- [x] 3.3 Em `TransactionUiMapper`: remover o `abs()` e delegar a `itemDisplayAmount`.
- [x] 3.4 Em `TransactionCard.kt:142-151`: apagar o `when` sobre `direction`/`label` e a concatenação manual de `"-"`; renderizar via `formatter.format(transaction.amount)`.
- [x] 3.5 Atualizar `core/ui/.../TransactionPerspectiveTest:44-46` e o KDoc da classe. *(agora `:core:ui` compila e testa, mesmo com report vermelho)*
- [x] 3.6 **Checkpoint mais valioso do plano**: `./gradlew :core:ui:jvmTest` — `:feature:report:impl` não está no grafo de `:core:ui`, então este verde vale mesmo no meio do vale.
- [x] 3.7 Em `ReportExportLayout.kt:185-207`: apagar `exportAmount` e renderizar pela extensão; em `exportTone`, ler o sinal do `value` — o ajuste passa a alcançar `ReportTone.NEGATIVE`, hoje inalcançável. *(vale fecha)*
- [x] 3.8 Testes desta correção, escritos **entre 3.2 e 3.3** para falharem antes e passarem depois: ajuste nas quatro direções (conta ↑/↓, fatura ↑/↓) → `EXPLICIT_SIGN`; não-regressão por forma (gasto em conta, gasto em cartão, receita, pagamento) → `MAGNITUDE` com `value` positivo; transferência com perspectiva → `EXPLICIT_SIGN`, `-X` na origem e `+X` no destino, **conferindo o texto nas duas** (o `+` é mudança); sem perspectiva → `MAGNITUDE`.
- [x] 3.9 Teste do tom em `feature/report/impl`: ajuste com perna negativa → `ReportTone.NEGATIVE`. `exportTone` e `exportAmount` são `private` — exercitar via `toReportLayout(...)`, que é público e instanciável em `commonTest`.
- [x] 3.10 Trocar `implementation(projects.core.common)` por `api(...)` em `core/ui/build.gradle.kts`: `TransactionUi.amount` passa a expor um tipo de `:core:common` na API pública de `:core:ui`. Hoje compila por acidente, porque cada `feature/*/impl` declara `core:common` por conta própria.
- [x] 3.11 `./gradlew :core:ui:jvmTest :feature:report:impl:jvmTest` e, uma vez, `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` — `:core:common` e `:core:ui` são exportados ao framework Obj-C (`app/ios/build.gradle.kts:23,31`) e `allTests` **não** cobre o link.

## 4. A modal de detalhe — commit `Fix(Transactions): let the detail modal read the adjustment the same way the list does`

> Vale independente do grupo 3, e **obrigatório antes de parar**: sem ele a lista e o detalhe discordam, violando o cenário "Lista e detalhe concordam" da spec.

- [x] 4.1 Em `ViewTransactionUiState.kt:61`: trocar `abs()` por `itemDisplayAmount` (a mesma de 3.1, não uma cópia) e renderizar em `ViewTransactionModal.kt:205` pela extensão.
- [x] 4.2 Atualizar `ViewTransactionViewModelTest.kt:56,68,70` — **não catalogado até aqui**, afirma `assertEquals(100.0, content.amount)`.
- [x] 4.3 `./gradlew :feature:transactions:impl:jvmTest`.

## 5. Travas de ponta a ponta — commit `Test(Accounts, CreditCards): tie the adjustment's recorded sign to the sign it shows`

- [x] 5.1 Em `feature/creditcards/impl`: executar `AdjustInvoiceUseCase` contra repositório fake, mapear a transação gravada e afirmar o sinal exibido. É o que impede a convenção do `LedgerEntryWriter` e a da tela de divergirem.
- [x] 5.2 O mesmo em `feature/accounts/impl` com `AdjustBalanceUseCase`.
- [x] 5.3 `./gradlew :feature:creditcards:impl:jvmTest :feature:accounts:impl:jvmTest`.

## 6. KDoc — commit `Docs(Ledger): point the transaction's amount at the mapper that signs it`

- [x] 6.1 `Transaction.kt:52` ("always positive — the sign is a display concern") passa a apontar ao mapper, agora que o valor de exibição carrega sinal.
- [x] 6.2 `TransactionUi.kt:11-14`, que descreve `amount` implicitamente como módulo.

> **⟵ Ponto de recuo real.** Aqui o defeito está corrigido nas três superfícies de item, `DisplayAmount` existe com dois consumidores, e sete sítios viraram cinco. Estado defensável indefinidamente. Parar antes do grupo 4 **não** é seguro: deixaria lista e detalhe em desacordo.

## 7. Absorção: resumo do mês — commit `Refactor(Transactions): let the summary receive its sign policy already resolved`

> Fechado em `:feature:transactions:impl`. `TransactionScopeTest.kt:136-215` já caracteriza os três corpos (afirma que a coluna fecha); garanta-o verde **antes**, e converta as asserções para `.value` mantendo a mesma aritmética — é a maior massa mecânica do change.

- [x] 7.1 `BalanceOverviewFactory.balanceOverview()` passa a devolver `DisplayAmount` em cada campo dos três overviews; `SummaryCard.kt:134-290` deixa de nomear política.
- [x] 7.2 Remover o `enum class SignDisplay` (`SummaryCard.kt:426`) depois de convertidos os 18 usos. Unificar `FORCED_POSITIVE`/`FORCED_NEGATIVE` com `absoluteValue`: **pré-condição já verificada** — `AccountFlows`, `AssetMonthFlows` e `LiabilityMonthFlows` (`IEntryRepository.kt:15-56`) documentam esses fluxos como magnitudes positivas; só `adjustment` é assinado, e usa `EXPLICIT_SIGN`.
- [x] 7.3 Mover para `BalanceOverviewFactory` os KDoc de `SummaryCard.kt:191-195` e `:262-263`, que explicam a regra e ficariam órfãos.
- [x] 7.4 Atualizar `TransactionScopeTest.kt:145-215` e `TransactionsViewModelCharacterizationTest.kt:98-99`; `BalanceOverviewFactory.kt:78` (`orNullIfZero`) passa a comparar `.value`.
- [x] 7.5 `./gradlew :feature:transactions:impl:jvmTest`.

## 8. Absorção: card de conta — commit `Refactor(Accounts): let the account card receive its sign policy already resolved`

> Único vale do grupo que cruza módulo (`:core:ui` → `:feature:accounts:impl`), de 1 arquivo.

- [x] 8.1 `AccountUi` passa a expor `DisplayAmount`; `AccountsViewModel.kt:95` resolve a política (`openingBalance`/`balance` `NATURAL`, `income` `FORCED_POSITIVE`, `expense`/`settlement` `FORCED_NEGATIVE`, `adjustment` `EXPLICIT_SIGN`).
- [x] 8.2 Remover o `private enum class AccountSignDisplay` (`AccountCard.kt:359`); `AccountCard.kt:179-228` só renderiza. Os gates de visibilidade (`:201`, `:210`) passam a comparar `.value`.
- [x] 8.3 Atualizar `RetireActionTest.kt:22-25` — **não catalogado até aqui**, constrói `AccountUi` com seis `Double`.
- [x] 8.4 Conferir as outras duas telas que renderizam `AccountCard` (`DashboardComponentContent.kt:930`, `ReportConfigScreen.kt:219`): só o `Variant.Detail` chega às linhas de resumo.
- [x] 8.5 `./gradlew :core:ui:jvmTest :feature:accounts:impl:jvmTest`.

## 9. Absorção: resumo da fatura — commit `Refactor(CreditCards): let the invoice summary receive its sign policy already resolved`

> O menor em volume e o mais perigoso: contém a armadilha do `total`.

- [x] 9.1 `InvoiceTransactionsViewModel.kt:170-180` resolve a política em `InvoiceSummary`: despesas `FORCED_NEGATIVE`, pagamentos antecipados `FORCED_POSITIVE`, ajustes `EXPLICIT_SIGN`, **total `NATURAL`**. O total **não** é `OWED`: `owedByDimension` já devolve positivo-como-dívida (`EntryRepository.kt:106-111`), e `OWED` (`max(0, −valor)`) o zeraria.
- [x] 9.2 Documentar no KDoc de `InvoiceSummary.total` **por que** é `NATURAL` — a inversão já foi feita a montante —, porque `summary.total` alimenta `currentBillAmount` em `InvoiceTransactionsScreen.kt:588` e `:703`, que é **pré-preenchimento de formulário**, não texto: sob `OWED` o modal de pagamento abriria zerado.
- [x] 9.3 `SummaryRow` (`InvoiceTransactionsScreen.kt:728` — **não** confundir com o homônimo de `SummaryCard.kt:399`) perde `isPositive`/`isNegative`/`showSign` e recebe `DisplayAmount`. `InvoiceTransactionsUiState.kt:84` (`mustShowAdjustment`) passa a comparar `.value`.
- [x] 9.4 Antes de editar, acrescentar a `InvoiceTransactionsViewModelCharacterizationTest` (`:112`, `:115`) a asserção de que `summary.total` é positivo-como-dívida — trava contra alguém "corrigir" `NATURAL` para `OWED` depois.
- [x] 9.5 `./gradlew :feature:creditcards:impl:jvmTest`.

## 10. Absorção: relatório — commit `Fix(Report): sign the invoice lines like the account lines of the same report`

> Único commit da absorção com mudança visual. Revertível isoladamente se a verificação 11.5 reprovar.

- [x] 10.1 `ReportViewerUiState.Stats.Account`/`.Invoice` passam a expor `DisplayAmount`; `ReportViewerViewModel` resolve a política.
- [x] 10.2 Linhas de **conta** (`ReportContextCard.kt:165,182` e `ReportExportLayout.kt:73,78`): os literais `"+${...}"`/`"-${...}"` somem, sem mudança visual.
- [x] 10.3 Linhas de **fatura** (`ReportExportLayout.kt:86,96` e `ReportContextCard.kt:199,235`): `FORCED_NEGATIVE` no gasto e `FORCED_POSITIVE` no pagamento antecipado. **Mudança visual 4, declarada** — hoje não exibem sinal, ao contrário das linhas de conta do mesmo relatório. O "Total" da fatura (`ReportExportLayout.kt:90`, `ReportContextCard.kt:221`) recebe `NATURAL`, pelo mesmo motivo de 9.1.
- [x] 10.4 As comparações de sinal que decidem cor (`ReportContextCard.kt:129,148,221`) passam a ler `.value`.
- [x] 10.5 Atualizar `ReportViewerViewModelCharacterizationTest.kt:126-129,204-207`.
- [x] 10.6 `./gradlew :feature:report:impl:jvmTest`.

## 11. Fechamento — commit `Chore(Transactions): delete the sign enums the display type replaced`

- [x] 11.1 Varredura final: buscar `"+${` e `"-${` **e também** chamadas de `formatter.format(` dentro de `@Composable` sobre figuras de resumo — as quatro linhas de 10.3 são invisíveis à primeira busca, e foi por isso que passaram despercebidas. Se voltar algo, é sítio não catalogado e o escopo precisa ser revisto.
- [x] 11.2 Remover imports órfãos (não há detekt/ktlint no projeto, então são apenas *warnings* do kotlinc): `kotlin.math.abs` em `TransactionUiMapper.kt:7` e `ViewTransactionUiState.kt:16`; `kotlin.math.absoluteValue` em `AccountCard.kt:38` e em `InvoiceTransactionsScreen.kt`; `TransactionType` onde os `when` sumiram.
- [x] 11.3 `./gradlew :app:shared:compileKotlinJvm` — primeira vez que todos os `impl` compilam juntos.
- [x] 11.4 `./gradlew allTests` e `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`.

## 12. Verificação manual

> Não há CI de teste (`.github/workflows/` só empacota o instalador Windows por tag) nem infraestrutura de teste de Compose. Todo o gate é local, o que dá peso extra a esta seção.

- [ ] 12.1 Cartões / extrato da fatura: ajuste que aumenta e que reduz a dívida; conferir que a lista concorda com a linha "Ajustes" do resumo e com a modal de ajuste. Abrir o modal de pagamento e conferir que o valor vem pré-preenchido (armadilha de 9.2).
- [ ] 12.2 Contas: ajuste de saldo para mais e para menos; transferência vista das duas pontas — `−` na origem e `+` no destino (o `+` é novo); linhas de resumo do `AccountCard` idênticas.
- [ ] 12.3 Transações: lista geral — transferência **sem** sinal (mudança 3); gasto, receita e pagamento idênticos; os três corpos do `SummaryCard` linha a linha. Modal de visualização de uma transferência e de um gasto.
- [ ] 12.4 Dashboard (recentes), parcelamentos, tela de cartões e lista do relatório: **todas** chamam `toTransactionUi()` sem perspectiva, então a transferência perde o `−` nas quatro — só a tela de contas passa `accountId`.
- [ ] 12.5 Relatório: tela e arquivo exportado (HTML/PDF) — valor e tom do ajuste; linhas de conta idênticas; linhas de fatura com os sinais novos (mudança 4).
- [ ] 12.6 Rodar em Desktop (`./gradlew :app:desktop:run`) e Android (`./gradlew :app:android:assembleDebug`).

## 13. Encerramento

- [x] 13.1 Abrir uma proposta OpenSpec separada para a ausência de perspectiva em `InvoiceTransactionsScreen`, `CreditCardsScreen` e `ReportViewerScreen` ao chamar `toTransactionUi()`. Registrar nela que o ajuste não é afetado, por ter uma única perna monetária, mas que a **transferência** passa a ser, já que a sua política depende da perspectiva.
- [ ] 13.2 `openspec validate fix-adjustment-amount-sign --strict` e `/opsx:verify`.
