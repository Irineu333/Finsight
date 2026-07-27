# Roadmap — Finance (KMP)

App de finanças em Kotlin Multiplatform (Android/Desktop/iOS) com Compose Multiplatform.

Produção atual: **1.8.0**. 
Em preparação: **1.9.0-rc01**.

| Nome                            | Descrição                                                                            | Tipo           | Versão        |
|---------------------------------|--------------------------------------------------------------------------------------|----------------|---------------|
| **Dashboard**                   | Visão geral de saldo, resumo de cartões e lista de contas                            | Funcionalidade | 1.0.0         |
| **Transações**                  | Receitas/despesas com filtros por conta, categoria e mês                             | Funcionalidade | 1.0.0         |
| **Contas**                      | Multi-conta, ajuste de saldo e transferência entre contas                            | Funcionalidade | 1.0.0         |
| **Cartões de crédito**          | Múltiplos cartões e ciclo de faturas (abrir/fechar/pagar/reabrir)                    | Funcionalidade | 1.0.0         |
| **Parcelamentos**               | Acompanhamento de compras parceladas ao longo das faturas                            | Funcionalidade | 1.0.0         |
| **Categorias**                  | Gestão com ícones e acompanhamento de gastos                                         | Funcionalidade | 1.0.0         |
| **Base arquitetural**           | Clean Architecture + MVI/MVVM, Compose Multiplatform, Room, Arrow (Either)           | Arquitetura    | 1.0.0         |
| **iOS via XcodeGen**            | Geração do projeto iOS por XcodeGen                                                  | Arquitetura    | 1.0.0         |
| **Orçamentos (Budgets)**        | Progresso de gasto por categoria                                                     | Funcionalidade | 1.1.0         |
| **i18n**                        | Suporte a inglês, Compose Resources e moeda por locale                               | Arquitetura    | 1.2.0         |
| **Recorrentes**                 | Transações recorrentes (confirmar/pular/parar/reativar)                              | Funcionalidade | 1.4.0         |
| **Relatórios**                  | Seletor de período, exportação HTML e impressão nativa                               | Funcionalidade | 1.5.0         |
| **Budget percentual**           | Limite percentual atrelado a uma receita recorrente                                  | Funcionalidade | 1.5.0         |
| **Dashboard personalizável**    | Modo de edição: adicionar/remover/reordenar e configurar componentes                 | Funcionalidade | 1.6.0 → 1.7.0 |
| **Suporte in-app**              | Reporte de problemas via chat, com Firebase Firestore como backend                   | Funcionalidade | 1.6.0         |
| **Telemetria**                  | Analytics e Crashlytics (Firebase)                                                   | Arquitetura    | 1.8.0         |
| **Modularização**               | `core:*`, feature `api`/`impl`, split `app/{shared,android,desktop,ios}`             | Arquitetura    | 1.9.0         |
| **Navegação adaptativa**        | Navigation rail + detail pane conforme a largura da janela                           | Funcionalidade | 1.9.0         |
| **Desktop Support**             | Estado de janela, empacotamento e Suporte via Firebase                               | Funcionalidade | 1.9.0         |
| **Ledger de partidas dobradas** | Razão de dupla entrada como fonte única de verdade; migração v7→v10 (`:core:ledger`) | Arquitetura    | 1.9.0         |
| **Arquivar/desarquivar**        | Retire/unarchive de contas, cartões, categorias e recorrentes, com arquivados        | Funcionalidade | 1.9.0         |
| **Redesign de Categorias**      | Filtro por chip, seções e visão de arquivados                                        | Funcionalidade | 1.9.0         |
| **Redesign de Recorrentes**     | Arquivar no lugar de parar, filtro único e confirmação atômica do ciclo              | Funcionalidade | 1.9.0         |
| **Perímetro de saldo**          | Escopo contas/cartões/tudo no resumo de transações e nos widgets do dashboard        | Funcionalidade | 1.9.0         |
| **Estados vazios**              | Transações, contas, cartões e faturas dizem o que está vazio                         | Funcionalidade | 1.9.0         |
| **Política de sinal**           | `DisplayAmount` como dono único do sinal exibido, em item e em resumo                | Arquitetura    | 1.9.0         |
