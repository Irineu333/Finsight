## 1. Dependências e config

- [x] 1.1 Adicionar `gitlive-firebase-app = { module = "dev.gitlive:firebase-app", version.ref = "gitlive-firebase" }` ao `gradle/libs.versions.toml`
- [x] 1.2 Adicionar `firebase-java-sdk` ao catálogo com a versão compatível com GitLive `2.1.0` (versão própria, confirmar valor)
- [x] 1.3 Declarar `libs.gitlive.firebase.app` e `libs.firebase.java.sdk` como dependências do `app/desktop/build.gradle.kts`
- [x] 1.4 Copiar o `google-services.json` do projeto para `app/desktop/src/main/resources/`

## 2. Inicialização do Firebase no desktop

- [x] 2.1 Implementar leitura/parse do `google-services.json` bundlado → `FirebaseOptions` (`apiKey`, `applicationId`, `projectId`, `gcmSenderId`, `storageBucket`), em unidade isolável para teste
- [x] 2.2 Implementar um `FirebasePlatform` com storage key/value em arquivo no diretório de dados do usuário (reusar o padrão de path do `WindowStatePersistence`) + log
- [x] 2.3 No `app/desktop/main.kt`, registrar o `FirebasePlatform` e chamar `Firebase.initialize(context, options)` antes do `startKoin`
  - **Correção inesperada:** o overload JVM do GitLive faz `context as android.content.Context` (cast não-nulo), então `context = null` lançava `NullPointerException` no boot. Passamos o stub `android.app.Application()` do `firebase-java-sdk` (subclasse de `Context`) como contexto. Corrigido em `Fix(Desktop): pass Application context to Firebase.initialize`.

## 3. Binding do Support no JVM

- [x] 3.1 Alterar `SupportModule.jvm.kt` para `single<ISupportRepository> { FirebaseSupportRepository(analytics = get(), crashlytics = get()) }`
- [x] 3.2 Remover `feature/support/impl/src/jvmMain/.../database/UnsupportedSupportRepository.kt`

## 4. Exibir Support no desktop

- [x] 4.1 Em `AppNavCatalog.kt`, remover `mobileOnly = true` do destino `SupportGraph` (linha ~96) → passa a aparecer no rail do desktop
- [x] 4.2 Em `DashboardScreen.kt`, remover o gate `if (!isDesktop)` que oculta o `IconButton` de Support no `TopAppBar` (linha ~99) → botão passa a aparecer no desktop
- [x] 4.3 Revisar o KDoc que menciona "mobile-only Support entry" em `AppNavCatalog.kt` (~linha 41) e `NavDestination.kt` (~linha 10) para refletir que Support já não é mobile-only
- [x] 4.4 Em `SupportIssueScreen.kt`, aplicar padding horizontal no card de responder (`ReplyComposer`) quando o rail está visível (janela wide) via `isWideWindow()`, para o card não ficar colado ao rail no desktop

## 5. Verificação

- [x] 5.1 Unit test do parse `google-services.json` → `FirebaseOptions`
- [ ] 5.2 `./gradlew :app:desktop:run` — validar manualmente: auth anônima, criar issue, responder, observar issues/mensagens via snapshots
- [ ] 5.3 Validar manualmente que a sessão de Auth persiste entre execuções (arquivo do FirebasePlatform)
- [ ] 5.4 `./gradlew check` e `./gradlew allTests` verdes; confirmar que Android/iOS não regridem
