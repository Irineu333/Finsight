## Why

O projeto tem 52 UseCases e 42 ViewModels, mas só 2 módulos com testes (`:core:database` para migrations e `:feature:report:impl` com 1 UseCase). Não há infraestrutura compartilhada (Turbine, `MainDispatcherRule`, fakes), e regras de negócio críticas — ciclo de faturas, cálculo de saldo, parcelamento, recorrência — vivem sem rede de proteção. Estabelecer uma fundação enxuta e cobrir as features financeiras de alto risco reduz regressões silenciosas e dá confiança para evoluir.

## What Changes

- **Novo módulo `:core:test`** com `MainDispatcherRule` (JVM-only), wrapper `runFlowTest` (runTest + Turbine) e helpers para assertions sobre `Either`. Sem dependência de feature.
- **Padrão de módulos `:feature:X:fake`** (singular, main source set) — criado sob demanda quando cada feature passa a ter testes. Depende apenas de `:feature:X:api`, expondo `FakeXxxRepository` (state holder reativo com `MutableStateFlow`) e factories (`invoiceOf`, `accountOf` etc.). Proibido uso de mock framework — fakes manuais reativos.
- **Dependências adicionadas ao `gradle/libs.versions.toml`:** `kotlinx-coroutines-test`, `app.cash.turbine:turbine` e bundle `test-kmp`.
- **Cobertura de testes em 7 features priorizadas:** `creditCards`, `transactions`, `installments`, `recurring`, `categories`, `accounts`, `budgets` — todos os UseCases e ViewModels com lógica não-trivial.
- **Regras de dependência entre módulos de teste** atualizadas no CLAUDE.md (`:fake` depende só de `:api`; `:impl/commonTest` pode consumir qualquer `:fake` + `:core:test`).
- **README** em `:core:test` documentando uso da infra.
- Fora do escopo: features `home`, `support`, `dashboard`, `report` (oportunístico); testes de Compose UI; testes de Repositories concretos (fakes substituem a interface).

## Capabilities

### New Capabilities
- `testing-foundation`: Estrutura de módulos (`:core:test` + padrão `:feature:X:fake`), convenções de fake/fixture, regras de dependência entre módulos de teste, convenções de localização e naming de testes, e baseline mínimo de bibliotecas de teste (Turbine + coroutines-test).

### Modified Capabilities
- `module-structure`: Acrescenta o módulo `:core:test` e o padrão `:feature:X:fake` como sufixo válido de feature (junto com `:api`, `:impl`, `:ui`), incluindo regra de dependência específica para esses módulos.
- `module-docs`: Requer README em `:core:test` e nota sobre `:feature:X:fake` na lista de módulos do CLAUDE.md.

## Impact

- **Build:** novo módulo `:core:test` em `settings.gradle.kts`; até 7 novos módulos `:feature:X:fake` adicionados incrementalmente.
- **Version catalog:** novas entradas `kotlinx-coroutines-test`, `turbine`, bundle `test-kmp` em `gradle/libs.versions.toml`.
- **Documentação:** CLAUDE.md atualizado (seção "Module convention" + index de Modules); README criado em `:core:test`.
- **Código de produção:** zero impacto — apenas código de teste e novos módulos.
- **CI/build time:** aumento esperado pelo volume de novos testes; `./gradlew check` permanece como gate.
- **DX:** ViewModels e UseCases passam a ter padrão claro para teste sem refatorar produção (interfaces `IXxxRepository` já existem em `:api`).
