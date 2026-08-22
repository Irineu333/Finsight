## Context

O razão já carrega `title` em `Transaction` e em `TransactionIntent`. O que não existe é o
formulário que o ofereça para uma transferência e a superfície que o exiba: os dois use cases
gravam `null` (`TransferBetweenAccountsUseCase.kt:62`) e o card da lista nomeia a operação pela
sua forma antes de olhar para o título.

As superfícies que nomeiam uma operação hoje não concordam entre si:

| Superfície | Onde | Cadeia |
|---|---|---|
| Detalhe da operação | `ViewTransactionModal.kt:216-223` | título → categoria → forma |
| Detalhe do ajuste | `ViewAdjustmentModal.kt:111` | título → forma |
| **Card da lista** | `TransactionCard.kt:163-169` | **forma → título → categoria** |
| Card de parcelamento | `InstallmentUiMapper.kt:58` | título → categoria → `"Untitled"` |
| Recorrência | `Recurring.kt:17` | título → categoria → `"Untitled"` |

A spec `transaction-detail:171-183` já enuncia a regra correta e a justifica. Nenhuma spec
cobre a lista, e é lá que a cadeia está invertida.

Um fato apurado contra o código em disco governa o resto deste documento: **`"Untitled"` é
inalcançável hoje, e é o card invertido que o torna inalcançável.** As únicas transações que
nascem sem título e sem categoria são as que use cases escrevem — transferência, ajuste de
saldo, ajuste de fatura, pagamento de fatura —, e são exatamente as quatro que o `when` do card
intercepta antes do `else`. Nos demais caminhos o segundo elo é garantido:
`ValidateTransactionFormUseCaseImpl.kt:38` para o formulário de transação,
`RecurringForm.kt:41` para a recorrência, e `DeleteCategoryUseCase` recusa apagar uma categoria
que tenha qualquer dependente, de modo que nenhuma linha perde a sua categoria depois.

## Goals / Non-Goals

**Goals:**
- Dar à transferência um título opcional, gravado tanto na criação quanto na correção.
- Fazer da precedência **título > categoria > forma** uma regra única, válida em toda superfície
  que nomeia uma operação.
- Remover `"Untitled"` por prova de inalcançabilidade, e não por otimismo.
- Deixar cada superfície declarar o seu terceiro elo, em vez de herdar um literal genérico.

**Non-Goals:**
- Campo de título no **ajuste de saldo** (D9).
- Título obrigatório, filtro ou busca por título.
- Mudar o que o detalhe faz: ele já implementa a regra e não muda de comportamento.
- Mexer na nomeação de contas, cartões, categorias ou orçamentos.

## Decisions

### D1 — Um formulário, um campo, opcional

O campo entra no formulário único que já serve os dois modos e não cria um terceiro modo. Ele é
opcional, como todo título no app: `AddTransactionModal`, `EditTransactionModal` e o formulário
de recorrência tratam título como preenchimento livre, e `isValidTransfer`
(`TransferBetweenAccountsModal.kt:294`) não ganha cláusula alguma.

Obrigá-lo contradiria o resto do app e quebraria o flow Maestro de contas sem ganho: quem não
tem razão a registrar não deve ser impedido de transferir.

**Ordem no formulário:** o título vem **primeiro**, antes dos seletores de conta, como em
`AddTransactionModal.kt:119`. É o precedente do app, e o campo que nomeia a operação abrindo o
formulário lê melhor que um apêndice depois da data.

### D2 — A correção grava o título do formulário, e D11 da mudança anterior é substituído

`UpdateTransferUseCase.kt:58-63` hoje carrega `transaction.title` adiante, com um comentário que
existe porque o formulário não tem o campo. Com o campo, ele passa a gravar o que o formulário
diz — inclusive `null`, quando o usuário apaga o que estava lá.

Isso **não** enfraquece a regra "o formulário não apaga o que não exibe": ela continua valendo
inteira, e apenas deixa de ter o título como exemplo. O que muda de natureza é o apagamento: era
perda silenciosa de um dado que a tela não mostrava, e passa a ser intenção declarada sobre um
campo que a tela mostra. O comentário sai no mesmo commit que o torna falso.

### D3 — Dois elos com dono único; o terceiro é da superfície

`displayTitleOrNull(title, category)` (`DisplayTitle.kt:32`) já é o dono dos dois primeiros elos,
e já existe exatamente porque uma superfície precisou parar antes do último. Ele passa a ser a
única porta; `displayTitleOf` é removida.

O terceiro elo **não** pode ter dono único, porque não é um texto só:

```
LISTA                              DETALHE
⇄ Transferência                    transferência      ← natureza
                                   Entre Contas       ← forma
transaction_card_transfer          view_transaction_title_transfer
nome autossuficiente               complemento: a natureza já foi dita acima
```

E no detalhe, para gasto, receita e ajuste, o terceiro elo é a **omissão da linha**
(`transaction-detail:181-183`). Uma função única com terceiro elo fixo não serviria a nenhum dos
dois. A precedência universaliza; o conteúdo do terceiro elo é de quem o exibe, porque só essa
camada sabe o que já foi dito ao redor — e porque é lá que um recurso de string é resolvido.

### D4 — `TransactionUi.title` vira `String?`, e o card resolve a forma

O mapper (`TransactionUiMapper.kt:47`) passa a devolver `displayTitleOrNull(...)`, e
`TransactionCard.kt:157-171` inverte a cadeia: o título vence, e a forma entra quando não há
título nem categoria. `TransactionUi.title` tem um único consumidor, o próprio card.

Isto espelha o detalhe, que já divide a responsabilidade assim
(`ViewTransactionUiState.kt:70` guarda `String?`, `ViewTransactionModal` fornece a forma). As
duas superfícies passam a concordar não só na precedência, mas em onde cada metade da regra mora.

**Alternativa considerada:** o mapper devolver `UiText` com o terceiro elo já escolhido
(`UiText.Raw` para título e categoria, `UiText.Res` para a forma), deixando o card só renderizar.
Põe toda a decisão no mapper, o que agrada a `presentation-mapping:23`. Recusada porque o
terceiro elo do card e o do detalhe são textos diferentes, então o mapper teria de saber para
qual superfície está mapeando — que é o acoplamento que o DTO plano existe para evitar.

### D5 — `"Untitled"` é removido por prova, não por substituição

Depois de D4, nenhum caminho o alcança:

- `EXPENSE`/`INCOME`: o segundo elo é garantido pelo validador do formulário, e a categoria não
  desaparece depois.
- `TRANSFER`/`PAYMENT`/`ADJUSTMENT`: o terceiro elo existe sempre, porque a forma é derivada dos
  tipos de conta das pernas (`deriveTransactionLabel`) — enum fechado, nunca ausente.

O literal é texto em inglês num app que existe em pt e en (`DisplayTitle.kt:16-19`, que o
descreve como "deixado como estava; é uma decisão separada"). Esta é a decisão separada.

### D6 — Onde o invariante é do domínio e está provado, afirma-se

`Recurring.label` não tem terceiro elo a inventar: o seu segundo elo é garantido por
`RecurringForm.toRecurring` (`RecurringForm.kt:41`), dono único da regra. A garantia foi
verificada, não presumida:

- Todo caminho de escrita passa por `toRecurring`: `SaveRecurringUseCase.kt:37-52` e
  `StartRecurringFromTransactionUseCaseImpl:57`. `ArchiveRecurringUseCase.kt:17` e
  `UnarchiveRecurringUseCase.kt:24` fazem `copy(isArchived = …)` sobre uma linha já lida, sem
  tocar título nem categoria.
- `git log -S "TITLE_OR_CATEGORY_REQUIRED"` aponta como commit mais antigo o próprio
  `014df500b Feat(Recurring): Add recurring transactions feature` — a regra nasceu com a feature,
  então nenhuma linha jamais pôde ser gravada violando-a.
- Nenhuma migração inventa recorrências: `Migration3To4.kt:100` é `INSERT … SELECT` sobre linhas
  existentes.

Então `label` lança em vez de mascarar. Não é programação defensiva: é a declaração executável de
um invariante que outro módulo sustenta, e que falha alto e cedo no dia em que um caminho novo o
violar — que é exatamente quando se quer saber.

Os dois testes que fixam o estado morto (`ComposeAppCommonTest.kt:15` e `:52`) invertem de
sentido: passam a provar que a violação lança.

### D7 — Onde a garantia é só de tela, dá-se terceiro elo real

O parcelamento **não** recebe o mesmo tratamento, e a diferença não é inconsistência: é cada caso
recebendo o que a sua garantia comporta.

`ValidateTransactionFormUseCase` só alimenta `canSubmit` — todos os seus call sites são de
habilitação de botão (`AddInstallmentViewModel.kt:116`, `EditTransactionViewModel.kt:201`,
`AddTransactionViewModel`) — e `AddInstallmentUseCaseImpl.kt:123` chama `buildTransactionUseCase`,
que valida conta, cartão e fatura (`BuildTransactionUseCaseImpl.kt:36,54,58`) e **não** valida
título-ou-categoria. Lançar ali apostaria a estabilidade de uma tela na disciplina de um botão.

E não é preciso: uma parcela tem forma — é sempre despesa em cartão. `InstallmentUi.title` vira
`String?` e `InstallmentsScreen.kt:510` fornece o terceiro elo, pelo mesmo desenho de D4.

**O texto é "Parcelamento", não "Despesa"**, pela mesma razão que o detalhe diz "Entre Contas" e
não "Transferência": a tela já mostra "1/12" ao lado, e "Despesa • 1/12" diz menos que
"Parcelamento • 1/12".

### D8 — A lista passa a misturar nomes, e isso é o comportamento correto

```
⇄ Reserva de emergência      R$ 500,00
⇄ Transferência              R$ 120,00
⇄ Acerto com o Pedro          R$ 80,00
```

É o que já acontece com gastos, onde um item traz título próprio e o seguinte traz o nome da
categoria. A natureza não depende do texto: o ícone `SwapHoriz` e a cor `Info`
(`TransactionCard.kt:176,186`) continuam dizendo o que a operação é, como o ícone e a cor de um
gasto já dizem.

### D9 — O ajuste de saldo fica de fora

`AdjustBalanceUseCase.kt:56` também grava `null`, e `ViewAdjustmentModal.kt:111` já lê um título
que ninguém escreve. Mesmo assim ele fica fora, por dois motivos.

O primeiro é a motivação: uma transferência acontece por razões variadas, e um ajuste tem uma só
— reconciliar o saldo com a realidade. O segundo é a forma da operação: o ajuste é idempotente e
reescrito por recálculo (`AdjustBalanceUseCase.kt:75-95` soma sobre a perna existente e apaga a
transação quando o resultado é zero), de modo que um título digitado teria de sobreviver a
reescritas que o usuário não vê acontecer. Nada impede que ganhe um campo depois; impede que
ganhe **de passagem**, sem que essas duas perguntas sejam respondidas.

### D10 — A inversão do card e a remoção do literal são um único passo

Inverter a cadeia sem fornecer o terceiro elo tornaria `"Untitled"` visível na tela mais usada do
app, para toda transferência, pagamento e ajuste. Não são duas mudanças que podem ser entregues
em ordem: são uma. As tarefas as mantêm no mesmo passo por isso, e não por conveniência.

## Risks / Trade-offs

- **[`Recurring.label` lançar em produção]** → É o objetivo declarado em D6, e a probabilidade foi
  reduzida a zero pelos caminhos existentes, não estimada. O risco real é um caminho de escrita
  **futuro** que não passe por `toRecurring`; a exceção é justamente o que o denuncia, em vez de
  deixá-lo produzir uma tela em inglês. Mitigação adicional: os dois testes convertidos fixam o
  invariante em vez de fixar o mascaramento.

- **[`TransactionUi.title` e `InstallmentUi.title` viram anuláveis]** → Um consumidor cada
  (`TransactionCard.kt:131`, `InstallmentsScreen.kt:510`). O compilador acusa os dois, e nenhum
  outro DTO é tocado.

- **[Uma transferência antiga continua sem título]** → Nenhuma migração de dados é feita, e nenhuma
  seria correta: inventar um título para uma operação passada seria escrever em nome do usuário.
  Elas passam a mostrar "Transferência", que é o que mostram hoje.

- **[O flow Maestro percorre o formulário com um campo a mais]** → Os elementos são alcançados por
  `id:` (`.maestro/flows/accounts/lifecycle.yaml:201-290`), e o campo é opcional, então o flow
  continua válido sem tocá-lo. Um `id` novo é publicado para que um flow futuro possa exercitá-lo.

- **[O card deixa de ter um nome fixo por natureza, e alguém pode ler a lista mais devagar]** →
  Aceito e declarado em D8. É a troca que a mudança inteira existe para fazer: a razão da operação
  passa a valer mais, na lista, do que a repetição da sua forma.
