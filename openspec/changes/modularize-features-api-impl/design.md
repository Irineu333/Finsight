# Design: Modularização por feature (api/impl)

> As regras de dependência e o padrão de entry point estão normatizados em `feature/README.md`.
> Este documento registra as decisões, o racional e o plano de migração.

## Context

Monólito `:composeApp` (KMP: Android, iOS, Desktop) com 430 arquivos organizados por camada (`domain/` 123, `database/` 45, `ui/` 225), DI Koin em 9 módulos por camada, Room com `AppDatabase` único, um `strings.xml` por idioma e navegação type-safe com sealed class `AppRoute` única. O Xcode integra via `:composeApp:embedAndSignAppleFrameworkForXcode` (XcodeGen, `iosApp/project.yml`).

Fatos levantados que condicionam o design:

- O domínio é um grafo: `Transaction` embute `Account`, `CreditCard`, `Invoice` e `Category` como objetos.
- Existem dependências bidirecionais entre features no nível de use cases: `BuildTransactionUseCase` (transactions) → `GetOrCreateInvoiceForMonthUseCase` (creditcards) e `AddInstallmentUseCase` (creditcards) → `BuildTransactionUseCase` (transactions).
- O Dashboard não embute UI de outras features: um único `DashboardViewModel` busca dados via use cases; o acesso cross-feature dele é só abrir modais e navegar.
- Código de plataforma é concentrado: entry points de app, `extension/`+`Platform.kt`, `database/`, trio Firebase (analytics/crashlytics/auth) e serviços de print/share do report. Todas as demais features são `commonMain` puro.
- `ModalManager` já desacopla modais (objetos empurrados a um manager).

## Goals / Non-Goals

**Goals:**
- Fronteiras entre features impostas pelo compilador e pelos convention plugins (não por disciplina).
- Ciclos entre features impossíveis por construção (topologia estrela).
- `:composeApp` reduzido a shell, preparado para o split futuro `:shared`/`:androidApp`/`:desktopApp`.
- Migração incremental: app compilando e testes verdes nos 3 targets ao fim de cada fase.
- Zero mudança no projeto Xcode nesta change.

**Non-Goals:**
- Split do domínio por feature — `:core:model` é kernel compartilhado nesta change (a "dependência cruzada de domínio público" fica documentada com as opções futuras: referência por ID ou kernel mínimo permanente).
- Split das strings por feature — `:core:resources` mantém `Res` único.
- Split do shell em `:shared`/`:androidApp`/`:desktopApp` — change futura; o único ponto de quebra será a task do Xcode.
- Mudanças de comportamento, UI ou schema de banco.

## Decisions

### D1 — api/impl por feature (não módulo único por feature)
As dependências bidirecionais reais (transactions ↔ creditcards) seriam ciclo irresolvível com módulos únicos, exigindo um terceiro módulo artificial. Com api/impl, `transactions:impl → creditcards:api` e `creditcards:impl → transactions:api` coexistem sem ciclo. *Alternativa descartada:* estilo Now-in-Android (core + feature único) — mais simples, mas não resolve o ciclo e não impõe fronteira de contrato.

### D2 — Regras de dependência: topologia estrela
(1) api não depende de api; (2) impl não depende de impl; (3) api não depende de impl; (4) impl depende de apis e `:core:*`. Mais forte que "grafo acíclico": ciclo torna-se impossível por construção, e as regras são verificáveis mecanicamente no convention plugin. Efeito de longo prazo aceito: descarta o split futuro de domínio via modelos em apis referenciando-se entre si.

### D3 — Domínio compartilhado em `:core:model`
Modelos e errors ficam em `:core:model`; interfaces de repositório e use cases públicos ficam na api da feature dona; use cases privados no impl. Critério de triagem: só entra na api o que outro módulo consome. *Alternativa descartada:* modelos por feature com referência por ID — refactor grande demais para acoplar a esta change.

### D4 — Room inteiro em `:core:database`
Entities, DAOs, `AppDatabase` e Converters ficam juntos (Room não compõe schema entre módulos de forma trivial). Implementações de repositório e mappers migram para o impl da feature dona, consumindo os DAOs.

### D5 — `:composeApp` permanece como shell
O Xcode chama `:composeApp:embedAndSignAppleFrameworkForXcode`; manter o módulo evita qualquer mudança no `project.yml`. Restam no shell: `App.kt`, `AppNavHost`, `AppNavigationDispatcher`, `HomeScreen`/`HomeRoute`, agregação Koin, entry points de plataforma e config do framework. Nada específico de plataforma pode sobrar no `commonMain` do shell fora dos source sets corretos — pré-requisito do split futuro.

### D6 — Home fica no shell
`HomeScreen` compõe `DashboardScreen` e `TransactionsScreen` como abas. Como feature, exigiria entry points retornando telas `@Composable` (o "caso raro" viraria estrutural). O shell enxerga impls por definição — o problema desaparece. Consequências: a sealed `AppRoute` é quebrada em rotas `@Serializable` por api (type-safe navigation não exige pai sealed comum) e o contrato `HomeChrome` desce para `:core:ui` (dashboard o consome; shell → impl impediria o import na direção atual).

### D7 — Entry point único por feature
Interface `<Nome>Entry` na api agrupando a superfície pública de UI (métodos retornando `Modal`); impl implementa; Koin faz o bind. Assinaturas só referenciam tipos do core. Critério entry point vs `:core:ui`: tem wiring próprio (ViewModel/use cases) → entry point; só renderiza modelos core (ex.: `AccountSelector`, `OperationCard`) → `:core:ui`.

### D8 — Trio Firebase como três módulos core
`:core:analytics`, `:core:crashlytics`, `:core:auth` (interface + impls de plataforma juntas; são serviços folha, sem emaranhado que justifique api/impl). Espelha a separação atual de `di/` e permite dependência seletiva. *Alternativa considerada:* um `:core:firebase` agrupado — menos módulos, mas quem só precisa de analytics enxergaria auth.

### D9 — Recorte das features
10 features: `support`, `categories`, `budgets` (feature própria, decisão do produto), `accounts`, `creditcards` (inclui invoices, installments e invoiceTransactions — use cases e modelos entrelaçados), `recurring`, `transactions`, `report` (única com source sets de plataforma no impl), `dashboard` (agregador: depende da api de todos).

### D10 — iOS export seletivo
No framework do `:composeApp`: `export()` de `:core:*` e `:feature:*:api`; impls linkados via `implementation`, invisíveis ao Swift.

### D11 — DI por feature, migração incremental
Cada impl expõe seu módulo Koin (`supportModule`, ...); o shell agrega. Os 9 módulos por camada encolhem a cada feature extraída e morrem na última fase — sem big-bang.

### D12 — Piloto: `:feature:support`
Pequena, mas exercita o template completo: api/impl, entry point, dependência de `:core:auth`, variação de plataforma no DI (`FirebaseSupportRepository` vs `UnsupportedSupportRepository` no desktop) e export iOS. Valida antes das features grandes.

## Risks / Trade-offs

- [Regressão silenciosa durante mover 430 arquivos] → commits atômicos por módulo extraído; `./gradlew check` + build dos 3 targets verdes ao fim de cada fase; sem mudança de comportamento permitida nos commits de extração.
- [Vazamento de tipos de impl em assinaturas de api] → verificação mecânica no convention plugin + revisão na fase do piloto, onde o template se consolida.
- [Referências ao `Res` quebram em massa ao mover strings para `:core:resources`] → mover o `composeResources` inteiro de uma vez na fase core, ajustando só imports (sem split de conteúdo).
- [`:core:ui` virar depósito de tudo] → critério D7 aplicado na revisão; componente usado por uma única feature migra para o impl dela.
- [Build multiplicar configuração por módulo] → convention plugins concentram 100% da configuração; `build.gradle.kts` de feature deve ter ~5 linhas.
- [Overhead de módulos para projeto solo (~25 módulos)] → aceito conscientemente: o objetivo é fronteira, não velocidade de build; convention plugins reduzem o custo marginal de módulo a quase zero.

## Migration Plan

Cada fase termina com app verde nos 3 targets; rollback = reverter os commits da fase.

- **Fase 0 — build-logic**: convention plugins (`kmp.library`, `compose.library`, `feature.api`, `feature.impl`) aplicados ao `:composeApp` atual, validando o build sem mover código.
- **Fase 1 — core (folhas primeiro)**: `:core:common` → `:core:model` → `:core:resources` → `:core:designsystem` → `:core:ui` → `:core:database` → `:core:analytics`/`:core:crashlytics`/`:core:auth`.
- **Fase 2 — piloto**: `:feature:support` api/impl completo (entry point, DI por feature, export iOS). Template consolidado aqui.
- **Fase 3 — ondas de features** (menos → mais acoplada): categories → budgets → accounts → creditcards → recurring → transactions → report. Cada onda migra sua fatia dos módulos DI por camada e quebra sua parte da `AppRoute`.
- **Fase 4 — agregadores e shell**: `:feature:dashboard`; `HomeChrome` → `:core:ui`; `:composeApp` vira shell puro; deletar `di/` por camada; verificação final das regras + `feature/README.md` consistente.

## Open Questions

- Mecanismo de registro de navegação: extension `NavGraphBuilder.xxxGraph()` exposta pelo impl (shell chama) vs interface registrada via Koin. Decidir no piloto.
- Forma da verificação mecânica das regras: task Gradle custom em `build-logic` inspecionando configurations vs plugin de dependency-rules existente. Decidir na fase 0.
- Destino de `domain/model/form` e `ui/model`/`ui/mapper` compartilhados: triagem caso a caso entre `:core:model`, `:core:ui` e impls, seguindo o critério D3/D7.
