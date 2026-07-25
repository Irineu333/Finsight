## 1. O tipo de exibição

- [ ] 1.1 Criar `DisplayAmount` em `core/common/.../extension/` (ao lado de `CurrencyFormatter`): `value: Double` + política fechada `MAGNITUDE`, `NATURAL`, `NEUTRAL`, `EXPLICIT_SIGN`, `FORCED_POSITIVE`, `FORCED_NEGATIVE`, `OWED`, com um construtor nomeado por política. Sem operação entre dois valores e sem moeda (design D6); transformar o próprio valor (módulo, negação, limite em zero) é permitido.
- [ ] 1.2 Portar o KDoc por caso do `SignDisplay` (`SummaryCard.kt:426-452`), que é a implementação de referência — em especial o significado de `NEUTRAL` (não move nada nesta perspectiva) e de `OWED` (magnitude devida, zero quando não se deve).
- [ ] 1.3 Adicionar a formatação como extensão sobre `CurrencyFormatter` (`fun CurrencyFormatter.format(amount: DisplayAmount): String`), cobrindo as sete políticas. `FORCED_POSITIVE`/`FORCED_NEGATIVE` aplicam `absoluteValue` — divergência entre os sítios de origem resolvida em 3.3.
- [ ] 1.4 Testar a formatação por política em `core/common`: zero em `EXPLICIT_SIGN` (sem sinal), negativo em `MAGNITUDE` (sai em módulo), positivo em `NATURAL` (sem `+`), saldo credor em `OWED` (zero).

## 2. A correção do defeito (superfície de item)

- [ ] 2.1 Trocar `TransactionUi.amount: Double` por `DisplayAmount`.
- [ ] 2.2 Em `TransactionUiMapper`: remover o `abs()` e resolver a política pela tabela de item do design D5, chaveando em `TransactionLabel` (a natureza derivada pelo razão) — `EXPLICIT_SIGN` para ajuste; `NATURAL` para transferência **com** perspectiva e `MAGNITUDE` para transferência **sem** perspectiva; `MAGNITUDE` para gasto, receita e pagamento.
- [ ] 2.3 No mesmo mapper, substituir a escolha da perna neutra reimplementada por `Transaction.primaryEntry` (`core/ledger`), preservando o caminho com perspectiva (`accountId`) e o retorno `null` quando não há perna correspondente.
- [ ] 2.4 Em `TransactionCard`: apagar o `when` sobre `direction`/`label` e a concatenação manual de `"-"`; renderizar via `formatter.format(transaction.amount)`.
- [ ] 2.5 Em `ReportExportLayout`: apagar `exportAmount` (clone do `when`) e renderizar pela mesma extensão.
- [ ] 2.6 Em `ReportExportLayout.exportTone`: ler o sinal do `value` do `DisplayAmount`, mantendo a ordem dos ramos — o ajuste passa a alcançar `ReportTone.NEGATIVE`, hoje inalcançável.
- [ ] 2.7 Atualizar `core/ui/.../TransactionPerspectiveTest`: a ponta de saída de uma transferência **com perspectiva** vale `-100.0`; a de entrada, `100.0`. Atualizar o KDoc da classe para nomear o sinal. **Pertence a este grupo**: entre 2.1 e esta tarefa o source set de teste de `:core:ui` não compila.
- [ ] 2.8 Trocar `implementation(projects.core.common)` por `api(...)` em `core/ui/build.gradle.kts`: `TransactionUi.amount` passa a expor um tipo de `:core:common` na API pública de `:core:ui`. Hoje compila por acidente.

## 3. Absorção dos demais sítios (superfície de resumo)

> Os resumos **já obedecem** à regra (design D5): esta seção troca o mecanismo, não o comportamento. Qualquer diferença visual encontrada aqui é regressão, não melhoria.

- [ ] 3.1 Em `AccountCard`: remover o `private enum class AccountSignDisplay` e mapear seus quatro casos para as políticas de `DisplayAmount` (`ALWAYS_POSITIVE`→`FORCED_POSITIVE`, `ALWAYS_NEGATIVE`→`FORCED_NEGATIVE`, `SHOW_ONLY_NEGATIVE`→`NATURAL`, `SHOW_ALWAYS`→`EXPLICIT_SIGN`), sem mudança nas seis linhas que o usam (`AccountCard.kt:179,187,194,202,211,221`); a única tela afetada é a de contas, via `Variant.Detail`.
- [ ] 3.2 Em `InvoiceTransactionsScreen.SummaryRow` (`InvoiceTransactionsScreen.kt:728` — **não** confundir com o `SummaryRow` homônimo de `SummaryCard.kt:399`, que é outro sistema): remover `isPositive`/`isNegative`/`showSign` e receber `DisplayAmount` nas quatro linhas do resumo.
- [ ] 3.3 Em `SummaryCard.kt`: remover o `enum class SignDisplay` e converter os 18 usos. Antes de unificar, verificar se algum chamador passa valor negativo a `ALWAYS_POSITIVE`/`ALWAYS_NEGATIVE` — `SummaryCard.kt:447-448` **não** aplica `absoluteValue` e `AccountCard.kt:325-326` aplica; se algum passar, é mudança de comportamento e precisa ser decidida, não assumida.
- [ ] 3.4 Em `ReportContextCard.kt:165,182` e `ReportExportLayout.kt:73,78`: trocar os literais `"+${...}"`/`"-${...}"` das linhas de resumo por `DisplayAmount`.
- [ ] 3.5 Confirmar por busca que não sobrou sítio compondo sinal à mão (`"+${`, `"-${`, `absoluteValue` junto de `format`) fora de `DisplayAmount`. A busca deve voltar vazia; se voltar algo, é sítio não catalogado e o escopo precisa ser revisto antes de marcar esta tarefa.

## 4. Testes

- [ ] 4.1 Novo teste em `core/ui/commonTest` para o ajuste nas quatro direções: conta ↑/↓ e fatura ↑/↓, afirmando o `value` e a política `EXPLICIT_SIGN`.
- [ ] 4.2 Novo teste de não-regressão da superfície de item, afirmando `value` e política: gasto em conta, gasto em cartão, receita e pagamento de fatura → `MAGNITUDE` com `value` positivo. **Afirmar o número, não o texto.**
- [ ] 4.3 Novo teste da transferência nas duas leituras: com perspectiva → `NATURAL`, `value = -X` na conta de saída e `+X` na de entrada; sem perspectiva → `MAGNITUDE`. É a única mudança visual além do ajuste.
- [ ] 4.4 Teste do texto por política, cobrindo o par que mais confunde: `EXPLICIT_SIGN` com valor positivo produz `+`; `NATURAL` com valor positivo não produz.
- [ ] 4.5 Teste em `feature/report/impl` para o tom: ajuste com perna negativa → `ReportTone.NEGATIVE` (tornando `exportTone` visível ao teste, ou exercitando-o via `toReportLayout`).
- [ ] 4.6 Teste de ponta a ponta do sinal em `feature/creditcards/impl`: executar `AdjustInvoiceUseCase` contra repositório fake, mapear a transação gravada e afirmar o sinal exibido — é o que impede a convenção do `LedgerEntryWriter` e a da tela de divergirem.
- [ ] 4.7 O mesmo em `feature/accounts/impl` com `AdjustBalanceUseCase`.
- [ ] 4.8 Rodar `./gradlew allTests`.

## 5. Verificação manual

- [ ] 5.1 Cartões / extrato da fatura: ajuste que aumenta e que reduz a dívida; conferir que a lista concorda com a linha "Ajustes" do resumo e com a modal de ajuste.
- [ ] 5.2 Contas: ajuste de saldo para mais e para menos; transferência entre duas contas vista das duas pontas (deve manter o `−` na saída); conferir que o `AccountCard` não mudou após 3.1.
- [ ] 5.3 Transações: lista geral — a transferência agora aparece **sem** sinal (mudança esperada); gasto, receita e pagamento idênticos. Conferir os três corpos do `SummaryCard` (contas, cartões, geral) após 3.3, linha a linha.
- [ ] 5.4 Dashboard e parcelamentos: itens idênticos ao comportamento anterior.
- [ ] 5.5 Relatório: tela e arquivo exportado (HTML/PDF) — valor e tom do ajuste, e as linhas de resumo de receita/despesa após 3.4.
- [ ] 5.6 Conferir em pt-BR a posição do sinal negativo agora que ele vem do `NumberFormat` e não de concatenação.

## 6. Fechamento

- [ ] 6.1 Ajustar o KDoc de `Transaction.amount` ("always positive — the sign is a display concern") apontando para o mapper, agora que o valor de exibição carrega sinal.
- [ ] 6.2 Abrir uma proposta OpenSpec separada para a ausência de perspectiva em `InvoiceTransactionsScreen`, `CreditCardsScreen` e `ReportViewerScreen` ao chamar `toTransactionUi()` — sem corrigir aqui. Registrar nela que o ajuste não é afetado, por ter uma única perna monetária, mas que a **transferência** passa a ser, já que a sua política depende da perspectiva (2.2).
- [ ] 6.3 `openspec validate fix-adjustment-amount-sign --strict` e `/opsx:verify`.
