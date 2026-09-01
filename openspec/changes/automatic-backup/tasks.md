## 1. O destino, como abstração

- [x] 1.1 Definir `BackupDestination` em `commonMain` — colocar um arquivo já capturado, listar o
      que há, remover um. **Nenhuma operação devolve caminho** (D2)
- [x] 1.2 Implementar o degrau 1 nas três plataformas, sobre o armazenamento privado do app
- [x] 1.3 Nome de arquivo com data e hora, ordenável, único; o `BackupFileName` de hoje tem
      granularidade de dia (`BackupFileName.kt:17`)
- [x] 1.4 Identificar um arquivo como cópia deste app pelo conteúdo, com o verificador já existente;
      o nome é só filtro barato (D9)
- [x] 1.5 Testes do degrau 1 em `jvmTest`: escreve, lista, remove; um arquivo que não é cópia deste
      app nunca é removido

## 2. Estado do cofre e a regra da cópia

- [x] 2.1 Preferências do cofre em `multiplatform-settings`, no padrão de `RateSyncStateRepository`:
      ligado, intervalo, retenção, instante da última captura bem-sucedida, destino em vigor
- [x] 2.2 Implementar a pré-condição de captura — só captura se algo foi acrescentado desde a última
      cópia (D8); registrar no código a versão conservadora como alternativa
- [x] 2.3 Nenhum gatilho escreve nada com o cofre desligado (D1) — garantir isso num ponto só, não
      em cada gatilho
- [x] 2.4 Testes: exclusões em sequência produzem uma cópia; inclusão entre duas exclusões produz
      duas; aberturas sem lançamento não produzem nenhuma; cofre desligado não produz nenhuma

## 3. Gatilho periódico

- [x] 3.1 Verificar o vencimento do intervalo na abertura do app e disparar a captura — não há
      `ProcessLifecycleOwner` no projeto hoje; criar o ponto de entrada no shell
- [x] 3.2 Garantir que a captura não compete com a primeira renderização nem bloqueia o uso do app
- [x] 3.3 Testes: intervalo vencido captura, não vencido não captura, meses fechado não inventa
      captura

## 4. Gatilho preventivo

- [x] 4.1 Declarar no domínio a classificação das ações destrutivas e quais classes o gatilho cobre
      (D7); a tela decide **se**, nunca **quais**
- [x] 4.2 Expor em `feature/backup/api` o contrato pelo qual outra feature pede uma captura
      preventiva sem conhecer a implementação
- [x] 4.3 Instalar em `TransactionRepository.deleteTransactionById` e `deleteTransactionsByIds`,
      **acima** do `useWriterConnection` — `VACUUM INTO` recusa rodar dentro de transação
      (`DatabaseCapture.kt:110-116`). Cobre exclusão de transação, de parcelamento e de fatura
- [x] 4.4 Instalar na exclusão de moeda e na remoção de cotação
- [x] 4.5 Instalar no `BackupViewModel`, antes de `replaceContentFrom`
- [x] 4.6 Uma captura preventiva que falha interrompe a ação e pede decisão do usuário antes de
      prosseguir sem cópia
- [x] 4.7 Testes: cada uma das seis ações produz uma cópia anterior que contém o que foi removido;
      exclusão de fachada que o domínio já recusa não dispara captura

## 5. Captura anterior à migração

- [x] 5.1 Ler a versão de schema de um arquivo existente sem abrir pelo Room, com conexão
      descartável do driver — reaproveitar o que o `CandidateVerifier` já faz para um candidato
- [x] 5.2 `getDatabaseBuilder` passa a aceitar um caminho opcional onde capturar antes de migrar:
      **um caminho, e captura; nenhum, e não captura**. `:core:database` não consulta preferência
      nenhuma e não ganha API de arquivo (D11)
- [x] 5.3 Quem monta o banco consulta o cofre e decide o que passar — é aí, e só aí, que o
      interruptor governa esta captura
- [x] 5.4 A cópia fica fora da contagem da retenção e é substituída pela migração seguinte
- [x] 5.5 Uma captura que falha registra e deixa a migração prosseguir — o app abre de qualquer
      jeito
- [x] 5.6 Testes: com destino e migração pendente captura; sem destino não captura; sem migração
      pendente não captura; instalação nova não cria nada para copiar; captura que falha não impede
      a migração

## 6. A tela de cópias guardadas

- [x] 6.1 Rota interna no `impl`, pendurada no `BackupGraph` que já existe; a `api` não ganha rota
      nova, porque ninguém fora do backup navega direto para o histórico (D15)
- [x] 6.2 Listar o destino no momento em que a tela abre; nada de tabela no banco (D9)
- [x] 6.3 A lista no padrão do app: `LazyColumn` com itens keyed e `animateItem()`, cabeçalho de
      data, e cabeçalho de destino no topo com a pasta, a contagem e o tamanho total
- [x] 6.4 Cada item mostra o que basta para ser reconhecido sem ser aberto — quando, tamanho, e o
      rótulo da cópia anterior a uma migração
- [x] 6.5 Ações por item: restaurar, entregar a um destino escolhido na hora (pelo caminho da
      exportação manual, sem capturar de novo) e remover
- [x] 6.6 Estado vazio próprio, que diz quando a primeira cópia acontece
- [x] 6.7 Na tela de backup, o tile que leva até aqui, com o destino em vigor no subtítulo
- [x] 6.8 O seletor de destino como cabeçalho tocável desta tela, e não da tela de backup: aqui é
      onde as cópias moram, lá é se elas acontecem
- [x] 6.9 Capturar agora e importar como cartões rotulados no corpo, presentes em todos os estados —
      inclusive vazio e ilegível, que é justamente quando alguém os procura
- [x] 6.10 Importar passa pelo mesmo portão da restauração antes de pousar, recebe nome desta
      convenção e nunca assume a marca de atual

## 7. Retenção

- [x] 7.1 Retenção por contagem: configurável nos dois degraus, com a opção de não remover nada
- [x] 7.2 Rodar a limpeza somente depois de uma captura bem-sucedida
- [x] 7.3 Testes: limite excedido remove os mais antigos e preserva o recém-capturado; retenção
      desligada não remove nada; captura que falha não remove nada; a cópia anterior à migração
      sobrevive à retenção

## 8. A tela de backup

- [x] 8.1 O interruptor do cofre e os dois gatilhos configuráveis, sobre os tiles que a tela já usa
- [x] 8.2 A linha do último backup bem-sucedido, com o destino, e o sinal de atraso quando ela
      envelhece além do intervalo
- [x] 8.3 A declaração do que o destino em vigor **não** cobre, por degrau
- [x] 8.4 A oferta do cofre junto da confirmação de uma ação destrutiva, que liga o cofre inteiro
- [x] 8.5 Os ajustes num sheet: o preventivo como interruptor no topo, intervalo e retenção em
      escolha segmentada, e uma linha que diz o que a combinação produz — quanto de histórico ela
      cobre e quanto ocupa, do tamanho real das cópias já feitas, com aviso quando a retenção é
      "todas". O rótulo do intervalo é **verificar** a cada, nunca "fazer uma cópia a cada"
- [x] 8.6 Unir `backup_group_export` e `backup_group_restore` num grupo só: hoje cada cabeçalho está
      sobre um tile e repete o próprio item, e juntos eles fazem o par com o backup automático — o
      que o app faz sozinho, e o que sai e entra pela mão do usuário
- [x] 8.7 Reescrever as duas frases de `local-backup` que deixaram de ser verdade — que o app não
      guarda cópias, e que restaurar é irreversível
- [x] 8.8 Chaves novas em `values/strings.xml` e `values-en/strings.xml`, as duas no mesmo commit
- [x] 8.9 `Modifier.testTag` nos elementos novos, e `Modifier.exposeTestTags()` em qualquer modal
      novo que seja raiz de composição

## 9. Fechamento do degrau 1

- [x] 9.1 `./gradlew jvmTest` verde
- [x] 9.2 Verificar em app rodando (não só em teste) que ligar o cofre, capturar, listar, restaurar
      e reter funcionam ponta a ponta em ao menos uma plataforma
- [x] 9.3 Fluxo Maestro para ligar o cofre e ver o histórico, seguindo `.maestro/README.md` §2

## 10. Degrau 2 — spikes, antes de qualquer implementação

- [x] 10.1 **Q1, iOS, aparelho real**: escolher uma pasta com `UIDocumentPickerViewController` e
      `UTTypeFolder`, guardar o bookmark, **reiniciar o aparelho**, resolver e escrever. Medir as
      duas variantes de **resolução** — com e sem `NSURLBookmarkResolutionWithoutImplicitStartAccessing`
      —, porque resolver já inicia o acesso a menos que ela seja passada; na criação não há variante
      que valha comparar no iOS. Critério: se não sobreviver, o degrau 2 no iOS precisa de outro
      desenho e esta entrega para aqui. **Medida uma variante**, a que o app usa: sobreviveu ao
      reboot em aparelho real, e o critério não disparou. A outra segue sem medição
- [x] 10.2 **Q2, Android, aparelho ou emulador**: verificar se uma subpasta de `Download` é
      selecionável em `ACTION_OPEN_DOCUMENT_TREE`; e estabelecer o que decide quais raízes o
      seletor de pasta oferece
- [x] 10.3 Registrar os resultados em `design.md`, fechando Q2; Q1 foi medida depois, em aparelho
      real (12.4) — o critério que pararia a entrega não disparou, e o arquivo diz qual metade do
      par de resolução ficou sem medição

## 11. Degrau 2 — a pasta apontada pelo usuário

- [x] 11.1 Android: `ActivityResultContracts.OpenDocumentTree` + `takePersistableUriPermission`,
      persistindo **apenas o tree URI**; as cópias vão direto na pasta apontada
- [x] 11.2 Android: listar com `DocumentsContract.buildChildDocumentsUriUsingTree` e projeção
      completa, não com `DocumentFile` (1 + 3N consultas)
- [x] 11.3 Android: sugerir `Documents/` ao abrir o seletor — conveniência, não correção: só
      `Download` em si é recusada, e uma subpasta dela serve como destino
- [x] 11.4 iOS: seleção de pasta, bookmark persistido, `start`/`stopAccessingSecurityScopedResource`
      balanceados em `finally`, e `NSFileCoordinator` na escrita e na remoção
- [x] 11.5 iOS: a URL security-scoped nunca atravessa `String` em nenhum ponto do percurso
- [x] 11.6 Desktop: pasta escolhida como caminho, sem cerimônia
- [x] 11.7 Verificar o vínculo na abertura do app, não só ao gravar
- [x] 11.8 Vínculo caído: avisar, oferecer reapontar ou guardar dentro do app, e continuar
      capturando no degrau 1 enquanto a decisão não vem
- [x] 11.9 Reapontar a mesma pasta faz o histórico existente aparecer por inteiro
- [x] 11.10 Trocar de pasta: copiar sem remover a origem, apenas o que a retenção do destino
      comporta, e funcionar com a origem inacessível
- [x] 11.11 A frase da tela sobre cobertura, por destino e não por plataforma: nenhuma regra do
      Android mantém o cofre local, então a frase diz o que a pasta escolhida não cobre (D16)

## 12. Fechamento

- [x] 12.1 `./gradlew jvmTest` verde
- [x] 12.2 Exercitar o degrau 2 em aparelho nas duas plataformas móveis: escolher pasta, capturar,
      reiniciar o app, capturar de novo, listar, reter, restaurar
- [x] 12.3 Exercitar o reencontro: desinstalar, reinstalar, reapontar a pasta, ver o histórico e
      restaurar
- [x] 12.4 Revisar `design.md` — fechar Q1 e Q2 com o que foi medido, e deixar Q3 registrada
- [x] 12.5 Conferir que `PlatformBackupIsOffTest` continua verde e sem alterações

## 13. Depois da entrega

Tudo o que veio depois de `fefce652d` — *"every task in the change is done"* —, que é o commit em
que esta lista se declarou completa. Levantado com `git log fefce652d..HEAD` e lido commit a
commit, não de memória. São 20, e nenhum deles é tarefa nova: são defeitos encontrados por
verificação, por auditoria ou por uso, e os ajustes que vieram com eles.

**Defeitos de comportamento**

- [x] 13.1 O stub de pasta em `VaultFolderTest` não tinha `forgetPrevious`, e o cache do Gradle
      restaurava uma compilação anterior à mudança da interface — os 290 testes do módulo estavam
      **sem rodar** com a suíte verde. Verificado do zero, sem cache (`afc9ee9dd`)
- [x] 13.2 Zerar um ajuste apagava a própria transação de ajuste e pedia cópia preventiva, para a
      única classe (`DERIVED_VALUE`) cujo argumento é que o valor se refaz. Uma captura que
      falhasse virava erro genérico, sem a oferta de prosseguir sem cópia (`b18915e9d`)
- [x] 13.3 A cópia anterior à migração mora no armazenamento do app, e o histórico lia só o degrau
      em vigor: para quem escolheu pasta ela era escrita e nunca vista — a cópia invisível que D1
      recusa. A listagem passa a buscá-la, e a troca de pasta a deixa onde está (`1085d2779`)
- [x] 13.4 Excluir fatura sem transações não anunciava a remoção — o anúncio ia pendurado na
      primeira linha de um laço — enquanto a folha já prometia a cópia (`53d52a054`)
- [x] 13.5 O desempate da ordenação usava o nome cru, e `imported-` ordena acima de qualquer data:
      num destino que empata todos os tempos, a varredura de uma captura podia remover o arquivo
      que aquela captura acabara de escrever (`ca12bccb9`)
- [x] 13.6 A varredura resolvia o degrau de novo, depois do `put`, e podia remover cópias de um
      destino em que aquela captura não pôs nada (`e4d53353b`)
- [x] 13.7 As cinco confirmações destrutivas diziam "não pode ser desfeita" com a caixa da oferta
      marcada — que liga o cofre e tira a cópia antes da ação. Oferta e cobertura eram mutuamente
      exclusivas por construção, e a frase nunca seguia a caixa (`3591ca3a1`)
- [x] 13.8 O tile das cópias estava atrás de `vault.isOn`, e é a única porta para a tela onde a
      pasta é escolhida — numa instalação nova, com o cofre desligado por requisito, não havia
      caminho até o seletor. É o reencontro de D4 (`a195478c1`)

**O que a tela afirma**

- [x] 13.9 O tile do cofre prometia "a cada N dias", que a spec proíbe (`e0abb7664`), e a oferta
      ao lado da confirmação prometia o mesmo, no lugar mais lido (`059d062d3`)
- [x] 13.10 A frase de cobertura dizia que desinstalar leva as cópias junto — falso no desktop, que
      o próprio código descreve como sobrevivente. `VaultCoverage` passa a ser o dono do fato
      (`5af06ad0a`)
- [x] 13.11 A linha "os dados do app são os desta cópia" deixava de ser verdade no primeiro
      lançamento seguinte e continuava exibida; virou passado (`76671ee5b`)
- [x] 13.12 O histórico mostrava só o último segmento da pasta, então duas pastas de mesmo nome
      liam igual. Passa a dizer onde ela está — caminho inteiro no desktop, caminho do document id
      no Android, e o iOS registrado como lacuna, não como regra (`de1cc7569`)

**Apresentação**

- [x] 13.13 A frase da cópia guardada ganhou um glifo inline, para não correr junto com a
      declaração acima dela como um parágrafo só (`6801b4fe6`)
- [x] 13.14 Contas e cartões passam a ter uma linha cada nos dois quadros de fatos, em vez de dois
      números pareados por posição sob um rótulo duplo (`5dfd984d3`)
- [x] 13.15 Três `testTag` estavam sobre o container e não sobre o nó que renderiza a figura, de
      modo que só davam para afirmar presença, nunca o número (`35d9e5f08`)

**Testes**

- [x] 13.16 O caso que a marca do acervo tem permissão de perder — alteração no lugar, sem linha
      nova — tinha cenário na spec e nenhum teste; agora tem, sobre banco real (`60b28f6bd`)
- [x] 13.17 Dois fluxos Maestro para as travessias que nenhuma camada abaixo do app montado
      exercita: o preventivo ponta a ponta e a captura na abertura (`c9488826f`)

**Artefatos**

- [x] 13.18 O requisito da pré-condição passou a dizer o que a marca mede e a nomear o caso que
      deixa passar, em vez de prometer mais do que ela alcança (`71a4b4657`)
- [x] 13.19 A tarefa 10.3 dizia que Q1 seguia aberta depois de ela ter sido medida em aparelho
      (`83a986570`)
- [x] 13.20 O cenário de cobertura da spec e D16 do design passaram a distinguir ler o próprio
      armazenamento de adivinhar o provedor, que é o que aquela decisão recusa — no mesmo commit
      de 13.10, porque o cenário afirmava sem ressalva o que o código dizia errado (`5af06ad0a`)
