## Context

`ViewCategoryModal` mostra hoje duas figuras, ambas do mês selecionado num `MonthSelector`
no topo do próprio modal: o total da categoria naquele mês e a contagem de lançamentos
naquele mês. O `ViewCategoryViewModel` mantém um `MutableStateFlow<YearMonth>` e recombina
tudo a cada deslocamento, chamando `dimensionBalanceInMonthByCurrency` e
`dimensionEntryCountInMonth`.

As restrições que governam este redesenho já estão escritas e não são negociáveis aqui:

- **O razão não consolida.** Toda leitura por dimensão responde `MoneyByCurrency`, e reduzir
  isso a um número é conversão, que vive acima do razão (`ledger-reporting`,
  `currency-consolidation`).
- **O passado não se move.** "Uma figura de um período passado MUST NOT ser recalculada à
  taxa corrente" (`currency-consolidation`). Consolidar doze meses às taxas de hoje está
  proibido.
- **`DisplayAmount` não combina dois valores.** Uma figura consolidada é resultado, não
  operando: somar doze `ConsolidatedAmount` para obter um total é exatamente o que o tipo
  existe para impedir.
- **Uma regra derivável do domínio tem um dono só, no domínio.** Janela, média e variação
  são regras; não podem morar num `ViewModel` nem numa `@Composable`.

Estas quatro, juntas, decidem quase toda a arquitetura abaixo.

## Goals / Non-Goals

**Goals:**

- Substituir as duas figuras mensais por três figuras com janela declarada, sem introduzir
  um segundo caminho de cálculo para dinheiro.
- Obter a janela inteira numa leitura só do razão.
- Colocar janela, média e variação num dono único, acima do razão e fora da UI.
- Preservar a resposta a "quanto gastei nisto em março" na superfície que já sabe respondê-la.

**Non-Goals:**

- Gráfico de tendência, fatia da categoria no mês, orçamento embutido e lista de lançamentos
  dentro do modal. São conteúdo de tela; este é um modal.
- Alterar o `AdaptiveModal` ou o padrão de detalhe adaptativo.
- Expor a série mensal a outras features. Ela nasce com um consumidor; generalizá-la antes
  do segundo é inventar requisito.

## Decisions

### D1 — Uma leitura agrupada por mês, não N leituras mensais

`EntryDao` ganha uma consulta que agrupa por `substr(o.date, 1, 7)` **e** por moeda, e
`IEntryRepository` a expõe como série mensal de uma dimensão.

*Alternativa considerada:* chamar `dimensionBalanceInMonthByCurrency` doze vezes num
`associateWith`, como faz `dimensionBalancesInMonthByCurrency`. Rejeitada: `ledger-reporting`
já exige que um detalhamento de N custe uma leitura e não N, e a extensão existente fana
sobre dimensões num mês, não sobre meses de uma dimensão.

Meses sem movimento **não voltam** na resposta — um `GROUP BY` não produz linha vazia. Os
zeros da janela são supridos acima do razão, por quem decide a janela, e isso é deliberado:
o razão não sabe que janela é essa.

### D2 — A aritmética da janela acontece por moeda, antes de qualquer conversão

O total e a média da janela são calculados somando e dividindo `MoneyByCurrency` — aritmética
dentro do espaço do razão, sem taxa nenhuma envolvida. Só o **resultado** atravessa o redutor,
uma vez.

```
série mensal (MoneyByCurrency por mês)
        │
        ├── Σ dos meses da janela ──────────┐   (por moeda, sem taxa)
        │                                    │
        └── (Σ) ÷ nº de meses ───────────┐   │   (por moeda, sem taxa)
                                          ▼   ▼
                        ConsolidateMoneyUseCase(on = último dia da janela)
                                          │   │
                                          ▼   ▼
                                   média     total   (ConsolidatedAmount)
```

Isto satisfaz as três restrições ao mesmo tempo: nenhum `ConsolidatedAmount` é somado a
outro; a conversão acontece uma vez por figura; e a data usada é o fim da janela — o período
a que a figura se refere —, não hoje, de modo que o passado não se move quando uma taxa muda.

*Alternativa considerada:* consolidar cada mês na sua própria data e somar as consolidações.
Rejeitada por violar `DisplayAmount`, e porque somar doze figuras aproximadas acumula doze
arredondamentos onde uma conversão bastava.

### D3 — A variação usa a escala comparativa, não as figuras exibidas

O percentual vem de `ConsolidateMoneyUseCase.comparativeMagnitudes`, sobre a família de duas
figuras `{mês corrente, média da janela}`, com **data única**.

A distinção que autoriza isso já está declarada no código: `ComparativeMagnitudes` "não é
dinheiro — o que sai dela é chave de ordenação e fração", e não carrega moeda nem exatidão. A
proibição de recalcular o passado à taxa corrente é sobre **figuras**; uma escala comparativa
não é uma figura, e comparar dois números postos em escalas diferentes é que produziria um
percentual sem significado — misturando variação de gasto com variação cambial.

`magnitudeOf` devolve `null` quando nada da figura pôde ser posto na escala. Esse `null`
propaga até a UI como ausência de variação, com o motivo dito em texto. Nunca vira `0%`.

### D4 — A janela encurta com a idade da categoria, e o rótulo declara o número real

A janela é `min(12, meses fechados desde o primeiro lançamento)`. O primeiro lançamento sai
de graça da série mensal — é a primeira linha —, sem leitura adicional.

Sem isto, uma categoria com três meses de vida teria a média dividida por doze e leria como
se gastasse quatro vezes menos do que gasta. O rótulo carrega o número de meses porque a
alternativa — um número fixo no texto e outro no divisor — é a forma mais silenciosa de a
figura mentir.

O mês corrente fica **fora** da janela. Isso mantém `média × meses = total` exato e
conferível, e impede que um mês pela metade puxe a média para baixo todo dia 1º.

### D5 — Janela, média e variação têm um dono, em `feature/categories/api`

Um caso de uso — `CalculateCategoryOverviewUseCase` — recebe a categoria e devolve o
resultado pronto: mês corrente, janela (figuras + nº de meses), variação, e o estado em que
a categoria está. O `ViewModel` observa e mapeia; não decide nada.

Vive na `api` de categories, e não em `:core:model`, porque é regra da fachada categoria e
não da consolidação; e não no `impl`, porque o razão e o redutor de que precisa são `:core:*`
e a `api` já os alcança.

O sinal de exibição continua vindo de onde já vem: `category.type.accountType.displaySign`.
Nada de novo sobre sinal é introduzido.

### D6 — O estado da categoria é variante do `UiState`, não `if` na UI

`arquivada`, `sem lançamento algum` e `sem mês fechado com lançamento` mudam **qual figura é
o destaque** e **se existe variação**. Modelados como variantes do resultado do caso de uso,
a tela não escolhe regra nenhuma: ela renderiza o que recebeu.

Se isso virasse `if (category.isArchived)` dentro da `@Composable`, a mesma decisão passaria
a existir também no teste do `ViewModel` e no dia em que uma segunda superfície mostrasse a
mesma coisa — que é como duas telas passam a discordar sobre a mesma categoria.

### D7 — As ações continuam no rodapé fixado

`DetailActions()` é um footer *fixado* do `AdaptiveModal`, e o corpo rola por baixo dele. Movê-las
para o cabeçalho quebraria o padrão partilhado por todos os detalhes do app **e** perderia a
garantia de estarem sempre acessíveis. Ficam onde estão.

O corpo rolar significa que "cabe" não é a pergunta certa; a pergunta é o que fica acima da
dobra. As três figuras e o comando dos lançamentos ficam.

### D8 — A rota carrega a identidade, não o objeto

`TransactionsRoute` ganha `filterCategoryId: Long?`, seguindo `filterLabel`/`filterTarget`.
Carrega o **id**, porque `SpendingSubject.Categorized` embrulha um `Category` inteiro e uma
rota `@Serializable` não transporta grafo de domínio — resolver id para categoria é trabalho
do `ViewModel` que já observa o repositório.

Um id que não resolve para categoria alguma abre a lista no estado neutro. Categoria
arquivada resolve normalmente: a lista já observa `observeAllCategoriesIncludingClosed`.

### D9 — A contagem de lançamentos deixa de ser mensal

Sem seletor de mês, "transações no mês" não tem mês. Ela sobrevive como legenda do total da
janela, não como linha própria — e é a janela que a delimita, como todas as demais figuras.

## Risks / Trade-offs

- **Um membro novo em `IEntryRepository` obriga ~26 fakes de teste a acompanhá-lo** → é
  mecânico e o compilador aponta cada um. A alternativa (default no método) seria pior:
  esconderia de um fake que ele passou a responder por uma leitura que não implementa.

- **O total deixa de ser o histórico completo, e alguém vai reparar** → é a mudança
  deliberada. Mitigação: o rótulo declara a janela em toda figura, e a categoria arquivada
  — onde o histórico completo é a única leitura que resta — passa a mostrá-lo em destaque.

- **A variação some com mais frequência do que um percentual apareceria** → média zero,
  categoria nova e escala indisponível todas produzem ausência. Mitigação: cada caso diz o
  motivo em texto. Um `0%` seria mais bonito e falso.

- **Comparar um mês parcial contra uma média de meses fechados enviesa para baixo** → o viés
  existe e não é removível sem projetar o mês. Mitigação: o mês anuncia-se parcial com dia e
  total de dias. Contra a média o viés ao menos é constante, em vez de oscilar conforme o mês
  anterior tenha sido atípico.

- **O flow Maestro afirma hoje figuras com outro significado** →
  `view_category_total_amount` passa a ser o total da janela e
  `view_category_transaction_count` deixa de ser mensal. As asserções precisam ser reescritas
  junto com a mudança, não depois, ou passam a verde afirmando outra coisa.

- **A série mensal de uma categoria muito antiga cresce sem limite** → a consulta agrupa
  todos os meses existentes. Mitigação: a janela é aplicada acima, e o volume é uma linha por
  (mês, moeda) — irrelevante nesta ordem de grandeza. Se um dia deixar de ser, o corte por
  data entra na própria consulta.

## Open Questions

- A categoria arquivada mostra o intervalo de datas do histórico. A data final é a do último
  lançamento ou a do arquivamento? As duas diferem quando se arquiva uma categoria meses
  depois do último gasto, e a primeira descreve o dinheiro enquanto a segunda descreve a
  gestão.
- Um lançamento **futuro** entra em qual figura? A série o traz num mês que ainda não chegou,
  que não é fechado nem é o corrente. A leitura natural é excluí-lo da janela e do mês, mas
  isso precisa ser dito em algum lugar antes de alguém descobrir por acidente.
