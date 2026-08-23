## Context

O pagamento de fatura já é **uma** operação com superfície única (`invoice-settlement`): cartão e
fatura escolhidos no próprio formulário, o devido lido da fatura selecionada, o modo derivado do
estado dela, e uma forma só de escrita — `WriteInvoicePaymentUseCase`, duas pernas, a dimensão
apenas na do cartão.

Falta a correção. E ela não precisa ser inventada: `transfer-editing` já resolveu o mesmo problema
sobre a mesma fronteira de escrita — `AccountsEntry.editTransferModal` → `UpdateTransferUseCase` →
`ITransactionRepository.updateTransaction`. Esta change replica esse molde, com uma torção que a
transferência não tem: o pagamento tem **modo** e tem **teto**, e os dois se comportam de forma
diferente quando a operação já existe.

O recorte já está imposto pelo código, em três níveis independentes que concordam entre si:

```
1. ViewTransactionModal.kt:352   invoice.status.isEditable == false → esconde editar E excluir
                                 (Status.isEditable = RETROACTIVE | OPEN | FUTURE)
2. InvoiceWriteGuard.kt:36-37    via ensureDimensionsAcceptRemoval, que passa
                                 settlesALiability=false sempre (TransactionRepository.kt:170)
                                 → PAID lança Paid; CLOSED lança ClosedToNewSpending
3. Invoice.acceptsPartialPayment domínio: OPEN | RETROACTIVE
```

Nenhum dos três é relaxado por esta change. O que existe hoje e não deveria é uma quarta barreira,
redundante e cega: `TransactionLabel.PAYMENT -> false` (`ViewTransactionUiState.kt:164`), que
recusa também o parcial que as outras três liberam.

## Goals / Non-Goals

**Goals:**
- Corrigir no lugar um pagamento **parcial** de fatura, pelo mesmo formulário que o cria.
- Alcançar todos os campos da operação — cartão, fatura, conta, valor que liquida, valor que sai,
  data — sem congelar nenhum.
- Dar ao teto do valor um dono único e uma fórmula só, correta na criação e na correção.
- Declarar a incorrigibilidade da quitação como regra, não como silêncio de escopo.

**Non-Goals:**
- Corrigir ou remover uma quitação total. Fatura `PAID` é história liquidada.
- Desfazer uma quitação (`PAID → CLOSED`), reabrir fatura paga, ou qualquer caminho novo no ciclo
  de vida da fatura. O beco sem saída da quitação errada é um defeito conhecido e é outra change.
- Tocar `:core:ledger` — nem a fronteira de escrita, nem `InvoiceWriteGuard`, nem `IEntryRepository`.
- Dar título ao pagamento. O formulário não o exibe; a correção o preserva.

## Decisions

### D1 — Um formulário, dois modos, distinguidos pela presença da operação

`transaction` é a **única** coisa que separa os dois modos, exatamente como em
`TransferBetweenAccountsViewModel` (`isEditMode = transaction != null`). Não há flag booleana e não
há um segundo formulário.

O modal expõe isso com construtor privado e dois construtores públicos — um por modo — como
`TransferBetweenAccountsModal` já faz. Koin recebe os dois parâmetros e resolve o do modo ausente
com `getOrNull()`, como `AccountsModule` já faz.

Com **uma** diferença do molde, e ela precisa ser dita porque é o oposto do que a transferência
faz. Lá o construtor de correção deriva a conta de origem da própria transação
(`transaction.entries.sourceLeg()?.account`), porque uma conta está hidratada na perna. Aqui a
fatura **não** está: a perna carrega um `dimensionId`, e transformá-lo numa `Invoice` é resolver
identidade → facade, o que exige repositório. O modal não pode fazê-lo e não deve tentar; quem
resolve a fatura pela dimensão é o ViewModel, na abertura.

*Alternativa considerada:* um `EditInvoicePaymentModal` próprio. Recusada pela mesma razão que
`transfer-editing` a recusou — seria uma segunda gramática para a mesma operação, e as duas
divergiriam. E aqui divergiriam mais rápido, porque o formulário de pagamento carrega regras
(o teto, a janela da data, o campo de contrapartida) que a transferência não tem.

### D2 — Na correção o modo é fixo, e o conjunto oferecido decorre dele

O estado da fatura decide o modo de uma operação **nova**. Uma operação já escrita tem o modo que
tem: corrigir um pagamento parcial é reafirmar um pagamento parcial.

Daí o conjunto de faturas oferecido muda com o modo, sem que a tela enumere status:

```
criação    → invoices.filter(Invoice::acceptsPartialPayment ou acceptsFullSettlement)
             ou seja, Invoice::acceptsPayment          (InvoicePaymentViewModel.kt:95, hoje)
correção   → invoices.filter(Invoice::acceptsPartialPayment)
```

**Por que isso não é preciosismo.** Sem essa restrição, trocar para uma fatura `CLOSED` durante uma
correção produziria isto:

```
settles vira true          → o campo trava no devido da fatura fechada
o botão continua "salvar"  → mas a operação virou uma quitação
o razão DEIXA passar:
    remoção → dimensão antiga (OPEN)                            ✅ livre
    escrita → dimensão nova (CLOSED), settlesALiability = true  ✅ o guard só recusa
                                                                   gasto novo
resultado: fatura CLOSED com devido zero e status CLOSED — nunca PAID,
           porque só PayInvoicePaymentUseCase marca, e a correção não o chama
```

Isso violaria dois requisitos vigentes de `invoice-settlement` de uma vez: *"um valor parcial sobre
fatura fechada MUST NOT ser aceito pelo domínio se algum caminho o alcançar"* e *"`PAID` é sempre
precedido de `CLOSED`"*.

A permissão correspondente vem de graça e no lugar certo: `UpdateAdvanceInvoicePaymentUseCase`
herda o `ensure(invoice.acceptsPartialPayment)` que `AdvanceInvoicePaymentUseCase` já tem
(`AdvanceInvoicePaymentUseCase.kt:88`). Oferta e permissão lendo o mesmo predicado do domínio, como
`invoice-settlement` exige — e sem tocar no razão.

### D3 — O teto é o devido desconsiderando a própria operação

O devido é `Σ entries da dimensão` (`CalculateInvoiceUseCase`), e essa soma **já inclui o pagamento
que está sendo corrigido**:

```
fatura deve 800, pago 300  →  devido = 500
corrigir esses 300 para 700:
    ensure(amount <= currentBillAmount)  →  700 <= 500  ✗ recusado, embora caiba
```

A formulação que resolve as três situações com uma frase:

> **o devido da fatura F, computado sobre as entries que não pertencem à operação T.**

| situação | T tem pernas em F? | efeito |
|---|---|---|
| criação | T não existe | nada é desconsiderado — o devido corrente |
| correção, mesma fatura | sim | o devido volta a incluir o que T liquidou |
| correção, fatura trocada | não | nada é desconsiderado — o devido da fatura nova |

Uma fórmula, três situações, sem ramo. Isso vira um parâmetro em `CalculateInvoiceUseCase`, que já
é o dono único dessa leitura — e não um segundo caso de uso ao lado dele.

*Sobre o default.* `excluding: Long? = null` recebe default, ao contrário de `contra` em
`updateTransaction` e de `account` em `InvoicePaymentAction.Submit`, que deliberadamente não têm.
A diferença: ali o valor omitido é *errado* (uma escrita desbalanceada, a conta padrão no lugar da
escolhida); aqui o valor omitido é o devido corrente, que é exatamente o que toda leitura que não é
uma correção quer. O default é o caso comum e correto, não uma armadilha.

*Sobre a composição.* O cálculo compõe duas leituras que já existem — `dimensionOwedByCurrency` e
`getEntriesByTransaction` — em vez de pedir uma consulta nova ao `IEntryRepository`. Expresso como
"o devido sobre as entries que não são de T", o sinal cai sozinho e não depende de o pagamento
debitar ou creditar. Atenção à unidade: entries são `Long` em centavos, o devido é `Double` em
unidades.

### D4 — Abrir preserva, trocar recalcula

Uma regra só, governando valor, valor de contrapartida, data e teto:

```
      ABRIR a correção            │      TROCAR cartão ou fatura
────────────────────────────────  │  ────────────────────────────────
 preenche com o que a operação    │   valor e contrapartida limpos
 registra: cartão, fatura, conta, │   (regra que já existe)
 valor, contrapartida, data       │   teto ← devido da fatura nova
                                  │   data ← reposicionada, se a atual
 NÃO reposiciona a data           │          não couber na janela nova
 (settlementDateFor, VM:151)      │
```

Abrir não é intenção declarada; trocar é. É a mesma distinção que `transfer-editing` faz entre o
valor registrado e a sugestão do acervo — *"a sugestão do acervo não substitui o valor registrado"*
— e a mesma que `invoice-settlement` já faz ao mandar limpar o valor quando o usuário troca a
fatura.

### D5 — `UpdateAdvanceInvoicePaymentUseCase` como irmão, não como flag

Espelha `UpdateTransferUseCase`: mesmas validações do caso de criação, `updateTransaction` no lugar
de `createTransaction`, e a identidade da transação preservada — as pernas são reescritas, a
operação continua sendo a mesma.

As validações não são copiadas. Hoje elas vivem inline em `AdvanceInvoicePaymentUseCase`; a
transferência já resolveu isso extraindo `ValidateTransferUseCase`, e é o mesmo movimento aqui:
`ValidateInvoicePaymentUseCase` devolvendo `Either<InvoiceError, ValidatedInvoicePayment>`, com a
`Invoice` resolvida dentro — porque *"esta fatura existe"* é uma das regras que ele verifica, e um
chamador que a lesse de novo repetiria a busca cuja falha o validador já nomeou. É a justificativa
que `ValidatedTransfer` documenta para devolver as duas contas.

Ou um validador compartilhado, ou os dois caminhos divergem sem que nada acuse — que é exatamente o
que `invoice-settlement` proíbe ao dizer que a regra *"MUST NOT ser reimplementada por cada um dos
dois caminhos"*.

O escopo do validador é o pagamento **parcial**, nos seus dois modos. `PayInvoicePaymentUseCase`
não passa a consumi-lo: a quitação tem regras próprias (`acceptsFullSettlement`, o valor que é o
devido em vez de ser limitado por ele) e está fora desta change.

A forma da escrita continua com dono único em `WriteInvoicePaymentUseCase`, que passa a servir
também à reescrita.

### D6 — A travessia da fronteira entre features

`CreditCardsEntry` ganha `editInvoicePaymentModal(transaction: Transaction): Modal` — um membro
novo ao lado de `invoicePaymentModal(invoiceId)`, e não um membro só com tudo nulável. É a decisão
que `AccountsEntry` já documenta: *"um único membro cobrindo os dois modos teria de receber tudo
como nulável e aceitaria dois estados que não significam nada"*.

Em `ViewTransactionModal`, a escolha do formulário passa a ter três braços em vez de dois, pela
mesma lógica que já governa os dois atuais — qual formulário corrige uma operação decorre do que a
operação **é**.

### D7 — A pré-seleção não passa por `selectCreditCard()`

`selectCreditCard()` limpa a fatura antes de assumir o cartão novo
(`InvoicePaymentViewModel.kt:236`), porque o par (cartão novo, fatura antiga) nomearia uma janela
que nenhuma das duas seleções representa. Correto para uma troca — destrutivo para a abertura de
uma correção, que evaporaria o valor e a data pré-preenchidos.

A abertura em modo de correção estabelece cartão e fatura diretamente, sem atravessar o caminho da
troca. É D4 aplicado ao mecanismo.

O que ela preenche vem dos donos que o razão já tem para essa leitura, e não de entries escolhidas
a dedo:

```
entries.liabilityLeg()   →  o valor que liquida a fatura, na moeda do cartão,
                            e o dimensionId que resolve qual fatura é
entries.sourceLeg()      →  a conta pagadora e o que saiu dela
```

`sourceLeg()` filtra por `ASSET` antes de olhar o sinal, o que importa aqui: num pagamento entre
moedas há pernas de conversão, e a que segura o resíduo negativo é indistinguível da conta pagadora
para quem procurar apenas "a perna negativa".

### D8 — O que o formulário não exibe, ele preserva

`updateTransaction` exige `title` explícito, sem default, e o pagamento grava `title = null` hoje. A
correção passa adiante o título que a transação carrega, em vez de `null` por hábito —
`transfer-editing` já enuncia a regra: *"O formulário MUST NOT apagar o que ele não exibe."*

### D9 — O cabeçalho anuncia o modo, e o subtítulo não precisa mudar

O cabeçalho passa a nomear a correção — "Corrigir pagamento" no lugar de "Pagamento de Fatura" —,
como `transfer-editing` faz ("Transferir entre contas" → "Corrigir transferência").

Isto **não** contradiz a decisão recente de deixar o cabeçalho parado. O que aquela decisão retirou
foi a dependência entre o cabeçalho e a **seleção**: o head não pode ser reescrito pelo campo
abaixo dele, porque o seletor é onde a escolha é feita e a linha acima já teria anunciado o
resultado. O modo não é uma seleção — é fixo desde a abertura e não muda enquanto o sheet vive.
O head continua parado; o que ele nomeia é que passa a distinguir as duas operações.

Sem isso, um sheet intitulado "Pagamento de Fatura" com todos os campos preenchidos se lê como um
pagamento novo já sugerido, e confirmar pareceria criar um segundo.

O **subtítulo** não muda: "Escolha a fatura e a conta que paga por ela" continua verdadeiro na
correção, justamente porque D2 mantém a fatura e a conta escolhíveis. Uma string a menos, e a que
sobra não mente em nenhum dos dois modos.

O botão ganha chave própria — `invoice_payment_edit_confirm` = "Salvar" —, e não reaproveita a da
transferência: o projeto tem uma chave de "Salvar" por formulário, dez delas, e reusar uma de outra
feature acoplaria as duas por um recurso.

## Risks / Trade-offs

**A correção troca para uma fatura de outro cartão, em outra moeda** → O valor é limpo pela regra
que já existe (`invoice-settlement`: trocar o cartão ou a fatura limpa o valor e a contrapartida), e
`isCrossCurrency` é derivado das duas pontas correntes, não congelado na abertura. Nada novo é
preciso; o risco é apenas o de a implementação da correção contornar esse caminho ao pré-selecionar
(mitigado por D7).

**A fatura fecha enquanto o sheet de correção está aberto** → O ViewModel já lê a fatura
reativamente (*"another screen may close the cycle while this sheet is open"*), então ela sai do
conjunto oferecido e a submissão fica indisponível. É o comportamento que a criação já tem, e o
razão recusaria de qualquer forma. Não é caso novo.

**O ViewModel esquece de passar `excluding` e o teto volta a mentir** → É o bug atual, reintroduzido
em silêncio. Mitigado por teste sobre o valor exato do cenário de D3 (800 / 300 / corrigir para
700), no caso de uso e na habilitação da submissão, que são os dois lugares que aplicam o teto.

**Trocar de fatura numa correção move dinheiro entre dois sub-razões** → É deliberado: nenhum campo
é identidade da operação, e a transferência já permite trocar as duas contas pelo mesmo princípio. O
razão trata os dois lados (`ensureDimensionsAcceptRemoval` sobre a fatura antiga,
`ensureDimensionsAccept` sobre a nova), e D2 garante que ambas aceitam parcial.

**A quitação errada continua sem saída** → Reconhecido e deixado fora de escopo por decisão
explícita: fatura `PAID` é história liquidada. A change o declara como requisito em vez de o deixar
como silêncio, de modo que a próxima pessoa a encontrar o beco saiba que ele é uma regra e não um
esquecimento.

**A taxa colhida numa correção entre moedas** → Herdada sem regra própria, como em
`transfer-editing`: a correção colhe a taxa que ela aplica e não revoga a anterior. Mesma chave
(par, data, origem) significa substituição pelo próprio valor; uma taxa é observação sobre um dia e
não pertence à operação que a revelou.
