# transaction-scope Specification

## Purpose

O **escopo de leitura** da tela de transações: os três perímetros de contas (geral, apenas contas `ASSET`, apenas cartões `LIABILITY`), a gramática única do resumo em cada um deles — abertura → fluxos → fechamento —, a identidade aritmética que todo escopo deve satisfazer, a regra de que movimento interno ao perímetro não é fluxo, e o alcance posicional dos controles: escopo e período governam resumo e lista; os demais filtros governam apenas a lista. Consome as leituras do razão (`ledger-reporting`) sobre o plano de contas (`chart-of-accounts`); nenhuma linha do resumo é somada a partir da lista já carregada.
## Requirements
### Requirement: Escopo governa resumo e lista

A tela de transações SHALL oferecer um **escopo de leitura** com exatamente três valores — geral (contas e cartões), apenas contas (`ASSET`), apenas cartões (`LIABILITY`) — e esse escopo SHALL governar **simultaneamente** o resumo e a lista.

MUST NOT ocorrer de o resumo somar um perímetro e a lista exibir outro. Em particular, um resumo derivado apenas de contas `ASSET` MUST NOT ser exibido sobre uma lista que inclua movimentação de cartão.

O escopo SHALL ser um eixo único: a mesma decisão MUST NOT ser oferecida em dois controles distintos que possam assumir estados contraditórios.

O escopo **recorta** a lista pela presença de perna no perímetro; ele MUST NOT alterar como um item é apresentado — valor, cor, ícone ou título —, que continuam derivando da natureza do lançamento.

#### Scenario: Compra no cartão sob escopo de contas
- **WHEN** o escopo é "contas" e existe uma compra no cartão no mês
- **THEN** essa compra não aparece na lista nem em nenhuma linha do resumo

#### Scenario: Compra no cartão sob escopo de cartões
- **WHEN** o escopo é "cartões" e existe uma compra no cartão no mês
- **THEN** a compra aparece na lista e está contida no total de gastos do resumo

#### Scenario: Escopo geral resume o que lista
- **WHEN** o escopo é o geral
- **THEN** a lista exibe a movimentação de contas e de cartões, e o resumo deriva do mesmo perímetro

#### Scenario: Item não muda de aparência entre escopos
- **WHEN** o mesmo lançamento é exibido em dois escopos que o contenham
- **THEN** ele é apresentado com o mesmo valor, a mesma cor e o mesmo título nos dois

### Requirement: Gramática única de resumo por escopo

O resumo SHALL ter a mesma gramática nos três escopos — **abertura, fluxos, fechamento** —, variando apenas o perímetro de contas. Toda linha SHALL derivar do razão; MUST NOT ser somada a partir da lista já carregada.

Sendo `P` o perímetro do escopo, SHALL valer:

```
saldo(P, mês) = saldo(P, mês anterior) + Σ entries de P no mês
```

As linhas de fluxo exibidas SHALL **particionar** `Σ entries de P no mês`: toda entry do perímetro no período pertence a exatamente uma linha exibida, ou a um lançamento interno ao perímetro.

Os **fluxos** de um escopo SHALL correr todos em **um único sentido de sinal**, o do razão: o que aumenta o saldo do perímetro é positivo e o que o diminui é negativo, qualquer que seja a natureza. Num perímetro de `LIABILITY`, portanto, o gasto é negativo e o pagamento é positivo. MUST NOT ocorrer de os fluxos correrem em um sentido e o total no outro — é o que aconteceria ao exibir dívida positiva sobre fluxos de caixa, e a coluna deixaria de fechar aos olhos de quem a lê.

Uma linha de abertura ou de fechamento nomeada como **dívida** SHALL exibir *quanto se deve* — magnitude, sem sinal —, e SHALL exibir **zero** quando nada é devido. Um rótulo que pergunta "quanto devo" MUST NOT ser respondido com um número negativo. A magnitude é decisão de **apresentação**: o estado da tela SHALL continuar carregando o saldo no sinal do razão, para que a identidade aritmética permaneça verificável.

Uma linha exibida fora dessa soma SHALL ser identificável como informativa — sem sinal — e MUST NOT participar do fechamento.

#### Scenario: Escopo de contas fecha
- **WHEN** o resumo do escopo "contas" é exibido em um mês com entradas, saídas, pagamento de fatura e ajuste
- **THEN** o saldo final é igual ao saldo inicial somadas as entradas, subtraídas as saídas, subtraídos os pagamentos de fatura e aplicados os ajustes

#### Scenario: Escopo de cartões fecha, inclusive com ajuste de fatura
- **WHEN** o resumo do escopo "cartões" é exibido em um mês que contém ajuste de fatura
- **THEN** o saldo final é igual ao saldo inicial subtraídos os gastos, somados os pagamentos e aplicados os ajustes

#### Scenario: Os fluxos do cartão correm no mesmo sentido dos da conta
- **WHEN** o resumo do escopo "cartões" é exibido
- **THEN** o gasto aparece negativo e o pagamento positivo, como em qualquer extrato

#### Scenario: A dívida é exibida como magnitude
- **WHEN** o perímetro de cartões deve 120 no início do mês e 75 no fim
- **THEN** as linhas de dívida inicial e final exibem 120 e 75, sem sinal

#### Scenario: Cartão sem dívida
- **WHEN** o perímetro de cartões não deve nada — saldo zero ou a favor do usuário
- **THEN** a linha de dívida exibe zero, e não um valor negativo

#### Scenario: Escopo geral fecha
- **WHEN** o resumo do escopo geral é exibido
- **THEN** o líquido final é igual ao líquido inicial somadas as entradas, subtraídas as saídas — de conta e de cartão — e aplicados os ajustes de conta e de fatura

#### Scenario: Nenhuma soma em memória
- **WHEN** qualquer linha do resumo é produzida
- **THEN** ela deriva de um agregado do razão, e não da lista de transações carregada na tela

### Requirement: Movimento interno ao perímetro não é fluxo

Um lançamento cujas pernas estejam **todas dentro** do perímetro do escopo SHALL contribuir zero para o fechamento daquele escopo, por consequência de `Σ = 0` por lançamento. Ele MUST NOT ser reportado como entrada, saída ou ajuste.

Essa classificação SHALL depender exclusivamente do perímetro, e MUST NOT ser expressa como exceção por tipo de lançamento. Um lançamento com perna fora do perímetro SHALL, ao contrário, ser reportado como fluxo — é o caso do pagamento de fatura visto do escopo de contas, cuja perna de passivo está fora.

Um movimento interno SHALL poder ser **exibido** como linha informativa, sem sinal e fora da soma.

#### Scenario: Transferência não altera o saldo do escopo de contas
- **WHEN** existe uma transferência entre duas contas no mês e o escopo é "contas"
- **THEN** ela aparece na lista e não altera entradas, saídas nem o saldo final

#### Scenario: Pagamento de fatura é fluxo no escopo de contas
- **WHEN** existe um pagamento de fatura no mês e o escopo é "contas"
- **THEN** ele é reportado em linha própria e reduz o saldo final, porque a sua perna de passivo está fora do perímetro

#### Scenario: Pagamento de fatura abate a dívida no escopo de cartões
- **WHEN** existe um pagamento de fatura no mês e o escopo é "cartões"
- **THEN** ele é reportado como pagamento e leva o saldo do perímetro na direção do crédito, abatendo o que se deve, porque o dinheiro veio de fora do perímetro

#### Scenario: Pagamento de fatura é neutro no escopo geral
- **WHEN** existe um pagamento de fatura no mês e o escopo é o geral
- **THEN** o valor é exibido como linha informativa, sem sinal, e o líquido final é idêntico ao que seria sem esse pagamento

### Requirement: O mês do escopo de cartões é o da transação

No escopo de cartões, o período selecionado SHALL recortar por **data do lançamento**, e MUST NOT recortar por ciclo ou vencimento de fatura. O resumo SHALL responder "quanto foi lançado no cartão neste mês", coerente com a lista que ele governa.

#### Scenario: Compra que cai em fatura de outro mês
- **WHEN** uma compra é lançada em dezembro e cai na fatura que vence em janeiro, e o escopo é "cartões" com dezembro selecionado
- **THEN** a compra aparece na lista de dezembro e está contida nos gastos de dezembro

### Requirement: Alcance dos controles é posicional

A tela SHALL distinguir dois alcances de controle pela posição:

- O **escopo** e o **período** SHALL governar resumo e lista, e SHALL ser apresentados no topo do card de resumo.
- Os demais **filtros** SHALL governar apenas a lista, e SHALL ser apresentados abaixo do card.

Um filtro que governe apenas a lista MUST NOT alterar nenhuma linha do resumo.

Um filtro cuja decisão o escopo já tomou, ou que não tem o que recortar dentro do perímetro, MUST NOT ser oferecido nesse escopo — é o caso do que seleciona entre conta e cartão, e do de parcelamento, que só existe em cartão. Um filtro que não é oferecido MUST NOT continuar recortando a lista: um recorte invisível é indistinguível de uma lista incompleta.

#### Scenario: Filtro de categoria não move o resumo
- **WHEN** o usuário filtra a lista por uma categoria
- **THEN** a lista é recortada e todas as linhas do resumo permanecem as do mês completo daquele escopo

#### Scenario: Filtro redundante é suprimido
- **WHEN** o escopo selecionado é "contas" ou "cartões"
- **THEN** o filtro que seleciona entre conta e cartão não é oferecido, porque o escopo já o decidiu

#### Scenario: Filtro sem objeto é suprimido
- **WHEN** o escopo selecionado é "contas"
- **THEN** o filtro de parcelamento não é oferecido, porque parcelamento é arranjo de cartão

#### Scenario: Filtro suprimido para de recortar
- **WHEN** um filtro está ativo e o usuário troca para um escopo que não o oferece
- **THEN** a lista deixa de ser recortada por ele, em vez de continuar filtrada por um controle que sumiu

#### Scenario: Filtro de recorte no escopo geral
- **WHEN** o escopo é o geral e o usuário filtra a lista por cartão
- **THEN** a lista exibe apenas movimentação de cartão e o resumo continua reconciliando o perímetro geral

### Requirement: Escopo inicial preserva a lista

O escopo inicial da tela SHALL ser o geral, de modo que a lista exibida por padrão permaneça a mesma que a tela já exibia antes de existir o eixo de escopo.

#### Scenario: Abertura padrão
- **WHEN** a tela de transações é aberta
- **THEN** o escopo é o geral e a lista exibe movimentação de contas e de cartões
