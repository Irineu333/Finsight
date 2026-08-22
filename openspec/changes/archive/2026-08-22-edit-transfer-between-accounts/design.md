## Context

A transferência entre contas é escrita por `TransferBetweenAccountsUseCase` como uma `TransactionIntent` de duas pernas — `EXPENSE` na origem, `INCOME` no destino — e nunca mais pode ser tocada. O detalhe da operação não oferece edição, e a razão é uma só linha em `ViewTransactionUiState`:

```kotlin
val isEditable: Boolean =
    label != TransactionLabel.ADJUSTMENT &&
        transaction.monetaryEntries.size == 1 &&   // ← a transferência tem duas
        transaction.installmentId == null &&
        isChangeable
```

A pilha abaixo dessa linha está em três estados diferentes, e é isso que define o desenho:

| Camada | Estado |
|---|---|
| `LedgerEntryWriter.rewriteEntries(id, legs: List<TransactionLeg>, contra)` | **já aceita a lista** |
| `ITransactionRepository.updateTransaction(… leg: TransactionLeg …)` | estreitada para uma perna |
| `EditTransactionModal` / `TransactionForm` | vocabulário de despesa/receita: `type`, `target`, `category`, **um** valor |

O razão, portanto, não é obstáculo: `rewriteEntries` percorre para duas pernas o mesmo caminho que `createTransaction` já percorre — Σ = 0 por moeda quando as contas compartilham moeda, e resíduos de sinais opostos completados nas contas de conversão quando não compartilham. O que falta é a assinatura intermediária e um formulário que fale a língua da operação.

O formulário existe: `TransferBetweenAccountsModal`, em `feature/accounts/impl`. O detalhe que precisa abri-lo vive em `feature/transactions/impl`, e a regra `impl ⊄ impl` proíbe a referência direta. O projeto já resolveu exatamente esse problema uma vez, com `AccountsEntry.accountFormModal(account: Account? = null)` — consumido pelo dashboard via `koinInject`.

## Goals / Non-Goals

**Goals:**
- Corrigir uma transferência sem apagá-la, mudando qualquer uma das cinco coisas que a definem: origem, destino, valor, valor de destino e data.
- Fazer a transferência entre moedas entrar pela mesma porta da de moeda única, sem gate próprio.
- Manter o pagamento de fatura fora da edição **por declaração**, e não pelo efeito colateral de uma contagem.
- Preservar, na correção, cada validação que a criação aplica — com um dono só, não duas cópias.
- Não deixar o razão saber que uma transferência existe.

**Non-Goals:**
- Editar pagamento de fatura, ajuste de saldo ou parcela.
- Transformar uma transferência em despesa, receita ou pagamento — o formulário só oferece contas, então a conversão de natureza é impedida pela forma.
- Revogar, reescrever ou reconciliar taxas de câmbio já colhidas por operações anteriores.
- Reabrir a edição de uma operação cuja perna esteja sobre conta arquivada.

## Decisions

### D1 — O gate passa a nomear rótulos, porque a contagem deixa de guardar o que guardava

O gate atual recusa transferência **e** pagamento de fatura com o mesmo `monetaryEntries.size == 1`. Admitir a transferência por relaxamento da contagem — `size == 1 || size == 2` — abriria o pagamento junto, em silêncio. A condição passa a citar o rótulo:

```
ADJUSTMENT  → recusado (rótulo)          [inalterado]
parcelamento → recusado                   [inalterado]
perna arquivada → recusado                [inalterado, via isChangeable]
fatura CLOSED/PAID → recusado             [inalterado, um nível acima]
EXPENSE/INCOME → admitido se size == 1    [inalterado]
TRANSFER    → admitido                    [NOVO]
PAYMENT     → recusado por razão própria  [comportamento igual, razão nova]
```

`PAYMENT` sai da edição porque foi declarado fora de escopo, não porque tem duas pernas. Escrever assim é o que impede a próxima change de abri-lo sem perceber.

**Alternativa considerada e recusada:** manter `size == 1` e acrescentar `|| label == TRANSFER`. Funciona e é menor, mas deixa o pagamento de fatura protegido por uma contagem que já não é sobre ele — exatamente a fragilidade que a regra de derivação do projeto existe para evitar.

**Consequência verificada:** a transferência entre moedas tem pernas `{ASSET, CONVERSION}` e `deriveTransactionLabel` a devolve como `TRANSFER`, porque `CONVERSION` tem tipo próprio e cai no `else`. Mono e cruzada compartilham o gate sem ramo.

### D2 — A reescrita recebe a lista, e o guarda de dimensões para de receber uma constante

`updateTransaction` troca `leg: TransactionLeg` por `legs: List<TransactionLeg>`, e a implementação repassa em vez de embrulhar em `listOf`. Junto sai uma constante que hoje mente por omissão:

```kotlin
// hoje, na reescrita
ensureDimensionsAccept(dimensionIds = setOfNotNull(leg.dimensionId), settlesALiability = false)
// passa a ser, como createTransaction já faz
ensureDimensionsAccept(legs)   // deriva settlesALiability das naturezas das contas
```

Para `ASSET → ASSET` o valor derivado **é** `false`, então nenhum comportamento muda nesta change. O que muda é que a constante deixa de existir antes que uma operação de duas pernas com passivo chegue aqui e passe pelo guarda errado sem sintoma.

**Alternativa considerada e recusada:** manter a constante e documentar que ela só vale enquanto o pagamento estiver fora. Documentação não é verificada pelo compilador, e a change seguinte herda uma armadilha cujo aviso está num comentário.

### D3 — O formulário é o da transferência, alcançado pelo entry point

O detalhe passa a escolher o modal de edição pela natureza da operação. Para `TRANSFER`, ele pede o modal a `AccountsEntry`, no molde que o `accountFormModal` já estabeleceu:

```
feature/transactions/impl          feature/accounts/api          feature/accounts/impl
  ViewTransactionModal ──koinInject──▶ AccountsEntry ◀──implementa── AccountsEntryImpl
                                    transferModal(tx?)                      │
                                                              TransferBetweenAccountsModal
```

**Alternativa considerada e recusada:** ensinar o `EditTransactionModal` a editar transferências. Ele é construído sobre `TransactionForm`, cujos campos são `type`, `target`, `category` e **um** valor — nenhum deles existe numa transferência, e todos os que uma transferência precisa faltam. Seria um segundo formulário disfarçado de ramo dentro do primeiro.

**Alternativa considerada e recusada:** mover o formulário de transferência para `feature/transactions/impl`. Inverte a posse — a transferência é operação entre contas, e a tela de contas é de onde ela nasce — e quebraria o ponto de criação atual sem ganhar nada.

### D4 — Criar e corrigir são o mesmo formulário, com dois casos de uso sobre um validador

O molde é o do `AccountFormModal`, que o projeto já pratica: um `Modal` parametrizado, `isEditMode` derivado do parâmetro nulo, títulos distintos, botão único, eventos de analytics distintos, e dois casos de uso.

A diferença em relação à conta é que ali criar e atualizar têm regras **próprias** (a moeda é imutável só na atualização), enquanto aqui as cinco validações são idênticas nos dois modos. Duplicá-las contrariaria a primeira regra de estilo do projeto. Elas saem de dentro de `TransferBetweenAccountsUseCase` para um validador próprio, e os dois casos de uso passam a consumi-lo — que é precisamente a relação entre `CreateAccountUseCase`, `UpdateAccountUseCase` e `ValidateAccountNameUseCase`.

**Alternativa considerada e recusada:** um único caso de uso com `transactionId: Long? = null`. Menos código, mas funde duas operações que falham de formas diferentes (uma cria, a outra reescreve pernas já existentes e passa por dois guardas de estado anterior) atrás de um parâmetro opcional, e foge do molde que o projeto repete.

### D5 — A correção colhe a taxa nova e não revoga a antiga

A criação de uma transferência cruzada colhe a taxa que ela aplicou e a grava no acervo. A correção faz o mesmo, e não toca no que já está lá.

Isso não é descuido, é a única leitura coerente com o que o acervo já é. A taxa é observação sobre um dia e sobrevive à operação que a revelou; **apagar** a operação já deixa a observação de pé, e a remoção existe apenas como ato manual do usuário na tela de taxas. Uma correção que limpasse o que a remoção não limpa inverteria a regra pelo caminho mais estreito.

O comportamento resultante, verificado contra a gravação `REPLACE` sobre `(par, data, origem)`:

| A correção muda… | A observação anterior |
|---|---|
| só o valor | **é sobrescrita** — mesma chave |
| a data | permanece na data antiga |
| uma das pontas, mudando o par | permanece no par antigo |
| de cruzada para moeda única | permanece; nada novo é colhido |

O caso dominante — corrigir um dígito — se resolve sozinho. Os demais deixam uma observação que o usuário alcança pela tela de taxas, exatamente como já acontece hoje ao apagar uma transferência cruzada.

**Alternativa considerada e recusada:** remover a observação antiga antes de colher a nova. Exigiria que a correção soubesse qual linha do acervo veio dela — vínculo que o modelo deliberadamente não tem, porque a taxa não é propriedade da operação —, e apagaria uma observação que pode ter sido corroborada por outra operação do mesmo dia.

### D6 — A conta arquivada congela a transferência, e isso não custa código

`isChangeable` já é `entries.closedLegBlockingChange() == null`, e `closedLegBlockingChange` já responde por qualquer perna sobre conta permanente arquivada. Como a transferência tem duas pernas `ASSET`, basta uma delas estar arquivada para a operação inteira congelar — edição e remoção juntas.

A transferência herda isso sem regra nova, e a tela já tem a mensagem: `view_transaction_archived_message` é exibida no lugar dos botões quando `!isChangeable`. A consequência é deliberada e foi confirmada: uma transferência para conta arquivada depois não se edita nem se apaga.

### D7 — A perna de destino ganha dono no razão, ao lado da de origem

Semear o formulário exige as duas pontas. O razão tem dono para uma só: `sourceLeg()` — a perna `ASSET` de valor **negativo**, com o critério nomeado e não implícito. Não há nada para o destino.

Resolver isso dentro do ViewModel seria reimplementar no consumidor uma regra derivável do domínio, que a regra de derivação do projeto atribui a um dono único. `destinationLeg()` entra em `core/ledger` espelhando `sourceLeg()`: a perna `ASSET` de valor **positivo**.

O filtro por `ASSET` não é detalhe. Uma transferência entre moedas tem quatro pernas — duas `ASSET` e duas `CONVERSION` —, e a de conversão de resíduo positivo é indistinguível da conta de destino para quem procurar apenas "a perna positiva". Filtrar por `ASSET` primeiro, como `sourceLeg` já faz, exclui a conversão por construção em vez de por cuidado.

**Alternativa considerada e recusada:** derivar o destino como "a perna monetária que não é a de origem". Funciona para a transferência e quebra para o pagamento de fatura, cuja segunda perna monetária é `LIABILITY` — um dono no razão não pode valer só para o chamador que o pediu.

### D8 — O ponto de entrada expõe apenas a correção, e o modal ganha dois construtores

A proposta descrevia `transferModal(transaction: Transaction? = null)`, no molde de `accountFormModal(account: Account? = null)`. **Não serve.** O paralelo é falso: criar uma conta não exige nada, mas `TransferBetweenAccountsModal` recebe `sourceAccount: Account` obrigatório e não-nulo, porque a criação nasce apontando para uma conta. Um único membro com tudo nulo aceitaria os dois estados inválidos — sem conta e sem transação, ou com ambas.

Quem atravessa a fronteira é só a **correção**: a criação nasce no `AccountsScreen`, dentro do mesmo módulo, e nunca precisou do entry point. Então `AccountsEntry` ganha **um** membro, `editTransferModal(transaction: Transaction): Modal`, sem parâmetro opcional e sem estado inexprimível.

O modal passa a ter dois construtores públicos sobre um privado, de modo que cada chamador só consiga enunciar um modo válido:

```
TransferBetweenAccountsModal(sourceAccount: Account)   → criação
TransferBetweenAccountsModal(transaction: Transaction) → correção
```

Em modo de correção a conta de origem não é parâmetro: ela é `sourceLeg()`, e pedi-la ao chamador abriria a porta para ela discordar da transação.

### D9 — Um valor gravado não é uma sugestão, e o campo precisa saber a diferença

`CounterpartAmountField` já distingue dois tipos de número: o que o app ofereceu (`offered`, lembrado) e o que o usuário digitou. Ele retira o primeiro quando a moeda muda e nunca sobrescreve o segundo. A correção introduz um **terceiro**: o valor gravado, que não é oferta nem digitação, e que o componente hoje trata mal nos dois sentidos.

Sem mudança, o efeito que sincroniza a sugestão faz duas coisas erradas ao abrir uma correção cruzada:

1. **Apaga.** Quando não existe observação da mesma data, o ramo `else` chama `state.clearText()` — e o valor que a operação registra desaparece da tela antes de o usuário ver.
2. **Sobrescreve numa corrida.** O valor semeado chega do ViewModel, que hidrata as contas por fluxo; se o efeito rodar antes, `state.text` está vazio, `typedOver` é `false`, e a sugestão do acervo ocupa o campo no lugar do que foi gravado.

E um terceiro, na direção oposta: trocada a conta de destino, o efeito compara com `offered` (nulo), conclui `typedOver` e **preserva** os dígitos gravados sob o símbolo da moeda nova — o defeito que o próprio KDoc do componente descreve para a oferta, agora com o valor gravado no lugar.

O componente passa a receber o valor pré-existente explicitamente. A regra fica: um valor gravado **não é sobrescrito** pela sugestão (como a digitação) e **é retirado** quando a moeda do campo muda (como a oferta).

**Alternativa considerada e recusada:** passar `suggestion = null` em modo de correção. Não resolve — sem sugestão o efeito cai justamente no ramo que limpa o campo.

**Alternativa considerada e recusada:** um campo próprio para a correção. Duplicaria a composição que já carrega a taxa implícita, o rótulo e o placeholder datado, e as duas divergiriam.

### D10 — O validador recebe o relógio, em vez de ler o do sistema

A regra "a data não pode ser futura" hoje lê um `Clock.System` global, declarado como propriedade de topo em `TransferBetweenAccountsUseCase`. O formulário que a alimenta lê outro relógio — o injetado, `koinInject<Clock>()` —, que é também o que limita o seletor de data e o que o resto do app usa.

Extraído o validador, ele passa a ser o dono da regra, e recebe o `Clock` por injeção. Não é mudança de comportamento — os dois relógios coincidem em produção —, é o que impede que a fronteira e o formulário discordem sobre "hoje" e o que torna a regra testável sem relógio real.

### D11 — A correção preserva o título que o formulário não mostra

`updateTransaction` reescreve a linha inteira, título incluído, e o formulário de transferência não tem campo de título — a criação grava `null`. Passar `null` na correção seria correto para toda transferência que este app cria, e destrutivo para qualquer uma que tenha título por outra via.

A correção passa `transaction.title`, preservando o que existe. Apagar em silêncio um dado que a tela não mostra é a forma mais barata de perder informação, e escrever o que não se ofereceu é escrever em nome do usuário.

## Risks / Trade-offs

- **[Reescrever uma transferência cruzada apaga quatro pernas e grava quatro]** → A reescrita e a atualização da linha compartilham uma única transação de escrita (`useWriterConnection { immediateTransaction { … } }`), como já compartilham hoje; uma falha no meio não deixa a operação sem pernas. O caminho de quatro pernas não é novo — é o que a criação cruzada já executa.

- **[O gate por rótulo é mais frouxo que uma contagem, e um rótulo novo entraria sem ser notado]** → `TransactionLabel` é um `enum` de cinco membros derivado de um conjunto fechado de tipos de conta; acrescentar um membro é mudança deliberada que atravessa a derivação inteira. O gate cita os rótulos que admite, não os que recusa, então um membro novo nasce **fora** da edição.

- **[Duas observações de taxa contraditórias sobre o mesmo dia após corrigir a data]** → Aceito e declarado (D5). A precedência do acervo é por data e, dentro dela, por origem; duas observações derivadas em datas diferentes são duas observações, não um conflito. O usuário tem a tela de taxas para remover a que não sustenta.

- **[Corrigir origem ou destino move dinheiro de uma conta para outra sem que a conta antiga seja tocada por uma escrita visível]** → É o mesmo risco que a edição de despesa já corre, e o guarda já existe: `ensureClosedAccountsKeepTheirBalance` roda sobre as pernas **antigas** antes da reescrita, precisamente para impedir que retargetar devolva saldo a uma conta arquivada.

- **[O formulário de transferência ganha um modo e fica mais difícil de ler]** → Mitigado por seguir o molde já existente no repositório em vez de inventar outro: quem leu `AccountFormViewModel` reconhece `isEditMode` e os dois casos de uso sem aprender nada novo.

## Migration Plan

Não há migração. Nenhuma tabela, coluna ou índice muda, `AppDatabase` não muda de versão, e nenhum dado gravado é reinterpretado. A change é de comportamento e de superfície: uma assinatura interna, um gate derivado, um membro de entry point e um modo de formulário.

O caminho antigo continua válido durante todo o percurso — apagar e recriar uma transferência nunca deixa de funcionar —, então não há janela em que o usuário fique sem forma de corrigir.

## Open Questions

As três que a exploração levantou foram fechadas: pagamento de fatura fora do escopo (D1), validador extraído no molde do projeto (D4), taxa não revogada pela correção (D5), e o congelamento por conta arquivada confirmado como desejado (D6).

Uma varredura posterior, contra o código em disco, encontrou cinco questões que a primeira leitura não tinha enunciado — todas de implementação, todas fechadas em D7 a D11: a perna de destino não tinha dono no razão; a assinatura proposta para o ponto de entrada não servia para os dois modos; o campo do valor de destino apaga ou sobrescreve um valor gravado; o validador leria um relógio diferente do que o formulário usa; e a correção apagaria um título que não mostra.

Duas coisas foram verificadas e **não** são questões:

- **Uma transferência nunca carrega metadados de recorrência ou de parcelamento.** `ConfirmRecurringUseCase` escreve sempre uma perna monetária mais a contra, nos dois ramos (conta e cartão), então nenhuma transferência nasce por esse caminho. `updateTransaction` atualiza apenas título e data da linha, deixando essas colunas intactas de qualquer forma.
- **O evento de analytics tem par natural.** `TransferBetweenAccounts` é um `object` sem parâmetros, e o padrão `Create*`/`Edit*` já existe ao lado (`CreateAccount`/`EditAccount`).

Resta uma limitação conhecida, herdada e **fora do escopo**: nada recusa um segundo envio enquanto o primeiro está em voo. É o defeito já registrado no backlog como `no-write-refuses-a-second-submit-while-the-first-is-in-flight`, comum a todos os formulários de escrita do app. A correção de transferência nasce com ele como todo o resto, e resolvê-lo aqui só para um formulário criaria uma exceção sem dono.
