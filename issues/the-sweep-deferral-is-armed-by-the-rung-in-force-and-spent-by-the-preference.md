---
area: settings
severity: low
type: data
version: 1.10.0
---

# O adiamento da varredura é armado pelo degrau em vigor e gasto pela preferência, então uma captura na pasta do app consome o que era da pasta escolhida

## Cenário

**DADO** o cofre ligado, e a pessoa apontando para uma pasta que já guarda o histórico
inteiro de uma instalação anterior — o caso que `deferSweepIfAlreadyHolding` existe para
proteger, armando `skipsNextSweepFor = USER_FOLDER`
**QUANDO** a pasta deixa de ser alcançável antes que qualquer captura tenha caído nela
(cartão removido, volume desmontado, permissão revogada), o app abre e captura — a cópia vai
para o armazenamento do próprio app, porque `VaultRung.inForce` recua provisoriamente — e só
depois a pasta volta
**ENTÃO** a primeira captura que enfim cai na pasta varre na hora, e leva até 5 cópias da
instalação anterior; o adiamento foi gasto por uma captura que nunca esteve lá
**DEVERIA** a pasta receber o adiamento que ganhou: a primeira captura que cair **nela**
não varre, que é a definição escrita em `VaultState.skipsNextSweepFor` — *"spent only by a
capture that lands there"*

## Mecânica

As duas pontas leem campos diferentes.

**Arma** por degrau em vigor: `deferSweepIfAlreadyHolding(to)` chama
`state.deferNextSweep(to.destination)`, e `to` vem de `VaultFolder.location`, que constrói
`VaultLocation` a partir de `rung.inForce`.

**Gasta** por preferência: `BackupVault.land()` chama
`vault.consumeSweepDeferral(state.destination)`, e `state` é o `VaultState` lido no início da
captura — `destination` ali é a preferência, não o degrau. A cópia, essa, foi entregue a
`destination.resolved()`, que no roteador responde `inForce`.

Os dois valores coincidem sempre, menos enquanto o link da pasta está caído — que é
exatamente a janela em que o adiamento ainda não foi gasto e o degrau em vigor é o
armazenamento do app.

O comentário em `land()` enxerga a divergência e conclui só metade dela: *"a sweep skipped
once more than it had to be there costs a copy kept a little longer, never one lost"*. Essa
é a direção em que o armazenamento do app deixa de ser varrido uma vez. A outra direção — a
bandeira sai do lugar onde era devida — não é uma varredura a mais adiada: é a única que era
devida, perdida.

## Evidência

- `feature/backup/impl/.../domain/vault/VaultMigration.kt` — `deferSweepIfAlreadyHolding(to)`:
  `state.deferNextSweep(to.destination)`
- `feature/backup/impl/.../domain/vault/VaultFolder.kt` — `location`: monta o
  `VaultLocation` com `destination = rung.inForce`
- `feature/backup/impl/.../domain/vault/BackupVault.kt` — `land()`:
  `if (!vault.consumeSweepDeferral(state.destination)) sweep(target, state, sparing)`, e o
  comentário que argumenta a direção segura
- `feature/backup/impl/.../domain/vault/VaultDestinations.kt` — `resolved()` devolve
  `inForce`, que é o `target` que a captura preencheu
- `feature/backup/impl/.../domain/vault/VaultRung.kt` — `isProvisional` / `inForce`: o recuo
  para `APP_STORAGE` que não toca a preferência
- `feature/backup/impl/.../database/repository/BackupVaultRepository.kt` —
  `consumeSweepDeferral(destination)`: limpa a bandeira **e** responde `true` só quando o
  destino bate
- nenhum teste em `feature/backup/impl/src/jvmTest` nomeia `consumeSweepDeferral`,
  `deferNextSweep` ou `skipsNextSweepFor`

## Consequência

O adiamento existe para uma pessoa específica: a que reinstalou o app e apontou de volta
para a pasta onde estava tudo (design D4). É ela que perde até 5 cópias antes de ter tido a
chance de olhar o que havia lá — que é literalmente a garantia que o mecanismo compra.

O limite de `MAX_REMOVED_PER_SWEEP` mantém o dano contido, e as cópias removidas são as que
a retenção escolhida removeria de qualquer forma na varredura seguinte. O que se perde é a
janela de uma captura, não o histórico inteiro.

## Sugestão

Gastar pelo mesmo valor que arma: levar até `land()` o `VaultDestination` do degrau que a
captura de fato preencheu — `VaultRung.inForce`, o mesmo que `resolved()` já escolheu — em
vez de `state.destination`. Fecha a divergência sem que a bandeira precise conhecer *qual*
pasta. Não vinculante.
