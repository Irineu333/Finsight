## 1. O tipo de exibição

- [ ] 1.1 Criar `DisplayAmount` em `core/common/.../extension/` (ao lado de `CurrencyFormatter`): `value: Double` + política fechada `MAGNITUDE`, `NATURAL`, `EXPLICIT_SIGN`, `FORCED_POSITIVE`, `FORCED_NEGATIVE`, com um construtor nomeado por política (`DisplayAmount.magnitude(v)`, `.natural(v)`, `.signed(v)`, `.forcedPositive(v)`, `.forcedNegative(v)`). Sem aritmética, sem moeda (design D4).
- [ ] 1.2 Adicionar a formatação como extensão sobre `CurrencyFormatter` (`fun CurrencyFormatter.format(amount: DisplayAmount): String`), cobrindo as cinco políticas: `MAGNITUDE` → `format(abs)`, `NATURAL` → `format(value)`, `EXPLICIT_SIGN` → `formatWithSign(value)`, `FORCED_POSITIVE`/`FORCED_NEGATIVE` → sinal + `format(abs)`.
- [ ] 1.3 Testar a formatação por política em `core/common`, incluindo o zero em `EXPLICIT_SIGN` (sem sinal) e o negativo em `MAGNITUDE` (sai em módulo).

## 2. A correção do defeito

- [ ] 2.1 Trocar `TransactionUi.amount: Double` por `DisplayAmount`.
- [ ] 2.2 Em `TransactionUiMapper`: remover o `abs()` e resolver a política por forma do razão conforme a tabela do design D5 — `EXPLICIT_SIGN` para ajuste, `NATURAL` para transferência, `MAGNITUDE` para despesa, receita e pagamento.
- [ ] 2.3 No mesmo mapper, substituir a escolha da perna neutra reimplementada por `Transaction.primaryEntry` (`core/ledger`), preservando o caminho com perspectiva (`accountId`) e o retorno `null` quando não há perna correspondente.
- [ ] 2.4 Em `TransactionCard`: apagar o `when` sobre `direction`/`label` e a concatenação manual de `"-"`; renderizar via `formatter.format(transaction.amount)`.
- [ ] 2.5 Em `ReportExportLayout`: apagar `exportAmount` (clone do `when`) e renderizar pela mesma extensão.
- [ ] 2.6 Em `ReportExportLayout.exportTone`: ajustar a leitura do sinal para o `value` do `DisplayAmount`, mantendo a ordem dos ramos — o ajuste passa a alcançar `ReportTone.NEGATIVE`, hoje inalcançável.
- [ ] 2.7 Atualizar `core/ui/.../TransactionPerspectiveTest`: a ponta de saída de uma transferência passa a valer `-100.0`; a de entrada segue `100.0`. Atualizar o KDoc da classe para nomear o sinal. **Pertence a este grupo**: entre 2.1 e esta tarefa o source set de teste de `:core:ui` não compila.
- [ ] 2.8 Trocar `implementation(projects.core.common)` por `api(...)` em `core/ui/build.gradle.kts`: `TransactionUi.amount` passa a expor um tipo de `:core:common` na API pública de `:core:ui`. Hoje compila por acidente, porque cada `feature/*/impl` declara `core:common` por conta própria.

## 3. Absorção das reimplementações existentes

- [ ] 3.1 Em `AccountCard`: remover o `private enum class AccountSignDisplay` e mapear seus quatro casos 1:1 para as políticas de `DisplayAmount` (`ALWAYS_POSITIVE`→`FORCED_POSITIVE`, `ALWAYS_NEGATIVE`→`FORCED_NEGATIVE`, `SHOW_ONLY_NEGATIVE`→`NATURAL`, `SHOW_ALWAYS`→`EXPLICIT_SIGN`), sem mudança de comportamento nas seis linhas que o usam (`AccountCard.kt:179,187,194,202,211,221`); a única tela afetada é a de contas, via `Variant.Detail`.
- [ ] 3.2 Em `InvoiceTransactionsScreen.SummaryRow` (`InvoiceTransactionsScreen.kt:728` — **não** confundir com o `SummaryRow` homônimo de `SummaryCard.kt:399`, que é outro sistema): remover os parâmetros `isPositive`/`isNegative`/`showSign` e receber `DisplayAmount`, atualizando as quatro linhas do resumo (despesas, pagamentos antecipados, ajustes, total) para a política equivalente à de hoje.
- [ ] 3.3 Em `SummaryCard.kt`: absorver o `enum class SignDisplay` (18 usos no arquivo). `ALWAYS_POSITIVE`/`ALWAYS_NEGATIVE` **não** aplicam `absoluteValue` ali, ao contrário de `AccountCard.kt:325-326` — decidir caso a caso se algum chamador passa valor negativo antes de unificar; se passar, é mudança de comportamento e não conversão mecânica. `NONE` é idêntico a `SHOW_ONLY_NEGATIVE` no comportamento (`SummaryCard.kt:450`) e some. `OWED` (`maxOf(0.0, -amount)`) é derivação, não formatação, e MUST NOT entrar em `DisplayAmount` (design D4): resolver a figura antes, em quem a produz.
- [ ] 3.4 Em `ReportContextCard.kt:165,182` e `ReportExportLayout.kt:73,78`: trocar os literais `"+${...}"`/`"-${...}"` das linhas de resumo por `DisplayAmount`.
- [ ] 3.5 Confirmar por busca que não sobrou nenhum sítio compondo sinal à mão (`"+${`, `"-${`, `absoluteValue` junto de `format`) fora de `DisplayAmount`. A busca deve voltar vazia; se voltar algo, é sítio não catalogado e o escopo precisa ser revisto antes de marcar esta tarefa.

## 4. Testes

- [ ] 4.1 Novo teste em `core/ui/commonTest` para o ajuste nas quatro direções: conta ↑/↓ e fatura ↑/↓, afirmando o `value` e a política `EXPLICIT_SIGN`.
- [ ] 4.2 Novo teste de não-regressão, um caso por forma do razão, afirmando `value` e política: despesa em conta, despesa em cartão, receita e pagamento de fatura → `MAGNITUDE` com `value` positivo; transferência de saída → `NATURAL` com `value = -X`; de entrada → `NATURAL` com `value = +X`. **Afirmar o número, não o texto** — em `NATURAL` o positivo renderiza sem "+".
- [ ] 4.3 Teste do texto renderizado por política, cobrindo o par que mais confunde: `EXPLICIT_SIGN` com valor positivo produz "+"; `NATURAL` com valor positivo não produz.
- [ ] 4.4 Teste em `feature/report/impl` para o tom: ajuste com perna negativa → `ReportTone.NEGATIVE` (tornando `exportTone` visível ao teste, ou exercitando-o via `toReportLayout`).
- [ ] 4.5 Teste de ponta a ponta do sinal em `feature/creditcards/impl`: executar `AdjustInvoiceUseCase` contra repositório fake, mapear a transação gravada e afirmar o sinal exibido — é o que impede a convenção do `LedgerEntryWriter` e a da tela de divergirem.
- [ ] 4.6 O mesmo em `feature/accounts/impl` com `AdjustBalanceUseCase`.
- [ ] 4.7 Rodar `./gradlew allTests`.

## 5. Verificação manual

- [ ] 5.1 Cartões / extrato da fatura: ajuste que aumenta e que reduz a dívida; conferir que a lista concorda com a linha "Ajustes" do resumo e com a modal de ajuste.
- [ ] 5.2 Contas: ajuste de saldo para mais e para menos; conferir também que os totais do `AccountCard` não mudaram após a absorção (3.1).
- [ ] 5.3 Transações, dashboard e parcelamentos: conferir que despesa, receita, pagamento e transferência renderizam idênticos ao comportamento anterior, e que o `SummaryCard` do mês não mudou após 3.3.
- [ ] 5.4 Relatório: tela e arquivo exportado (HTML/PDF) — valor e tom do ajuste, e as linhas de resumo de receita/despesa após 3.4.
- [ ] 5.5 Conferir em pt-BR a posição do sinal negativo agora que ele vem do `NumberFormat` e não de concatenação (risco de locale, design).

## 6. Fechamento

- [ ] 6.1 Ajustar o KDoc de `Transaction.amount` ("always positive — the sign is a display concern") apontando para o mapper, agora que o valor de exibição carrega sinal.
- [ ] 6.2 Abrir uma proposta OpenSpec separada (`openspec new change`) para a ausência de perspectiva em `InvoiceTransactionsScreen`, `CreditCardsScreen` e `ReportViewerScreen` ao chamar `toTransactionUi()` — sem corrigir aqui. Registrar no `proposal.md` dela que o ajuste não é afetado, por ter uma única perna monetária.
- [ ] 6.3 `openspec validate fix-adjustment-amount-sign --strict` e `/opsx:verify`.
