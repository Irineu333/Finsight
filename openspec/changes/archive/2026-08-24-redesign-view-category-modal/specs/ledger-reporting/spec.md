## ADDED Requirements

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
