Projeto Kotlin Multiplatform com alvos Android, Desktop (JVM) e iOS.

## Estrutura de módulos

O app é modularizado **por feature** no padrão **api/impl**, sobre módulos **core**
compartilhados; as regras de dependência são impostas mecanicamente pelos convention
plugins do `build-logic`.

- `build-logic/` — convention plugins (`finsight.kmp.library` / `compose.library` /
  `feature.api` / `feature.impl`).
- `core/*` — `common`, `ledger`, `model`, `navigation`, `resources`, `designsystem`, `ui`,
  `database`, `analytics`, `crashlytics`, `auth`.
- `feature/<nome>/{api,impl}` — um par por feature (support, categories, budgets,
  accounts, creditcards, recurring, transactions, report, dashboard). A `api` guarda
  rotas, interfaces de repositório/use case e o `<Nome>Entry`; o `impl` guarda as
  telas, ViewModels, use cases, repositórios e o módulo Koin da feature.
- `app/*` — o app partido por responsabilidade: `shared` (shell/agregador KMP: `App` raiz,
  `AppNavHost`, wiring do Koin via `appModules`), `android` (`com.android.application`),
  `desktop` (`kotlin("jvm")`), `ios` (framework KMP só-iOS, hospeda
  `:app:ios:embedAndSignAppleFrameworkForXcode`).

> Veja **`feature/README.md`** para as regras normativas de dependência e o padrão de entry
> point, e **`CLAUDE.md`** para o mapa completo de módulos.

## O razão

Dinheiro é modelado como um **razão de partidas dobradas balanceado**, e esse é o único
modelo — nenhum saldo guardado numa coluna, nenhuma segunda forma de calcular um número.
Vive em `:core:ledger`, que não depende de nenhum outro módulo do projeto: toda escrita é
um conjunto de lançamentos que soma zero, toda figura (saldo, devido de fatura, gasto por
categoria, patrimônio) é `Σ lançamentos`, e as features são sabores dessa única verdade.

> O razão é a fonte de verdade, com garantia contábil; as features são sabores dessa
> verdade, e as fachadas, o açúcar.

> Veja **`core/ledger/README.md`** — a referência normativa do razão: vocabulário,
> superfícies de leitura e escrita, as duas portas e o que é derivado em vez de persistido.

* [/app/shared](./app/shared/src) é o shell do app — os entry points Compose compartilhados
  entre os alvos. Os entry points de plataforma vivem em [/app/android](./app/android/src/main),
  [/app/desktop](./app/desktop/src/main) e [/app/ios](./app/ios/src/iosMain).

### Compilar e rodar o app Android

Para compilar e rodar a versão de desenvolvimento do app Android, use a configuração de
execução no widget da toolbar da IDE ou compile direto pelo terminal:

- em macOS/Linux
  ```shell
  ./gradlew :app:android:assembleDebug
  ```
- em Windows
  ```shell
  .\gradlew.bat :app:android:assembleDebug
  ```

### Compilar e rodar o app Desktop (JVM)

Para compilar e rodar a versão de desenvolvimento do app desktop, use a configuração de
execução no widget da toolbar da IDE ou rode direto pelo terminal:

- em macOS/Linux
  ```shell
  ./gradlew :app:desktop:run
  ```
- em Windows
  ```shell
  .\gradlew.bat :app:desktop:run
  ```

---

Saiba mais sobre [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
