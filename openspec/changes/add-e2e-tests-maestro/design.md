## Context

O Finance é um app KMP (Android, iOS, Desktop) com Compose Multiplatform, Clean Architecture e dados mistos: Room local para o grosso do estado, Firebase (Auth, Firestore, Crashlytics, Analytics) para auth e telemetria, e `multiplatform-settings` para preferências. A suíte de testes hoje é apenas `commonTest` + `jvmTest` com cobertura mínima (1 use case, 1 renderer, 4 migrações Room).

Uma refatoração grande está chegando. Testes unitários e de feature já estão sendo escritos em paralelo em outra branch — eles dão confiança nas regras de negócio mas não exercitam navegação, persistência ponta-a-ponta, recomposição e ciclo de vida real. Maestro foi escolhido pelo usuário pela proposta black-box: roda Android e iOS de verdade, descreve fluxos em YAML, baixa barreira de manutenção.

O projeto tem 18 telas e 28 modais e **zero `testTag`** no código hoje. Sem instrumentação mínima, qualquer flow Maestro vira XPath frágil dependendo de texto traduzível.

## Goals / Non-Goals

**Goals:**
- Cobrir os fluxos críticos de transação (criar/editar/excluir despesa e receita, transferência) com Maestro como rede de proteção pré-refator
- Estabelecer infraestrutura **estável e determinística**: cada flow começa em estado limpo, sem dependência de rede, sem flaky
- Definir convenções claras de `testTag` e organização de flows que escalem para os 5 grupos de prioridade (smoke, transactions, invoices, installments, recurring)
- Permitir execução local fácil (dev rodando no PR) e CI manual em Android para começar
- Não estragar o app de produção — toda instrumentação deve ser inerte fora do flavor de teste, ou ter custo zero (caso de `testTag`)

**Non-Goals:**
- Cobrir Desktop/JVM (Maestro não suporta — fica para Compose UI Test futuro, se necessário)
- Substituir testes unitários ou de feature
- Rodar Maestro automaticamente em todo PR no início — só `workflow_dispatch` até a suíte estabilizar
- Cobrir iOS no CI no início — Android primeiro, iOS local apenas
- Testes de acessibilidade, snapshot/visual regression, performance ou segurança
- Cobrir 100% das telas — flows são intencionalmente um subconjunto representativo

## Decisions

### Decisão 1: `testTag` como única estratégia de seleção

**Escolha:** Todo elemento que um flow Maestro precisa interagir recebe `Modifier.testTag("kebab-case-id")`. Maestro encontra via `id:` no YAML. Não dependemos de texto visível nem de estrutura de árvore.

**Por quê:**
- O app usa `UiText.Res` (i18n). Texto pode mudar; testTag é estável.
- testTag é multiplataforma e tem custo zero em produção (sem branching).
- Seletor por estrutura/posição é o que torna Maestro frágil — testTag elimina isso.

**Alternativas consideradas:**
- *Texto visível* — descartado: i18n + churn de copy quebra os flows.
- *contentDescription* — descartado: já é usado para acessibilidade real, sobrecarregar o campo gera ambiguidade e degrada a11y.
- *Posição/hierarquia* — descartado: frágil a qualquer mudança de layout.

**Convenções:**
- Formato: `<área>-<elemento>` ou `<área>-<elemento>-<id>` (ex.: `transactions-fab`, `transactions-item-{transactionId}`, `account-form-name`).
- Definir constantes em um único `object TestTags` por área (`/ui/screen/transactions/TransactionsTestTags.kt`) — evita string mágica e facilita refator.
- Tag apenas o necessário: pontos de interação (botões, FABs, campos), pontos de assert (textos de saldo, status), e itens de lista que o teste precisa selecionar.
- **Não taggear tudo.** Estimativa: 50–80 tags estratégicos cobrem P1+P2.

### Decisão 2: `testTagsAsResourceId = true` no Android

**Escolha:** Habilitar globalmente via tema/root composable do Android:
```kotlin
Modifier.semantics { testTagsAsResourceId = true }
```

**Por quê:**
- Por padrão, no Android, testTag fica em `SemanticsProperties.TestTag` mas Maestro/UIAutomator procura `resource-id`. Sem essa flag, `id: "fab"` não acha nada.
- No iOS, testTag já vira `accessibilityIdentifier` automaticamente — sem ajuste necessário.

**Trade-off:** Expõe os testTags como `resource-id` na árvore de acessibilidade do Android. É inofensivo em produção (não vaza dados), só torna a árvore de a11y um pouco mais ruidosa para ferramentas externas.

### Decisão 3: Build flavor `e2e` com fakes de Firebase via Koin

**Escolha:** Criar um build type/flavor `e2e` (Android) + configuração espelho (iOS via XcodeGen) que:
- Substitui implementações de Auth/Firestore/Crashlytics/Analytics por fakes locais via módulo Koin alternativo.
- Auth: login anônimo automático com UID fixo (`e2e-user`).
- Firestore: implementação in-memory que satisfaz a interface usada pelos repositórios.
- Crashlytics/Analytics: no-op.
- Mantém Room real (queremos exercitar a persistência de verdade).

**Por quê:**
- **Determinismo > realismo de rede.** Flows ponta-a-ponta com Firestore real introduzem latência, falhas transitórias e cleanup compartilhado. Cada flow tem que começar idêntico.
- **Sem custo em produção.** O flavor `e2e` é um artefato separado; o release não muda.
- **Mais simples que Firebase Emulator Suite.** Emulador exigiria runners com Docker/Java extra, port forwarding em iOS, e ainda assim teria limpeza de estado a fazer. Fakes em memória são triviais de zerar.

**Alternativas consideradas:**
- *Firebase Emulator Suite* — descartado por complexidade de CI e overhead de cleanup. Pode ser revisitado se a suíte crescer e quisermos exercitar regras Firestore de verdade.
- *Mesmo binário com deep link de reset* — descartado: vaza superfície de teste em prod (mitigável, mas frágil), e ainda dependeria da rede.
- *Mockar nível mais alto (Repository)* — descartado: tira justamente o "end-to-end" do teste; queremos Room real e fluxo real.

**Implicação:** Onde os repositórios consomem APIs Firebase concretas, vamos extrair uma **interface mínima de fronteira** e injetá-la via Koin. Isso já é boa prática (Clean Architecture) e o esforço é localizado.

### Decisão 4: Estrutura de flows e helpers em `.maestro/`

**Escolha:**
```
.maestro/
├── config.yaml
├── flows/
│   ├── smoke/
│   │   ├── 01-app-launch.yaml
│   │   └── 02-bottom-nav.yaml
│   ├── transactions/
│   │   ├── 01-create-expense.yaml
│   │   ├── 02-create-income.yaml
│   │   ├── 03-edit-transaction.yaml
│   │   └── 04-delete-transaction.yaml
│   ├── transfers/
│   ├── invoices/
│   ├── installments/
│   └── recurring/
├── helpers/
│   ├── reset-app.yaml
│   ├── seed-account.yaml
│   └── seed-category.yaml
└── README.md
```

**Por quê:**
- Numeração (`01-`, `02-`) define ordem natural quando alguém quer rodar tudo localmente, sem virar requisito (cada flow segue independente).
- `helpers/` chamados via `runFlow:` em `onFlowStart` evitam repetição. `reset-app.yaml` é o pré-requisito universal: `clearState` + `clearKeychain` + reabertura.
- Estrutura por área (não por prioridade) — prioridade vive em `tasks.md`, não no filesystem; áreas são duráveis.

**Convenção de tamanho:** Cada flow YAML cobre **um caminho feliz**. Casos de erro/borda só entram quando o caminho feliz já está estável e há sinal de regressão real.

### Decisão 5: Isolamento de estado via `clearState` + fakes em memória

**Escolha:** Toda flow começa com `clearState` (Android) / `clearKeychain` (iOS) chamado em `helpers/reset-app.yaml`. Como o flavor `e2e` usa Firestore fake in-memory, esse fake também é zerado quando o processo do app reinicia. Auth fake re-loga o usuário anônimo automaticamente no boot.

**Por quê:**
- Estado entre flows é **a maior fonte de flaky em E2E**. Eliminar de uma vez no `onFlowStart`.
- Não precisamos de deep links de reset, factory reset de emulador, nem teardown manual.

**Trade-off:** Toda flow paga o custo de cold start. Aceitável: melhor lento e estável que rápido e flaky.

### Decisão 6: CI manual Android-only no início

**Escolha:** Workflow `.github/workflows/e2e-android.yml` com `workflow_dispatch` apenas. Roda em runner Ubuntu com `reactivecircus/android-emulator-runner`, instala Maestro, builda APK `e2e`, executa todos os flows. Sem trigger automático em PR.

**Por quê:**
- Suíte vai crescer e estabilizar antes de virar gate de PR — um teste flaky bloqueando merge é pior que nenhum teste.
- iOS precisa de runner macOS (caro e mais lento) — entra na fase 2 quando a suíte Android já estiver provando valor.
- `workflow_dispatch` permite rodar sob demanda em branches de refator (caso de uso primário).

**Critério para promover a trigger automático:** quando ≥ 2 semanas sem flaky e tempo de execução < 10 min para a suíte completa.

### Decisão 7: Execução incremental por prioridade

**Escolha:** Implementar em 5 fases incrementais, cada uma entregando valor por si:

```
Fase 1 — Fundação (sem testes)
  └─ Maestro instalado, build flavor e2e, testTagsAsResourceId, .maestro/ scaffold,
     helpers/reset-app, README, CI manual

Fase 2 — Smoke (P1)
  ├─ App launch → dashboard renderiza
  └─ Navegação entre as 6 abas principais

Fase 3 — Transações (P2)  ← onde o usuário quer parar e respirar
  ├─ Criar conta + categoria (helpers)
  ├─ Lançar despesa → conferir saldo
  ├─ Lançar receita → conferir saldo
  ├─ Editar transação
  └─ Deletar transação

Fase 4 — Movimentações compostas (P3)
  ├─ Transferência entre contas
  ├─ Ajuste de saldo
  └─ Despesa em cartão → fatura aberta

Fase 5 — Fatura, parcelamento, recorrência (P4+P5)
  └─ Fluxos restantes
```

**Por quê:** O usuário declarou que vai executar "aos poucos, começando por smoke + CRUD". A change documenta o destino completo, mas as fases 4 e 5 podem virar changes separadas se a estratégia evoluir com o aprendizado das fases 2 e 3.

## Risks / Trade-offs

- **Risco: testTag não cobrir caso novo durante refator.** → Mitigação: a refatoração não bloqueia nos flows; quem alterar a tela é responsável por re-taggear conforme convenção. Listar em `.maestro/README.md` o ritual de "tela mudou ⇒ rever testTag e flow".

- **Risco: fake de Firestore divergir do comportamento real e mascarar bug.** → Mitigação: implementar somente as operações usadas pelos repositórios (não toda API Firestore), e garantir que a interface de fronteira seja consumida pelos mesmos contratos da implementação real.

- **Risco: flows ficarem flaky por timing (animações, recomposição).** → Mitigação: usar `extendedWaitUntil` com asserções por `id:`, evitar `tapOn` com texto, e desabilitar animações via `adb` no setup do CI. Todo flaky vira issue antes de adicionar mais flows.

- **Risco: Maestro não conseguir interagir com `ModalBottomSheet` por causa de `LocalModalManager`.** → Mitigação: validar logo na fase 1 abrindo um modal trivial (ex.: `viewBudget`) e testar `tapOn` em elemento com testTag dentro dele. Se houver problema, ajustar o `ModalManager` para garantir que conteúdo do sheet recebe semantics corretamente.

- **Risco: `testTagsAsResourceId` quebrar testes de a11y futuros.** → Mitigação: sem efeito em a11y real (TalkBack lê `contentDescription`/texto, não `resource-id`). Documentar a decisão.

- **Risco: instrumentação ficar inconsistente — alguns devs taggeiam tudo, outros nada.** → Mitigação: documentar regra em `.maestro/README.md` e no `CLAUDE.md` ao final da change. Constantes em `object TestTags` por área tornam o padrão visível.

- **Risco: extração de interfaces para fakes Firebase virar refator grande.** → Mitigação: fazer só onde necessário, no menor escopo. Se um repositório usa Firestore direto, extrai uma interface; se já passa por contrato, só plugar fake. Caso a extração se mostre maior que 1 dia, parar e propor change separada.

- **Trade-off: cold start por flow é lento.** → Aceito. Velocidade vem da paralelização em CI, não de compartilhar estado.

- **Trade-off: Desktop fica descoberto.** → Aceito. Desktop não é a plataforma primária; cobertura entra via Compose UI Test em `jvmTest` se necessário (change futura).

## Migration Plan

Não há migração de dados nem mudança de comportamento de produção. Plano de rollout:

1. **Fundação** (Fase 1) merge isolado. Permite outros devs já validarem flavor `e2e` localmente sem nenhum flow rodando.
2. **Smoke + Transações** (Fases 2 e 3) em PRs separados, cada um adicionando flows + testTags da área.
3. Refatoração grande começa **depois** da Fase 3 estar verde no CI manual.
4. Fases 4 e 5 podem ocorrer durante ou após o refator, conforme prioridade.

**Rollback:** trivial — toda a infraestrutura é aditiva. Remover `.maestro/`, build flavor `e2e` e `testTagsAsResourceId` reverte tudo sem afetar produção.

## Open Questions

- **Autenticação real do app:** o app usa Firebase Auth. Os flows assumem usuário logado anônimo via fake. Confirmar se há fluxo de login interativo no app que algum flow futuro precise cobrir (ex.: vinculação de conta) — se sim, esse flow específico talvez precise de Auth real ou de uma versão "seedada" do fake.
- **Idioma do device durante o teste:** assumimos que testTag elimina o problema, mas alguns asserts (ex.: status "Pago") podem cair em texto. Padronizar locale no setup do flow (`launchApp` aceita argumentos via Maestro? validar) ou só assertar via testTag.
- **Tempo/data:** o app trabalha com mês corrente. Flows que dependem de "fatura do mês X" podem quebrar na virada do mês. Estratégias: fixar relógio no flavor `e2e` via `Clock` injetado, ou estruturar flows para serem agnósticos a data atual. Decidir antes da Fase 4 (faturas).
