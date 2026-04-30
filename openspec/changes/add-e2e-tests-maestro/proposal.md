## Why

Uma refatoração grande está chegando e o app não tem rede de proteção: zero testes de UI, zero testes de fluxo. Testes unitários e de feature já estão sendo escritos em paralelo, mas eles não pegam regressões de integração ponta-a-ponta — exatamente o tipo de bug que dói num app financeiro (fatura paga incorretamente, transferência que não credita, parcela distribuída em fatura errada). Um conjunto enxuto de testes Maestro rodando o app de verdade fecha esse buraco antes do refator começar.

## What Changes

- Adicionar **Maestro** como ferramenta de teste end-to-end para Android e iOS (Desktop fica de fora)
- Instrumentar a UI com `testTag` nos pontos-chave (FABs, botões de ação, campos de formulário, itens de lista de transação) — sem taggear tudo, só o que os flows precisam encontrar
- Habilitar `Modifier.semantics { testTagsAsResourceId = true }` no Android para Maestro enxergar testTags como resource IDs
- Criar **build flavor `e2e`** (Android + iOS) que:
  - Substitui Firebase Auth/Firestore/Crashlytics/Analytics por implementações fake locais
  - Garante isolamento determinístico entre flows (sem rede, sem estado compartilhado)
- Criar estrutura `.maestro/` na raiz com flows organizados por área (smoke, transactions, invoices, installments, recurring) e helpers reutilizáveis
- Cobrir incrementalmente os fluxos críticos em 5 prioridades, começando por **smoke + CRUD de transações** (P1+P2)
- Adicionar workflow de CI manual (`workflow_dispatch`) rodando Maestro Android em emulador no GitHub Actions; iOS e execução automática em PR ficam para depois

## Capabilities

### New Capabilities

- `e2e-testing`: Infraestrutura e convenções para testes end-to-end com Maestro — instalação, organização de flows, helpers, build flavor de teste, isolamento de estado, integração com CI, e padrões de uso de `testTag` no código de produção

### Modified Capabilities

<!-- Nenhuma capability existente em openspec/specs/ é alterada. Esta change introduz uma nova capability isolada de testes E2E. -->

## Impact

**Código:**
- `composeApp/build.gradle.kts`: novo build flavor `e2e` (Android), source set `e2eMain` para fakes de Firebase
- `composeApp/src/commonMain/.../ui/`: adição incremental de `Modifier.testTag(...)` em telas e modais cobertos pelos flows
- `composeApp/src/androidMain/`: aplicação de `testTagsAsResourceId = true` no theme/root
- `composeApp/src/iosMain/`: testTags já viram `accessibilityIdentifier` automaticamente (sem mudança)
- Possível pequena extração de interfaces de fronteira (Auth/Crashlytics/Analytics) para permitir injeção de fakes via Koin no flavor `e2e` — apenas onde ainda não existir

**Repositório:**
- Nova pasta raiz `.maestro/` com `config.yaml`, `flows/`, `helpers/`, `README.md`
- Novo workflow `.github/workflows/e2e-android.yml` (manual, opcional)

**Dependências:**
- Maestro CLI (instalação local de dev e em CI; não vira dependência Gradle)
- Nenhuma dependência nova no `libs.versions.toml`

**iOS:**
- `iosApp/project.yml` (XcodeGen) ganha configuração `e2e` espelhando a do Android

**Fora de escopo:**
- Não cobre Desktop/JVM (Maestro não suporta)
- Não substitui testes unitários nem de feature (esforço paralelo)
- Não cobre acessibilidade nem snapshot/visual regression (ficam para changes futuras)
- Não roda automaticamente em PR no início — apenas execução manual em CI até a suíte estabilizar
