## ADDED Requirements

### Requirement: Todo widget de saldo nomeia o seu perímetro

Um widget do dashboard que exiba saldo ou fluxo de dinheiro SHALL declarar no seu rótulo **qual perímetro do plano de contas** ele soma. Um rótulo neutro — que não qualifique o perímetro — MUST NOT ser usado por um widget que some apenas uma natureza de conta.

Em particular, um widget que some apenas contas `ASSET` MUST NOT ser apresentado como saldo ou balanço **total**, **geral** ou equivalente, uma vez que o plano de contas registra também os passivos do usuário.

Esta regra rege o **rótulo**, não a leitura: corrigir um nome enviesado MUST NOT alterar o valor que o widget já exibia.

#### Scenario: Saldo de contas não se diz total
- **WHEN** um widget exibe o saldo acumulado apenas das contas `ASSET`
- **THEN** o rótulo qualifica o perímetro como sendo o das contas, e não o do dinheiro do usuário como um todo

#### Scenario: Correção de rótulo não move valor
- **WHEN** o rótulo de um widget é corrigido para nomear o seu perímetro
- **THEN** o valor exibido permanece idêntico ao anterior

#### Scenario: Fluxo de contas não se diz balanço sem qualificação
- **WHEN** um widget exibe receitas e despesas apenas das contas `ASSET`
- **THEN** ele é distinguível, na tela, de um widget que exiba o mesmo par sobre contas e cartões

### Requirement: Os três perímetros existem como widgets independentes

O dashboard SHALL oferecer o fluxo do mês em três perímetros — apenas contas (`ASSET`), apenas cartões (`LIABILITY`) e o neutro (`ASSET` + `LIABILITY`) —, e os três SHALL poder estar presentes na tela **simultaneamente**.

O perímetro SHALL ser a identidade do widget, e MUST NOT ser uma configuração de um widget único, enquanto a identidade de um widget for o seu tipo — sob essa identidade, um widget configurável ofereceria um perímetro por vez e tornaria a coexistência impossível.

O widget neutro SHALL nascer **ausente** dos dashboards já existentes e ser adicionado pelo modo de edição, como qualquer outro widget. Nenhuma preferência salva SHALL ser reescrita.

#### Scenario: Contas, cartões e geral na mesma tela
- **WHEN** o usuário adiciona os três widgets de fluxo
- **THEN** os três aparecem simultaneamente, cada um somando o seu perímetro

#### Scenario: Dashboard existente não muda
- **WHEN** um dashboard montado antes desta capacidade é carregado
- **THEN** ele exibe exatamente os mesmos widgets, na mesma ordem e com a mesma aparência, sem o widget neutro

### Requirement: O perímetro neutro é a soma das naturezas disjuntas

O fluxo do perímetro neutro SHALL ser derivado **somando** as leituras de fluxo do razão por natureza, e MUST NOT depender de um agregado dedicado a esse perímetro.

A despesa do perímetro neutro SHALL ser a soma da despesa de `ASSET` e da despesa de `LIABILITY`. A soma MUST NOT dupla-contar: os dois conjuntos são disjuntos, porque um lançamento de cartão não tem perna `ASSET`.

O pagamento de fatura MUST NOT ser reportado como despesa em nenhuma das duas parcelas, por ser movimento interno ao perímetro neutro — as suas duas pernas estão dentro dele.

A receita do perímetro neutro SHALL ser idêntica à do perímetro de contas, uma vez que o razão não registra receita em perna de `LIABILITY`.

#### Scenario: Compra no cartão entra uma vez só
- **WHEN** existe uma compra de cartão no mês e o fluxo do perímetro neutro é exibido
- **THEN** ela é contada exatamente uma vez na despesa

#### Scenario: Pagamento de fatura não é despesa do perímetro neutro
- **WHEN** existe um pagamento de fatura no mês e o fluxo do perímetro neutro é exibido
- **THEN** o valor não aparece na despesa, nem pela parcela de contas nem pela de cartões

#### Scenario: A despesa neutra é a soma das duas exibidas
- **WHEN** os três widgets de fluxo estão presentes no mesmo mês
- **THEN** a despesa do widget neutro é igual à despesa do widget de contas somada ao gasto do widget de cartões

#### Scenario: Sem agregado dedicado
- **WHEN** a origem do fluxo neutro é inspecionada
- **THEN** ele deriva da soma das leituras por natureza que o razão já expõe, e não de uma consulta criada para esse perímetro

### Requirement: Widget de fluxo reporta fluxo, não fechamento

Um widget de fluxo do dashboard SHALL exibir **apenas** as classes de fluxo do seu perímetro, e MUST NOT afirmar a identidade `abertura + fluxos = fechamento`: ele não exibe abertura nem fechamento, logo não deve essa identidade.

O conjunto de classes reportadas SHALL ser o **mesmo entre os três perímetros**. Em particular, o **ajuste** — a contrapartida em `EQUITY` — SHALL ficar fora dos três, ou entrar nos três; MUST NOT ser reportado em um perímetro e omitido em outro.

Enquanto o ajuste ficar fora, os valores do dashboard PODEM divergir dos de um resumo que feche a conta sobre o mesmo perímetro e o mesmo mês. Essa divergência SHALL ser consequência declarada da escolha de classes, e MUST NOT ser tratada como caso especial em uma das leituras.

#### Scenario: O widget não exibe saldo de abertura nem de fechamento
- **WHEN** um widget de fluxo é exibido
- **THEN** ele mostra apenas as classes de fluxo do perímetro, sem linha de abertura ou de fechamento

#### Scenario: Ajuste tratado igual nos três perímetros
- **WHEN** existe ajuste de saldo de conta e ajuste de saldo de fatura no mesmo mês
- **THEN** nenhum dos três widgets o reporta, e a aritmética entre eles permanece verificável
