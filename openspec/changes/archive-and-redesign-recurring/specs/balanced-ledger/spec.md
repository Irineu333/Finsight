## ADDED Requirements

### Requirement: Confirmar um ciclo de recorrência é uma única unidade de trabalho

Confirmar um ciclo de recorrência escreve duas coisas: a transação no razão e a ocorrência que registra que aquele ciclo foi tratado. As duas SHALL ser gravadas na **mesma transação de banco** — ou ambas persistem, ou nenhuma persiste.

Um ciclo de recorrência MUST NOT ser confirmado mais de uma vez. A escrita parcial é o que torna a dupla confirmação alcançável: com a transação gravada e a ocorrência não, o ciclo volta a ser apresentado como pendente, e a recusa de reentrada — que consulta a ocorrência — não encontra o registro que faltou escrever, de modo que uma segunda confirmação grava um **lançamento duplicado** no razão.

A escrita da fatura eventualmente necessária para o ciclo MAY permanecer fora dessa unidade de trabalho: uma fatura criada e não usada é dano menor que uma estrutura de fatura desfeita, e menor que um lançamento duplicado.

#### Scenario: Falha ao registrar a ocorrência não deixa o lançamento gravado
- **WHEN** a confirmação de um ciclo grava a transação mas falha ao registrar a ocorrência
- **THEN** a transação é desfeita junto com a ocorrência, o razão permanece sem o lançamento, e o ciclo continua pendente de forma consistente

#### Scenario: Confirmar o mesmo ciclo duas vezes não duplica o lançamento
- **WHEN** o usuário confirma um ciclo que já foi confirmado
- **THEN** a operação é recusada e nenhum segundo lançamento é gravado no razão
