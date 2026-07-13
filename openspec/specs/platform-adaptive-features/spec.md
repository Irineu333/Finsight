# platform-adaptive-features Specification

## Purpose
TBD - created by syncing change adaptive-layout. Update Purpose after archive.
## Requirements
### Requirement: Features mobile-only ocultadas por plataforma
Uma feature classificada como `mobile-only` — cuja implementação não é suportada no desktop (ex.: Support, cujo `jvmMain` provê `UnsupportedSupportRepository`) — MUST NOT ser oferecida como ponto de entrada navegável nas plataformas onde não é suportada. A ocultação SHALL usar o eixo de **plataforma** (`isDesktop`), independente do eixo de largura de janela que governa a chrome. A ocultação SHALL ser consistente em **todos** os pontos de entrada da feature na UI — nenhum ponto de entrada pode continuar oferecendo a feature onde ela não funciona.

#### Scenario: Support ocultado no desktop
- **WHEN** o app roda no desktop (`isDesktop == true`)
- **THEN** nenhum ponto de entrada de Support é exibido — nem a quick action no grid do Dashboard, nem o botão de Support no `TopAppBar` do Dashboard

#### Scenario: Support disponível no mobile
- **WHEN** o app roda em uma plataforma mobile (`isDesktop == false`)
- **THEN** os pontos de entrada de Support são exibidos normalmente, independentemente da largura da janela

#### Scenario: Eixos independentes
- **WHEN** o app roda no desktop em uma janela estreita (< Medium)
- **THEN** a chrome usa bottom bar (regida pela largura) e Support permanece oculto (regido pela plataforma), demonstrando que os dois eixos são ortogonais
