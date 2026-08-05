# chart-of-accounts Specification

## Purpose

O plano de contas unificado: toda conta, cartão e categoria é uma `Account` com um `type` do conjunto **fechado** `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}`. Define "categoria" e "cartão" como fachadas projetadas sobre esse plano, ligadas à sua conta, e a conta de sistema usada como contrapartida de ajustes e baixas. É a base sobre a qual o razão (`balanced-ledger`) registra entries e da qual as leituras (`ledger-reporting`) derivam.
## Requirements
### Requirement: Plano de contas unificado
O sistema SHALL representar toda conta e cartão como uma `Account` pertencente a um plano de contas único, cada `Account` com um `type` do conjunto fechado `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY, CONVERSION}` e uma `currency`. Conta corrente, poupança, dinheiro, investimento e valores a receber de terceiros SHALL ter `type = ASSET`; cartão de crédito, empréstimo e valores a pagar a terceiros SHALL ter `type = LIABILITY`; contas de reconciliação SHALL ter `type = EQUITY`; contas de conversão cambial SHALL ter `type = CONVERSION`. Nenhum outro tipo de conta SHALL existir.

A conversão cambial MUST NOT ser representada como `EQUITY`. A presença de uma perna `EQUITY` numa transação SHALL continuar significando exatamente uma coisa — que aquela transação é um ajuste —, e esse significado MUST NOT ser compartilhado com nenhum outro conceito. Uma perna de conversão MUST NOT tornar a sua transação um ajuste, MUST NOT ser classificada como ajuste em nenhuma leitura, e MUST NOT satisfazer nenhum predicado que identifique um ajuste existente.

Toda `Account` SHALL estar denominada em **exatamente uma** moeda, e isso SHALL valer para **toda** linha do plano, sem exceção para as contas de sistema. Uma conta MUST NOT acolher entries de mais de uma moeda.

O plano de contas SHALL conter **apenas o que é contábil**. Categoria MUST NOT ser uma linha do plano de contas: a classificação de um lançamento por rótulo do usuário SHALL ser expressa por dimensão, não por conta. O plano SHALL conter exatamente **duas** contas nominais **por moeda em uso** — uma de `type = EXPENSE` e uma de `type = INCOME` — sobre as quais toda perna de contrapartida nominal daquela moeda posta, qualquer que seja a sua classificação. Uma moeda que nenhuma conta usa MUST NOT ter contas nominais no plano: elas nascem sob demanda, como as demais contas de sistema.

O plano de contas SHALL distinguir as contas **monetárias** (`ASSET` e `LIABILITY` — onde o dinheiro está, e que o usuário escolhe ao registrar um lançamento) das contas de **contrapartida** (`INCOME`, `EXPENSE`, `EQUITY` e `CONVERSION` — por que o dinheiro se moveu, sintetizadas pelo sistema). Essa distinção SHALL ser expressa no próprio tipo de conta, e MUST NOT ser reimplementada caso a caso pelos consumidores. `CONVERSION` SHALL ser de natureza credora, MUST NOT ser monetária e MUST NOT acolher dimensão de categoria.

#### Scenario: Conta financeira do usuário
- **WHEN** o usuário cria uma conta corrente
- **THEN** ela é registrada no plano de contas com `type = ASSET` e a moeda escolhida

#### Scenario: Cartão de crédito como passivo
- **WHEN** o usuário cria um cartão de crédito
- **THEN** ele é registrado no plano de contas com `type = LIABILITY` e a moeda escolhida

#### Scenario: Conversão não é patrimônio
- **WHEN** uma transferência entre contas de moedas diferentes é registrada
- **THEN** as pernas de conversão postam em contas `CONVERSION`, nenhuma perna `EQUITY` existe na transação, e ela não é tratada como ajuste em nenhuma superfície ou leitura

#### Scenario: Categoria não entra no plano de contas
- **WHEN** o usuário cria uma categoria de despesa ou de receita
- **THEN** nenhuma conta é criada, e a categoria passa a existir como dimensão

#### Scenario: Plano de contas contém duas nominais por moeda
- **WHEN** o plano de contas é inspecionado, com qualquer número de categorias existentes e lançamentos em duas moedas
- **THEN** ele contém exatamente uma conta `EXPENSE` e uma conta `INCOME` **por moeda**, e nenhuma para uma moeda sem lançamentos

#### Scenario: Nenhuma conta acolhe duas moedas
- **WHEN** qualquer conta do plano — de usuário ou de sistema — tem as suas entries inspecionadas
- **THEN** todas estão na moeda daquela conta

#### Scenario: Contas monetárias e de contrapartida
- **WHEN** o sistema precisa saber quais contas de uma operação representam dinheiro
- **THEN** as contas `ASSET` e `LIABILITY` são identificadas como monetárias, e as `INCOME`, `EXPENSE`, `EQUITY` e `CONVERSION` como contrapartida

### Requirement: Fachada de categoria e cartão preservada
A interface do usuário SHALL continuar apresentando "categoria" e "cartão" como conceitos. O cartão SHALL projetar sobre a `Account` de tipo `LIABILITY` que o representa; a categoria SHALL projetar sobre a sua **dimensão**, e sobre a conta nominal correspondente à sua natureza. O usuário MUST NOT precisar conhecer os tipos contábeis, as dimensões nem os termos débito/crédito para operar o app.

A natureza `INCOME`/`EXPENSE` da categoria SHALL ser estado primário da fachada de categoria, e é o que decide em qual conta nominal a perna posta. Esta é uma **exceção explícita e documentada** à regra de que toda regra derivável tem seu dono no domínio, justificada por essa natureza ser declaração do usuário no momento da criação, e não fato derivável do razão. A exceção SHALL estar restrita a este único dado: nenhuma outra classificação contábil SHALL migrar para a fachada por analogia com ela.

O estado de arquivamento da categoria SHALL ser próprio da fachada, deixando de ser lido do plano de contas. Isso MUST NOT alterar comportamento: a fronteira de escrita nunca aplicou verificação de fechamento a categorias, por uma categoria poder ser arquivada com qualquer saldo.

#### Scenario: Usuário classifica uma despesa
- **WHEN** o usuário escolhe a categoria "Alimentação" em uma despesa
- **THEN** a UI mostra "Alimentação" como categoria, enquanto internamente a perna nominal posta na conta `EXPENSE` única, carregando a dimensão de "Alimentação"

#### Scenario: Natureza da categoria escolhe a conta nominal
- **WHEN** um lançamento é escrito com uma categoria de natureza `INCOME`
- **THEN** a perna de contrapartida posta na conta nominal `INCOME`, e com natureza `EXPENSE` posta na conta nominal `EXPENSE`

#### Scenario: Compatibilidade categoria x sentido do lançamento
- **WHEN** uma categoria de natureza `EXPENSE` é associada a um lançamento
- **THEN** o sistema SHALL aceitá-la apenas em lançamentos que aumentam despesa, preservando a regra de coerência entre a natureza da categoria e o sentido do lançamento

#### Scenario: Arquivamento de categoria não passa pela conta
- **WHEN** uma categoria é arquivada
- **THEN** o estado é gravado na própria fachada, nenhuma conta muda de estado, e a escrita de lançamentos que a referenciam continua permitida como antes

### Requirement: Contas de sistema
O sistema SHALL prover, **por moeda**, uma conta de `type = EQUITY` para reconciliação de saldo usada como contrapartida de ajustes, uma conta de `type = CONVERSION` para conversão cambial, e as duas contas nominais (`EXPENSE` e `INCOME`) sobre as quais toda contrapartida nominal daquela moeda posta. Essas contas SHALL existir de forma garantida quando um lançamento que as referencie for registrado, sendo criadas sob demanda ou semeadas, e MUST NOT ser apagáveis pelo usuário enquanto houver lançamentos que as referenciem.

Uma conta de sistema SHALL ser identificada pela tripla `(type, nome, moeda)`. A natureza da conta MUST NOT ser usada como chave: uma mesma natureza pode ter mais de uma conta de sistema por moeda. O nome SHALL permanecer chave de busca e MUST NOT ser renderizado ao usuário, para nenhuma das contas de sistema — a de conversão inclusive.

As contas de conversão SHALL ser criadas exclusivamente pela fronteira de escrita, ao completar uma operação que atravessa moedas. Elas MUST NOT ser oferecidas em nenhum seletor, e o usuário MUST NOT poder lançar diretamente nelas.

O sistema MUST NOT prover conta de sistema para representar a ausência de classificação: um lançamento sem categoria SHALL ser uma perna nominal **sem dimensão**, e não uma perna numa conta de sistema dedicada. O sistema MUST NOT prover uma conta de sistema de "saldo inicial" enquanto não existir um conceito de saldo inicial exposto ao usuário: contas de sistema SHALL existir apenas quando houver um uso real que as referencie.

#### Scenario: Ajuste referencia conta de reconciliação
- **WHEN** um ajuste de saldo é registrado numa conta em USD e ainda não existe a conta `EQUITY` de reconciliação em USD
- **THEN** o sistema garante a existência dessa conta, naquela moeda, antes de persistir a operação

#### Scenario: Conversão referencia conta de conversão
- **WHEN** uma transação que atravessa moedas é registrada e ainda não existem as contas de conversão das moedas envolvidas
- **THEN** o sistema garante a existência de uma conta `CONVERSION` **por moeda envolvida** antes de persistir a operação

#### Scenario: Duas contas de sistema da mesma natureza
- **WHEN** o plano de contas de uma moeda com ajustes e câmbio é inspecionado
- **THEN** existem uma conta `EQUITY` de reconciliação e uma conta `CONVERSION`, distinguidas pela tripla que as identifica

#### Scenario: Conta de sistema não é renderizada nem oferecida
- **WHEN** uma transação com perna em conta de conversão é exibida, ou um seletor de conta é aberto
- **THEN** o nome da conta de conversão não aparece em nenhuma superfície e ela não é oferecida como escolha

#### Scenario: Lançamento sem categoria não cria conta
- **WHEN** uma despesa sem categoria é registrada
- **THEN** a contrapartida posta na conta nominal `EXPENSE` da moeda da conta, sem dimensão, e nenhuma conta de "sem categoria" existe no plano

#### Scenario: Sem conta de saldo inicial
- **WHEN** o plano de contas é inspecionado após a migração
- **THEN** não existe conta de sistema de "saldo inicial", pois nenhuma operação a referencia

### Requirement: A migração reetiqueta as contas legadas para a moeda que o usuário já lia

Na primeira execução após a atualização, quando a moeda que o dispositivo nomeia diferir da moeda padrão legada e satisfizer a premissa de duas casas decimais, o sistema SHALL **reetiquetar** as contas existentes para aquela moeda.

Quem decide SHALL ser **a mesma leitura que sempre produziu o símbolo na tela** — o locale do dispositivo, que é o que `NumberFormat.getCurrencyInstance()` e `NSLocale.currentLocale` consultavam dentro do formatador. Isso não é uma inferência sobre **onde** o usuário está, e MUST NOT ser substituído por uma: um sinal de lugar — operadora, rede, região do sistema — é um segundo palpite sobre uma pergunta que o primeiro já respondeu, e por isso pode discordar dele. Num aparelho em que a rede diz uma coisa e o locale diz outra, o que foi renderizado sempre foi o símbolo do locale; reetiquetar por qualquer outro sinal é a única forma de **mudar** o que o usuário vê, que é justamente o que esta regra existe para evitar.

Segue disso que um usuário que apenas **lê** a interface noutro idioma é reetiquetado, e esse é o resultado correto: numa plataforma sem idioma sem país, escolher *English (United States)* é `en-US`, e esse usuário vem lendo `$` sobre os seus reais desde sempre. Manter a denominação legada é a opção que **muda** a tela dele; reetiquetar é a que não muda. O que nenhum sinal do dispositivo recupera é a **intenção** por trás dos números, e o locale de hoje não relata o de ontem: o falso positivo residual SHALL ser declarado e é estreito — quem trocou o idioma da interface pouco antes de atualizar é reetiquetado para a moeda que passou a ler quando trocou.

O silêncio SHALL ser resposta: um locale para o qual a plataforma não nomeia moeda alguma MUST NOT disparar reetiquetagem, e não há leitura mais fraca em que recair nem moeda de último recurso em que aterrissar.

As contas existentes antes desta mudança estão denominadas na moeda padrão que o modelo aplicava, e essa denominação **nunca foi fato visível ao usuário**: a formatação sempre usou o locale do dispositivo, de modo que um usuário de locale estrangeiro sempre leu o símbolo dele sobre um dado que dizia outra coisa. Reetiquetar faz o dado dizer o que aquele usuário sempre leu. Reetiquetar SHALL alterar apenas a denominação: **nenhum valor e nenhum saldo** MUST ser alterado, e a denominação de uma conta e a das suas entries SHALL mudar **junta, na mesma transação**. A invariante de soma zero por moeda SHALL continuar satisfeita, porque a moeda de todas as linhas envolvidas muda junto. Reetiquetar a conta sem reetiquetar as suas entries partiria a história daquela conta em duas moedas, e tornaria a verificação da invariante — que lê apenas as entries — incapaz de ser lida como verdade sobre a conta.

A reetiquetagem SHALL ocorrer **uma única vez** e SHALL registrar que ocorreu. O registro SHALL ser a própria versão do esquema, e não um sinalizador próprio: a reetiquetagem acontece dentro da migração que introduz esta mudança, que por construção não roda duas vezes. Uma alteração posterior do locale do dispositivo MUST NOT dispará-la novamente.

A moeda-alvo SHALL ser fornecida à migração já resolvida e já validada contra a premissa. A camada de persistência MUST NOT conhecer locale nem premissa alguma — ela recebe um código de moeda, ou a ausência dele, que significa "não reetiquetar".

Isso MUST NOT ser lido como exceção à imutabilidade da moeda de uma conta: a reetiquetagem é migração, e acontece antes de aquela denominação ser observável. Após ela, a imutabilidade vale sem exceção — pelo mesmo princípio que o sistema já aplica a dado migrado, que obedece às mesmas regras que o novo mesmo quando a migração produziu o que o runtime não produz.

Consequência aceita: um usuário cuja moeda real seja a legada mas que vinha lendo o símbolo de outra terá as contas reetiquetadas sem aviso, e o sistema MUST NOT oferecer caminho para desfazê-lo. É a mesma tela que ele já via, com o dado passando a concordar com ela.

#### Scenario: Usuário que lia outro símbolo tem as contas reetiquetadas
- **WHEN** um banco existente inteiramente na moeda legada é aberto pela primeira vez após a atualização, num dispositivo cujo locale nomeia dólar
- **THEN** as contas passam a ser denominadas em dólar, nenhum valor muda, e as figuras seguem exibindo o mesmo símbolo que sempre exibiram

#### Scenario: Usuário que já lia a moeda legada não é tocado
- **WHEN** o mesmo banco é aberto num dispositivo cujo locale nomeia a moeda legada
- **THEN** nenhuma reetiquetagem ocorre

#### Scenario: Ler a interface noutro idioma reetiqueta, e a tela não muda
- **WHEN** o dispositivo tem a interface noutro idioma, e o locale correspondente nomeia outra moeda
- **THEN** a reetiquetagem ocorre para aquela moeda, porque é a que o usuário vinha lendo sobre todos os seus valores

#### Scenario: Dispositivo que não nomeia moeda não dispara
- **WHEN** a plataforma não nomeia moeda alguma para o locale do dispositivo
- **THEN** nenhuma reetiquetagem ocorre, e nenhuma outra leitura MUST ser usada como substituto

#### Scenario: Moeda fora da premissa de duas casas não dispara
- **WHEN** o locale do dispositivo nomeia uma moeda de zero ou três casas decimais
- **THEN** nenhuma reetiquetagem ocorre e as contas permanecem na moeda legada

#### Scenario: Reetiquetagem não se repete
- **WHEN** o usuário troca o locale do dispositivo depois de a reetiquetagem já ter ocorrido
- **THEN** nada é reetiquetado, e a moeda das contas permanece a que ficou

#### Scenario: Reetiquetar preserva o balanceamento
- **WHEN** a reetiquetagem ocorre sobre um banco com transações
- **THEN** toda transação continua somando zero em cada moeda presente

### Requirement: A moeda de uma conta é fixada na criação e nunca muda

A moeda de uma `Account` SHALL ser escolhida na sua criação e MUST NOT ser alterada depois, em nenhuma circunstância. O sistema MUST NOT oferecer a troca, e o domínio SHALL recusá-la **sem consultar condição alguma** — nem a existência de lançamentos, nem qualquer outro estado.

A moeda SHALL ser atributo de identidade da linha do plano de contas, no mesmo grau que o seu tipo. Trocá-la não reinterpreta um dado: reescreve em silêncio o significado de toda entry já gravada, que passa a valer outra coisa sem que nada registre o que valia antes.

Corrigir uma moeda escolhida por engano SHALL usar o caminho que já existe, sem mecanismo próprio: uma conta sem lançamento algum pode ser removida, então apagar e recriar é a correção. Uma conta que já tem lançamentos MUST NOT ter correção alguma, porque o significado das entries gravadas depende da moeda que elas assumiram.

A `Account` MUST NOT ser construível sem que a sua moeda seja decidida: o modelo MUST NOT prover valor padrão para ela, de modo que omiti-la seja impossível em vez de recair num padrão silencioso.

O controle que apresenta a moeda SHALL estar sempre presente no formulário, e MUST NOT depender de o app já possuir mais de uma moeda: a moeda é atributo da conta como o seu nome e o seu ícone são, e um formulário que muda de forma conforme o estado global do app esconde a decisão de quem ainda não a tomou. Ele SHALL ser seletor no formulário de **criação** e estado travado no de **edição**, decidido pelo modo do formulário e não pelo estado da conta.

#### Scenario: Criação oferece a escolha
- **WHEN** o usuário cria uma conta
- **THEN** a moeda é escolhível, pré-selecionada com a moeda base

#### Scenario: Edição nunca oferece a troca
- **WHEN** o usuário edita uma conta, com ou sem lançamentos
- **THEN** a moeda é apresentada travada, e nenhuma interação a altera

#### Scenario: O domínio recusa sem condição
- **WHEN** uma atualização de conta que altere a moeda é submetida ao domínio
- **THEN** ela é recusada, sem que a existência de lançamentos seja consultada

#### Scenario: Correção de conta não usada é apagar e recriar
- **WHEN** o usuário percebe que escolheu a moeda errada numa conta que ainda não tem lançamentos
- **THEN** ele a remove pela ação que o sistema já oferece e cria outra, sem que exista caminho de edição de moeda

#### Scenario: Cartão segue a mesma regra
- **WHEN** o usuário edita um cartão
- **THEN** a moeda da sua conta `LIABILITY` é apresentada travada, pela mesma regra

#### Scenario: Conta sem moeda é inexprimível
- **WHEN** o modelo de conta é inspecionado
- **THEN** a moeda não possui valor padrão, e nenhuma conta é construível sem que ela seja informada

#### Scenario: O controle existe mesmo com uma moeda só
- **WHEN** o usuário abre o formulário de conta num app cujas contas estão todas na mesma moeda
- **THEN** o controle de moeda está presente, exibindo a moeda corrente
