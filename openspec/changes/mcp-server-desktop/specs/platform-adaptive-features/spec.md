## ADDED Requirements

### Requirement: Features desktop-only ocultadas por plataforma

O eixo de plataforma SHALL valer nos **dois** sentidos. Uma feature classificada como `desktop-only` —
cuja implementação existe apenas no target JVM de desktop — MUST NOT ser oferecida como ponto de
entrada navegável no Android nem no iOS, pela mesma regra e pelo mesmo eixo (`isDesktop`) que
oculta uma feature `mobile-only` no desktop.

A ocultação SHALL ser consistente em **todos** os pontos de entrada da feature, e SHALL alcançar
também a rota: uma feature oculta MUST NOT ser alcançável por navegação direta na plataforma
onde não é suportada.

O **servidor MCP** é `desktop-only`: ele escuta numa porta do processo de desktop, e nada
equivalente existe nas plataformas móveis. A sua entrada em Configurações SHALL aparecer apenas
no desktop.

#### Scenario: Entrada do MCP no desktop
- **WHEN** o app roda no desktop
- **THEN** Configurações oferece a entrada do servidor MCP

#### Scenario: Entrada do MCP oculta no mobile
- **WHEN** o app roda no Android ou no iOS
- **THEN** Configurações não oferece a entrada do servidor MCP, e a sua rota não é alcançável

#### Scenario: A ocultação não depende da largura da janela
- **WHEN** o app roda no desktop numa janela estreita
- **THEN** a entrada do servidor MCP continua sendo oferecida — a disponibilidade é regida pela
  plataforma, não pelo layout
