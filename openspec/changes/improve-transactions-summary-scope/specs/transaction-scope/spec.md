## ADDED Requirements

### Requirement: Escopo governa resumo e lista

A tela de transações SHALL oferecer um **escopo de leitura** com exatamente três valores — todas as contas e cartões, apenas contas (`ASSET`), apenas cartões (`LIABILITY`) — e esse escopo SHALL governar **simultaneamente** o resumo e a lista.

MUST NOT ocorrer de o resumo somar um perímetro e a lista exibir outro. Em particular, um resumo derivado apenas de contas `ASSET` MUST NOT ser exibido sobre uma lista que inclua movimentação de cartão.

O escopo SHALL ser um eixo único: a mesma decisão MUST NOT ser oferecida em dois controles distintos que possam assumir estados contraditórios.

#### Scenario: Compra no cartão sob escopo de contas
- **WHEN** o escopo é "contas" e existe uma compra no cartão no mês
- **THEN** essa compra não aparece na lista nem em nenhuma linha do resumo

#### Scenario: Compra no cartão sob escopo de cartões
- **WHEN** o escopo é "cartões" e existe uma compra no cartão no mês
- **THEN** a compra aparece na lista e está contida no total de gastos do resumo

#### Scenario: Escopo geral resume o que lista
- **WHEN** o escopo é o geral
- **THEN** a lista exibe a movimentação de contas e de cartões, e o resumo deriva do mesmo perímetro

### Requirement: Gramática única de resumo por escopo

O resumo SHALL ter a mesma gramática nos três escopos — **abertura, fluxos, fechamento** —, variando apenas o perímetro. Toda linha SHALL derivar do razão; MUST NOT ser somada a partir da lista já carregada.

Para todo escopo SHALL valer a identidade:

```
fechamento = abertura + entradas − saídas + ajustes
```

Uma linha exibida fora dessa soma SHALL ser identificável como informativa — sem sinal aritmético — e MUST NOT participar do fechamento.

#### Scenario: Escopo de contas fecha
- **WHEN** o resumo do escopo "contas" é exibido
- **THEN** o saldo final é igual ao saldo inicial somado às entradas, subtraídas as saídas e somados os ajustes do mês

#### Scenario: Escopo de cartões fecha, inclusive com ajuste de fatura
- **WHEN** o resumo do escopo "cartões" é exibido em um mês que contém ajuste de fatura
- **THEN** a dívida final é igual à dívida inicial somados os gastos, subtraídos os pagamentos e aplicados os ajustes

#### Scenario: Escopo geral fecha
- **WHEN** o resumo do escopo geral é exibido
- **THEN** o líquido final é igual ao líquido inicial somadas as entradas, subtraídas as saídas — de conta e de cartão — e somados os ajustes

#### Scenario: Nenhuma soma em memória
- **WHEN** qualquer linha do resumo é produzida
- **THEN** ela deriva de um agregado do razão, e não da lista de transações carregada na tela

### Requirement: Movimento interno ao perímetro não é fluxo

Um lançamento cujas pernas estejam **todas dentro** do perímetro do escopo SHALL ser tratado como movimento interno: ele MUST NOT contribuir para entradas, saídas ou ajustes daquele escopo, porque não altera o seu fechamento.

Essa classificação SHALL depender exclusivamente do perímetro, e MUST NOT ser expressa como exceção por tipo de lançamento:

| escopo | movimento interno |
|---|---|
| contas | transferência entre contas |
| cartões | transferência entre cartões |
| geral | transferência entre contas **e** pagamento de fatura |

Um movimento interno ao escopo geral SHALL, ainda assim, poder ser **exibido** como linha informativa, sem sinal e fora da soma.

#### Scenario: Transferência não altera o saldo do escopo de contas
- **WHEN** existe uma transferência entre duas contas no mês e o escopo é "contas"
- **THEN** ela aparece na lista e não altera entradas, saídas nem o saldo final

#### Scenario: Pagamento de fatura abate a dívida no escopo de cartões
- **WHEN** existe um pagamento de fatura no mês e o escopo é "cartões"
- **THEN** ele é reportado como pagamento e reduz a dívida final, porque o dinheiro veio de fora do perímetro

#### Scenario: Pagamento de fatura é neutro no escopo geral
- **WHEN** existe um pagamento de fatura no mês e o escopo é o geral
- **THEN** o valor é exibido como linha informativa, sem sinal, e o líquido final é idêntico ao que seria sem esse pagamento

### Requirement: A perna exibida segue o escopo

Quando o escopo determinar um perímetro de uma única natureza de conta, o item de lista SHALL ser apresentado pela perna daquela natureza, de modo que o sinal exibido concorde com o efeito sobre o total do próprio escopo.

MUST NOT ocorrer de um item exibir o sinal de uma perna que não pertence ao perímetro que o resumo está somando.

#### Scenario: Pagamento de fatura visto de dois livros
- **WHEN** o mesmo pagamento de fatura é exibido no escopo "contas" e no escopo "cartões"
- **THEN** no primeiro ele lê como saída de dinheiro e no segundo como redução da dívida, e cada um concorda com o total do seu escopo

#### Scenario: Transação sem perna do perímetro
- **WHEN** uma transação não possui perna da natureza do escopo selecionado
- **THEN** ela é omitida da lista, sem falha de leitura

### Requirement: Alcance dos controles é posicional

A tela SHALL distinguir dois alcances de controle pela posição:

- O **escopo** e o **período** SHALL governar resumo e lista, e SHALL ser apresentados no topo do card de resumo.
- Os demais **filtros** SHALL governar apenas a lista, e SHALL ser apresentados abaixo do card.

Um filtro que governe apenas a lista MUST NOT alterar nenhuma linha do resumo. Quando um filtro se tornar redundante com o escopo selecionado — por decidir a mesma coisa que ele —, esse filtro MUST NOT ser oferecido naquele escopo.

#### Scenario: Filtro de categoria não move o resumo
- **WHEN** o usuário filtra a lista por uma categoria
- **THEN** a lista é recortada e todas as linhas do resumo permanecem as do mês completo daquele escopo

#### Scenario: Filtro redundante é suprimido
- **WHEN** o escopo selecionado é "contas" ou "cartões"
- **THEN** o filtro que seleciona entre conta e cartão não é oferecido, porque o escopo já o decidiu

#### Scenario: Filtro de recorte no escopo geral
- **WHEN** o escopo é o geral e o usuário filtra a lista por cartão
- **THEN** a lista exibe apenas movimentação de cartão e o resumo continua reconciliando o perímetro geral

### Requirement: Escopo inicial preserva a lista

O escopo inicial da tela SHALL ser o geral, de modo que a lista exibida por padrão permaneça a mesma que a tela já exibia antes de existir o eixo de escopo.

Uma navegação externa SHALL poder abrir a tela em um escopo determinado, e esse escopo SHALL concordar com o recorte da origem da navegação.

#### Scenario: Abertura padrão
- **WHEN** a tela de transações é aberta sem escopo determinado
- **THEN** o escopo é o geral e a lista exibe movimentação de contas e de cartões

#### Scenario: Navegação a partir de um total de contas
- **WHEN** o usuário aciona um total de saldo em conta em outra tela
- **THEN** a tela de transações abre no escopo "contas", e o seu resumo concorda com o total de origem
