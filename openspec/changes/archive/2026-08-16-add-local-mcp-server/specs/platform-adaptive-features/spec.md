## ADDED Requirements

### Requirement: Features desktop-only ocultadas por plataforma

Uma feature classificada como `desktop-only` MUST NOT ser oferecida como ponto de entrada
navegável nas plataformas onde não é suportada — sendo `desktop-only` aquela cuja implementação
depende de recurso que só existe no target JVM de desktop. A ocultação SHALL usar o mesmo eixo
de **plataforma** que governa a direção oposta, e SHALL ser consistente em **todos** os pontos
de entrada da feature na UI.

O eixo de plataforma é, portanto, simétrico: ele nomeia tanto o que não funciona no desktop
quanto o que só funciona nele. O servidor MCP é a primeira feature da segunda direção — ele
escuta num socket local e é iniciado pelo processo do app desktop, que é o único a ter um.

Esta ocultação SHALL permanecer ortogonal ao eixo de largura de janela: uma janela estreita no
desktop continua sendo desktop, e a feature continua oferecida.

#### Scenario: Feature desktop-only no desktop
- **WHEN** o app roda no desktop e a feature é classificada como `desktop-only`
- **THEN** os seus pontos de entrada são exibidos

#### Scenario: Feature desktop-only no mobile
- **WHEN** o app roda em uma plataforma mobile
- **THEN** nenhum ponto de entrada da feature é exibido, em nenhuma superfície

#### Scenario: Largura de janela não decide disponibilidade
- **WHEN** o app roda no desktop numa janela estreita
- **THEN** a feature `desktop-only` continua sendo oferecida, porque quem decide é a plataforma e não a largura

#### Scenario: A ocultação alcança todo ponto de entrada, seja qual for a afordância
- **WHEN** uma feature `desktop-only` é oferecida por um ponto de entrada que não pertence ao catálogo de destinos — um item dentro de outra tela, por exemplo
- **THEN** esse ponto de entrada também deixa de ser exibido no mobile, porque a regra é sobre a feature e não sobre a afordância que a oferece

#### Scenario: Os dois sentidos coexistem
- **WHEN** os pontos de entrada do app são inspecionados numa plataforma
- **THEN** os de features não suportadas nela não aparecem, nos dois sentidos, e os de features sem classificação aparecem em ambas
