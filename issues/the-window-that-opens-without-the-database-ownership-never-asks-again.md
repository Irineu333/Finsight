---
area: mcp
severity: low
type: concurrency
---

# A janela que abre sem a posse do banco nunca mais a pede

## Cenário

**DADO** que a posse do banco está tomada por outro processo por mais de dez segundos
**QUANDO** o usuário abre a janela do app
**ENTÃO** ela abre sem a posse — o previsto — e nunca mais tenta tomá-la: toda chamada `--mcp`
posterior consegue a posse, escreve no arquivo pelo grafo do processo headless em vez de encaminhar
pela ponte, e a janela segue mostrando os números que leu antes
**DEVERIA** voltar a ser dona assim que a posse vagar, ou dizer que não é — o que não pode é seguir
como se fosse

## Mecânica

Abrir sem a posse é decisão tomada e continua certa: D10 recusa explicitamente travar a abertura do
app por causa de um lock. O defeito é o que vem depois da decisão, e são duas coisas.

**Não há segunda tentativa.** `acquire` é chamado uma vez, dentro de um `remember`, e o resultado
nulo é guardado como qualquer outro: nada no processo volta a chamar `tryAcquire`. Enquanto isso o
processo headless pergunta por chamada e, com a janela sem a posse, recebe — o `?: elsewhere` de
`McpStdioCallSite.answer` só encaminha quando a posse é recusada, e aqui ela não é. Cada `tools/call`
passa a executar no processo errado.

**E o texto que justifica a decisão descreve um custo menor do que o que o código produz.** Tanto o
comentário de `windowMain` quanto D10 dizem que o preço é *"um `Flow` que perde uma atualização"* —
singular, como se fosse a chamada em voo no instante da abertura. Não é uma atualização: é a
garantia inteira, pelo resto da vida daquela janela, porque nenhuma escrita de outro processo acorda
os `Flow`s deste. O builder do desktop não pede invalidação entre instâncias, e o Room a mantém por
processo.

## Evidência

- `windowMain()` (`app/desktop/.../main.kt`) — `remember { DatabaseOwnership().acquire(WAIT_LIMIT) }`,
  a única chamada; `ownership?.release()` no fechamento é o único outro uso do valor
- `McpStdioCallSite.answer()` — `val owned = claim() ?: return elsewhere.answer(...)`: a ponte é o
  ramo da posse **recusada**, não o da janela aberta
- `DatabaseOwnership.acquire()` — devolve `null` no fim de `WAIT_LIMIT`, documentado
- `createDatabaseBuilder()` (`core/database/.../Database.jvm.kt`) — `Room.databaseBuilder(name = path)`,
  sem invalidação entre instâncias
- `openspec/changes/archive/2026-09-05-mcp-stdio-launcher/design.md`, D10 — *"e recusar abrir o app
  por causa de um lock seria pior que um `Flow` que perde uma atualização"*

## Consequência

A janela engana sem impedir: os saldos, as listas e os gráficos ficam nos valores lidos antes, e
nada na tela diz que outro processo está escrevendo por baixo. O arquivo não corrompe — o SQLite
serializa escritores entre processos —, e reabrir a janela resolve, se o usuário souber que
precisa. A faixa desce um degrau porque o estado exige uma configuração rara: alguém tem de segurar
o lock por dez segundos seguidos, e uma chamada headless o segura por milissegundos.

## Sugestão

Duas partes, e a segunda vale mesmo se a primeira não for feita:

1. Reobservar a posse enquanto a janela estiver sem ela — uma tentativa em intervalo largo, num
   escopo que morre com a janela — e tomá-la assim que vagar.
2. Corrigir as duas frases que dizem "uma atualização": o comentário de `windowMain` e D10.

Não vinculante.
