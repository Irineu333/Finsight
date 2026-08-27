# recurring-month-overview Specification

## Purpose
TBD - created by archiving change redesign-recurring-screen. Update Purpose after archive.
## Requirements
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

### Requirement: Uma metade sem nada sob o rótulo abre dobrada

Cada um dos dois blocos SHALL poder ser dobrado, e um bloco cujas duas figuras não afirmam
movimento algum SHALL abrir **dobrado**.

Duas linhas de `R$ 0,00` são o card tomando espaço para afirmar uma ausência, e o bloco
vazio é quase sempre o que o usuário não está perguntando: o fato no começo de um mês, a
projeção no fim dele. O que se dobra é a **figura**, e o **rótulo permanece** — um card que
escondesse também as palavras teria encolhido por um motivo que o usuário não pode ver, e o
que ficou dobrado deixaria de estar nomeado.

Dobrar MUST NOT ser lido como omitir. A figura existe, foi reduzida pelo redutor e está a um
toque; o que a dobra governa é o que a primeira tela gasta, nunca o que o card afirma. Ela
não dispensa nem enfraquece a exigência de que uma metade sem movimento tenha **zero
denominado** em vez da figura vazia.

"Nada sob o rótulo" SHALL ser lido sobre **todos os termos das duas figuras**, e MUST NOT ser
decidido por um termo só: o redutor responde um termo por moeda, e um mês pode ter
`US$ 50,00` ao lado de um `R$ 0,00` que apenas diz que nada se moveu em reais. Uma
verificação de um termo dobraria um mês com dinheiro dentro, e o usuário teria de adivinhar
que o bloco escondia alguma coisa.

A dobra é estado da **tela**: a partir do momento em que o usuário abre ou fecha um bloco, a
escolha dele governa, e o estado inicial SHALL ser rederivado apenas quando aquele bloco
cruzar entre ter e não ter movimento. Rederivá-lo a cada mudança dobraria de novo o bloco
que o usuário acabou de abrir, no instante seguinte ao de mexer no seletor de mês.

#### Scenario: O mês ainda não tem fato algum
- **WHEN** nada foi lançado no mês selecionado e o mês ainda tem projeção
- **THEN** o bloco lançado abre dobrado, com o seu rótulo visível, e o bloco da projeção abre aberto

#### Scenario: Um zero ao lado de uma moeda que se moveu
- **WHEN** a despesa lançada do mês é `R$ 0,00 + US$ 50,00`
- **THEN** o bloco lançado abre aberto, porque um dos seus termos afirma movimento

#### Scenario: A escolha do usuário sobrevive à troca de mês
- **WHEN** o usuário abre um bloco que abrira dobrado e seleciona outro mês em que aquele bloco também não tem movimento
- **THEN** o bloco continua aberto

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
- **THEN** o valor sai da figura lançada, o template volta a compor a figura não lançada, e o ciclo volta a ser um dos que o mês ainda pode pedir

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

### Requirement: O seletor de mês governa o resumo e o filtro governa a lista

O resumo SHALL carregar um **seletor de mês**, e o mês selecionado SHALL governar as quatro
figuras.

O mês SHALL governar **também a lista**. O que a lista exibe são os **ciclos** dos templates no
mês selecionado, e um ciclo tem mês por definição — as duas metades da tela respondem pela mesma
pergunta, sobre o mesmo mês, e um seletor que movesse só metade delas seria indistinguível de um
defeito.

A premissa que antes proibia isso permanece verdadeira e é o que a torna possível: um template
não tem mês, apenas a ocorrência dele tem. O que mudou não foi a regra, foi o **objeto da
lista**, que deixou de ser o template.

O filtro da lista MUST NOT governar o resumo. Sob um recorte por natureza o resumo teria de
suprimir uma das duas linhas de cada bloco — mudando de **forma** enquanto a lista muda de
**conteúdo**, sem que o usuário possa saber qual das duas coisas o seletor fez.

#### Scenario: O filtro muda a lista e não o resumo
- **WHEN** o usuário recorta a lista por despesas
- **THEN** cada seção passa a exibir apenas ciclos de despesa e as quatro figuras permanecem inalteradas

#### Scenario: O mês muda o resumo e a lista
- **WHEN** o usuário seleciona o mês anterior no seletor do resumo
- **THEN** as quatro figuras passam a responder por aquele mês, e a lista passa a exibir os ciclos daquele mês

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

Um mês sem movimento SHALL produzir **zero denominado pelo redutor**, e MUST NOT produzir a
figura vazia: zero é uma afirmação sobre o mês, e a ausência de figura é a recusa de afirmar.
O que o bloco que a contém faz com ela — dobrá-la, inclusive — é decisão do bloco, e tem
requisito próprio.

#### Scenario: Figura multimoeda
- **WHEN** as receitas fixas lançadas do mês somam R$ 5.865,00 e US$ 50,00 sem taxa cadastrada
- **THEN** a figura é exibida em dois termos, marcada como aproximada, e o card oferece a via para o arquivo de taxas

#### Scenario: Mês sem movimento
- **WHEN** nada foi lançado no mês selecionado
- **THEN** as duas figuras do bloco lançado são zero denominado pelo redutor, e não a figura vazia

### Requirement: O resumo permanece quando o recorte da lista está vazio

O resumo SHALL permanecer visível quando o filtro da lista não devolve item algum.

O vazio de recorte MUST NOT ocupar a tela: ele SHALL ser exibido **abaixo** do resumo, no
lugar da lista. Um recorte sem itens é justamente quando o resumo tem mais a dizer — ele é a
única coisa na tela que ainda afirma alguma coisa sobre o mês —, e apagá-lo junto com a lista
deixaria o usuário sem resposta e sem contexto.

O resumo MUST NOT ser exibido quando não existe recorrência alguma cadastrada. Aí não há mês a
resumir, e a tela SHALL manter a sua oferta de criar a primeira.

#### Scenario: Recorte sem itens numa base povoada
- **WHEN** o usuário recorta a lista por receitas numa base que tem recorrências, nenhuma delas de receita
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

