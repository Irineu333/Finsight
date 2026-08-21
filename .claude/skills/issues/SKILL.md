---
name: issues
description: Gerencia o backlog de bugs em `issues/` — registra um bug relatado, conduz a correção de um bug do backlog, arquiva um bug corrigido ou refutado, e revalida os bugs abertos contra o código atual. Use quando o usuário relatar um defeito, quando pedir para corrigir um bug, quando uma correção terminar, ou quando pedirem uma revisão do que ainda está aberto.
---

Um bug é **um arquivo Markdown**. Ele nasce em `issues/`, é movido para `issues/archive/`
quando deixa de estar aberto, e nunca é apagado.

```
issues/
├── edit-card-days-inverts-invoice-dates.md      ← aberto
└── archive/
    └── 2026-08-21-pager-skips-initial-page.md   ← fechado, com desfecho
```

Nome do arquivo em **inglês**, kebab-case, descrevendo o defeito e não o sintoma
(`edit-card-days-inverts-invoice-dates`, não `card-bug`). O conteúdo é em **português**.
O arquivamento renomeia o arquivo com a data do dia — é ela que registra quando o bug
fechou, e por isso nenhum campo de data existe dentro do arquivo.

## Regras duras

1. **Nenhum bug nasce sem âncora.** Todo arquivo carrega ao menos um `Símbolo.método()`
   ou uma repro numerada. Prefira o símbolo ao número da linha: a linha apodrece a cada
   commit, o símbolo não. Um bug que só existe em prosa não é registro, é lembrança —
   e um backlog de lembranças custa tanto para reverificar quanto para reinvestigar do zero.
2. **Nenhum índice manual.** A listagem do backlog é derivada do frontmatter por `grep`.
   Um índice é a segunda coisa a manter atualizada, e a segunda é sempre a que fica para trás.
3. **Fato e hipótese são visualmente distintos.** O que não tem âncora se declara hipótese,
   em itálico, na própria frase.
4. **Arquivar é obrigatório.** Um bug que fechou e continua em `issues/` corrompe a lista
   inteira: depois de dois desses, ninguém confia mais em nenhum.
5. **Um fato aparece em uma seção só.** Repetir a mesma constatação em Cenário, Mecânica e
   Consequência é a forma mais comum de poluir o arquivo.

## Modo: registrar

O usuário relata um defeito.

1. **Monte o cenário** — `DADO` / `QUANDO` / `ENTÃO` (o que acontece) / `DEVERIA` (o certo).
   A linha `DEVERIA` é o critério de fechamento; sem ela, "corrigido" vira opinião.
   Se o defeito não é observável por quem usa o app (performance, acessibilidade estrutural,
   i18n, padrão repetido no código), use a forma **Invariante** — ver `TEMPLATE.md`.
2. **Se faltar alguma linha, pergunte — uma rodada só.** As perguntas saem da lacuna:

   | falta | pergunte |
   |---|---|
   | `ENTÃO` | o que exatamente está errado — número, ordem, texto, o que some, o que sobra? |
   | `DEVERIA` | o que você esperava ver? |
   | `QUANDO` | o que você fez logo antes? |
   | `DADO` | em que situação? sempre, ou só com certos dados? |

   Pergunte também **se segue um padrão ou é intermitente**. Intermitência não é uma
   categoria de bug: é o sintoma de um `DADO` incompleto. A pergunta que ela pede não é
   "com que frequência?", e sim "o que mais estava acontecendo naquele momento?".

3. **Verifique no código.** O relato é evidência, não verdade — vale para o relato do
   usuário como vale para qualquer outra fonte. O que a rodada de perguntas não fechou,
   busque no código: é lá que as âncoras estão.
4. **Se não confirmar**, registre assim mesmo com `confirmed: no` no frontmatter, e declare
   no arquivo **o que falta para confirmá-lo** — é a única coisa que torna um não-confirmado
   útil em vez de entulho. Se o código provar que o cenário não se sustenta, não registre
   como aberto: registre já arquivado, com veredito `refuted`.
5. **Classifique** com a régua de `SEVERITY.md`. Não invente faixa por impressão.
6. **Escreva** o arquivo a partir de `TEMPLATE.md`.

## Modo: corrigir

O usuário pede para corrigir um bug. **É o modo que garante o arquivamento** — sem ele a
correção acontece fora do backlog, e a lista volta a mentir.

1. **Localize o arquivo** em `issues/`. Se o bug não estiver registrado, siga o modo
   *registrar* até ter cenário e âncoras: corrigir sem cenário é corrigir sem critério
   de fechamento.
2. **Leia o arquivo inteiro** antes de tocar no código. O cenário é o critério, as âncoras
   são o ponto de partida, e a `## Sugestão` é um palpite de quem registrou — **não vincula**.
3. **Corrija.** Como corrigir é decisão de quem corrige. `FIXING.md` traz o ciclo
   recomendado — reproduzir, morder com um teste, corrigir, provar — e não é obrigatório.
4. **Arquive**, sem exceção: siga o modo *arquivar*. A correção não está pronta enquanto
   o arquivo estiver em `issues/`.

Se a correção revelar que o cenário estava errado, **corrija o arquivo do bug antes de
corrigir o código**. Se revelar outros defeitos, registre-os em vez de resolvê-los de
passagem: um bug por vez é o que mantém o desfecho legível.

**Bug não registrado, corrigido na hora:** não force um arquivo que nasceria e morreria na
mesma sessão — o commit `Fix(...)` já é o registro. Registre se a correção for adiada, se o
defeito tiver irmãos, ou se ela exigir uma decisão que alguém vá querer reler depois.

## Modo: arquivar

Um bug deixou de estar aberto.

1. **Escolha o veredito.** Cada um existe porque muda o que se faz depois:

   | veredito | o que aconteceu | e por isso, depois |
   |---|---|---|
   | `fixed` | atacado e resolvido | nada — a prova está no arquivo |
   | `refuted` | o cenário não se sustenta contra o código | nada, mas o falso positivo não volta a ser registrado |
   | `incidental` | sumiu sem ninguém atacá-lo | **decida se entra prova de regressão** — o buraco continua aberto |
   | `obsolete` | o código que continha o defeito não existe mais | nada, mas ninguém tenta reproduzir em terreno morto |

   `refuted` e `obsolete` não são a mesma coisa: no primeiro o bug nunca existiu, no segundo
   existiu e o terreno mudou por baixo.
2. **Escreva a seção `## Desfecho`** — ver `TEMPLATE.md`. Em `fixed`, o campo mais valioso é
   **a causa real**, principalmente quando ela diverge da sugestão original: é o único registro
   de onde o raciocínio errou.
3. **Mova e renomeie**: `issues/<slug>.md` → `issues/archive/<AAAA-MM-DD>-<slug>.md`, com a
   data de hoje. Use `git mv` para o histórico do arquivo sobreviver.
4. **Em `incidental`, pergunte ao usuário** se entra teste de regressão. É o único veredito
   que fecha um bug deixando o buraco aberto.

## Modo: revalidar

Reconcilia o backlog com o código de hoje. É o que impede o diretório de virar ficção.

1. Para cada arquivo em `issues/`, releia as âncoras da seção `## Evidência`.
2. Classifique cada um:

   | achado | ação |
   |---|---|
   | a âncora sumiu, ou o `DEVERIA` já vale | arquivar como `incidental` — confirme o commit que fechou com `git log -S` |
   | o símbolo foi renomeado ou movido | corrigir a âncora no arquivo, sem arquivar |
   | o código que continha o defeito não existe mais | arquivar como `obsolete` |
   | o cenário não se sustenta | arquivar como `refuted` |
   | continua vivo | nada |

3. **Verifique, não deduza.** "A âncora sumiu" significa que você leu o arquivo agora e ela
   não está lá — não que o nome parece antigo.
4. Relate o que mudou: quantos revalidados, quantos arquivados e por qual veredito, quantas
   âncoras corrigidas.

## Listar o backlog

Sem índice — derive do frontmatter:

```bash
grep -l "severity: critical" issues/*.md          # os críticos
grep -l "area: creditcards" issues/*.md           # por área
grep -rl "confirmed: no" issues/*.md              # os que ainda não foram confirmados
grep -l "verdict: incidental" issues/archive/*.md # fecharam sem prova de regressão
```

## Referências

| arquivo | o que traz |
|---|---|
| `TEMPLATE.md` | o esqueleto do arquivo de bug, nas duas formas, e a seção de desfecho |
| `SEVERITY.md` | a régua de criticidade, ancorada em consequência |
| `FIXING.md` | a referência sugestiva de correção |
