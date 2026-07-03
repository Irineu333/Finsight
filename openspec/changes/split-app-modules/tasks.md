# Tasks: split-app-modules

## 1. Migração Koin para os cores (composeApp ainda vivo)

- [x] 1.1 Mover `databaseModule` + `databasePlatformModule` (expect/actual android/ios/jvm) de `composeApp` para `:core:database`, adicionando Koin às dependências do módulo se necessário
- [x] 1.2 Criar `commonModule` em `:core:common` com `single { Settings() }`, `single { CurrencyFormatter() }` e `factory { DebounceManager(delayMillis = 500L) }`; migrar dependências `multiplatform-settings`/`multiplatform-settings-no-arg` do composeApp para `:core:common`
- [x] 1.3 Criar `designsystemModule` em `:core:designsystem` com `single { ModalManager() }`
- [x] 1.4 Remover `ShellModule.kt` e `DatabaseModule.*` do composeApp; atualizar a agregação Koin dos entry points para incluir `databaseModule`, `commonModule` e `designsystemModule`; rodar `./gradlew check` (compilação JVM verde; `check` completo bloqueado por issue pré-existente de resolução Firebase no `androidTest`, sem relação com esta change)

## 2. Preparação da estrutura app/

- [x] 2.1 Criar convention plugin `finsight.app.shared` no `build-logic` (targets KMP android-library/jvm/ios, Compose, serialization, opt-ins) com verificação de dependências que admite `feature:*:impl` apenas nesta convenção
- [x] 2.2 Registrar `:app:shared`, `:app:android`, `:app:desktop` e `:app:ios` no `settings.gradle.kts`

## 3. :app:shared

- [x] 3.1 Mover `App.kt`, `AppNavHost.kt`, `AppNavigationDispatcher.kt`, `HomeScreen.kt`, `HomeRoute.kt` e a lista de agregação Koin (`appModules`) do composeApp para `app/shared/src/commonMain` (via `git mv`) — `appModules` criado consolidando a lista antes duplicada nos 3 entry points
- [x] 3.2 Criar `app/shared/build.gradle.kts` aplicando `finsight.app.shared` com as dependências de cores, apis e impls (herdadas do composeApp); mover `ComposeAppCommonTest` para `commonTest` (+ `kotlinx.datetime` em `commonTest`)
- [x] 3.3 Compilar `:app:shared` isoladamente — `:app:shared:compileKotlinJvm`/iOS/Android verdes + `:app:shared:jvmTest` passa

## 4. :app:android

- [x] 4.1 Criar `app/android/build.gradle.kts` não-KMP (`com.android.application` + `kotlin.android` + compose) com applicationId, versionCode/versionName, signing/keystore, `googleServices`, `firebaseCrashlytics` e dependência em `:app:shared` (aliases `kotlinAndroid`/`kotlinJvm` adicionados ao catalog + root `apply false`)
- [x] 4.2 Mover `composeApp/src/androidMain/` para `app/android/src/main/` (Manifest, `MainActivity`, `AndroidApp`, mipmaps, res, xml) e `google-services.json` para `app/android/`; deps androidMain: `activity-compose`/`koin-android` no app, gitlive analytics/crashlytics permanecem nos cores (transitivos)
- [x] 4.3 `./gradlew :app:android:assembleDebug` verde (APK gerado). **Smoke test em device/emulador pendente de verificação manual** (ambiente headless)

## 5. :app:desktop

- [x] 5.1 Criar `app/desktop/build.gradle.kts` (`kotlin("jvm")` + compose) com `mainClass`, `nativeDistributions` (Dmg/Msi/Deb, packageName, packageVersion) e dependência em `:app:shared`; mover `main.kt`
- [x] 5.2 `:app:desktop:compileKotlin` verde. **`./gradlew :app:desktop:run` + smoke test (janela) pendente de verificação manual** (ambiente headless)

## 6. :app:ios

- [x] 6.1 Criar `app/ios/build.gradle.kts` (KMP só-iOS) com o framework `ComposeApp` — `baseName`, `bundleId`, `isStatic`, `linkerOpts` e lista de `export()` preservados do composeApp — declarando `api()` para todos os módulos exportados + dependência em `:app:shared`; mover `MainViewController.kt` (verificado: `linkDebugFrameworkIosSimulatorArm64` verde)
- [x] 6.2 Atualizar `iosApp/project.yml` para `:app:ios:embedAndSignAppleFrameworkForXcode` e regenerar projeto com XcodeGen (`xcodegen generate` — `project.pbxproj` aponta para o novo módulo)
- [x] 6.3 Framework linka via Gradle. **Build iOS no simulador via Xcode + smoke test pendente de verificação manual** (requer Xcode interativo)

## 7. Remoção do composeApp

- [x] 7.1 Remover `include(":composeApp")` do `settings.gradle.kts`, apagar o diretório `composeApp/` e varrer referências remanescentes — restantes só em docs históricos (`docs/features/**`) e docs vivas atualizadas (`README.md`/`CLAUDE.md`/`feature/README.md`/`project.yml`/`bump-version`)
- [x] 7.2 `./gradlew jvmTest` verde (todos os testes unitários, incl. `:app:shared:jvmTest`) + compilação/assemble/link-framework verdes por plataforma. `check` completo e `allTests` bloqueados por issues **pré-existentes/ambientais** (Firebase no `androidTest`; `ld: framework 'FirebaseCore' not found` no link de teste iOS — atinge módulos não tocados como `:feature:report:impl`) — ver `notes.md`

## 8. Documentação e tooling

- [x] 8.1 Atualizar `CLAUDE.md` (comandos de teste, seção de estrutura de módulos) e `feature/README.md` (papel do shell → `:app:shared`) — também atualizado o `README.md` raiz (comandos + descrição de módulos)
- [x] 8.2 Atualizar skill `bump-version` (versionCode/Name em `app/android`, packageVersion em `app/desktop`)
- [x] 8.3 Registrar em nota da change os débitos remanescentes fora de escopo: migração para `com.android.kotlin.multiplatform.library` (AGP 9) e separação de `:core:model`/domínio por features — em `notes.md`
