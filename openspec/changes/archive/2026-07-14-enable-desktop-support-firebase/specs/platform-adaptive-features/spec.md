## MODIFIED Requirements

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
