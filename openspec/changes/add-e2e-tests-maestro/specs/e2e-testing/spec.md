## ADDED Requirements

### Requirement: Maestro como ferramenta de teste end-to-end

O projeto SHALL adotar Maestro como única ferramenta de testes end-to-end black-box para Android e iOS. Desktop/JVM está fora de escopo desta capability.

#### Scenario: Maestro CLI documentado como dependência de dev

- **WHEN** um novo desenvolvedor consulta `.maestro/README.md`
- **THEN** encontra instruções para instalar Maestro CLI localmente, comandos para rodar um flow individual, comandos para rodar todos os flows, e a versão mínima suportada do Maestro

#### Scenario: Flows não dependem de ferramenta proprietária paga

- **WHEN** um flow é descrito em YAML em `.maestro/flows/`
- **THEN** ele MUST usar apenas comandos do Maestro open-source — sem dependência obrigatória de Maestro Cloud, Studio ou qualquer SaaS pago

### Requirement: Build flavor `e2e` isolado e determinístico

O projeto SHALL fornecer um build flavor `e2e` (Android) com configuração espelho em iOS (XcodeGen) que substitui dependências externas por implementações fake locais. Esse flavor SHALL ser usado para todos os runs de Maestro — flows não devem rodar contra builds `debug` ou `release`.

#### Scenario: Flavor e2e não faz nenhuma chamada de rede

- **WHEN** o app roda no flavor `e2e`
- **THEN** Auth, Firestore, Crashlytics e Analytics são servidos por implementações fake locais — nenhuma requisição HTTP, gRPC ou WebSocket sai do dispositivo durante a execução de um flow

#### Scenario: Auth fake provê usuário anônimo determinístico

- **WHEN** o app inicia no flavor `e2e`
- **THEN** o usuário está autenticado anonimamente com UID estável (`e2e-user`), sem prompt de login e sem roundtrip de rede

#### Scenario: Firestore fake é zerado a cada cold start

- **WHEN** o app é morto (via `clearState`) e reaberto
- **THEN** o Firestore fake retorna ao estado vazio inicial — nenhum dado de flow anterior persiste em memória ou em disco

#### Scenario: Flavor de produção permanece intocado

- **WHEN** o build `release` é gerado
- **THEN** ele NÃO inclui o módulo Koin com fakes nem nenhuma classe específica do flavor `e2e` — release continua usando Firebase real

### Requirement: Instrumentação com testTag em pontos de interação

Toda UI Compose que precise ser exercitada por um flow Maestro SHALL expor um identificador estável via `Modifier.testTag(...)`. Texto visível NÃO deve ser usado como seletor primário.

#### Scenario: testTag em pontos de interação cobertos por flows

- **WHEN** uma tela ou modal é coberta por um flow Maestro
- **THEN** botões de ação, FABs, campos de formulário e itens de lista que o flow interage recebem `testTag` com identificador em kebab-case

#### Scenario: testTags definidos como constantes por área

- **WHEN** uma área de UI introduz testTags
- **THEN** os identificadores SHALL viver em um único `object TestTags` por área (ex.: `TransactionsTestTags`) — string mágica em call site não é aceita

#### Scenario: testTag visível ao Maestro no Android

- **WHEN** o app Android renderiza qualquer Composable
- **THEN** o root composable aplica `Modifier.semantics { testTagsAsResourceId = true }`, e os testTags se tornam consultáveis via `id:` no YAML do Maestro

#### Scenario: testTag não interfere em acessibilidade

- **WHEN** um Composable recebe `testTag`
- **THEN** seu `contentDescription` (quando aplicável) permanece intacto e dedicado a leitores de tela — testTag e contentDescription não são intercambiáveis

#### Scenario: Convenção de nomeação consistente

- **WHEN** um testTag é criado
- **THEN** seu nome segue o formato `<área>-<elemento>` (ex.: `transactions-fab`) ou `<área>-<elemento>-<id>` para itens de lista (ex.: `transactions-item-{transactionId}`)

### Requirement: Estrutura padrão do diretório `.maestro/`

O projeto SHALL organizar flows e helpers em `.maestro/` na raiz do repositório, separados por área funcional.

#### Scenario: Estrutura por área funcional

- **WHEN** um novo flow é adicionado
- **THEN** ele vive em `.maestro/flows/<área>/<nn>-<nome>.yaml`, onde `<área>` é uma subpasta entre `smoke`, `transactions`, `transfers`, `invoices`, `installments`, `recurring` (ou nova área documentada no README)

#### Scenario: Helpers reutilizáveis isolados

- **WHEN** um trecho de fluxo é usado por mais de um flow
- **THEN** ele MUST ser extraído para `.maestro/helpers/<nome>.yaml` e referenciado via `runFlow:` — duplicação entre flows não é aceita

#### Scenario: Cada flow é independente

- **WHEN** qualquer flow é executado isoladamente
- **THEN** ele passa sem depender de outro flow ter rodado antes — pré-condições são montadas via `helpers/`

### Requirement: Isolamento de estado entre flows

Cada flow Maestro SHALL começar com estado completamente limpo. Compartilhamento de estado entre flows NÃO é permitido.

#### Scenario: Reset de app antes de cada flow

- **WHEN** um flow é executado
- **THEN** seu `onFlowStart` chama `helpers/reset-app.yaml`, que executa `clearState` (Android) / `clearKeychain` (iOS) e relança o app

#### Scenario: Pré-condições montadas via helpers

- **WHEN** um flow precisa de uma conta ou categoria pré-criada
- **THEN** ele monta esse estado chamando `helpers/seed-*.yaml` no início do flow — nunca assumindo estado deixado por outro flow

#### Scenario: Sem cleanup pós-flow

- **WHEN** um flow termina (sucesso ou falha)
- **THEN** ele NÃO precisa de teardown explícito — o próximo flow garante limpeza via `reset-app`

### Requirement: Convenções de seletor e estabilidade

Flows Maestro SHALL priorizar seletores estáveis e comportamento determinístico.

#### Scenario: Seleção primária por id

- **WHEN** um flow interage com um elemento da UI
- **THEN** ele usa `tapOn: id: "<test-tag>"` ou `assertVisible: id: "<test-tag>"` como forma primária — `tapOn: "<texto>"` é permitido apenas para elementos que comprovadamente não podem receber testTag

#### Scenario: Esperas explícitas em vez de sleep

- **WHEN** um flow precisa aguardar uma transição ou recomposição
- **THEN** ele usa `extendedWaitUntil` baseado em asserção — `wait` com tempo fixo NÃO é aceito exceto para casos documentados (ex.: animação não desabilitável)

#### Scenario: Cada flow cobre um caminho feliz

- **WHEN** um flow é escrito para uma feature
- **THEN** ele cobre o caminho principal de sucesso — variações de erro e edge cases entram em flows separados apenas após sinal de regressão real

### Requirement: Cobertura mínima por fase

A implementação SHALL seguir as fases incrementais definidas no design. Cada fase entrega valor por si só e pode ser merged independentemente.

#### Scenario: Fase 1 — Fundação entrega infraestrutura sem flows

- **WHEN** a fase 1 é mergeada
- **THEN** o projeto tem: build flavor `e2e` funcional, `testTagsAsResourceId` aplicado no root Android, scaffold completo de `.maestro/`, `helpers/reset-app.yaml`, README, e workflow CI manual — sem flows de feature

#### Scenario: Fase 2 — Smoke cobre boot e navegação

- **WHEN** a fase 2 é mergeada
- **THEN** existem flows que validam: app abre sem crash, dashboard renderiza, e usuário navega por todas as abas principais sem crash

#### Scenario: Fase 3 — Transações cobre CRUD básico

- **WHEN** a fase 3 é mergeada
- **THEN** existem flows que validam: criar despesa, criar receita, editar transação, deletar transação — cada um conferindo via testTag que a operação refletiu na UI (lista e/ou saldo)

#### Scenario: Fases 4 e 5 cobrem fluxos compostos

- **WHEN** as fases 4 e 5 são mergeadas
- **THEN** existem flows cobrindo: transferência entre contas, ajuste de saldo, despesa em cartão, ciclo de fatura (fechar/pagar/reabrir), parcelamento distribuído em faturas, e CRUD básico de recorrência

### Requirement: Integração com CI

O projeto SHALL fornecer um workflow GitHub Actions que executa a suíte Maestro Android sob demanda.

#### Scenario: Workflow manual disponível desde a fase 1

- **WHEN** um desenvolvedor abre a aba Actions do GitHub
- **THEN** existe um workflow `e2e-android` com trigger `workflow_dispatch` que builda o APK `e2e`, sobe um emulador Android e roda todos os flows em `.maestro/flows/`

#### Scenario: Workflow não é gate de PR no início

- **WHEN** um PR é aberto
- **THEN** o workflow `e2e-android` NÃO roda automaticamente — execução é manual até a suíte estabilizar

#### Scenario: Promoção a gate exige critério explícito

- **WHEN** alguém propõe tornar `e2e-android` obrigatório em PRs
- **THEN** a decisão SHALL ser tomada em uma change OpenSpec separada, com critério atendido: ≥ 2 semanas sem flaky e tempo de execução total < 10 min

### Requirement: Documentação para o time

A capability SHALL ser autoexplicativa para qualquer dev que abra o repositório pela primeira vez.

#### Scenario: README cobre fluxo completo

- **WHEN** um novo dev abre `.maestro/README.md`
- **THEN** encontra: como instalar Maestro, como buildar o flavor `e2e`, como rodar um flow específico, como rodar a suíte completa, convenções de testTag, e ritual "tela mudou ⇒ rever testTag e flow"

#### Scenario: CLAUDE.md referencia a capability

- **WHEN** o agente Claude lê o `CLAUDE.md` do projeto
- **THEN** existe uma seção curta apontando para `.maestro/` e indicando que mudanças em telas cobertas por flow exigem revisão dos testTags e do flow correspondente
