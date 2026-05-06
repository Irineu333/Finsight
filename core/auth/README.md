# `:core:auth`

Abstração de autenticação. Implementação atual via Firebase Auth.

## Responsabilidade

Expor `AuthService` para que outros módulos obtenham o usuário corrente sem depender de Firebase diretamente.

## Conteúdo principal

- **Interface:** `AuthService` — `suspend fun getUserId(): String?`.
- **DI:** `AuthModule` (Koin) — registra implementação Firebase.

## Dependências

Nenhuma intra-projeto.

## Quem depende

- `:feature:support:impl` (associa tickets ao usuário corrente)
- `:app` para wiring inicial de identificação no Analytics/Crashlytics.
