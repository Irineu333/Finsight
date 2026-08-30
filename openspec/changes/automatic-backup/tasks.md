## 1. A rede pré-migração (`:core:database`)

Entrega valor sozinha, não depende do cofre e não depende de nenhuma pergunta aberta.

- [ ] 1.1 Ler a versão de schema de um arquivo existente sem abrir pelo Room, com conexão
      descartável do driver — reaproveitar o que o `CandidateVerifier` já faz para um candidato
- [ ] 1.2 Capturar o conteúdo anterior antes de a cadeia de migrações rodar, com `captureInto`,
      apenas quando a versão do arquivo for menor que a do app
- [ ] 1.3 Receber o destino de fora: `getDatabaseBuilder` passa a aceitar onde escrever a cópia, e
      quem constrói o banco por plataforma fornece o caminho (`:core:database` não ganha API de
      arquivo — D7 de `local-backup`)
- [ ] 1.4 Substituir a cópia anterior a cada nova migração; garantir que ela não é alcançada por
      nenhuma política de retenção do cofre
- [ ] 1.5 Uma captura que falha registra e deixa a migração prosseguir — o app abre de qualquer
      jeito
- [ ] 1.6 Testes: com migração pendente captura; sem migração pendente não captura; instalação nova
      não cria nada para copiar; captura que falha não impede a migração

## 2. O destino, como abstração

- [ ] 2.1 Definir `BackupDestination` em `commonMain` — colocar um arquivo já capturado, listar o
      que há, remover um. **Nenhuma operação devolve caminho** (D2)
- [ ] 2.2 Implementar o degrau 1 nas três plataformas, sobre o armazenamento privado do app
- [ ] 2.3 Nome de arquivo com data e hora, ordenável, único; o `BackupFileName` de hoje tem
      granularidade de dia (`BackupFileName.kt:17`)
- [ ] 2.4 Identificar um arquivo como cópia deste app pelo conteúdo, com o verificador já existente;
      o nome é só filtro barato (D9)
- [ ] 2.5 Testes do degrau 1 em `jvmTest`: escreve, lista, remove; um arquivo que não é cópia deste
      app nunca é removido

## 3. Estado do cofre e a regra da cópia

- [ ] 3.1 Preferências do cofre em `multiplatform-settings`, no padrão de `RateSyncStateRepository`:
      ligado, intervalo, retenção, instante da última captura bem-sucedida, destino em vigor
- [ ] 3.2 Implementar a pré-condição de captura — só captura se algo foi acrescentado desde a última
      cópia (D8); registrar no código a versão conservadora como alternativa
- [ ] 3.3 Testes: exclusões em sequência produzem uma cópia; inclusão entre duas exclusões produz
      duas; aberturas sem lançamento não produzem nenhuma

## 4. Gatilho periódico

- [ ] 4.1 Verificar o vencimento do intervalo na abertura do app e disparar a captura — não há
      `ProcessLifecycleOwner` no projeto hoje; criar o ponto de entrada no shell
- [ ] 4.2 Garantir que a captura não compete com a primeira renderização nem bloqueia o uso do app
- [ ] 4.3 Testes: intervalo vencido captura, não vencido não captura, meses fechado não inventa
      captura

## 5. Gatilho preventivo

- [ ] 5.1 Declarar no domínio a classificação das ações destrutivas e quais classes o gatilho cobre
      (D7); a tela decide **se**, nunca **quais**
- [ ] 5.2 Expor em `feature/backup/api` o contrato pelo qual outra feature pede uma captura
      preventiva sem conhecer a implementação
- [ ] 5.3 Instalar em `TransactionRepository.deleteTransactionById` e `deleteTransactionsByIds`,
      **acima** do `useWriterConnection` — `VACUUM INTO` recusa rodar dentro de transação
      (`DatabaseCapture.kt:110-116`). Cobre exclusão de transação, de parcelamento e de fatura
- [ ] 5.4 Instalar na exclusão de moeda e na remoção de cotação
- [ ] 5.5 Instalar no `BackupViewModel`, antes de `replaceContentFrom`
- [ ] 5.6 Uma captura preventiva que falha interrompe a ação e pede decisão do usuário antes de
      prosseguir sem cópia
- [ ] 5.7 Testes: cada uma das seis ações produz uma cópia anterior que contém o que foi removido;
      exclusão de fachada que o domínio já recusa não dispara captura

## 6. Histórico e retenção

- [ ] 6.1 Listar o destino no momento em que a tela abre; nada de tabela no banco (D9)
- [ ] 6.2 Ler o `snapshot_meta` de um arquivo só quando o usuário tocar para restaurar
- [ ] 6.3 Retenção por contagem: fixa e pequena no degrau 1, configurável no degrau 2, com a opção
      de não remover nada
- [ ] 6.4 Rodar a limpeza somente depois de uma captura bem-sucedida
- [ ] 6.5 Testes: limite excedido remove os mais antigos e preserva o recém-capturado; retenção
      desligada não remove nada; captura que falha não remove nada

## 7. A tela

- [ ] 7.1 O interruptor do cofre e os dois gatilhos, sobre os tiles que a tela já usa
- [ ] 7.2 A linha do último backup bem-sucedido, com o destino, e o sinal de atraso quando ela
      envelhece além do intervalo
- [ ] 7.3 O histórico, com data, tamanho e o caminho para restaurar um item
- [ ] 7.4 A declaração do que o destino em vigor **não** cobre, por degrau
- [ ] 7.5 A oferta do cofre junto da confirmação de uma ação destrutiva, que liga os dois gatilhos
- [ ] 7.6 Reescrever as duas frases de `local-backup` que deixaram de ser verdade — que o app não
      guarda cópias, e que restaurar é irreversível
- [ ] 7.7 Chaves novas em `values/strings.xml` e `values-en/strings.xml`, as duas no mesmo commit
- [ ] 7.8 `Modifier.testTag` nos elementos novos, e `Modifier.exposeTestTags()` em qualquer modal
      novo que seja raiz de composição

## 8. Fechamento do degrau 1

- [ ] 8.1 `./gradlew jvmTest` verde
- [ ] 8.2 Verificar em app rodando (não só em teste) que ligar o cofre, capturar, listar, restaurar
      e reter funcionam ponta a ponta em ao menos uma plataforma
- [ ] 8.3 Fluxo Maestro para ligar o cofre e ver o histórico, seguindo `.maestro/README.md` §2

## 9. Degrau 2 — spikes, antes de qualquer implementação

- [ ] 9.1 **Q1, iOS, aparelho real**: escolher uma pasta com `UIDocumentPickerViewController` e
      `UTTypeFolder`, guardar o bookmark, **reiniciar o aparelho**, resolver e escrever. Medir as
      duas variantes de criação do bookmark. Critério: se não sobreviver, o degrau 2 no iOS precisa
      de outro desenho e esta entrega para aqui
- [ ] 9.2 **Q2, Android, aparelho ou emulador**: verificar se uma subpasta de `Download` é
      selecionável em `ACTION_OPEN_DOCUMENT_TREE`; de passagem, confirmar que Drive, OneDrive e
      Dropbox não aparecem no seletor de pasta
- [ ] 9.3 Registrar os resultados em `design.md`, fechando Q1 e Q2

## 10. Degrau 2 — a pasta apontada pelo usuário

- [ ] 10.1 Android: `ActivityResultContracts.OpenDocumentTree` + `takePersistableUriPermission`,
      persistindo **apenas o tree URI**; subpasta própria criada dentro da escolhida
- [ ] 10.2 Android: listar com `DocumentsContract.buildChildDocumentsUriUsingTree` e projeção
      completa, não com `DocumentFile` (1 + 3N consultas)
- [ ] 10.3 Android: sugerir `Documents/` ao abrir o seletor — `Download` é proibido desde o
      Android 11
- [ ] 10.4 iOS: seleção de pasta, bookmark persistido, `start`/`stopAccessingSecurityScopedResource`
      balanceados em `finally`, e `NSFileCoordinator` na escrita e na remoção
- [ ] 10.5 iOS: a URL security-scoped nunca atravessa `String` em nenhum ponto do percurso
- [ ] 10.6 Desktop: pasta escolhida como caminho, sem cerimônia
- [ ] 10.7 Verificar o vínculo na abertura do app, não só ao gravar
- [ ] 10.8 Vínculo caído: avisar, oferecer reapontar ou guardar dentro do app, e continuar
      capturando no degrau 1 enquanto a decisão não vem
- [ ] 10.9 Reapontar a mesma pasta faz o histórico existente aparecer por inteiro
- [ ] 10.10 Trocar de pasta: copiar sem remover a origem, apenas o que a retenção do destino
      comporta, e funcionar com a origem inacessível
- [ ] 10.11 A frase da tela sobre cobertura, diferente por plataforma — no Android o cofre é local

## 11. Fechamento

- [ ] 11.1 `./gradlew jvmTest` verde
- [ ] 11.2 Exercitar o degrau 2 em aparelho nas duas plataformas móveis: escolher pasta, capturar,
      reiniciar o app, capturar de novo, listar, reter, restaurar
- [ ] 11.3 Exercitar o reencontro: desinstalar, reinstalar, reapontar a pasta, ver o histórico e
      restaurar
- [ ] 11.4 Revisar `design.md` — fechar Q1 e Q2 com o que foi medido, e deixar Q3 registrada
- [ ] 11.5 Conferir que `PlatformBackupIsOffTest` continua verde e sem alterações
