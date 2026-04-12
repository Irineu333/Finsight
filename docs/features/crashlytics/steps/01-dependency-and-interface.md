# Etapa 01 — Dependência e interface

> Parte do plano: [Crashlytics](../plan.md)

---

## O que fazer

Adicionar a dependência `dev.gitlive:firebase-crashlytics` ao projeto e criar a interface `Crashlytics` no domínio, seguindo o mesmo padrão da interface `Analytics`.

---

## Arquivos afetados

- `gradle/libs.versions.toml` — adicionar entrada `gitlive-firebase-crashlytics` com `version.ref = "gitlive-firebase"`
- `composeApp/build.gradle.kts` — adicionar `implementation(libs.gitlive.firebase.crashlytics)` em `androidMain.dependencies` e `iosMain.dependencies`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/crashlytics/Crashlytics.kt` — criar interface com:
  - `fun setUserId(id: String?)`
  - `fun recordException(e: Exception)`

---

## Critério de aceite

**Validação manual:**
1. Sync do Gradle sem erros de resolução de dependência.
2. Interface `Crashlytics` visível e compilável em `commonMain`.

**Revisão de código:**
- [x] `Crashlytics` é uma interface em `domain/crashlytics/`, sem imports de plataforma
- [x] Versão da dependência usa `version.ref = "gitlive-firebase"` (não hardcoded)
- [x] Dependência adicionada apenas em `androidMain` e `iosMain` (não em `commonMain`)

---

## Desvio

**O que era esperado:** apenas adicionar a dependência `gitlive-firebase-crashlytics` em `androidMain`/`iosMain`.

**O que foi feito:**
1. Plugin Gradle `com.google.firebase.crashlytics` (v3.0.3) adicionado em `libs.versions.toml`, declarado com `apply false` no root `build.gradle.kts` e aplicado no `composeApp/build.gradle.kts`.
2. `FirebaseCrashlytics` adicionado como produto SPM em `iosApp/project.yml`.
3. `FirebaseAnalytics` adicionado como produto SPM em `iosApp/project.yml` — estava ausente (issue pré-existente), o linker só falhou ao adicionar `firebase-crashlytics` a `iosMain`.

**Por quê:**
- Android: o SDK do Crashlytics exige o plugin Gradle para gerar o Crashlytics Build ID em compile time. Sem ele, o `FirebaseInitProvider` lança `IllegalStateException`.
- iOS: os produtos SPM `FirebaseCrashlytics` e `FirebaseAnalytics` precisam estar linkados no target Xcode para que os símbolos Objective-C referenciados pelo framework KMP sejam resolvidos.

**Impacto nas etapas seguintes:** nenhum.
