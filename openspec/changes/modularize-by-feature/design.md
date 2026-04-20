## Context

O projeto é KMP (Android/iOS/Desktop) com Compose Multiplatform, Room, Koin e Firebase. Atualmente existe um único módulo `:composeApp` com ~50 use cases, ~12 repositórios, ~35 modais e ~13 screens organizados por camada (domain/, database/, ui/). A UI já está organizada por feature em `ui/screen/`, mas domínio e banco são planos.

Restrições relevantes:
- Room exige que todos os `@Entity` estejam visíveis ao `@Database` no momento da compilação via KSP — impossibilita entidades distribuídas por módulos sem multi-module Room (complexo em KMP)
- KSP em KMP requer 6 configurações por módulo (commonMainMetadata, Android, iOS x3, JVM)
- iOS produz um único framework estático — todos os módulos KMP são linked juntos

## Goals / Non-Goals

**Goals:**
- Isolar compilação: mudança em `:feature:transactions:impl` não recompila `:feature:accounts:impl`
- Estabelecer boundaries de domínio explícitos via módulos Gradle
- Eliminar dependências acidentais entre features
- Preparar base para build incremental real no Gradle

**Non-Goals:**
- Multi-module Room (cada feature com seu próprio banco) — complexidade não justificada agora
- Dynamic feature modules (carregamento lazy) — não se aplica a KMP
- Mudanças de comportamento do produto — refactoring estrutural puro

## Decisions

### D1: Padrão api/impl por feature

**Decisão:** Cada feature tem dois módulos: `:feature:X:api` (contrato público) e `:feature:X:impl` (implementação). Nenhum `:impl` depende de outro `:impl`.

**Rationale:** Isola compilação incremental e torna dependências cruzadas explícitas e verificadas em tempo de build.

**Alternativa considerada:** Módulo único por feature (sem separação api/impl). Mais simples, mas permite dependências impl-to-impl acidentais e reduz o ganho de build incremental.

---

### D2: Cada feature é seu próprio domínio

**Decisão:** Modelos de domínio, interfaces de repositório e interfaces de use cases cross-feature vivem no `:api` da feature dona, não num `:core:domain` centralizado.

**Rationale:** Evita um "deus módulo" que cresce sem controle. Cada feature define e expõe o que precisa.

**Alternativa considerada:** `:core:domain` centralizado. Mais simples inicialmente, mas se torna um gargalo de compilação e dilui boundaries.

---

### D3: Room centralizado em `:core:database`

**Decisão:** `AppDatabase`, todas as entities e DAOs ficam em `:core:database`. Mappers e repository implementations pertencem a cada `feature:X:impl` — migram junto com o domínio de cada feature nas seções 8–16.

**Rationale:** KSP Room em KMP multi-módulo é instável. O `@Database` precisa ver todos os `@Entity`. Centralizar entities e DAOs elimina esse problema. Mappers e repos ficam em `:impl` porque implementam contratos de domínio da feature e não são infraestrutura Room pura.

**Alternativa considerada (Opção A):** Entities em cada `:feature:impl`, `@Database` no `:app`. Possível com Room 2.6+ mas aumenta complexidade de build e tem bugs conhecidos em KMP com KSP cross-module.

**Caminho de melhoria:** Quando Room multi-module KMP estabilizar, migrar entities para cada `:feature:impl` e `@Database` para `:app`.

---

### D4: Quebra do ciclo recurring ↔ transactions

**Decisão:** `Recurring` define seu próprio `Recurring.Type { INCOME, EXPENSE }`, independente de `Transaction.Type`. O mapper em `:core:database` faz a conversão.

**Rationale:** `recurring:api` com dep em `transactions:api` e `transactions:api` com dep em `recurring:api` (via `OperationRecurring`) cria ciclo insolúvel no grafo de módulos Gradle.

**Evidência:** `RecurringEntity.Type` já existe como enum próprio com `{ EXPENSE, INCOME }` — o padrão já existe na camada de banco, só precisa subir para o domínio.

---

### D5: `Category.iconKey: String` e `Budget.iconKey: String`

**Decisão:** Remover `CategoryLazyIcon` (tipo Compose) dos modelos de domínio. Substituir por `iconKey: String`. O `CategoryLazyIcon` é construído na camada de UI a partir da key.

**Rationale:** `categories:api` não pode depender de Compose. Domínio não deve conhecer tipos de UI.

---

### D6: Convention plugins em `build-logic/`

**Decisão:** Três convention plugins Kotlin DSL cobrem todos os módulos:
- `kmp-library.gradle.kts` — targets KMP + Android library (para `:core:*` e `:feature:X:api`)
- `kmp-compose.gradle.kts` — aplica `kmp-library` + Compose (intermediário, usado por `kmp-feature`)
- `kmp-feature.gradle.kts` — aplica `kmp-compose` + Koin + Arrow + Navigation (para `:feature:X:impl`)

`:core:database` aplica `kmp-library` e configura Room/KSP manualmente no próprio `build.gradle.kts` — um plugin dedicado adicionaria complexidade desnecessária para um único módulo.

Módulos `:feature:X:api` usam `kmp-library` diretamente — são módulos KMP puros sem Compose ou Koin. Não há `kmp-feature-api` dedicado: `kmp-library` já atende sem sobrecarga.

**Rationale:** Com ~25 módulos, repetir a configuração KMP (targets, source sets) em cada `build.gradle.kts` é impraticável. Convention plugins eliminam a repetição.

---

### D7: Features terminais sem `:api`

**Decisão:** `dashboard`, `home` e `support` têm apenas `:impl`, sem módulo `:api`.

**Rationale:** Nenhum outro módulo depende deles. Criar `:api` vazio só adiciona indireção sem valor.

---

### D8: Use cases cross-feature via interface em `:api`

**Decisão:** Use cases usados por outras features expõem interface em `:api`. O `:impl` implementa. Koin faz o binding.

**Use cases cross-feature identificados:**
- `transactions:api` → `IBuildTransactionUseCase`, `ICalculateBalanceUseCase`
- `creditCards:api` → `IGetOrCreateInvoiceForMonthUseCase`
- `accounts:api` → `IEnsureDefaultAccountUseCase`

**Rationale:** Mantém a regra "nenhum impl vê outro impl". A feature consumidora depende da interface, o Gradle verifica em compile time.

---

### D9: Analytics events por feature ficam em `:impl`

**Decisão:** As subclasses de `Event` específicas de cada feature (ex: `CreateTransaction`, `EnterDashboardEditMode`) ficam em `:feature:X:impl`. Apenas `Analytics`, `Crashlytics` e a base `Event` ficam em `:core:analytics`.

**Rationale:** Nenhuma outra feature precisa conhecer os eventos de outra. São detalhes de implementação.

## Risks / Trade-offs

**[Build complexity]** → 25+ módulos aumentam o tempo de configuração do Gradle e a complexidade do `settings.gradle.kts`. Mitigação: convention plugins reduzem a superfície de erro. O ganho em build incremental compensa após a migração.

**[KSP + KMP]** → KSP em KMP ainda tem comportamentos inconsistentes entre versões do Kotlin/KSP. Mitigação: Room centralizado em `:core:database` limita a exposição a um único módulo com KSP Room.

**[Migração incremental com código em dois lugares]** → Durante a migração, código existirá tanto no `:composeApp` original quanto nos novos módulos. Pode causar confusão. Mitigação: migrar feature por feature, remover do `:composeApp` imediatamente após mover.

**[iOS framework linking]** → Cada módulo KMP adicionado precisa ser linked no framework estático do iOS. O tempo de linking pode aumentar. Mitigação: é custo único por módulo, não incremental por mudança de código.

## Migration Plan

Ordem incremental — cada etapa deve compilar e rodar antes de prosseguir:

1. **Preparação** — Criar `build-logic/` com convention plugins; criar estrutura de diretórios; atualizar `settings.gradle.kts`
2. **Breaking changes de domínio** — `Recurring.Type`, `Category.iconKey`, `Budget.iconKey` (ainda dentro do `:composeApp`)
3. **`:core:utils`** — mover extensões puras; sem Compose
4. **`:core:platform`** — mover `Platform`, `PlatformContext`
5. **`:core:analytics`** e **`:core:auth`** — interfaces + DI platform modules
6. **`:core:ui`** — mover componentes, theme, ModalManager, NavigationDispatcher
7. **`:core:database`** — mover Room; o `:composeApp` passa a depender de `:core:database`
8. **Features nível 0** (sem dep de outras features): `accounts`, `categories`, `creditCards` — api depois impl
9. **Features nível 1**: `installments`, `recurring`, `budgets`, `transactions`
10. **Features terminais**: `report`, `support`, `dashboard`, `home`
11. **`:app`** — `:composeApp` vira `:app`; só navigation + startKoin + entry points

**Rollback:** Cada etapa é um commit atômico. Reverter é `git revert` do commit da etapa.

## Open Questions

*(nenhuma — todas as decisões foram tomadas durante a fase de exploração)*