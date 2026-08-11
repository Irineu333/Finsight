## ADDED Requirements

### Requirement: A fatura tem uma janela de compra, não um mês

Uma fatura SHALL ser descrita, para efeito de datas, por uma **janela de compra**: o intervalo
`[abertura, fechamento)` no dia de fechamento do cartão, do mês de abertura ao mês de fechamento.
A borda de abertura SHALL ser **inclusiva** e a de fechamento **exclusiva**.

A janela MUST NOT ser confundida com o mês de vencimento. O mês de vencimento é o que a interface
exibe no seletor de fatura e é posterior à janela; nenhuma data de compra deriva dele diretamente.

A derivação da janela a partir de um cartão e de um mês de vencimento SHALL ter **exatamente um
dono**, em `:core:model`. Nenhum outro módulo MUST reimplementar a regra
`dueDay < closingDay → closingMonth = dueMonth − 1`; os consumidores a invocam.

Quando a fatura já existe, a janela SHALL derivar dos meses **gravados** nela, não de uma
rederivação a partir do cartão. Quando a fatura ainda não existe — o caso de navegar para um mês
sem fatura criada — a janela SHALL ser derivada do cartão e do mês de vencimento alvo.

#### Scenario: Janela de uma fatura cujo vencimento sucede o fechamento
- **WHEN** um cartão fecha no dia 10 e vence no dia 20, e a fatura tem vencimento em março
- **THEN** a janela vai de 10/fevereiro (inclusive) a 10/março (exclusive)

#### Scenario: Janela de uma fatura cujo vencimento antecede o fechamento no mês
- **WHEN** um cartão fecha no dia 25 e vence no dia 5, e a fatura tem vencimento em março
- **THEN** o mês de fechamento é fevereiro e a janela vai de 25/janeiro (inclusive) a 25/fevereiro (exclusive)

#### Scenario: Fatura existente responde pelos meses que gravou
- **WHEN** uma fatura existente é consultada pela sua janela
- **THEN** a janela usa o `openingMonth` e o `closingMonth` persistidos na própria fatura

#### Scenario: Fatura ainda não criada tem janela derivada
- **WHEN** o mês de vencimento alvo não tem fatura correspondente
- **THEN** a janela é derivada do cartão e desse mês, e é idêntica à que a fatura teria se fosse criada

### Requirement: A projeção de uma data na janela preserva o dia

Dada uma janela e um dia do mês, a projeção SHALL devolver a data **dentro da janela** que cai
naquele dia. O dia SHALL ser preservado; o que muda é o mês.

A projeção SHALL ser **idempotente**: uma data já contida na janela SHALL ser devolvida
inalterada.

A projeção SHALL ser **total** — devolve sempre uma data dentro da janela, inclusive quando o
dia pedido não existe em nenhum dos dois meses candidatos por conta do fim de mês; nesse caso o
resultado SHALL ser recolhido para dentro das bordas.

#### Scenario: Dia posterior ao fechamento cai no mês de abertura
- **WHEN** a janela é 10/fevereiro–10/março e o dia pedido é 15
- **THEN** a projeção devolve 15/fevereiro

#### Scenario: Dia anterior ao fechamento cai no mês de fechamento
- **WHEN** a janela é 10/fevereiro–10/março e o dia pedido é 5
- **THEN** a projeção devolve 5/março

#### Scenario: O dia de fechamento pertence à abertura
- **WHEN** a janela é 10/fevereiro–10/março e o dia pedido é 10
- **THEN** a projeção devolve 10/fevereiro, a borda inclusiva

#### Scenario: Projetar uma data já dentro da janela não a move
- **WHEN** a data 05/março é projetada na janela 10/fevereiro–10/março
- **THEN** o resultado é 05/março

#### Scenario: Dia inexistente no mês candidato
- **WHEN** o cartão fecha no dia 31, a janela é 31/janeiro–28/fevereiro e o dia pedido é 30
- **THEN** a projeção devolve uma data dentro da janela, sem estourar a borda de abertura
