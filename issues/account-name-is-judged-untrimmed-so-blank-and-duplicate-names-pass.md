---
area: accounts
severity: medium
type: data
---

# O nome da conta é julgado sem `trim`, então nome em branco e duplicata passam

## Cenário

**DADO** uma conta existente
**QUANDO** o usuário edita o nome para `"   "` (só espaços)
**ENTÃO** a validação devolve válido, o botão habilita, `Account.init` passa — porque
`"   ".isNotEmpty()` é verdadeiro — e a conta é gravada sem nome, aparecendo em branco em
toda listagem, seletor e histórico
**DEVERIA** recusar com `EMPTY_NAME`

**DADO** a criação de uma conta com o mesmo `"   "`
**QUANDO** o usuário salva
**ENTÃO** `Account(name = name.trim())` estoura o `require` do `init`, o `Either.catch`
engole, e o botão não faz nada
**DEVERIA** dizer o motivo

**DADO** uma conta "Nubank"
**QUANDO** o usuário a edita para `"Nubank "` — aceito, porque a comparação trima só a
entrada e o `ignoreId` é a própria conta — e depois cria outra "Nubank"
**ENTÃO** a segunda é aceita: o armazenado `"Nubank "` nunca casa com `"Nubank"`
**DEVERIA** recusar com `ALREADY_EXIST`

## Mecânica

`ValidateAccountNameUseCase` decide vazio com `isEmpty()`, compara unicidade contra
`name.trim()` e devolve `name.right()` — sem trim. A normalização acontece em um só
lugar, fora dele: `CreateAccountUseCase` monta `Account(name = name.trim())`. O caminho de
edição não normaliza em lugar nenhum.

O `init` de `Account` documenta que é uma última linha de defesa e não a validação que o
usuário vê — e é justamente por isso que ele usa `isNotEmpty()` e não segura o branco.

As duas features vizinhas já resolveram isso, e a de categorias explica exatamente este
bug no comentário: *"Trim once, at the boundary: empty and uniqueness must judge the same
string"*. Contas é a divergente, não a norma.

## Evidência

- `feature/accounts/impl/.../usecase/ValidateAccountNameUseCase.kt` — `invoke()` com
  `if (name.isEmpty())` e `return name.right()`; `hasDuplicateName()` comparando contra
  `name.trim()`; há um `// TODO: improve this` no corpo
- `feature/accounts/impl/.../usecase/CreateAccountUseCase.kt` — `Account(name = name.trim())`
  dentro de `catch {}`
- `core/ledger/.../model/Account.kt` — `init`, `require(name.isNotEmpty())` e o KDoc que o
  declara guarda de último recurso
- `feature/accounts/impl/.../accountForm/AccountFormViewModel.kt` — `submit()`, ramo de
  edição: `it.copy(name = name, ...)` sem trim
- contraste: `feature/categories/impl/.../usecase/ValidateCategoryNameUseCase.kt` — trima
  na entrada e devolve o trimado; `feature/budgets/impl/.../ValidateBudgetTitleUseCase.kt`
  usa `isBlank()`
- existe `ValidateCategoryNameUseCaseTest`; não existe equivalente para contas

## Consequência

Conta anônima gravada e permanente, duplicata de nome que a própria regra diz impedir, e um
submit que falha sem dizer nada.

## Sugestão

Trimar na fronteira do `ValidateAccountNameUseCase` e devolver o trimado, como categorias.
A parte muda do submit é a mesma de
`a-refused-write-says-nothing-to-whoever-asked-for-it`. Não vinculante.
