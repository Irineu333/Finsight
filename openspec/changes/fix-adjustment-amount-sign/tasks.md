## 1. O tipo de exibição

- [ ] 1.1 Criar `DisplayAmount` em `core/common/.../extension/` (ao lado de `CurrencyFormatter`): `value: Double` + política fechada `MAGNITUDE`, `NATURAL`, `EXPLICIT_SIGN`, `FORCED_POSITIVE`, `FORCED_NEGATIVE`, com construtores nomeados por intenção. Sem aritmética, sem moeda (design D4).
- [ ] 1.2 Adicionar a formatação como extensão sobre `CurrencyFormatter` (`fun CurrencyFormatter.format(amount: DisplayAmount): String`), cobrindo as cinco políticas: `MAGNITUDE` → `format(abs)`, `NATURAL` → `format(value)`, `EXPLICIT_SIGN` → `formatWithSign(value)`, `FORCED_POSITIVE`/`FORCED_NEGATIVE` → sinal + `format(abs)`.
- [ ] 1.3 Testar a formatação por política em `core/common`, incluindo o zero em `EXPLICIT_SIGN` (sem sinal) e o negativo em `MAGNITUDE` (sai em módulo).

## 2. A correção do defeito

- [ ] 2.1 Trocar `TransactionUi.amount: Double` por `DisplayAmount`.
- [ ] 2.2 Em `TransactionUiMapper`: remover o `abs()` e resolver a política por forma do razão conforme a tabela do design D5 — `EXPLICIT_SIGN` para ajuste, `NATURAL` para transferência, `MAGNITUDE` para despesa, receita e pagamento.
- [ ] 2.3 No mesmo mapper, substituir a escolha da perna neutra reimplementada por `Transaction.primaryEntry` (`core/ledger`), preservando o caminho com perspectiva (`accountId`) e o retorno `null` quando não há perna correspondente.
- [ ] 2.4 Em `TransactionCard`: apagar o `when` sobre `direction`/`label` e a concatenação manual de `"-"`; renderizar via `formatter.format(transaction.amount)`.
- [ ] 2.5 Em `ReportExportLayout`: apagar `exportAmount` (clone do `when`) e renderizar pela mesma extensão.
- [ ] 2.6 Em `ReportExportLayout.exportTone`: ajustar a leitura do sinal para o `value` do `DisplayAmount`, mantendo a ordem dos ramos — o ajuste passa a alcançar `ReportTone.NEGATIVE`, hoje inalcançável.
- [ ] 2.7 Verificar que a cor do card (`TransactionUi.color()`) segue lendo `label`/`direction` e não regrediu para leitura por sinal.

## 3. Absorção das reimplementações existentes

- [ ] 3.1 Em `AccountCard`: remover o `private enum class AccountSignDisplay` e mapear seus quatro casos 1:1 para as políticas de `DisplayAmount` (`ALWAYS_POSITIVE`→`FORCED_POSITIVE`, `ALWAYS_NEGATIVE`→`FORCED_NEGATIVE`, `SHOW_ONLY_NEGATIVE`→`NATURAL`, `SHOW_ALWAYS`→`EXPLICIT_SIGN`), sem mudança de comportamento nos seis chamadores.
- [ ] 3.2 Em `InvoiceTransactionsScreen.SummaryRow`: remover os parâmetros `isPositive`/`isNegative`/`showSign` e receber `DisplayAmount`, atualizando as quatro linhas do resumo (despesas, pagamentos antecipados, ajustes, total) para a política equivalente à de hoje.
- [ ] 3.3 Confirmar por busca que não sobrou nenhum sítio compondo sinal à mão (`"+${`, `"-${`, `absoluteValue` junto de `format`) fora de `DisplayAmount`.

## 4. Testes

- [ ] 4.1 Atualizar `core/ui/.../TransactionPerspectiveTest`: a ponta de saída de uma transferência passa a valer `-100.0`; a de entrada segue `100.0`. Atualizar o KDoc da classe para nomear o sinal.
- [ ] 4.2 Novo teste em `core/ui/commonTest` para o ajuste nas quatro direções: conta ↑/↓ e fatura ↑/↓, afirmando valor e política.
- [ ] 4.3 Novo teste de não-regressão, um caso por forma do razão: despesa em conta, despesa em cartão, receita, pagamento de fatura → `MAGNITUDE` e valor positivo; transferência pelas duas pontas → `NATURAL` com `-X`/`+X`.
- [ ] 4.4 Teste em `feature/report/impl` para o tom: ajuste com perna negativa → `ReportTone.NEGATIVE` (tornando `exportTone` visível ao teste, ou exercitando-o via `toReportLayout`).
- [ ] 4.5 Teste de ponta a ponta do sinal em `feature/creditcards/impl`: executar `AdjustInvoiceUseCase` contra repositório fake, mapear a transação gravada e afirmar o sinal exibido — é o que impede a convenção do `LedgerEntryWriter` e a da tela de divergirem.
- [ ] 4.6 O mesmo em `feature/accounts/impl` com `AdjustBalanceUseCase`.
- [ ] 4.7 Rodar `./gradlew allTests`.

## 5. Verificação manual

- [ ] 5.1 Cartões / extrato da fatura: ajuste que aumenta e que reduz a dívida; conferir que a lista concorda com a linha "Ajustes" do resumo e com a modal de ajuste.
- [ ] 5.2 Contas: ajuste de saldo para mais e para menos; conferir também que os totais do `AccountCard` não mudaram após a absorção (3.1).
- [ ] 5.3 Transações, dashboard e parcelamentos: conferir que despesa, receita, pagamento e transferência renderizam idênticos ao comportamento anterior.
- [ ] 5.4 Relatório: tela e arquivo exportado (HTML/PDF) — valor e tom do ajuste.
- [ ] 5.5 Conferir em pt-BR a posição do sinal negativo agora que ele vem do `NumberFormat` e não de concatenação (risco de locale, design).

## 6. Fechamento

- [ ] 6.1 Ajustar o KDoc de `Transaction.amount` ("always positive — the sign is a display concern") apontando para o mapper, agora que o valor de exibição carrega sinal.
- [ ] 6.2 Registrar como defeito separado — sem corrigir aqui — a ausência de perspectiva em `InvoiceTransactionsScreen`, `CreditCardsScreen` e `ReportViewerScreen` ao chamar `toTransactionUi()`.
- [ ] 6.3 `openspec validate fix-adjustment-amount-sign --strict` e `/opsx:verify`.
