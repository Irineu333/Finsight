## MODIFIED Requirements

### Requirement: Modelos de UI sem grafo de domínio

Um modelo de apresentação SHALL conter apenas valores já resolvidos para exibição (textos,
valores monetários com sinal de exibição, rótulos, ids). Um modelo de apresentação MUST NOT
conter modelo de domínio como campo — nem agregado, nem entidade, nem coleção deles —
carregando no máximo o identificador do domínio que representa. Um modelo de apresentação
MUST NOT executar cálculo de domínio (soma, filtro, derivação de saldo) em construtor, `init`
ou propriedade.

A regra governa **toda superfície de apresentação**, e não apenas a tela. A UI é a primeira
instância; a superfície de ferramentas que responde a um agente é a segunda, e o consumidor
ser um programa em vez de uma pessoa MUST NOT afrouxar nenhuma das restrições acima — afrouxa
menos, porque um agente que recebe um agregado de domínio o interpretará por conta própria,
enquanto uma tela apenas deixaria de compilar.

Cada superfície SHALL ter os seus próprios modelos de apresentação, porque o que uma resolve
para exibição a outra não usa — um ícone e uma cor de tema não significam nada para um agente,
e um nome de conta por extenso é obrigatório para ele e redundante numa tela que já o mostra
no cabeçalho. Modelos distintos MUST NOT implicar decisões distintas: as duas superfícies
consomem os mesmos donos de derivação.

#### Scenario: Modelo de UI de transação
- **WHEN** a UI exibe uma transação em uma lista
- **THEN** o modelo de UI expõe id, rótulo, valor de exibição, data e categoria como valores planos, sem referenciar o agregado de domínio

#### Scenario: Modelo de UI de conta
- **WHEN** a UI exibe uma conta com seus totais do período
- **THEN** o modelo de UI expõe os totais como valores já calculados, sem receber lançamentos nem computá-los

#### Scenario: Ação da UI sobre um item
- **WHEN** o usuário aciona uma ação sobre um item exibido
- **THEN** a UI a identifica pelo id, e o domínio correspondente é resolvido fora do modelo de UI

#### Scenario: Modelo de apresentação de uma superfície não visual
- **WHEN** uma superfície que responde a um agente devolve uma transação
- **THEN** o modelo devolvido expõe valores planos e no máximo o identificador, sem carregar o agregado de domínio

#### Scenario: Superfície não visual não calcula
- **WHEN** uma superfície que responde a um agente devolve uma lista e o seu total
- **THEN** o total provém de uma leitura do domínio, e não de uma soma feita no modelo de apresentação

#### Scenario: Duas superfícies, uma derivação
- **WHEN** a mesma transação é apresentada na tela e devolvida a um agente
- **THEN** o rótulo, a perna lida e o sinal vêm dos mesmos donos de derivação, ainda que os dois modelos de apresentação sejam distintos

## ADDED Requirements

### Requirement: Um mapper por superfície, uma decisão por regra

Quando existir mais de uma superfície de apresentação, cada uma SHALL ter o seu próprio
mapper, e todos SHALL consumir as mesmas definições de domínio para as decisões que
apresentam — derivação de rótulo, escolha da perna lida, resolução de perspectiva, inversão de
sinal por `AccountType` e escolha da ponta que denomina uma figura cruzada.

Um mapper MUST NOT re-derivar por conta própria uma decisão que já tem dono, ainda que a sua
superfície seja nova. Duas derivações da mesma escolha podem divergir sem que nada falhe, e a
divergência entre uma tela e um agente é a mais difícil de perceber: ninguém as olha lado a
lado.

#### Scenario: Rótulo concorda entre superfícies
- **WHEN** a mesma transação é rotulada por duas superfícies de apresentação
- **THEN** o rótulo é o mesmo, por virem ambas da derivação do razão

#### Scenario: Mapper novo não reimplementa a perna primária
- **WHEN** o mapper de uma superfície nova precisa ler uma transação sem perspectiva
- **THEN** ele consome a definição de perna primária existente, sem reimplementar o critério

#### Scenario: Figura cruzada concorda entre superfícies
- **WHEN** uma operação que atravessa moedas é apresentada em duas superfícies como uma figura única
- **THEN** ambas exibem a mesma ponta, pela mesma definição de qual delas denomina a figura
