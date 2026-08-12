## 1. Origem única da recusa de apagar uma categoria orçada

*Barreira de entrada: árvore limpa, nenhum arquivo desta mudança editado ainda. Este grupo
não compartilha arquivo algum com o grupo 2 e pode correr ao mesmo tempo que ele.*
*Barreira de saída: `AccountError.HAS_BUDGET` e `account_error_has_budget` não existem mais
em lugar nenhum, `retire_error_has_budget` está reescrita nos dois idiomas, e o projeto
compila (`./gradlew jvmTest` chega a executar os testes). Como as três tarefas são o mesmo
apagamento visto de três arquivos, estados intermediários do grupo podem não compilar — a
compilação é exigida no fim do grupo, não entre as suas tarefas.*

- [ ] 1.1 Em `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/error/AccountError.kt`, remover o valor `HAS_BUDGET` (com o seu KDoc), o ramo `AccountError.HAS_BUDGET ->` de `toUiText` e o `import` de `com.neoutils.finsight.resources.account_error_has_budget` (D4: o valor é inalcançável — nenhum caso de uso o emite — e o seu `message` descreve categoria dentro do enum de conta; o `when` é exaustivo, então valor e ramo saem juntos, por construção consistentes).
- [ ] 1.2 Em `core/resources/src/commonMain/composeResources/values/strings.xml`, remover a chave `account_error_has_budget` e reescrever `retire_error_has_budget` para `Esta categoria está em uso por um ou mais orçamentos. Remova-a de todos antes de excluir.` (D5). Não tocar em `recurring_retire_error_has_budget` — é Non-Goal declarado.
- [ ] 1.3 Em `core/resources/src/commonMain/composeResources/values-en/strings.xml`, remover a chave `account_error_has_budget` e reescrever `retire_error_has_budget` para `This category is used by one or more budgets. Remove it from all of them before deleting.` (D5). Não tocar em `recurring_retire_error_has_budget`.

## 2. Remoção da trava de exclusividade no formulário de orçamento

*Barreira de entrada: nada — este grupo não depende do grupo 1 (arquivos disjuntos). O que
ele exige já está decidido em D1 e D6: a nova assinatura é `offeredCategories(open, selected)`,
declarada no design, e por isso a tarefa do teste não espera pelo resultado da tarefa do
ViewModel.*
*Barreira de saída: `otherBudgetCategoryIds` não aparece em nenhum arquivo do repositório, o
projeto compila e `OfferedCategoriesTest` passa. Estados intermediários podem não compilar,
pela mesma razão do grupo 1: a assinatura muda de um lado e é consumida do outro.*

- [ ] 2.1 Em `feature/budgets/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/budgetForm/BudgetFormViewModel.kt`, remover a trava na origem e adequar a leitura do formulário — uma única tarefa porque tudo é o mesmo arquivo: (a) `offeredCategories` perde o parâmetro `otherBudgetCategoryIds` e o `filterNot`, ficando `withAlreadyChosen(offered = open, chosen = selected, id = Category::id)` (D1 — passar `emptySet()` foi descartado); (b) o KDoc de `offeredCategories` é reescrito para descrever o estado atual — o que a função oferece e por quê — sem afirmar que a categoria pertence a no máximo um orçamento e sem narrar que existiu uma trava (D3); (c) o braço `budgetRepository.observeAllBudgets()` sai do `combine` de `uiState`, que cai de quatro para três braços, e o cálculo de `budgetedCategoryIds` desaparece junto com o argumento na chamada de `offeredCategories` (D2); (d) `budgetRepository` continua sendo parâmetro do construtor, porque `submit()` o usa para `insert`/`update` (D2).
- [ ] 2.2 Em `feature/budgets/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/modal/budgetForm/OfferedCategoriesTest.kt`, inverter o caso da subtração e adequar as chamadas à nova assinatura: o teste `open categories are offered minus those held by other budgets` passa a afirmar o contrário — uma categoria contida em outro orçamento continua sendo oferecida —, com nome e comentário coerentes com a nova regra (D6: invertido, não apagado, para que a nova regra não fique sem teste no mesmo ponto). Os três casos de continuidade de arquivada permanecem com a mesma asserção, apenas sem o argumento `otherBudgetCategoryIds`; o KDoc da classe passa a descrever a única regra que o seletor aplica.

## 3. Verificação

*Barreira de entrada: grupos 1 e 2 concluídos — a verificação lê o resultado dos dois, então
é obrigatoriamente posterior a ambos.*
*Barreira de saída: os comandos abaixo executados de fato e a sua saída lida; suíte verde,
nenhuma referência remanescente às chaves e ao valor removidos, e os dois `strings.xml`
simétricos.*

- [ ] 3.1 Rodar `./gradlew :feature:budgets:impl:jvmTest --tests "*.OfferedCategoriesTest"` e confirmar que os quatro casos passam (a classe vive em `feature/budgets/impl`, não em `:app:shared` — o alvo `:app:shared:testDebugUnitTest --tests "*.XxxTest"` do CLAUDE.md não a alcança).
- [ ] 3.2 Rodar `./gradlew jvmTest` e confirmar a suíte inteira verde — é isso que fecha o risco de "remover um valor de enum público em `core/model` quebrar consumidor não mapeado": a falha, se houver, é de compilação e aparece aqui.
- [ ] 3.3 Conferir, lendo os dois arquivos, que `core/resources/src/commonMain/composeResources/values/strings.xml` e `values-en/strings.xml` ficaram simétricos: `account_error_has_budget` ausente nos dois, `retire_error_has_budget` presente nos dois com o texto de D5, e nenhuma chave presente em só um dos arquivos (uma chave em um único idioma é bug pela regra do projeto).
- [ ] 3.4 Buscar no repositório (excluindo `build/`) por `otherBudgetCategoryIds`, `AccountError.HAS_BUDGET` e `account_error_has_budget` e confirmar zero ocorrências — nenhum resto do filtro nem da duplicata morta.
