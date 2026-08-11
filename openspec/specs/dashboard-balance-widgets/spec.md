# dashboard-balance-widgets Specification

## Purpose

Os perímetros que o dashboard oferece como widget de dinheiro, e a regra que os governa: **todo widget nomeia o perímetro do plano de contas que ele soma** — um rótulo neutro sobre uma só natureza de conta é afirmação falsa, não ambiguidade. Os três perímetros de fluxo (apenas contas `ASSET`, apenas cartões `LIABILITY`, e o neutro) existem como widgets independentes e coexistem na tela, porque o perímetro é a identidade do widget e não uma configuração dele. O neutro é derivado **somando** as leituras por natureza que o razão já expõe, nunca por um agregado dedicado — as duas despesas são disjuntas, então a soma não dupla-conta, e o pagamento de fatura é interno ao perímetro. E o que um widget de fluxo reporta é fluxo: sem abertura, sem fechamento, e com o mesmo conjunto de classes nos três, o que faz `Despesas(geral) = Despesas(contas) + Gasto no Cartão` verificável na tela. Consome as leituras do razão (`ledger-reporting`) sobre o plano de contas (`chart-of-accounts`); distingue-se de `transaction-scope`, que modela os mesmos três perímetros sob a gramática de fechamento da tela de transações.
## Requirements
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

A **natureza de conta** SHALL ser a identidade do widget, e MUST NOT ser uma configuração de um widget único, enquanto a identidade de um widget for o seu tipo — sob essa identidade, um widget configurável ofereceria uma natureza por vez e tornaria a coexistência impossível.

Esta regra rege o eixo da **natureza**, e MUST NOT ser lida como proibição de configurar **quais contas, dentro de uma natureza**, compõem a figura de um widget. Os dois eixos são distintos e a razão da regra separa-os: naturezas diferentes são figuras que o usuário quer comparar lado a lado, o que exige widgets simultâneos; um recorte de contas dentro de uma natureza é uma figura só, que o usuário quer ver de um jeito, o que se exprime como configuração. Um recorte de contas MUST NOT ser modelado como tipo de widget novo.

O widget neutro SHALL nascer **ausente** dos dashboards já existentes e ser adicionado pelo modo de edição, como qualquer outro widget. Nenhuma preferência salva SHALL ser reescrita.

#### Scenario: Contas, cartões e geral na mesma tela
- **WHEN** o usuário adiciona os três widgets de fluxo
- **THEN** os três aparecem simultaneamente, cada um somando a sua natureza

#### Scenario: Dashboard existente não muda
- **WHEN** um dashboard montado antes desta capacidade é carregado
- **THEN** ele exibe exatamente os mesmos widgets, na mesma ordem e com a mesma aparência, sem o widget neutro

#### Scenario: Recorte de contas não vira tipo de widget
- **WHEN** o usuário restringe quais contas compõem a figura de um widget de saldo
- **THEN** isso é gravado como configuração daquele widget, e nenhum tipo de widget novo é criado para o recorte

### Requirement: O perímetro neutro é a soma das naturezas disjuntas

O fluxo do perímetro neutro SHALL ser derivado **somando** as leituras de fluxo do razão por natureza, e MUST NOT depender de um agregado dedicado a esse perímetro.

Sendo as leituras de fluxo expressas por moeda, a soma SHALL ser a soma **por moeda** — cada moeda somada com a sua própria, sem conversão. Essa operação SHALL usar a única implementação de soma de saldos por moeda do razão (`ledger-reporting`), e MUST NOT ser realizada em linha pelo construtor do widget. Consumir essa operação MUST NOT ser entendido como depender de agregado dedicado: ela é aritmética genérica sobre dois resultados, e não uma consulta criada para este perímetro — a distinção que este requisito preserva é entre somar o que o razão já expõe e criar uma leitura nova para o perímetro neutro.

A despesa do perímetro neutro SHALL ser a soma da despesa de `ASSET` e da despesa de `LIABILITY`. A soma MUST NOT dupla-contar: os dois conjuntos são disjuntos, porque um lançamento de cartão não tem perna `ASSET`.

O pagamento de fatura MUST NOT ser reportado como despesa em nenhuma das duas parcelas, por ser movimento interno ao perímetro neutro — as suas duas pernas estão dentro dele. Um pagamento de fatura que atravesse moedas SHALL permanecer interno pela mesma razão: as suas pernas monetárias estão ambas no perímetro, e as de conversão não o tornam fluxo.

A receita do perímetro neutro SHALL ser idêntica à do perímetro de contas, uma vez que o razão não registra receita em perna de `LIABILITY`.

#### Scenario: Compra de cartão entra na despesa neutra
- **WHEN** existe uma compra de cartão no mês e o fluxo do perímetro neutro é exibido
- **THEN** ela é somada à despesa do perímetro neutro

#### Scenario: Compra no cartão entra uma vez só
- **WHEN** existe uma compra de cartão no mês e o fluxo do perímetro neutro é exibido
- **THEN** ela é contada exatamente uma vez na despesa

#### Scenario: Pagamento de fatura não é despesa do perímetro neutro
- **WHEN** existe um pagamento de fatura no mês e o fluxo do perímetro neutro é exibido
- **THEN** ele não é somado como despesa, por ser interno ao perímetro

#### Scenario: Pagamento de fatura entre moedas continua interno
- **WHEN** existe um pagamento de fatura em moeda distinta da conta pagadora e o fluxo do perímetro neutro é exibido
- **THEN** ele não é somado como despesa, e as suas pernas de conversão não o tornam fluxo

#### Scenario: A despesa neutra é a soma das duas exibidas
- **WHEN** os três widgets de fluxo estão presentes na tela
- **THEN** a despesa do widget neutro é igual à despesa do widget de contas somada ao gasto do widget de cartões

#### Scenario: Soma por moeda, sem conversão
- **WHEN** o fluxo do perímetro neutro é derivado sobre contas em duas moedas
- **THEN** cada moeda é somada com a sua própria, e nenhuma conversão participa da derivação

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

### Requirement: O widget de saldo em contas tem perímetro de contas configurável

O widget que exibe o saldo acumulado das contas `ASSET` SHALL permitir ao usuário definir **quais contas** compõem o seu valor, excluindo as que ele não quiser somar.

O conjunto excluído SHALL ser configuração **daquele widget**, gravado com as demais preferências do dashboard, e MUST NOT ser propriedade da conta: excluir uma conta do total MUST NOT alterar a conta, os seus lançamentos, o seu próprio saldo, nem o comportamento dela em qualquer outra tela do app.

O padrão SHALL ser o conjunto **vazio**. Um dashboard montado antes desta capacidade SHALL exibir exatamente o mesmo valor que exibia, sem reescrita de preferência e sem ramo de compatibilidade.

A exclusão SHALL ser reversível pela mesma configuração, e reincluir uma conta SHALL devolver o valor anterior sem qualquer outra ação do usuário.

O recorte SHALL ser aplicado **dentro da leitura** que produz a figura (`ledger-reporting`), e MUST NOT ser obtido somando contas uma a uma no construtor do widget nem subtraindo saldos individuais de um total já somado.

#### Scenario: Conta excluída sai do total
- **WHEN** o usuário desmarca uma conta com saldo na configuração do widget de saldo em contas
- **THEN** o valor exibido passa a ser o das demais contas, e o saldo daquela conta continua íntegro onde quer que ele seja exibido

#### Scenario: Reincluir devolve o valor
- **WHEN** o usuário torna a marcar uma conta que havia excluído
- **THEN** o valor exibido volta a ser o de antes da exclusão

#### Scenario: Dashboard existente exibe o mesmo número
- **WHEN** um dashboard montado antes desta capacidade é carregado
- **THEN** o widget de saldo em contas exibe exatamente o valor que exibia antes, sem nenhuma conta excluída

#### Scenario: Excluir do total não é excluir da lista
- **WHEN** o usuário exclui uma conta do widget de saldo em contas
- **THEN** ela continua aparecendo no card de contas, cuja própria exclusão permanece independente desta

#### Scenario: O recorte não é propriedade da conta
- **WHEN** uma conta está excluída do widget de saldo em contas
- **THEN** nada na conta a distingue de outra conta, e nenhuma outra tela do app deixa de a somar por causa dessa exclusão

### Requirement: Total sem parcela alguma vale zero

Um widget de saldo cujo perímetro de contas tenha sido reduzido até não restar conta alguma SHALL exibir **zero**, e MUST NOT desaparecer da tela.

A distinção com um card de lista é deliberada: uma lista sem itens não tem o que desenhar, enquanto um total sem parcelas tem — vale zero. E um widget que se oculta enquanto o usuário opera a sua própria configuração torna a configuração inoperável, já que o alvo da edição some no meio dela.

O zero SHALL ser denominado pela mesma regra que denomina qualquer figura sem termos (`currency-consolidation`), e MUST NOT receber tratamento de moeda próprio por ter nascido de uma exclusão.

#### Scenario: Todas as contas excluídas
- **WHEN** o usuário exclui todas as contas do widget de saldo em contas
- **THEN** o widget permanece na tela exibindo zero

#### Scenario: Zero por exclusão é um zero como outro qualquer
- **WHEN** o widget exibe zero por não ter restado conta alguma no perímetro
- **THEN** a moeda em que esse zero é exibido é a mesma que qualquer figura sem termos receberia

### Requirement: Um perímetro autoral do usuário não exige qualificação no rótulo

A regra que obriga um widget de dinheiro a nomear o seu perímetro rege o perímetro que **o sistema** escolhe — a natureza de conta somada —, e MUST NOT ser estendida ao recorte de contas que o **próprio usuário** definiu na configuração daquele widget.

Um widget cujo perímetro de contas foi reduzido pelo usuário SHALL conservar o seu rótulo. A razão da regra original não se aplica: ela protege o usuário de um recorte que ele não tem como conhecer, e um recorte que ele mesmo autorou está inteiro visível na configuração do widget.

Reduzir o perímetro MUST NOT alterar o rótulo, o ícone ou a forma do widget — o que muda é o valor, e apenas ele.

#### Scenario: Rótulo preservado sob exclusão
- **WHEN** o usuário exclui contas do widget de saldo em contas
- **THEN** o rótulo do widget permanece o mesmo, e apenas o valor exibido muda

#### Scenario: A regra de rótulo continua valendo para a natureza
- **WHEN** um widget soma apenas contas `ASSET`
- **THEN** ele continua proibido de se dizer saldo total ou geral, com ou sem contas excluídas

### Requirement: A exatidão da figura acompanha o perímetro

A marca de aproximação de um widget de saldo SHALL ser derivada das moedas que **restaram** no perímetro, e MUST NOT ser preservada a partir das moedas que a exclusão retirou.

Em consequência, excluir a única conta de uma moeda sem taxa conhecida SHALL fazer a figura deixar de ter mais de um termo e passar a ser exibida como exata. Isso é o perímetro produzindo o seu efeito, e MUST NOT ser tratado como caso especial nem compensado por regra própria.

#### Scenario: Excluir a moeda sem taxa torna a figura exata
- **WHEN** o usuário exclui a única conta denominada numa moeda para a qual não há taxa conhecida
- **THEN** a figura passa a ter um único termo, na moeda restante, e deixa de exibir marca de aproximação

#### Scenario: Excluir conta da mesma moeda não muda a exatidão
- **WHEN** o usuário exclui uma conta cuja moeda continua representada por outra conta do perímetro
- **THEN** a exatidão da figura permanece a que era, e apenas o valor muda
