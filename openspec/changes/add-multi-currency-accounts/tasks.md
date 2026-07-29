> **Ordem deliberada, e a razão dela é de segurança, não de conveniência.** Enquanto nenhuma
> conta puder ser criada em outra moeda, **nenhum dado errado é produzível** — as agregações
> continuam somando uma moeda só, e todo número permanece correto. Por isso o seletor de moeda
> (§9) e os fluxos de dois valores (§10) vêm **por último**, depois de o razão, as leituras, a
> consolidação e a exibição já saberem lidar com a segunda moeda. Cada grupo até §8 deixa o app
> num estado entregável e indistinguível do atual.
>
> Grupos §1–§3 tocam o razão; §4 é a única alteração de schema; §5–§6 são a camada nova; §7–§8
> varrem a superfície; §9–§10 abrem a porta. §11 fecha.

## 1. O tipo de conta de conversão (D2)

- [ ] 1.1 `AccountType`: acrescentar `CONVERSION`. Definir as quatro propriedades derivadas — `isDebitNatured = false` (natureza credora, como GnuCash: débito decresce), `isMonetary = false`, `isNominal = false`, `isPermanent = true` **com KDoc registrando que é vacuamente verdadeiro** (a propriedade decide se arquivar encalha saldo, e uma conta de conversão nunca é arquivada).
- [ ] 1.2 `AccountEntity.Type`: acrescentar `CONVERSION`. KDoc registrando o fato verificado de que **não há migração**: Room persiste este enum nativamente como `TEXT`, sem `TypeConverter`, e um membro novo não altera o schema.
- [ ] 1.3 `AccountTypeMapper`: os dois `when` exaustivos (linhas 13 e 21) ganham o novo membro. São, junto com 1.4, os **três únicos** `when` exaustivos sobre `AccountType` do repositório.
- [ ] 1.4 `AccountType.displaySign` (`Ledger.kt:20`): `CONVERSION` é credora → `-1`.
- [ ] 1.5 `ClosedFacade.of`: decidir explicitamente e documentar que `CONVERSION` recai em `ACCOUNT`, e que isso é inalcançável — nenhuma conta de conversão é arquivada.
- [ ] 1.6 `SystemAccount`: acrescentar a chave `CONVERSION`. KDoc registrando que ela é criada exclusivamente pela fronteira de escrita e não é postável à mão.
- [ ] 1.7 `LedgerTest`: acrescentar os casos `{ASSET, CONVERSION} → TRANSFER` e `{ASSET, LIABILITY, CONVERSION} → PAYMENT` em `deriveTransactionLabel`, e o caso de `deriveTransactionType` de uma perna monetária de operação cruzada **não** ler `ADJUSTMENT`. São a prova de D2 e devem existir antes de o escriturador produzir a primeira perna de conversão.

## 2. A fronteira de escrita (D1, D5, D6, D7, D15)

- [ ] 2.1 **Remover** `LedgerEntryWriter.validate(legs)` e as suas duas chamadas em `TransactionRepository:219` e `:240` (D7). Remover/realocar os testes de `validate()` em `LedgerEntryWriterTest`.
- [ ] 2.2 `AccountDao.getByTypeAndName`: acrescentar a moeda à chave, virando `getByTypeNameAndCurrency(type, name, currency)`.
- [ ] 2.3 `ensureSystemAccount`: resolver e criar por `(type, name, currency)`, gravando a moeda pedida em vez de `BASE_CURRENCY`.
- [ ] 2.4 `systemAccountId`: deixar de resolver por natureza — `EQUITY` passa a ter **duas** contas de sistema por moeda (reconciliação e, sob o tipo novo, conversão), então a natureza deixou de ser chave. Resolver por `(SystemAccount, currency)`.
- [ ] 2.5 `writeEntries`: gravar `currency` lida da `AccountEntity` de cada perna, que `orRejectIfClosed` já carrega, em vez de `BASE_CURRENCY` (D5). Reaproveitar a conta já carregada — sem segunda leitura.
- [ ] 2.6 `writeEntries`: a contrapartida de uma intenção de perna única usa a conta de sistema **da moeda da perna**.
- [ ] 2.7 `writeEntries`: agrupar por moeda e calcular o resíduo de cada grupo. **Uma moeda** → resíduo tem de ser zero, senão recusa (comportamento atual, intacto). **Duas ou mais** → lançar o oposto do resíduo de cada moeda na conta de conversão daquela moeda.
- [ ] 2.8 A perna de conversão é a **última calculada e recebe o resíduo por diferença** (D6) — nunca calculada de forma independente e comparada. Comentar por quê: ela concentra, por construção, todo o erro de arredondamento do câmbio.
- [ ] 2.9 A perna de conversão é gravada **sem dimensão** (D15). Sem isso `rejectIfDimensionLandsWrong` recusa todo pagamento de fatura cruzado, porque a dimensão da fatura só pousa em `LIABILITY`.
- [ ] 2.10 Guarda de D1: com duas ou mais moedas, os resíduos MUST NOT ser todos do mesmo sinal. Novo caso em `LedgerError` + o erro tipado correspondente.
- [ ] 2.11 `LedgerFixture` (`core/ledger/jvmTest`): parâmetro de moeda na criação de conta e de entry. **Sem isto nenhum teste cruzado é escrevível**, então vem antes de 2.12.
- [ ] 2.12 Testes de escrita: transferência cruzada gravando quatro entries somando zero em cada moeda; pagamento de fatura cruzado idem, com as pernas de conversão sem dimensão; operação monomoeda inalterada, sem perna de conversão; desbalanceamento monomoeda ainda recusado; resíduos de mesmo sinal recusados; resíduo de arredondamento absorvido pela perna de conversão.

## 3. As leituras por moeda (D8)

- [ ] 3.1 `EntryDao`: acrescentar `GROUP BY e.currency` às agregações que atravessam contas — `balanceUpToMonthByType`, `assetMonthTotals`, `liabilityMonthTotals`, `netWorthCents`, `scopeStats`, `dimensionBalanceInMonth`, `totalsByDimensionWithSiblingLeg`, `totalsByDimensionInScope`, `naturalBalanceByDimension`, `periodTotalsByDimension`. Tipos de retorno ganham a moeda.
- [ ] 3.2 `EntryDao`: as agregações escopadas a **uma** conta (`balanceOf`, `balanceUpToMonth`, `accountPeriodTotals`) mantêm a forma; quem as consome anexa a moeda da conta.
- [ ] 3.3 `IEntryRepository`: expressar por moeda `netWorth`, `naturalBalanceUpTo`, `balanceUpTo` (quando `accountId == null`), `dimensionBalanceInMonth`, `totalsByDimension`, `totalsByDimensionInScope`, `dimensionBalancesInMonth`, `owedByDimension`, `flowsByDimension`, `LiabilityMonthFlows`, `AssetMonthFlows`, `ScopeStats`.
- [ ] 3.4 `IEntryRepository`: **manter a forma** de `AccountFlows` e `DimensionFlows`, acrescentando só a moeda — monomoeda por escopo. KDoc registrando que, no caso da fatura, isso é garantia da **fachada de cartão** e não construção do razão, e que o razão MUST NOT consultar `DimensionKind` na leitura para decidir forma de retorno.
- [ ] 3.5 `IEntryRepository`: acrescentar a operação de **soma de dois resultados por moeda**, dona única da aritmética que o widget de perímetro neutro e o `BalanceOverviewFactory` exigem (`ledger-reporting`). KDoc registrando que não é conversão e não pertence à consolidação.
- [ ] 3.6 Atualizar os ~25 arquivos de teste que implementam `IEntryRepository` como stub completo. Considerar extrair um stub base compartilhado antes de tocar os 25 — hoje cada um redeclara a interface inteira.
- [ ] 3.7 Atualizar as 6 suítes de query do razão construídas sobre `LedgerFixture`, acrescentando ao menos um caso multimoeda em cada agregação de 3.1.

## 4. A tabela de taxas e a migração (D11)

- [ ] 4.1 `ExchangeRateEntity` em `:core:database`: `(moeda, data, taxa, origem)`, com a origem distinguindo colhida-de-operação de informada-pelo-usuário. Índice por `(moeda, data)`.
- [ ] 4.2 `ExchangeRateDao`: a consulta "última taxa em ou antes da data" para uma moeda; a listagem por moeda; o upsert com a regra de que a informada pelo usuário prevalece sobre a derivada na mesma data.
- [ ] 4.3 `AppDatabase`: `version = 10` → `11`, com a entidade registrada.
- [ ] 4.4 `MIGRATION_10_11` em `Database.kt`: apenas `CREATE TABLE`. Nenhuma tabela existente é alterada e nenhum dado migra.
- [ ] 4.5 Exportar `schemas/com.neoutils.finsight.database.AppDatabase/11.json`.
- [ ] 4.6 Estender `MigrationSchemaEquivalenceTest`, hoje cobrindo apenas `7 → 10`, para cobrir `10 → 11`.
- [ ] 4.7 Verificar que `LedgerBalanceCheck` **não precisa de alteração** — já agrupa por `(transactionId, currency)` — e cobrir com um teste de banco contendo transação cruzada.

## 5. A colheita da taxa (D11)

- [ ] 5.1 Ao persistir uma operação que atravessa moedas, registrar a taxa derivada das duas pontas, na data da operação, com origem de operação. A escrita da taxa acontece **fora** do razão, consumindo a operação gravada — o razão continua sem campo de taxa (`balanced-ledger`).
- [ ] 5.2 Decidir e documentar o que acontece com a taxa colhida quando a operação que a produziu é removida. Registrado na change como fora de escopo se a decisão for não fazer nada — mas a decisão precisa ser explícita.
- [ ] 5.3 Teste: uma transferência de R$ 550 → US$ 100 em 12/07 registra a taxa 5,50 para USD naquela data, com origem de operação; uma taxa do usuário para a mesma data prevalece.

## 6. A camada de consolidação (D8, D9, D11)

- [ ] 6.1 A moeda base em `Settings`, exposta como **fluxo observável** — toda figura consolidada reage à sua mudança. Molde: `DashboardPreferencesRepository`.
- [ ] 6.2 `EnsureDefaultAccountUseCase` (`feature/accounts/api`): semear a moeda base junto da criação da primeira conta do app. É hoje o único ponto que cria conta sem moeda explícita.
- [ ] 6.3 O catálogo curado de moedas oferecidas, restrito às de **duas casas decimais** (D14), em `:core:model`. KDoc registrando a premissa e o que ela custaria mudar.
- [ ] 6.4 O redutor de saldo-por-moeda a **lista de termos** (D9): um termo na base com tudo o que a taxa permitiu converter, e um termo próprio por moeda sem taxa. Dono único, consumido por toda feature que exiba figura consolidada.
- [ ] 6.5 O redutor produz a **exatidão** junto de cada figura, derivada conforme a tabela de D9, e é impossível obter uma figura sem ela. Um resultado que não exigiu conversão sai como exato.
- [ ] 6.6 A conversão usa **a última taxa em ou antes da data** da figura, e não a corrente. Uma figura de período passado não muda quando uma taxa mais recente é cadastrada.
- [ ] 6.7 Testes do redutor cobrindo as seis linhas da tabela de D9, incluindo `{BRL:100, USD:50}` sem taxa de USD → dois termos, e `{USD:50}` sem taxa → um termo em dólar, **exato**.

## 7. A exibição (D7, D10, D21, D22)

- [ ] 7.1 `DisplayAmount`: acrescentar moeda e exatidão, indissociáveis do valor e da política. Atualizar `DisplayAmountTest`.
- [ ] 7.2 `CurrencyFormatter`: passar a receber a moeda a formatar, nos três `actual` (jvm, android, ios). O locale continua governando **formato** — separador e posição do símbolo —, nunca a moeda. Decidir se o parâmetro migra para o método ou muda o binding Koin (`CommonModule:10`).
- [ ] 7.3 `CurrencyFormatter.format(DisplayAmount)`: o `≈` como prefixo textual, sempre **mais externo** que o sinal (D21). Testes de `≈ +R$`, `≈ -R$`, `≈ R$`.
- [ ] 7.4 O tipo de figura **multitermo** e a sua renderização empilhada — primeiro termo no estilo da superfície, demais um degrau abaixo em `onSurfaceVariant` (D22). Um componente, consumido por todas as superfícies que comportem mais de um termo.
- [ ] 7.5 O **rodapé de card** (D21): linha de 13sp `onSurfaceVariant` renderizada só quando alguma linha do card é aproximada, explicando a marca e navegando para a tela de taxas. Molde de renderização condicional: `AccountCard:199-213`.
- [ ] 7.6 `AccountUi` e `TransactionUi` (`core/ui`) passam a carregar a moeda — hoje não a têm, e sem ela o formatador não a recebe.
- [ ] 7.7 `MoneyInputTransformation`: exibir o símbolo da moeda da conta escolhida (D10), incluindo a troca do símbolo quando a conta muda **com o campo já preenchido** — caso real em `TransferBetweenAccountsModal` e `ConfirmRecurringModal`.
- [ ] 7.8 Varrer os ~105 sítios de formatação nos 10 módulos, passando a formatar a partir do `DisplayAmount` completo. Eliminar as duplicatas locais que contornam o tipo: `formatMoney`/`parseMoneyToDouble` em `EditInvoiceBalanceModal`, `EditAccountBalanceModal` e `AdvancePaymentModal` — a primeira prepende `"-"` à mão, reimplementando a política de sinal fora do tipo.
- [ ] 7.9 `BalanceCard`: a API pública recebe `balance: Double` cru; passar a receber a figura completa.
- [ ] 7.10 Degradação declarada (D20) em `BudgetProgressCard`, `CategorySpendingCard` e no documento exportado: só o termo na base, com a marca, mais a indicação da parcela não convertida. `InstallmentCounter` e o medidor `limite − devido` **não mudam** — são monomoeda garantidos por D17.
- [ ] 7.11 Modelos do relatório exportado (`ReportLayout`, `ReportSummaryItem`, `CategoryItem`, `TransactionItem`): hoje guardam string já formatada e recebem um único formatador, o que os torna incapazes de representar figura aproximada ou multitermo. Acrescentar a marca e a nota de rodapé por família de figura.
- [ ] 7.12 Atualizar `TransactionItemSignTest`, `TransactionPerspectiveTest` e `TransactionFormCoherenceTest`.

## 8. Os consumidores que somam entre contas

- [ ] 8.1 `Transaction.primaryEntry` e `Ledger.sourceLeg()`: trocar `minByOrNull { it.amount }` pela **perna monetária de valor negativo** (D16). Hoje comparam `Long` de moedas diferentes. Cobrir com teste de transferência cruzada.
- [ ] 8.2 `CalculateBalanceUseCase` (`feature/transactions/api`): o `accountId` com default `null` é a porta pela qual o `balanceUpTo` multimoeda entra no app.
- [ ] 8.3 `DashboardComponentsBuilder`: o saldo total (`accountId = null`) e a soma `assetMonthFlows + liabilityMonthFlows`, passando a consumir a operação de soma de 3.5.
- [ ] 8.4 `BalanceOverviewFactory`: as somas `ASSET + LIABILITY`, `asset.expense + liability.expense` e `asset.adjustment + liability.adjustment`, idem.
- [ ] 8.5 `CalculateBudgetProgressUseCase` (`feature/budgets/api`): progresso aproximado sobre limite na moeda base (D13). Documentar a consequência aceita — o progresso pode se mover por variação de taxa, sem gasto novo.
- [ ] 8.6 `CalculateCategorySpendingUseCaseImpl` e `ViewCategoryViewModel` (`feature/categories/impl`): total da categoria e o denominador de porcentagem, ambos multimoeda por natureza.
- [ ] 8.7 `CalculateReportStatsUseCase`, `CalculateReportCategorySpendingUseCase`, `ReportViewerViewModel` (`feature/report/impl`). Atenção: escopo vazio significa **todas as contas, inclusive arquivadas** — é a figura mais cruzada do app.
- [ ] 8.8 `CalculateInvoiceOverviewsUseCase` e `InvoiceTransactionsViewModel`: somas entre faturas de cartões possivelmente de moedas distintas.
- [ ] 8.9 `AccountsViewModel` e `EditAccountBalanceViewModel`: leituras monomoeda, que só precisam anexar a moeda da conta.
- [ ] 8.10 Verificar que `AdjustBalanceUseCase` e `AdjustInvoiceUseCase` **não precisam de alteração** — a idempotência por perna `EQUITY` continua correta porque conversão não é `EQUITY` — e cobrir com um teste que registre uma transferência cruzada e um ajuste na mesma conta e data, provando que o ajuste não a captura.

## 9. A feature de configurações (D18, D25)

- [ ] 9.1 `feature/settings/api`: rota `@Serializable`, `SettingsGraph`/`SettingsRoute`, o entry point.
- [ ] 9.2 `feature/settings/impl`: `NavGraphBuilder.settingsGraph()`, módulo Koin, registro em `appModules` e no `AppNavHost`.
- [ ] 9.3 Registrar o destino no `AppNavCatalog`, imediatamente **antes** de `Support` — cujo KDoc registra ser o último de propósito.
- [ ] 9.4 Tela de configurações: linha de moeda base (não editável na v1, com o mesmo tratamento visual da moeda travada) e linha de taxas.
- [ ] 9.5 Tela de taxas: uma linha por moeda não-base em uso, com valor, data e **origem** — ícone de 16dp + `labelSmall`, no formato de procedência do `CategoryCard:58-75`, usando `SwapHoriz` para a colhida e `ModeEdit` para a digitada.
- [ ] 9.6 Sinalização de taxa **desatualizada aos 30 dias**: cor `Warning` **e a palavra**, nunca cor sozinha. A data aparece sempre.
- [ ] 9.7 Modal de edição de taxa: campo numérico, campo de data via `DatePickerModal`, e a sugestão externa como **placeholder** — o único ponto do app onde rede é permitida, e sem estado de carregamento que bloqueie a modal.
- [ ] 9.8 Strings em `core/resources`, com paridade `values` / `values-en`.

## 10. A segunda moeda de fato (D12, D23, D24, D26)

> A partir daqui uma segunda moeda passa a ser criável. Nada neste grupo deve
> entrar antes de §1–§8 estarem completos.

- [ ] 10.1 `CurrencyPickerModal` em `core/designsystem`, irmão do `IconPickerModal`.
- [ ] 10.2 O seletor de moeda no `AccountFormModal`, reutilizando a estrutura e a mecânica de travamento do `DefaultAccountSelector` (`AccountFormModal:207-290`): símbolo como glifo na caixa de 52dp, subtítulos alternativos, caixa de `primary` → `onSurfaceVariant` e chevron ausente quando travada. **Sempre renderizado** (D23).
- [ ] 10.3 O mesmo seletor no `CreditCardFormModal`.
- [ ] 10.4 A regra de travamento consumindo `hasEntries(accountId)` — a mesma implementação que já decide apagar-vs-arquivar. A tela consulta o que o domínio consultaria; nunca oferece o que seria recusado.
- [ ] 10.5 `CreditCardRepository:57`: gravar a moeda escolhida na conta `LIABILITY` do cartão, em vez de `BASE_CURRENCY` fixo.
- [ ] 10.6 `AccountSelector` e `CreditCardSelector`: sufixo `· US$` no nome quando houver mais de uma moeda no app — sufixo de texto, não chip, porque ali não há número que porte o símbolo.
- [ ] 10.7 **Reordenar** `PayInvoiceModal` e `AdvancePaymentModal` para pedir a conta antes do valor, alinhando os três fluxos à gramática do `TransferBetweenAccountsModal` (D24). Muda o caso comum de todo usuário.
- [ ] 10.8 `TransferBetweenAccountsUseCase`: aceitar o valor de destino quando as moedas divergem. `UiState` ganha os valores (hoje o valor vive só no `TextFieldState` do composable), `Action.Submit` ganha o segundo valor, e `isValidTransfer` passa a validar os dois.
- [ ] 10.9 `PayInvoicePaymentUseCase`: acrescentar o valor de entrada que **hoje não existe** — o campo somente-leitura que mostra a dívida permanece com o seu papel, e o editável é novo, abaixo dele. `PayInvoiceAction.Submit` e o `UiState` ganham o valor.
- [ ] 10.10 `AdvanceInvoicePaymentUseCase`: o par de valores, com o teto `amount <= currentBillAmount` passando a valer sobre o campo **na moeda do cartão**. Espelhar em `AdvancePaymentModal:197-208`.
- [ ] 10.11 Nos três modais: segundo campo revelado por `AnimatedVisibility` quando as moedas divergem (molde: `ConfirmRecurringModal:123-178`); rótulos nomeando a conta (*"Sai de Nubank"* / *"Entra em Chase"*); taxa derivada como `supportingText` do segundo campo.
- [ ] 10.12 Pré-preenchimento do segundo valor **apenas** quando a taxa conhecida é do mesmo dia. Fora disso, o valor implícito vai para o placeholder e a data para o `supportingText`. Sem essa regra, a taxa velha é gravada como nova, em laço (§5).
- [ ] 10.13 Estender a guarda de `enabled` dos três botões para cobrir o **segundo** campo — é o que torna a guarda de resíduos de mesmo sinal inalcançável pela UI (D26), e a alteração de validação mais fácil de esquecer.
- [ ] 10.14 `ConfirmRecurringUseCase`: recusar redirecionar para conta ou cartão de outra moeda, com erro tipado (D17).
- [ ] 10.15 `ConfirmRecurringModal`: filtrar o `AccountSelector`/`CreditCardSelector` para a moeda da recorrência, e exibir por que a lista encolheu. A recusa do domínio permanece como rede, nunca como caminho projetado.
- [ ] 10.16 Strings de todos os estados novos, com paridade `values` / `values-en`.

## 11. Fechamento

- [ ] 11.1 Testes de ponta a ponta do caminho cruzado: criar conta em USD, transferir de BRL, verificar as quatro entries, o rótulo `TRANSFER`, a taxa colhida, o patrimônio consolidado aproximado e o saldo de cada conta exato.
- [ ] 11.2 Teste de regressão do usuário monomoeda: com todas as contas na moeda base, **nenhuma** figura do app recebe marca de aproximação e todo número é idêntico ao de antes da mudança.
- [ ] 11.3 `./gradlew allTests` verde nas plataformas.
- [ ] 11.4 Atualizar `core/ledger/README.md` — a referência normativa do módulo — com a conta de conversão, a moeda da perna vinda da conta, e as leituras por moeda.
- [ ] 11.5 Atualizar `CLAUDE.md` na seção do razão: o conjunto de tipos de conta deixa de ser de cinco membros, e a lista de contas de sistema deixa de ser "apenas três".
- [ ] 11.6 Revisar os Non-Goals registrados e confirmar que nenhum foi implementado por acidente: edição de transação cruzada, taxa entre duas moedas não-base, troca de moeda base, ganho cambial exposto em tela, moeda de expoente ≠ 2.
