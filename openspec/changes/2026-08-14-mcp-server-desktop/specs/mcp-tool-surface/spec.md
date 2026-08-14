## ADDED Requirements

### Requirement: Toda tool é um caso de uso do domínio, e nenhuma tool é uma regra

Cada tool SHALL delegar a um caso de uso ou repositório já existente no domínio. Uma tool
MUST NOT decidir *qual* regra se aplica, MUST NOT compor um resultado que o domínio saberia
compor, e MUST NOT alcançar o razão diretamente quando existe caso de uso que governa aquela
escrita.

Uma tool pode legitimamente decidir **se** oferece uma operação — como qualquer consumidor —,
nunca reimplementá-la.

#### Scenario: Verbo agregador não decide regra
- **WHEN** um agente registra uma despesa em cartão de crédito
- **THEN** em qual fatura ela cai é resolvido pelo caso de uso que já é dono dessa regra, e
  não por lógica da camada de tools

#### Scenario: Escrita sempre passa pelo caso de uso
- **WHEN** qualquer tool de escrita é inspecionada
- **THEN** ela chama o caso de uso correspondente, e não a fronteira de escrita do razão

### Requirement: A superfície é deliberada e derivada do que o usuário consegue fazer

A superfície de tools SHALL cobrir o que o usuário consegue fazer nas telas do app, excluindo
o que depende de julgamento visual. Ela MUST NOT ser gerada automaticamente a partir de todos
os casos de uso existentes.

Cada caso de uso alcançado por uma tool SHALL ser promovido de `impl` para `api` da sua
feature, aplicando o critério de triagem já vigente — só entra na `api` o que outro módulo
consome.

#### Scenario: Promoção segue o critério existente
- **WHEN** uma tool precisa de um caso de uso que hoje vive no `impl`
- **THEN** o caso de uso é promovido para a `api` da feature, e nenhum módulo passa a
  depender de `impl` para alcançá-lo

#### Scenario: Caso de uso não alcançado permanece interno
- **WHEN** um caso de uso não é consumido por nenhuma tool nem por outra feature
- **THEN** ele permanece no `impl`

### Requirement: Dinheiro atravessa a fronteira por moeda

Uma tool cuja leitura pode cruzar contas SHALL responder o valor por moeda, como o razão o
produz. Somente uma leitura escopada a uma única conta SHALL responder um valor escalar.

Quando o valor consolidado na moeda base for oferecido, ele SHALL vir acompanhado da taxa e
da data da taxa que o produziram. Um valor consolidado sem a taxa é irreproduzível, e o
consumidor fará aritmética sobre ele.

#### Scenario: Saldo que cruza contas
- **WHEN** uma tool devolve um saldo agregado sobre mais de uma conta
- **THEN** a resposta traz um valor por moeda, e não um único número

#### Scenario: Consolidado carrega a taxa
- **WHEN** uma resposta inclui o valor consolidado na moeda base
- **THEN** ela inclui a taxa aplicada e a data dessa taxa

#### Scenario: Conta única continua escalar
- **WHEN** a leitura é escopada a uma conta, que declara uma moeda só
- **THEN** a resposta é um valor escalar naquela moeda

### Requirement: O erro devolvido é o do domínio, em inglês, e a recusa é resposta

Quando o domínio recusa uma operação, a tool SHALL responder o erro nomeado com a mensagem em
inglês destinada a log. Ela MUST NOT devolver o texto internacionalizado destinado à tela.

Uma recusa de regra de negócio SHALL ser uma resposta bem-sucedida do transporte descrevendo a
recusa, e não uma falha de transporte — o consumidor precisa distinguir "a regra proíbe" de "a
chamada quebrou".

#### Scenario: Recusa de regra
- **WHEN** uma tool tenta lançar em fatura fechada
- **THEN** a resposta nomeia o erro do domínio com a sua mensagem em inglês, e nada é gravado

#### Scenario: Nenhum texto de tela vaza
- **WHEN** qualquer resposta de erro é inspecionada
- **THEN** ela não contém texto traduzido de interface

#### Scenario: Recusa distinguível de falha
- **WHEN** o consumidor recebe uma recusa de regra e, noutra chamada, uma falha inesperada
- **THEN** as duas são distinguíveis sem interpretar texto livre
