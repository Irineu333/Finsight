# `:core:platform`

Detecção e acesso à plataforma (Android, Desktop, iOS) via `expect/actual`.

## Responsabilidade

Tipos e utilitários relacionados estritamente a plataforma. **Sem lógica de negócio.**

## Conteúdo principal

- `Platform` enum (`Android`, `Desktop`, `iOS`) com helpers (`isDesktop`).
- `currentPlatform` — `expect val`, com `actual` por target.

## Dependências

Nenhuma intra-projeto.

## Quem depende

- `:core:ui` (para variar comportamento por plataforma)
- `:feature:dashboard:impl`, demais `:impl` que precisem de checagem de plataforma.
