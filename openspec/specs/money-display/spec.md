# money-display Specification

## Purpose

Como um valor monetário se lê, e como se chama a linha que o nomeia. O sinal exibido expressa o efeito daquele valor sobre o patrimônio da perspectiva em que é lido — e por isso é omitido onde o rótulo já entrega a direção, omitido onde não há perspectiva sobre a qual produzir efeito, e exibido onde o valor participa de uma soma que ele explica. A política de sinal viaja com o valor, em um tipo de exibição que responde apenas *como* uma figura se lê; *quanto* ela vale é do razão.

O rótulo é a mesma pergunta um passo antes do sinal: numa decomposição fixa de um total, o par de rótulos declara *o que está dentro do número* — entrada/saída onde o rendimento tem linha própria, receita/despesa onde ele está contido na linha de entrada. A regra mora aqui, e não na tela, porque atravessa superfícies de donos distintos que de outro modo a reimplementariam.

## Requirements

### Requirement: O sinal exibido expressa efeito sobre a perspectiva

O sinal de um valor monetário exibido SHALL expressar o efeito daquele valor sobre o patrimônio da perspectiva em que é lido. Deste princípio decorrem as duas omissões e a inclusão que o sistema SHALL respeitar:

- o sinal MUST NOT ser exibido quando o rótulo do valor já entrega a sua direção;
- o sinal MUST NOT ser exibido quando não há perspectiva sobre a qual o valor produza efeito;
- o sinal SHALL ser exibido quando o valor participa de uma soma cujo resultado ele explica.

Nenhuma superfície MUST decidir sinal por critério próprio: a decisão SHALL derivar deste princípio, resolvida no mapeamento.

#### Scenario: Rótulo entrega a direção
- **WHEN** um gasto é exibido como item de uma lista
- **THEN** o valor aparece sem sinal, porque "gasto" já diz para que lado ele move o patrimônio

#### Scenario: Sem perspectiva não há efeito
- **WHEN** o pagamento de uma fatura é somado em um resumo de patrimônio total, onde a conta e o cartão estão no mesmo perímetro
- **THEN** a linha aparece sem sinal, porque o pagamento não altera o patrimônio total

#### Scenario: Mesma figura, perspectivas opostas
- **WHEN** o pagamento de uma fatura é somado no resumo de contas e no resumo de cartões
- **THEN** ele aparece negativo no primeiro e positivo no segundo, porque o efeito sobre cada perspectiva é oposto

#### Scenario: Sinal explica a aritmética
- **WHEN** um resumo exibe uma coluna de valores acima de um total
- **THEN** cada linha exibe o sinal com que entra no total, de modo que a coluna justifique o resultado

### Requirement: A política de sinal de um valor exibido tem dono único

Como um valor monetário é apresentado — em módulo, no seu sinal natural, neutro, com sinal sempre explícito, forçado a uma direção, ou como magnitude de dívida — SHALL ser expresso por um tipo de exibição que carrega, indissociáveis, o valor e a sua política de sinal. Um componente de UI MUST NOT decidir sinal por conta própria a partir de rótulo, natureza, direção ou tipo de conta.

O conjunto de políticas SHALL ser fechado e SHALL cobrir apenas casos com chamador existente. Duas políticas de mesmo comportamento e significados distintos SHALL permanecer distintas, para que uma figura mantenha a sua intenção quando o seu valor mudar de sinal.

Valor e política MUST NOT ser campos independentes de um modelo de UI: um valor construído sem a sua política, ou alterado sem ela, é o modo de falha que este requisito existe para tornar impossível.

#### Scenario: Componente não escolhe sinal
- **WHEN** um componente de UI renderiza um valor monetário
- **THEN** ele aplica a política que acompanha o valor, sem consultar rótulo, natureza, direção ou tipo de conta

#### Scenario: Linha de resumo recebe a política pronta
- **WHEN** um resumo exibe uma coluna de linhas com políticas diferentes entre si
- **THEN** cada linha recebe valor e política já resolvidos por quem produziu a figura, e o componente que a desenha não nomeia política alguma

#### Scenario: Política acompanha o valor
- **WHEN** um modelo de UI expõe um valor monetário
- **THEN** valor e política vêm no mesmo tipo, e não é possível expor um sem o outro

#### Scenario: Duas superfícies, mesma figura, mesmo resultado
- **WHEN** a mesma transação é exibida em uma lista na tela e no relatório exportado
- **THEN** ambas produzem o mesmo texto, por consumirem a mesma política, e não por coincidência entre duas implementações

### Requirement: O tipo de exibição não responde "quanto vale"

O tipo de exibição SHALL responder apenas como um valor se lê. Ele MUST NOT combinar dois valores — soma, subtração, multiplicação — nem conhecer moeda: quanto uma figura vale é do razão, que é o dono único desse cálculo.

Uma política SHALL poder transformar o seu próprio valor para leitura — módulo, negação, limite em zero —, o que é apresentação de um único número e não cálculo.

O tipo SHALL expor o valor numérico com o seu sinal, para que decisões que dependem do sinal — cor, tom, ordenação — o leiam da mesma fonte que o texto exibido.

#### Scenario: O tipo de exibição não oferece cálculo
- **WHEN** o tipo de exibição é inspecionado
- **THEN** ele não expõe operação entre dois valores nem moeda, e uma tela que precise de total, saldo ou diferença os recebe do razão já calculados

#### Scenario: Magnitude de dívida é apresentação
- **WHEN** uma linha responde quanto se deve, a partir de um saldo no sinal do razão
- **THEN** a política apresenta a magnitude da dívida, e um cartão em crédito lê zero em vez de dívida negativa

#### Scenario: Cor e texto concordam
- **WHEN** uma apresentação escolhe cor ou tom pelo sinal de um valor
- **THEN** ela lê o sinal do mesmo valor que será exibido, e não de uma segunda fonte

### Requirement: Item exibe sinal apenas onde o rótulo não entrega a direção

Na superfície de **item** — o card de uma transação, a sua modal, a sua linha no relatório exportado — o valor SHALL ser exibido sem sinal para gasto, receita e pagamento de fatura, cujos rótulos entregam a direção.

O **ajuste** SHALL exibir sinal sempre, positivo quando aumenta o patrimônio líquido da perspectiva e negativo quando o reduz — inclusive quando a perspectiva é um cartão, onde aumentar a dívida reduz o patrimônio. É a única transação cuja direção o rótulo não entrega.

A **transferência** SHALL exibir sinal explícito nas duas pontas quando lida sob uma perspectiva, porque elas compartilham rótulo, ícone e cor e sem sinal seriam indistinguíveis; exibir o sinal em apenas uma delas obrigaria o leitor a inferir a outra por ausência. Lida sem perspectiva, ela MUST NOT exibir sinal: a mesma transação contém as duas pontas, e a direção de uma perna escolhida arbitrariamente não é propriedade da transação.

#### Scenario: Ajuste que aumenta a dívida de um cartão
- **WHEN** a dívida de uma fatura é ajustada de R$ 0,00 para R$ 100,00 e a transação resultante é exibida como item
- **THEN** o valor é exibido como negativo, porque a dívida maior reduz o patrimônio

#### Scenario: Ajuste que reduz a dívida de um cartão
- **WHEN** a dívida de uma fatura é ajustada para menos e a transação resultante é exibida como item
- **THEN** o valor é exibido como positivo

#### Scenario: Ajuste que reduz o saldo de uma conta
- **WHEN** o saldo de uma conta é ajustado para menos e a transação resultante é exibida como item
- **THEN** o valor é exibido como negativo

#### Scenario: Ajuste que aumenta o saldo de uma conta
- **WHEN** o saldo de uma conta é ajustado para mais e a transação resultante é exibida como item
- **THEN** o valor é exibido como positivo

#### Scenario: Gasto não ganha sinal
- **WHEN** um gasto em conta ou em cartão é exibido como item
- **THEN** o valor é exibido em módulo, e a direção permanece expressa pelo rótulo, pelo ícone e pela cor

#### Scenario: Pagamento não ganha sinal
- **WHEN** o pagamento de uma fatura é exibido como item, em qualquer perspectiva
- **THEN** o valor é exibido em módulo, porque o rótulo "pagamento" entrega a direção

#### Scenario: Transferência sob a perspectiva de uma conta
- **WHEN** uma transferência é exibida na lista da conta de onde o dinheiro saiu
- **THEN** o valor é exibido com sinal negativo, e na lista da conta de destino, com sinal positivo explícito

#### Scenario: Transferência sem perspectiva
- **WHEN** uma transferência é exibida em uma lista que não declara perspectiva
- **THEN** o valor é exibido sem sinal, porque a transação contém as duas pontas

### Requirement: Resumo exibe o efeito sobre a perspectiva que soma

Na superfície de **resumo** — a linha que participa de uma soma exibida — o sinal SHALL ser o do efeito daquele valor sobre a perspectiva do resumo: gasto negativo; receita positiva; ajuste positivo quando aumenta o patrimônio líquido e negativo quando o reduz.

O pagamento de fatura e a transferência SHALL seguir a perspectiva: sob a perspectiva que o dinheiro deixa, negativo; sob a que o recebe, positivo; e sem perspectiva, neutro, por terem as duas pernas dentro do mesmo perímetro.

Uma linha de saldo SHALL exibir apenas o negativo, e uma linha de dívida SHALL exibir a magnitude devida sem sinal.

#### Scenario: Pagamento no resumo de contas
- **WHEN** o resumo do mês de uma conta soma o pagamento de uma fatura
- **THEN** a linha é negativa, porque o dinheiro deixou a conta

#### Scenario: Pagamento no resumo de cartões
- **WHEN** o resumo do mês dos cartões soma o mesmo pagamento
- **THEN** a linha é positiva, porque a dívida diminuiu

#### Scenario: Pagamento no resumo de patrimônio total
- **WHEN** o resumo de patrimônio total soma o mesmo pagamento
- **THEN** a linha aparece sem sinal, e a coluna acima do total continua fechando

#### Scenario: Ajuste no resumo
- **WHEN** um resumo soma os ajustes do período
- **THEN** a linha exibe sinal explícito, na mesma direção em que o ajuste aparece como item

#### Scenario: Linha de dívida
- **WHEN** um resumo de cartões exibe a dívida de abertura ou de fechamento
- **THEN** ela aparece sem sinal, como magnitude devida, e um cartão em crédito lê zero

### Requirement: O ajuste lê igual em toda superfície

O sinal exibido de um ajuste SHALL ser o mesmo em toda superfície que o apresenta — lista, detalhe, resumo de fatura, resumo do mês e relatório exportado —, por derivar do mesmo valor do razão. Uma superfície MUST NOT apresentar um ajuste com sinal oposto ao de outra.

#### Scenario: Lista e detalhe concordam
- **WHEN** um ajuste é aberto no detalhe a partir da lista em que aparece
- **THEN** o sinal exibido nas duas superfícies é o mesmo

#### Scenario: Lista e resumo da fatura concordam
- **WHEN** uma fatura exibe a sua linha de ajustes acima da lista de lançamentos
- **THEN** o ajuste na lista tem o mesmo sinal que a linha de resumo

#### Scenario: Tom do ajuste no relatório exportado
- **WHEN** um ajuste que reduz o patrimônio é exportado no relatório
- **THEN** ele recebe o tom negativo, e não o positivo

### Requirement: O rótulo de uma linha de fluxo declara o que está dentro do número

Numa **decomposição fixa de um total** — um conjunto de linhas de fluxo nomeadas pelo app, exibidas sobre um resultado que elas explicam —, o par de rótulos das linhas de entrada e de saída SHALL declarar se o rendimento está segregado:

- onde o **rendimento tem linha própria**, o par SHALL ser **entrada/saída**;
- onde o rendimento **está contido** na linha de entrada, o par SHALL ser **receita/despesa**, ficando subentendido que receita engloba rendimento.

O rótulo SHALL, portanto, responder *o que está dentro do número*, e não meramente nomear a tela em que ele aparece. Duas superfícies que exibem valores diferentes para o mesmo período MUST NOT fazê-lo sob o mesmo rótulo: é o rótulo que torna a diferença legível em vez de contraditória.

Essa regra SHALL ter **dono único** e MUST NOT ser re-derivada por tela. Ela atravessa superfícies de donos distintos — o cartão de uma conta, o resumo de um escopo, o relatório e o painel —, e alojá-la em qualquer uma obrigaria as demais a reimplementá-la.

A regra SHALL acompanhar a **forma do número**, não a tela: uma superfície que passe a segregar rendimento SHALL passar a usar o par entrada/saída, e uma que deixe de segregar SHALL voltar ao par receita/despesa, sem decisão caso a caso.

Dois usos ficam **fora** do alcance da regra, e MUST NOT ser renomeados por ela:

- **Seletor de tipo, filtro e formulário.** Ali a palavra nomeia um *tipo de lançamento* que o usuário escolhe, não o conteúdo de uma soma; a pergunta "isso inclui rendimento?" não tem sentido, porque inclui sempre. Esse eixo SHALL seguir o vocabulário de tipo de transação, uniformemente **receita/despesa**, e MUST NOT misturar um termo de um par com um termo do outro.
- **Agrupamento por dimensão.** Um total agrupado por categoria separa o rendimento por construção, já que ele é uma categoria. Se isso contasse como segregação, todo agrupamento por categoria adotaria entrada/saída e a regra deixaria de distinguir coisa alguma.

#### Scenario: Superfície que segrega rendimento
- **WHEN** um resumo exibe uma linha própria de rendimento sobre um total
- **THEN** as linhas de fluxo correspondentes são rotuladas como entrada e saída

#### Scenario: Superfície que não segrega rendimento
- **WHEN** um resumo exibe o rendimento contido na sua linha de entrada
- **THEN** as linhas de fluxo correspondentes são rotuladas como receita e despesa

#### Scenario: A diferença entre duas telas é legível
- **WHEN** o mesmo período é exibido em uma superfície que segrega e em outra que não segrega
- **THEN** a primeira exibe entrada e rendimento em linhas separadas, a segunda exibe receita contendo ambos, e os rótulos explicam por que os números diferem

#### Scenario: Filtro de tipo não adota o par segregado
- **WHEN** o usuário abre um filtro ou seletor de tipo de lançamento
- **THEN** as opções são rotuladas como receita e despesa, e MUST NOT combinar um termo de um par com o do outro

#### Scenario: Agrupamento por categoria não é segregação
- **WHEN** um total de receitas é exibido agrupado por categoria, com o rendimento aparecendo na sua própria categoria
- **THEN** o rótulo do agrupamento permanece o do par não segregado, por a separação decorrer da dimensão e não de uma linha de fluxo

#### Scenario: A regra segue o número, não a tela
- **WHEN** uma superfície que hoje não segrega rendimento passa a exibi-lo em linha própria
- **THEN** os seus rótulos de fluxo passam a ser entrada e saída, sem que a decisão seja tomada tela a tela
