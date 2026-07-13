# platform-adaptive-features Specification

## Purpose
TBD - created by syncing change adaptive-layout. Update Purpose after archive.
## Requirements
### Requirement: Features mobile-only ocultadas por plataforma
Uma feature classificada como `mobile-only` — cuja implementação não é suportada no desktop (ex.: Support, cujo `jvmMain` provê `UnsupportedSupportRepository`) — MUST NOT ser oferecida como ponto de entrada navegável nas plataformas onde não é suportada. A ocultação SHALL usar o eixo de **plataforma** (`isDesktop` e/ou o flag `mobileOnly` do catálogo de destinos), independente do eixo de largura de janela que governa o layout do seletor. A ocultação SHALL ser consistente em **todos** os pontos de entrada da feature na UI — nenhum ponto de entrada pode continuar oferecendo a feature onde ela não funciona. Os pontos de entrada incluem o rail (desktop), o grid de quick actions (afordância **mobile-only**) e o botão de Support no `TopAppBar` do Dashboard.

#### Scenario: Support ocultado no desktop
- **WHEN** o app roda no desktop (`isDesktop == true`)
- **THEN** nenhum ponto de entrada de Support é exibido — o rail não inclui destinos `mobileOnly`, o grid de quick actions não é exibido no desktop, e o botão de Support no `TopAppBar` do Dashboard permanece oculto

#### Scenario: Support disponível no mobile
- **WHEN** o app roda em uma plataforma mobile (`isDesktop == false`)
- **THEN** os pontos de entrada de Support são exibidos normalmente no grid de quick actions, independentemente da largura da janela

#### Scenario: Grid é afordância mobile-only
- **WHEN** a largura da janela é ≥ Medium e o rail está ativo
- **THEN** o grid de quick actions não é exibido — o rail é o ponto de entrada das features, e os destinos `mobileOnly` continuam fora dele

#### Scenario: Eixos independentes
- **WHEN** o app roda no desktop em uma janela estreita (< Medium)
- **THEN** o seletor usa bottom bar (regido pela largura) e Support permanece oculto (regido pela plataforma), demonstrando que os dois eixos são ortogonais

