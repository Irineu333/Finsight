# desktop-firebase-support Specification

## Purpose
TBD - created by archiving change enable-desktop-support-firebase. Update Purpose after archive.
## Requirements
### Requirement: Inicialização do Firebase no desktop
O app desktop SHALL inicializar o `firebase-java-sdk` durante o boot, antes de qualquer resolução de dependências que dependam do Firebase (antes do `startKoin`). A inicialização SHALL registrar um `FirebasePlatform` que persiste dados key/value em arquivo sob o diretório de dados do usuário e SHALL chamar `Firebase.initialize` com `FirebaseOptions` derivados da configuração do projeto Firebase.

#### Scenario: Firebase inicializado antes do Koin
- **WHEN** o app desktop inicia
- **THEN** o `firebase-java-sdk` é inicializado (FirebasePlatform registrado e `Firebase.initialize` chamado) antes de o `startKoin` resolver o grafo de dependências

#### Scenario: Persistência local do FirebasePlatform
- **WHEN** o Firebase é inicializado no desktop
- **THEN** o `FirebasePlatform` grava e lê seus dados key/value em arquivo no diretório de dados do usuário, permitindo que a sessão de Auth anônima persista entre execuções

### Requirement: FirebaseOptions derivados do google-services.json
As credenciais de cliente do desktop SHALL ser lidas de uma cópia bundlada do `google-services.json` (mesmo projeto Firebase do Android) e mapeadas para `FirebaseOptions`, mantendo fonte única de configuração. Os campos `apiKey`, `applicationId`, `projectId`, `gcmSenderId` e `storageBucket` SHALL ser preenchidos a partir desse arquivo.

#### Scenario: Config parseada do arquivo bundlado
- **WHEN** o app desktop inicia e lê o `google-services.json` bundlado nos resources
- **THEN** os `FirebaseOptions` resultantes contêm os mesmos `apiKey`, `applicationId`, `projectId`, `gcmSenderId` e `storageBucket` do projeto Firebase usado pelo Android

### Requirement: Support usa Firestore/Auth reais no desktop
No target JVM, a dependência `ISupportRepository` SHALL ser resolvida para o `FirebaseSupportRepository` (backed por Firestore + Auth), e não mais para um repositório stub. O `FirebaseSupportRepository` SHALL operar com implementações no-op de `Analytics` e `Crashlytics` no desktop.

#### Scenario: Binding do repositório real no JVM
- **WHEN** o grafo de Koin do desktop resolve `ISupportRepository`
- **THEN** a instância retornada é `FirebaseSupportRepository`, e não um repositório de fallback não-suportado

#### Scenario: Criar e observar issues no desktop
- **WHEN** o usuário no desktop cria uma issue de suporte e observa suas issues/mensagens
- **THEN** a issue é autenticada anonimamente e persistida na coleção `support_issues` do Firestore, e as issues/mensagens são observadas via snapshots do Firestore

#### Scenario: Analytics e crashlytics permanecem no-op
- **WHEN** o `FirebaseSupportRepository` invoca `analytics.setUserId` ou `crashlytics.recordException` no desktop
- **THEN** as chamadas são no-op e não impedem a operação do Support

