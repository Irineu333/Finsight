# Etapa 01 — Interface `Analytics` + DI

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Criar a interface `Analytics` no domínio, as implementações de plataforma e o módulo Koin seguindo o padrão de `ReportModule`.

- **Interface** em `commonMain/domain/analytics/Analytics.kt` com os três métodos do contrato definido na spec.
- **Implementação Firebase** (Android e iOS) em `androidMain` e `iosMain` — usa `dev.gitlive:firebase-analytics:2.1.0`.
- **Implementação no-op** (JVM/Desktop) em `jvmMain` — métodos vazios, sem logs, sem erros.
- **`AnalyticsModule`** em `commonMain/di/` com `expect val analyticsPlatformModule: Module` e `includes(analyticsPlatformModule)`, registrando `Analytics` como `single {}`.
- **Implementações do módulo** (`AnalyticsModule.android.kt`, `AnalyticsModule.ios.kt`, `AnalyticsModule.jvm.kt`) com `actual val analyticsPlatformModule`.
- **Dependência** `dev.gitlive:firebase-analytics:2.1.0` adicionada no `build.gradle.kts` apenas nos sourceSets Android e iOS.
- **Registro do módulo** nos três entry points: `AndroidApp.kt`, `main.kt` e `MainViewController.kt`.

---

## Arquivos afetados

- `composeApp/build.gradle.kts` — adicionar `firebase-analytics:2.1.0` nos sourceSets `androidMain` e `iosMain`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/Analytics.kt` — criar interface
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/AnalyticsModule.kt` — criar módulo comum com `expect`
- `composeApp/src/androidMain/kotlin/com/neoutils/finsight/di/AnalyticsModule.android.kt` — criar implementação Firebase Android
- `composeApp/src/androidMain/kotlin/com/neoutils/finsight/analytics/FirebaseAnalyticsImpl.kt` — criar classe de implementação
- `composeApp/src/iosMain/kotlin/com/neoutils/finsight/di/AnalyticsModule.ios.kt` — criar implementação Firebase iOS
- `composeApp/src/iosMain/kotlin/com/neoutils/finsight/analytics/FirebaseAnalyticsImpl.kt` — criar classe de implementação
- `composeApp/src/jvmMain/kotlin/com/neoutils/finsight/di/AnalyticsModule.jvm.kt` — criar módulo JVM com no-op
- `composeApp/src/jvmMain/kotlin/com/neoutils/finsight/analytics/NoOpAnalytics.kt` — criar classe no-op
- `composeApp/src/androidMain/kotlin/com/neoutils/finsight/AndroidApp.kt` — adicionar `analyticsModule`
- `composeApp/src/jvmMain/kotlin/com/neoutils/finsight/main.kt` — adicionar `analyticsModule`
- `composeApp/src/iosMain/kotlin/com/neoutils/finsight/MainViewController.kt` — adicionar `analyticsModule`

---

## Critério de aceite

**Validação manual:**
1. Compilar Android, iOS e Desktop sem erros.
2. No Desktop, abrir o app e confirmar que nenhuma exceção é lançada (sem analytics real).

**Revisão de código:**
- [ ] `Analytics` é uma interface em `domain/analytics/` sem dependências de plataforma
- [ ] Implementações Firebase estão em `androidMain` e `iosMain` — nunca em `commonMain`
- [ ] `NoOpAnalytics` em `jvmMain` implementa todos os métodos sem nenhuma lógica
- [ ] Dependência `firebase-analytics` declarada apenas para Android e iOS no `build.gradle.kts`
- [ ] `Analytics` registrado como `single {}` no Koin
- [ ] `analyticsModule` adicionado nos três entry points

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
