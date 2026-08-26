## ADDED Requirements

### Requirement: O resumo separa o que já foi lançado do que ainda não foi

A tela de recorrentes SHALL exibir, acima da lista, um resumo do mês composto por **quatro
figuras** em **dois blocos rotulados**: as despesas e as receitas fixas **já lançadas** no
mês, e as despesas e as receitas fixas **ainda não lançadas**.

Os dois blocos MUST NOT ser apresentados como pares de uma mesma classe. Uma figura lançada é
**fato** — o dinheiro está no razão —, e uma figura não lançada é **projeção** — nada foi
escrito, e o mês pode terminar sem que se escreva. Apresentá-las com o mesmo rótulo, o mesmo
peso e a mesma vizinhança afirmaria que uma recorrência é dinheiro, que é exatamente o que ela
não é enquanto não for confirmada.

A distinção SHALL ser carregada por um **rótulo de bloco**, e não apenas por posição, ordem ou
cor: quem não lê a diferença tipográfica precisa lê-la em palavras.

O bloco do fato SHALL vir **antes** do bloco da projeção.

#### Scenario: Os dois blocos são nomeados
- **WHEN** o resumo do mês é exibido
- **THEN** cada bloco carrega um rótulo próprio dizendo se as suas figuras já foram lançadas ou ainda não

#### Scenario: Fato e projeção não se confundem
- **WHEN** o mês tem R$ 1.240,00 de despesa fixa lançada e R$ 380,00 de despesa fixa ainda não lançada
- **THEN** os dois valores aparecem em blocos distintos, e nenhuma superfície do card os apresenta como um único total

### Requirement: A metade lançada é lida do razão, nunca do template

As figuras do bloco lançado SHALL ser derivadas das **ocorrências confirmadas** do mês e do
dinheiro que as transações apontadas por elas de fato registraram no razão.

Elas MUST NOT ser derivadas do valor do template. Confirmar um ciclo permite ao usuário
sobrescrever o valor, a conta, o cartão, o título e a categoria daquele ciclo, e o template
permanece intacto — de modo que somar templates confirmados produziria um número que nunca
existiu.

O corte do mês SHALL ser o mês declarado pela **ocorrência**, e não a data da transação, para
que o dinheiro somado e o ciclo contado nunca discordem sobre a que mês um ciclo pertence.

A natureza de cada figura — despesa ou receita — SHALL ser derivada do razão, e MUST NOT ser
lida do tipo declarado no template.

#### Scenario: Ciclo confirmado com valor sobrescrito
- **WHEN** um template de R$ 940,00 tem o seu ciclo confirmado por R$ 865,00
- **THEN** a figura de receita fixa lançada do mês soma R$ 865,00, e não R$ 940,00

#### Scenario: Transação de um ciclo é apagada
- **WHEN** a transação gerada por um ciclo confirmado é removida
- **THEN** o valor sai da figura lançada, o template volta a compor a figura não lançada, e o contador do mês recua um ciclo

### Requirement: A metade não lançada é o mês inteiro, não apenas o vencido

As figuras do bloco não lançado SHALL somar **todos** os templates do mês sem ocorrência
registrada, independentemente de o dia do ciclo já ter chegado.

O corte por dia — o que o app chama de *pendente* — MUST NOT governar essas figuras: o card
responde quanto **o mês** ainda vai pedir, e um compromisso do dia 28 continua sendo dinheiro
que vai sair.

A definição de "sem ocorrência registrada" SHALL ser consumida do dono que o domínio já tem, e
MUST NOT ser reimplementada pela tela.

#### Scenario: Compromisso ainda por vencer
- **WHEN** hoje é dia 10 e há um template não tratado com dia de ciclo 28
- **THEN** o valor dele compõe a figura de despesa fixa ainda não lançada

#### Scenario: Ciclo já tratado não volta à projeção
- **WHEN** um template já tem ocorrência no mês, confirmada ou ignorada
- **THEN** ele não compõe nenhuma das duas figuras não lançadas

### Requirement: O contador é onde um ciclo ignorado é representável

O resumo SHALL exibir um **contador de ciclos** do mês: quantos dos templates ativos já foram
tratados, de quantos, e quantos o foram por **terem sido ignorados**.

Um ciclo ignorado não é lançamento — não há entry — nem pendência — o domínio já o conta como
tratado. Ele é, por construção, invisível nas quatro figuras, e essa aritmética está correta:
contrabandeá-lo para qualquer uma delas afirmaria dinheiro que não existe ou um compromisso
que não existe mais. O contador é o **único** lugar do resumo em que ele é representável, e
sem ele o card não presta contas da diferença entre a projeção que encolheu e o fato que não
cresceu.

A menção aos ciclos ignorados SHALL ser exibida apenas quando houver algum no mês: uma
anotação condicional é **ausente**, não zero.

O contador MUST NOT ser expresso como proporção preenchida, porque uma proporção teria de
decidir visualmente o que um ciclo ignorado preenche — que é precisamente a decisão que este
requisito exige declarada em palavras.

#### Scenario: Um ciclo é ignorado
- **WHEN** o usuário ignora o ciclo do mês de um template de R$ 77,00
- **THEN** a figura não lançada diminui em R$ 77,00, nenhuma figura lançada aumenta, e o contador registra o ciclo como tratado e como ignorado

#### Scenario: Mês sem ciclos ignorados
- **WHEN** nenhum ciclo do mês foi ignorado
- **THEN** o contador não menciona ciclos ignorados

### Requirement: O seletor de mês governa o resumo e o filtro governa a lista

O resumo SHALL carregar um **seletor de mês próprio**, e o mês selecionado SHALL governar as
quatro figuras e o contador.

O resumo MUST NOT responder ao filtro da lista. Sob o recorte de arquivadas a metade não
lançada é estruturalmente vazia, porque uma recorrência arquivada não gera pendência; e sob um
recorte por natureza o resumo teria de suprimir uma das duas linhas de cada bloco — mudando de
**forma** enquanto a lista muda de **conteúdo**, sem que o usuário possa saber qual das duas
coisas o seletor fez.

O filtro da lista MUST NOT governar o resumo, e o seletor de mês MUST NOT governar a lista. Um
resumo que não tem controle nenhum e ignora o único controle da tela é indistinguível de um
defeito: é o seletor próprio que torna visível que o card tem governo próprio.

A **lista** MUST NOT passar a ser recortada por mês. Um template não tem mês; apenas a
ocorrência dele tem.

#### Scenario: O filtro muda a lista e não o resumo
- **WHEN** o usuário troca o filtro da lista de ativas para arquivadas
- **THEN** a lista passa a exibir as arquivadas e as quatro figuras e o contador permanecem inalterados

#### Scenario: O mês muda o resumo e não a lista
- **WHEN** o usuário seleciona o mês anterior no seletor do resumo
- **THEN** as quatro figuras e o contador passam a responder por aquele mês, e a lista continua exibindo os mesmos templates

### Requirement: As figuras do resumo não exibem sinal

Cada uma das quatro figuras SHALL ser exibida como **magnitude, sem sinal**, com a direção
entregue pelo rótulo da linha.

A superfície de **resumo** de `money-display` — a que obriga a linha a exibir o efeito do
valor sobre a perspectiva somada — rege, pelos seus próprios termos, "a linha que participa de
uma soma exibida". Nenhuma destas quatro participa: o card não exibe total algum, nem entre
os blocos nem dentro deles, e as figuras não se somam nem se subtraem em superfície nenhuma.
Sem soma exibida não há efeito a declarar, e um sinal aqui seria decoração tipográfica
tomada de empréstimo a uma política cujo significado é outro.

Se o card algum dia passar a exibir um total, as quatro figuras SHALL passar a exibir sinal, e
esta reconciliação SHALL ser revista junto.

#### Scenario: Despesa fixa lançada
- **WHEN** o bloco lançado exibe a despesa fixa do mês
- **THEN** o valor aparece sem sinal, e o rótulo da linha entrega que se trata de despesa

#### Scenario: O card não exibe total
- **WHEN** o resumo do mês é exibido
- **THEN** nenhuma linha do card soma ou subtrai as demais

### Requirement: Um template que nenhuma conta denomina sai da soma, e o resumo declara

Um template cuja conta ou cartão não pode ser resolvido MUST NOT ser somado a figura alguma:
não há moeda que o denomine.

O resumo MUST NOT omitir esse descarte em silêncio: ele SHALL declarar, em texto, **quantos**
templates ficaram de fora da soma. Um total que descarta parcelas caladamente é indistinguível
de um total completo, e o usuário a quem ele falha é justamente o que tem um template
apontando para uma conta que não existe mais.

A figura MUST NOT ser substituída pela marca de valor irresolvível: o restante do dinheiro é
conhecido, e uma soma parcial declarada é mais honesta do que a recusa de exibir o que se sabe.

A declaração MUST NOT reusar a explicação de figura aproximada por falta de taxa. As duas
falhas são distintas e têm saídas distintas — uma se resolve apontando o template para uma
conta, a outra cadastrando uma taxa —, e reusar aquela copy diria a frase errada com
autoridade.

#### Scenario: Template sem conta resolvível
- **WHEN** um dos templates não tratados do mês aponta para uma conta que não existe mais
- **THEN** o valor dele não entra na figura não lançada, e o card declara que um template ficou de fora da soma

#### Scenario: Nenhum template fora da soma
- **WHEN** todos os templates do mês têm conta ou cartão resolvível
- **THEN** o card não exibe declaração de templates fora da soma

### Requirement: Toda figura do resumo é consolidada e sabe se explicar

Cada uma das quatro figuras SHALL ser reduzida pelo redutor único da consolidação, e MUST NOT
ser somada ou convertida pela tela, pelo view model ou por um modelo de UI. Elas atravessam
contas e, por consequência, podem atravessar moedas.

Uma figura que o redutor não conseguiu reduzir a um único termo SHALL ser exibida em termos,
com a marca de aproximação que a consolidação define, e o card SHALL oferecer **uma** via para
o arquivo de taxas que a explique — uma para o card inteiro, e não uma por figura.

#### Scenario: Figura multimoeda
- **WHEN** as receitas fixas lançadas do mês somam R$ 5.865,00 e US$ 50,00 sem taxa cadastrada
- **THEN** a figura é exibida em dois termos, marcada como aproximada, e o card oferece a via para o arquivo de taxas

#### Scenario: Mês sem movimento
- **WHEN** nada foi lançado no mês selecionado
- **THEN** as duas figuras do bloco lançado exibem zero, denominado pelo redutor, e não desaparecem

### Requirement: O resumo permanece quando o recorte da lista está vazio

O resumo SHALL permanecer visível quando o filtro da lista não devolve item algum.

O vazio de recorte MUST NOT ocupar a tela: ele SHALL ser exibido **abaixo** do resumo, no
lugar da lista. Um recorte sem itens é justamente quando o resumo tem mais a dizer — ele é a
única coisa na tela que ainda afirma alguma coisa sobre o mês —, e apagá-lo junto com a lista
deixaria o usuário sem resposta e sem contexto.

O resumo MUST NOT ser exibido quando não existe recorrência alguma cadastrada. Aí não há mês a
resumir, e a tela SHALL manter a sua oferta de criar a primeira.

#### Scenario: Recorte sem itens numa base povoada
- **WHEN** o usuário seleciona o recorte de arquivadas numa base que tem recorrências, nenhuma delas arquivada
- **THEN** o resumo continua exibido e a mensagem de recorte vazio aparece abaixo dele

#### Scenario: Base sem nenhuma recorrência
- **WHEN** não existe recorrência cadastrada
- **THEN** o resumo não é exibido e a tela oferece a criação da primeira recorrência

### Requirement: A arquivada sai da projeção sem levar o que já lançou

Uma recorrência arquivada MUST NOT compor as figuras não lançadas, em mês algum: arquivar
interrompe a geração de pendências.

O dinheiro que ela lançou **antes** de ser arquivada SHALL permanecer nas figuras lançadas do
mês em que foi lançado, inclusive quando o arquivamento acontece no mesmo mês da confirmação.
O lançamento aconteceu, continua no razão e continua vinculado ao template — retirá-lo do
resumo faria o card contradizer o que o próprio arquivamento promete ao usuário.

A assimetria é deliberada e SHALL ser preservada: a metade lançada é uma afirmação sobre
**dinheiro**, e a metade não lançada é uma afirmação sobre **templates**.

#### Scenario: Arquivamento depois da confirmação, no mesmo mês
- **WHEN** um template tem o ciclo do mês confirmado e em seguida é arquivado
- **THEN** o valor confirmado permanece na figura lançada do mês, e o template não compõe figura não lançada alguma

#### Scenario: Arquivamento antes do ciclo
- **WHEN** um template é arquivado sem ter ocorrência no mês
- **THEN** ele não compõe nem a figura lançada nem a não lançada
