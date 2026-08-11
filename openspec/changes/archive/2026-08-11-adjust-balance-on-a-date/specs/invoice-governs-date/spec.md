## MODIFIED Requirements

### Requirement: O escopo da hierarquia é o lançamento novo

A recolocação SHALL valer nos formulários de **criação** — adicionar transação, adicionar
parcelamento e **ajustar fatura**. Ela MUST NOT valer no formulário de **edição** de transação.

A distinção não é de escopo de entrega: na criação a data é um valor padrão do sistema, e
sugerir sobre ele é legítimo; na edição a data é dado que o usuário já escreveu, e sobrescrevê-la
contradiria a regra de que a palavra final é dele. O ajuste de fatura é criação sob esse critério:
a data com que ele abre é uma sugestão do sistema, e trocar a fatura é o gesto que a redefine.

O **teto** da projeção, porém, SHALL depender da natureza da perna que o formulário grava, e não
do formulário. Um lançamento de compra não pode se liquidar num ciclo que fechou antes de ele
acontecer, e por isso tem o fechamento da fatura como limite superior além de hoje. Um **ajuste**
não ocorre dentro do ciclo — ele ocorre sobre o ciclo —, e por isso tem **apenas hoje** como
limite: a janela da fatura MUST NOT limitá-lo em nenhuma das duas direções.

O aviso de divergência SHALL valer em todos esses formulários, inclusive no de edição e no de
ajuste, com o mesmo dono no domínio e sem corrigir coisa alguma.

#### Scenario: Editar uma transação e trocar a fatura
- **WHEN** o usuário edita uma transação existente e troca a fatura selecionada
- **THEN** a data da transação permanece exatamente como estava

#### Scenario: Parcelamento segue a mesma hierarquia da transação
- **WHEN** o usuário troca a fatura no modal de adicionar parcelamento
- **THEN** a data é recolocada pela mesma regra do modal de adicionar transação

#### Scenario: Ajuste de fatura segue a hierarquia na troca de fatura
- **WHEN** o usuário troca a fatura no modal de ajuste de fatura
- **THEN** a data é recolocada na janela da nova fatura, preservando o dia e travando em hoje

#### Scenario: O ajuste não tem o fechamento como teto
- **WHEN** o usuário ajusta uma fatura já fechada e mantém a data de hoje, posterior ao fechamento dela
- **THEN** a data é aceita como está, o ajuste é gravado, e o formulário apenas sinaliza a divergência

#### Scenario: A compra continua limitada pelo fechamento
- **WHEN** o usuário lança uma compra numa fatura selecionada
- **THEN** o limite superior da data permanece o menor entre hoje e o fechamento daquela fatura
