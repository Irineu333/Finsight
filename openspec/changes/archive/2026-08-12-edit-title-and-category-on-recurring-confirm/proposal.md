## Why

A confirmação de uma recorrência já deixa o usuário ajustar **valor**, **data**, **conta**, **cartão** e **fatura** do ciclo — tudo o que varia de mês para mês — mas trata **título** e **categoria** como imutáveis: o modal os exibe em campos desabilitados (`ConfirmRecurringModal.kt:109-135`) e o caso de uso escreve `recurring.title` e `recurring.category` direto no `TransactionIntent` (`ConfirmRecurringUseCase.kt:95,107,118,131`). A consequência é que um ciclo que fugiu do padrão — a "Assinatura de streaming" que neste mês foi cobrada como outra coisa, a "Farmácia" que virou "Consulta" — só pode ser corrigido depois, editando a transação já lançada, ou pior, alterando o template e contaminando todos os ciclos seguintes.

A recorrência é um **modelo do ciclo**, não uma sentença sobre ele. O que o usuário confirma é uma transação real, e a classificação dessa transação é a informação que alimenta orçamentos e relatórios — ficar errada por falta de um campo editável é um custo desproporcional ao da mudança.

## What Changes

- O modal de confirmação passa a oferecer **título editável**, pré-preenchido com o título da recorrência.
- O modal passa a oferecer **seletor de categoria**, pré-selecionado com a categoria da recorrência, no lugar do campo desabilitado atual. O seletor oferece as categorias do **tipo** da recorrência (receita ou despesa) e permite **remover** a categoria, já que "sem categoria" é a ausência de dimensão e um estado legítimo do domínio.
- A edição vale **apenas para o ciclo confirmado**: a transação gerada leva o título e a categoria escolhidos, e o template da recorrência permanece intacto — a próxima confirmação volta a sugerir os valores originais. Não há propagação, nem opção de propagar.
- **Um campo de título apagado nunca volta em silêncio ao título do template**: a transação é gravada sem título e passa a ser exibida pela categoria, como qualquer outra sem título. `Confirmar` só é desabilitado quando título e categoria estão ambos vazios — a exigência que `RecurringForm.isValid()` já faz do template.
- `ConfirmRecurringUseCase` passa a aceitar `title` e `category` como parâmetros com *default* nos valores da recorrência — o comportamento atual continua sendo o de quem não os informa, e nenhum chamador existente quebra.
- Continuidade de categoria arquivada: uma categoria arquivada **depois** de ter sido escolhida pela recorrência continua sendo oferecida (marcada) para que o usuário possa mantê-la ou trocá-la; ela não volta a ser oferecida como escolha nova.

## Capabilities

### New Capabilities
- `recurring-confirmation`: o que o usuário pode ajustar ao confirmar um ciclo de recorrência, o que a confirmação escreve na transação e o que ela deixa intacto no template.

### Modified Capabilities
<!-- Nenhuma spec viva descreve hoje a confirmação de recorrência. -->

## Impact

- `feature/recurring/impl` — `ConfirmRecurringModal`, `ConfirmRecurringUiState`, `ConfirmRecurringAction`, `ConfirmRecurringViewModel`, `ConfirmRecurringUseCase`.
- `feature/recurring/impl/di/RecurringModule` — o view model passa a receber `ICategoryRepository`.
- `core/resources` — chaves novas de string para o campo de título da confirmação, em `values/strings.xml` (pt) e `values-en/strings.xml` (en).
- Testes: `ConfirmRecurringCurrencyTest` e os testes do modal/view model de confirmação; nenhuma migração de banco e nenhuma mudança no ledger.
- Sem impacto no template: `SaveRecurringUseCase` e `IRecurringRepository` não são tocados.
