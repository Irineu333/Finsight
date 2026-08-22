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

Nenhuma pendente. As três que a exploração levantou foram fechadas: o pagamento de fatura fica fora do escopo (D1); as validações seguem o molde de validador extraído do projeto (D4); e a taxa colhida não é revogada pela correção (D5). O comportamento da conta arquivada foi confirmado como desejado (D6).
