---
area: accounts
severity: medium
type: ux
---

# A tela de contas oferece excluir onde o domínio recusa, e não oferece arquivar

## Cenário

**DADO** uma conta sem nenhum lançamento, mas apontada por uma recorrência
**QUANDO** o usuário abre a tela de contas
**ENTÃO** o botão oferece **Excluir**; ao confirmar, a operação falha com `HAS_RECURRING` —
e como a tela nunca oferece **Arquivar** nesse caso, não há ali nenhuma forma de aposentar
a conta
**DEVERIA** oferecer arquivar, que o domínio aceitaria: a conta não tem saldo e não é a
padrão

## Mecânica

A oferta é derivada de dois fatos — `hasMovement` e `isDefault`. O domínio recusa por três:
conta padrão, ter lançamentos, e ter recorrência apontando para ela. O terceiro não tem
representação na decisão da tela, então a tela escolhe por um critério mais pobre que o de
quem decide.

É exatamente a inversão que o projeto evita em categorias, onde
`ResolveCategoryRetirabilityUseCase` é o dono único das guardas e alimenta a tela — de modo
que as duas não podem discordar. É a regra de derivação do `CLAUDE.md` aplicada num lugar e
não no outro.

## Evidência

- `core/ui/.../model/AccountRetireOffer.kt` — `accountRetireOfferOf(hasMovement, isDefault)`,
  que delega a `retireActionOf(hasMovement)`
- `feature/accounts/impl/.../accounts/AccountsViewModel.kt` — `hasMovement =
  entryRepository.hasEntries(account.id)` é o único insumo
- `feature/accounts/impl/.../usecase/DeleteAccountUseCaseImpl.kt` — as três guardas:
  `CANNOT_DELETE_DEFAULT`, `HAS_TRANSACTIONS`, `HAS_RECURRING`
- `feature/accounts/impl/src/commonTest/.../RetireAccountGuardsTest.kt` — `deleting an
  account a recurring still points at is refused`: a guarda de domínio é testada, a oferta
  da tela não
- contraste: `feature/categories/impl/.../usecase/ResolveCategoryRetirabilityUseCase.kt` e
  `feature/categories/impl/.../viewCategory/ViewCategoryViewModel.kt`

## Consequência

Um botão que promete uma ação e entrega um erro, e a ação que funcionaria fica inalcançável
a partir dessa tela.

## Sugestão

Um `ResolveAccountRetirabilityUseCase` espelhando o de categorias, consumido tanto pela
tela quanto pelo `DeleteAccountUseCase`. Não vinculante.
