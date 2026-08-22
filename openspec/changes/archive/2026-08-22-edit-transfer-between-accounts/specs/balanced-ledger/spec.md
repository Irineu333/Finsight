## MODIFIED Requirements

### Requirement: Editabilidade derivada, preservando os gates existentes
A editabilidade de uma transação SHALL ser derivada, nunca persistida, e SHALL ser expressa pelo **rótulo** da operação, e não por uma contagem de pernas que sirva a mais de um propósito ao mesmo tempo.

Uma transação MUST NOT ser editável se pertencer a uma fatura cujo status seja `CLOSED` ou `PAID`; MUST NOT ser editável se o seu rótulo for `ADJUSTMENT`; MUST NOT ser editável se pertencer a um parcelamento; e MUST NOT ser editável se qualquer uma das suas pernas postar em conta permanente arquivada. Esses quatro gates SHALL valer para toda operação, seja qual for o seu rótulo.

Passados os quatro, a editabilidade SHALL ser decidida pelo rótulo, nomeando o que **admite**:

- `EXPENSE` e `INCOME` SHALL ser editáveis quando possuírem exatamente uma perna em conta **monetária** (`ASSET`/`LIABILITY`). A contagem MUST NOT usar o total de entries, já que toda transação balanceada tem ao menos duas.
- `TRANSFER` SHALL ser editável, com as suas duas pernas monetárias. A transferência **entre moedas** SHALL ser reconhecida como `TRANSFER` e admitida pelo mesmo gate, sem regra própria: as suas pernas de **conversão** não alteram o rótulo, porque a conversão tem tipo de conta próprio e a derivação de rótulo já a atravessa.
- `PAYMENT` MUST NOT ser editável.

A recusa do pagamento de fatura SHALL ser uma **declaração própria**, e MUST NOT decorrer de uma contagem de pernas. Enquanto o gate era "exatamente uma perna monetária", o pagamento ficava de fora pelo mesmo efeito que excluía a transferência; admitida a transferência, essa contagem deixa de dizer qualquer coisa sobre o pagamento, e mantê-lo fora passa a exigir que se diga.

Um rótulo que venha a existir MUST NOT ser editável por omissão: o gate cita os rótulos que admite, nunca os que recusa, de modo que uma natureza nova nasce fora da edição e só entra por decisão explícita.

#### Scenario: Despesa é editável
- **WHEN** uma despesa em conta (`ASSET` + `EXPENSE`) sem parcelamento é exibida
- **THEN** ela é editável

#### Scenario: Compra no cartão é editável
- **WHEN** uma compra no cartão (`LIABILITY` + `EXPENSE`) sem parcelamento é exibida
- **THEN** ela é editável

#### Scenario: Ajuste de conta não é editável
- **WHEN** um ajuste de saldo de conta (`ASSET` + `EQUITY`) é exibido
- **THEN** ele não é editável, por seu rótulo ser `ADJUSTMENT`

#### Scenario: Ajuste de fatura não é editável
- **WHEN** um ajuste de saldo de fatura (`LIABILITY` + `EQUITY`) é exibido
- **THEN** ele não é editável, por seu rótulo ser `ADJUSTMENT`

#### Scenario: Lançamento de baixa não é editável
- **WHEN** o lançamento de baixa que a migração `v7 → v9` gerou para uma conta apagada no v7 é exibido
- **THEN** ele não é editável, pelo mesmo gate de rótulo, sem regra nova — arquivar não gera baixa em runtime (`account-lifecycle`), mas a migração gera, e o dado migrado obedece às mesmas regras que o novo

#### Scenario: Transferência é editável
- **WHEN** uma transferência (`ASSET` + `ASSET`) é exibida
- **THEN** ela é editável, por seu rótulo ser `TRANSFER`, e as suas duas pernas monetárias não a impedem

#### Scenario: Transferência entre moedas é editável, pelo mesmo gate
- **WHEN** uma transferência entre contas de moedas diferentes, com as suas pernas de conversão, é exibida
- **THEN** ela é editável pelo mesmo gate da transferência de moeda única, porque as pernas de conversão não alteram o seu rótulo

#### Scenario: Pagamento de fatura não é editável
- **WHEN** um pagamento de fatura (`ASSET` + `LIABILITY`) é exibido
- **THEN** ele não é editável, porque `PAYMENT` não está entre os rótulos admitidos — e não por contagem de pernas

#### Scenario: Parcelamento não é editável
- **WHEN** uma compra pertencente a um parcelamento é exibida
- **THEN** ela não é editável, por pertencer a um parcelamento

#### Scenario: Transferência com perna em conta arquivada não é editável
- **WHEN** uma transferência com uma das pernas em conta permanente arquivada é exibida
- **THEN** ela não é editável nem removível, pelo gate de conta arquivada, que precede a decisão por rótulo

## ADDED Requirements

### Requirement: A reescrita de uma transação aceita o mesmo vocabulário de pernas que a criação

A reescrita das pernas de uma transação SHALL aceitar um **conjunto** de pernas, com a mesma forma que a criação já aceita. A superfície de reescrita MUST NOT admitir menos do que a criação: uma assimetria em que apenas a criação pode expressar mais de uma perna torna inexprimível a correção de operações que a própria fronteira sabe escrever, e o limite passa a viver numa assinatura em vez de numa regra.

A fronteira de escrita SHALL aplicar à reescrita exatamente as mesmas invariantes que aplica à criação — `Σ = 0` por moeda, a regra de pouso de dimensão e a recusa de perna em conta arquivada —, e SHALL completar a intenção incompleta da mesma forma, inclusive posando o resíduo de cada moeda na conta de conversão daquela moeda.

Toda verificação que uma reescrita faça sobre o estado **anterior** da transação SHALL ser derivada das pernas que estão sendo escritas, e MUST NOT ser expressa como valor constante. Em particular, saber se uma escrita liquida um passivo SHALL ser derivado das naturezas das contas em que as pernas postam — a mesma derivação que a criação usa — de modo que o guarda que depende dessa resposta não possa ser consultado com um valor que ninguém calculou.

#### Scenario: Reescrita de duas pernas em moeda única
- **WHEN** uma operação de duas pernas monetárias de mesma moeda é reescrita
- **THEN** as pernas antigas são substituídas pelas novas, e a moeda soma zero

#### Scenario: Reescrita de duas pernas atravessando moedas
- **WHEN** uma operação entre contas de moedas diferentes é reescrita
- **THEN** ela é completada com uma perna de conversão por moeda, cada moeda soma zero, e o resíduo é recebido por diferença como na criação

#### Scenario: A reescrita e a atualização da linha são uma unidade
- **WHEN** uma reescrita falha depois de as pernas antigas terem sido removidas
- **THEN** nada é gravado, e a operação permanece com as pernas que tinha

#### Scenario: A liquidação de passivo é derivada, não declarada
- **WHEN** o guarda de dimensões é consultado durante uma reescrita
- **THEN** a informação de a escrita liquidar ou não um passivo vem das naturezas das contas das pernas, e não de um valor fixo
