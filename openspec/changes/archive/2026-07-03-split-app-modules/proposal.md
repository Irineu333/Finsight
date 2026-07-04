# Proposal: split-app-modules

## Why

O `:composeApp` é o último módulo fora do padrão da modularização: acumula quatro papéis (shell comum, app Android, app Desktop, framework iOS) num único `build.gradle.kts` manual de ~213 linhas, sem convention plugin. Além da inconsistência, o AGP 9 não suportará mais `com.android.application` em módulo KMP — a recomendação oficial (JetBrains/Google) é extrair um módulo Android puro e transformar o módulo compartilhado em library. Esta change fecha a topologia da modularização e prepara o terreno para o AGP 9.

## What Changes

- **BREAKING (interno)**: o módulo `:composeApp` deixa de existir, substituído por quatro módulos sob `app/`:
  - `:app:shared` — KMP library pura (sem plugin de application): `App`, `AppNavHost`, `AppNavigationDispatcher`, `HomeScreen`/`HomeRoute`, agregação Koin. Único módulo que enxerga `feature:*:impl`.
  - `:app:android` — `com.android.application` puro (não-KMP): `MainActivity`, `AndroidApp` (startKoin), Manifest, mipmaps, signing, google-services, crashlytics, versionCode/versionName.
  - `:app:desktop` — `kotlin("jvm")`: `main.kt` + `compose.desktop` com `nativeDistributions` (Dmg/Msi/Deb).
  - `:app:ios` — KMP só-iOS: `MainViewController` + framework `ComposeApp` com export seletivo de `:core:*` e `feature:*:api`.
- **BREAKING (interno)**: `iosApp/project.yml` passa a chamar `:app:ios:embedAndSignAppleFrameworkForXcode` (antes `:composeApp:...`).
- Dissolução do `shellModule` — cada binding Koin migra para o core dono, seguindo o padrão já existente em `core:analytics`/`auth`/`crashlytics`:
  - `databaseModule` + `databasePlatformModule` (expect/actual) → `:core:database`.
  - `Settings`, `CurrencyFormatter`, `DebounceManager` → novo `commonModule` em `:core:common` (dependência `multiplatform-settings` migra junto).
  - `ModalManager` → novo `designsystemModule` em `:core:designsystem`.
- Novo convention plugin `finsight.app.shared` no `build-logic`; módulos de plataforma (`:app:android`, `:app:desktop`, `:app:ios`) mantêm build explícito (signing, packaging e framework são inerentemente únicos).
- Atualização de documentação e comandos: `CLAUDE.md`, `feature/README.md`, skill `bump-version` (targets de versão mudam de módulo).
- **Fora de escopo (change futura)**: migração de `com.android.library` para `com.android.kotlin.multiplatform.library` (AGP 9 completo); separação de `:core:model`/domínio por features.

## Capabilities

### New Capabilities

(nenhuma — os requisitos alterados pertencem a capabilities existentes)

### Modified Capabilities

- `module-architecture`: o requisito "composeApp como shell agregador" é substituído pela estrutura de módulos `app/` (shared/android/desktop/ios); a regra de dependência ":composeApp é o único autorizado a depender de impl" passa a valer para `:app:shared`; o requisito "Export seletivo no framework iOS" muda de dono (`:app:ios`) e o `project.yml` deixa de ser imutável; novo requisito: módulos `core` que provêm injetáveis expõem seu próprio módulo Koin (o shell apenas agrega).
- `build-conventions`: novo convention plugin `finsight.app.shared`; módulos de plataforma com build explícito documentado como exceção intencional.

## Impact

- **Módulos Gradle**: `settings.gradle.kts` (remove `:composeApp`, adiciona 4 módulos `app/`); `build-logic` (novo plugin).
- **Código movido (~15 arquivos)**: conteúdo de `composeApp/src/*` redistribuído entre os 4 módulos; `DatabaseModule.*` para `core:database`; bindings do `shellModule` para `core:common`/`core:designsystem`.
- **iOS**: `iosApp/project.yml` (task do Gradle), framework mantém `baseName = "ComposeApp"` e `bundleId` (sem impacto no Swift).
- **Android**: `google-services.json`, keystore e config de release migram para `app/android/`.
- **Docs/comandos**: `CLAUDE.md` (comandos de teste `:composeApp:testDebugUnitTest` → novo módulo), `feature/README.md`, skill `bump-version`.
- **Riscos**: pipeline iOS/XcodeGen exige verificação real no Xcode (embedAndSign); run configurations do IDE precisam ser recriadas.
