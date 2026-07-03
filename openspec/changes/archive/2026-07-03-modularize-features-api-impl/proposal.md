# Proposal: Modularização por feature (api/impl)

## Why

O projeto é um monólito `:composeApp` com 430 arquivos organizados por camada, onde qualquer tela pode importar qualquer coisa — o acoplamento cresce sem fronteiras e ficará mais caro corrigir conforme o projeto cresce. A modularização por feature no padrão api/impl estabelece fronteiras impostas pelo compilador antes que isso aconteça, e é o único corte que resolve as dependências bidirecionais reais entre features (ex.: transactions ↔ creditcards) sem módulos artificiais.

## What Changes

- Criação de `build-logic` com convention plugins que padronizam os módulos e **impõem mecanicamente** as regras de dependência.
- Extração de módulos core: `:core:common`, `:core:model`, `:core:database`, `:core:designsystem`, `:core:ui`, `:core:resources`, `:core:analytics`, `:core:crashlytics`, `:core:auth`.
- Extração de 10 features no padrão api/impl: support (piloto), categories, budgets, accounts, creditcards (inclui invoices e installments), recurring, transactions, report, dashboard.
- `:composeApp` reduzido a shell do app: wiring de Koin, NavHost raiz, HomeScreen (abas), entry points de plataforma e configuração do framework iOS — preparado para o split futuro em `:shared`/`:androidApp`/`:desktopApp`.
- Quebra da sealed class `AppRoute` única em rotas `@Serializable` por feature (cada api declara as suas).
- Contrato de chrome do Home (`HomeChrome`) movido para `:core:ui`.
- Migração do DI de módulos por camada (`UseCaseModule`, `RepositoryModule`...) para módulos Koin por feature.
- Framework iOS passa a exportar apenas `:core:*` e `:feature:*:api`; impls linkados mas não exportados.
- **Fora do escopo (changes futuras)**: split do domínio compartilhado por feature (`:core:model` permanece como kernel), split das strings por feature (`:core:resources` mantém `Res` único), split do shell em `:shared`/`:androidApp`/`:desktopApp`.

Sem mudanças de comportamento para o usuário final — refatoração estrutural pura.

## Capabilities

### New Capabilities
- `module-architecture`: estrutura de módulos core + feature api/impl, as 4 regras de dependência, topologia estrela, domínio compartilhado em `:core:model` e papel do `:composeApp` como shell agregador (incl. export seletivo no framework iOS).
- `feature-entry-points`: padrão de acesso cross-feature à UI — interface `<Nome>Entry` por feature na api, navegação por rotas declaradas na api, modais via entry point, critério entry point vs `:core:ui`.
- `build-conventions`: convention plugins do `build-logic` (`kmp.library`, `compose.library`, `feature.api`, `feature.impl`) com verificação mecânica das regras de dependência.

### Modified Capabilities
<!-- Nenhuma — não existem specs anteriores. -->

## Impact

- **Código**: todos os 430 arquivos Kotlin mudam de módulo (pacotes preservados onde possível); `settings.gradle.kts` passa de 1 para ~25 módulos incluídos.
- **Build**: novo build incluído `build-logic`; version catalog compartilhado; cada extração deve manter `./gradlew check` verde nos 3 targets.
- **iOS**: nenhuma mudança no projeto Xcode — `:composeApp:embedAndSignAppleFrameworkForXcode` permanece o alvo (razão pela qual `:composeApp` continua sendo o módulo do framework).
- **Testes**: migram junto com o módulo dono do código testado.
- **Documentação**: `feature/README.md` (já criado) é a referência normativa das regras; esta change deve mantê-lo consistente com o que for implementado.
