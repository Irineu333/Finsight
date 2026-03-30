# Dashboard Edit Mode — Step 4: Known Issues

## Issue 1 — Configs padrão não refletiam na dashboard na primeira abertura

**Severidade:** Alta — resolvida

**Descrição:**
Os componentes que deveriam ter `top_spacing` habilitado por padrão (`accounts_overview`, `credit_cards_pager`, `spending_pager`, `pending_recurring`, `recents`, `quick_actions`) não exibiam o espaçamento superior na dashboard ao abrir o app pela primeira vez.

**Causa raiz:**
Dois problemas combinados:

1. **`defaultPreferences()` era consultado apenas no `buildEditingState`**, nunca no `viewingState`. Na primeira abertura, `preferences` é `emptyList()`. O `viewingState` usava `preferences` diretamente para construir `configByKey`:

   ```kotlin
   val configByKey = preferences.associate { it.key to it.config }
   ```

   Com `preferences` vazio, `configByKey` ficava vazio e nenhum config (incluindo `top_spacing`) era aplicado ao rendering.

2. **Falta de fonte de verdade única.** `applyPreferences` tinha seu próprio fallback independente:

   ```kotlin
   if (preferences.isEmpty()) return all
   ```

   Isso significava que ordem/visibilidade e config tinham comportamentos diferentes quando `preferences` estava vazio — ordem usava todos os componentes na sequência do builder, config usava mapa vazio. As duas responsabilidades não consultavam a mesma fonte.

**Correção:**
Introduzir `effectivePrefs` no `viewingState` como fonte de verdade única — `preferences` quando há dados salvos, `defaultPreferences()` caso contrário — e usar esse valor para **ambas** as responsabilidades:

```kotlin
val effectivePrefs = preferences.ifEmpty { DashboardComponentRegistry.defaultPreferences() }
val configByKey = effectivePrefs.associate { it.key to it.config }
// ...
val ordered = applyPreferences(effectivePrefs, allComponents)
```

O fallback `if (preferences.isEmpty()) return all` em `applyPreferences` foi removido por ser dead code.

**Observação sobre recorrência:**
Sempre que um estado inicial é definido em múltiplos lugares (`defaultPreferences()` no editing, `if (isEmpty) return all` no viewing), há risco de divergência silenciosa. A regra é: um único lugar define o estado padrão e todos os outros o leem via `effectivePrefs` (ou equivalente). Não inicializar no repositório foi uma escolha deliberada para evitar double-emit na inicialização do `StateFlow.Eagerly` (que causaria flash visual).
