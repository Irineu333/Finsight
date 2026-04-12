# Plano: Crashlytics

> O plano descreve *como* entregar o que a spec define.
> Pode ser ajustado durante a implementação. Desvios devem ser registrados.
> A spec não muda por dificuldade técnica — só quando a intenção mudar.

---

## Contexto técnico

- Firebase já integrado via `dev.gitlive` v2.1.0. O módulo `firebase-crashlytics` ainda **não está** no projeto — precisa ser adicionado.
- O padrão arquitetural já está estabelecido pelo Analytics: interface no domínio, implementações por plataforma, no-op em JVM, módulo Koin por plataforma via `expect/actual`.
- Arquivos de referência do padrão a seguir:
  - Interface: `domain/analytics/Analytics.kt`
  - Implementação: `androidMain/analytics/FirebaseAnalyticsImpl.kt`
  - No-op: `jvmMain/analytics/NoOpAnalytics.kt`
  - Módulo: `commonMain/di/AnalyticsModule.kt` + plataformas
  - Registro: `AndroidApp.kt`, `MainViewController.kt`, `main.kt`
  - Init de userId: `App.kt`
- Exceções tratadas com `runCatching` estão em: `CreateSupportIssueUseCase`, `AddSupportReplyUseCase`, `FirebaseSupportRepository`.
- Crashes fatais são capturados automaticamente pelo SDK — sem código adicional necessário.

**Riscos:**
- A API do `dev.gitlive:firebase-crashlytics` v2.1.0 deve expor `Firebase.crashlytics.recordException(e)` e `Firebase.crashlytics.setUserId(id)`. Confirmar ao implementar a etapa 2.

---

## Etapas

- [x] [01 — Dependência e interface](steps/01-dependency-and-interface.md)
- [ ] [02 — Implementações por plataforma](steps/02-platform-implementations.md)
- [ ] [03 — DI e registro nos entry points](steps/03-di-wiring.md)
- [ ] [04 — Inicialização do user ID](steps/04-user-id-init.md)
- [ ] [05 — Refatorar use cases](steps/05-refactor-use-cases.md)
- [ ] [06 — Reportar exceções](steps/06-report-exceptions.md)

---

## Registro de desvios

- **Etapa 01:** plugin Gradle `com.google.firebase.crashlytics` adicionado (Android); `FirebaseCrashlytics` e `FirebaseAnalytics` adicionados como produtos SPM no `project.yml` (iOS). Desvios necessários para que as plataformas linkassem os frameworks nativos corretamente.

---

## Issues
