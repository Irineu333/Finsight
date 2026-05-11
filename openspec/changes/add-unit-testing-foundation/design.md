## Context

O projeto é KMP (Compose Multiplatform) com 17 módulos Gradle (`:core:*` + `:feature:*:api|impl|ui`). A arquitetura já é favorável a testes:
- ViewModels recebem `IXxxRepository` (interfaces em `:api`) via Koin.
- UseCases recebem repositórios e outros UseCases por construtor.
- `Either<Error, T>` (Arrow) padroniza falhas — assertable sem framework de mock.
- `viewModelScope` (Android lifecycle) usa `Dispatchers.Main`, exigindo override em testes de VM.

Estado atual: 2 módulos com teste. Faltam ferramentas (`kotlinx-coroutines-test`, Turbine), uma regra de Main dispatcher reutilizável e fakes prontos para Repository. A exploração definiu o pacote completo: `:core:test` para infra comum e `:feature:X:fake` por feature sob demanda.

## Goals / Non-Goals

**Goals:**
- Infra mínima para testar UseCases puros, UseCases que dependem de Repository, e ViewModels com `StateFlow<UiState>` + Actions.
- Padrão claro e replicável de fake reativo (sem mock framework).
- Preservação do DAG de módulos: `:fake` nunca cria caminho de `:impl` para `:impl`.
- Cobertura efetiva (não cosmética) nas 7 features de maior risco financeiro.
- Curva de aprendizado curta: estilo idêntico ao `CalculateReportStatsUseCaseTest` existente.

**Non-Goals:**
- Testes de Compose UI (screenshot, semantics) — exclusos.
- Testes de implementações concretas de Repository — fakes substituem a interface; a camada Room é testada por outros mecanismos (migrations já cobertos em `:core:database`).
- Cobertura das features fora do conjunto priorizado (`home`, `support`, `dashboard`, `report`).
- Convention plugin para `:fake` no build-logic — adiado.
- `expect/actual` de `MainDispatcherRule` em commonTest — JVM-only é suficiente.
- Métricas de coverage automatizadas (Jacoco etc).
- Test-doubles avançados (in-memory Room, fake DAOs).

## Decisions

### Decisão 1: Módulo dedicado `:core:test` vs. helpers locais
**Escolha:** Criar `:core:test` como módulo KMP enxuto, com `MainDispatcherRule`, `runFlowTest` e helpers de `Either`. Sem dependência em features.

**Por quê:** `MainDispatcherRule` e wrappers de `runTest`/Turbine seriam duplicados em ≥7 módulos. Centralizar é DRY genuíno (mesma classe, comportamento idêntico, zero acoplamento a feature). Manter o módulo "pequeno e estúpido" (sem fixtures de modelo) evita virar dumping ground.

**Alternativas:**
- *(a) Helpers em cada `commonTest`*: zero overhead inicial, mas duplicação real e divergência inevitável.
- *(b) `:core:test` + fixtures genéricas (LocalDate, Either)*: aceito; é o caminho escolhido.
- *(c) `:core:test` + fixtures de modelo de feature*: rejeitado — criaria dependência em `:feature:*:api` e violaria o princípio "core não conhece feature".

### Decisão 2: Padrão `:feature:X:fake` (singular, main source set, on-demand)
**Escolha:** Cada feature ganha um módulo `:feature:X:fake` quando for necessário testá-la ou quando outra feature precisar fakeá-la. Singular (`fake`, não `fakes`) para alinhar com `:api`/`:impl`/`:ui`. Main source set (não `commonTest`) para que outros módulos possam consumir.

**Por quê:**
- KMP não suporta `testFixtures` do Gradle de forma idiomática; um módulo dedicado é o equivalente clean.
- Singular fica coerente com o convention já adotado no projeto.
- Main source set é necessário para que `:feature:Y:impl/commonTest` consiga importar `:feature:X:fake`.
- On-demand evita criar 9 módulos vazios.

**Regra de dependência (nova):**
```
:feature:X:fake  →  SOMENTE :feature:X:api  (+ kotlinx-coroutines-core para MutableStateFlow)
:feature:X:impl/commonTest  →  qualquer :feature:Y:fake  +  :core:test  +  bundle test-kmp
:core:test  →  nenhuma feature
```

Isso preserva o DAG do CLAUDE.md (`:impl` nunca depende de `:impl`).

**Alternativas:**
- *(a) Fakes em `commonTest` da feature consumidora*: rejeitado — `:feature:transactions:impl` precisaria duplicar `FakeAccountRepository` toda vez que um teste consumisse contas.
- *(b) Todos os `:fake` criados upfront*: rejeitado — custo sem retorno; criar quando necessário.

### Decisão 3: Fake como state holder reativo, sem mock framework
**Escolha:** Cada `FakeXxxRepository` implementa a interface `IXxxRepository` com `MutableStateFlow<List<Model>>` interno. Métodos suspend mutam o flow; `observeXxx()` expõe o flow imutável.

```kotlin
class FakeInvoiceRepository : IInvoiceRepository {
    val invoices = MutableStateFlow<List<Invoice>>(emptyList())

    override fun observeAll() = invoices.asStateFlow()
    override suspend fun getInvoiceById(id: Long) = invoices.value.find { it.id == id }
    override suspend fun update(invoice: Invoice) {
        invoices.update { list -> list.map { if (it.id == invoice.id) invoice else it } }
    }
}
```

**Por quê:**
- MockK/Mockito em KMP têm suporte parcial, configurações específicas por target, e produzem testes frágeis (`every { } returns`, `coVerify`).
- Fake reativo testa a integração real entre VM e Flow: ao mudar `invoices.value`, o `combine` no ViewModel reage. Mock não faz isso naturalmente.
- Princípio: testar comportamento, não chamadas.
- Manutenção: adicionar método novo na interface obriga implementar no fake — pressão saudável.

**Alternativas:** MockK (rejeitado — atrito KMP, testes frágeis); Stubs estáticos sem reatividade (rejeitado — quebra Flow tests).

### Decisão 4: `MainDispatcherRule` apenas em `jvmMain` de `:core:test`
**Escolha:** `MainDispatcherRule` é `TestWatcher` JUnit (JVM). UseCases puros e suspend testam em `commonTest` com `runTest` direto. ViewModels (que usam `viewModelScope` → `Dispatchers.Main`) testam em `jvmTest` da `:impl` da feature.

**Por quê:** `TestWatcher`/`@Rule` é JUnit, não existe em `commonTest`. Tentar `expect/actual` ou ferramental cross-target é custo desproporcional para um app sem cobertura iOS-only de VM. JVM cobre Android + Desktop, que é onde a lógica vive.

**Implicação:** estrutura de teste vira:
```
:feature:X:impl/
  src/
    commonTest/.../usecase/      ← UseCases (KMP test)
    jvmTest/.../screen/          ← ViewModels (precisam de MainDispatcherRule)
```

**Alternativas:** `expect/actual` de `MainDispatcherRule` (rejeitado — custo alto, ganho marginal); rodar tudo em `jvmTest` (rejeitado — perde-se KMP test de UseCases sem motivo).

### Decisão 5: `runFlowTest { }` wrapper
**Escolha:** `:core:test` expõe wrapper conveniente:
```kotlin
fun runFlowTest(
    dispatcher: TestDispatcher = StandardTestDispatcher(),
    block: suspend TestScope.() -> Unit,
) = runTest(dispatcher) { block() }
```

Para Flow testing dentro do bloco, consumidores usam Turbine diretamente (`flow.test { awaitItem() }`) — não criar abstração sobre Turbine.

**Por quê:** Centraliza escolha de `TestDispatcher`; Turbine já tem DSL boa, não vale envolver.

### Decisão 6: Helpers para `Either`
**Escolha:** `:core:test` expõe:
```kotlin
inline fun <reified L> Either<*, *>.assertLeftIs(): L
fun <R> Either<*, R>.assertRight(): R
```

**Por quê:** Use cases retornam `Either<XxxException, T>` — assertion pattern repetitivo. Helper enxuto remove boilerplate sem esconder semântica.

### Decisão 7: Onde ficam fixtures de modelos
**Escolha:** Fixtures (`invoiceOf(...)`, `creditCardOf(...)`) ficam em `:feature:X:fake/fixture/`, junto com os fakes da mesma feature. `:core:test` não tem fixtures de modelo.

**Por quê:** Quem define o modelo (`:api`) é quem sabe os defaults sensatos. Fixtures no `:fake` ficam disponíveis para qualquer consumidor que já depende do `:fake`. Sem dependência cruzada estranha.

### Decisão 8: Bundle aplicado apenas em módulos com testes
**Escolha:** `libs.bundles.test-kmp` é aplicado em `commonTest` apenas dos módulos `:impl` que tiverem testes. Não aplicar globalmente via convention plugin.

**Por quê:** Convention plugin global obrigaria todos os `:impl` a baixarem Turbine + coroutines-test mesmo sem teste — churn sem valor. Quando uma feature ganha teste, o `build.gradle.kts` dela adiciona o bundle.

### Decisão 9: Estrutura de diretórios padronizada
**Escolha:**
```
:core:test/
  src/
    commonMain/kotlin/com/neoutils/finsight/core/test/
      either/AssertEither.kt
      flow/RunFlowTest.kt
    jvmMain/kotlin/com/neoutils/finsight/core/test/
      dispatcher/MainDispatcherRule.kt

:feature:X:fake/
  src/commonMain/kotlin/com/neoutils/finsight/feature/<x>/fake/
    Fake<Xxx>Repository.kt
  src/commonMain/kotlin/com/neoutils/finsight/feature/<x>/fixture/
    Fixtures.kt   // funções top-level: invoiceOf(...), creditCardOf(...)

:feature:X:impl/src/commonTest/kotlin/.../usecase/
:feature:X:impl/src/jvmTest/kotlin/.../screen/
```

### Decisão 10: Ordem de cobertura por feature
**Escolha:** Tasks organizadas pela seguinte ordem (alto risco → menor risco), permitindo PRs incrementais:
1. `creditCards` (ciclo de fatura: open/close/pay/reopen, cálculos)
2. `transactions` (CalculateBalance, stats, transfer entre contas)
3. `installments` (divisão por faturas)
4. `recurring` (confirm/skip/stop/reactivate)
5. `categories`
6. `accounts`
7. `budgets`

**Por quê:** maximiza valor cedo. Bugs em invoice lifecycle ou balance calculation são os mais caros operacionalmente.

## Risks / Trade-offs

- **[Risco] Fakes desatualizados com mudanças de interface:** ao adicionar método em `IInvoiceRepository`, o build do `:fake` quebra primeiro, exigindo update. → *Mitigação:* esse é o comportamento desejado; sinaliza precocemente e força sincronização.
- **[Risco] Testes de VM em `jvmTest` perdem cobertura em iOS:** ViewModels não rodam em testes iOS. → *Mitigação:* aceito; lógica está em commonMain, testada via UseCases. VM é orquestração de `combine` e despacho de actions — risco baixo.
- **[Risco] Pulverização de `:fake` na navegação do IDE:** mais módulos no `settings.gradle.kts`. → *Mitigação:* criar on-demand mantém visibilidade focada; agrupar visualmente no IntelliJ com filtros.
- **[Trade-off] Sem MockK = mais código boilerplate de fake:** cada Repository ganha implementação manual completa, ainda que algumas operações sejam triviais. → *Aceito:* boilerplate é explícito, lê-se rápido, e elimina classe inteira de bugs sutis de configuração de mock.
- **[Trade-off] Tempo de build sobe:** novos módulos `:fake` + dependências de teste aumentam compilação total. → *Aceito:* trade ROI claro; `./gradlew check` continua sendo o gate.
- **[Risco] Tasks longas geram PR gigante:** cobrir 7 features de uma vez pode virar PR inviável. → *Mitigação:* tasks organizadas por feature; recomendação explícita de 1 PR por seção (fundação + 1 PR por feature).

## Migration Plan

1. **Fase 1 — Fundação** (PR 1): `:core:test` + libs no version catalog + atualização de CLAUDE.md + READMEs.
2. **Fase 2 — Piloto `creditCards`** (PR 2): cria `:feature:creditCards:fake`, cobre todos os UseCases e VM/modais.
3. **Fase 3 a 8** (PRs 3–8): uma feature por PR, na ordem da Decisão 10.

Rollback de cada fase é trivial: módulos novos podem ser removidos sem impacto em produção (zero acoplamento com código de runtime).

## Open Questions

- Vale criar um `BaseFake<Model>` em `:core:test` (genérico CRUD reativo) ou cada feature implementa do zero? **Hipótese atual:** começar sem; só extrair se duplicação ficar dolorosa após 3 features.
- O `runFlowTest` deve aceitar parâmetro de timeout? **Hipótese atual:** não; defaults de Turbine são suficientes.
- Padronizar `MainDispatcherRule` com `StandardTestDispatcher` ou `UnconfinedTestDispatcher` por default? **Hipótese atual:** `StandardTestDispatcher` (controle explícito via `advanceUntilIdle()`); revisar se aparecer fricção.
