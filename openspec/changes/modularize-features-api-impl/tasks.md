# Tasks: Modularização por feature (api/impl)

> Regra transversal: cada extração de módulo é um commit atômico, sem mudança de comportamento.
> Ao fim de cada grupo, `./gradlew check` e build dos 3 targets (Android, iOS, Desktop) devem passar.

## 1. Fase 0 — build-logic e convenções

- [ ] 1.1 Criar build incluído `build-logic` (limpar resquícios `.gradle` de `build-logic/` e `app/`) com version catalog compartilhado
- [ ] 1.2 Implementar convention plugins `kmp.library` e `compose.library` (targets Android/iOS/Desktop, compiler options, Compose)
- [ ] 1.3 Implementar convenções `feature.api` e `feature.impl` com verificação mecânica das regras de dependência (falha de build com mensagem indicando módulo e dependência proibida)
- [ ] 1.4 Aplicar as convenções ao `:composeApp` atual e validar build verde nos 3 targets sem mover código

## 2. Fase 1 — módulos core

- [ ] 2.1 Extrair `:core:common` (`util/`, `extension/`, `Platform.kt`, `UiText`) com source sets de plataforma
- [ ] 2.2 Extrair `:core:model` (`domain/model/`, `domain/error/`, `domain/exception/`)
- [ ] 2.3 Extrair `:core:resources` movendo `composeResources` inteiro (sem split de conteúdo) e ajustando imports de `Res`
- [ ] 2.4 Extrair `:core:designsystem` (`ui/theme/`, `ui/icons/`, `ModalManager`, componentes genéricos sem modelo de domínio)
- [ ] 2.5 Extrair `:core:ui` (componentes que renderizam modelos core: `AccountSelector`, `OperationCard`, seletores etc.)
- [ ] 2.6 Extrair `:core:database` (entities, DAOs, `AppDatabase`, `Converters`, expect/actual de builder por plataforma)
- [ ] 2.7 Extrair `:core:analytics`, `:core:crashlytics` e `:core:auth` (interfaces de domínio + impls Firebase/no-op por plataforma + módulos Koin próprios)
- [ ] 2.8 Verificação da fase: check verde nos 3 targets; `:composeApp` sem `util/`, `extension/`, `domain/model`, `theme` e `database/`

## 3. Fase 2 — piloto :feature:support

- [ ] 3.1 Criar `:feature:support:api` (rotas `@Serializable`, interface `ISupportRepository`, `SupportEntry` com modais públicos)
- [ ] 3.2 Criar `:feature:support:impl` (telas, ViewModels, `supportIssueForm` modal, `FirebaseSupportRepository`/`UnsupportedSupportRepository`, `supportModule` Koin)
- [ ] 3.3 Definir e implementar o mecanismo de registro de navegação no shell (open question do design — decidir aqui e documentar em `feature/README.md`)
- [ ] 3.4 Configurar export seletivo no framework iOS (`export()` de core + `support:api`) e validar símbolos no Swift
- [ ] 3.5 Migrar testes de support e validar o template completo (fase verde nos 3 targets)

## 4. Fase 3 — ondas de features

- [ ] 4.1 Extrair `:feature:categories` (api/impl, rotas, entry point, fatia dos módulos DI)
- [ ] 4.2 Extrair `:feature:budgets` (api/impl; depende de `categories:api` para spending por categoria)
- [ ] 4.3 Extrair `:feature:accounts` (api/impl; inclui transferências e ajustes de saldo)
- [ ] 4.4 Extrair `:feature:creditcards` (api/impl; inclui invoices, installments e invoiceTransactions — a maior onda)
- [ ] 4.5 Extrair `:feature:recurring` (api/impl; `recurring:impl` → `creditcards:api` para invoice do mês)
- [ ] 4.6 Extrair `:feature:transactions` (api/impl; `transactions:impl` → `creditcards:api`, `accounts:api`, `categories:api`)
- [ ] 4.7 Extrair `:feature:report` (api/impl; único impl com source sets de plataforma — print/share)
- [ ] 4.8 Verificação da fase: check verde; módulos DI por camada contêm apenas o que resta de dashboard/home

## 5. Fase 4 — agregadores e shell

- [ ] 5.1 Mover contrato `HomeChrome` para `:core:ui`
- [ ] 5.2 Extrair `:feature:dashboard` (api/impl; consome entries e apis das demais features; `DashboardPreferences` junto)
- [ ] 5.3 Quebrar a sealed `AppRoute` restante: shell mantém apenas `HomeRoute`; rotas remanescentes migram para as apis donas
- [ ] 5.4 Reduzir `:composeApp` a shell (App, AppNavHost, dispatcher, Home, agregação Koin, entry points de plataforma, framework iOS) e deletar os módulos DI por camada
- [ ] 5.5 Garantir que nada de plataforma sobrou no `commonMain` do shell fora dos source sets corretos (pré-requisito do split futuro `:shared`/`:androidApp`/`:desktopApp`)

## 6. Verificação final

- [ ] 6.1 Rodar `./gradlew check` e builds dos 3 targets; smoke test manual dos fluxos principais (transação, fatura, recorrência, relatório)
- [ ] 6.2 Validar as regras de dependência com testes negativos (dependência proibida deve falhar o build)
- [ ] 6.3 Atualizar `feature/README.md` (seção "O papel do shell" + mecanismo de navegação decidido) e `CLAUDE.md`/`README.md` com a nova estrutura de módulos
