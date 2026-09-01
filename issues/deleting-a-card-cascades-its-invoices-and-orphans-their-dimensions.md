---
area: creditcards
severity: low
type: data
---

# Excluir um cartão leva as faturas pelo `CASCADE` e deixa as dimensões delas órfãs

## Invariante

Toda linha de `dimensions` é de propriedade de uma fachada viva: quem a emite na criação a
remove na remoção, na mesma transação de escrita.

A regra é normativa e tem dono declarado — `core/ledger/README.md`, seção "Dimensões: ciclo de
vida": "A fachada é dona da sua dimensão. Ela **emite** na criação e **remove** na remoção, na
mesma transação de escrita." O `CLAUDE.md` repete a mesma frase.

Hoje é falso no caminho de exclusão de cartão: a fatura some por `CASCADE` do banco, e a
dimensão dela fica.

## Mecânica

Um cartão nasce com fatura aberta — `AddCreditCardUseCase.invoke()` chama `OpenInvoiceUseCase`
como parte da criação, e `InvoiceRepository.insert()` emite uma `DimensionKind.INVOICE` junto
com a linha de `invoices`.

`InvoiceEntity` declara `ForeignKey(entity = CreditCardEntity, childColumns = ["creditCardId"],
onDelete = ForeignKey.CASCADE)`. Então `CreditCardRepository.delete()` — que executa
`dao.delete(...)` seguido de `accountDao.delete(...)` — apaga as faturas do cartão pelo
`CASCADE`, sem passar por `InvoiceRepository.deleteById()`, que é o **único** ponto do app que
remove a dimensão de uma fatura (`dimensionId?.let { dimensionDao.deleteById(it) }`).

`invoiceRepository.deleteById()` só tem um chamador em produção: `DeleteFutureInvoiceUseCase`.
O caminho do cartão não passa por lá.

`DeleteCreditCardUseCase` recusa cartão com movimento (`entryRepository.hasEntries(accountId)`)
e cartão usado por recorrência, mas **não** recusa cartão com fatura — e não tem por quê: todo
cartão tem pelo menos uma, desde o instante em que existe.

## Evidência

- `feature/creditcards/impl/.../usecase/AddCreditCardUseCase.kt` — `invoke()` abre a primeira
  fatura como parte da criação
- `feature/creditcards/impl/.../repository/InvoiceRepository.kt` — `insert()`:
  `dimensionDao.emit(DimensionKind.INVOICE)`; e `deleteById()`, o único remove-dimensão
- `feature/creditcards/impl/.../repository/CreditCardRepository.kt` — `delete()`: apaga cartão e
  conta, nunca dimensão
- `core/database/.../entity/InvoiceEntity.kt` — `onDelete = ForeignKey.CASCADE` em `creditCardId`
- `core/database/schemas/.../14.json` — `invoices`: `FOREIGN KEY(creditCardId) REFERENCES
  credit_cards(id) … ON DELETE CASCADE`
- `core/ledger/README.md`, "Dimensões: ciclo de vida" — a regra que isso viola

Verificado em execução: sonda temporária sobre o `AppDatabase` real (Room +
`BundledSQLiteDriver`), inserindo conta `LIABILITY` + cartão + dimensão `INVOICE` + fatura e
repetindo exatamente o que `CreditCardRepository.delete()` faz. Resultado com
`PRAGMA foreign_keys = 1`: `credit_cards = 0`, `invoices = 0`, `accounts = 0`, **`dimensions = 1`**.
A sonda foi apagada.

## Consequência

Linhas de `dimensions` que nenhuma fachada nomeia se acumulam, uma por fatura de cada cartão
excluído. Nenhum número do razão muda e nenhuma tela mostra nada errado: como
`DeleteCreditCardUseCase` exige cartão sem movimento, nenhuma perna carrega essas dimensões,
`verifyNoOrphanDimensions` (que checa `entries → dimensions`) continua verdadeiro, e `dimensions`
não tem listagem.

O que se perde é a invariante em si — e com ela a garantia de que "dimensão sem dono é
impossível" possa ser usada como premissa por quem escrever a próxima leitura por dimensão.

## Sugestão

Fazer `CreditCardRepository.delete()` remover as dimensões das faturas do cartão dentro do mesmo
`immediateTransaction`, ou trocar o `CASCADE` de `invoices.creditCardId` por uma remoção
explícita que passe por `InvoiceRepository.deleteById()`. Não vinculante.
