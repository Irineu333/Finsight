## Why

Na tela de cartões, deslizar o pager de volta para uma página já visitada não atualiza nada abaixo dele: a fatura, os botões de ação e a lista de transações continuam descrevendo o cartão anterior. A tela passa a exibir dois cartões ao mesmo tempo — um no pager, outro em todo o resto.

A causa está em `CreditCardsScreen.kt:417-425`:

```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { pagerState.currentPage }.collect {
        if (pagerState.currentPage != selectedIndex) {   // ← valor congelado
            onSelectCard(pagerState.currentPage)
        }
    }
}
```

`selectedIndex` é um parâmetro `Int` comum, não um `State`. O lambda captura o **valor** vigente quando o efeito foi lançado e, como a chave é `Unit`, o efeito nunca é relançado — a comparação passa a ser sempre contra o índice inicial, não contra a seleção corrente. Toda vez que o usuário volta para essa página, a guarda entende que "nada mudou" e a notificação é engolida:

```
        pager        selectedIndex capturado = 0        ViewModel
 ────────────────────────────────────────────────────────────────
 abre    página 0    0 != 0 → nada                      card 0  ok
 →       página 1    1 != 0 → SelectCard(1)             card 1  ok
 ←       página 0    0 == 0 → NADA                      card 1  divergiu
 →       página 1    1 != 0 → SelectCard(1)             card 1  sem efeito
```

Com `initialCreditCardId` apontando para um índice diferente de zero, o índice "morto" é justamente o do cartão de entrada.

A tela de contas — mesmo pager, mesma modelagem de `selectedIndex` no ViewModel — não tem essa guarda e por isso funciona (`AccountsScreen.kt:436-444`): ela notifica toda mudança de página, deduplicando com `distinctUntilChanged()`. Duas telas com o mesmo contrato divergiram na implementação, e uma delas quebrou.

A ordenação dos cartões não é o problema: `observeAllCreditCards()` e `getAllCreditCardsList()` usam a mesma query (`ORDER BY cc.createdAt ASC`), então a tradução `índice → id` feita em `CreditCardsViewModel.kt:171-175` é consistente.

## What Changes

- **O pager de cartões passa a notificar toda mudança de página**, alinhando-se ao de contas: a guarda contra `selectedIndex` sai e a deduplicação fica por conta de `distinctUntilChanged()` sobre o `snapshotFlow`, que é a única comparação correta — ela é feita contra o valor anterior *do próprio pager*, não contra um estado externo capturado.
- **O bloco comentado do efeito inverso** (pager seguindo `selectedIndex`, `CreditCardsScreen.kt:427-431`) é removido. Ele não está em uso, e a guarda que sobrou dele é a causa do defeito.
- **Nenhuma mudança de ViewModel, domínio, razão, banco ou DI.** O `CreditCardsViewModel` já reage corretamente a `SelectCard`; ele só não estava sendo chamado.

### Non-goals

Sincronizar o pager quando a seleção muda **por fora** dele — cartão selecionado arquivado ou excluído com a tela aberta, deep link chegando com a tela já composta. Nem contas nem cartões cobrem isso hoje; tratar esse caso exige reintroduzir o sentido ViewModel → pager, com o ciclo que ele traz, e é decisão separada desta correção.

## Capabilities

### New Capabilities
- `pager-selection`: o contrato entre um `HorizontalPager` que governa o escopo de uma tela e o estado que descreve esse escopo — quem é a fonte da verdade, quando a mudança de página é notificada e o que a tela garante sobre a coerência entre a página exibida e o restante do conteúdo.

### Modified Capabilities
<!-- Nenhuma. Nenhum requisito existente falava sobre a seleção via pager. -->

## Impact

- `feature/creditcards/impl/.../ui/screen/creditCards/CreditCardsScreen.kt` — `CreditCardPager`: a guarda sai, entra `distinctUntilChanged()`; o bloco comentado é removido.
- Sem impacto em domínio, razão, banco, DI ou em qualquer outro módulo.
