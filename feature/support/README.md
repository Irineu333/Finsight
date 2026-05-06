# `:feature:support`

Feature terminal — apenas `:impl`. Sistema de suporte (tickets) com backend Firebase Firestore.

## Responsabilidade

Permitir que o usuário abra e acompanhe issues de suporte, incluindo respostas e anexos quando aplicável.

## Implementação (`:impl`)

- **Tela:** `SupportScreen` + `SupportViewModel` + `SupportUiState` (lista e detalhe).
- **Use cases:** `AddSupportReplyUseCase`.
- **Repositório:** `ISupportRepository` com implementação Firestore + variante `unsupported` para plataformas sem Firebase.
- **Modelos:** `SupportIssue`, drafts e formulários internos.
- **Modais e DI** específicos do fluxo de suporte.

## Dependências

- `:core:ui`, `:core:analytics`, `:core:auth`, `:core:utils`.
- Firebase Firestore + Auth, `kotlinx-datetime`, `kotlinx-serialization`.
