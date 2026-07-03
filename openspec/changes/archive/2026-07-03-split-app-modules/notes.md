# Notes: split-app-modules

## Débitos remanescentes (fora de escopo desta change)

Registrados para changes futuras:

1. **Migração AGP 9 completa** — mover os módulos de biblioteca de `com.android.library`
   para `com.android.kotlin.multiplatform.library`. Esta change apenas preparou o terreno
   (extraiu `:app:android` como `com.android.application` puro não-KMP); a migração das
   `library`s KMP para o novo plugin do AGP 9 é uma change separada.

2. **Separação de `:core:model`/domínio por features** — os modelos de domínio e tipos de
   erro ainda vivem monolíticos em `:core:model`. Separá-los por feature (respeitando os
   agregados emaranhados como `Transaction` que embute `Account`/`CreditCard`/`Invoice`/
   `Category`) é um débito conhecido, tratado em change futura.

## Verificação realizada nesta sessão

- **Compilação verde** (via Gradle) de todos os módulos `app/`:
  - `:app:shared` (JVM, Android, iOS) + `:app:shared:jvmTest`;
  - `:app:desktop:compileKotlin`;
  - `:app:android:compileDebugKotlin` + `:app:android:assembleDebug` (APK gerado);
  - `:app:ios:compileKotlinIosSimulatorArm64` + `:app:ios:linkDebugFrameworkIosSimulatorArm64`
    (framework `ComposeApp` linkado com todos os `export()`).
- **XcodeGen** regenerou `iosApp/iosApp.xcodeproj` apontando para
  `:app:ios:embedAndSignAppleFrameworkForXcode`.

## Pendências que exigem verificação manual (device/simulador/janela)

Os smoke tests interativos não puderam ser executados no ambiente headless:

- **4.3** — abrir o APK em device/emulador (navegar, persistir).
- **5.2** — `./gradlew :app:desktop:run` (janela abre, app funciona).
- **6.3** — build iOS no simulador via Xcode (app abre, navega, persiste).

## Observações: verificações bloqueadas por ambiente (pré-existentes)

Duas verificações agregadas não fecham verdes neste ambiente headless por motivos
**pré-existentes e independentes deste split** (o `:composeApp` tinha o mesmo setup):

1. **`./gradlew check`** — o classpath de `androidTest` não resolve o Firebase
   (`com.google.firebase:firebase-analytics:` sem versão), reproduzível na `HEAD` anterior
   à change → ambiental (offline/BOM).
2. **`./gradlew allTests`** — o **link dos binários de teste iOS**
   (`linkDebugTestIos*`) falha com `ld: framework 'FirebaseCore' not found`. O gitlive-firebase
   iOS (via `:core:analytics`/`:core:crashlytics`) exige os frameworks do Firebase iOS, que só
   existem no build do Xcode (SPM declarado em `iosApp/project.yml`), não no link standalone do
   Gradle. Atinge vários módulos que dependem de `:core:analytics` (ex.: `:feature:report:impl`,
   não tocado por esta change) → ambiental.

Verificação efetiva usada no lugar: **`./gradlew jvmTest`** (testes unitários de todos os
módulos, incl. `:app:shared:jvmTest`) + os alvos de compilação/assemble/link-framework acima.
