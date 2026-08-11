# Adaptadores de biblioteca externa: api/impl

> Este diretório abriga os módulos que adaptam **bibliotecas externas sem suporte oficial a
> Kotlin Multiplatform**, para que o resto do app as consuma como se tivessem.
> Este documento define o que entra aqui, a estrutura e as regras. Ele não é verificado
> pelo build: vale por revisão, e é a referência normativa.

---

## O que é um módulo de `library/`

Um módulo daqui existe por um motivo só: **uma dependência que o app precisa não é
multiplataforma**, e sem alguém para traduzi-la o `commonMain` não pode nomeá-la.

Hoje são três, todos sobre o Firebase — que é um SDK Android e um SDK iOS, alcançados por
um port da comunidade (os bindings GitLive), não um produto multiplataforma:

| Módulo | Contrato | Fornecedor |
|---|---|---|
| **`analytics`** | `Analytics` + o catálogo de `Event` | Firebase Analytics (Android, iOS) · no-op (Desktop) |
| **`crashlytics`** | `Crashlytics` | Firebase Crashlytics (Android, iOS) · no-op (Desktop) |
| **`auth`** | `AuthService`, `AuthError` | Firebase Auth (Android, iOS) · no-op (Desktop) |

**O que não entra aqui.** Um módulo de `library/` adapta um fornecedor externo; ele não
tem domínio nem regra de negócio. Se o que você está escrevendo decide alguma coisa sobre
dinheiro, categoria ou fatura, é `:core:*` ou uma feature — mesmo que use uma dependência
externa para isso. E se a biblioteca **já é** multiplataforma (Room, Ktor, Koin,
kotlinx-*), não há nada para adaptar: declare-a onde ela é usada.

---

## Estrutura

Cada adaptador é um par de módulos Gradle, o mesmo par das features:

```
library/
└── <nome>/
    ├── api/    ← o contrato, sem fornecedor
    └── impl/   ← um fornecedor por plataforma
```

| Módulo | Contém | Não contém |
|---|---|---|
| **api** | A interface que o app consome e os tipos que ela usa nas assinaturas | O nome de qualquer fornecedor, em qualquer source set |
| **impl** | Um `actual` por plataforma, o módulo Koin e as dependências do SDK | Qualquer coisa que um consumidor precise importar |

O **`api` não conhece o fornecedor** — é o que permite ao Desktop não ter nenhum, e ao app
trocar de fornecedor sem tocar em quem consome. `library/crashlytics/api` inteiro é isto:

```kotlin
interface Crashlytics {
    fun setUserId(id: String?)
    fun recordException(e: Throwable)
}
```

---

## Regras de dependência

Valem as mesmas quatro regras do `feature/README.md`, porque o par é o mesmo — e a tabela
de dependências permitidas está lá, já com as colunas de `library`.

Em uma linha: **uma feature declara `:library:<nome>:api`; só o `:app:shared` declara um
`impl`.**

Isso não é etiqueta. O `impl` de iOS carrega um `cinterop` sobre frameworks Objective-C que
apenas o Xcode resolve, via SPM — o Gradle não tem cópia deles. Qualquer módulo que alcance
esse `cinterop` no seu classpath **não consegue linkar um executável de teste iOS**: o `ld`
falha com `framework 'FirebaseCore' not found`. Manter o `impl` fora das features é o que
mantém a suíte iOS delas executável (ver `CLAUDE.md`, seção *Conventions*).

---

## O padrão de fornecedor por plataforma

O `impl` expõe um módulo Koin comum que inclui um `expect val` por plataforma:

```kotlin
// library/analytics/impl — commonMain
val analyticsModule = module {
    includes(analyticsPlatformModule)
}

expect val analyticsPlatformModule: Module
```

```kotlin
// library/analytics/impl — iosMain
actual val analyticsPlatformModule = module {
    single<Analytics> { FirebaseAnalyticsImpl() }
}
```

```kotlin
// library/analytics/impl — jvmMain
actual val analyticsPlatformModule = module {
    single<Analytics> { NoOpAnalytics() }
}
```

O `:app:shared` agrega o módulo comum em `appModules` e não sabe qual `actual` respondeu.

### O no-op é um fornecedor, não um dublê de teste

`NoOpAnalytics`, `NoOpCrashlytics` e `NoOpAuthService` vivem em `jvmMain` do `impl`, ao
lado dos fornecedores Firebase, porque é isso que eles são: a resposta do Desktop, em
produção, a um serviço que **não existe naquela plataforma**. `NoOpAuthService.getUserId()`
devolve `null.right()` — não há id anônimo em Desktop, e isso não é falha.

Um teste que precisa de um `Analytics` silencioso escreve o seu próprio dublê, no
`commonTest` de quem testa. Reaproveitar o no-op de produção acopla o teste a uma decisão
de plataforma: no dia em que o Desktop ganhar um fornecedor de verdade, o no-op muda ou
desaparece, e o teste quebra por um motivo que nada tem a ver com o que ele verifica.

---

## Como adicionar um adaptador

1. Confirme que a biblioteca **não é** multiplataforma e que o que você quer é adaptá-la —
   não embrulhar por embrulhar.
2. Crie `library/<nome>/api` com a interface e nada além do que as assinaturas dela pedem.
   Se um tipo de terceiro aparece na assinatura (como `Either` em `AuthService`), declare-o
   com `api(...)` para que o consumidor o enxergue.
3. Crie `library/<nome>/impl` com o `expect val <nome>PlatformModule`, um `actual` por
   source set de plataforma e as dependências do SDK em `androidMain`/`iosMain`. Toda
   plataforma precisa de um `actual` — se o serviço não existe lá, o `actual` é o no-op.
4. Registre os dois em `settings.gradle.kts`, sob `// Library`.
5. Some o módulo Koin do `impl` a `appModules`, em `:app:shared`, e declare lá o par:
   `api(...api)` e `implementation(...impl)`.
6. Se o tipo precisa ser visível ao Swift, acrescente o **`api`** ao `export()` do
   `:app:ios` — nunca o `impl`.
