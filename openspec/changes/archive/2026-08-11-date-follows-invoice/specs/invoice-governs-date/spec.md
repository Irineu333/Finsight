## ADDED Requirements

### Requirement: A fatura governa a data; a data não governa a fatura

Nos formulários de lançamento em cartão, o preenchimento SHALL obedecer a uma hierarquia estrita:
**cartão governa fatura, fatura governa data**. Um campo SHALL alterar apenas o que está abaixo
dele.

Selecionar uma fatura SHALL recolocar a data pela projeção do dia corrente do formulário na
janela dessa fatura. Trocar o cartão SHALL ter o mesmo efeito, porque o cartão redefine a janela
mesmo sob o mesmo mês de vencimento.

Editar a data MUST NOT alterar a fatura selecionada, nem diretamente nem por efeito colateral. A
projeção é uma **sugestão** do sistema; a palavra final sobre a data é do usuário, e uma data que
ele escreveu fora da janela SHALL ser preservada exatamente como escrita.

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

### Requirement: A projeção nunca produz uma data futura

O resultado da projeção SHALL ser limitado a **hoje**: se a janela da fatura selecionada é
posterior a hoje, a data SHALL ser hoje.

Isso decorre do significado de uma fatura futura: ela não representa um gasto no futuro, e sim um
gasto no presente que se paga no futuro. A trava SHALL valer igualmente para faturas ainda não
criadas, alcançadas por navegação adiante.

O seletor de data SHALL continuar recusando datas futuras; a projeção MUST NOT produzir um valor
que esse seletor recusaria.

#### Scenario: Navegar para uma fatura futura trava a data em hoje
- **WHEN** hoje é 12/março e o usuário navega para uma fatura cuja janela começa em abril
- **THEN** a data passa a ser 12/março, e não uma data de abril

#### Scenario: Voltar de uma fatura futura reprojeta a partir de hoje
- **WHEN** a data está travada em hoje por uma fatura futura e o usuário navega de volta para a fatura aberta
- **THEN** a data volta a ser hoje, por a projeção ser idempotente dentro da janela aberta

### Requirement: Abrir o formulário não altera a data

Ao abrir o formulário, a data SHALL ser hoje e a fatura SHALL ser a aberta do cartão. A
recolocação disparada por essa seleção inicial SHALL ser um no-op, porque hoje pertence à janela
da fatura aberta por definição.

A seleção automática do único cartão disponível MUST NOT produzir efeito visível sobre a data.

#### Scenario: Formulário aberto mostra hoje
- **WHEN** o modal de adicionar transação é aberto com alvo cartão e há uma fatura aberta
- **THEN** o campo de data exibe hoje, inalterado

#### Scenario: Cartão único selecionado automaticamente
- **WHEN** existe um só cartão e ele é selecionado sem ação do usuário
- **THEN** a data permanece hoje

### Requirement: O campo de data reflete o estado que o governa

O campo de data SHALL exibir o valor que o formulário sustenta. Uma recolocação decidida pelo
estado SHALL aparecer no campo sem exigir nova ação do usuário, e a sincronização entre campo e
estado MUST NOT realimentar-se: escrever no campo, receber o valor de volta e reescrevê-lo não
SHALL produzir laço nem perder o que está sendo digitado.

Um campo de data em estado incompleto — ainda sendo digitado e não interpretável como data —
MUST NOT impedir a recolocação; nesse caso o dia preservado SHALL ser o de hoje.

#### Scenario: Recolocação aparece no campo
- **WHEN** a fatura muda e o estado recoloca a data
- **THEN** o campo de data passa a exibir a nova data

#### Scenario: Digitação não é sobrescrita por eco
- **WHEN** o usuário digita no campo de data
- **THEN** o valor digitado permanece, sem que o eco do estado o reescreva

#### Scenario: Data incompleta no momento da troca de fatura
- **WHEN** o campo contém um valor incompleto e o usuário troca a fatura
- **THEN** a data é recolocada usando o dia de hoje como dia preservado

### Requirement: A divergência é dita, nunca corrigida

Quando a data não pertence à janela da fatura selecionada, o formulário SHALL dizê-lo, de forma
discreta e junto ao campo de data.

O aviso MUST NOT alterar a data, a fatura, ou a possibilidade de gravar: divergir não é errar, e
nada do lançamento é decidido pela data — a fatura é. Por isso ele MUST NOT ser apresentado como
erro, nem impedir o envio.

A pergunta "esta data está fora do que a fatura admite" SHALL ter um dono único no domínio; as
telas a consomem e MUST NOT reimplementá-la. Uma data ainda em digitação, não interpretável como
data, MUST NOT ser acusada de divergir — ela não afirma nada.

A decisão de exibir o aviso SHALL pertencer ao estado da tela, não ao seu desenho, de modo que
seja verificável sem um dispositivo.

Diferentemente da recolocação, o aviso SHALL valer também no formulário de **edição**: ele não
altera nada, e é ali que a divergência é mais provável.

#### Scenario: Data escrita fora da janela
- **WHEN** o usuário escreve uma data que a fatura selecionada não admite
- **THEN** o formulário sinaliza a divergência sem alterar a data, a fatura, nem a possibilidade de gravar

#### Scenario: Data recolocada pela projeção
- **WHEN** a data acaba de ser recolocada por uma troca de fatura
- **THEN** nenhuma divergência é sinalizada, porque a projeção a colocou dentro da janela

#### Scenario: Data em digitação
- **WHEN** o campo contém um valor incompleto
- **THEN** nenhuma divergência é sinalizada

#### Scenario: Divergência numa transação existente
- **WHEN** a data de uma transação em edição está fora da janela da fatura dela
- **THEN** o formulário sinaliza a divergência e não move nem a data nem a fatura

### Requirement: O escopo da hierarquia é o lançamento novo

A recolocação SHALL valer nos formulários de **criação** — adicionar transação e adicionar
parcelamento. Ela MUST NOT valer no formulário de **edição** de transação.

A distinção não é de escopo de entrega: na criação a data é um valor padrão do sistema, e
sugerir sobre ele é legítimo; na edição a data é dado que o usuário já escreveu, e sobrescrevê-la
contradiria a regra de que a palavra final é dele.

#### Scenario: Editar uma transação e trocar a fatura
- **WHEN** o usuário edita uma transação existente e troca a fatura selecionada
- **THEN** a data da transação permanece exatamente como estava

#### Scenario: Parcelamento segue a mesma hierarquia da transação
- **WHEN** o usuário troca a fatura no modal de adicionar parcelamento
- **THEN** a data é recolocada pela mesma regra do modal de adicionar transação
