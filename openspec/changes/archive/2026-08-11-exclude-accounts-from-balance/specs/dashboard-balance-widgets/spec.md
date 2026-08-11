## MODIFIED Requirements

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

## ADDED Requirements

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
