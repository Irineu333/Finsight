# Etapa 03 — DI e registro nos entry points

> Parte do plano: [Crashlytics](../plan.md)

---

## O que fazer

Criar os módulos Koin para `Crashlytics` (commonMain + 3 plataformas) e registrá-los nos entry points da aplicação. Segue exatamente o mesmo padrão do `analyticsModule`.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/CrashlyticsModule.kt` — criar `val crashlyticsModule` e `expect val crashlyticsPlatformModule: Module`
- `composeApp/src/androidMain/kotlin/com/neoutils/finsight/di/CrashlyticsModule.android.kt` — `actual val crashlyticsPlatformModule` com `single<Crashlytics> { FirebaseCrashlyticsImpl() }`
- `composeApp/src/iosMain/kotlin/com/neoutils/finsight/di/CrashlyticsModule.ios.kt` — idem para iOS
- `composeApp/src/jvmMain/kotlin/com/neoutils/finsight/di/CrashlyticsModule.jvm.kt` — `actual val crashlyticsPlatformModule` com `single<Crashlytics> { NoOpCrashlytics() }`
- `composeApp/src/androidMain/kotlin/com/neoutils/finsight/AndroidApp.kt` — adicionar `crashlyticsModule` na lista de módulos do `startKoin`
- `composeApp/src/iosMain/kotlin/com/neoutils/finsight/MainViewController.kt` — idem
- `composeApp/src/jvmMain/kotlin/com/neoutils/finsight/main.kt` — idem

---

## Critério de aceite

**Validação manual:**
1. App Android inicia sem `NoBeanDefFoundException` para `Crashlytics`.
2. App Desktop inicia sem erros.

**Revisão de código:**
- [ ] `Crashlytics` registrado como `single {}` (não `factory {}`)
- [ ] Módulo usa `expect/actual` igual ao padrão do Analytics
- [ ] `crashlyticsModule` adicionado nos três entry points

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
