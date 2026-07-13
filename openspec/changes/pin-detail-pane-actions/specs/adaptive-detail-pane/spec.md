## MODIFIED Requirements

### Requirement: Rolagem do conteúdo adaptativo em ambas as apresentações

Uma superfície adaptativa SHALL distinguir seu **corpo** (conteúdo) das suas **ações** (rodapé de botões). O corpo SHALL poder rolar quando exceder a altura disponível, tanto na apresentação em **painel** quanto na apresentação em **bottom sheet**. A superfície MUST NOT prover rolagem interna própria concorrente com a rolagem do container; a rolagem do corpo SHALL ser responsabilidade do container (painel ou sheet).

Na apresentação em **painel** (janela larga), as ações SHALL ser **fixadas no rodapé** do painel, fora da área rolável — apenas o corpo rola. Quando houver conteúdo do corpo rolável por baixo do rodapé fixo, uma **elevação/sombra sutil** SHALL separar visualmente as ações do corpo; sem conteúdo rolável, a separação SHALL desaparecer. Uma superfície sem ações SHALL apresentar apenas o corpo rolável, sem rodapé reservado.

Na apresentação em **bottom sheet** (janela estreita), o corpo e as ações SHALL rolar juntos no mesmo container, como hoje; a elevação de separação das ações MUST NOT se aplicar nessa apresentação.

#### Scenario: Conteúdo longo no painel
- **WHEN** uma superfície adaptativa com corpo mais alto que o painel é exibida em janela larga
- **THEN** o corpo pode ser rolado dentro do painel, sem erro de medição por rolagem aninhada, e as ações permanecem fixas no rodapé sem rolar

#### Scenario: Separação visual das ações fixas
- **WHEN** o corpo de uma superfície adaptativa em painel é mais alto que o espaço disponível e há conteúdo rolável por baixo do rodapé
- **THEN** uma elevação/sombra sutil separa as ações fixas do corpo

#### Scenario: Conteúdo curto no painel
- **WHEN** uma superfície adaptativa com corpo mais baixo que o painel é exibida em janela larga
- **THEN** as ações permanecem ancoradas no rodapé do painel (não flutuam junto ao corpo) e não há elevação de separação, pois não há conteúdo rolável

#### Scenario: Superfície sem ações no painel
- **WHEN** uma superfície adaptativa sem ações é exibida em janela larga
- **THEN** o painel apresenta apenas o corpo rolável, sem reservar um rodapé de ações

#### Scenario: Conteúdo longo no bottom sheet
- **WHEN** uma superfície adaptativa com corpo mais alto que a área visível é exibida como bottom sheet em janela estreita
- **THEN** o corpo e as ações rolam juntos dentro do sheet, como hoje, sem rodapé fixo
