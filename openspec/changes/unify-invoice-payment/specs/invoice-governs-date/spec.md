## MODIFIED Requirements

### Requirement: A fatura governa a data; a data não governa a fatura

Todo formulário que escolhe uma fatura SHALL obedecer a uma hierarquia estrita de preenchimento:
**cartão governa fatura, fatura governa data**. São eles os de lançamento em cartão e o de
**pagamento de fatura**. Um campo SHALL alterar apenas o que está abaixo dele.

Selecionar uma fatura SHALL recolocar a data pela projeção do dia corrente do formulário na
janela dessa fatura. Trocar o cartão SHALL ter o mesmo efeito, porque o cartão redefine a janela
mesmo sob o mesmo mês de vencimento.

**Qual** janela recoloca a data SHALL ser resolvida pela natureza do que o formulário grava: a
janela de compra (`invoice-purchase-window`) quando o formulário lança na fatura, e a janela de
**liquidação** — derivada do estado da fatura — quando o formulário a paga. A hierarquia é a
mesma; a janela é do formulário.

Editar a data MUST NOT alterar a fatura selecionada, nem diretamente nem por efeito colateral.

Se uma data fora da janela é preservada ou recusada SHALL depender do que o **domínio** aceita, e
MUST NOT ser decidido pelo formulário. Onde ele aceita a divergência — um lançamento, que pertence
à fatura por escolha do usuário e não pela sua data —, a projeção é uma **sugestão**: a palavra
final é do usuário, e uma data que ele escreveu fora da janela SHALL ser preservada exatamente
como escrita. Onde ele a recusa — um pagamento, que só pode ocorrer dentro da janela em que a
liquidação se dá —, a janela é **limite**: o formulário MUST NOT oferecer nem aceitar data que a
operação recusaria, e não há divergência a sinalizar porque não há divergência possível.

A recolocação MUST NOT depender do valor corrente do campo de data como gatilho — apenas do
cartão e da fatura —, de modo que a assimetria seja estrutural e não uma disciplina de quem
escreve o código.

#### Scenario: Navegar para a fatura anterior recoloca a data
- **WHEN** hoje é 12/março, o cartão fecha no dia 10, e o usuário navega da fatura de abril para a de março
- **THEN** a data passa de 12/março para 12/fevereiro

#### Scenario: Navegar para uma fatura retroativa recoloca a data
- **WHEN** hoje é 12/março, o cartão fecha no dia 10, e o usuário navega até a fatura de janeiro
- **THEN** a data passa a 12/dezembro, dentro da janela dessa fatura

#### Scenario: Trocar de cartão recoloca a data
- **WHEN** o mês de vencimento selecionado permanece o mesmo mas o usuário troca para um cartão com outro dia de fechamento
- **THEN** a data é reprojetada na janela do novo cartão

#### Scenario: Editar a data não move a fatura
- **WHEN** o usuário edita a data para um dia fora da janela da fatura selecionada
- **THEN** a fatura selecionada permanece a mesma e a data permanece como o usuário a escreveu

#### Scenario: A data escrita pelo usuário é o dia preservado na próxima projeção
- **WHEN** o usuário escreve o dia 03 e em seguida navega para outra fatura
- **THEN** a nova data mantém o dia 03 e muda apenas o mês

#### Scenario: Trocar a fatura no pagamento recoloca a data na janela de liquidação
- **WHEN** o usuário troca, dentro do pagamento, de uma fatura `OPEN` para uma `CLOSED` do mesmo cartão
- **THEN** a data é recolocada na janela de liquidação da fatura agora selecionada, e não na janela de compra dela

#### Scenario: No pagamento a janela é limite, não sugestão
- **WHEN** o usuário tenta datar um pagamento fora da janela de liquidação da fatura selecionada
- **THEN** o formulário não oferece essa data e não a aceita, sem sinalizar divergência alguma

### Requirement: O escopo da hierarquia é o lançamento novo

A recolocação SHALL valer nos formulários de **criação** — adicionar transação, adicionar
parcelamento, **ajustar fatura** e **pagar fatura**. Ela MUST NOT valer no formulário de
**edição** de transação.

A distinção não é de escopo de entrega: na criação a data é um valor padrão do sistema, e
sugerir sobre ele é legítimo; na edição a data é dado que o usuário já escreveu, e sobrescrevê-la
contradiria a regra de que a palavra final é dele. O ajuste de fatura é criação sob esse critério:
a data com que ele abre é uma sugestão do sistema, e trocar a fatura é o gesto que a redefine. O
pagamento também: a fatura que ele paga é escolhida ali, e a data acompanha a escolha.

O **teto** da projeção, porém, SHALL depender da natureza da perna que o formulário grava, e não
do formulário. Um lançamento de compra não pode se liquidar num ciclo que fechou antes de ele
acontecer, e por isso tem o fechamento da fatura como limite superior além de hoje. Um **ajuste**
não ocorre dentro do ciclo — ele ocorre sobre o ciclo —, e por isso tem **apenas hoje** como
limite: a janela da fatura MUST NOT limitá-lo em nenhuma das duas direções. Um **pagamento**
ocorre dentro da janela em que aquela fatura pode ser liquidada, que o estado dela decide: o
ciclo, enquanto ela ainda recebe compras; o intervalo entre o fechamento e o vencimento, depois
que ela fechou. Nos dois casos o teto SHALL ser também hoje, e a janela SHALL limitar em ambas as
direções.

O aviso de divergência SHALL valer em todos os formulários em que a divergência é possível,
inclusive no de edição e no de ajuste, com o mesmo dono no domínio e sem corrigir coisa alguma.
No pagamento ele não se aplica, por a janela ali ser limite e não sugestão.

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

#### Scenario: O pagamento de uma fatura aberta é limitado pelo ciclo
- **WHEN** o usuário paga uma fatura `OPEN`
- **THEN** as datas oferecidas vão da abertura do ciclo ao menor entre o fechamento e hoje

#### Scenario: O pagamento de uma fatura fechada começa no fechamento
- **WHEN** o usuário paga uma fatura `CLOSED`
- **THEN** as datas oferecidas vão do fechamento ao menor entre o vencimento e hoje
