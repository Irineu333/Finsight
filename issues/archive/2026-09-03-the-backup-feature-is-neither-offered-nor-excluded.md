---
area: mcp
severity: low
type: ux
verdict: fixed
---

# A feature de backup não está nem oferecida pela superfície MCP, nem declarada como exclusão

## Invariante

Toda capacidade real do app aparece na superfície MCP de um dos dois jeitos: coberta por uma
tool (`McpSurface.offered`) ou nomeada em `McpSurface.exclusions`, com a razão de estar fora.
É essa dupla contabilidade — e não a lista de tools sozinha — que `McpSurfaceIsClosedTest`
sustenta, e que `docs/mcp-tool-surface.md` declara ter sido obtida por varredura: *"Varrido
contra as features do app, não amostrado. O que faltar aqui é omissão, não silêncio."*

Hoje é falso para `feature/backup`: a feature não está em nenhuma das duas listas, e também
não consta na linha `Features:` do `CLAUDE.md` do projeto (home, support, categories, budgets,
accounts, creditcards, recurring, transactions, report, dashboard, settings — sem backup).

## Mecânica

`feature/backup` é uma feature completa e em produção, não um módulo experimental:
`settings.gradle.kts:62-63` a inclui, `AppModules.kt:17` agrega `backupModule` no Koin, e
`BackupSitsUnderSettingsTest` prova que ela é alcançada a partir da tela de configurações.
Roda em `jvmMain` — o mesmo processo que hospeda o servidor MCP — além de `androidMain` e
`iosMain`, e tem cobertura própria no `.maestro/flows/backup` (`periodic.yaml`,
`preventive.yaml`, `reach.yaml`, `vault.yaml`).

As ações reais de usuário, em `BackupAction.kt:17-78`: `Export` (gerar uma cópia agora),
`ChooseFileToRestore` / `Restore` / `RestoreWithoutCopy` (restaurar — substitui o conteúdo do
banco, `ArchiveRestore.replaceContentFrom`), e `SetVaultOn` / `SetPeriodicOn` /
`SetPreventiveOn` / `SetInterval` / `SetRetention` / `ChooseFolder` / `KeepInsideApp`
(configurar o cofre de backups automáticos), mais o histórico em `BackupHistoryViewModel`.

Nenhuma delas é referenciada em `feature/mcp`: uma busca por `backup|Backup|vault|Vault` no
módulo inteiro devolve um único resultado, um comentário de prosa em
`McpServerSettings.kt:24` sobre um cofre de credenciais do sistema operacional — sem relação
com a feature. E `McpSurface.exclusions`, que hoje lista treze capacidades de fora com a razão
de cada uma, não nomeia backup, restauração nem o cofre em nenhuma delas.

## Evidência

- `feature/backup/api/src/commonMain/.../BackupEntry.kt` — ponto de entrada, alcançado a
  partir de settings
- `feature/backup/impl/src/commonMain/.../ui/screen/backup/BackupAction.kt:17-78` — as ações
  listadas acima
- `feature/backup/impl/src/commonMain/.../domain/restore/ArchiveRestore.kt` — a restauração em
  si, que substitui o conteúdo inteiro do banco
- `feature/backup/impl/src/jvmMain/.../JvmBackupFileService.kt`,
  `.../JvmBackupDestination.kt` — a feature roda no desktop, o mesmo processo do servidor MCP
- `app/shared/src/commonMain/.../di/AppModules.kt:17` — `backupModule` agregado
- `app/shared/src/jvmTest/.../BackupSitsUnderSettingsTest.kt` — confirma que a feature é
  alcançada a partir de Settings
- `feature/mcp/impl/src/jvmMain/.../McpSurface.kt` — `exclusions` (treze entradas) não cita
  backup, restore ou cofre
- `feature/mcp/impl/src/jvmMain/.../McpServerSettings.kt:24` — o único resultado da busca por
  "vault" no módulo mcp, e é um falso positivo (cofre de credenciais do SO)
- `CLAUDE.md` — a linha `Features:` não cita backup
- `docs/mcp-tool-surface.md`, seção "O que fica de fora" — a alegação de varredura completa
  que este achado contraria

## Consequência

Um agente a quem se pede *"faz um backup agora"* ou *"restaura o backup de ontem"* não encontra
tool para nenhuma das duas ações — e, pela mesma falha que o KDoc de `McpSurfaceIsClosedTest` já
nomeia para uma tool esquecida, não há como distinguir "isso foi recusado de propósito" de
"ninguém decidiu". Quem lê `docs/mcp-tool-surface.md` confiando na alegação de varredura
completa recebe um perímetro menor do que o que de fato existe.

A restauração em particular tem o mesmo formato de dano que as três exclusões `WITHHELD` já
nomeiam para escrever uma taxa, trocar a moeda base ou administrar o servidor: um único
chamado alcança tudo de uma vez, de forma irreversível sem tocar o arquivo do banco por fora
do app. Se ela deve entrar ao lado delas — enquanto gerar uma cópia (`Export`) é inofensivo o
bastante para ficar apenas `OUT_OF_SCOPE` — é decisão de quem é dono da superfície, não algo
que este achado resolve.

## Sugestão

Acrescentar uma entrada (ou duas, se exportar e restaurar merecerem tratamentos diferentes) em
`McpSurface.exclusions`, e incluir `backup` na lista de features do `CLAUDE.md`. Não
vinculante — quem decide o perímetro da superfície escolhe a forma.

## Desfecho

**Causa real** — a lacuna era exatamente a descrita: a feature de backup nunca foi varrida
contra a superfície MCP quando `McpSurface.exclusions` foi escrita, e `CLAUDE.md` nunca a citou.

**Mudança** — duas entradas novas em `McpSurface.exclusions`, ambas `OUT_OF_SCOPE`: capturar e
configurar backups (export manual, cofre automático, retenção — inofensivo, simplesmente não
alcançado), e restaurar o banco (não alcançado, com a ressalva honesta de que carrega o mesmo
formato de dano que escrever uma taxa ou mudar a moeda base, mas sem um `MUST NOT` escrito em
`openspec/specs/mcp-tool-surface/spec.md` que sustente `WITHHELD` — isso ficou registrado como
decisão em aberto, não resolvida aqui). `CLAUDE.md` passou a citar `backup` na lista de features.

**Prova** — `McpSurfaceIsClosedTest` (que exige razão não vazia em toda exclusão, e verifica o
conjunto `WITHHELD` contra a constante do teste) segue verde depois da mudança — rodado via
`./gradlew :feature:mcp:impl:jvmTest --tests "*.McpSurfaceIsClosedTest"`.

**Commit** — `Fix(Mcp): declare backup and restore as out-of-scope capabilities`
