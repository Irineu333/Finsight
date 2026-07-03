# Arquitetura de Features: api/impl

> Este diretório abriga os módulos de feature do projeto, organizados no padrão **api/impl**.
> Este documento define a estrutura, as regras de dependência e o padrão de entry point.
> Toda nova feature deve seguir estas regras — elas são impostas pelos convention plugins do `build-logic`.

---

## Estrutura

Cada feature é um par de módulos Gradle:

```
feature/
└── <nome>/
    ├── api/    ← contratos públicos da feature
    └── impl/   ← implementação completa da feature
```

| Módulo | Contém | Não contém |
|---|---|---|
| **api** | Rotas de navegação (data classes), interfaces de repositório, interfaces de use cases públicos, entry point de UI (`<Nome>Entry`) | Qualquer implementação |
| **impl** | Telas, ViewModels, modais, use cases (públicos e privados), implementações de repositório, mappers, módulo Koin da feature | Tipos consumidos por outras features |

**Critério de triagem:** só entra na `api` o que **outro módulo consome**. Tudo o mais é detalhe de implementação e vive no `impl`. Na dúvida, comece no `impl` — promover para a `api` depois é barato; o inverso quebra consumidores.

---

## Regras de dependência

1. **api não depende de api**
2. **impl não depende de impl**
3. **api não depende de impl**
4. **A direção natural é: impl depende de api** (de qualquer feature) **e de `:core:*`**

As regras produzem uma **topologia estrela**: as apis só enxergam o core, e os impls
cruzam livremente para qualquer api. Ciclos entre features tornam-se *impossíveis por
construção* — não existe caminho de volta.

```
                ┌────────────────────────────────┐
                │   :core:model   :core:common   │
                │   (kernel compartilhado)       │
                └──────────────▲─────────────────┘
          ┌──────────┬────────┴──┬───────────┐
    ┌─────┴────┐ ┌───┴────┐ ┌────┴───┐ ┌─────┴──┐
    │trans:api │ │acct:api│ │card:api│ │ rec:api│   ← nenhuma seta entre elas
    └─────▲────┘ └───▲────┘ └────▲───┘ └─────▲──┘
          │          │           │           │
     ═════╪══════════╪═══════════╪═══════════╪═════
          │  (impls cruzam livremente para qualquer api)
    ┌─────┴────┐ ┌───┴─────┐ ┌───┴────────────┐
    │trans:impl│ │card:impl│ │ dashboard:impl │ ...
    └──────────┘ └─────────┘ └────────────────┘
```

Exemplo real: `transactions:impl → creditcards:api` e `creditcards:impl → transactions:api`
coexistem sem ciclo, porque as apis não se enxergam.

### Dependências permitidas por tipo de módulo

| De \ Para | `:core:*` | `feature:*:api` | `feature:*:impl` |
|---|---|---|---|
| **feature:\*:api** | ✅ | ❌ | ❌ |
| **feature:\*:impl** | ✅ | ✅ (qualquer) | ❌ |
| **:composeApp** (shell) | ✅ | ✅ | ✅ (é o agregador) |

O `:composeApp` é o único módulo que enxerga os `impl` — é ele quem faz o wiring do Koin,
registra os grafos de navegação e configura o framework iOS.

---

## Domínio compartilhado

Os modelos de domínio (`Transaction`, `Account`, `Invoice`...) e os tipos de erro vivem em
`:core:model`, **não** nas apis das features. Motivo: os agregados são emaranhados
(`Transaction` embute `Account`, `CreditCard`, `Invoice` e `Category`), e qualquer api que
os mencionasse em assinaturas arrastaria os demais — violando a regra 1.

Chamamos isso de **dependência cruzada de domínio público**: modelos que precisariam
existir simultaneamente na api de várias features. O kernel compartilhado (`:core:model`)
mitiga o problema por ora. O split do domínio por feature é uma evolução futura, com duas
saídas possíveis (a decidir quando chegar a hora):

- **Referência por ID** — `Transaction.accountId: Long` em vez de `account: Account`;
- **Kernel mínimo permanente** — `:core:model` permanece apenas com os agregados emaranhados.

> A regra "api não depende de api" descarta deliberadamente a terceira opção
> (modelos nas apis referenciando-se entre si). Restrição intencional.

---

## Padrão de entry point

Todo acesso a recursos de UI de outra feature passa por **entry point** — nunca por
import direto de composable, modal ou ViewModel de outro `impl` (as regras de dependência
já impedem isso; o entry point é o caminho sancionado).

Cada `api` expõe uma interface única agrupando a superfície pública de UI da feature:

```kotlin
// feature/creditcards/api
interface CreditCardsEntry {
    fun payInvoiceModal(invoiceId: Long): Modal
    fun creditCardFormModal(creditCardId: Long? = null): Modal
}
```

O `impl` implementa e registra no módulo Koin da feature:

```kotlin
// feature/creditcards/impl
internal class CreditCardsEntryImpl(...) : CreditCardsEntry { ... }

val creditCardsModule = module {
    single<CreditCardsEntry> { CreditCardsEntryImpl(...) }
    // viewModels, use cases, repositórios...
}
```

O consumidor injeta a interface e usa via `ModalManager`:

```kotlin
// feature/dashboard/impl — enxerga apenas creditcards:api
val entry = koinInject<CreditCardsEntry>()
modalManager.show(entry.payInvoiceModal(invoice.id))
```

### Os três tipos de acesso cross-feature

| Acesso | Mecanismo |
|---|---|
| **Navegação** | Rota (`@Serializable`) vive na `api`; consumidor navega por rota. O registro do `NavGraph` é feito pelo `impl` e agregado pelo `:composeApp` |
| **Modais** | Método no entry point retornando `Modal` (tipo de `:core:designsystem`) |
| **Composable embutido** | Método no entry point retornando conteúdo `@Composable` — caso raro; só se surgir necessidade real |

### Mecanismo de registro de navegação

Cada `impl` expõe uma **extension `NavGraphBuilder.<feature>Graph(navController)`** que registra os
`composable<Rota>` da feature (as telas permanecem `internal` ao `impl`). O `:composeApp` — único
módulo que enxerga os `impl` — agrega essas extensions no `AppNavHost`:

```kotlin
// feature/support/impl — ui/navigation/SupportGraph.kt
fun NavGraphBuilder.supportGraph(navController: NavController) {
    composable<SupportRoute> { SupportScreen(...) }
    composable<SupportIssueRoute> { ... }
}

// :composeApp — AppNavHost
NavHost(...) {
    // rotas do shell (Home, abas)...
    supportGraph(navController)
}
```

As rotas (`SupportRoute`, `SupportIssueRoute`) vivem na `api`, então qualquer feature navega para
elas sem depender do `impl` de destino. *Alternativa descartada:* registrar grafos via Koin —
indireção desnecessária, já que o shell enxerga os `impl` por definição.

> **Entry point é opcional.** Uma feature só declara `<Nome>Entry` quando **outra** feature consome
> UI dela (modal/composable). O piloto `support` não expõe modal a terceiros (seu modal é interno),
> então **não** declara entry point — apenas rotas na `api`.

### Entry point vs. `:core:ui`

Nem todo componente visual compartilhado precisa de entry point:

- **Tem wiring próprio** (ViewModel, use cases)? → pertence a uma feature; acesso **via entry point**.
- **Apenas renderiza modelos do core** (ex.: `AccountSelector`, `OperationCard`)? → componente
  compartilhado; vive em **`:core:ui`** e é importado diretamente.

As assinaturas dos entry points só referenciam tipos do core (`:core:model`,
`Modal` de `:core:designsystem`) — o que preserva a topologia estrela.

---

## Notas de plataforma

- Todos os módulos de feature declaram os targets KMP (Android, iOS, Desktop), mas a
  regra é código `commonMain` puro. Source sets de plataforma no `impl` são exceção
  justificada (ex.: `report:impl`, com serviços nativos de print/share).
- No framework iOS (configurado no `:composeApp`), apenas `:core:*` e `feature:*:api`
  são exportados (`export()`); os `impl` são linkados, mas invisíveis ao Swift.
