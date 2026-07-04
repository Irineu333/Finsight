# build-conventions — Delta (split-app-modules)

## ADDED Requirements

### Requirement: Convention plugin do shell de app
O `build-logic` SHALL prover a convenção `finsight.app.shared`, aplicada exclusivamente pelo `:app:shared`, concentrando: targets KMP (Android library, JVM, iOS), Compose Multiplatform, serialization e opt-ins comuns. A convenção SHALL ser a única que admite dependências de módulos `feature:*:impl`; o `build.gradle.kts` do `:app:shared` SHALL conter apenas a aplicação da convenção e suas dependências.

#### Scenario: Shell sob convenção
- **WHEN** o `:app:shared` é configurado
- **THEN** seu `build.gradle.kts` aplica `finsight.app.shared` e não repete configuração de targets, compiler options ou Compose

#### Scenario: Módulo comum tenta depender de impl
- **WHEN** um módulo sob convenção de library ou de feature declara dependência de um `feature:*:impl`
- **THEN** o build falha na verificação de regras, pois apenas a convenção `finsight.app.shared` admite dependências de `impl`

### Requirement: Build explícito nos módulos de plataforma
Os módulos `:app:android`, `:app:desktop` e `:app:ios` SHALL manter `build.gradle.kts` explícito, sem convention plugin — signing, empacotamento desktop e framework iOS são configurações únicas, sem segundo consumidor que justifique convenção. Configuração comum acidentalmente duplicada entre eles SHOULD ser movida para o `build-logic` apenas quando um segundo consumidor real existir.

#### Scenario: Configuração única de plataforma
- **WHEN** uma configuração existe em exatamente um módulo de plataforma (ex.: signing config, `nativeDistributions`, `export()` do framework)
- **THEN** ela reside no `build.gradle.kts` desse módulo, sem abstração no `build-logic`
