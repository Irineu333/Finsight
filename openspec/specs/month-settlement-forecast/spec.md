# month-settlement-forecast Specification

## Purpose

A janela de **liquidação** do mês corrente como perímetro de um widget de dinheiro: o que ainda vai entrar e o que ainda vai sair antes de o mês fechar. O perímetro é o tempo, e não uma natureza de conta — ele atravessa `ASSET` e `LIABILITY` por construção —, e é composto por duas fontes disjuntas e configuráveis: as recorrentes do mês sem ocorrência registrada, cujo tratamento é o que `recurring-confirmation` governa, e as faturas não pagas cujo vencimento não é futuro, incluída a retroativa com saldo por exceção local e declarada. O corte inclui o vencido, porque dívida vencida e não liquidada continua sendo dinheiro por sair, e o alcance retroativo é desigual entre as duas fontes — propriedade de cada fonte, que esta capacidade declara em vez de disfarçar. Os valores vêm das leituras do razão (`ledger-reporting`), com o devido das faturas lido de forma agregada e por moeda; a redução a uma única moeda vem do redutor único da consolidação (`currency-consolidation`); e o rótulo, a forma e a ocultação do widget obedecem ao que `dashboard-balance-widgets` fixa para todo widget do dashboard.
## Requirements
### Requirement: O perímetro de liquidação é a identidade do widget

O dashboard SHALL oferecer um widget cujo perímetro é a **liquidação**, e não uma natureza de
conta: ele soma os compromissos que ainda **não se liquidaram** e cujo vencimento **não é
futuro**, dentro do mês corrente.

A figura SHALL ser um **par** — o que ainda vai entrar e o que ainda vai sair —, exibido como as
demais figuras de fluxo do dashboard.

O rótulo SHALL nomear a **janela de liquidação**, e MUST NOT nomear uma natureza de conta, porque
o perímetro atravessa naturezas por construção: uma recorrente de conta e uma fatura de cartão
compõem a mesma classe. A regra que obriga um widget a nomear a natureza que soma
(`dashboard-balance-widgets`) rege o eixo da natureza e MUST NOT ser lida como exigência de
qualificar por natureza um widget cujo eixo é o tempo.

O rótulo MUST NOT afirmar futuridade — o perímetro inclui o **vencido e não liquidado**, e um
rótulo que prometesse apenas o futuro seria afirmação falsa sobre metade do que a figura soma.

O rótulo é o **nome do widget**, e o mesmo texto serve ao catálogo do modo de edição e ao cabeçalho
na tela. *Se* o cabeçalho é exibido é a preferência de layout que `dashboard-balance-widgets` já
governa para todo widget, e não uma decisão desta capacidade: ela nasce **desligada**, como nas
demais figuras de fluxo do dashboard, e permanece uma opção do usuário. O que esta capacidade fixa
é o que o rótulo diz, não onde ele aparece.

#### Scenario: A figura é um par
- **WHEN** o widget de liquidação está presente na tela
- **THEN** ele exibe duas classes, o que ainda entra e o que ainda sai, como qualquer widget de fluxo do dashboard

#### Scenario: O rótulo não se diz apenas futuro
- **WHEN** o widget soma uma fatura vencida no mês passado e ainda não paga
- **THEN** o rótulo do widget não a descreve como despesa futura, e sim como valor a liquidar

#### Scenario: O perímetro atravessa naturezas sem qualificar-se por uma
- **WHEN** a classe a sair soma uma recorrente de conta e uma fatura de cartão
- **THEN** o rótulo não nomeia nem `ASSET` nem `LIABILITY`, por o perímetro não ser de natureza

#### Scenario: O cabeçalho nasce desligado e continua oferecido
- **WHEN** o widget é adicionado ou lido do layout padrão
- **THEN** ele é exibido sem cabeçalho, e o modo de edição oferece ligá-lo como oferece nos demais widgets de fluxo

### Requirement: As duas fontes do perímetro e o que cada uma alimenta

O perímetro SHALL ser composto por exatamente duas fontes:

1. **Recorrentes do mês** — templates não arquivados e **sem ocorrência registrada no mês
   corrente**, independentemente de o dia efetivo do ciclo já ter passado ou ainda estar por vir.
   Esta fonte alimenta as **duas** classes, conforme o tipo do template.
2. **Faturas a pagar** — faturas não pagas cujo mês de vencimento não é posterior ao mês corrente.
   Esta fonte alimenta **apenas** a classe a sair, porque uma fatura não tem contraparte de
   receita.

A assimetria entre as fontes SHALL ser estrutural e MUST NOT ser compensada: a classe a entrar
tem uma fonte, a classe a sair tem duas, e nenhuma fonte artificial é criada para emparelhá-las.

A **parcela futura** MUST NOT ser fonte própria: parcelas são pré-lançadas nas faturas dos meses
em que vencem, então a parcela que vence neste mês já está no devido da fatura deste mês, e
somá-la à parte dupla-contaria.

#### Scenario: Recorrente do mês ainda não vencida entra
- **WHEN** hoje é dia 5, existe uma recorrente de despesa do dia 20 sem ocorrência no mês, e o widget está presente
- **THEN** ela é somada à classe a sair

#### Scenario: Recorrente vencida e não tratada entra
- **WHEN** hoje é dia 25, existe uma recorrente de despesa do dia 10 sem ocorrência no mês, e o widget está presente
- **THEN** ela é somada à classe a sair

#### Scenario: Recorrente de receita alimenta a outra classe
- **WHEN** existe uma recorrente de receita sem ocorrência no mês
- **THEN** ela é somada à classe a entrar

#### Scenario: Recorrente já tratada não entra
- **WHEN** uma recorrente do mês já tem ocorrência registrada no mês corrente
- **THEN** ela não é somada a nenhuma das duas classes

#### Scenario: Fatura não alimenta a classe a entrar
- **WHEN** existem faturas a pagar e nenhuma recorrente de receita pendente
- **THEN** a classe a entrar vale zero, e nenhuma fatura a compõe

#### Scenario: Parcela futura entra pela fatura, não por si
- **WHEN** existe uma compra parcelada cuja parcela vence neste mês
- **THEN** ela é contada exatamente uma vez, dentro do devido da fatura deste mês

### Requirement: O corte de vencimento inclui o vencido

A fonte de faturas SHALL incluir toda fatura não paga cujo mês de vencimento seja **igual ou
anterior** ao mês corrente, e MUST NOT restringir-se ao vencimento do mês corrente.

A razão é a mesma que inclui a recorrente já vencida: uma dívida vencida e não liquidada continua
sendo dinheiro que vai sair, e omiti-la faria a figura encolher sem que nada tivesse sido pago.

Uma fatura cujo vencimento é **posterior** ao mês corrente MUST NOT ser somada, ainda que já tenha
gasto lançado: ela não liquida nesta janela.

#### Scenario: Fatura vencida em mês anterior entra
- **WHEN** existe uma fatura com vencimento no mês passado, ainda não paga, e o widget está presente
- **THEN** o seu devido é somado à classe a sair

#### Scenario: Fatura de vencimento futuro fica fora
- **WHEN** existe uma fatura aberta cujo vencimento é no mês que vem, já com gasto lançado
- **THEN** o seu devido não é somado, por ela não liquidar nesta janela

#### Scenario: Fatura paga sai da figura
- **WHEN** o usuário paga uma fatura que estava sendo somada
- **THEN** ela deixa de compor a classe a sair

### Requirement: As duas fontes são disjuntas e o total é invariante sob confirmação

As duas fontes SHALL ser disjuntas por construção, e a figura MUST NOT aplicar qualquer regra de
deduplicação entre elas: uma recorrente ainda não tratada não tem lançamento, logo não compõe o
devido de fatura nenhuma.

Em consequência, confirmar uma recorrente de **cartão** SHALL deixar o total **inalterado** — o
valor migra da fonte de recorrentes para o devido da fatura de mesmo mês de vencimento, que é a
fatura que a confirmação resolve. Confirmar uma recorrente de **conta** SHALL **reduzir** o total,
porque o dinheiro saiu de fato e deixou de estar por liquidar.

Quando a fatura de vencimento no mês corrente estiver fechada a novos gastos, a confirmação
aterrissa na fatura de vencimento posterior e o valor **sai** da figura. Isso SHALL ser
consequência declarada do corte de vencimento, e MUST NOT ser tratado como caso especial.

#### Scenario: Confirmar recorrente de cartão não move o total
- **WHEN** o usuário confirma uma recorrente de cartão que estava pendente, e a fatura de vencimento neste mês aceita gasto novo
- **THEN** o total a sair permanece idêntico, tendo o valor migrado da fonte de recorrentes para o devido daquela fatura

#### Scenario: Confirmar recorrente de conta reduz o total
- **WHEN** o usuário confirma uma recorrente de conta que estava pendente
- **THEN** o total a sair diminui daquele valor, por o dinheiro já ter saído

#### Scenario: Nenhuma deduplicação entre as fontes
- **WHEN** existem recorrentes pendentes de cartão e faturas a pagar do mesmo cartão
- **THEN** as duas parcelas são somadas inteiras, sem subtração ou cruzamento entre elas

#### Scenario: Confirmação bloqueada pela fatura fechada tira o valor da janela
- **WHEN** a fatura de vencimento neste mês está fechada a novos gastos e o usuário confirma a recorrente na fatura seguinte
- **THEN** o valor deixa de compor a figura, por passar a liquidar em janela posterior

### Requirement: A fatura retroativa com saldo entra na figura

Uma fatura de status `RETROACTIVE` com saldo devedor SHALL ser somada à classe a sair como
qualquer outra fatura a pagar, por ser exatamente o caso de dívida vencida em regularização.

Esta leitura MUST NOT ser obtida de uma consulta que exclua `RETROACTIVE` do conjunto de faturas
não pagas. A inclusão SHALL ser **exceção local** desta figura, declarada como tal: a contradição
entre as leituras que tratam `RETROACTIVE` como liquidada e as regras de domínio que a tratam
como devedora permanece aberta fora deste widget, e resolvê-la MUST NOT ser pré-requisito desta
capacidade.

#### Scenario: Retroativa com saldo é somada
- **WHEN** existe uma fatura `RETROACTIVE` com saldo devedor e o widget está presente
- **THEN** o seu devido é somado à classe a sair

#### Scenario: Retroativa sem saldo não altera a figura
- **WHEN** existe uma fatura `RETROACTIVE` cujo devido é zero
- **THEN** ela não altera o valor de nenhuma das duas classes

### Requirement: A configuração governa quais fontes compõem, e nunca a forma

O widget SHALL permitir ao usuário ligar e desligar cada uma das duas fontes de forma
independente, e ambas SHALL nascer **ligadas**.

Desligar uma fonte SHALL alterar **apenas o valor** das classes, e MUST NOT alterar o rótulo, o
ícone, a quantidade de classes exibidas ou a forma do widget. Uma classe que fique estruturalmente
zerada por configuração — como a classe a entrar quando apenas a fonte de faturas está ligada —
SHALL continuar sendo exibida, **como zero**.

Com as duas fontes desligadas o widget SHALL exibir **zero nas duas classes** e MUST NOT
desaparecer da tela, ainda que esteja configurado para ocultar-se quando vazio: o alvo da edição
não pode sumir enquanto o usuário o edita.

A configuração de ocultar-quando-vazio SHALL governar **apenas** o caso em que as fontes ligadas
nada têm a somar no mês.

#### Scenario: Ambas as fontes ligadas por padrão
- **WHEN** o usuário adiciona o widget pelo modo de edição
- **THEN** as duas fontes já estão ligadas, e a figura soma recorrentes e faturas

#### Scenario: Classe estruturalmente zerada continua visível
- **WHEN** apenas a fonte de faturas está ligada
- **THEN** as duas classes continuam exibidas, e a classe a entrar exibe zero

#### Scenario: Sem fonte alguma o widget vale zero e permanece
- **WHEN** o usuário desliga as duas fontes com o widget configurado para ocultar-se quando vazio
- **THEN** o widget permanece na tela exibindo zero nas duas classes

#### Scenario: Ocultar-quando-vazio ainda vale para ausência de dados
- **WHEN** as duas fontes estão ligadas, nada há a liquidar no mês, e o widget está configurado para ocultar-se quando vazio
- **THEN** o widget inteiro desaparece

#### Scenario: Religar a fonte devolve o valor
- **WHEN** o usuário torna a ligar uma fonte que havia desligado
- **THEN** o valor exibido volta a ser o de antes, sem qualquer outra ação

### Requirement: O alcance do vencido é desigual entre as fontes e é declarado

O alcance retroativo das duas fontes SHALL ser reconhecidamente **desigual**: a fonte de faturas
alcança qualquer mês anterior, enquanto a fonte de recorrentes alcança apenas o mês corrente,
porque a leitura de recorrentes pendentes resolve tudo sobre o mês de hoje e não consulta mês
anterior algum.

Em consequência, um ciclo de recorrente de mês anterior que nunca foi confirmado nem pulado
MUST NOT ser esperado nesta figura. Esta capacidade MUST NOT afirmar que soma todo compromisso
vencido, e MUST NOT introduzir uma varredura retroativa própria de recorrentes para disfarçar a
diferença — o alcance da fonte é propriedade dela, e ampliá-lo é mudança daquela capacidade.

#### Scenario: Recorrente de mês anterior não confirmada fica fora
- **WHEN** uma recorrente de mês anterior nunca foi confirmada nem pulada
- **THEN** ela não compõe a figura, e o widget não afirma tê-la somado

#### Scenario: Fatura de mês anterior fica dentro
- **WHEN** uma fatura de mês anterior continua não paga
- **THEN** ela compõe a figura, ainda que uma recorrente do mesmo mês não componha

### Requirement: A figura é reduzida pela consolidação, e não pela fonte

Cada parcela da figura SHALL ser denominada pela moeda que a origina — a conta ou o cartão que a
recorrente nomeia, e a conta `LIABILITY` do cartão da fatura —, e a soma SHALL ser **por moeda**,
sem conversão na origem.

A redução a uma única moeda SHALL acontecer no reducer único da camada de consolidação
(`currency-consolidation`), como em qualquer figura do dashboard que atravesse contas. Uma leitura
que colapse o devido de uma fatura a um escalar antes da soma MUST NOT ser usada como fonte, por
perder o agrupamento por moeda antes da consolidação.

O devido das faturas SHALL ser lido de forma **agregada** sobre o conjunto de faturas do
perímetro, e MUST NOT ser obtido por uma leitura por fatura de cada vez.

#### Scenario: Duas moedas somam cada uma com a sua
- **WHEN** a figura compõe uma recorrente em uma moeda e uma fatura em outra
- **THEN** cada moeda é somada com a sua própria antes de qualquer conversão

#### Scenario: A marca de aproximação acompanha o perímetro
- **WHEN** a figura tem mais de um termo e falta taxa para um deles
- **THEN** ela é exibida com a marca de aproximação, pela mesma regra de qualquer figura consolidada

#### Scenario: As faturas custam uma leitura
- **WHEN** o perímetro contém várias faturas a pagar
- **THEN** o devido de todas elas é obtido em uma leitura agregada, e não em uma leitura por fatura
