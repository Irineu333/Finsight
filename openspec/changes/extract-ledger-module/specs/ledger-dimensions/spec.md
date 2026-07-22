## ADDED Requirements

### Requirement: Dimensão como eixo analítico do razão
O razão SHALL prover um espaço de identidade próprio — a dimensão — pelo qual uma `Entry` pode ser classificada sem que a conta a que ela pertence mude. Uma dimensão SHALL ter uma identidade e um `kind`, e MUST NOT carregar nome, descrição, natureza ou qualquer atributo pertencente à fachada que a utiliza.

O domínio do razão MUST NOT atribuir significado a nenhum `kind` de dimensão: o razão soma entries agrupando por dimensão sem saber o que a dimensão representa. O `kind` existe para que a fronteira de escrita possa validar em qual perna cada dimensão pode pousar, e para que o schema permaneça legível.

#### Scenario: Dimensão não descreve a si mesma
- **WHEN** a tabela de dimensões é inspecionada
- **THEN** ela contém apenas identidade e `kind`, e nenhum atributo de fachada

#### Scenario: Razão agrega sem conhecer o significado
- **WHEN** o razão calcula um total agrupado por dimensão
- **THEN** o cálculo não consulta nenhuma tabela de fachada, e nenhum ramo do código depende de qual `kind` está sendo agregado

### Requirement: Fachada liga-se à dimensão por identidade
Uma fachada que classifica lançamentos SHALL guardar a identidade da sua dimensão, espelhando o vínculo `facade.accountId` já existente para as fachadas que projetam sobre o plano de contas. O razão MUST NOT guardar chave estrangeira para nenhuma tabela de fachada.

A identidade de dimensão SHALL ser emitida por um espaço único, de modo que dimensões originadas de fachadas distintas jamais colidam.

#### Scenario: Categoria e fatura ligam-se por dimensão
- **WHEN** uma categoria ou uma fatura é criada
- **THEN** uma dimensão é emitida e a fachada guarda a sua identidade

#### Scenario: Razão sem chave estrangeira para fachada
- **WHEN** o schema das tabelas do razão é inspecionado
- **THEN** nenhuma coluna referencia `categories`, `invoices`, `credit_cards`, `installments`, `recurrings` ou `budgets`

#### Scenario: Identidades de fachadas distintas não colidem
- **WHEN** uma categoria e uma fatura existem simultaneamente
- **THEN** suas dimensões têm identidades distintas, e uma soma por dimensão nunca mistura as duas

### Requirement: Uma dimensão por entry
Cada `Entry` SHALL carregar no máximo uma dimensão. A ausência de dimensão SHALL ser um estado legítimo e significar exatamente "não classificada" — MUST NOT existir dimensão de sistema, nem conta de sistema, cumprindo o papel de "sem classificação".

Quando a fachada que originou uma dimensão deixa de existir, as entries que a referenciavam SHALL passar ao estado não classificado, preservando o seu `amount` e a sua conta. Nenhuma remoção de fachada SHALL alterar o saldo de conta alguma.

#### Scenario: Perna não classificada
- **WHEN** o usuário registra uma despesa sem escolher categoria
- **THEN** a perna nominal é gravada sem dimensão, e nenhuma conta ou dimensão de sistema é criada para representá-la

#### Scenario: Remoção de fachada preserva o razão
- **WHEN** uma fachada com dimensão é removida
- **THEN** as entries que a referenciavam passam a não classificadas, seus `amount` permanecem inalterados e todo saldo de conta permanece idêntico

### Requirement: Regra de pouso validada na escrita
A fronteira de escrita SHALL validar que o `kind` da dimensão de cada perna é compatível com a natureza da conta daquela perna, e MUST NOT persistir uma transação que viole essa compatibilidade. A validação SHALL ocorrer no mesmo ponto único em que a invariante de soma zero é verificada.

A compatibilidade SHALL ser propriedade do `kind` — o conjunto de naturezas de conta em que ele pode pousar, declarado uma única vez, no razão. Ela MUST NOT ser declarada por dimensão, por escrita ou pela feature que a emite, e a validação MUST NOT ramificar por `kind`: um mesmo predicado uniforme decide o pouso de qualquer dimensão.

Essa validação existe porque uma dimensão pousada na perna errada não produz erro observável: as somas agrupadas por dimensão simplesmente ficam incorretas, em silêncio.

#### Scenario: Dimensão na perna errada é rejeitada
- **WHEN** uma escrita tenta pousar uma dimensão de fatura numa perna de conta nominal
- **THEN** a persistência falha com erro tipado e nada é gravado

#### Scenario: Dimensões corretas coexistem na mesma transação
- **WHEN** uma compra no cartão é registrada com fatura e categoria
- **THEN** a perna `LIABILITY` carrega a dimensão da fatura, a perna nominal carrega a dimensão da categoria, e a transação é persistida

#### Scenario: A regra acompanha o kind, não a linha
- **WHEN** duas dimensões do mesmo `kind` existem
- **THEN** ambas aceitam exatamente as mesmas naturezas de conta, e nenhum caminho de escrita pode conceder a uma delas um pouso que a outra não teria
