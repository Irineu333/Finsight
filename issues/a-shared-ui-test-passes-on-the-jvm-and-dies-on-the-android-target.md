---
area: designsystem
severity: medium
type: test
---

# Um teste de `commonTest` passa no JVM e morre nos quatro casos no target Android

## Invariante

Um teste de `commonTest` roda em todos os targets em que o módulo é compilado.

Hoje é falso: `FloatingActionMenuTest` vive em `core/designsystem/src/commonTest`, passa nos
seus 4 casos em `jvmTest` e falha nos **mesmos 4** em `testDebugUnitTest`, cada um com
`java.lang.NullPointerException: Cannot invoke "String.toLowerCase(java.util.Locale)" because
"android.os.Build.FINGERPRINT" is null`.

É a **única** classe do repositório que falha no target Android — a varredura de todo
`build/test-results/testDebugUnitTest/` devolve só ela.

## Mecânica

O ambiente de unit test do Android roda numa JVM comum, com um `android.jar` de stubs cujos
métodos lançam por padrão e cujos campos estáticos ficam nulos. Compose lê
`android.os.Build.FINGERPRINT` ao decidir se está num emulador, e o campo nulo derruba a
composição antes de qualquer asserção do teste.

O contorno existe e é uma linha de build (`testOptions.unitTests.isReturnDefaultValues`, ou
Robolectric), mas nenhum módulo do projeto o declara — o que só não aparece porque este é o
único teste de `commonTest` que compõe uma árvore Compose alcançando esse campo.

A falha **não é desta mudança**: verificada num worktree de `ccfddddd5`, sem nenhuma linha da
change `mcp-stdio-launcher`, com a mesma classe e as mesmas 4 falhas. Nenhum commit daquela
mudança toca `core/designsystem`.

## Evidência

- `core/designsystem/src/commonTest/.../ui/component/FloatingActionMenuTest.kt` — a classe, com
  os 4 `@Test`
- `core/designsystem/build/test-results/jvmTest/TEST-…FloatingActionMenuTest.xml` —
  `tests="4" failures="0"`
- `core/designsystem/build/test-results/testDebugUnitTest/TEST-…FloatingActionMenuTest.xml` —
  `tests="4" failures="4"`
- `core/designsystem/build.gradle.kts` — não declara `testOptions` nem dependência de Robolectric
- `CLAUDE.md`, bloco `## Commands` — documenta `./gradlew :app:shared:testDebugUnitTest` como
  "Unit tests only", um comando da mesma família que esta falha derruba

## Consequência

`./gradlew testDebugUnitTest` termina em `BUILD FAILED` sempre, em qualquer árvore limpa. Quem
tentar verificar o app pelo target Android encontra vermelho que não é seu, e o hábito que isso
cria — ignorar o vermelho porque "aquele já falhava" — é o dano real: é o mesmo hábito que faz
a próxima regressão passar despercebida.

O comando canônico do projeto, `./gradlew jvmTest`, fica verde e não revela nada disso, porque
não executa o target Android.

## Sugestão

Declarar `testOptions.unitTests.isReturnDefaultValues = true` no módulo, ou puxar Robolectric
para o `androidUnitTest` — e, seja qual for, decidir se o target Android entra em algum comando
documentado, porque hoje nada o executa. Não vinculante.
