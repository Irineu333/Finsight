## 1. Strings

- [ ] 1.1 Adicionar `transfer_title_label` em `values/strings.xml` (pt) e `values-en/strings.xml` (en) — o rótulo do campo de título do formulário de transferência
- [ ] 1.2 Adicionar `installment_card_installment` em ambos os arquivos — a forma de uma parcela sem título nem categoria ("Parcelamento" / "Installment"), conforme D7

## 2. A transferência ganha título

- [ ] 2.1 `TransferBetweenAccountsAction.Submit`: acrescentar `title: String?`
- [ ] 2.2 `TransferBetweenAccountsUseCase`: receber `title` e gravá-lo no `TransactionIntent` em vez do `null` fixo (`:62`)
- [ ] 2.3 `UpdateTransferUseCase`: receber `title` e gravá-lo em `updateTransaction`, substituindo o `transaction.title` carregado adiante (`:63`); remover o comentário `:58-62`, que descreve um formulário que deixou de existir
- [ ] 2.4 `TransferBetweenAccountsViewModel.submit`: repassar o título aos dois use cases, sem transformá-lo — vazio vira `null` num único ponto
- [ ] 2.5 `TransferBetweenAccountsModal`: campo `OutlinedTextField` de título antes dos seletores de conta (D1), semeado com `transaction?.title` no modo de correção, com `testTag("transfer_title")`
- [ ] 2.6 Confirmar que `isValidTransfer` **não** muda: o título é opcional e não participa da habilitação do botão

## 3. A precedência universal na lista

- [ ] 3.1 `DisplayTitle.kt`: remover `displayTitleOf` e o literal `"Untitled"`; atualizar o KDoc de `displayTitleOrNull` para descrever o estado atual — dona dos dois primeiros elos, sem último recurso
- [ ] 3.2 `TransactionUiMapper.kt:47`: passar a usar `displayTitleOrNull(title, category)`
- [ ] 3.3 `TransactionUi.title`: tornar `String?` e ajustar o KDoc — é o nome que a operação tem por si, não o nome final exibido
- [ ] 3.4 `TransactionCard.displayTitle` (`:157-171`): inverter a cadeia para título → categoria → forma, com os textos de forma continuando resolvidos no componente (D4); o sufixo de parcela segue aplicado ao nome resultante

## 4. As demais superfícies do nome

- [ ] 4.1 `Recurring.label` (`:17`): `displayTitleOrNull(title, category) ?: error(...)`, com KDoc citando `RecurringForm.toRecurring` como dono do invariante (D6)
- [ ] 4.2 `InstallmentUi.title`: tornar `String?` e `InstallmentUiMapper.kt:58` passar a usar `displayTitleOrNull`
- [ ] 4.3 `InstallmentsScreen.kt:510`: fornecer a forma da parcela como terceiro elo, usando a chave de 1.2 (D7)

## 5. Testes

- [ ] 5.1 `ComposeAppCommonTest.kt:15` e `:52`: converter os dois testes de `"Untitled"` para provar que a violação do invariante **lança**, em vez de produzir um literal
- [ ] 5.2 `TransferBetweenAccountsViewModelTest`: cobrir registrar com título, registrar sem título, corrigir o título e apagá-lo
- [ ] 5.3 `EditTransferEndToEndTest`: cobrir que a correção grava o título que o formulário exibe, inclusive quando esvaziado
- [ ] 5.4 Cobrir a precedência na lista pelo mapper: operação com título, sem título e com categoria, e sem nenhum dos dois
- [ ] 5.5 Cobrir que uma parcela sem título nem categoria é nomeada pela sua forma, e não por um literal genérico

## 6. Verificação

- [ ] 6.1 `grep -rn "Untitled\|displayTitleOf" --include="*.kt" .` não retorna nada fora de arquivos arquivados
- [ ] 6.2 Confirmar que toda chave nova existe nos **dois** `strings.xml` — uma chave só num deles é bug
- [ ] 6.3 `./gradlew jvmTest` verde, com a saída lida
- [ ] 6.4 Exercitar o formulário no app: registrar uma transferência com título e confirmar que a lista a nomeia por ele, e o detalhe também
