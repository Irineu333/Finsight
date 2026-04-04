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
Sempre que um estado inicial é definido em múltiplos lugares (`defaultPreferences()` no editing, `if (isEmpty) return all` no viewing), há risco de divergência silenciosa. A regra é: um único lugar define o estado padrão e todos os outros o leem via `effectivePrefs` (ou equivalente). A solução evoluiu depois para distinguir explicitamente `null` (primeira abertura) de `emptyList()` (dashboard vazia salva), removendo a ambiguidade sem precisar de fallback por lista vazia.

---

## Issue 2 — Remover todos os componentes resetava a dashboard para o padrão

**Severidade:** Alta — resolvida

**Descrição:**
Ao remover todos os componentes da dashboard e confirmar a edição, a próxima renderização voltava para a composição padrão em vez de manter a dashboard vazia.

**Causa raiz:**
O sistema tratava `emptyList()` com dois significados incompatíveis:

1. **Primeira abertura sem preferências salvas**
2. **Dashboard intencionalmente vazia após o usuário remover tudo**

Como o `DashboardViewModel` fazia fallback com:

```kotlin
val effectivePrefs = preferences.ifEmpty { DashboardComponentRegistry.defaultPreferences() }
```

qualquer lista vazia era reinterpretada como "usar defaults", inclusive a lista vazia persistida após o usuário remover todos os componentes.

**Correção:**
Mudar o contrato do repositório para expor `StateFlow<List<DashboardComponentPreference>?>`, com semântica explícita:

- `null` = nenhuma preferência salva ainda
- `emptyList()` = dashboard vazia salva pelo usuário

Com isso, o ViewModel passa a fazer fallback apenas para `null`:

```kotlin
val effectivePrefs = preferences ?: DashboardComponentRegistry.defaultPreferences()
```

e o `buildEditingState` usa a mesma regra.

**Resultado:**
- Primeira abertura continua exibindo a dashboard padrão
- Dashboard vazia permanece vazia após confirmar a remoção de todos os componentes
- O modo edição e o modo visualização passam a compartilhar a mesma semântica de estado

---

## Issue 3 — Configuração de "dias à frente" em recorrentes pendentes tinha semântica errada

**Severidade:** Média — resolvida

**Descrição:**
O componente `PendingRecurring` expunha uma configuração chamada "Dias à frente" com opções como 7, 14 e 30, mas o comportamento real não mostrava recorrências futuras dentro dessa janela.

Na prática, os itens só começavam a aparecer a partir da data de vencimento, e a configuração apenas limitava por quantos dias um item já vencido continuava visível. Isso tornava a opção enganosa para o usuário e conflitava com a regra de negócio esperada para pendências.

**Causa raiz:**
O builder aplicava a configuração em cima da lista já produzida por `GetPendingRecurringUseCase`.

Esse use case já filtrava apenas recorrências:

- ativas
- sem ocorrência confirmada/ignorada no mês atual
- cujo dia efetivo no mês atual já tivesse chegado

Ou seja, a lista de entrada para o componente já continha somente pendências vencidas ou vencendo hoje.

Depois disso, o builder aplicava um segundo filtro:

```kotlin
val filtered = pendingRecurring.filter { recurring ->
    val effectiveDay = input.today.yearMonth.effectiveDay(recurring.dayOfMonth)
    input.today.day - effectiveDay <= daysAhead
}
```

Esse cálculo não media "quantos dias à frente faltam para vencer", e sim "quantos dias se passaram desde o vencimento". O nome do config e o efeito real estavam desalinhados.

**Correção:**
Separar explicitamente dois conceitos:

1. **Pendência:** recorrências vencidas ou vencendo hoje continuam visíveis até serem confirmadas ou ignoradas.
2. **Horizonte futuro:** uma nova configuração controla quantos dias à frente o componente também deve exibir recorrências ainda não vencidas.

O comportamento final ficou assim:

- pendências permanecem sempre visíveis até serem tratadas
- recorrências futuras entram opcionalmente dentro de uma janela configurável
- a configuração passa a usar a chave `upcoming_days_ahead`
- o default passa a ser `0` (`Hoje`)
- as opções passam a ser `Hoje`, `7 dias`, `15 dias` e `Este mês`

No builder, o componente agora combina:

- `pendingRecurring` vindas do use case
- `upcomingRecurring` calculadas a partir da lista total de recorrências ativas do mês ainda não tratadas

Depois disso, a lista é ordenada pelo dia efetivo de vencimento, o que faz mais sentido do que a ordem anterior por `createdAt`.

**Resultado:**
- A semântica do config passa a bater com o rótulo exibido ao usuário
- Pendências não desaparecem sozinhas por janela arbitrária de atraso
- O componente consegue funcionar tanto como lista de pendências quanto como preview do que está para vencer

**Observação sobre recorrência:**
Quando um config de UI descreve um conceito temporal, o nome da chave precisa refletir exatamente o eixo de filtragem. "Dias à frente" sugere janela futura; se a implementação estiver medindo dias desde o vencimento, o nome correto é outro. Misturar esses dois significados no mesmo config cria comportamento aparentemente "bugado" mesmo quando o código está executando como escrito.

---

## Issue 4 — Configuração salva sobrevivia ao reativar componente sem default explícito

**Severidade:** Média — resolvida

**Descrição:**
Algumas configurações de componentes continuavam valendo depois de desativar e reativar o componente. O comportamento era perceptível ao sair do modo edição e entrar novamente: componentes inativos deveriam voltar com a configuração padrão, mas certas chaves antigas reapareciam porque ainda existiam nas preferências salvas.

**Causa raiz:**
`DashboardComponentType.defaultConfig` não declarava todas as chaves relevantes de cada componente. Quando uma configuração era salva e depois o componente passava a depender do default ao ser restaurado, não havia valor default explícito para sobrescrever aquela chave.

Na prática, o fluxo acabava fazendo merge entre:

- o que existia em `defaultConfig`
- o que já estava salvo em `DashboardComponentPreference.config`

Se a chave não existia no default, o valor salvo sobrevivia por inércia.

**Correção:**
Preencher `DashboardComponentType.defaultConfig` com o conjunto completo de configurações iniciais de cada componente, incluindo chaves universais e específicas.

Exemplos do ajuste:

- `top_spacing` passou a existir explicitamente com `"false"` nos componentes em que antes estava implícito
- configs como `show_header`, `hide_when_empty`, `excluded_account_ids`, `excluded_card_ids`, `count`, `upcoming_days_ahead` e `hidden_actions` passaram a ter valor inicial definido no próprio tipo

**Resultado:**
- componentes ativos continuam restaurando preferências salvas
- componentes inativos voltam para o estado inicial definido pelo tipo
- configs antigas deixam de “vazar” quando a chave não existe mais implicitamente no fluxo de restore

**Observação sobre recorrência:**
Quando um `defaultConfig` é parcial, ele deixa de ser uma fonte de verdade e vira apenas um patch. Nesse cenário, qualquer valor persistido ausente no default tende a sobreviver sem intenção explícita. Para componentes configuráveis, o default precisa descrever o estado inicial completo, não só uma fração dele.
