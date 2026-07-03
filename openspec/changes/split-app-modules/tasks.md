# Tasks: split-app-modules

## 1. Migração Koin para os cores (composeApp ainda vivo)

- [ ] 1.1 Mover `databaseModule` + `databasePlatformModule` (expect/actual android/ios/jvm) de `composeApp` para `:core:database`, adicionando Koin às dependências do módulo se necessário
- [ ] 1.2 Criar `commonModule` em `:core:common` com `single { Settings() }`, `single { CurrencyFormatter() }` e `factory { DebounceManager(delayMillis = 500L) }`; migrar dependências `multiplatform-settings`/`multiplatform-settings-no-arg` do composeApp para `:core:common`
- [ ] 1.3 Criar `designsystemModule` em `:core:designsystem` com `single { ModalManager() }`
- [ ] 1.4 Remover `ShellModule.kt` e `DatabaseModule.*` do composeApp; atualizar a agregação Koin dos entry points para incluir `databaseModule`, `commonModule` e `designsystemModule`; rodar `./gradlew check`

## 2. Preparação da estrutura app/

- [ ] 2.1 Criar convention plugin `finsight.app.shared` no `build-logic` (targets KMP android-library/jvm/ios, Compose, serialization, opt-ins) com verificação de dependências que admite `feature:*:impl` apenas nesta convenção
- [ ] 2.2 Registrar `:app:shared`, `:app:android`, `:app:desktop` e `:app:ios` no `settings.gradle.kts`

## 3. :app:shared

- [ ] 3.1 Mover `App.kt`, `AppNavHost.kt`, `AppNavigationDispatcher.kt`, `HomeScreen.kt`, `HomeRoute.kt` e a lista de agregação Koin (`appModules`) do composeApp para `app/shared/src/commonMain` (via `git mv`)
- [ ] 3.2 Criar `app/shared/build.gradle.kts` aplicando `finsight.app.shared` com as dependências de cores, apis e impls (herdadas do composeApp); mover `ComposeAppCommonTest` para `commonTest`
- [ ] 3.3 Compilar `:app:shared` isoladamente (`./gradlew :app:shared:build -x lint` ou equivalente) antes de prosseguir

## 4. :app:android

- [ ] 4.1 Criar `app/android/build.gradle.kts` não-KMP (`com.android.application` + `kotlin.android` + compose) com applicationId, versionCode/versionName, signing/keystore, `googleServices`, `firebaseCrashlytics` e dependência em `:app:shared`
- [ ] 4.2 Mover `composeApp/src/androidMain/` para `app/android/src/main/` (Manifest, `MainActivity`, `AndroidApp`, mipmaps, res, xml) e `google-services.json` para `app/android/`; redistribuir deps androidMain (activity-compose, koin-android, gitlive analytics/crashlytics — verificar se pertencem ao app ou aos cores)
- [ ] 4.3 `./gradlew :app:android:assembleDebug` e smoke test no device/emulador (app abre, navega, persiste)

## 5. :app:desktop

- [ ] 5.1 Criar `app/desktop/build.gradle.kts` (`kotlin("jvm")` + compose) com `mainClass`, `nativeDistributions` (Dmg/Msi/Deb, packageName, packageVersion) e dependência em `:app:shared`; mover `main.kt`
- [ ] 5.2 `./gradlew :app:desktop:run` e smoke test (janela abre, app funciona)

## 6. :app:ios

- [ ] 6.1 Criar `app/ios/build.gradle.kts` (KMP só-iOS) com o framework `ComposeApp` — `baseName`, `bundleId`, `isStatic`, `linkerOpts` e lista de `export()` preservados do composeApp — declarando `api()` para todos os módulos exportados + dependência em `:app:shared`; mover `MainViewController.kt`
- [ ] 6.2 Atualizar `iosApp/project.yml` para `:app:ios:embedAndSignAppleFrameworkForXcode` e regenerar projeto com XcodeGen
- [ ] 6.3 Build iOS no simulador via Xcode e smoke test (app abre, navega, persiste)

## 7. Remoção do composeApp

- [ ] 7.1 Remover `include(":composeApp")` do `settings.gradle.kts`, apagar o diretório `composeApp/` e varrer referências remanescentes (`grep -rn "composeApp"` fora de `openspec/`/docs históricos)
- [ ] 7.2 `./gradlew check` e `./gradlew allTests` verdes com a árvore final

## 8. Documentação e tooling

- [ ] 8.1 Atualizar `CLAUDE.md` (comandos de teste, seção de estrutura de módulos) e `feature/README.md` (papel do shell → `:app:shared`)
- [ ] 8.2 Atualizar skill `bump-version` (versionCode/Name em `app/android`, packageVersion em `app/desktop`)
- [ ] 8.3 Registrar em nota da change os débitos remanescentes fora de escopo: migração para `com.android.kotlin.multiplatform.library` (AGP 9) e separação de `:core:model`/domínio por features
