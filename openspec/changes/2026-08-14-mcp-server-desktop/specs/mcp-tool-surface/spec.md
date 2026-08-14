## ADDED Requirements

### Requirement: Toda tool delega ao domínio, e nenhuma tool é uma regra

Cada tool SHALL delegar a casos de uso ou repositórios já existentes no domínio. Uma tool
MUST NOT decidir *qual* regra se aplica, MUST NOT compor um resultado que o domínio saberia
compor, e MUST NOT alcançar o razão diretamente quando existe caso de uso que governa aquela
escrita.

Atender vários itens numa chamada é composição, não regra: uma tool em lote SHALL invocar o
mesmo caso de uso por item, e MUST NOT ganhar comportamento que a operação unitária não tenha.

Uma tool pode legitimamente decidir **se** oferece uma operação — como qualquer consumidor —,
nunca reimplementá-la.

#### Scenario: Verbo agregador não decide regra
- **WHEN** um agente registra uma despesa em cartão de crédito
- **THEN** em qual fatura ela cai é resolvido pelo caso de uso que já é dono dessa regra, e
  não por lógica da camada de tools

#### Scenario: Escrita sempre passa pelo caso de uso
- **WHEN** qualquer tool de escrita é inspecionada
- **THEN** ela chama o caso de uso correspondente, e não a fronteira de escrita do razão

#### Scenario: Lote não inventa comportamento
- **WHEN** uma tool em lote processa um item
- **THEN** o resultado é o mesmo que a operação unitária produziria para aquele item

### Requirement: A superfície é deliberada, e a lacuna que ela revela é preenchida no domínio

A superfície de tools SHALL cobrir o que o usuário consegue fazer nas telas do app, excluindo o
que depende de julgamento visual. Ela MUST NOT ser gerada automaticamente a partir de todos os
casos de uso existentes.

Cada caso de uso alcançado por uma tool SHALL ser promovido de `impl` para `api` da sua feature,
aplicando o critério de triagem já vigente — só entra na `api` o que outro módulo consome.

Quando o comportamento que uma tool precisa **não existe** como caso de uso, ele SHALL ser
criado no domínio, com dono único, e não improvisado na camada de tools. A lacuna é achado sobre
o domínio, não licença para o servidor compor regra: o que ela revela é que aquela regra nunca
teve dono, e o consumidor novo apenas a expôs.

O caso de uso novo SHALL passar a ser o único dono daquele comportamento. Se a mesma regra já
existia embutida numa tela, num ViewModel ou num repositório chamado direto, essa cópia SHALL
ser substituída pelo caso de uso novo. MUST NOT existir caso de uso que só o servidor MCP usa
enquanto a interface continua fazendo o mesmo por outro caminho — seriam dois donos da mesma
regra, e o segundo é um domínio paralelo para agentes.

Ampliar uma leitura existente para atender a superfície — uma variante não reativa, um recorte
por período onde só havia por dia — SHALL acontecer no módulo que já é dono daquela leitura, e
MUST NOT ser compensado filtrando ou agregando o resultado fora dele.

#### Scenario: Promoção segue o critério existente
- **WHEN** uma tool precisa de um caso de uso que hoje vive no `impl`
- **THEN** o caso de uso é promovido para a `api` da feature, e nenhum módulo passa a depender
  de `impl` para alcançá-lo

#### Scenario: Caso de uso não alcançado permanece interno
- **WHEN** um caso de uso não é consumido por nenhuma tool nem por outra feature
- **THEN** ele permanece no `impl`

#### Scenario: Comportamento sem caso de uso
- **WHEN** uma tool precisa de um comportamento que hoje não existe como caso de uso
- **THEN** o caso de uso é criado na feature dona do comportamento, e a camada de tools apenas
  o invoca

#### Scenario: Regra que vivia embutida numa tela
- **WHEN** o comportamento que a tool precisa já existia inline num ViewModel
- **THEN** ele é extraído para o caso de uso novo, e o ViewModel passa a consumi-lo — a cópia
  embutida deixa de existir

#### Scenario: Nenhum caso de uso exclusivo do agente
- **WHEN** os casos de uso criados por esta mudança são inspecionados
- **THEN** nenhum deles duplica comportamento que a interface continua executando por outro
  caminho

#### Scenario: Leitura ampliada no dono
- **WHEN** a superfície precisa de um recorte de leitura que o repositório dono não oferece
- **THEN** o recorte é acrescentado a esse repositório, e a tool não filtra nem agrega o
  resultado por fora

### Requirement: O agente expressa intenção, nunca lançamentos

Nenhuma tool SHALL aceitar ou devolver lançamentos assinados do razão. A escrita SHALL ser
expressa como intenção — a natureza da operação e as identidades envolvidas — e o
balanceamento SHALL permanecer na fronteira de escrita.

Nenhuma tool SHALL aceitar o rótulo da transação como entrada. O que a transação é decorre
dos tipos de conta envolvidos, e aceitar a declaração criaria uma segunda fonte de verdade
que divergiria da derivação no primeiro caso de borda.

#### Scenario: Não há como postar pernas
- **WHEN** a superfície é inspecionada
- **THEN** não existe tool que receba pernas, valores assinados ou contas do plano por tipo

#### Scenario: O rótulo volta derivado
- **WHEN** uma escrita conclui
- **THEN** a resposta informa o rótulo que o domínio derivou, e nenhuma entrada permitiu
  declará-lo

### Requirement: Contas de sistema nunca são expostas

A superfície SHALL expor apenas contas e cartões do usuário. As contas criadas sob demanda pela
fronteira de escrita — as nominais, a de reconciliação e a de conversão — MUST NOT aparecer em
nenhuma listagem, em nenhuma transação devolvida e em nenhum parâmetro de tool. Elas são
mecanismo, não fato do usuário.

Um agente que as enxergasse as citaria ao usuário como se fossem destino de dinheiro dele.

#### Scenario: Listagem de contas
- **WHEN** as contas são listadas
- **THEN** apenas contas e cartões do usuário aparecem

#### Scenario: Transação que cruzou moedas
- **WHEN** uma transação cujo resíduo foi para a conta de conversão é devolvida
- **THEN** essa perna não aparece como conta do usuário na resposta

### Requirement: Dinheiro tem uma forma só, e ela nunca colapsa

Todo valor monetário SHALL atravessar a fronteira como um objeto com a moeda, o valor inteiro
na menor unidade e a escala. Um texto formatado PODE acompanhá-lo, e SHALL ser declarado como
exclusivo para exibição.

Uma leitura que pode cruzar contas SHALL responder uma **coleção** de valores por moeda,
**mesmo quando há uma única moeda em uso**. Ela MUST NOT colapsar para um valor escalar nesse
caso: o consumidor aprenderia a forma escalar e quebraria na primeira conta em outra moeda.
Somente leitura escopada a uma única conta — que declara uma moeda só — SHALL ser escalar.

Quando o valor consolidado na moeda base for oferecido, ele SHALL ser um campo **irmão** do
valor por moeda, nunca um substituto, e SHALL carregar a taxa, a data da taxa e se ela está
defasada.

Faltando taxa para algum par no período, o campo consolidado SHALL vir **ausente com o
motivo**. Ele MUST NOT ser preenchido com taxa de valor um, com a taxa de hoje no lugar da
taxa datada, nem descartando silenciosamente as moedas sem cotação. Um total errado é pior que
um total ausente, porque nada permite ao consumidor suspeitar dele.

O sinal SHALL ser o de exibição — despesa negativa —, e SHALL ser o mesmo em toda a superfície.
A convenção débito-positivo do razão MUST NOT vazar para resposta alguma.

#### Scenario: Uma moeda só continua sendo coleção
- **WHEN** o usuário tem contas em uma única moeda e pede o patrimônio líquido
- **THEN** a resposta é uma coleção com um elemento, e não um valor escalar

#### Scenario: Consolidado carrega proveniência
- **WHEN** uma resposta inclui o valor na moeda base
- **THEN** ela inclui a taxa aplicada, a data dessa taxa e se está defasada

#### Scenario: Taxa faltando não vira número
- **WHEN** o acervo não tem cotação para um par necessário ao consolidado
- **THEN** o campo consolidado vem ausente com o motivo, e o valor por moeda continua completo

#### Scenario: Sinal uniforme
- **WHEN** qualquer resposta que contenha despesa e receita é inspecionada
- **THEN** a despesa é negativa e a receita positiva, em todas as tools

### Requirement: Identificadores são opacos, e nome nunca é chave

Nenhuma tool de escrita SHALL aceitar nome, rótulo ou texto livre como identificador de conta,
categoria, cartão, fatura ou orçamento. Identificadores SHALL vir de uma leitura anterior.

Toda leitura SHALL devolver o identificador junto do nome em todo objeto aninhado, para que o
consumidor nunca precise resolver um nome para um id numa segunda chamada.

Uma busca por nome SHALL devolver os candidatos quando for ambígua, e MUST NOT escolher um
deles. Ambiguidade é resultado, não erro.

#### Scenario: Escrita recusa nome
- **WHEN** uma escrita é chamada com o nome de uma categoria em vez do identificador
- **THEN** ela é recusada como entrada inválida, e nada é gravado

#### Scenario: Busca ambígua devolve candidatos
- **WHEN** uma busca por nome casa com mais de um registro
- **THEN** todos os candidatos são devolvidos, e nenhum é eleito

#### Scenario: Objeto aninhado carrega id
- **WHEN** uma transação é devolvida com a sua categoria e a sua conta
- **THEN** cada uma vem com identificador e nome

### Requirement: Listas paginam, agregados são calculados no servidor

Toda tool que devolve lista SHALL paginar por cursor opaco — não por deslocamento numérico, que
duplica e pula itens diante de escrita concorrente — e SHALL informar o total de registros que
satisfazem o filtro, para que o consumidor saiba que viu uma parte.

Um limite acima do teto SHALL ser recusado com erro explícito. A resposta MUST NOT ser truncada
em silêncio.

Toda figura agregada — total por categoria, por mês, por conta, gasto, receita, fluxo,
patrimônio — SHALL ser calculada no servidor sobre o conjunto completo, e SHALL existir como
tool própria. Sem ela o consumidor pagina, soma e apresenta o resultado como exato: um número
errado com aparência de precisão, porque somar do lado de fora ignora a moeda e conta como
gasto o que o domínio não classifica como gasto.

#### Scenario: Lista informa o que não coube
- **WHEN** um filtro casa com mais registros do que a página devolve
- **THEN** a resposta traz o total de registros que satisfazem o filtro e o cursor da próxima
  página

#### Scenario: Teto recusado, não truncado
- **WHEN** um limite acima do teto é pedido
- **THEN** a chamada é recusada com erro que nomeia o teto

#### Scenario: Agregado não depende de paginação
- **WHEN** um total por categoria é pedido para um período com milhares de lançamentos
- **THEN** ele é calculado sobre o período inteiro numa única resposta, sem paginação

### Requirement: Escrita é em lote, ensaiável e idempotente

Toda tool de escrita sobre lançamentos SHALL aceitar de um a muitos itens na mesma chamada.
Lançar um extrato é um pedido de primeira classe, e uma chamada por linha multiplica as
oportunidades de falha parcial silenciosa.

Toda tool de escrita SHALL aceitar um ensaio, que devolve exatamente o que seria gravado — os
rótulos derivados e as faturas resolvidas incluídos — **sem persistir nada**.

Toda tool de escrita SHALL aceitar uma chave de idempotência por chamada, e uma repetição com a
mesma chave MUST NOT duplicar. Agentes repetem chamadas por tempo esgotado, por reinício de
sessão e por decisão própria, e duplicação em contabilidade é dano silencioso.

A resposta SHALL informar o desfecho **por item**, distinguindo gravado, recusado e ignorado por
duplicidade, mais quantos foram aplicados. Um sucesso agregado sem detalhe por item MUST NOT ser
devolvido.

Duplicata provável SHALL ser sinalizada como aviso no item, e MUST NOT bloquear a gravação:
importar o mesmo extrato duas vezes é o erro mais comum que existe, e decidir por conta própria
que um lançamento legítimo é repetido é o erro oposto.

#### Scenario: Ensaio antes de gravar
- **WHEN** trinta lançamentos são enviados em ensaio
- **THEN** a resposta descreve os trinta resultados, inclusive em qual fatura cada compra de
  cartão cairia, e nada foi gravado

#### Scenario: Repetição com a mesma chave
- **WHEN** a mesma chamada é repetida com a mesma chave de idempotência
- **THEN** nada é gravado novamente, e a resposta descreve o que já havia sido gravado

#### Scenario: Item recusado não derruba o lote
- **WHEN** um item entre trinta é recusado por regra do domínio
- **THEN** a resposta nomeia esse item e o seu erro, e informa o desfecho de cada um dos demais

#### Scenario: Duplicata avisa, não bloqueia
- **WHEN** um item coincide com um lançamento já existente em data, valor, conta e descrição
- **THEN** ele é gravado com aviso de provável duplicidade

### Requirement: O erro tem classe, código estável e retentabilidade explícita

Toda recusa SHALL ser devolvida num envelope contendo: a **classe** do erro, distinguindo ao
menos recusa de regra do domínio, não encontrado, entrada inválida, conflito, indisponibilidade
e falha interna; um **código estável e enumerado** no schema da tool; uma mensagem em inglês
destinada a log; e se a chamada **pode ser repetida**.

Recusa de regra do domínio SHALL ser sempre não repetível: o estado do sistema está correto e
tentar de novo produzirá a mesma recusa. Indisponibilidade e falha interna SHALL ser repetíveis.

Uma recusa de regra SHALL ser resposta bem-sucedida do transporte descrevendo a recusa, e não
falha de transporte. O consumidor MUST NOT precisar interpretar texto livre para decidir se
repete, explica ou desiste.

A resposta MUST NOT conter o texto internacionalizado destinado à tela como mensagem para o
consumidor. Um texto já traduzido PODE acompanhar o envelope, declarado como destinado ao
usuário final.

Ausência de taxa numa leitura MUST NOT ser erro: é resultado parcial, e SHALL ser sinalizada
como aviso no payload de sucesso.

#### Scenario: Regra recusa
- **WHEN** uma escrita é recusada por uma regra do domínio
- **THEN** a resposta traz classe de regra, código enumerado, mensagem em inglês e indicação de
  que repetir não adianta

#### Scenario: Repetível é distinguível
- **WHEN** o consumidor recebe uma indisponibilidade e, noutra chamada, uma recusa de regra
- **THEN** ele distingue as duas sem interpretar texto

#### Scenario: Taxa ausente não é erro
- **WHEN** uma leitura não consegue consolidar por falta de cotação
- **THEN** ela conclui com sucesso, com o valor por moeda completo e um aviso

### Requirement: A resposta é reproduzível

Todo valor assumido por omissão SHALL ser ecoado na resposta — a data de referência, o período,
o recorte de arquivados. Duas chamadas idênticas SHALL produzir o mesmo número, e isso é
impossível se o consumidor não souber o que foi assumido.

Datas SHALL ser civis, no fuso do usuário. A superfície MUST NOT interpretar expressão de
período em linguagem natural: o consumidor informa datas explícitas.

Registros arquivados SHALL ficar de fora por omissão, e o recorte aplicado SHALL constar da
resposta — senão o consumidor relata que algo não existe quando ele apenas está arquivado.

Quando uma entidade tiver mais de uma data pertinente — a data da compra e a fatura em que ela
caiu —, a resposta SHALL trazer ambas, e o filtro SHALL declarar sobre qual delas recorta.

#### Scenario: Default ecoado
- **WHEN** uma leitura é chamada sem data de referência
- **THEN** a resposta informa a data que foi assumida

#### Scenario: Arquivado declarado
- **WHEN** uma listagem é chamada sem recorte de arquivados
- **THEN** ela informa que arquivados foram omitidos

#### Scenario: Compra de cartão tem duas datas
- **WHEN** uma compra no cartão é devolvida
- **THEN** a resposta traz a data da compra e a fatura em que ela caiu, e o filtro declara sobre
  qual das duas recortou

### Requirement: A superfície mínima é ler tudo e escrever lançamento

A primeira entrega SHALL expor estas tools e nenhuma outra:

**Leitura** — panorama (moeda base, patrimônio por moeda, saldo por conta, resumo de cartões e
cobertura do acervo de taxas, servindo também de ponto de entrada para os identificadores);
contas; categorias; lançamentos, com recorte por período, conta, cartão, fatura, categoria em
três estados (qualquer, uma dada, sem classificação) e faixa de valor; agregados por categoria,
mês, conta ou cartão; faturas; orçamentos com progresso; recorrências, incluindo ocorrências
pendentes; parcelamentos.

**Escrita** — registrar lançamentos, com as intenções despesa, receita, compra em cartão
(inclusive parcelada), transferência entre contas, pagamento de fatura, ajuste de saldo de conta
e ajuste de fatura; alterar lançamentos, restrito a categoria, descrição e data; e remover
lançamentos.

Alterar valor ou conta de um lançamento MUST NOT ser oferecido: mudar o dinheiro de um
lançamento é removê-lo e criar outro, e uma edição que o disfarçasse esconderia a correção.

Ficam de fora desta entrega, e SHALL permanecer inalcançáveis por agente até que existam
deliberadamente: transições de ciclo de vida de fatura e de recorrência; criação e alteração de
conta, categoria, cartão e orçamento; e tudo que toque moeda base ou o acervo de taxas.

Em consequência, o agente SHALL classificar apenas em categorias existentes, e o registro de
lançamentos MUST NOT criar categoria implicitamente. Um agente que criasse categoria durante
importação produziria variações da mesma categoria a cada extrato, e isso é difícil de desfazer.

#### Scenario: Categoria inexistente
- **WHEN** um lançamento é enviado com categoria que não existe
- **THEN** o item é recusado como entrada inválida, nenhuma categoria é criada, e a recusa nomeia
  a categoria pedida

#### Scenario: Editar valor não é oferecido
- **WHEN** a tool de alteração é inspecionada
- **THEN** ela não aceita valor nem conta como campo alterável

#### Scenario: Ciclo de vida fora do alcance
- **WHEN** um agente tenta fechar, pagar ou reabrir uma fatura
- **THEN** nenhuma tool oferece essa operação nesta entrega
