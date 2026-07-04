# Design: split-app-modules

## Context

Após a modularização por features (api/impl + core), o `:composeApp` ficou enxuto (~15 arquivos, ~340 linhas em `commonMain`) mas continua sendo a única exceção às regras mecânicas do projeto: 4 responsabilidades (shell comum, app Android, app Desktop, framework iOS) num único módulo KMP com `build.gradle.kts` manual, aplicando `com.android.application` + `googleServices` + `firebaseCrashlytics` num módulo que também compila Desktop e iOS.

A recomendação oficial mais recente (guia JetBrains "Migrate your project to AGP 9" + "Recommended project structure") é: entry points de app em módulos separados; o módulo compartilhado vira library; AGP 9 não suporta `com.android.application` em módulo KMP. Versões atuais do projeto: AGP 8.12.3, Kotlin 2.3.10 — o split funciona hoje e remove o bloqueador principal do AGP 9.

Precedente interno relevante: `core:analytics`, `core:auth` e `core:crashlytics` já expõem módulos Koin próprios com `expect val xxxPlatformModule` — o mesmo padrão do `databaseModule` que hoje vive órfão no shell.

## Goals / Non-Goals

**Goals:**
- Eliminar `:composeApp`, substituindo por `app/` com 4 módulos de responsabilidade única: `shared`, `android`, `desktop`, `ios`.
- `:app:android` como `com.android.application` puro (não-KMP) — pronto para AGP 9.
- `:app:shared` como KMP library pura, único módulo que enxerga `feature:*:impl`, sob convention plugin.
- Dissolver o `shellModule`: cada binding Koin no core dono; shell apenas agrega.
- `databaseModule` em `:core:database`, completando o padrão dos demais cores.
- Documentação e tooling (CLAUDE.md, feature/README.md, bump-version) atualizados.

**Non-Goals:**
- Migrar `com.android.library` → `com.android.kotlin.multiplatform.library` (change futura; este split apenas prepara o terreno).
- Separar `:core:model`/domínio por features (débito conhecido, change futura).
- Qualquer mudança de comportamento, UI ou navegação — a change é 100% estrutural.
- Renomear o framework iOS (`baseName`/`bundleId` permanecem `ComposeApp`/`com.neoutils.finsight.ComposeApp` para não tocar o Swift).

## Decisions

### D1 — Quatro módulos sob `app/`, incluindo `:app:ios` para o framework

Estrutura alvo:

```
:app:android ─┐        (com.android.application puro)
:app:desktop ─┼─▶ :app:shared ─▶ feature:*:impl ─▶ feature:*:api ─▶ core:*
:app:ios ─────┘        (KMP só-iOS, framework ComposeApp)
```

Alternativa considerada: manter o framework iOS no `:app:shared` (é o que o guia JetBrains faz — só extrai `androidApp`). Rejeitada: deixaria config de packaging de plataforma no módulo compartilhado, quebrando a simetria "shared é library pura, cada plataforma tem seu módulo". O custo do `:app:ios` é re-declarar `api()` dos módulos exportados (exigência do `export()` de framework estático) — lista que já existe hoje no composeApp, apenas muda de endereço.

### D2 — `:app:android` não-KMP

`com.android.application` + `org.jetbrains.kotlin.android` + compose, com `src/main` (Manifest, mipmaps, `MainActivity`, `AndroidApp`). Signing/keystore, `google-services.json`, plugin crashlytics, `versionCode`/`versionName` migram para cá. É exatamente o layout do guia oficial de migração AGP 9 (mover `androidMain/` → `androidApp/src/main`).

### D3 — `:app:desktop` como `kotlin("jvm")` puro

`main.kt` + bloco `compose.desktop { application { nativeDistributions } }`. Módulo JVM simples dependendo de `:app:shared`; `mainClass` e `packageVersion` migram para cá.

### D4 — Convention plugin só para o `:app:shared` (`finsight.app.shared`)

O plugin encapsula: targets KMP (androidTarget library + jvm + ios), Compose, serialization, opt-ins — e é o lugar natural para codificar mecanicamente o papel de agregador (único módulo autorizado a depender de `impl`s, em paridade com as verificações de `feature.api`/`feature.impl`). Alternativa considerada: reutilizar `finsight.compose.library`. Rejeitada: as verificações de dependência das convenções de library proibiriam (ou não verificariam) a dependência em `impl`s; um plugin dedicado torna a exceção explícita e verificada. Os módulos de plataforma (`android`/`desktop`/`ios`) ficam com build explícito: signing, packaging e framework são únicos por natureza — plugin ali seria abstração sem segundo consumidor.

### D5 — Dissolução do `shellModule` no padrão dos cores

| Binding | Destino | Racional |
|---|---|---|
| `databaseModule` + `databasePlatformModule` (expect/actual ×3) | `:core:database` | Os `actual`s só chamam `getDatabaseBuilder()`, que já vive lá por plataforma; completa o padrão de `analytics`/`auth`/`crashlytics` |
| `single { Settings() }`, `single { CurrencyFormatter() }`, `factory { DebounceManager }` | novo `commonModule` em `:core:common` | Classes já residem em `core:common`; dependência `multiplatform-settings` migra do composeApp para lá |
| `single { ModalManager() }` | novo `designsystemModule` em `:core:designsystem` | Classe já reside lá |

O `:app:shared` mantém apenas a lista de agregação (`appModules`: módulos dos cores + das features), consumida pelos `startKoin` de cada entry point de plataforma (Android adiciona `androidContext`).

### D6 — iOS: mudança mínima e verificável

`iosApp/project.yml` muda uma linha (`:composeApp:embedAndSignAppleFrameworkForXcode` → `:app:ios:...`). `baseName`, `bundleId`, `isStatic`, `linkerOpts` e a lista de `export()` são preservados byte a byte — o Swift não percebe a mudança. `MainViewController` migra para `:app:ios/src/iosMain`.

### D7 — Nomes e comandos

- Módulos: `:app:shared`, `:app:android`, `:app:desktop`, `:app:ios` (pastas `app/shared` etc.).
- Comandos documentados mudam: `:composeApp:testDebugUnitTest` → `:app:shared:testDebugUnitTest`; run desktop → `:app:desktop:run`.
- Skill `bump-version` passa a apontar `app/android/build.gradle.kts` (versionCode/Name) e `app/desktop/build.gradle.kts` (packageVersion); iOS via XcodeGen permanece.

## Risks / Trade-offs

- **[Pipeline iOS/XcodeGen]** O `embedAndSignAppleFrameworkForXcode` num módulo novo precisa de verificação real (XcodeGen + build no Xcode/simulador), não só `./gradlew check` → Mitigação: preservar nome/bundleId do framework; task de verificação explícita no plano; a mudança no `project.yml` é 1 linha, revert trivial.
- **[Export chain do framework estático]** `export()` exige que cada módulo exportado seja dependência `api` direta do `:app:ios` → Mitigação: copiar a lista existente do composeApp; o build falha rápido (erro de configuração) se faltar algum.
- **[Duplicação de dependências de plataforma]** Deps `androidMain`/`iosMain` do composeApp (koin-android, activity-compose, gitlive analytics/crashlytics) precisam ser redistribuídas entre `:app:android`/`:app:ios`/cores sem sobrar nem faltar → Mitigação: auditar cada dependência do `composeApp/build.gradle.kts` na task de migração; `./gradlew check` + smoke test por plataforma.
- **[Run configurations do IDE]** Configurações apontando `composeApp` quebram → Mitigação: documentar os novos alvos; custo único e local.
- **[Histórico git]** Mover ~15 arquivos + recriar builds pode poluir blame → Mitigação: `git mv` onde possível; módulo era recente e pequeno.

## Migration Plan

1. Criar `build-logic` plugin `finsight.app.shared` e os 4 módulos vazios em `settings.gradle.kts` (composeApp ainda vivo).
2. Migrar Koin para os cores (`core:database`, `core:common`, `core:designsystem`) — composeApp passa a consumir os novos módulos (passo verificável isolado).
3. Mover shell comum para `:app:shared`; entry points para `:app:android`/`:app:desktop`/`:app:ios`; configs de plataforma junto.
4. Atualizar `iosApp/project.yml`; remover `:composeApp` do settings e apagar o diretório.
5. Verificação por plataforma: `./gradlew check`, `:app:android:assembleDebug` + run, `:app:desktop:run`, XcodeGen + build iOS no simulador.
6. Atualizar docs (CLAUDE.md, feature/README.md, bump-version).

Rollback: a change é estrutural e commits são incrementais — revert por fase; o passo 4 (morte do composeApp) só acontece com as 3 plataformas verificadas.

## Open Questions

(nenhuma — decisões fechadas na exploração com o usuário)
