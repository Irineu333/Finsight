## 1. Domínio — o caso de uso aceita as sobreposições do ciclo

- [ ] 1.1 Em `ConfirmRecurringUseCase.invoke`, adicionar `title: String = recurring.title` e `category: Category? = recurring.category`, junto das sobreposições já existentes (D1)
- [ ] 1.2 Usar `title` no `TransactionIntent` dos dois ramos (cartão e conta), no lugar de `recurring.title`
- [ ] 1.3 Usar `contraLegFor(recurring.type, category)` nos dois ramos, no lugar de `recurring.category`
- [ ] 1.4 Atualizar o KDoc da classe para descrever o estado atual: a confirmação sobrepõe título e categoria do ciclo, e não toca no template (sem narrar a mudança)
- [ ] 1.5 Conferir que nenhum chamador existente precisou mudar (os *defaults* preservam o comportamento) — `grep` por `confirmRecurringUseCase(` e por `ConfirmRecurringUseCase(`

## 2. View model — categoria selecionável e continuidade da arquivada

- [ ] 2.1 Injetar `ICategoryRepository` em `ConfirmRecurringViewModel` e registrar o parâmetro em `RecurringModule` (`viewModel { ConfirmRecurringViewModel(...) }`)
- [ ] 2.2 Adicionar `selectedCategory: MutableStateFlow<Category?>` iniciado com `recurring.category`
- [ ] 2.3 Compor a lista oferecida: `observeAllCategories()` filtrado por `it.type.isAccept(recurring.type)` (D2), somada à categoria já escolhida quando ela não estiver na lista (D4), com KDoc explicando a continuidade da fachada arquivada
- [ ] 2.4 Expor `categories: List<Category>` e `selectedCategory: Category?` em `ConfirmRecurringUiState`
- [ ] 2.5 Adicionar `ConfirmRecurringAction.CategorySelected(val category: Category?)` e tratá-la em `onAction`
- [ ] 2.6 Fazer `ConfirmRecurringAction.Confirm` carregar o título (`Confirm(val amount: String, val title: String)`) e repassar `title` e `selectedCategory.value` ao caso de uso em `confirm(...)`

## 3. Modal — campo de título e seletor de categoria

- [ ] 3.1 Adicionar as chaves `recurring_confirm_title_label` em `values/strings.xml` (pt) e `values-en/strings.xml` (en)
- [ ] 3.2 Trocar o `Text` do cabeçalho/`OutlinedTextField` desabilitado de título por um `OutlinedTextField` editável com `rememberTextFieldState(recurring.title)`, `KeyboardCapitalization.Sentences`, `imeAction = ImeAction.Next` e `testTag("confirm_recurring_title")`
- [ ] 3.3 Substituir o campo desabilitado de categoria pelo `CategorySelector` (`core/ui`), alimentado por `uiState.categories` / `uiState.selectedCategory`, despachando `ConfirmRecurringAction.CategorySelected`, com `valueTestTag = "confirm_recurring_category"`
- [ ] 3.4 Renderizar o seletor de categoria também quando a recorrência não tem categoria (hoje o bloco só existe dentro de `recurring.category?.let { ... }`)
- [ ] 3.5 Adicionar `title.text.isNotBlank()` às condições de `enabled` do botão `Confirmar` (D3) e passar o título no `ConfirmRecurringAction.Confirm`

## 4. Testes

- [ ] 4.1 Caso de uso: confirmar sem informar `title`/`category` grava os do template (comportamento preservado)
- [ ] 4.2 Caso de uso: confirmar com `title` e `category` diferentes grava os informados na transação, nos dois ramos (conta e cartão)
- [ ] 4.3 Caso de uso: confirmar com `category = null` grava a transação sem dimensão de categoria, sem recusa
- [ ] 4.4 Caso de uso: após confirmar com sobreposições, o template lido do repositório continua com título e categoria originais
- [ ] 4.5 View model: a lista oferecida contém apenas categorias que `isAccept` aceita para o tipo da recorrência
- [ ] 4.6 View model: categoria arquivada nomeada pelo template aparece na lista e selecionada; desmarcada, deixa de ser oferecida
- [ ] 4.7 View model: `CategorySelected(null)` leva `category = null` ao caso de uso
- [ ] 4.8 Conferir que `ConfirmRecurringCurrencyTest` e `OfferedForCurrencyTest` continuam válidos (moeda não é afetada) e ajustar as chamadas se a assinatura mudou

## 5. Verificação

- [ ] 5.1 Rodar `./gradlew :app:shared:testDebugUnitTest --tests "*ConfirmRecurring*"` e ler a saída
- [ ] 5.2 Rodar `./gradlew jvmTest` e confirmar a suíte inteira verde
- [ ] 5.3 Conferir que toda chave nova de string existe nos dois arquivos (pt e en)
- [ ] 5.4 Exercitar o fluxo no Desktop (`./gradlew :app:desktop:run`): editar título e categoria, confirmar, ver a transação lançada com os valores editados e a recorrência inalterada
