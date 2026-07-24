## MODIFIED Requirements

### Requirement: Perspectiva como argumento de mapeamento
Quando uma transação puder ser apresentada sob mais de um ponto de vista (a conta ou a fatura em que aparece), a perspectiva SHALL ser um argumento do mapeamento, e MUST NOT ser um campo do modelo de UI resolvido preguiçosamente na leitura. A resolução da perspectiva SHALL ocorrer no momento do mapeamento, onde a ausência de correspondência é tratável.

A perspectiva SHALL poder ser expressa tanto por uma **conta identificada** quanto pela **natureza de conta** cuja perna a tela está observando. As duas formas SHALL resolver-se pelo mesmo mecanismo e produzir o mesmo tratamento de ausência: quando nenhuma perna corresponder, o mapper não produz modelo de UI e o chamador omite o item.

Quando uma tela exibir um total derivado de um perímetro de contas, a perspectiva do item SHALL ser a desse perímetro, de modo que o sinal exibido no item concorde com o efeito daquele lançamento sobre o total exibido. MUST NOT ocorrer de um item ser apresentado pela perna de um perímetro e somado em outro.

#### Scenario: Mesma transação em duas telas
- **WHEN** uma transferência entre contas é exibida na tela da conta de origem e na da conta de destino
- **THEN** o mapper é invocado com a perspectiva de cada conta e produz um modelo de UI distinto para cada tela

#### Scenario: Perspectiva sem correspondência
- **WHEN** um mapeamento é solicitado com uma perspectiva que não corresponde a nenhuma perna da transação
- **THEN** o mapper não produz modelo de UI para aquela transação e o chamador a omite da lista, sem lançar — e a ausência MUST NOT se manifestar como falha na leitura de uma propriedade

#### Scenario: Perspectiva de cartão é sempre construível
- **WHEN** uma perspectiva de cartão é construída para qualquer cartão, inclusive um recém-criado sem nenhum lançamento
- **THEN** ela é construível, porque todo cartão possui conta no plano de contas desde a sua criação (`account-lifecycle`), e a resolução não depende de tratamento para vínculo ausente

#### Scenario: Perspectiva por natureza de conta
- **WHEN** uma lista observa o perímetro das contas `LIABILITY` e exibe um pagamento de fatura
- **THEN** o mapper é invocado com a perspectiva daquela natureza e o item é apresentado pela perna `LIABILITY`, lendo como redução da dívida

#### Scenario: Sinal do item concorda com o total da tela
- **WHEN** um lançamento é exibido em uma tela cujo total deriva de um perímetro de contas
- **THEN** o sinal exibido no item corresponde ao efeito desse lançamento sobre aquele total
