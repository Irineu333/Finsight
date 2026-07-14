## Context

O `feature/support/impl` é um módulo KMP com target `jvm`. Seu `commonMain` contém o `FirebaseSupportRepository`, que usa `dev.gitlive.firebase.firestore`/`auth` diretamente e **já compila para JVM** (os artefatos JVM do GitLive `2.1.0` já resolvem). O que impede o Support de rodar no desktop hoje:

1. O backend JVM do GitLive é o `firebase-java-sdk` (um port do Firebase Android SDK), que exige inicialização manual (`FirebasePlatform` + `Firebase.initialize`) — no Android isso é feito automaticamente pelo plugin `google-services`; no desktop, ninguém inicializa. Sem isso, `Firebase.firestore`/`Firebase.auth` lançam em runtime.
2. Por causa disso, o `SupportModule.jvm.kt` faz bind proposital em `UnsupportedSupportRepository`, e o Support é classificado como `mobile-only` e ocultado da UI no desktop (spec `platform-adaptive-features`).

O escopo aqui é **apenas o Support**. Analytics e crashlytics permanecem no-op no JVM — não há SDK Firebase para esses serviços em JVM (o `firebase-java-sdk` não os inclui). O `FirebaseSupportRepository` recebe `Analytics`/`Crashlytics` injetados e tolera implementações no-op.

## Goals / Non-Goals

**Goals:**
- Inicializar o `firebase-java-sdk` no desktop e ligar o `FirebaseSupportRepository` real no target JVM.
- Reaproveitar 100% do código de Firestore/Auth já existente em `commonMain` — sem reimplementar repositório.
- Tornar o Support navegável no desktop (deixa de ser `mobile-only`).
- Manter a config Firebase como fonte única com o Android (`google-services.json`).

**Non-Goals:**
- Analytics e crashlytics no desktop (sem SDK JVM; seguem no-op).
- Alterar o comportamento de Support no Android/iOS.
- Cobrir a inicialização do Firebase com unit tests.

## Decisions

### Decisão 1 — Inicializar no `app/desktop/main.kt`, antes do `startKoin`
O `main.kt` é o composition root do desktop e o análogo direto do que o plugin `google-services` faz no boot do Android. A inicialização precisa ocorrer antes de qualquer resolução de `ISupportRepository`, então precede o `startKoin`.

- **Alternativa considerada**: expor a init via um módulo `core` (jvmMain). Rejeitada por ora — adiciona cerimônia (um novo core só para init de plataforma) sem ganho claro, já que a init é desktop-only e naturalmente pertence ao entrypoint da plataforma. O `app/desktop` já concentra wiring específico de plataforma (ex.: `WindowStatePersistence`).

### Decisão 2 — `FirebaseOptions` a partir de uma cópia bundlada do `google-services.json`
Uma cópia do `google-services.json` do projeto vai para `app/desktop/src/main/resources/`, é lida no boot e mapeada para `FirebaseOptions` (`apiKey`, `applicationId`, `projectId`, `gcmSenderId`/`messagingSenderId`, `storageBucket`). Mantém fonte única com o Android e evita divergência de valores.

- **Alternativa considerada**: `FirebaseOptions(...)` com valores hardcoded em Kotlin. Rejeitada — duplica config que já existe e tende a divergir. (Os valores são config de cliente, não segredo; podem ser versionados.)

### Decisão 3 — `FirebasePlatform` com storage em arquivo no diretório de dados do usuário
O `firebase-java-sdk` exige um `FirebasePlatform` provendo persistência key/value (equivalente ao SharedPreferences) e log. A implementação grava em arquivo sob o diretório de dados do usuário por-OS, reutilizando o mesmo padrão de resolução de path já usado pelo `WindowStatePersistence` do desktop.

### Decisão 4 — Trocar apenas o binding no `SupportModule.jvm.kt`
`single<ISupportRepository> { FirebaseSupportRepository(analytics = get(), crashlytics = get()) }`, espelhando o `SupportModule.android.kt`. O `UnsupportedSupportRepository` é removido. Nenhuma mudança em `commonMain`.

### Decisão 5 — Reclassificar Support como suportado no desktop
Como o backend passa a funcionar, o Support deixa de ser `mobile-only` e seus pontos de entrada passam a aparecer no desktop. Há dois gates concretos hoje:
- `AppNavCatalog.kt` — o destino `SupportGraph` tem `mobileOnly = true`, que o exclui do rail do desktop.
- `DashboardScreen.kt` — o `IconButton` de Support no `TopAppBar` está envolto em `if (!isDesktop)`.

Ambos são removidos. Isso modifica a capability `platform-adaptive-features`.

## Risks / Trade-offs

- **Persistência offline do Firestore no port JVM difere da nativa** → snapshots/realtime funcionam; o comportamento offline pode não ser idêntico ao Android/iOS. Aceitável para o fluxo de Support (predominantemente online); validar manualmente cenários de reconexão.
- **Inicialização difícil de unit-testar** → validação manual rodando o desktop e exercitando criar issue / responder / observar mensagens, no estilo dos testes de desktop existentes. A lógica de parse do `google-services.json` → `FirebaseOptions` pode ser isolada e coberta por unit test.
- **Dependências JVM do stack Firebase adicionadas ao `app/desktop`** → aumenta o tamanho do artefato desktop; restrito ao necessário (`firebase-app` + `firebase-java-sdk`, firestore/auth já transitivos via `:feature:support:impl`).
- **`firebase-java-sdk` tem versão própria** (não segue o `version.ref = gitlive-firebase`) → fixar a versão compatível com o GitLive `2.1.0` no catálogo.

## Open Questions

- Confirmar a versão exata do `dev.gitlive:firebase-java-sdk` compatível com GitLive `2.1.0` ao adicionar ao catálogo.
- Definir o caminho concreto do diretório de storage do `FirebasePlatform` por-OS (reusar helper do `WindowStatePersistence` se aplicável).
