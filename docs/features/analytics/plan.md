# Plano: Analytics

> O plano descreve *como* entregar o que a spec define.
> Pode ser ajustado durante a implementação. Desvios devem ser registrados.
> A spec não muda por dificuldade técnica — só quando a intenção mudar.

---

## Contexto técnico

- Firebase já integrado via `dev.gitlive` 2.1.0 (Firestore + Auth). O `google-services.json` (Android) e `GoogleService-Info.plist` (iOS) já estão configurados — sem configuração adicional de Firebase.
- Padrão de módulo com plataforma: `ReportModule.kt` (commonMain) + `ReportModule.android.kt` / `ReportModule.ios.kt` / `ReportModule.jvm.kt`. O `AnalyticsModule` seguirá esse mesmo padrão.
- Koin é inicializado em três entry points: `AndroidApp.kt`, `main.kt` (JVM) e `MainViewController.kt` (iOS). O `analyticsModule` precisa ser adicionado nos três.
- Screen views serão chamados via `LaunchedEffect` nos composables de tela (não nos ViewModels) — mantém os ViewModels focados em estado/ações e evita acoplamento desnecessário.
- `setUserId` será chamado em `App.kt` ou `AppNavHost.kt` via `LaunchedEffect`, observando o `currentUser?.uid` do Firebase Auth já em uso.
- Desktop/JVM não suporta Firebase Analytics — a implementação no-op deve compilar sem erros e não fazer nada.

### Referências
- [`docs/reference/firebase-analytics.md`](../../reference/firebase-analytics.md) — API GitLive Analytics, restrições Firebase, comportamento no Desktop

---

## Etapas

- [x] [01 — Interface `Analytics` + DI](steps/01-interface-analytics-di.md)
- [x] [02 — Screen views](steps/02-screen-views.md)
- [x] [03 — Identificação de usuário](steps/03-user-id.md)
- [x] [04 — Eventos: Transactions](steps/04-events-transactions.md)
- [x] [05 — Eventos: Accounts](steps/05-events-accounts.md)
- [x] [06 — Eventos: Credit Cards](steps/06-events-credit-cards.md)
- [x] [07 — Eventos: Installments](steps/07-events-installments.md)
- [x] [08 — Eventos: Budgets](steps/08-events-budgets.md)
- [x] [09 — Eventos: Recurring](steps/09-events-recurring.md)
- [x] [10 — Eventos: Categories](steps/10-events-categories.md)
- [x] [11 — Eventos: Dashboard](steps/11-events-dashboard.md)
- [x] [12 — Eventos: Reports](steps/12-events-reports.md)
- [x] [13 — Eventos: Support](steps/13-events-support.md)
- [x] [14 — Eventos tipados](steps/14-typed-events.md)

---

## Registro de desvios

- **Etapa 02:** adicionado `screen_view` para `home` — decisão de produto para comparar acessos à home com acessos às tabs. Spec atualizada.
- **Etapa 03:** `App.kt` passa a fazer `signInAnonymously()` no startup para garantir `user_id` desde a primeira sessão. `FirebaseSupportRepository` recebeu `Analytics` e chama `setUserId` após o login anônimo como fallback.
- **Etapa 12:** `generate_report` movido de `ReportViewerViewModel` para `ReportConfigViewModel` — disparado ao confirmar a geração (antes da navegação), não ao carregar o viewer.
- **Etapa 15:** `is_installment` removido de `create_transaction`; `AddTransactionViewModel` usa `create_installments` no branch de parcelamento.

---

## Issues

- [x] [01 — Firebase não inicializado no Desktop](issues/01-firebase-not-initialized-desktop.md)
