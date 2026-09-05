---
area: transversal
severity: medium
type: test
---

# Os testes de arquitetura varrem worktrees de agente e acusam o código delas

## Cenário

**DADO** uma worktree git dentro do repositório — o harness cria uma por agente que trabalha
isolado, cada uma com uma cópia completa da árvore
**QUANDO** alguém roda `./gradlew jvmTest`
**ENTÃO** os testes de arquitetura falham acusando arquivos dentro de `.claude/worktrees/`, e um
invariante do tipo "existe exatamente um lugar que faz X" quebra por multiplicidade
**DEVERIA** varrer o que é fonte dos módulos, e só isso

O gatilho é a worktree ter fonte. As duas que sobraram na árvore hoje são cascas — `.gradle` e um
esqueleto de `build-logic`, nenhum `.kt` — então a suíte está verde e o defeito, armado.

## Mecânica

Cada teste de arquitetura acha a raiz do repo subindo até o `settings.gradle.kts` e desce a partir
dela com `walkTopDown()`. A exclusão existe e é curta demais: `onEnter { it.name != "build" &&
it.name != ".git" }`. `.claude/` não está lá, e é onde as worktrees vivem.

O filtro seguinte não salva: ele exige `/src/` e um `/src/<algo>Main/` no caminho, que é exatamente
o que uma cópia do repositório também tem. O mesmo arquivo passa a ser contado uma vez por worktree,
e a mensagem culpa o código — que está certo.

São **dez** classes que varrem a partir da raiz do repo, todas em `app/shared/src/jvmTest`, e a
exclusão está escrita onze vezes — copiada em cada uma, e duas vezes numa delas.

## Evidência

- `ConsolidationBoundaryTest` — `repoRoot` por `generateSequence(File("").absoluteFile) { it.parentFile }
  .first { File(it, "settings.gradle.kts").exists() }`, e `repoRoot.walkTopDown().onEnter { it.name
  != "build" && it.name != ".git" }`
- as outras nove, com a mesma varredura e a mesma exclusão: `ConsolidatedFiguresReactTest`,
  `BaseCurrencyReachTest`, `CurrencyRegistrySourceTest`, `CurrencyRelabelIsMigrationOnlyTest`,
  `NothingIsTruncatedSilentlyTest`, `RemoteSourceIsNeverReadTest`, `SingleCurrencyInertiaTest`,
  `ViewModelWritesGoThroughUseCasesTest`, `EveryFigureCanExplainItselfTest`
- as que **não** são atingidas, e por quê: `UseCaseIdentityTest`, `McpSurfaceIsClosedTest`,
  `AgentSurfaceCarriesNoDomainTest`, `ScopedUrlNeverTextTest` e `RateIsNeverWrittenTest` varrem a
  partir de um módulo, não da raiz
- `grep -rn "\.claude" app core feature build-logic --include='*.kt'` — a única ocorrência é
  `AgentInstructionsTest`, que lê `.claude/skills` de propósito; nenhuma exclusão
- `.claude/worktrees/` — o diretório existe, com dois restos de worktree; **nenhum tem fonte**
  (`find .claude -name '*.kt'` devolve zero), e por isso a varredura não acha nada neles hoje
- `./gradlew :app:shared:jvmTest --tests "*ConsolidationBoundaryTest*"` — verde agora, com esses
  dois restos no lugar: o defeito está armado, não disparado

*A ocorrência que originou o registro: com três worktrees povoadas, onze falhas de uma vez. Essa
contagem não foi remedida — as worktrees que a produziram não existem mais. O que foi verificado
nesta revalidação é a varredura, a ausência da exclusão, e que o suspeito de hoje está vazio.*

## Consequência

Quem rodar a suíte enquanto uma worktree povoada existe recebe falhas que não têm nada a ver com a
mudança em curso, apontando caminhos que não fazem parte do build. A conclusão natural — "quebrei alguma
coisa" — é falsa, e o diagnóstico custa a sessão inteira. Uma worktree fica para trás quando um
agente termina sem limpá-la, que foi como isto apareceu.

## Sugestão

Excluir da varredura o que não é fonte de módulo — `.claude/` ao lado de `build` e `.git`. O lugar
natural é um utilitário único que enumere os arquivos para todas essas classes: hoje a exclusão está
copiada onze vezes, e é por isso que ela envelheceu nas onze ao mesmo tempo. Não vinculante.
