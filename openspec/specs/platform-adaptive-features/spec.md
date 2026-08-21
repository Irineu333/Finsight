# platform-adaptive-features Specification

## Purpose
TBD - created by syncing change adaptive-layout. Update Purpose after archive.
## Requirements
### Requirement: Features mobile-only ocultadas por plataforma
Uma feature classificada como `mobile-only` — cuja implementação não é suportada no desktop — MUST NOT ser oferecida como ponto de entrada navegável nas plataformas onde não é suportada. A ocultação SHALL usar o eixo de **plataforma** (`isDesktop` e/ou o flag `mobileOnly` do catálogo de destinos), independente do eixo de largura de janela que governa o layout do seletor. A ocultação SHALL ser consistente em **todos** os pontos de entrada da feature na UI — nenhum ponto de entrada pode continuar oferecendo a feature onde ela não funciona.

Uma feature que passa a ser suportada no desktop (com backend disponível no target JVM) MUST NOT ser classificada como `mobile-only` e SHALL ter seus pontos de entrada exibidos no desktop. O Support deixa de ser `mobile-only`: seu `jvmMain` passa a prover o `FirebaseSupportRepository` real (Firestore + Auth via `firebase-java-sdk`), portanto seus pontos de entrada — o rail (desktop) e o botão de Support no `TopAppBar` do Dashboard — SHALL ser exibidos no desktop.

#### Scenario: Support disponível no desktop
- **WHEN** o app roda no desktop (`isDesktop == true`)
- **THEN** os pontos de entrada de Support são exibidos — o rail inclui o destino de Support e o botão de Support no `TopAppBar` do Dashboard permanece visível

#### Scenario: Support disponível no mobile
- **WHEN** o app roda em uma plataforma mobile (`isDesktop == false`)
- **THEN** os pontos de entrada de Support são exibidos normalmente no grid de quick actions, independentemente da largura da janela

#### Scenario: Grid é afordância mobile-only
- **WHEN** a largura da janela é ≥ Medium e o rail está ativo
- **THEN** o grid de quick actions não é exibido — o rail é o ponto de entrada das features, e eventuais destinos `mobileOnly` continuam fora dele

#### Scenario: Eixo de plataforma continua ortogonal ao de largura
- **WHEN** o app roda no desktop em uma janela estreita (< Medium)
- **THEN** o seletor usa bottom bar (regido pela largura) enquanto a disponibilidade das features é regida pela plataforma, demonstrando que os dois eixos permanecem ortogonais

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
