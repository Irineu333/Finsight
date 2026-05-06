## Why

O projeto vive num único módulo `:composeApp`, o que elimina paralelismo de build, remove boundaries explícitos entre features e permite que qualquer código acesse qualquer outro sem restrição. A modularização por feature com padrão api/impl estabelece isolamento de compilação, fronteiras de domínio claras e prepara o projeto para crescer sem perder coesão.

## What Changes

- **BREAKING** Extração de `Recurring.Type` como enum próprio — remove dependência de `Recurring` em `Transaction.Type`, quebrando ciclo de dependência entre os módulos futuros
- **BREAKING** `Category.iconKey: String` substitui `CategoryLazyIcon` no modelo de domínio — remove dependência de UI no domínio
- **BREAKING** `Budget.iconKey: String` substitui `CategoryLazyIcon` no modelo de domínio — mesma razão
- Criação de módulos `:core:*` (platform, ui, utils, analytics, auth, database)
- Criação de módulos `:feature:X:api` e `:feature:X:impl` para cada feature
- Criação de `build-logic/` com convention plugins KMP para evitar repetição de configuração
- Atualização de `settings.gradle.kts` para incluir todos os novos módulos
- O módulo `:composeApp` é esvaziado progressivamente até virar `:app`

## Capabilities

### New Capabilities

- `module-structure`: Estrutura de módulos Gradle do projeto — quais módulos existem, suas responsabilidades e regras de dependência entre eles
- `build-logic`: Convention plugins KMP para padronizar configuração de build em múltiplos módulos

### Modified Capabilities

*(nenhuma — mudanças de domínio são breaking changes de implementação, não de requisitos de produto)*

## Impact

**Código afetado:**
- Todo o código de `composeApp/src/commonMain/` é redistribuído entre os novos módulos
- `Recurring` e `RecurringForm` — mudança de `Transaction.Type` para `Recurring.Type`
- `RecurringMapper` em `:core:database` — adiciona conversão `Recurring.Type ↔ Transaction.Type`
- `Category` e `Budget` — remoção de `CategoryLazyIcon`, adição de `iconKey: String`; todos os usos na UI precisam construir `CategoryLazyIcon` a partir da `iconKey`

**Build:**
- `settings.gradle.kts` — cresce de 1 para ~25 módulos
- Cada módulo KMP exige 6 configurações de KSP (Android, iOS x3, JVM, CommonMain) onde aplicável
- `build-logic/` com plugins Gradle Kotlin DSL

**Dependências sem mudança:**
- Room permanece centralizado em `:core:database` — sem multi-module Room
- Firebase, Koin, Arrow, Compose Multiplatform — mesmas versões, redistribuídas por módulo