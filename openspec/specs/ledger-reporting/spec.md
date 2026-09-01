# ledger-reporting Specification

## Purpose

Toda leitura de dinheiro — saldo de conta, saldo de abertura do período, saldo devido de fatura, gasto por categoria e patrimônio líquido — deriva de um mecanismo único: `Σ entries` da conta. Não há regra de sinal específica por tipo de lançamento, nem cálculo alternativo por tela: o sinal de exibição vem do `AccountType`, e cada agregado tem um só dono. Consome o razão (`balanced-ledger`) sobre o plano de contas (`chart-of-accounts`).
## Requirements
### Requirement: Saldo de conta a partir das entries
O saldo de qualquer conta SHALL ser calculado exclusivamente como a soma dos `amount` das entries que a referenciam, aplicando a convenção débito-positivo, sem funções de sinal específicas por tipo de lançamento. O cálculo de saldo MUST NOT depender de nenhuma função de sinal derivada de um modelo legado, nem de qualquer regra de sinal invertida específica de cartão.

SHALL existir **uma única** implementação do cálculo de saldo. MUST NOT existir uma forma alternativa que some lançamentos já carregados em memória, nem qualquer recálculo de saldo em modelo de UI ou componente de tela.

O saldo devido de uma fatura SHALL ser derivado pelo mesmo mecanismo, como a soma das entries que carregam a dimensão daquela fatura, sem consultar tabela de fachada.

O corte temporal do saldo SHALL usar a data da transação como única referência, e essa invariante SHALL permanecer verificada por teste, de modo que um consumidor futuro não a quebre em silêncio.

A leitura **escalar** do saldo de uma conta SHALL admitir corte por **data**, com a resolução que a data da transação já possui. O corte por dia é a leitura real; MUST NOT existir uma segunda consulta que produza o acumulado da mesma conta com resolução diferente.

O acumulado de uma conta **até um mês** SHALL ser derivado dessa leitura, como o acumulado até o último dia daquele mês. Ele MUST NOT ter implementação própria, porque não é outro número: é o mesmo número perguntado com menos precisão.

A resolução por dia SHALL permanecer restrita à leitura escalar por conta. As leituras que atravessam contas — expressas por moeda — MUST NOT ser alteradas por esta regra enquanto nenhum consumidor delas perguntar por dia, e a assimetria SHALL ser deliberada e registrada, não presumida.

#### Scenario: Saldo de conta corrente
- **WHEN** o saldo de uma conta `ASSET` é solicitado
- **THEN** o sistema retorna a soma dos `amount` das entries daquela conta até a data-alvo

#### Scenario: Saldo de fatura sem sinal invertido ad-hoc
- **WHEN** o saldo devido de uma fatura de cartão é solicitado
- **THEN** o sistema o deriva da soma das entries que carregam a dimensão daquela fatura, sem aplicar um sinal invertido especial

#### Scenario: Sem cálculo de saldo em memória
- **WHEN** uma tela precisa do saldo de uma conta
- **THEN** ela o obtém do razão, e MUST NOT somá-lo a partir de uma lista de lançamentos já carregada

#### Scenario: Data do corte é inequívoca
- **WHEN** uma transação é persistida
- **THEN** a data que governa o corte de período é única para a transação e suas entries, sem possibilidade de divergência entre elas

#### Scenario: Saldo até um dia do mês
- **WHEN** o saldo de uma conta até o dia 10 de um mês é solicitado, e existem lançamentos no dia 5 e no dia 20 daquele mês
- **THEN** o resultado inclui o lançamento do dia 5 e exclui o do dia 20

#### Scenario: O acumulado até um mês deriva do acumulado até uma data
- **WHEN** o saldo de uma conta até um mês é solicitado
- **THEN** o valor é o saldo daquela conta até o último dia daquele mês, obtido pela mesma consulta, e não por uma segunda com corte mensal próprio

#### Scenario: Leituras por moeda permanecem mensais
- **WHEN** o saldo acumulado que atravessa contas é solicitado
- **THEN** ele continua expresso por moeda e cortado por mês, sem ganhar resolução por dia

### Requirement: Gasto por categoria a partir das entries
O gasto (ou receita) de uma categoria em um período SHALL ser derivado da soma das entries que carregam a **dimensão** daquela categoria, usando o mesmo mecanismo de soma do saldo de conta. Não SHALL existir um caminho de cálculo separado para gasto por categoria.

O total das entries **sem dimensão** na conta nominal SHALL ser o total "sem categoria", derivado pelo mesmo mecanismo e sem tratamento especial. A leitura MUST NOT depender de conta dedicada para representar a ausência de classificação.

A assinatura dessa leitura no razão SHALL ser expressa em vocabulário de razão — natureza de conta, período e dimensão — e MUST NOT nomear categoria. A tradução para o vocabulário de categoria pertence à feature dona da fachada.

O razão SHALL oferecer essa leitura na forma **agregada**: um total por dimensão para uma natureza
de conta nominal em um mês, com a ausência de dimensão como uma chave do mesmo agregado. Um
detalhamento de N categorias SHALL custar uma leitura, não N, e o total sem classificação SHALL ser
um grupo desse agregado — nunca uma leitura à parte, que poderia divergir do resto.

O filtro por natureza de conta nominal SHALL ser obrigatório nessa leitura: sem ele, a ausência de
dimensão alcançaria toda perna não classificada do razão — de ativo, de passivo, de conversão — e o
total deixaria de ser um total de classificação.

#### Scenario: Total gasto em uma categoria
- **WHEN** o total gasto na categoria "Alimentação" em um mês é solicitado
- **THEN** o sistema retorna a soma das entries que carregam a dimensão de "Alimentação" naquele período

#### Scenario: Total sem categoria
- **WHEN** o total de despesas sem categoria em um mês é solicitado
- **THEN** o sistema retorna a soma das entries sem dimensão na conta nominal `EXPENSE` naquele período, pelo mesmo mecanismo

#### Scenario: Reembolso reduz o gasto da categoria
- **WHEN** existe uma entry de crédito carregando a dimensão de "Alimentação" (contrapartida de um reembolso)
- **THEN** o total da categoria é reduzido por essa entry, sem tratamento especial

#### Scenario: Um mês inteiro em uma leitura
- **WHEN** os totais por dimensão do nominal `EXPENSE` de um mês são solicitados
- **THEN** o sistema retorna um total por dimensão e um total para a ausência de dimensão, tudo num
  único agregado por moeda

#### Scenario: Compra de cartão sem categoria conta como sem categoria
- **WHEN** uma compra no cartão é registrada sem categoria
- **THEN** a sua perna nominal `EXPENSE`, que não carrega dimensão porque a dimensão da fatura pousa
  na perna `LIABILITY`, entra no total sem classificação do mês

#### Scenario: O resíduo de conversão fica de fora
- **WHEN** uma transação entre moedas deixa resíduo na conta `CONVERSION`, sem dimensão
- **THEN** esse resíduo não entra no total sem classificação, porque `CONVERSION` não é natureza
  nominal

#### Scenario: Perna de ativo sem dimensão fica de fora
- **WHEN** uma despesa sem categoria é paga de uma conta corrente
- **THEN** a perna `ASSET`, que também não carrega dimensão, é contada uma única vez pelo lado
  nominal e não duplica o total sem classificação

### Requirement: Patrimônio líquido a partir das entries
O patrimônio líquido SHALL ser derivado do plano de contas como a soma dos saldos das contas `ASSET` menos a soma dos saldos das contas `LIABILITY`, usando o mesmo mecanismo de saldo das demais leituras.

As contas de conversão MUST NOT entrar no patrimônio líquido. O resultado cambial já se manifesta nos saldos das próprias contas do usuário quando expressos numa mesma moeda: uma transferência de R$ 550 para US$ 100 deixa `−550 BRL` e `+100 USD`, que consolidam a zero à taxa aplicada e passam a consolidar em ganho quando a taxa se move. Incluir as contas de conversão o contaria duas vezes.

Por atravessar contas de moedas possivelmente distintas, essa leitura SHALL ser expressa **por moeda**, e MUST NOT ser reduzida pelo razão a um único número. Reduzi-la à moeda base é conversão, e pertence à camada de consolidação (`currency-consolidation`).

#### Scenario: Patrimônio líquido em uma moeda
- **WHEN** o patrimônio líquido é solicitado e todas as contas estão na mesma moeda
- **THEN** o sistema retorna um saldo naquela moeda, igual à soma dos saldos `ASSET` menos os `LIABILITY`

#### Scenario: Patrimônio líquido em várias moedas
- **WHEN** o patrimônio líquido é solicitado e existem contas em duas moedas
- **THEN** o sistema retorna o saldo de cada moeda separadamente, sem somá-los e sem aplicar taxa alguma

#### Scenario: Conversão fora do patrimônio
- **WHEN** o patrimônio líquido é solicitado após operações que atravessaram moedas
- **THEN** os saldos das contas de conversão não participam do resultado

### Requirement: Ajuste sem tratamento especial em relatórios
Os relatórios e leituras MUST NOT tratar ajustes como um caso especial. A contrapartida de reconciliação de um ajuste SHALL ser uma conta `EQUITY` como qualquer outra no plano de contas, entrando nas leituras pelo mesmo mecanismo de soma de entries.

#### Scenario: Ajuste entra pelo mecanismo comum
- **WHEN** um relatório de saldo é computado sobre contas que incluem lançamentos de ajuste
- **THEN** os ajustes contribuem via suas entries normais, sem ramo condicional específico para o tipo ajuste

### Requirement: Razão como única fonte de leitura
Toda leitura de dinheiro — saldo de conta, saldo devido de fatura, gasto por categoria, patrimônio líquido e totais de período — SHALL derivar do razão. Nenhum consumidor SHALL derivar valor monetário de um modelo de lançamento legado. O grafo de objetos exibido ao usuário SHALL igualmente derivar do razão: as telas de transações, contas, categorias, orçamentos, faturas e relatórios SHALL ler entries, e MUST NOT ler um modelo de perna paralelo.

Uma leitura escopada a uma fachada SHALL derivar da identidade com que o razão a representa — a conta, para conta e cartão; a dimensão, para categoria e fatura — e MUST NOT consultar a tabela da fachada para obter valor monetário.

#### Scenario: Tela de contas lê o razão
- **WHEN** a tela de contas exibe saldos e totais do período
- **THEN** os valores derivam do razão, e não de uma soma de lançamentos legados

#### Scenario: Orçamentos leem o razão
- **WHEN** o progresso de um orçamento por categoria é calculado
- **THEN** ele deriva das entries que carregam a dimensão daquela categoria

#### Scenario: Nenhum leitor legado remanescente
- **WHEN** o código é inspecionado
- **THEN** não existe consumidor que derive valor monetário de um modelo de lançamento legado, pois esse modelo não existe mais

### Requirement: Saldo de abertura do período
O saldo de abertura de um período SHALL ser o saldo da conta até o instante anterior ao início do período, derivado pelo **mesmo** mecanismo do saldo de conta. MUST NOT existir mais de uma implementação desse cálculo. O saldo de abertura MUST NOT ser chamado de "saldo inicial": ele é derivado do período exibido, e não representa um aporte inicial registrado.

#### Scenario: Saldo de abertura de um mês
- **WHEN** a tela de um mês exibe o saldo de abertura de uma conta
- **THEN** o valor é o saldo da conta até o fim do mês anterior, obtido pelo mecanismo comum de saldo

#### Scenario: Uma única implementação
- **WHEN** o saldo de abertura é exibido em telas distintas (conta, lista de transações, relatório)
- **THEN** todas obtêm o valor do mesmo mecanismo, e MUST NOT recalculá-lo independentemente

### Requirement: Ajuste de fatura consistente com o razão
A edição de um ajuste de saldo de fatura SHALL atualizar o razão. MUST NOT existir caminho de escrita que altere o valor de um ajuste sem atualizar as entries correspondentes, sob pena de o saldo devido exibido divergir do valor registrado.

#### Scenario: Edição de ajuste de fatura atualiza as entries
- **WHEN** o usuário altera o valor de um ajuste de saldo de uma fatura já existente
- **THEN** as entries do ajuste são reescritas, e o saldo devido exibido reflete o novo valor

### Requirement: Leituras do razão expressas em vocabulário de razão
As leituras providas pelo razão SHALL ter assinaturas expressas exclusivamente em natureza de conta, sinal, período e dimensão. Uma leitura MUST NOT nomear fatura, cartão, categoria, orçamento ou relatório na sua assinatura, mesmo quando servir exclusivamente a um deles.

Uma leitura cuja regra é derivável do razão SHALL permanecer nele, ainda que hoje carregue nome de fachada: a correção SHALL ser a renomeação, e MUST NOT ser a transferência da regra para a feature. Em particular, a classificação de fluxos pela natureza das contra-pernas de uma transação e a exclusão de transferências internas ao escopo de um relatório SHALL permanecer no razão, por serem deriváveis dele.

#### Scenario: Assinatura sem vocabulário de fachada
- **WHEN** a superfície de leitura do razão é inspecionada
- **THEN** nenhuma assinatura nomeia fatura, cartão, categoria, orçamento ou relatório

#### Scenario: Classificação por contra-perna permanece no razão
- **WHEN** uma leitura classifica fluxos pela presença de contra-perna de determinada natureza
- **THEN** ela permanece implementada no razão, e nenhuma feature reimplementa essa classificação

#### Scenario: Feature nomeia o próprio sabor
- **WHEN** uma feature precisa apresentar uma figura sob o seu vocabulário
- **THEN** ela o faz traduzindo a leitura do razão, sem duplicar o cálculo

### Requirement: Saldo até um mês por natureza de conta

O saldo acumulado até um mês SHALL ser derivável para **qualquer** natureza do plano de contas, pelo mesmo mecanismo de soma de entries, e MUST NOT existir apenas para `ASSET`.

A leitura SHALL ser expressa em vocabulário de razão — natureza de conta e mês —, e MUST NOT nomear conta corrente, cartão ou qualquer fachada. MUST NOT existir uma segunda consulta que derive o mesmo acumulado com a natureza fixada no seu próprio texto.

A leitura SHALL admitir um conjunto de **contas a excluir** da soma, expresso por identidade de conta do plano. Excluir por identidade permanece vocabulário de razão: uma conta do plano é entidade do razão, ao contrário de uma fachada. O razão MUST NOT conhecer o **motivo** da exclusão, e a leitura MUST NOT ganhar parâmetro que nomeie preferência, widget, tela ou intenção de quem a chama.

O conjunto vazio SHALL ser o padrão e SHALL produzir exatamente o mesmo resultado que a leitura produzia sem o parâmetro — toda conta daquela natureza. A exclusão MUST NOT introduzir um segundo caminho para o acumulado: ela SHALL ser honrada **dentro** da mesma consulta parametrizada, e MUST NOT ser obtida subtraindo saldos de contas individuais de um total previamente somado, nem somando conta a conta fora do razão.

O resultado da leitura com exclusão SHALL continuar expresso **por moeda**, como toda leitura que atravessa contas, e o agrupamento por moeda MUST NOT ser realizado fora do razão em consequência da exclusão.

O saldo consolidado de `ASSET` e `LIABILITY` até um mês SHALL ser obtido pela **soma** dos saldos das duas naturezas, sem agregado adicional e sem regra de sinal própria, já que passivos são registrados em crédito.

#### Scenario: Saldo acumulado de passivos
- **WHEN** o saldo acumulado das contas `LIABILITY` até um mês é solicitado
- **THEN** o sistema retorna a soma das entries dessas contas até aquele mês, pelo mesmo mecanismo usado para `ASSET`

#### Scenario: Consolidado sem agregado novo
- **WHEN** o saldo consolidado de ativos e passivos até um mês é necessário
- **THEN** ele é obtido somando as duas leituras por natureza, e MUST NOT existir uma terceira consulta dedicada a esse total

#### Scenario: Sem caminho duplicado para ativos
- **WHEN** o saldo acumulado das contas `ASSET` até um mês é solicitado
- **THEN** ele deriva da mesma leitura parametrizada, e não de uma consulta paralela

#### Scenario: Excluir nada é o comportamento de sempre
- **WHEN** o saldo acumulado de uma natureza até um mês é solicitado com conjunto de exclusão vazio
- **THEN** o resultado é idêntico, valor a valor e moeda a moeda, ao que a leitura devolvia antes de admitir o parâmetro

#### Scenario: Conta excluída não entra na soma
- **WHEN** o saldo acumulado das contas `ASSET` até um mês é solicitado excluindo uma conta com lançamentos
- **THEN** as entries dessa conta não participam do resultado, e as das demais contas `ASSET` participam integralmente

#### Scenario: Exclusão sem segunda consulta
- **WHEN** a origem do acumulado com exclusão é inspecionada
- **THEN** ele deriva da mesma consulta parametrizada que o acumulado sem exclusão, e não de uma subtração entre um total e saldos de contas individuais

#### Scenario: Exclusão preserva a expressão por moeda
- **WHEN** o saldo acumulado é solicitado com exclusão sobre contas de moedas distintas
- **THEN** o resultado continua expresso por moeda, agrupado pelo razão, e nenhuma moeda é somada com outra

#### Scenario: Id que não corresponde a conta alguma
- **WHEN** o saldo acumulado é solicitado excluindo um identificador que não corresponde a nenhuma conta do plano
- **THEN** nenhuma conta é excluída e o resultado é o mesmo do conjunto vazio

#### Scenario: O razão não conhece o motivo da exclusão
- **WHEN** a assinatura da leitura é inspecionada
- **THEN** ela recebe identidades de conta do plano, e nenhum parâmetro que nomeie preferência de exibição, widget ou fachada

### Requirement: Fluxos do mês simétricos entre naturezas

Quando o razão reportar os fluxos de um mês para uma natureza de conta, o conjunto de classes reportadas SHALL ser o mesmo entre naturezas que registrem as mesmas formas de lançamento. Em particular, o **ajuste** — a contrapartida em `EQUITY` — SHALL ser reportado para toda natureza em que possa ocorrer, e MUST NOT ser reportado para uma natureza e omitido em outra.

Este requisito rege a **simetria entre as leituras de fluxo**; ele MUST NOT ser lido como obrigação de que os fluxos reportados esgotem as entries do período, já que uma leitura pode legitimamente excluir movimento interno ao seu perímetro.

#### Scenario: Ajuste de fatura é reportado
- **WHEN** os fluxos do mês das contas `LIABILITY` são solicitados em um mês que contém ajuste de fatura
- **THEN** o ajuste é reportado como classe própria, com sinal preservado, e não desaparece do relatório

#### Scenario: Paridade de classes entre naturezas
- **WHEN** as leituras de fluxo mensal de `ASSET` e de `LIABILITY` são comparadas
- **THEN** ambas reportam ajuste como classe própria, e nenhuma forma de lançamento registrável naquela natureza fica sem classe

### Requirement: Leitura que atravessa contas é expressa por moeda

Toda leitura de dinheiro capaz de abranger contas de moedas distintas SHALL expressar o seu resultado **por moeda**. O razão MUST NOT somar valores de moedas diferentes num mesmo número, e MUST NOT reduzir um resultado multimoeda a um único valor.

Uma leitura escopada a **uma** conta é monomoeda por construção — a moeda é atributo da conta. Ela SHALL permanecer com a forma que tem hoje, acrescida apenas da moeda em que o seu resultado está denominado.

Uma leitura escopada a uma **dimensão** SHALL ser expressa por moeda, qualquer que seja o `kind` da dimensão. O razão MUST NOT consultar o `kind` para decidir a forma do resultado: nada no razão amarra uma dimensão a uma única conta, e que a dimensão de uma fatura recaia sempre sobre um único cartão é garantia da **fachada** de cartão, não construção do razão. Uma feature que saiba que o seu resultado tem sempre uma moeda MAY tratá-lo assim; o razão MUST NOT presumi-lo.

#### Scenario: Saldo de uma conta
- **WHEN** o saldo de uma conta é solicitado
- **THEN** o resultado é um valor único, denominado na moeda daquela conta

#### Scenario: Saldo devido de uma fatura
- **WHEN** o saldo devido de uma fatura é solicitado ao razão
- **THEN** o resultado é expresso por moeda, e a feature de cartões o consome sabendo que contém uma única moeda

#### Scenario: Gasto de uma categoria com lançamentos em duas moedas
- **WHEN** o gasto de uma categoria é solicitado e ela tem lançamentos em BRL e em USD
- **THEN** o sistema retorna o total de cada moeda separadamente

#### Scenario: Totais do mês por natureza de conta
- **WHEN** os totais de receita e despesa do mês são solicitados sobre todas as contas
- **THEN** o resultado é expresso por moeda, e nenhuma soma cruza moedas

#### Scenario: Razão não ramifica por tipo de dimensão
- **WHEN** as leituras por dimensão são inspecionadas
- **THEN** nenhuma delas consulta o `kind` da dimensão para decidir a forma do seu resultado

#### Scenario: Nenhuma agregação soma moedas
- **WHEN** as agregações do razão são inspecionadas
- **THEN** nenhuma delas soma valores sem separá-los por moeda

### Requirement: O razão não converte e não conhece moeda base

O razão MUST NOT conhecer taxa de câmbio, moeda base, nem qualquer preferência de exibição de moeda. Nenhuma leitura do razão SHALL consultar uma taxa, e nenhuma dependência que forneça taxa SHALL ser injetada nele.

A frase que o razão sustenta — toda figura é `Σ entries` — SHALL permanecer literalmente verdadeira. Uma leitura que multiplicasse entries por uma taxa deixaria de sê-lo, e é por isso que a conversão vive acima do razão e não dentro dele.

A moeda que uma conta nova recebe quando nenhuma é escolhida MAY continuar sendo um valor conhecido do razão; ela é um padrão de criação, e MUST NOT ser confundida com a moeda base do usuário, que é preferência de exibição e pertence a `currency-consolidation`.

#### Scenario: Razão sem dependência de taxa
- **WHEN** as dependências do razão são inspecionadas
- **THEN** nenhuma delas fornece taxa de câmbio ou moeda base

#### Scenario: Toda figura continua sendo soma de entries
- **WHEN** qualquer leitura de dinheiro do razão é inspecionada
- **THEN** o seu resultado é uma soma de `amount` de entries, sem fator de conversão

#### Scenario: Trocar a moeda base não altera o razão
- **WHEN** o usuário troca a sua moeda base
- **THEN** nenhuma entry, conta ou leitura do razão muda; apenas as figuras consolidadas passam a ser expressas na nova base

### Requirement: A soma de saldos por moeda tem dono no razão

Combinar dois resultados por moeda num terceiro — somar os saldos de perímetros disjuntos, por exemplo — SHALL ter exatamente uma implementação, e ela SHALL pertencer ao razão, que é o dono de quanto uma figura vale.

Essa operação MUST NOT ser atribuída à camada de consolidação, que responde apenas pela conversão entre moedas, nem ao tipo de exibição, que MUST NOT combinar dois valores (`money-display`). Sem dono explícito, um consumidor que precise da soma de dois perímetros a implementaria em linha, que é exatamente a reimplementação de regra derivável que o razão proíbe.

#### Scenario: Perímetro neutro soma naturezas disjuntas
- **WHEN** um resumo precisa do total de duas naturezas de conta disjuntas, cada uma lida por moeda
- **THEN** ele obtém a soma da única implementação do razão, sem somar mapas em linha

#### Scenario: Soma respeita a separação por moeda
- **WHEN** dois resultados por moeda são somados
- **THEN** cada moeda é somada com a sua própria, e nenhuma conversão participa da operação

### Requirement: Série mensal de uma dimensão a partir das entries

O razão SHALL oferecer a leitura da **série mensal** de uma dimensão: o total das entries
que a carregam, agrupado por mês e por moeda, em **uma única consulta**. Uma janela de N
meses SHALL custar uma leitura, não N.

A leitura SHALL derivar do mesmo mecanismo de soma que todas as demais — `Σ amount` das
entries, na convenção débito-positivo — e MUST NOT ter caminho de cálculo próprio. Ela é o
mesmo agregado do gasto por dimensão num mês, apenas agrupado também pelo mês em vez de
filtrado por um.

A assinatura SHALL ser expressa em vocabulário de razão — dimensão e mês — e MUST NOT
nomear categoria, orçamento ou fatura. Traduzir a fachada para a identidade da dimensão
pertence a quem é dono da fachada.

A leitura SHALL aceitar um **corte superior** por mês, e SHALL devolver apenas os meses até
ele, inclusive. O corte é parâmetro de quem chama, como já é no acumulado escalar de uma
conta: o razão MUST NOT decidir período, e MUST NOT conhecer a noção de "mês corrente" nem
consultar relógio algum.

O corte existe porque entries com data futura são um estado ordinário do razão — uma compra
parcelada as produz — e uma leitura que as trouxesse sem que o chamador pudesse dizer até
onde quer ler deixaria cada consumidor livre para filtrar por conta própria, ou esquecer.

Um mês sem nenhuma entry da dimensão MUST NOT aparecer na resposta como total zero: um
agregado agrupado não tem linha vazia, e a ausência de linha é a resposta honesta a "não
houve movimento". Quem precisa de zeros na janela os supre acima do razão, onde a janela é
decidida.

A série SHALL ser expressa **por moeda**, como toda leitura do razão que atravessa contas.
Uma dimensão não tem moeda própria, e o razão MUST NOT reduzir duas moedas a um número.

#### Scenario: Uma leitura cobre a janela inteira
- **WHEN** a série mensal de uma dimensão é solicitada
- **THEN** o sistema a obtém em uma consulta agrupada por mês e moeda, e MUST NOT emitir uma
  consulta por mês da janela

#### Scenario: Mês sem movimento não vira linha
- **WHEN** a dimensão não tem nenhuma entry num mês do período coberto
- **THEN** esse mês não aparece na resposta, em vez de aparecer com total zero

#### Scenario: Dimensão em mais de uma moeda
- **WHEN** a dimensão carrega entries em duas moedas dentro do mesmo mês
- **THEN** a resposta traz uma linha por moeda naquele mês, e nenhuma soma entre elas é feita
  pelo razão

#### Scenario: Vocabulário de razão
- **WHEN** a leitura é declarada na interface do razão
- **THEN** ela nomeia dimensão e mês, e MUST NOT nomear categoria

#### Scenario: Mesmo número por dois caminhos
- **WHEN** o total de uma dimensão num mês é obtido pela série mensal e pela leitura daquele
  mês isolado
- **THEN** os dois resultados coincidem, porque derivam do mesmo agregado

#### Scenario: O corte superior exclui os meses posteriores
- **WHEN** a série é solicitada com corte num mês, e a dimensão tem entries em meses
  posteriores a ele
- **THEN** a resposta traz os meses até o corte, inclusive, e nenhum posterior

#### Scenario: O razão não conhece "hoje"
- **WHEN** a leitura da série é declarada
- **THEN** ela recebe o corte como parâmetro, e MUST NOT derivá-lo de relógio nem presumir o
  mês corrente
