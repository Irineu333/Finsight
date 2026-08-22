# Notas de versão — Finsight

Histórico de funcionalidades e correções, versão a versão, do primeiro commit até o que
está em preparação hoje.

O projeto **não usa tags git**: a versão é declarada em `app/android/build.gradle.kts`
(`versionName` / `versionCode`), em `iosApp/project.yml` (`MARKETING_VERSION`) e em
`app/desktop/build.gradle.kts` (`packageVersion`). Cada seção abaixo cobre os commits
alcançáveis a partir do commit que subiu a versão e não alcançáveis a partir do anterior —
é isso que decide em qual release uma mudança saiu, e não a data em que ela foi escrita.
Os release candidates aparecem quando a mudança entrou por um deles.

Estado atual: **1.9.0** em produção, **1.10.0-rc02** em preparação, com trabalho já
mesclado depois do rc02.

---

## 1.10.0 — em preparação

Ciclo aberto desde 08/08/2026. `versionCode` 31 (rc01) e 32 (rc02); o desktop já empacota
como `1.10.0`. Esquema do banco: **10 → 14**.

A versão em que o app deixou de ser mono-moeda.

### Multimoeda e consolidação (rc01)

- **Moeda por conta e por cartão.** Toda conta do plano de contas declara uma moeda, sem
  valor padrão e imutável. Uma conta sem moeda deixou de ser representável.
- **Intenção entre moedas completada no razão.** Uma transação que cruza moedas chega
  incompleta e a fronteira de escrita a completa, lançando o resíduo de cada moeda na conta
  de conversão daquela moeda. `CONVERSION` ganhou tipo próprio, para que `EQUITY` continue
  significando "ajuste".
- **Leitura por moeda.** Nenhuma leitura soma duas moedas num número só: toda leitura que
  pode cruzar contas responde `MoneyByCurrency`. Só as leituras de uma conta única
  continuam escalares.
- **Moeda base como preferência de exibição**, com um único redutor acima do razão. O
  `:core:ledger` não conhece taxa nem moeda base.
- **Marca de aproximação** com três severidades: uma figura diz quando passou por uma taxa,
  qual termo passou, e quando não há taxa que a sustente (`***` no lugar de um zero
  inventado). A marca chegou aos widgets do dashboard, aos cards de orçamento e à lista.
- **Arquivo local de taxas de câmbio**, datado por par, editável nos ajustes e agrupado por
  data. A taxa é dinheiro e lê pelo formatador de dinheiro do app.
- **Troca de moeda base**, dando a cada taxa o seu par — o arquivo passou a agrupar pela
  moeda contraparte.
- **Registro de moedas pelo usuário**: o app parou de embarcar uma lista pronta; o usuário
  registra e arquiva as moedas que usa. O seed nasce com a tabela, traz a moeda do
  dispositivo e toda moeda em que já existe conta.
- **Sincronização de taxas** a partir de uma fonte remota (Ktor), que escreve no mesmo
  arquivo local, limitada por par, e que passa a dever uma rodada quando a base muda.
- **Limite de orçamento denominado na criação**, e não derivado da base.
- **Rendimento de conta**: o que o dinheiro rendeu sozinho é um lançamento próprio,
  separado das demais receitas, com folha própria para escolher a conta em que cai.
- **Detalhe da transação** passou a dizer a taxa que a operação aplicou e a nomear a moeda
  de cada conta quando duas dividem a tela.
- **Relabel das contas legadas** por região, para que um usuário fora do Brasil não herde
  contas em BRL. É redenominação, não conversão: contas, lançamentos e orçamentos mudam de
  moeda na mesma transação, e `Σ = 0` por moeda continua valendo.

### Correções e ajustes (rc01)

- A marca de milhar deixou de ser lida como separador decimal no campo de taxa.
- O botão de salvar do formulário de taxa deixou de cancelar o próprio salvamento.
- Um valor sugerido parou de sobreviver à troca de moeda.
- Um zero parou de guardar a moeda em que por acaso chegou.
- A confirmação de recorrente passou a oferecer cartões também por moeda.
- O `setter` da moeda base foi removido: escrevê-la sozinha era a corrupção.
- A exclusão de uma moeda e o seu arquivo de taxas viraram uma escrita só.
- Migrações reorganizadas: um arquivo por migração, cada uma documentando o que faz e em
  que versão saiu.
- Suíte E2E: décimo terceiro fluxo, cruzando a linha da moeda — algo que nenhuma camada
  abaixo consegue montar.

### Novidades (rc02)

- **Faturas retroativas**: a fatura de qualquer mês pode ser criada na tela de faturas, e a
  fatura escolhida é quem posiciona a data — nunca o contrário. A tela avisa quando a data
  cai fora do período da fatura.
- **Ajuste de saldo datado**: um único ajuste, com alvo numa data, no lugar dos três modos
  anteriores (saldo atual / final / inicial).
- **Detalhe da transação por pernas**: a operação é composta de um card por perna
  monetária, tingido pela direção da perna, com a seta entre os cards, a categoria como
  linha de contexto e o ícone da fachada à frente do nome de cada perna. Não há mais escolha
  de uma ponta.
- **Orçamentos sobrepostos**: uma categoria pode ser medida por quantos orçamentos o
  usuário quiser.
- **Recorrente sem redigitar**: uma transação pode nascer recorrente no próprio lançamento,
  e a confirmação do ciclo passou a editar título e categoria. Pular um ciclo agora pergunta
  antes.
- **Ícone sugerido**: uma conta nova abre com o primeiro ícone do catálogo que nenhuma conta
  aberta esteja usando.
- **Contas fora do total**: desmarcar uma conta no widget de saldo a retira da soma, sem
  alterar a conta.
- Widgets do dashboard passaram a declarar em quais modos de janela aparecem.
- Suíte E2E: décimo quarto fluxo, o da recorrente nascida no próprio lançamento.

### Correções (rc02)

- Criação de cartão falha quando a primeira fatura não abre, em vez de deixar o cartão sem
  fatura.
- A fatura aberta a partir do detalhe é a que o chamador nomeou.
- A perna do cartão leva à fatura, não ao registro do cartão.
- O formulário rola em vez de ser espremido pelo teclado.
- O card de saldo mantém a mesma altura com e sem a marca de aproximação.
- A área de toque de uma categoria ficou contida na linha que a mostra.
- O id do usuário pode não resolver sem abortar o app.

### Depois do rc02 — ainda sem build

- **Gasto sem categoria**: o não classificado ganhou linha e fatia próprias na quebra por
  categoria, no dashboard e no relatório, lendo a própria natureza.
- **Filtro sem categoria**: as cinco listas que filtram por categoria passaram a recortar
  também o que não tem nenhuma, e a oferecer a opção só quando ela encontra algo.
- **A liquidar este mês**: novo widget somando recorrentes do mês e faturas por pagar em
  "A entrar" e "A sair", com cabeçalho opcional.
- **Backlog de bugs** em `issues/`, com regra de entrada, correção e arquivamento, e uma
  skill que o conduz.
- Correções: o seletor de mês só marca o mês quando o ano também bate; a hidratação de uma
  perna usa o mapper dono da conta; a recusa da conta padrão fala na língua do usuário; o
  Suporte decide o vazio pelo escopo que está na tela; o documento exportado do relatório
  diz em que língua foi escrito.

---

## 1.9.0 — 07/08/2026

`versionCode` 30. Esquema do banco: **7 → 10**. 394 commits — a maior versão do projeto.

A versão em que o app trocou o modelo de dados por um razão de partidas dobradas e se
partiu em módulos.

### Arquitetura

- **Modularização por feature no padrão api/impl**, sobre módulos `core`, com convention
  plugins em `build-logic` impondo as regras — um `build.gradle.kts` de feature virou ~5
  linhas. Todas as features foram extraídas em ondas: support, categories, recurring,
  budgets, accounts, report, transactions, creditcards, dashboard e home.
- **`:composeApp` partido em `app/{shared,android,desktop,ios}`**, cada um com uma
  responsabilidade.
- **Navegação reescrita**: o dispatcher deu lugar a `LocalNavController` e a um único
  `NavHost`; toda feature expõe um subgrafo `navigation<XxxGraph>`; marcadores
  `NavRoute`/`NavGraphRoute` tornam toda rota localizável pelas suas implementações.
- **Razão de partidas dobradas como fonte única de verdade.** Todo saldo, fatura, gasto por
  categoria e patrimônio passou a ser `Σ lançamentos`, sem regra de sinal por tipo e sem uma
  segunda forma de calcular um número. `Σ = 0` por moeda é validado num único ponto de
  escrita. O que uma transação *é* passou a ser derivado dos tipos de conta das suas pernas,
  e não persistido.
- **`:core:ledger` extraído**, sem depender de nenhum módulo do app: uma `@Query` que
  nomeie uma tabela de fachada não compila. Duas portas deixam uma fachada participar sem o
  razão saber que ela existe — `DimensionWriteGuard` e `TransactionRemovalHook`.
- **Categoria virou dimensão**, não conta do plano; o sub-razão do cartão também.
- **Política de sinal** com dono único (`DisplayAmount`), aplicada em item e em resumo, em
  transações, contas, cartões e relatório.
- **Suíte E2E com Maestro** em `.maestro/`: 12 fluxos dirigindo o app real, com dispositivo
  fixado, alcance por `testTag` e um relógio móvel no build de debug para alcançar o que
  precisa de tempo.
- Migração colapsada num único `7 → 10`, com cobertura de paridade de leitura e reparo
  explícito do que a v7 permitia (perna órfã, perna apontando para conta apagada, operação
  que nunca somava zero).

### Novidades

- **Navegação adaptativa**: navigation rail e detail pane conforme a largura da janela; o
  detail pane é reativo por id, fixa as ações no rodapé no desktop e faz crossfade entre
  conteúdos. As configurações de widget e o chat do Suporte abrem nele.
- **Desktop de verdade**: estado da janela persistido e restaurado, instalador para Windows,
  ícones, empacotamento em CI e Suporte habilitado via Firebase.
- **Arquivar em vez de apagar**, com desarquivamento, para contas, cartões, categorias e
  recorrentes — incluindo lista de arquivados, guarda contra arquivar a conta padrão, e a
  regra de que quem já usa uma fachada arquivada continua usando.
- **Redesign de Categorias**: filtro por chip no topo, seções e visão de arquivados.
- **Redesign de Recorrentes**: arquivar no lugar de parar, filtro único e confirmação
  atômica do ciclo.
- **Perímetro de saldo**: o resumo de transações e os widgets do dashboard passaram a
  nomear a que escopo se referem (contas, cartões, tudo), com um widget neutro.
- **Estados vazios** em transações, contas, cartões e faturas, dizendo o que está vazio em
  vez de deixar a lista em branco.
- Uma fatura recusada para reabertura passou a dizer o porquê, e o botão some quando a
  regra o recusa.

### Correções

- O pagamento de fatura sai da conta que o usuário escolheu, e não da conta padrão.
- Reabrir uma fatura retroativa não corrompe mais o ciclo.
- Uma fatura paga deixou de ser mutável por edição.
- Uma edição de transação parou de descartar silenciosamente a mudança inteira.
- A lista de transações filtra por natureza, não pela direção da perna.
- Telas com agregado do razão passaram a reagir a escritas.
- O FAB permanece acima da bottom bar durante as transições, e o card vizinho parou de
  pintar sobre a navigation rail.
- Toda folha modal fica acima do teclado, e devolve o espaço quando coberta.
- `TransactionLabel` virou serializável — sem isso o iOS não subia.
- Excluir uma categoria passou a ser guardado contra perda de dados em orçamentos e
  recorrentes, oferecendo arquivar quando a exclusão é recusada.
- `Perf`: leituras por dimensão passaram a ser feitas em lote, eliminando o N+1.

---

## 1.8.0 — 13/04/2026

`versionCode` 27. A versão da telemetria.

### Novidades

- **Analytics (Firebase)** com interface própria e implementação no-op: `screen_view` nas
  13 telas, `user_id` a partir do Firebase Auth, e eventos tipados cobrindo transações,
  contas, transferências, ajustes de saldo, cartões, faturas, parcelamentos, orçamentos,
  recorrentes, categorias, modo de edição do dashboard, relatórios e suporte.
- **Crashlytics** com a mesma separação: interface de domínio, implementação Firebase e
  no-op, id do usuário no arranque e reporte de exceções em ViewModels e repositórios.

### Correções

- Crash no Desktop por dependência direta do Firebase Auth, resolvido abstraindo em
  `AuthService`.
- Eventos de analytics só são registrados quando o use case tem sucesso.
- `AdjustInvoice`, `AdjustBalance`, `SetDefaultAccount` e `EnsureDefaultAccount` passaram a
  capturar todas as exceções, devolvendo `Either`.
- Permissões de advertising ID injetadas pelo Firebase/GMS removidas do Manifest do Android.

---

## 1.7.1 — 09/04/2026

`versionCode` 25. Versão de correção.

- Crash ao abrir o ajuste de saldo de uma conta.
- Crash ao obter o dispatcher de navegação a partir de um modal.

---

## 1.7.0 — 09/04/2026

`versionCode` 24. A versão do dashboard personalizável.

### Novidades

- **Modo de edição do dashboard**, entregue em quatro etapas: prévias reais dos
  componentes, lista unificada com seções de ativos e disponíveis, reordenação por arrastar
  (com alça arrastável sem long press) e modal de configuração por componente, com
  confirmar/cancelar.
- Ações em massa para adicionar e remover no cabeçalho da seção.
- Novos componentes: estatísticas de saldo de cartão de crédito e receita por categoria.
- Configuração de visibilidade de cabeçalho por componente, e espaçamento superior
  configurável.
- Dica de onboarding do modo de edição para novos usuários.
- Atalho para o Suporte na topbar (oculto por padrão).

### Correções

- O modo de edição deixou de ser acionado durante a rolagem, e de esconder o chrome do app.
- Preferências: config padrão aplicada quando nada foi salvo, padrão restaurado ao
  re-adicionar um widget, dashboard vazio preservado após remover componentes e acesso ao
  modo de edição preservado nele.
- Uso da instância configurada de `Json` no repositório de preferências.
- Prévias dos componentes internacionalizadas.

---

## 1.6.0 — 31/03/2026

`versionCode` 20. A versão do suporte in-app.

### Novidades

- **Suporte in-app**: reporte de problemas por chat, com Firebase Firestore como backend —
  mensagens em subcoleção, estados de carregamento, bolhas redesenhadas, divisores por dia,
  auto-scroll para a última mensagem, indicador de resposta pendente, status (Aberto,
  Planejado, Fazendo, Feito), filtro ativo/inativo e transição de elemento compartilhado
  entre o card e o cabeçalho do chat.
- Categorias: `HorizontalPager` para alternar entre as abas de despesa e receita.

### Correções

- O compositor de resposta deixou de sobrepor a barra de navegação do sistema.
- O teclado do chat é dispensado ao tocar fora do compositor.
- Faturas editáveis são recarregadas quando o cartão muda.
- Suporte escondido no Desktop, onde o Firebase ainda não estava disponível.

---

## 1.5.0 — 18/03/2026

`versionCode` 18. Esquema do banco: **5 → 7**. A versão dos relatórios.

### Novidades

- **Relatórios**: telas de configuração e de visualização, seletor de período com opções
  rápidas, perspectivas por conta e por cartão (com perspectiva de fatura e carrossel de
  seleção múltipla), saldo inicial, receita por categoria e cabeçalho de contexto.
- **Exportação em HTML e impressão nativa** nas três plataformas, com o fluxo de
  compartilhamento nativo.
- **Limite de orçamento percentual**, atrelado a uma receita recorrente, com atalho da linha
  de percentual para a recorrente.
- Ícone próprio para cartões de crédito.
- Atalhos para cadastrar categoria ou cartão faltante direto dos modais de formulário.
- Seleção automática do cartão quando só existe um.
- Cards de resumo de receita e despesa pendentes no dashboard.
- Ao confirmar uma recorrente, é possível trocar a conta ou o cartão de destino.

### Correções

- Ajustes passaram a entrar no saldo do relatório, e transferências internas a ficar de
  fora das estatísticas por conta.
- Clique no gasto por categoria corrigido (ordem do modifier) e modal ligado.
- Desmarcar a última conta na configuração do relatório voltou a ser possível.
- Botão de confirmar do formulário de cartão deixou de ser coberto pelo teclado.
- Padding extra no topo do dashboard no iOS.
- `statusBarsPadding` na topbar de transações.

---

## 1.4.0 — 04/03/2026

`versionCode` 13. Esquema do banco: **2 → 5**. A versão das recorrentes.

### Novidades

- **Transações recorrentes**: confirmar, pular, parar e reativar, com ocorrências
  rastreadas por ciclo, filtro por status na topbar e detalhes acessíveis pelo modal da
  operação.
- Dashboard: card de saldo total e linha rápida de contas.
- Contas: seleção e persistência de ícone.
- Categorias: abas por tipo, formulário abrindo com o tipo já selecionado, seletor de
  ícones melhorado.
- Orçamentos: seletor de ícone independente da categoria.
- Filtros por parcelamento nas listagens de operações.
- Atalhos de saldo e fatura no visualizador de ajuste.
- Cinco telas convertidas para estado selado com Loading, Empty e Content.

### Correções

- Dias 29 a 31 passaram a ser suportados no fluxo de pendências.
- Confirmação em data futura bloqueada.
- `createdAt` preservado ao editar uma recorrente.
- Títulos duplicados de orçamento passaram a ser validados.
- Dispensa duplicada de bottom sheet impedida.
- Visibilidade do saldo inicial e do seletor de mês corrigida no tema claro.

### Testes

- Todas as migrações Room existentes passaram a ter testes unitários.

---

## 1.3.2 — 01/03/2026

`versionCode` 8.

- Campos da operação sincronizados ao editar uma transação.
- Verificação de título fixo substituída pela propriedade `isInvoicePayment`.

---

## 1.3.1 — 28/02/2026

`versionCode` 7.

- Conteúdo dependente de locale que ainda estava fixo no código foi resolvido em todo o app.

---

## 1.3.0 — 28/02/2026

`versionCode` 6. A versão da internacionalização.

- **Suporte a inglês** e migração de todas as strings de UI para Compose Resources.
- **Formatador de moeda sensível ao locale do dispositivo** (`expect`/`actual`), no lugar do
  formatador BRL fixo.
- Mensagens de log de erro traduzidas para inglês.
- Espaçamento entre páginas do pager de gastos aumentado no dashboard.

---

## 1.2.0 — 28/02/2026

`versionCode` 5. Esquema do banco: **1 → 2**. A versão dos orçamentos.

- **Orçamentos** com suporte a múltiplas categorias, e sinalização de cor progressiva no
  dashboard e nas telas de orçamento.
- Estados vazios nas telas de cartões e parcelamentos.
- Cores de ação padronizadas nas topbars.
- Parcelas futuras excluídas da seção "Recentes" do dashboard.

---

## 1.1.0 — 27/02/2026

`versionCode` 4.

- Limite total exibido ao lado do limite disponível no cartão.
- Navegação para cartão, fatura e parcelamento a partir das linhas de detalhe da operação.
- Filtro ativo/concluído/todos nos parcelamentos.
- Contagem e valor total de um parcelamento atualizados ao excluir uma de suas operações.
- Ícone de categoria ou de calendário no parcelamento — nunca os dois.
- Título da operação truncado em uma linha.

---

## 1.0.2 — 25/02/2026

`versionCode` 3.

- **Tema claro** seguindo a preferência do sistema.
- `operationId` preservado ao atualizar uma transação.
- Card de pagamento de fatura removido do dashboard sem deixar espaço vazio.

---

## 1.0.1 — 24/02/2026

`versionCode` 2.

- Ícone do app e configuração de assinatura de release no Android.
- Política de privacidade adicionada ao projeto.
- Operações apagadas junto com o cartão de crédito removido.
- Cálculo invertido do valor de ajuste de fatura corrigido.
- Barra de status escura no tema escuro.

---

## 1.0.0 — 22/02/2026

`versionCode` 1. Primeira versão publicada, sob o nome **Finsight**. 176 commits desde o
projeto vazio (22/11/2025).

### O que o app já fazia

- **Dashboard** com visão de saldo, gastos por categoria e resumo de cartões.
- **Transações**: lançar, ver, editar e excluir, com seletor de mês e filtros por conta,
  categoria e tipo.
- **Contas**: multi-conta, ajuste de saldo (com suporte a cheque especial) e transferência
  entre contas.
- **Cartões de crédito**: múltiplos cartões, limite, dia de fechamento e vencimento, e o
  ciclo de faturas — abrir, fechar, pagar e ajustar. Faturas futuras e lançamentos
  retroativos.
- **Parcelamentos**: criação, listagem e exclusão, com o total da compra visível na
  transação.
- **Categorias**: gestão com ícones, categorias padrão no primeiro uso e visão de gastos por
  categoria.

### Base técnica

- Kotlin Multiplatform com Compose Multiplatform (Android, Desktop e iOS), Room, Koin.
- **Arrow (Either)** adotado em formulários, transações e use cases, com abordagem
  *operation-first*.
- Suporte a iOS e migração do projeto Xcode para **XcodeGen**.
- `UiText` para texto seguro de UI, mappers e use cases isolando o domínio.

---

## Notas sobre a reconstrução deste histórico

- O repositório nunca teve tags. As fronteiras entre versões vêm dos commits que alteram
`versionName`/`versionCode`, e cada commit foi atribuído a uma versão por ancestralidade,
não por data — várias features foram escritas em branch e mescladas depois do bump
seguinte, e as datas de autoria deste repositório foram reescritas em algum momento.
- O desktop larga o sufixo de release candidate de propósito: `packageVersion` não o aceita
(`.claude/skills/bump-version/SKILL.md:22`).
- As faixas de esquema do banco vêm do KDoc de cada migração em
`core/database/src/commonMain/kotlin/com/neoutils/finsight/database/migration/`. O app
declara hoje a versão **14**.
