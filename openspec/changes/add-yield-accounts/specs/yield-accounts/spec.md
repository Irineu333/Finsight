## ADDED Requirements

### Requirement: A conta declara que rende, e essa declaração governa apenas afordância

Uma conta `ASSET` SHALL poder declarar que **rende** — que o seu saldo cresce por remuneração do próprio dinheiro, sem que o usuário o movimente. A declaração SHALL ser estado primário da conta, escolhido pelo usuário na configuração dela, e MUST NOT ser derivada de lançamentos: uma conta recém-declarada ainda não rendeu, e derivá-la a manteria invisível exatamente quando o usuário precisa começar.

Essa declaração SHALL governar **apenas** o que o sistema oferece: se a conta exibe a linha de rendimento e o caminho para lançar. Ela MUST NOT participar de nenhuma soma, de nenhuma classificação de lançamento e de nenhuma consulta de total — a separação do rendimento nas leituras é feita pela dimensão (ver "Rendimento é separado por dimensão"), e não por ela. São dois donos distintos: a declaração decide *se a linha existe*, a dimensão decide *qual é o número*.

Declarar que uma conta rende, ou deixar de declarar, MUST NOT alterar valor algum já registrado. Lançamentos de rendimento existentes permanecem intactos, classificados como sempre estiveram, e continuam somados no total da conta.

Nenhuma outra natureza de conta SHALL poder declarar rendimento: a declaração pertence a onde o dinheiro do usuário está e é remunerado.

#### Scenario: Conta declarada que ainda não rendeu
- **WHEN** o usuário declara que uma conta rende e nenhum rendimento foi lançado nela
- **THEN** a linha de rendimento é exibida com valor zero e permanece acionável, de onde o primeiro lançamento pode ser feito

#### Scenario: Conta que não declara rendimento
- **WHEN** uma conta não declara que rende
- **THEN** a linha de rendimento não é exibida para ela, e o caminho de lançamento não é oferecido

#### Scenario: Retirar a declaração preserva a história
- **WHEN** o usuário deixa de declarar que uma conta rende, havendo rendimentos já lançados nela
- **THEN** nenhum lançamento é alterado ou removido, o saldo da conta permanece o mesmo, e apenas a oferta do afordance cessa

### Requirement: Rendimento é lançamento, nunca ajuste

Registrar um rendimento SHALL ser um **lançamento**: o usuário informa a data e o valor **do rendimento**, e o sistema registra uma transação nova. MUST NOT ser expresso como saldo-alvo, e MUST NOT reescrever, reconhecer ou acumular sobre lançamento anterior algum.

A transação registrada SHALL ser uma receita comum do razão — uma perna na conta e a contrapartida na conta nominal de receita — carregando a dimensão de rendimentos na perna nominal. Ela MUST NOT ter contrapartida de reconciliação: um rendimento é dinheiro que entrou, não a correção de um saldo que estava errado.

Consequentemente, o rendimento SHALL ser derivado como receita por todo consumidor que classifica uma transação pelas naturezas das suas contas, sem que nenhum deles precise conhecer o conceito de rendimento.

O caminho de ajuste de saldo SHALL permanecer inalterado: ajustar o saldo de uma conta que rende continua registrando ajuste com contrapartida de reconciliação. As duas operações respondem a perguntas distintas — "rendeu quanto" e "o saldo é quanto" — e MUST NOT ser fundidas.

#### Scenario: Lançar rendimento
- **WHEN** o usuário informa a data e o valor de um rendimento em uma conta que declara render
- **THEN** o sistema registra uma transação de receita nessa conta, com a contrapartida na conta nominal de receita carregando a dimensão de rendimentos

#### Scenario: Dois rendimentos na mesma data somam
- **WHEN** o usuário lança dois rendimentos na mesma conta e na mesma data
- **THEN** ambos são registrados como transações distintas e ambos somam no total de rendimento do período, sem que o segundo substitua o primeiro

#### Scenario: Rendimento não colide com receita do mesmo dia
- **WHEN** existe uma receita comum na conta na mesma data de um rendimento
- **THEN** cada uma permanece uma transação independente, e lançar ou alterar uma MUST NOT afetar a outra

#### Scenario: Rendimento é lido como receita
- **WHEN** um lançamento de rendimento é exibido em uma lista de transações
- **THEN** ele é derivado como receita, exibido com a classificação da sua categoria, sem tratamento especial

#### Scenario: Ajuste de saldo permanece ajuste
- **WHEN** o usuário ajusta o saldo de uma conta que declara render
- **THEN** o sistema registra um ajuste com contrapartida de reconciliação, exatamente como em qualquer outra conta

### Requirement: A categoria de rendimentos é de sistema, identificada por chave e criada sob demanda

O sistema SHALL prover **uma** categoria de receita para rendimentos, única em todo o app, e SHALL garantir a sua existência quando a primeira conta declarar que rende — criada sob demanda, não semeada na instalação. Enquanto nenhuma conta declarar rendimento, ela MUST NOT existir.

Essa categoria SHALL ser identificada por uma **chave de sistema**, e MUST NOT ser identificada pelo seu nome. Disso decorre a propriedade que se quer: o usuário SHALL poder renomeá-la e trocar o seu ícone livremente — para "CDI", "Rendimento", o que fizer sentido para ele — sem que a identificação, a classificação de lançamentos ou a separação nas leituras deixem de funcionar.

Em tudo o mais ela SHALL ser uma categoria comum: aparece nas listagens, é oferecida nos seletores de lançamento, soma nos agrupamentos por categoria e participa de orçamento como qualquer outra. Ser de sistema MUST NOT lhe conferir imutabilidade, invisibilidade nem tratamento especial em leitura alguma.

O sistema MUST NOT oferecer, entre as categorias sugeridas na criação do conjunto padrão, uma categoria de investimentos separada — ela é conceitualmente a mesma coisa e conviveria como duplicata.

#### Scenario: Categoria criada na primeira declaração
- **WHEN** o usuário declara pela primeira vez que uma conta rende
- **THEN** o sistema garante a existência da categoria de rendimentos antes de concluir a operação

#### Scenario: Segunda conta não cria segunda categoria
- **WHEN** o usuário declara que uma segunda conta rende
- **THEN** nenhuma categoria nova é criada, e as duas contas classificam os seus rendimentos na mesma categoria

#### Scenario: Categoria nunca criada sem uso
- **WHEN** nenhuma conta declara render
- **THEN** a categoria de rendimentos não existe no sistema

#### Scenario: Renomear não quebra a identificação
- **WHEN** o usuário renomeia a categoria de rendimentos para "CDI" e troca o seu ícone
- **THEN** os rendimentos continuam classificados nela, a separação nas leituras continua correta, e um novo lançamento de rendimento continua encontrando-a

#### Scenario: Categoria de rendimentos é oferecida como qualquer outra
- **WHEN** o usuário registra uma transação de receita comum e abre o seletor de categorias
- **THEN** a categoria de rendimentos é oferecida entre as demais

### Requirement: Rendimento é separado por dimensão, e o razão não sabe o que ele é

A separação do rendimento nas leituras SHALL ser feita pela **dimensão** que a categoria de rendimentos carrega, e MUST NOT ser feita por conta dedicada no plano de contas, por coluna na transação nem por qualquer marcador próprio.

A leitura no razão SHALL ser expressa em vocabulário de razão — natureza de conta, período e identidade de dimensão — e MUST NOT nomear categoria nem rendimento. Traduzir "a categoria de rendimentos" na identidade da sua dimensão SHALL ser responsabilidade da feature que detém a fachada, não do razão.

Quando nenhuma dimensão de rendimento existe, os totais SHALL ser idênticos aos que seriam produzidos sem esta capacidade: a separação SHALL degradar para nada, sem tratamento condicional próprio.

#### Scenario: Total de rendimento de uma conta no período
- **WHEN** o total de rendimento de uma conta em um mês é solicitado
- **THEN** o sistema retorna a soma das entries da conta cujas transações têm contrapartida nominal de receita carregando a dimensão de rendimentos

#### Scenario: A leitura do razão não nomeia a fachada
- **WHEN** a leitura que separa rendimento é declarada no razão
- **THEN** ela recebe a identidade da dimensão como parâmetro, e MUST NOT nomear categoria, rendimento ou qualquer tabela de fachada

#### Scenario: Sem dimensão de rendimento os totais não mudam
- **WHEN** nenhuma conta declara render e a categoria de rendimentos não existe
- **THEN** os totais de receita e despesa são exatamente os mesmos que seriam produzidos antes desta capacidade
