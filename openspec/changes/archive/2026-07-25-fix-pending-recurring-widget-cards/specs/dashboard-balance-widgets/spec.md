## ADDED Requirements

### Requirement: Widget de fluxo presente exibe o par completo de classes

Um widget de fluxo do dashboard que esteja presente na tela SHALL exibir **todas** as classes do seu perímetro, e uma classe cujo total seja zero SHALL ser exibida **como zero**, não omitida.

O conjunto de classes exibidas é determinado pela **identidade** do widget, e MUST NOT variar com os dados do mês: o mesmo widget, em meses diferentes, apresenta a mesma forma. Ausência de valor é uma leitura — R$ 0,00 —, e omitir o cartão a converte na afirmação mais forte de que aquela classe não se aplica ao perímetro, que é falsa.

A única decisão binária de visibilidade que um widget de fluxo SHALL admitir é sobre **o widget inteiro**, governada pela sua configuração de ocultar-quando-vazio. Widget presente ⇒ par completo; widget oculto ⇒ nada. MUST NOT existir estado intermediário em que parte das classes desaparece.

Esta regra é a forma interna da que exige o mesmo conjunto de classes entre os três perímetros: aquela compara widgets lado a lado, esta compara o mesmo widget ao longo do tempo.

#### Scenario: Só há receita prevista no mês
- **WHEN** o widget de recorrentes previstas está presente e só há recorrentes de receita pendentes no mês
- **THEN** os dois cartões são exibidos, com o valor previsto na receita e R$ 0,00 na despesa

#### Scenario: Só há despesa prevista no mês
- **WHEN** o widget de recorrentes previstas está presente e só há recorrentes de despesa pendentes no mês
- **THEN** os dois cartões são exibidos, com R$ 0,00 na receita e o valor previsto na despesa

#### Scenario: A forma do widget não muda com o mês
- **WHEN** o usuário navega entre um mês com as duas classes e um mês com apenas uma
- **THEN** o widget ocupa a mesma largura e exibe a mesma quantidade de cartões nos dois meses

#### Scenario: A ocultação continua sendo do widget inteiro
- **WHEN** o widget está configurado para ocultar-se quando vazio e nenhuma classe tem valor no mês
- **THEN** o widget inteiro desaparece, e nunca apenas um dos seus cartões

#### Scenario: Correção de forma não move valor
- **WHEN** um widget deixa de omitir a classe zerada
- **THEN** os valores exibidos nas classes restantes permanecem idênticos aos anteriores
