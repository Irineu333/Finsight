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
- [ ] `Crashlytics` é uma interface em `domain/crashlytics/`, sem imports de plataforma
- [ ] Versão da dependência usa `version.ref = "gitlive-firebase"` (não hardcoded)
- [ ] Dependência adicionada apenas em `androidMain` e `iosMain` (não em `commonMain`)

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
