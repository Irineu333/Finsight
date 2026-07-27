## MODIFIED Requirements

### Requirement: Gramática única de resumo por escopo

O resumo SHALL ter a mesma gramática nos três escopos — **abertura, fluxos, fechamento** —, variando apenas o perímetro de contas. Toda linha SHALL derivar do razão; MUST NOT ser somada a partir da lista já carregada.

Sendo `P` o perímetro do escopo, SHALL valer:

```
saldo(P, mês) = saldo(P, mês anterior) + Σ entries de P no mês
```

As linhas de fluxo exibidas SHALL **particionar** `Σ entries de P no mês`: toda entry do perímetro no período pertence a exatamente uma linha exibida, ou a um lançamento interno ao perímetro.

Segregar um fluxo em linha própria SHALL ser uma **reparticão**, nunca um acréscimo: a linha nova SHALL retirar da linha de origem exatamente o que passa a exibir, de modo que a soma das linhas permaneça igual a `Σ entries de P no mês`. Uma linha que exibisse valores já contidos em outra faria a coluna deixar de fechar aos olhos de quem a lê, que é a propriedade que esta gramática existe para garantir.

O **rendimento** SHALL ser uma dessas linhas nos escopos cujo perímetro contém contas `ASSET`, e SHALL sair da linha de entradas. Ele MUST NOT aparecer no escopo de `LIABILITY`, onde não há rendimento a segregar.

A linha SHALL ser exibida sempre que o período contiver rendimento, e MUST NOT depender de conta alguma declarar que rende. Uma vez que a linha de entradas **deixou de conter** o rendimento, ocultá-la faria a coluna deixar de fechar — e a declaração é retirável a qualquer momento, sem que isso altere lançamento algum. Num período **sem** rendimento a linha MUST NOT ser exibida: o resumo não oferece caminho de lançamento, então uma linha zerada aqui não diria nada.

Os **fluxos** de um escopo SHALL correr todos em **um único sentido de sinal**, o do razão: o que aumenta o saldo do perímetro é positivo e o que o diminui é negativo, qualquer que seja a natureza. Num perímetro de `LIABILITY`, portanto, o gasto é negativo e o pagamento é positivo. MUST NOT ocorrer de os fluxos correrem em um sentido e o total no outro — é o que aconteceria ao exibir dívida positiva sobre fluxos de caixa, e a coluna deixaria de fechar aos olhos de quem a lê.

Uma linha de abertura ou de fechamento nomeada como **dívida** SHALL exibir *quanto se deve* — magnitude, sem sinal —, e SHALL exibir **zero** quando nada é devido. Um rótulo que pergunta "quanto devo" MUST NOT ser respondido com um número negativo. A magnitude é decisão de **apresentação**: o estado da tela SHALL continuar carregando o saldo no sinal do razão, para que a identidade aritmética permaneça verificável.

Uma linha exibida fora dessa soma SHALL ser identificável como informativa — sem sinal — e MUST NOT participar do fechamento.

#### Scenario: Escopo de contas fecha
- **WHEN** o resumo do escopo "contas" é exibido em um mês com entradas, saídas, pagamento de fatura e ajuste
- **THEN** o saldo final é igual ao saldo inicial somadas as entradas, subtraídas as saídas, subtraídos os pagamentos de fatura e aplicados os ajustes

#### Scenario: Escopo de contas fecha com rendimento segregado
- **WHEN** o resumo do escopo "contas" é exibido em um mês que contém rendimento
- **THEN** o saldo final é igual ao saldo inicial somadas as entradas **e** os rendimentos, subtraídas as saídas, subtraídos os pagamentos de fatura e aplicados os ajustes

#### Scenario: A linha de rendimento sai da linha de entradas
- **WHEN** um rendimento de 12,40 e um salário de 5.000,00 são registrados no mesmo mês na mesma conta
- **THEN** a linha de entradas exibe 5.000,00 e a de rendimento exibe 12,40, e a soma das duas é igual ao total que a linha de entradas exibiria sem a segregação

#### Scenario: Mês sem rendimento não exibe a linha
- **WHEN** o resumo é exibido para um perímetro em um mês sem nenhum rendimento lançado
- **THEN** a linha de rendimento não é exibida, e a linha de entradas exibe o total que sempre exibiu

#### Scenario: Rendimento lançado permanece exibido após a declaração ser retirada
- **WHEN** o resumo é exibido para um mês que contém rendimento e nenhuma conta do perímetro declara mais render
- **THEN** a linha de rendimento continua exibida com o valor do período, e a coluna continua fechando

#### Scenario: Escopo de cartões não segrega rendimento
- **WHEN** o resumo do escopo "cartões" é exibido
- **THEN** nenhuma linha de rendimento é exibida, por o perímetro não conter conta `ASSET`

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
- **THEN** o líquido final é igual ao líquido inicial somadas as entradas e os rendimentos, subtraídas as saídas — de conta e de cartão — e aplicados os ajustes de conta e de fatura

#### Scenario: Nenhuma soma em memória
- **WHEN** qualquer linha do resumo é produzida
- **THEN** ela deriva de um agregado do razão, e não da lista de transações carregada na tela
