# `:core:analytics`

Abstrações de observabilidade (Analytics e Crashlytics) com implementações Firebase por plataforma.

## Responsabilidade

Expor interfaces neutras (`Analytics`, `Crashlytics`) para que features registrem eventos e exceções sem acoplar a um provedor específico.

## Conteúdo principal

- **Interfaces:** `Analytics` (`logScreenView`, `logEvent`, `setUserId`), `Crashlytics` (`setUserId`, `recordException`).
- **Modelo:** `Event(name, params)` — base para eventos customizados de feature.
- **DI:** `AnalyticsModule`, `CrashlyticsModule` (Koin) — bindings Firebase.

## Dependências

Nenhuma intra-projeto.

## Quem depende

- Todos os `:feature:X:impl` para emitir eventos das próprias features.
