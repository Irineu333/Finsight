# Dashboard Edit Mode — General Issues

## Issue 1 — Dashboard vazia não permitia voltar ao modo edição

**Severidade:** Alta — resolvida

**Descrição:**
Depois que o usuário removia todos os componentes da dashboard e confirmava a edição, a tela ficava vazia e sem qualquer affordance para reabrir o modo de edição. Na prática, a dashboard entrava em um estado sem saída pela UI.

**Causa raiz:**
O ponto de entrada para `EnterEditMode` existia apenas na interação com componentes renderizados:

```kotlin
DashboardComponentContent(
    variant = variant,
    modifier = Modifier
        .fillMaxWidth()
        .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
)
```

Quando `state.components` ficava vazio, o `DashboardViewingContent` continuava renderizando apenas a `LazyColumn`, mas sem nenhum item dentro dela. Isso preservava a regra de "entrar em edição interagindo com um componente", porém deixava de existir qualquer componente com o qual interagir.

**Correção:**
Promover a dashboard vazia para um estado explícito da tela, em vez de tratá-la como um branch interno de `Viewing`. O `DashboardViewModel` agora emite `DashboardUiState.Empty` quando a composição resultante não tem componentes, e a `DashboardScreen` renderiza um conteúdo específico para esse estado:

```kotlin
if (ordered.isEmpty()) {
    DashboardUiState.Empty(
        yearMonth = today.yearMonth,
        accounts = accounts,
        creditCards = creditCards,
    )
} else {
    DashboardUiState.Viewing(...)
}
```

**Decisão de produto incorporada na solução:**
- Não bloquear a remoção de todos os componentes
- Não expandir o gesto de entrada em edição para qualquer área vazia da tela
- Modelar a dashboard vazia como um estado distinto do `UiState`, preservando a arquitetura da tela e a consistência do fluxo normal

**Resultado:**
- A dashboard pode continuar intencionalmente vazia
- O usuário mantém um caminho claro para voltar ao modo edição
- O comportamento padrão de `long press` em componentes continua inalterado quando há conteúdo

## Issue 2 — Dashboard saindo do modo edição com configuration changes (Android)

**Severidade:** Média — resolvida

**Descrição:**
No Android, ao rotacionar a tela ou disparar qualquer outra mudança de configuração enquanto a dashboard estava no modo edição, a tela voltava automaticamente para o modo de visualização, perdendo as alterações não confirmadas.

**Causa raiz:**
A `DashboardScreen` utilizava um `DisposableEffect` que disparava o cancelamento da edição quando o composable era descartado:

```kotlin
DisposableEffect(viewModel) {
    onDispose {
        viewModel.onAction(DashboardAction.CancelEdit)
    }
}
```

Embora o `DashboardViewModel` sobreviva a mudanças de configuração (por ser um `viewModel` do Koin), o `DisposableEffect` é disparado durante a recriação da `Activity`, forçando o estado de volta para `Viewing`.

**Correção:**
Remover o `DisposableEffect` que forçava o `CancelEdit` no `onDispose`. Como o `DashboardViewModel` preserva o estado `_editingState` corretamente durante a mudança de configuração, a UI agora restaura o estado de edição após a recriação.

**Decisão de produto incorporada na solução:**
- Preservar o progresso do usuário durante mudanças de configuração.
- Permitir que o modo edição sobreviva à navegação entre abas da `HomeScreen` (Dashboard/Transações), permitindo que o usuário consulte informações em outra tela sem perder sua configuração em andamento.
- O cancelamento ou confirmação continua sendo explícito através dos botões na TopAppBar.
