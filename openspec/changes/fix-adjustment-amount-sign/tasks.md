## 1. O tipo de exibição

- [ ] 1.1 Criar `DisplayAmount` em `core/common/.../extension/` (ao lado de `CurrencyFormatter`): `value: Double` + política fechada `MAGNITUDE`, `NATURAL`, `NEUTRAL`, `EXPLICIT_SIGN`, `FORCED_POSITIVE`, `FORCED_NEGATIVE`, `OWED`, com um construtor nomeado por política. Sem operação entre dois valores e sem moeda (design D6); transformar o próprio valor (módulo, negação, limite em zero) é permitido.
- [ ] 1.2 Portar o KDoc por caso do `SignDisplay` (`SummaryCard.kt:426-452`), que é a implementação de referência — em especial o significado de `NEUTRAL` (não move nada nesta perspectiva) e de `OWED` (magnitude devida, zero quando não se deve).
- [ ] 1.3 Adicionar a formatação como extensão sobre `CurrencyFormatter` (`fun CurrencyFormatter.format(amount: DisplayAmount): String`), cobrindo as sete políticas. `FORCED_POSITIVE`/`FORCED_NEGATIVE` aplicam `absoluteValue` — divergência entre os sítios de origem resolvida em 3.3.
- [ ] 1.4 Testar a formatação por política em `core/common`: zero em `EXPLICIT_SIGN` (sem sinal), negativo em `MAGNITUDE` (sai em módulo), positivo em `NATURAL` (sem `+`), saldo credor em `OWED` (zero).

## 2. A correção do defeito (superfície de item)

- [ ] 2.1 Trocar `TransactionUi.amount: Double` por `DisplayAmount`.
- [ ] 2.2 Em `TransactionUiMapper`: remover o `abs()` e resolver a política pela tabela de item do design D5, chaveando em `TransactionLabel` (a natureza derivada pelo razão) — `EXPLICIT_SIGN` para ajuste e para transferência **com** perspectiva; `MAGNITUDE` para transferência **sem** perspectiva, gasto, receita e pagamento.
- [ ] 2.3 No mesmo mapper, substituir a escolha da perna neutra reimplementada por `Transaction.primaryEntry` (`core/ledger`), preservando o caminho com perspectiva (`accountId`) e o retorno `null` quando não há perna correspondente.
- [ ] 2.4 Em `TransactionCard`: apagar o `when` sobre `direction`/`label` e a concatenação manual de `"-"`; renderizar via `formatter.format(transaction.amount)`.
- [ ] 2.5 Em `ReportExportLayout`: apagar `exportAmount` (clone do `when`) e renderizar pela mesma extensão.
- [ ] 2.6 Em `ReportExportLayout.exportTone`: ler o sinal do `value` do `DisplayAmount`, mantendo a ordem dos ramos — o ajuste passa a alcançar `ReportTone.NEGATIVE`, hoje inalcançável.
- [ ] 2.7 Em `ViewTransactionUiState.amount` (`:61`): trocar o `abs()` por `DisplayAmount` pela mesma tabela de item, e renderizar em `ViewTransactionModal.kt:205` pela extensão. A modal é superfície de item ("para cards e modais"); a modal de ajuste (`ViewAdjustmentModal`) já está correta e serve de referência.
- [ ] 2.8 Atualizar `core/ui/.../TransactionPerspectiveTest`: a ponta de saída de uma transferência **com perspectiva** vale `-100.0`; a de entrada, `100.0`. Atualizar o KDoc da classe para nomear o sinal. **Pertence a este grupo**: entre 2.1 e esta tarefa o source set de teste de `:core:ui` não compila.
- [ ] 2.9 Trocar `implementation(projects.core.common)` por `api(...)` em `core/ui/build.gradle.kts`: `TransactionUi.amount` passa a expor um tipo de `:core:common` na API pública de `:core:ui`. Hoje compila por acidente.

## 3. Absorção dos demais sítios (superfície de resumo)

> Os resumos **já obedecem** à regra (design D5), com **uma exceção declarada**: o ramo de fatura do relatório (3.5). Fora dela, esta seção move a decisão para quem produz a figura (design D6) sem mudar comportamento — qualquer diferença visual encontrada é regressão, não melhoria.

- [ ] 3.1 Mover a política do `SummaryCard` para o produtor: `BalanceOverviewFactory.balanceOverview()` passa a devolver `DisplayAmount` em cada campo dos três overviews, e `SummaryCard.kt:134-290` deixa de nomear política. Remover o `enum class SignDisplay` (`:426`) depois de convertidos os 18 usos. Antes de unificar, verificar se algum chamador passa valor negativo a `ALWAYS_POSITIVE`/`ALWAYS_NEGATIVE` — `SummaryCard.kt:447-448` **não** aplica `absoluteValue` e `AccountCard.kt:325-326` aplica; hoje os fluxos chegam como magnitudes positivas (`IEntryRepository.kt:41-56`), então unificar com `absoluteValue` é seguro — confirmar antes de assumir.
- [ ] 3.2 Mover a política do `AccountCard` para `AccountsViewModel.kt:95`, onde `AccountUi` é montado: os campos viram `DisplayAmount` (`openingBalance`/`balance` `NATURAL`, `income` `FORCED_POSITIVE`, `expense`/`settlement` `FORCED_NEGATIVE`, `adjustment` `EXPLICIT_SIGN`). Remover o `private enum class AccountSignDisplay` (`AccountCard.kt:359`); `AccountCard.kt:179-228` só renderiza. Verificar as outras duas telas que renderizam `AccountCard` (`DashboardComponentContent.kt:930`, `ReportConfigScreen.kt:219`) — só o `Variant.Detail` chega às linhas de resumo.
- [ ] 3.3 Mover a política do resumo da fatura para `InvoiceTransactionsViewModel.kt:170-180`, em `InvoiceSummary`: despesas `FORCED_NEGATIVE`, pagamentos antecipados `FORCED_POSITIVE`, ajustes `EXPLICIT_SIGN`, **total `NATURAL`**. O total **não** é `OWED`: `owedByDimension` já devolve positivo-como-dívida (`EntryRepository.kt:106-111`), e `OWED` o zeraria (design D5). O `SummaryRow` (`InvoiceTransactionsScreen.kt:728` — **não** confundir com o homônimo de `SummaryCard.kt:399`) perde `isPositive`/`isNegative`/`showSign` e recebe `DisplayAmount`.
- [ ] 3.4 Mover a política das linhas de resumo do relatório para `ReportViewerUiState.Stats.Account`/`.Invoice`, que passam a expor `DisplayAmount`. Nas linhas de **conta** (`ReportContextCard.kt:165,182` e `ReportExportLayout.kt:73,78`) os literais `"+${...}"`/`"-${...}"` somem sem mudança visual.
- [ ] 3.5 Nas linhas de **fatura** do relatório (`ReportExportLayout.kt:86,96` e `ReportContextCard.kt:199,235`): `FORCED_NEGATIVE` no gasto e `FORCED_POSITIVE` no pagamento antecipado. **Mudança visual 4, declarada** — essas quatro linhas hoje não exibem sinal, ao contrário das linhas de conta do mesmo relatório. A linha "Total" da fatura (`ReportExportLayout.kt:90`, `ReportContextCard.kt:221`) recebe `NATURAL`, pelo mesmo motivo de 3.3.
- [ ] 3.6 Confirmar que não sobrou sítio decidindo sinal fora de quem produz a figura. Buscar `"+${` e `"-${` **e também** chamadas de `formatter.format(` dentro de `@Composable` sobre figuras de resumo — as quatro linhas de 3.5 são invisíveis à primeira busca, e foi por isso que passaram despercebidas. Se voltar algo, é sítio não catalogado e o escopo precisa ser revisto antes de marcar esta tarefa.

## 4. Testes

- [ ] 4.1 Novo teste em `core/ui/commonTest` para o ajuste nas quatro direções: conta ↑/↓ e fatura ↑/↓, afirmando o `value` e a política `EXPLICIT_SIGN`.
- [ ] 4.2 Novo teste de não-regressão da superfície de item, afirmando `value` e política: gasto em conta, gasto em cartão, receita e pagamento de fatura → `MAGNITUDE` com `value` positivo. **Afirmar o número, não o texto.**
- [ ] 4.3 Novo teste da transferência nas duas leituras: com perspectiva → `EXPLICIT_SIGN`, `value = -X` na conta de saída e `+X` na de entrada, **com o texto conferido nas duas** (a entrada deve trazer `+`, que é mudança); sem perspectiva → `MAGNITUDE`.
- [ ] 4.4 Teste do texto por política, cobrindo o par que mais confunde: `EXPLICIT_SIGN` com valor positivo produz `+`; `NATURAL` com valor positivo não produz.
- [ ] 4.5 Teste em `feature/report/impl` para o tom: ajuste com perna negativa → `ReportTone.NEGATIVE` (tornando `exportTone` visível ao teste, ou exercitando-o via `toReportLayout`).
- [ ] 4.6 Teste de ponta a ponta do sinal em `feature/creditcards/impl`: executar `AdjustInvoiceUseCase` contra repositório fake, mapear a transação gravada e afirmar o sinal exibido — é o que impede a convenção do `LedgerEntryWriter` e a da tela de divergirem.
- [ ] 4.7 O mesmo em `feature/accounts/impl` com `AdjustBalanceUseCase`.
- [ ] 4.8 Rodar `./gradlew allTests`.

## 5. Verificação manual

- [ ] 5.1 Cartões / extrato da fatura: ajuste que aumenta e que reduz a dívida; conferir que a lista concorda com a linha "Ajustes" do resumo e com a modal de ajuste.
- [ ] 5.2 Contas: ajuste de saldo para mais e para menos; transferência entre duas contas vista das duas pontas — `−` na origem e `+` na de destino (o `+` é novo); conferir que as linhas de resumo do `AccountCard` não mudaram após 3.2.
- [ ] 5.3 Transações: lista geral — a transferência agora aparece **sem** sinal (mudança esperada); gasto, receita e pagamento idênticos. Conferir os três corpos do `SummaryCard` (contas, cartões, geral) após 3.3, linha a linha. Abrir a modal de visualização de uma transferência e de um gasto após 2.7.
- [ ] 5.4 Dashboard (recentes), parcelamentos, tela de cartões e lista do relatório: **todas** chamam `toTransactionUi()` sem perspectiva, então a transferência perde o `−` nas quatro — só a tela de contas passa `accountId`. Conferir que o resto está idêntico.
- [ ] 5.5 Relatório: tela e arquivo exportado (HTML/PDF) — valor e tom do ajuste; linhas de conta idênticas (3.4); e linhas de fatura com os sinais novos (3.5, mudança visual 3).
- [ ] 5.6 Conferir em pt-BR a posição do sinal negativo agora que ele vem do `NumberFormat` e não de concatenação.

## 6. Fechamento

- [ ] 6.1 Ajustar o KDoc de `Transaction.amount` ("always positive — the sign is a display concern") apontando para o mapper, agora que o valor de exibição carrega sinal.
- [ ] 6.2 Abrir uma proposta OpenSpec separada para a ausência de perspectiva em `InvoiceTransactionsScreen`, `CreditCardsScreen` e `ReportViewerScreen` ao chamar `toTransactionUi()` — sem corrigir aqui. Registrar nela que o ajuste não é afetado, por ter uma única perna monetária, mas que a **transferência** passa a ser, já que a sua política depende da perspectiva (2.2).
- [ ] 6.3 `openspec validate fix-adjustment-amount-sign --strict` e `/opsx:verify`.
