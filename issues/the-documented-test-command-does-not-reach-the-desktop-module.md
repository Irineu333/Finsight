---
area: transversal
severity: medium
type: test
---

# O comando de teste documentado não alcança o módulo desktop

## Invariante

Toda função `@Test` do repositório é executada pelo comando que o `CLAUDE.md` documenta
como **"All tests"**.

Hoje é falso: `./gradlew jvmTest` não toca `:app:desktop`. Os **8 testes** de
`app/desktop/src/test` nunca rodam por esse comando — nem por nenhum outro dos cinco
listados no bloco `## Commands`.

## Mecânica

`jvmTest` é o nome que o plugin **Kotlin Multiplatform** dá à task de teste do target
`jvm`. O `:app:desktop` não é KMP: aplica `alias(libs.plugins.kotlinJvm)`, e `kotlin("jvm")`
cria a task chamada **`test`**. Um `./gradlew jvmTest` resolve o nome por todos os projetos
e simplesmente não encontra candidato ali — sem erro, porque outros projetos têm a task.

Nenhum outro comando documentado cobre a lacuna: `:app:shared:testDebugUnitTest` é escopado
a um módulo, e `assembleDebug` / `desktop:run` / `maestro test` não são de teste.

## Evidência

- `./gradlew jvmTest --dry-run | grep ":app:desktop"` → **nenhuma linha**. O módulo não
  entra no grafo da task documentada
- `app/desktop/build.gradle.kts` — `alias(libs.plugins.kotlinJvm)` no bloco `plugins`, e
  nenhum `tasks.register("jvmTest")` que dê ao módulo o nome documentado
- `app/desktop/src/test/.../window/WindowStatePersistenceTest.kt` — 7 `@Test`
- `app/desktop/src/test/.../firebase/GoogleServicesParserTest.kt` — 1 `@Test`
- `CLAUDE.md`, bloco `## Commands` — `./gradlew jvmTest    # All tests`

A afirmação que originou a divergência está na mensagem de `bc5174062`: *"commonTest and
jvmTest are the only test source sets in the repository"*. Já era falsa quando escrita —
`app/desktop/src/test` existe desde `f95e127f8`, um mês antes.

## Consequência

Persistência e restauração do estado da janela principal e o parser do `google-services` do
desktop são as duas únicas coisas cobertas por esses 8 testes, e nada os executa. Uma
regressão em qualquer uma passa por toda a verificação documentada em verde — e "a suíte
passa" continua sendo dito com honestidade por quem rodou o comando que o próprio
repositório manda rodar.

Não é um teste ausente: é um teste presente que ninguém executa. Ele compra confiança sem
entregar nada, e nada na árvore sinaliza a diferença.

## Sugestão

Ou corrigir a documentação para `./gradlew jvmTest :app:desktop:test`, ou dar ao
`:app:desktop` um alias `jvmTest` que dependa de `test`, para que o nome documentado passe
a alcançá-lo sem que ninguém precise lembrar. Não vinculante.
