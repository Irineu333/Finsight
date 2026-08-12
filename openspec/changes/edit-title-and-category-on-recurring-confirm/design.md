## Context

O modal de confirmação de recorrência (`feature/recurring/impl` — `ui/modal/confirmRecurring/`) já é um formulário: ajusta valor, data, destino (conta/cartão) e fatura. Título e categoria são a exceção — aparecem como `OutlinedTextField` com `enabled = false` (`ConfirmRecurringModal.kt:109-135`), e o caso de uso lê os dois direto do template ao montar o `TransactionIntent`: `title = recurring.title` (`ConfirmRecurringUseCase.kt:95,118`) e `contra = contraLegFor(recurring.type, recurring.category)` (`ConfirmRecurringUseCase.kt:107,131`).

Restrições que a mudança tem de respeitar:

- **A categoria não é uma conta.** No ledger ela é uma **dimensão**, carregada pelo `ContraLeg` via `dimensionId`; `contraLegFor` (`core/model` — `extension/Category.kt:39-43`) é o único dono da tradução tipo + categoria → `ContraLeg`, e a ausência de categoria já é modelada como `dimensionId == null` com o `AccountType` nominal. Não há nada a inventar: basta a confirmação passar *outra* categoria a essa mesma função.
- **Coerência tipo × categoria** é regra do domínio, expressa por `Category.Type.isAccept(TransactionType)` (`core/model` — `extension/Category.kt:8`) e já aplicada por `RecurringForm.from` (`RecurringForm.kt:51`). O seletor consome a regra; não a reimplementa.
- **Seletores oferecem apenas fachadas abertas.** `ICategoryRepository.observeAllCategories()` já exclui arquivadas (`CategoryDao.kt:27-28`, `OPEN_CATEGORIES`), e a continuidade da escolha já feita é resolvida por quem monta a lista — é o que `BudgetFormViewModel.withAlreadyChosen` (`BudgetFormViewModel.kt:327-331`) faz para orçamentos.
- **Moeda não é afetada.** Título e categoria não denominam nada; o guarda de moeda (design D17) e o filtro `offeredFor` continuam exatamente como estão.

## Goals / Non-Goals

**Goals:**

- Tornar título e categoria editáveis na confirmação, com o valor do template como ponto de partida.
- Escrever os valores editados na transação daquele ciclo, e apenas nela.
- Manter o comportamento atual como o resultado de quem confirma sem editar — inclusive para os chamadores do caso de uso que não informarem os novos parâmetros.
- Exigir título não vazio, como o formulário da recorrência exige.

**Non-Goals:**

- Propagar a edição para o template ou para os próximos ciclos, sob qualquer forma (nem *checkbox*, nem pergunta pós-confirmação).
- Registrar em algum lugar que aquele ciclo divergiu do template: a transação lançada já é esse registro, e a ocorrência (`RecurringOccurrence`) continua guardando apenas ciclo, mês, status e data.
- Tornar editáveis os demais campos travados (o tipo da recorrência continua fixo — trocá-lo mudaria o sinal do lançamento e a lista de categorias válidas, e é uma edição do modelo, não do ciclo).
- Extrair para um módulo comum o `withAlreadyChosen` de `budgets` (ver Decisões, D4).
- Alterar `SaveRecurringUseCase`, o repositório de recorrências ou qualquer coisa no ledger. Nenhuma migração de banco.

## Decisions

### D1 — `title` e `category` viram parâmetros do caso de uso, com *default* no template

`ConfirmRecurringUseCase.invoke` ganha `title: String = recurring.title` e `category: Category? = recurring.category`, na mesma forma que `amount`, `account`, `creditCard` e `invoice` já têm. O `TransactionIntent` passa a usar `title` e `contraLegFor(recurring.type, category)` nos dois ramos (conta e cartão).

*Por quê:* é a forma que o caso de uso já usa para tudo o que a confirmação pode redirecionar; o *default* preserva o comportamento de qualquer chamador atual sem uma linha de adaptação, e o parâmetro explícito mantém a decisão do lado de quem confirma.

*Alternativa considerada:* receber um objeto de "sobreposições do ciclo" (`ConfirmOverrides`). Rejeitada: cinco parâmetros de sobreposição já existem soltos na assinatura, e introduzir o objeto só para os dois novos criaria duas convenções para a mesma coisa.

*Alternativa considerada:* deixar o view model montar o `TransactionIntent`. Rejeitada: a montagem envolve fatura, ciclo e guarda de moeda — é domínio, e sairia da camada errada.

### D2 — A categoria é filtrada por `isAccept`, e não por igualdade de `Category.Type`

O view model observa `ICategoryRepository.observeAllCategories()` e oferece as que satisfazem `it.type.isAccept(recurring.type)`.

*Por quê:* a coerência tipo × categoria tem um dono no domínio (`isAccept`), e `RecurringForm` já a consome. Repetir `filter { it.type == Category.Type.EXPENSE }` — como o `RecurringFormViewModel` faz hoje ao montar duas listas — seria reimplementar a regra numa terceira forma. Aqui o tipo é fixo, então uma lista basta e a regra pode ser consumida diretamente.

*Alternativa considerada:* `observeCategoriesByType(...)`, que filtra no SQL. Rejeitada: obrigaria o view model a traduzir `TransactionType` → `Category.Type` na mão, que é justamente a regra que `isAccept` possui.

### D3 — Título vazio bloqueia o botão, não é substituído em silêncio

A habilitação do botão `Confirmar` ganha `title.text.isNotBlank()`, junto das condições de valor e destino que já existem (`ConfirmRecurringModal.kt:286-291`).

*Por quê:* o *default* do caso de uso ainda é o título do template, mas ele existe para quem **não informa** o parâmetro — não para quem apagou o campo. Cair no título do template nesse caso entregaria ao usuário uma transação com um nome que ele acabou de remover. Bloquear é o que `RecurringForm.isValid()` já faz no formulário, e mantém a interface com uma única linguagem para "falta preencher".

### D4 — A continuidade da categoria arquivada é resolvida localmente, com a mesma forma de `budgets`

`observeAllCategories()` não devolve arquivadas, então uma recorrência cuja categoria foi arquivada *depois* de escolhida abriria o modal com o seletor vazio — apagando em silêncio a classificação do ciclo. O view model resolve isso somando a categoria já escolhida à lista oferecida quando ela não estiver lá, exatamente o que `withAlreadyChosen` faz em `BudgetFormViewModel`.

*Por quê não promover o helper a um módulo comum:* a seleção aqui é única, e a expressão se resume a uma linha; `withAlreadyChosen` é genérico porque em orçamentos a escolha é uma lista. Mover o helper obrigaria a mexer em `budgets` dentro de uma mudança que não é sobre orçamentos, e `impl → impl` é proibido, então o destino teria de ser `:core:*` — abstração compartilhada sem um terceiro caso que a justifique. O que **não** pode divergir é a regra: oferecer o aberto, preservar o escolhido, e nunca reoferecer o arquivado depois de desmarcado. Isso vai em teste.

### D5 — Nada é lembrado entre ciclos

Nenhuma persistência do que foi editado: a transação lançada carrega o título e a dimensão escolhidos, e é onde o histórico vive. A confirmação seguinte lê o template e sugere o template.

*Alternativa considerada:* guardar as sobreposições em `RecurringOccurrence` para pré-preencher o ciclo seguinte. Rejeitada: transforma uma correção pontual em nova regra do modelo pelas costas do usuário — quem quer mudar o modelo edita o modelo — e ainda exigiria migração de banco.

### D6 — Estado do título fica no modal, categoria no view model

O título é um `rememberTextFieldState` no `ConfirmRecurringModal`, entregue ao view model no `ConfirmRecurringAction.Confirm`, como o valor já é. A categoria vira estado do view model (`ConfirmRecurringAction.CategorySelected`), porque a lista oferecida depende do repositório e da continuidade de D4 e precisa aparecer no `ConfirmRecurringUiState`.

*Por quê:* segue exatamente a divisão que o modal já pratica — texto no `TextFieldState`, seleção no `UiState` — em vez de introduzir uma terceira convenção.

## Risks / Trade-offs

- **Usuário edita esperando que valha para os próximos ciclos** → A ausência de qualquer *checkbox* de propagação torna o alcance da edição o mesmo de todos os outros campos do modal (valor, data, conta), que já valem só para o ciclo. Editar o modelo continua a um toque de distância, no formulário da recorrência.
- **Categoria de tipo incoerente chegando ao ledger** → Só é possível se o seletor oferecer o que `isAccept` recusa; a filtragem é a mesma função que o domínio usa, e o teste de tipo cobre o caso. `contraLegFor` continua sendo o único tradutor para `ContraLeg`.
- **Categoria arquivada some do seletor e apaga a classificação do ciclo** → D4, coberto por cenário na spec e por teste de view model.
- **`ConfirmRecurringUseCase` cresce em parâmetros** (passa a sete sobreposições) → Aceito nesta mudança; o *default* mantém a chamada curta para quem não sobrepõe. Se um oitavo aparecer, aí sim o objeto de sobreposições de D1 se paga.
- **Duas cópias da continuidade de fachada arquivada** (`budgets` e `recurring`) → Aceito conscientemente em D4, com a regra fixada em spec e teste; um terceiro caso passa a justificar a promoção para `:core:*`.
