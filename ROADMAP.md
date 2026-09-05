# Roadmap — Finance (KMP)

App de finanças em Kotlin Multiplatform (Android/Desktop/iOS) com Compose Multiplatform.

Produção atual: **1.10.0**.
Há um ciclo aberto depois dela, ainda sem número declarado: as linhas com `—` na coluna de versão
estão no `main`, escritas e testadas, e não saíram em release nenhuma.

## Entregue

| Nome                            | Descrição                                                                             | Tipo           | Versão        |
|---------------------------------|---------------------------------------------------------------------------------------|----------------|---------------|
| **Dashboard**                   | Visão geral de saldo, resumo de cartões e lista de contas                             | Funcionalidade | 1.0.0         |
| **Transações**                  | Receitas/despesas com filtros por conta, categoria e mês                              | Funcionalidade | 1.0.0         |
| **Contas**                      | Multi-conta, ajuste de saldo e transferência entre contas                             | Funcionalidade | 1.0.0         |
| **Cartões de crédito**          | Múltiplos cartões e ciclo de faturas (abrir/fechar/pagar/reabrir)                     | Funcionalidade | 1.0.0         |
| **Parcelamentos**               | Acompanhamento de compras parceladas ao longo das faturas                             | Funcionalidade | 1.0.0         |
| **Categorias**                  | Gestão com ícones e acompanhamento de gastos                                          | Funcionalidade | 1.0.0         |
| **Base arquitetural**           | Clean Architecture + MVI/MVVM, Compose Multiplatform, Room, Arrow (Either)            | Arquitetura    | 1.0.0         |
| **iOS via XcodeGen**            | Geração do projeto iOS por XcodeGen                                                   | Arquitetura    | 1.0.0         |
| **Orçamentos (Budgets)**        | Progresso de gasto por categoria                                                      | Funcionalidade | 1.2.0         |
| **i18n**                        | Suporte a inglês, Compose Resources e moeda por locale                                | Arquitetura    | 1.3.0         |
| **Recorrentes**                 | Transações recorrentes (confirmar/pular/parar/reativar)                               | Funcionalidade | 1.4.0         |
| **Relatórios**                  | Seletor de período, exportação HTML e impressão nativa                                | Funcionalidade | 1.5.0         |
| **Budget percentual**           | Limite percentual atrelado a uma receita recorrente                                   | Funcionalidade | 1.5.0         |
| **Suporte in-app**              | Reporte de problemas via chat, com Firebase Firestore como backend                    | Funcionalidade | 1.6.0         |
| **Dashboard personalizável**    | Modo de edição: adicionar/remover/reordenar e configurar componentes                  | Funcionalidade | 1.7.0         |
| **Telemetria**                  | Analytics e Crashlytics (Firebase)                                                    | Arquitetura    | 1.8.0         |
| **Modularização**               | `core:*`, feature `api`/`impl`, split `app/{shared,android,desktop,ios}`              | Arquitetura    | 1.9.0         |
| **Navegação adaptativa**        | Navigation rail + detail pane conforme a largura da janela                            | Funcionalidade | 1.9.0         |
| **Desktop Support**             | Estado de janela, empacotamento e Suporte via Firebase                                | Funcionalidade | 1.9.0         |
| **Ledger de partidas dobradas** | Razão de dupla entrada como fonte única de verdade; migração v7→v10 (`:core:ledger`)  | Arquitetura    | 1.9.0         |
| **Arquivar/desarquivar**        | Retire/unarchive de contas, cartões, categorias e recorrentes, com arquivados         | Funcionalidade | 1.9.0         |
| **Redesign de Categorias**      | Filtro por chip, seções e visão de arquivados                                         | Funcionalidade | 1.9.0         |
| **Redesign de Recorrentes**     | Arquivar no lugar de parar, filtro único e confirmação atômica do ciclo               | Funcionalidade | 1.9.0         |
| **Perímetro de saldo**          | Escopo contas/cartões/tudo no resumo de transações e nos widgets do dashboard         | Funcionalidade | 1.9.0         |
| **Estados vazios**              | Transações, contas, cartões e faturas dizem o que está vazio                          | Funcionalidade | 1.9.0         |
| **Política de sinal**           | `DisplayAmount` como dono único do sinal exibido, em item e em resumo                 | Arquitetura    | 1.9.0         |
| **E2E com Maestro**             | Suíte de fluxos em `.maestro/`, com dispositivo fixado e alcance por `testTag`        | Arquitetura    | 1.9.0         |
| **Multimoeda**                  | Moeda por conta/cartão, intenção entre moedas completada no razão e leitura por moeda | Funcionalidade | 1.10.0        |
| **Consolidação e moeda base**   | Moeda base como preferência de exibição, redutor único e marca de aproximação         | Arquitetura    | 1.10.0        |
| **Arquivo de taxas de câmbio**  | Taxas datadas por par, editáveis nos ajustes e agrupadas por data                     | Funcionalidade | 1.10.0        |
| **Sincronização de taxas**      | Fonte remota alimentando o arquivo local, limitada por par                            | Funcionalidade | 1.10.0        |
| **Registro de moedas**          | O usuário registra e arquiva as moedas, em vez de receber uma lista pronta            | Funcionalidade | 1.10.0        |
| **Rendimento de conta**         | Lançamento do que o dinheiro rendeu sozinho, separado das demais receitas             | Funcionalidade | 1.10.0        |
| **Faturas retroativas**         | Criar a fatura de qualquer mês e deixar a fatura escolhida posicionar a data          | Funcionalidade | 1.10.0        |
| **Ajuste de saldo datado**      | Um único ajuste, alvo numa data, no lugar de saldo atual/final/inicial                | Funcionalidade | 1.10.0        |
| **Detalhe por pernas**          | Detalhe da transação como um card por perna monetária, sem escolher uma ponta         | Funcionalidade | 1.10.0        |
| **Orçamentos sobrepostos**      | Uma categoria medida por quantos orçamentos o usuário quiser                          | Funcionalidade | 1.10.0        |
| **Recorrentes sem redigitar**   | Nascer recorrente no próprio lançamento e confirmar o ciclo com título e categoria    | Funcionalidade | 1.10.0        |
| **Contas fora do total**        | Desmarcar uma conta no widget de saldo a retira do total, sem alterar a conta         | Funcionalidade | 1.10.0        |
| **Ícone sugerido**              | Conta nova abre com o primeiro ícone do catálogo que nenhuma conta aberta usa         | Funcionalidade | 1.10.0        |
| **Gasto sem categoria**         | Linha e fatia próprias para o não classificado, no dashboard e no relatório           | Funcionalidade | 1.10.0        |
| **Filtro sem categoria**        | As cinco listas que filtram por categoria recortam também o que não tem nenhuma       | Funcionalidade | 1.10.0        |
| **A liquidar este mês**         | Recorrentes do mês e faturas por pagar somadas em "A entrar" e "A sair"               | Funcionalidade | 1.10.0        |
| **Backlog de bugs**             | Bugs como arquivos em `issues/`, com regra de entrada, correção e arquivamento        | Arquitetura    | 1.10.0        |
| **Transferência editável**      | Corrigir uma transferência no lugar, pelo mesmo formulário que a registrou            | Funcionalidade | 1.10.0        |
| **Título na transferência**     | A transferência guarda o motivo do movimento, sob a cadeia título > categoria > forma | Funcionalidade | 1.10.0        |
| **Pagamento de fatura único**   | Um formulário que nomeia a fatura; o estado decide o modo, e a retroativa é pagável   | Funcionalidade | 1.10.0        |
| **Pagamento corrigível**        | Corrigir um pagamento parcial de fatura no lugar, sem apagar e refazer                | Funcionalidade | 1.10.0        |
| **Categoria contra a média**    | O mês de uma categoria lido contra a média de 12 meses fechados, sem navegar no tempo | Funcionalidade | 1.10.0        |
| **Recorrentes por ciclo**       | Os ciclos do mês em quatro estados, com resumo do mês e arquivadas em tela própria    | Funcionalidade | 1.10.0        |
| **Listas em linha**             | Recorrentes e Orçamentos trocam a ficha pela linha: de ~180 e ~232dp para 64 e ~62dp  | Funcionalidade | 1.10.0        |
| **Ajuste no detalhe único**     | Um ajuste abre o mesmo detalhe de qualquer operação, nomeado pelo alvo que corrige    | Funcionalidade | 1.10.0        |
| **Telemetria de arquivar**      | Arquivar deixou de ser reportado como apagar, e reabrir deixou de não ser reportado   | Arquitetura    | 1.10.0        |
| **Backup local**                | Exportar e restaurar o acervo num arquivo, com o backup automático da plataforma desligado | Funcionalidade | —             |
| **Cofre de backups**            | O app guarda cópias sozinho por três gatilhos, com retenção e histórico no destino    | Funcionalidade | —             |
| **Botão de ação único**         | Um FAB para o app: a tela publica as ações, a casca decide a forma e a posição        | Funcionalidade | —             |
| **Snapshot do banco**           | Capturar, verificar em isolamento e substituir o conteúdo, sem reiniciar o app        | Arquitetura    | —             |
| **Prelúdio de remoção**         | O razão anuncia a remoção antes das linhas saírem; calar exige `@OptIn`               | Arquitetura    | —             |
| **Servidor MCP**                | Servidor local atrás de token, no socket da janela e por stdio com ela aberta ou não  | Funcionalidade | —             |
| **Superfície de 58 ferramentas** | Perguntas, catálogo, registro e operações — uma tela com um agente no lugar de quem lê | Funcionalidade | —             |
| **Permissões por eixo**         | Ler, registrar, apagar e operar decidem quais ferramentas existem, e o retido é dito   | Funcionalidade | —             |
| **Registro de atividade**       | O que um agente mudou fica registrado, com retenção declarada e alcance ao lançamento | Funcionalidade | —             |
| **Use case por id**             | O id carrega a implementação e o agregado delega; 45 use cases chegaram à `api`        | Arquitetura    | —             |
| **Plataforma nos dois sentidos** | O que só funciona no desktop se oculta pelo mesmo eixo do que não funciona nele       | Arquitetura    | —             |

## Planejado

Nada começado: sem change em `openspec/` e sem código no repositório.

| Nome                            | Descrição                                                                             | Tipo           |
|---------------------------------|---------------------------------------------------------------------------------------|----------------|
| **Sincronização**               | O mesmo razão em mais de um dispositivo, sem exportar e importar à mão                | Funcionalidade |
| **Open Finance**                | Lançamentos e saldos vindos do banco pelo Open Finance, em vez de digitados           | Funcionalidade |
| **Assistente de IA**            | Perguntar e lançar em linguagem natural **dentro** do app, sem um cliente MCP de fora | Funcionalidade |
| **Cofrinhos**                   | Reservar parte do saldo de uma conta para um fim, sem tirá-lo da conta                | Funcionalidade |
| **Criptomoedas**                | Cripto como moeda de conta, com as casas decimais e a cotação que ela exige           | Funcionalidade |
| **Saldo guardado**              | A conta mostra o que está guardado à parte do que resta livre para gastar             | Funcionalidade |
| **Metas**                       | Objetivo de valor com prazo, medido pelo que já foi guardado para ele                 | Funcionalidade |
