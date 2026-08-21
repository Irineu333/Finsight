# 029 — Nenhuma mensagem de `RecurringError` chega ao usuário: a tela mostra uma genérica

**Área:** recurring (UI) · **Tipo:** UX · **Criticidade:** BAIXA · **Status:** aberto
**Verificado em:** 2026-08-19, `feature/local-mcp-server`, durante a revisão da correção da
[025](archive/025-confirm-recurring-writes-the-wrong-direction.md)

## O que está errado

`RecurringError` tem oito membros e um `toUiText()` completo, com as oito chaves em pt e en
(`core/model` — `domain/error/RecurringError.kt:66-75`). **Nenhuma delas é renderizada em lugar
nenhum.** A feature inteira não consome a função:

```
grep -rn "toUiText" feature/recurring/    # nenhum resultado
```

O que a sheet faz com qualquer recusa é mostrar a mesma frase:

| Arquivo | Linha | O que mostra |
|---|---|---|
| `ConfirmRecurringViewModel.kt` | `:219-222` | `onLeft { crashlytics.recordException(it); showError(retire_action_error_generic) }` |
| `SkipRecurringViewModel.kt` | `:41` | idem |

Então o usuário que confirma um ciclo e é recusado — por valor não positivo
([001](archive/001-create-transaction-accepts-negative-amount.md)), por moeda divergente, ou agora
por categoria do sentido oposto ([025](archive/025-confirm-recurring-writes-the-wrong-direction.md)) —
lê a mesma coisa nos três casos, e ela não diz nada sobre nenhum deles.

## Por que isso não é só "falta uma string"

A convenção de Error Types (`CLAUDE.md`) manda cada erro trazer `message` para log e `toUiText()`
para a tela, e ela foi cumprida oito vezes. O que não existe é o consumidor. O efeito é que **cada
correção que acrescenta uma recusa a este domínio escreve uma mensagem que ninguém vai ler**, e
passa na revisão porque a convenção está cumprida.

Outras features consomem as suas: `ArchiveCurrencyViewModel.kt:22`, `DeleteCurrencyViewModel.kt:21`,
`CurrencyFormViewModel.kt:104`, `AddInstallmentViewModel.kt:246`, `DeleteCreditCardViewModel.kt:43`,
`CreditCardFormViewModel.kt:226`. `recurring` é a exceção, não o padrão da casa.

## Cenário de falha

Um template legado classificado sob uma categoria do sentido oposto (situação da
[026](026-incoherent-templates-already-stored-have-no-migration.md), sem migração). A sheet abre e
**oferece** a categoria incoerente, porque `offeredCategories`
(`ConfirmRecurringViewModel.kt:263-274`) devolve a seleção à lista. O usuário confirma. O domínio
recusa, corretamente, e a tela responde a frase genérica. Não há nada — nem na tela, nem na sheet —
que aponte para a categoria. O caminho de saída existe (trocar a categoria) e é invisível.

## Correção sugerida

Uma camada, e é a que já existe em seis outras telas:

- `ConfirmRecurringViewModel` e `SkipRecurringViewModel` mapeiam `RecurringException` para
  `error.toUiText()` e mantêm o genérico como o ramo de qualquer outro `Throwable`. `is XxxException
  -> error.toUiText()` é a forma que `DeleteCreditCardViewModel.kt:43` e
  `AddInstallmentViewModel.kt:246` já usam.
- `crashlytics.recordException` permanece: uma recusa de domínio prevista não deixa de ser
  interessante no log, e a decisão de reportar ou não é separada da de exibir.

O teste tem de olhar o `UiText` que chegou ao `ModalManager`, não o fato de ter havido erro — uma
asserção sobre "mostrou um erro" passa com a mensagem genérica, que é exatamente o estado atual.

## Relacionado

A [026](026-incoherent-templates-already-stored-have-no-migration.md) é o que torna este caminho
alcançável em dados reais, e o item "a sheet ainda oferece o que o domínio recusa" está registrado
no fim da [025](archive/025-confirm-recurring-writes-the-wrong-direction.md).
