## Context

A trava existe em um único lugar: `offeredCategories`, em `BudgetFormViewModel.kt:346-354`, que subtrai da lista oferecida toda categoria pertencente a outro orçamento. O conjunto subtraído é montado em `BudgetFormViewModel.kt:159-163`, a partir de `budgetRepository.observeAllBudgets()` — um braço do `combine` que existe **só** para isso.

Abaixo dessa função, nada assume exclusividade. Verificado arquivo a arquivo:

| camada | estado |
|---|---|
| `BudgetCategoryEntity.kt:9` | chave primária `(budgetId, categoryId)`, índices separados, nenhum `UNIQUE` em `categoryId` — o schema já é N:M |
| `BudgetDao.kt:29` | `COUNT(*) … WHERE categoryId` — já contava N; o repositório o reduz a booleano |
| `BudgetRepository.kt:31-39` | hidratação agrupa por `budgetId`, nunca por categoria |
| `CalculateBudgetProgressUseCase.kt:64-98` | `budgets.map { … }`: cada barra soma as suas próprias dimensões |
| `ResolveCategoryRetirabilityUseCase.kt:34` | guard booleano "está em algum orçamento" |
| `BudgetsScreen`, `DashboardComponentsBuilder` | nenhum total agregado entre orçamentos |
| `openspec/specs/**` | nenhum requisito codifica a exclusividade |

A regra nunca desceu abaixo do ViewModel: ela só existiu como filtro de dropdown, documentada em um KDoc (`BudgetFormViewModel.kt:343`) que afirma *"a category belongs to at most one"* — uma afirmação que o schema sempre contradisse.

No caminho da verificação apareceu um segundo achado, independente da trava: `AccountError.HAS_BUDGET` (`AccountError.kt:46`) declara `message = "Cannot delete a category a budget still uses"` — uma regra de **categoria** dentro do enum de **conta** — e a sua única referência é o próprio mapeamento em `toUiText` (`AccountError.kt:71`). Nenhum caso de uso o emite; o guard real é `RetireError.HAS_BUDGET`, emitido por `ResolveCategoryRetirabilityUseCase.kt:35`. A string `account_error_has_budget` é cópia literal de `retire_error_has_budget`.

## Goals / Non-Goals

**Goals:**

- Uma categoria participa de quantos orçamentos o usuário quiser, sem aviso e sem confirmação.
- O seletor do formulário fica com **uma** regra: continuidade da escolha já feita.
- A mensagem de recusa de exclusão deixa de pressupor um único orçamento, nos dois idiomas.
- A duplicata morta some, para que a mensagem tenha origem única.

**Non-Goals:**

- **Mostrar, a partir de uma categoria, quais orçamentos a vigiam.** Hoje nenhuma tela responde isso — `ViewCategoryModal` não menciona orçamento —, e com no máximo um a ausência já existia. Com N ela fica mais sentida, mas é tela nova, não consequência desta remoção. Decidido fora de escopo.
- **Redigir `recurring_retire_error_has_budget`.** Ela também pressupõe um único orçamento (*"Um orçamento usa esta recorrência como receita base"*), e **já** está ambígua hoje: nada impede N orçamentos elegerem a mesma recorrência, e `hasBudgetForRecurring` é booleano. É o mesmo defeito de redação, mas anterior a esta mudança e não causado por ela.
- **Qualquer agregação entre orçamentos.** Nenhuma existe, e a sobreposição é exatamente a leitura que a tornaria errada.
- **Migração de banco.** Nenhum dado muda de forma; nenhum orçamento existente se torna inválido.

## Decisions

### D1 — A trava é removida na origem, não relaxada no comportamento

`offeredCategories` perde o parâmetro `otherBudgetCategoryIds` e o `filterNot`. A alternativa considerada — manter o parâmetro e passar `emptySet()` — foi descartada: deixaria no código uma junta que só serve para desligar uma regra que não existe mais, e o próximo leitor teria de descobrir sozinho que ela nunca é preenchida.

Removido o filtro, a função fica idêntica em forma a `offeredRecurrings`, que está logo abaixo dela:

```kotlin
internal fun offeredCategories(open: List<Category>, selected: List<Category>) =
    withAlreadyChosen(offered = open, chosen = selected, id = Category::id)
```

Fundi-las numa só foi considerado e recusado: `withAlreadyChosen` **já é** essa unificação, e as duas funções nomeadas são o que dá a cada seletor o seu vocabulário — categoria e recorrência — sem esconder qual escolha está sendo feita.

### D2 — `observeAllBudgets` sai da leitura do formulário, e o repositório permanece injetado

O braço existia unicamente para montar `budgetedCategoryIds`. Sem ele o `combine` de `uiState` cai de quatro para três braços, e o formulário deixa de reagir a mudanças em outros orçamentos — que é o correto, porque nada do que ele apresenta depende delas.

`budgetRepository` continua sendo dependência do ViewModel: `submit()` o usa para `insert`/`update`.

### D3 — O KDoc é reescrito, não emendado

`BudgetFormViewModel.kt:341-345` afirma que a categoria pertence a no máximo um orçamento. A afirmação deixa de valer inteira, e o texto passa a descrever o estado atual — o que a função oferece e por quê — sem narrar que existiu uma trava. É a regra de KDoc do projeto: quem lê sem conhecer a história entende o mesmo que quem a viveu.

### D4 — `AccountError.HAS_BUDGET` é apagado, e não sincronizado

Duas alternativas foram pesadas. Sincronizar o texto das duas strings mantém a duplicata viva e garante que a próxima reescrita esqueça uma delas. Apagar o valor do enum, o seu ramo em `toUiText` e a chave nos dois `strings.xml` deixa a mensagem com origem única — que é o que o requisito de recusa pede.

Apagar encosta em `core/model/AccountError`, fora de orçamentos, e é por isso uma decisão consciente e não um efeito colateral: o valor é inalcançável (nenhum produtor), o seu `message` descreve categoria dentro do enum de conta, e o `when` de `toUiText` é exaustivo — remover valor e ramo juntos é consistente por construção, e o compilador acusa qualquer referência esquecida.

### D5 — A redação da recusa é neutra quanto à contagem

`retire_error_has_budget`, nos dois idiomas:

```
pt  Esta categoria está em uso por um ou mais orçamentos.
    Remova-a de todos antes de excluir.

en  This category is used by one or more budgets.
    Remove it from all of them before deleting.
```

Alternativas descartadas: `orçamento(s)`, que é notação de formulário e não de frase; e o plural genérico *"é usada por orçamentos"*, que soa estranho quando é exatamente um. `um ou mais` é explícito e correto nos dois casos, sem exigir que o app conte quantos são para escolher a mensagem — contar seria estado novo atravessando a fronteira do erro tipado, por um ganho de redação que não o justifica.

### D6 — O teste da subtração é invertido, não apagado

`OfferedCategoriesTest:24-31` afirma hoje que a categoria de outro orçamento é subtraída. Ele passa a afirmar o contrário — que ela continua oferecida —, mantendo a cobertura no mesmo ponto em vez de deixar a nova regra sem teste. Os três casos de continuidade de arquivada (`:34-65`) não são tocados: eles testam a única regra que sobreviveu.

## Risks / Trade-offs

**O usuário pode montar orçamentos que se sobrepõem sem perceber, e ler a mesma despesa duas vezes como se fossem duas.** → Não há mitigação de produto nesta mudança, e a ausência é deliberada: um aviso reintroduziria a exclusividade como desaconselhamento, que o requisito proíbe explicitamente. O que a torna aceitável é que nenhuma leitura soma orçamentos entre si — cada barra é uma resposta contra o seu próprio limite, e a repetição só seria erro numa agregação que não existe. A tela que responderia "quais orçamentos vigiam esta categoria" é a mitigação real, e está registrada como Non-Goal.

**Remover um valor de enum público em `core/model` pode quebrar consumidor não mapeado.** → `AccountError` é consumido dentro do próprio repositório de código, e o `when` de `toUiText` é exaustivo: qualquer referência remanescente falha na compilação, não em runtime. O risco é de build quebrado, não de defeito silencioso.

**Duas chaves de string somem/mudam e um idioma pode ficar para trás.** → As duas edições são simétricas por construção — `values` e `values-en` na mesma alteração —, e a regra do projeto é que uma chave presente em só um dos arquivos é bug. Verificação por leitura dos dois arquivos ao final.
