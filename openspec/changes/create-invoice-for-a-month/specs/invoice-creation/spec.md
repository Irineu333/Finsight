## ADDED Requirements

### Requirement: Criar uma fatura declara o ciclo, e não o seu valor

Criar uma fatura SHALL receber apenas o **alvo** — o cartão e o mês de vencimento — e MUST NOT
receber valor, lançamento ou data de evento. A fatura nasce **vazia**, e o que ela vale é
declarado depois, pelo ajuste de saldo ou pelos lançamentos.

O que a criação acrescenta ao sistema SHALL ser exatamente o que nenhum cálculo produz: a
afirmação de que aquele ciclo existiu para o usuário. A janela, o vencimento e o valor MUST NOT
ser informados na criação, porque nenhum deles é declaração — os dois primeiros derivam do
cartão e o terceiro dos lançamentos.

Uma fatura criada e não usada SHALL poder ser removida, e MUST NOT deixar resíduo no razão.

#### Scenario: Criar não move o razão
- **WHEN** o usuário cria a fatura de um mês
- **THEN** nenhuma entry é gravada, e o saldo devido daquela fatura é zero

#### Scenario: O valor vem depois, por outro gesto
- **WHEN** o usuário cria a fatura de um mês passado e em seguida ajusta o saldo dela
- **THEN** o valor declarado é o do ajuste, e a criação não participou dessa decisão

#### Scenario: Fatura criada e abandonada é removível
- **WHEN** o usuário cria uma fatura e não lança nada nela
- **THEN** ela pode ser removida, e removê-la não deixa dimensão nem lançamento para trás

### Requirement: A criação de fatura é uma operação única parametrizada pelo mês

SHALL existir **uma só** operação de criação de fatura, parametrizada pelo mês de vencimento.
MUST NOT existir uma operação por status resultante, nem uma operação cuja identidade seja o
período criado — retroativo e futuro são o mesmo ato aplicado a meses diferentes.

Todo caminho que faz nascer uma fatura por mês alvo SHALL passar por essa operação: tanto o
gesto explícito do usuário quanto a criação sob demanda de um lançamento. MUST NOT existir um
segundo caminho de inserção que derive a fatura por conta própria.

#### Scenario: Uma só operação de criação
- **WHEN** o código que faz nascer uma fatura por mês alvo é inspecionado
- **THEN** existe uma única operação, e nenhuma cujo nome ou corpo se especialize num status

#### Scenario: O gesto e o lançamento criam pelo mesmo caminho
- **WHEN** o usuário cria a fatura de um mês pela tela, e noutro cartão a mesma fatura nasce por
  um lançamento naquele mês
- **THEN** as duas faturas são idênticas em janela, vencimento e status

### Requirement: O status da fatura criada é derivado do mês, nunca escolhido

O status SHALL ser decidido comparando o mês de vencimento alvo com o **vencimento da fatura
aberta** do cartão: antes dela a fatura nasce `RETROACTIVE`, dela em diante nasce `FUTURE`.

Essa regra SHALL ter um dono único, e a interface MUST NOT ter opinião sobre ela — nem
escolhendo o status, nem reimplementando a comparação para antecipar o resultado.

A referência SHALL ser a fatura aberta, e MUST NOT ser a data de hoje: um cartão cuja fatura
aberta ficou para trás continua criando pelo mesmo critério, sem caso especial.

#### Scenario: Mês anterior ao vencimento da aberta
- **WHEN** o usuário cria a fatura de um mês cujo vencimento é anterior ao da fatura aberta
- **THEN** ela nasce `RETROACTIVE`

#### Scenario: Mês do vencimento da aberta em diante
- **WHEN** o usuário cria a fatura de um mês cujo vencimento é igual ou posterior ao da fatura aberta
- **THEN** ela nasce `FUTURE`

#### Scenario: A referência é a fatura aberta, não hoje
- **WHEN** a fatura aberta do cartão vence em julho, hoje é outubro, e o usuário cria a fatura de
  agosto
- **THEN** ela nasce `FUTURE`, porque agosto vence depois de julho

#### Scenario: Sem fatura aberta não há como classificar
- **WHEN** o cartão não possui fatura aberta e o usuário tenta criar uma fatura
- **THEN** a criação é recusada, pela mesma falta que já impede o lançamento nesse cartão

### Requirement: Um mês que já tem fatura não é criável

A criação SHALL ser recusada quando já existir fatura com aquele mês de vencimento no cartão, em
qualquer status.

A interface SHALL **exibir** o mês ocupado e desabilitar o envio, e MUST NOT escondê-lo nem
saltá-lo na navegação: pular meses tiraria do usuário a noção de onde ele está no calendário.

A recusa SHALL existir também no domínio, e MUST NOT depender de a interface ter filtrado antes.

#### Scenario: Navegar até um mês ocupado
- **WHEN** o usuário navega, na modal de criação, até um mês que já tem fatura
- **THEN** o mês é exibido, sinalizado como já existente, e o envio fica indisponível

#### Scenario: A recusa não depende da tela
- **WHEN** a criação é solicitada para um mês que já tem fatura
- **THEN** ela falha, sem inserir e sem devolver a fatura existente no lugar da criada

### Requirement: A janela da fatura criada é derivada do cartão

Escolhido o mês de vencimento, a abertura e o fechamento SHALL ser derivados pelo dono da janela
de compra, a partir do `closingDay` e do `dueDay` do cartão. MUST NOT existir na criação qualquer
cálculo próprio de meses — em particular, MUST NOT haver mês de abertura ou fechamento fixado a
partir do mês corrente.

A janela exibida na modal antes de criar SHALL ser a mesma da fatura criada.

#### Scenario: A janela prometida é a janela gravada
- **WHEN** o usuário vê a janela de um mês na modal e cria a fatura daquele mês
- **THEN** a fatura gravada tem exatamente aquela abertura e aquele fechamento

#### Scenario: Cartão com vencimento adiado
- **WHEN** o usuário cria uma fatura num cartão cujo `dueDay` é anterior ao `closingDay`
- **THEN** o fechamento cai no mês anterior ao do vencimento, pela mesma regra que os demais
  formulários já aplicam

### Requirement: A fatura criada é o destino da tela

Criar uma fatura SHALL levar a tela de faturas até ela, e MUST NOT deixar o usuário na fatura em
que ele estava.

Chegando lá, o ajuste de saldo SHALL estar disponível pelos meios que a tela já oferece. A
criação MUST NOT encadear automaticamente outro formulário: declarar quanto a fatura valia é
gesto seguinte do usuário, não continuação do gesto de criar.

#### Scenario: Criar navega até a fatura nova
- **WHEN** o usuário cria a fatura de um mês passado
- **THEN** a tela passa a exibir essa fatura, entre as demais na ordem do calendário

#### Scenario: O ajuste está ao alcance, sem ser imposto
- **WHEN** a tela chega à fatura recém-criada
- **THEN** o ajuste de saldo está disponível ali, e nenhum formulário foi aberto sem o usuário pedir
