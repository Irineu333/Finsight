## Why

O Support (issues/mensagens sobre Firestore + Auth) hoje não funciona no desktop: o `jvmMain` do `feature/support/impl` faz bind proposital em `UnsupportedSupportRepository` (retorna vazio e lança `error()` ao criar issue), e por isso a feature é ocultada da UI no desktop. O `FirebaseSupportRepository` já vive em `commonMain` e **já compila para o target JVM** — o único bloqueio é que o backend JVM do GitLive (`firebase-java-sdk`) nunca é inicializado em runtime. Ligar essa inicialização entrega o canal de suporte para os usuários de desktop reaproveitando o código de Firestore que já existe.

## What Changes

- Inicializar o `firebase-java-sdk` no boot do desktop (`app/desktop/main.kt`, antes do `startKoin`): configurar um `FirebasePlatform` com storage em arquivo e chamar `Firebase.initialize(context = null, options)` com `FirebaseOptions` parseados de uma cópia bundlada do `google-services.json`.
- Trocar o bind de `ISupportRepository` no `SupportModule.jvm.kt`: `UnsupportedSupportRepository` → `FirebaseSupportRepository(analytics = get(), crashlytics = get())` (analytics/crashlytics permanecem no-op no JVM — o repositório tolera).
- Remover `UnsupportedSupportRepository`.
- Deixar o Support **navegável no desktop**: como deixa de ser `mobile-only`/não-suportado, seus pontos de entrada (rail, botão de Support no `TopAppBar` do Dashboard) passam a ser exibidos no desktop.
- Adicionar ao catálogo de versões `dev.gitlive:firebase-app` e a dependência `firebase-java-sdk` (necessários no `app/desktop` para `Firebase.initialize`/`FirebaseOptions`/`FirebasePlatform`).

Fora de escopo: analytics e crashlytics no desktop — não há SDK Firebase para JVM (`firebase-java-sdk` não inclui esses serviços); continuam no-op.

## Capabilities

### New Capabilities
- `desktop-firebase-support`: inicialização do `firebase-java-sdk` no desktop (FirebasePlatform + FirebaseOptions a partir do `google-services.json`) e binding do `FirebaseSupportRepository` real no target JVM, habilitando Firestore/Auth do Support no desktop.

### Modified Capabilities
- `platform-adaptive-features`: o Support deixa de ser classificado como `mobile-only`; a exigência de ocultá-lo no desktop é substituída pela exibição dos seus pontos de entrada no desktop, já que a feature passa a ser suportada.

## Impact

- **Código**: `app/desktop/main.kt`, `app/desktop/build.gradle.kts`, `app/desktop/src/main/resources/` (cópia do `google-services.json`), `feature/support/impl/src/jvmMain/` (troca de bind + remoção do `UnsupportedSupportRepository`), catálogo de destinos/UI que aplica o flag `mobileOnly` ao Support.
- **Dependências**: `gradle/libs.versions.toml` (+ `gitlive-firebase-app`, + `firebase-java-sdk`); `app/desktop` passa a depender do stack Firebase JVM.
- **Runtime**: desktop passa a autenticar anonimamente e ler/escrever no Firestore (coleção `support_issues`) do mesmo projeto Firebase do Android; cria arquivo de persistência local do `firebase-java-sdk` no diretório de dados do usuário.
- **Riscos**: persistência offline do Firestore no port JVM difere da nativa (snapshots/realtime funcionam); inicialização é difícil de cobrir por unit test (validação manual, no estilo dos testes de desktop atuais).
