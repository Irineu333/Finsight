---
area: app
severity: low
type: performance
---

# A janela sai deixando o banco aberto, onde o modo headless o fecha

## Invariante

Um processo que termina fecha o banco que abriu, para que a última conexão feche com checkpoint
e o SQLite remova o `-wal` e o `-shm` que sustentavam a sessão.

Hoje é falso na janela, e verdadeiro no modo `--mcp` — o mesmo app, os dois caminhos de saída,
comportamentos opostos.

## Mecânica

O ponto de saída headless fecha o banco à mão, e o KDoc diz por quê: nada mais o fecha, porque o
`single<AppDatabase>` não declara callback de fechamento e `stopKoin()` larga a referência sem
tocar no que ela segura.

A janela não faz o equivalente. O `onCloseRequest` para o servidor, devolve a posse do banco e
chama `exitApplication()`; o processo termina com as conexões do Room abertas. O SQLite então
nunca vê a última conexão fechar, o checkpoint final não roda, e os dois arquivos ficam.

Medido nesta máquina, com o app aberto e fechado pela janela às 20:39: `finsight.db-shm`
carimbado no fechamento e `finsight.db-wal` **intacto, com 3,8 MB e data de oito horas antes** —
os dois ainda no disco depois de o processo morrer. Fechada a última conexão como manda o
figurino, os dois teriam sido removidos.

## Evidência

- `app/desktop/src/main/kotlin/com/neoutils/finsight/McpMain.kt:45` — `koin.get<AppDatabase>().close()`
  no `finally`, com o KDoc explicando que nada mais fecha
- `app/desktop/src/main/kotlin/com/neoutils/finsight/main.kt:81-90` — `onCloseRequest`:
  `mcpServer.stop()`, `ownership?.release()`, `exitApplication()`, e nada sobre o banco
- `core/database/src/commonMain/kotlin/com/neoutils/finsight/di/DatabaseModule.kt` — o
  `single<AppDatabase>` sem `onClose`
- `~/.finance/finsight.db-wal` e `-shm` sobrevivendo a um fechamento normal da janela

## Consequência

Nenhum dado se perde, e vale dizer o que **não** é afetado: o cofre copia por `VACUUM INTO`
(`core/database/.../snapshot/DatabaseCapture.kt`), que roda dentro do SQLite e enxerga o estado
consistente com o WAL aplicado, produzindo um arquivo único. Uma cópia feita com o WAL cheio
continua completa.

O que se paga é abertura: a sessão seguinte recupera um WAL que ninguém fechou, e o arquivo não
volta a encolher — 3,8 MB de journal persistente para um banco de 4 KB de página inicial. E é
assimetria entre dois caminhos do mesmo app, que é o tipo de diferença que ninguém lembra de
considerar ao investigar outra coisa.

## Sugestão

Fechar o banco no `onCloseRequest`, como o `McpMain` faz — ou dar ao `single<AppDatabase>` um
`onClose` e deixar `stopKoin()` responder pelos dois caminhos, que remove a chance de um terceiro
ponto de saída futuro nascer com o mesmo esquecimento. Atenção ao que já é sabido sobre esse
`onCloseRequest`: ele roda na thread que pinta, e já há um bug aberto sobre esperar o servidor
ali (`closing-the-desktop-window-waits-on-the-server-from-the-thread-that-paints`) — fechar o
banco no mesmo lugar acrescenta trabalho à mesma thread, e as duas coisas provavelmente se
resolvem juntas. Não vinculante.
