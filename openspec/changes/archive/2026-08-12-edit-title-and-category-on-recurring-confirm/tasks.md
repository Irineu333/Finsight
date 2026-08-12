## 1. Domínio — o caso de uso aceita as sobreposições do ciclo

- [x] 1.1 Em `ConfirmRecurringUseCase.invoke`, adicionar `title: String? = recurring.title` e `category: Category? = recurring.category`, junto das sobreposições já existentes (D1) — `title` é anulável porque `Recurring.title` também é
- [x] 1.2 Usar `title` no `TransactionIntent` dos dois ramos (cartão e conta), no lugar de `recurring.title`
- [x] 1.3 Usar `contraLegFor(recurring.type, category)` nos dois ramos, no lugar de `recurring.category`
- [x] 1.4 Atualizar o KDoc da classe para descrever o estado atual: a confirmação sobrepõe título e categoria do ciclo, e não toca no template (sem narrar a mudança)
- [x] 1.5 Conferir que nenhum chamador existente precisou mudar (os *defaults* preservam o comportamento) — único chamador é `ConfirmRecurringViewModel`

## 2. View model — categoria selecionável e continuidade da arquivada

- [x] 2.1 Injetar `ICategoryRepository` em `ConfirmRecurringViewModel` e registrar o parâmetro em `RecurringModule`
- [x] 2.2 Adicionar `selectedCategory: MutableStateFlow<Category?>` iniciado com `recurring.category`
- [x] 2.3 Compor a lista oferecida em `offeredCategories`: `observeAllCategories()` filtrado por `it.type.isAccept(recurring.type)` (D2), somada à categoria já escolhida quando ela não estiver na lista (D4), com KDoc explicando a continuidade da fachada arquivada
- [x] 2.4 Expor `categories: List<Category>` e `selectedCategory: Category?` em `ConfirmRecurringUiState` — via o flow `categorySelection`, para caber no `combine` de 10 argumentos
- [x] 2.5 Adicionar `ConfirmRecurringAction.CategorySelected(val category: Category?)` e tratá-la em `onAction`
- [x] 2.6 Fazer `ConfirmRecurringAction.Confirm` carregar o título (`Confirm(amount, title)`) e repassar `title`/`selectedCategory.value` ao caso de uso, com título em branco virando `null` (D3)

## 3. Modal — campo de título e seletor de categoria

- [x] 3.1 Adicionar as chaves `recurring_confirm_title_label` em `values/strings.xml` (pt) e `values-en/strings.xml` (en)
- [x] 3.2 Adicionar um `OutlinedTextField` editável com `rememberTextFieldState(recurring.title.orEmpty())`, `KeyboardCapitalization.Sentences`, `imeAction = ImeAction.Next` e `testTag("confirm_recurring_title")`, no topo do formulário
- [x] 3.3 Substituir o campo desabilitado de categoria pelo `CategorySelector` (`core/ui`), alimentado por `uiState.categories` / `uiState.selectedCategory`, despachando `ConfirmRecurringAction.CategorySelected`, com `valueTestTag = "confirm_recurring_category"`
- [x] 3.4 Renderizar o seletor de categoria também quando a recorrência não tem categoria (antes o bloco só existia dentro de `recurring.category?.let { ... }`)
- [x] 3.5 Exigir `title.text.isNotBlank() || uiState.selectedCategory != null` no `enabled` do botão `Confirmar` (D3) e passar o título no `ConfirmRecurringAction.Confirm`

## 4. Testes

- [x] 4.1 Caso de uso: confirmar sem informar `title`/`category` grava os do template (comportamento preservado)
- [x] 4.2 Caso de uso: confirmar com `title` e `category` diferentes grava os informados na transação, nos dois ramos (conta e cartão)
- [x] 4.3 Caso de uso: confirmar com `category = null` grava a transação sem dimensão de categoria, sem recusa
- [x] 4.4 Caso de uso: após confirmar com sobreposições, o template continua com título e categoria originais
- [x] 4.5 Caso de uso: `title = null` é gravado como ausência, sem voltar ao título do template (D3)
- [x] 4.6 `offeredCategories`: só as categorias que `isAccept` aceita para o tipo da recorrência
- [x] 4.7 `offeredCategories`: categoria arquivada nomeada pelo template aparece na lista; desmarcada, deixa de ser oferecida; a já aberta não é oferecida duas vezes
- [x] 4.8 Conferir que `ConfirmRecurringCurrencyTest` e `OfferedForCurrencyTest` continuam válidos (moeda não é afetada) — ambos passam sem alteração

## 5. Ajustes do teste manual

- [x] 5.1 Remover o cabeçalho com o `label` da recorrência: com o título editável logo abaixo, ele repetia a mesma informação
- [x] 5.2 Passar `onEmpty` ao `CategorySelector`, abrindo `categoriesEntry.categoryFormModal(initialType = ...)` — sem ele o controle ficava desabilitado quando não havia categoria, contra o padrão do app. O tipo inicial sai de `isAccept`, sem mapa novo entre `TransactionType` e `Category.Type`
- [x] 5.3 Passar `onEmpty` ao `CreditCardSelector` da mesma modal, abrindo `creditCardsEntry.creditCardFormModal()` — a mesma lacuna, no outro seletor que oferece cadastro. `AccountSelector` não expõe `onEmpty`, então a conta segue sem essa ação

## 6. Verificação

- [x] 6.1 Rodar `./gradlew :feature:recurring:impl:jvmTest --tests "*ConfirmRecurring*" --tests "*OfferedCategories*"` — 15 testes, verde
- [x] 6.2 Rodar `./gradlew jvmTest` — 1159 testes, `BUILD SUCCESSFUL`, nenhuma falha
- [x] 6.3 Conferir que a chave nova de string existe nos dois arquivos (pt e en)
- [x] 6.4 Fluxo exercitado no app pelo usuário: título e categoria editados, ciclo confirmado, recorrência inalterada
