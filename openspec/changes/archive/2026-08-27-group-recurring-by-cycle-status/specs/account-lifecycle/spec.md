## MODIFIED Requirements

### Requirement: Recorrência arquivada pode ser desarquivada

Uma recorrência arquivada SHALL poder voltar à circulação por uma operação de **desarquivamento**, com use case próprio, simétrica ao arquivamento: o flag de arquivamento da fachada é revertido e nada mais muda — os lançamentos gerados e as ocorrências já existiam antes do arquivamento, que nada tocou além do flag.

O desarquivamento MUST NOT ser recusado por invariante alguma. Ele SHALL, porém, ser **confirmado pelo usuário**, divergindo de conta, cartão e categoria, cujo desarquivamento é ação direta. A divergência não é de regra, é da fachada: naquelas três, voltar à circulação é recuperar visibilidade; numa recorrência é voltar a **gerar**. A confirmação existe para anunciar o alcance dessa retomada — e sobretudo o que ela **não** faz.

A inocuidade SHALL ser entendida como **nada desfazer**, não como repor o intervalo: os ciclos que decorreram durante o arquivamento não foram gerados e o desarquivamento MUST NOT gerá-los retroativamente — a geração retoma do ciclo corrente. Nisto o desarquivamento de recorrência difere do de conta e cartão, que reabrem um estado garantidamente de saldo zero e por isso nada deixam para trás. A confirmação SHALL dizer as duas coisas: que a geração retoma, e que o intervalo não volta.

Uma vez desarquivada, a recorrência SHALL voltar a ser apresentada como pendente quando for o caso, a reaparecer nas listagens ativas, e a ser oferecida como receita base de orçamento.

Uma recorrência arquivada SHALL permanecer **acessível** por um destino próprio na feature, de onde possa ser visualizada e desarquivada. Não é um recorte da lista mensal: uma recorrência arquivada não gera ciclo em mês algum, logo não tem estado de ciclo e não cabe em seção alguma dela (`recurring-archive`). Torná-la visível ali MUST NOT reintroduzi-la nas listagens ativas, nas pendências nem em seletor algum.

A oferta de desarquivar e a de retirar SHALL ser mutuamente exclusivas pelo estado de arquivamento, decisão de apresentação com o mesmo dono único.

#### Scenario: Desarquivar uma recorrência arquivada
- **WHEN** o usuário desarquiva uma recorrência que estava arquivada
- **THEN** o flag é revertido, ela volta a ser apresentada como pendente quando for o caso, reaparece nas listagens ativas e nos seletores, e os seus lançamentos e ocorrências permanecem intactos

#### Scenario: Desarquivar não repõe os ciclos decorridos
- **WHEN** o usuário desarquiva uma recorrência que passou meses arquivada
- **THEN** os ciclos daquele intervalo não são gerados retroativamente, e a geração retoma a partir do ciclo corrente

#### Scenario: Desarquivar é confirmado, e a confirmação diz o que não volta
- **WHEN** o usuário aciona o desarquivar de uma recorrência arquivada
- **THEN** a interface pede confirmação, dizendo que a geração retoma a partir do ciclo corrente e que os ciclos decorridos durante o arquivamento não são repostos

#### Scenario: Desarquivar não é recusado por invariante
- **WHEN** o desarquivamento de uma recorrência arquivada é solicitado ao domínio
- **THEN** a operação é executada, sem recusa por invariante alguma

#### Scenario: A visualização de uma recorrência arquivada oferece apenas desarquivar
- **WHEN** o usuário abre a visualização de uma recorrência arquivada
- **THEN** entre as ofertas de retirada a interface apresenta apenas desarquivar, e não oferece arquivar nem apagar

#### Scenario: Arquivada é alcançável sem voltar às listagens ativas
- **WHEN** o usuário abre o destino de arquivadas a partir da tela de recorrências
- **THEN** as recorrências arquivadas são listadas e podem ser abertas, sem que passem a aparecer nos recortes ativos nem nas pendências
