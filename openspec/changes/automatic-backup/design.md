## Context

O backup local está pronto e testado: `VACUUM INTO` produz um arquivo autossuficiente
(`DatabaseCapture.kt`), o `CandidateVerifier` reprova um arquivo inválido antes de tocar o banco em
uso, e `replaceContentFrom` troca o conteúdo numa transação sem derrubar quem observa. Nada disso
muda aqui. **O que falta é tudo o que está em volta**: um destino que sobreviva à sessão, uma razão
para capturar sem alguém pedir, e uma tela que diga a verdade sobre o que está guardado onde.

Três fatos do código de hoje moldam a entrega:

1. **Não existe destino persistido.** O `BackupFileService` tem quatro operações
   (`BackupFileService.kt:37,43,57,71`) e as duas que alcançam o mundo externo abrem um seletor.
   A interface inteira pressupõe um humano na frente.
2. **Não existe relógio nem ciclo de vida.** Grep por `ProcessLifecycleOwner`,
   `LifecycleEventObserver`, `ON_START`/`ON_STOP` em `app/`, `feature/` e `core/`: zero ocorrências.
   O único precedente de estado de manutenção persistido é `RateSyncStateRepository`, e a
   sincronização que ele acompanha é disparada por botão.
3. **O nome de arquivo colide.** `backupFileName` tem granularidade de dia
   (`BackupFileName.kt:17`). Hoje é inofensivo porque o seletor pergunta ao usuário sobre
   substituir; um cofre não tem a quem perguntar.

E um fato do domínio que reduz o escopo do gatilho preventivo pela metade: **o app já se defende**.
`DeleteAccountUseCaseImpl.kt:24-27` recusa apagar conta com entries — o KDoc é explícito, *"the
guard, not merely a hint to the UI"* — e `ResolveCategoryRetirabilityUseCase.kt:30-33` devolve
`MustArchive` para categoria com lançamento, orçamento, recorrente ou conta rendendo. O mesmo em
cartão e recorrente. Exclusão de fachada só é oferecida quando não destrói nada, e arquivar é um
`UPDATE isArchived` com inverso implementado nos cinco casos.

## Goals / Non-Goals

**Goals**

- O app mantém cópias sem que o usuário lembre, quando o usuário liga isso.
- O usuário aponta onde as cópias ficam, e o app volta lá sem pedir nada de novo.
- Uma ação destrutiva deixa de ser irreversível — porque a cópia é feita **antes** dela.
- Uma atualização que reescreva o banco deixa de ser irreversível, para quem ligou o cofre.
- A tela diz sempre quando foi o último backup que deu certo, e o que o degrau escolhido não cobre.

**Non-Goals**

- Cifrar ou proteger o arquivo por senha. Non-goal do design de `local-backup`, mantido: o degrau 1
  não expõe nada além do que o banco já expõe, e o degrau 2 é uma pasta que o usuário escolheu.
- Nuvem própria. O container iCloud do app foi avaliado e recusado (D3).
- Backup em segundo plano de verdade — `BGTaskScheduler`, `WorkManager` periódico (D5).
- Mesclar acervos, exportar planilha, restaurar seletivamente. Herdados de `local-backup`.
- Religar o backup automático da plataforma (D14, e Q3).

## Decisions

### D1 — Tudo o que o cofre faz obedece ao interruptor, sem exceção

Os três gatilhos são do cofre e valem a partir do mesmo sim. Cofre desligado, o app não escreve
cópia alguma — nem antes de migrar.

A alternativa foi considerada e recusada: uma captura pré-migração **sempre** ligada, apresentada
como rede da operação e não como backup do usuário, com o argumento de que ninguém pede permissão
para poder fazer *rollback* de uma transação. Três coisas a derrubaram.

**A cópia invisível não protege ninguém.** Se ela não aparece no histórico, o usuário nunca a vê e
nunca a restaura — e o único cenário em que ela salva é justamente o que se descobre dias depois.
Torná-la visível a transformaria em backup, e a distinção que a justificava desapareceria.

**O Room reverte a cadeia inteira.** `RoomConnectionManager.configureDatabase` abre uma
`BEGIN EXCLUSIVE TRANSACTION`, chama `onMigrate` para todo o intervalo de versões, escreve o
`user_version` e só então dá `END TRANSACTION`; qualquer falha cai no `ROLLBACK`. Uma migração que
lança não deixa banco pela metade: os dados ficam intactos na versão antiga, e a versão seguinte do
app, com a migração corrigida, migra normalmente. A cópia não é necessária nesse cenário.

**Os invariantes já barram outra fatia.** As migrações encerram verificando o razão — o mesmo
código que `CandidateVerifier` reutiliza —, e uma migração que desequilibre o razão aborta, o que
dispara o `ROLLBACK` acima. Sobra a corrupção que conclui sem erro e não viola invariante — o caso
do `Migration12To13`, que reescreve a moeda de contrapartida de todas as cotações sem condição. É
um buraco real e estreito, e cobri-lo não vale uma exceção na única promessa que o produto faz.

Consequência na fala: `local-backup` deixa de prometer que o app não guarda cópias por conta
própria, e passa a dizer que **não guarda enquanto o cofre está desligado**. Uma frase, sem
ressalva.

### D2 — O destino é um objeto opaco de plataforma, nunca um caminho de texto

Esta é a decisão que mais custa se for tomada errada, e ela é forçada pelo iOS: uma URL
*security-scoped* carrega a permissão **dentro do objeto**, e a Apple documenta que converter para
texto e de volta destrói o escopo. Um destino modelado como `String` não é inconveniente no iOS —
é impossível.

Então `BackupDestination` é uma interface de `commonMain` com três operações — colocar um arquivo
já capturado, listar o que há, remover um — e três `actual` que guardam o que cada plataforma
precisa guardar: um tree `Uri` no Android, um bookmark resolvido em `NSURL` no iOS, um caminho no
desktop. **Nenhuma delas devolve caminho para quem chama.**

O que continua sendo `String` é o arquivo temporário do próprio app, e está certo: `VACUUM INTO` só
sabe escrever num caminho, e o fluxo já é *capturar no temporário → entregar ao destino → apagar o
temporário* (`BackupViewModel.captureInto`). A arquitetura atual não trava nada; só o destino muda
de natureza.

*Alternativa recusada:* um `String` com um esquema (`saf://`, `bookmark://`). Reintroduz o
round-trip exatamente onde ele é proibido.

### D3 — Dois degraus, e o degrau 1 é o destino ao ligar

O degrau 1 é o armazenamento privado do app; o degrau 2 é a pasta que o usuário aponta. O degrau 1
não sobrevive à desinstalação em nenhuma plataforma móvel — `getExternalFilesDir()` é documentado
como removido na desinstalação, e o sandbox do iOS é apagado inteiro, com o keychain como única
exceção. No desktop os dois degraus coincidem: `~/.finance/` (`Database.jvm.kt:12`) sobrevive por
natureza.

Isso é escada de proteção com custo crescente, e a tela nomeia os degraus. O ganho de escopo é o
argumento decisivo: **o produto inteiro — os três gatilhos, a regra da cópia, o histórico, a
retenção e a tela — cabe no degrau 1**, onde o destino é trivial, nada pode ser revogado e tudo é
testável em `jvmTest`. O degrau 2 é uma implementação de `BackupDestination`, não uma reescrita —
desde que D2 valha desde o primeiro dia.

*Alternativas recusadas, com evidência:* `MediaStore.Downloads` — o arquivo sobrevive mas a posse
não, e o app reinstalado precisa de diálogo do sistema por operação para apagar o que ele mesmo
criou; em Android 13+ com `targetSdk 36` não existe permissão que devolva acesso a um `.db`, porque
as granulares cobrem imagem, vídeo e áudio. `MANAGE_EXTERNAL_STORAGE` — a política do Google exige
*core functionality* ("sem a qual o app está quebrado"), que um app de finanças não satisfaz.
Container iCloud do app — exige entitlement e conta paga, depende de o usuário estar logado, e a
Apple não documenta o que acontece com esses dados na desinstalação.

### D4 — Apontar a pasta é uma máquina só, usada em três momentos

Configurar, reconectar depois que o vínculo cair, e reencontrar o histórico após reinstalar são a
mesma tela. Isso não é economia de código: é o reconhecimento de que **os arquivos sobrevivem e o
vínculo não**, nas duas plataformas móveis, por construção — a permissão persistida do Android é
removida com o pacote, e o bookmark do iOS morre com o sandbox.

O terceiro momento é o de maior valor do produto inteiro: alguém acabou de perder tudo, num
aparelho novo. É por isso que "escolher a pasta", que chegou como requisito negociável, não é
negociável — sem essa máquina, os arquivos sobrevivem e ninguém os encontra.

O app cria uma **subpasta própria** dentro da escolhida. A retenção nunca chega perto de um arquivo
do usuário, e a listagem não varre a pasta de documentos inteira.

### D5 — O periódico roda na abertura; nada em segundo plano

A promessa é *"na primeira abertura depois de N dias"*, e não *"a cada N dias"*, porque a segunda é
uma frase que o app não pode cumprir: o `BGTaskScheduler` do iOS declara que a data pedida só
garante que a tarefa **não** começará antes, e a Apple recomenda não pedir mais de uma semana
porque *"podemos escolher não executar sua tarefa"*; no Android, um app hibernado por meses deixa
de executar trabalho de fundo, em silêncio.

Capturar na abertura tem uma propriedade boa: reflete o fim da sessão anterior, que já terminou.

O padrão é **3 dias**. A escolha de frequência alta com histórico curto vem de uma consequência da
spec, não de gosto: restaurar é tudo-ou-nada (`local-backup` proíbe mesclar), então um backup de
três semanas atrás custa três semanas de lançamentos e ninguém o usará. **O valor de um backup
decai rápido**, e isso derruba retenção escalonada e validade em meses.

### D6 — O preventivo é antes, e o gancho fica acima da transação

Um backup posterior à exclusão registra o estado já mutilado. Só o anterior devolve o que foi
apagado.

Onde instalar é decidido por uma restrição do mecanismo: **`VACUUM INTO` recusa rodar dentro de uma
transação** (documentado e classificado em `DatabaseCapture.kt:110-116`), e a remoção acontece
dentro de `immediateTransaction`. O gancho fica nos métodos públicos —
`TransactionRepository.deleteTransactionById` e `deleteTransactionsByIds` — antes do
`useWriterConnection`.

Isso também descarta os dois candidatos que o nome sugeriria. `TransactionRemovalHook` roda
**depois** da remoção e recebe a transação já removida — é hook de correção, não de prevenção. E
`LedgerEntryWriter` não participa de remoção alguma: `removeRow` chama o DAO direto e as entries
somem por cascata.

Não existe ponto único para as demais. São três territórios, e as seis ações que importam (D7) se
distribuem em quatro lugares: os dois métodos do `TransactionRepository` (cobrem exclusão de
transação, de parcelamento e de fatura), a exclusão de moeda, a remoção de cotação, e o
`BackupViewModel` antes de `replaceContentFrom`.

### D7 — A classificação vive no domínio; a tela tem um interruptor

A regra do repositório decide isto. `CLAUDE.md`: *"A consumer decides **whether** it applies a rule
— never **which** rule it is."* Uma configuração por classificação faria a tela escolher **qual**
regra vale; um interruptor faz a tela escolher **se** ela vale.

A evidência confirma que a configuração não teria consequência: as cinco exclusões de fachada são
inofensivas por guarda de domínio, então as duas opções produziriam o mesmo resultado para
praticamente todo mundo.

Das treze ações destrutivas inventariadas, o preventivo cobre as seis que destroem trabalho que o
usuário não redigita: **restaurar backup** (o acervo), **excluir parcelamento** (N transações),
**excluir fatura** (N transações reais quando `RETROACTIVE` — ver Riscos), **excluir transação**,
**excluir moeda** (N cotações observadas) e **remover cotação**. Ficam de fora as cinco de fachada
(as guardas bastam), **editar transação** (reescreve as entries anteriores, mas é frequente demais
para justificar captura a cada edição) e **o ajuste que zera** (o valor anterior é derivado).

Uma exclusão nova nasce dentro de uma classe e é protegida sem que ninguém altere a tela.

### D8 — Uma cópia serve enquanto nada foi acrescentado depois dela

A pré-condição é a mesma para os três gatilhos, e não é tempo: **exclusões não criam necessidade de
cópia nova** — a cópia anterior é justamente a mais completa das duas. Só inclusões e edições
criam.

```
apaga · apaga · apaga              → 1 cópia
apaga · lança 3 · apaga            → 2 cópias (a 2ª protege as 3 novas)
abre o app 10 dias sem lançar nada → 0 cópias novas
```

Isso resolve três coisas de uma vez: vinte exclusões seguidas não produzem vinte arquivos; a
exclusão de fatura, que apaga transação a transação em laço, captura uma vez; e quem abre o app
todo dia sem lançar nada não acumula arquivos idênticos. E torna a abrangência do preventivo
barata, o que sustenta D7.

*Plano B, se distinguir inclusão de exclusão sair caro:* a condição conservadora *"houve qualquer
escrita desde a última cópia"* nunca deixa buraco e só custa arquivos a mais no caso de exclusões em
sequência.

### D9 — O histórico é a pasta; o `snapshot_meta` é a verdade, o nome é dica

O histórico **não** é uma tabela. Uma tabela viajaria dentro do arquivo — o backup contém todo o
acervo — e uma restauração a faria voltar no tempo, passando a mentir sobre a própria pasta. Ela
também nunca saberia que o usuário apagou arquivos pelo gerenciador de arquivos, e uma pasta que
sobrevive à desinstalação é, por definição, uma pasta que o usuário enxerga e mexe.

O nome ganha data e hora, para unicidade e ordenação. Mas **o nome não é autoridade**: no Android,
`DocumentsProvider.createDocument` pode alterar o nome pedido para evitar conflito, e o app pode
receber `… (1).db`. A verdade sobre um arquivo está no `snapshot_meta` dentro dele, que é lido no
momento em que o usuário toca um item para restaurar — que é quando a confirmação já precisa
daqueles dados de qualquer forma. A lista mostra o que o sistema de arquivos entrega: nome, data,
tamanho.

A retenção usa o nome como filtro barato e **confirma pelo conteúdo antes de apagar**, com o mesmo
verificador do fluxo de restauração. O app só apaga o que ele mesmo escreveu.

O gatilho de origem não é persistido nesta entrega. O `formatVersion` do `snapshot_meta` existe
justamente para permitir acrescentá-lo depois sem falhar de modo obscuro.

### D10 — Retenção por contagem, e o mais recente nunca vence

Contagem, não idade: o espaço fica previsível, nunca chega a zero por construção, e é o que a
pessoa quer dizer quando pede que backups parem de acumular.

- **Degrau 1: 3, fixo.** É infraestrutura invisível — o usuário não vê os arquivos, não os
  gerencia. Configurar o invisível é configuração sem propósito.
- **Degrau 2: configurável (5 · 10 · 20 · tudo), padrão 10.** É a pasta dele, o espaço é dele, e
  "tudo" transforma a retenção em algo que ele desliga em vez de sofrer.

A limpeza roda **depois de uma captura bem-sucedida**, nunca na abertura por conta própria: assim
ela está sempre ancorada num momento em que provadamente existe uma cópia nova, e o usuário nunca
fica com zero.

A cópia anterior a uma migração **não entra na contagem** e é substituída apenas pela próxima
migração. Se entrasse, três capturas periódicas depois ela sumiria — justamente no cenário em que
ela é a única coisa que salva: a migração concluiu sem erro técnico e escreveu dado errado, e isso
se descobre dias depois.

### D11 — A captura anterior à migração vive em `:core:database`, e recebe o destino de fora

O mecanismo é do banco, no vocabulário do módulo que já não conhece a palavra "backup" (D7 do
design de `local-backup`, que continua valendo).

A ordem importa: a captura precisa acontecer **antes** de o Room abrir o banco e aplicar as
migrações — abrir para descobrir já teria migrado. A versão gravada no arquivo é lida sem Room, com
uma conexão descartável do driver, exatamente como o `CandidateVerifier` já faz com um candidato.
Se a versão do arquivo for menor que a do app, captura; senão, não há o que proteger.

**O destino vem de fora, e é assim que o cofre governa sem ser conhecido.** `:core:database` não
tem API de arquivo e não vai ganhar uma — a decisão D7 de `local-backup` é explícita, inclusive na
parte em que remover o arquivo é de quem escolheu o caminho. Ele recebe, ou não recebe, um caminho
onde escrever antes de migrar: **um caminho, e captura; nenhum, e não captura**. Quem monta o banco
é quem consulta a preferência do cofre e decide o que passar, e o módulo continua sem saber que
existe cofre.

É o que torna D1 implementável sem que `:core:database` leia configuração de produto alguma, e sem
uma segunda condição espalhada.

### D12 — Quando o vínculo cair, avisa, oferece, e não deixa buraco

O vínculo **vai** cair, e isso é comportamento documentado, não defeito: no iOS o bookmark pode
expirar num update maior do sistema (precedente real — a Apple force-expirou todos os bookmarks no
iOS 14 e fechou o relato como "funcionando conforme esperado") e o usuário pode revogar em
*Ajustes › Privacidade › Arquivos e Pastas*; nas duas plataformas, mover ou apagar a pasta invalida
o acesso.

Somado ao fato de que trabalho de fundo pode parar em silêncio, isso reposiciona a tela: **o
elemento mais importante dela não é o interruptor, é a linha que diz quando foi o último backup
bem-sucedido**. É o único mecanismo pelo qual a pessoa descobre que a proteção parou. Um app que mostra
"ativado" enquanto não grava nada há sete meses é pior do que um app sem cofre, porque produz
confiança sem lastro.

O comportamento: o app **verifica o vínculo na abertura**, não só na hora de gravar, para não
descobrir a queda dias depois. Ao cair, ele **avisa e oferece** — *reconectar a pasta* ou *manter
dentro do app* — e, enquanto a decisão não vem, grava no degrau 1 **provisoriamente**, dizendo que
é provisório. A pergunta continua sendo feita; ninguém fica desprotegido esperando ler um aviso.

### D13 — Trocar de pasta copia, não move

Migrar é copiar para o destino novo e **deixar a origem intacta**. Se falhar no meio, os arquivos
estão nos dois lugares: o pior caso é duplicata, nunca perda. Apagar a origem depois de copiar
transformaria qualquer falha num jeito de perder o histórico.

Migra apenas os N mais recentes que a retenção do destino comporta — copiar vinte para apagar dez
em seguida é tráfego jogado fora.

E o fluxo de troca **funciona sem acesso à origem**, porque o caso mais comum a longo prazo é
justamente trocar de pasta *porque* o acesso à anterior caiu (D12).

### D14 — O backup automático da plataforma continua desligado

Auto Backup no Android e backup do iCloud continuam recusados, e `PlatformBackupIsOffTest`
continua valendo como está.

O argumento técnico original **não se aplica** ao arquivo capturado, e isso precisa ficar
registrado: o motivo de excluir o banco é que em WAL ele são três arquivos copiados sem coordenação
transacional, e *"o que volta pode não somar um banco"* (`Database.ios.kt:37-42`). Um arquivo de
`VACUUM INTO` é um arquivo só, consistente e autossuficiente — exatamente o que se pode copiar
assim.

O que sustenta a recusa é outro argumento, e é de postura: com o cofre desligado por padrão e o
degrau 1 no armazenamento privado, o app não espalha dados financeiros sozinho — quem espalha é o
usuário, escolhendo uma pasta externa. Religar o Auto Backup faria todo backup do degrau 1 subir
para a conta Google **por padrão, sem ninguém decidir**, que é precisamente o que este desenho
evita e o que `local-backup` lista como não-objetivo.

O custo da recusa é concreto e mensurável, e está nos Riscos. Se um dia for reaberta, é como
opt-in explícito — nunca como padrão (Q3).

## Risks / Trade-offs

- **No Android, nenhum caminho deste produto cobre perda do aparelho.** Verificado no AOSP: o
  DocumentsUI filtra do seletor de pasta toda raiz que não declare `FLAG_SUPPORTS_IS_CHILD`, e
  Google Drive, OneDrive e Dropbox não a declaram (Proton Drive, MEGA e ownCloud confirmados no
  código-fonte; os três primeiros por evidência convergente). Nextcloud, Seafile e wrappers rclone
  aparecem, mas exigem um app extra fora da Play Store.
  → Sem remédio do lado do app. A tela **diz**: os backups ficam neste aparelho, e cobrir a perda
  dele depende de escolher uma pasta que algum serviço de nuvem sincronize.
- **A cobertura passa a diferir entre plataformas**, o que `local-backup` evitou de propósito
  (D11 daquele design). No iOS o iCloud Drive aparece no seletor de pasta; no Android nada
  equivalente aparece.
  → Trade-off aceito, com a fala da tela diferindo por plataforma em vez de uma frase única e vaga
  o bastante para valer nas duas.
- **`DeleteFutureInvoiceUseCase` apaga transações reais sob um texto que chama a fatura de
  "futura".** Verificado: `Invoice.Status.isDeletable` inclui `RETROACTIVE` (`Invoice.kt:85-87`),
  o use case apaga toda transação da dimensão da fatura (`:28-32`), e a confirmação não diz
  quantas (`strings.xml:448`), ao contrário do modal de parcelamento.
  → É a ação onde o preventivo mais faz falta, e o preventivo a cobre. **A correção do texto é
  outra entrega** — não se conserta uma mentira de UI dentro de uma proposta de backup.
- **Remover cotação não pede confirmação nenhuma** — apaga no toque
  (`ExchangeRateFormModal.kt:348-349`). É a única exclusão de dado do usuário sem confirmação no
  app, e está entre as seis do preventivo.
  → Idem: coberta aqui, corrigida noutra entrega.
- **O bookmark do iOS pode não sobreviver a reboot.** A documentação da Apple se contradiz, e a
  frase sobre escopo implícito *"válido até o reboot, no máximo"* é o maior risco em aberto do
  degrau 2.
  → Q1, spike obrigatório e primeira tarefa do degrau 2. Se não sobreviver, o degrau 2 no iOS não
  existe na forma desenhada.
- **O gatilho preventivo entra no caminho crítico de uma exclusão.** `VACUUM INTO` precisa de
  espaço livre da ordem do tamanho do banco, e a captura acontece antes de o botão responder.
  → D8 reduz a frequência real a quase nada. Uma captura que falha por falta de espaço precisa
  decidir se deixa a exclusão prosseguir — decisão de produto, registrada como requisito na spec.
- **A tela de backup deixa de ser uma tela de configuração** e passa a ser um painel de saúde, com
  estados que não existiam (vínculo caído, provisório, último backup envelhecendo).
  → É o preço de um cofre que pode parar em silêncio, e é o que impede confiança sem lastro.
- **`DocumentFile` custa 1 + 3N queries** por listagem, e o próprio javadoc recomenda
  `DocumentsContract` direto.
  → A listagem usa `buildChildDocumentsUriUsingTree` com projeção completa, uma consulta.
- **Um vazamento de escopo no iOS derruba o sandbox do app até o relaunch**, se um
  `startAccessingSecurityScopedResource` não for balanceado.
  → O padrão já existe no `IosBackupFileService` (`NonCancellable` + `finally`) e é o que o
  destino segue.

## Migration Plan

Não há migração de schema: nenhuma entidade é acrescentada e o histórico não vive no banco (D9).

O que muda para quem já usa o app:

1. Nada, até que o usuário ligue o cofre. Ele nasce desligado, e nenhum dos três gatilhos escreve
   coisa alguma antes disso.
2. Ligado o cofre, a primeira atualização que trouxer migração passa a ser precedida de uma cópia —
   uma só, fora da contagem da retenção, substituída pela migração seguinte.
3. A tela de backup ganha o cofre e a linha do último backup, e reescreve as duas frases que
   deixaram de ser verdade.

**Rollback**: desligar o cofre para de capturar e não apaga nada; os arquivos já escritos continuam
sendo bancos SQLite válidos e restauráveis pelo fluxo manual, que não muda. Reverter a mudança
inteira deixa os arquivos onde estão, restauráveis um a um pelo seletor.

**Ordem de entrega.** O degrau 1 é a primeira entrega inteira — os três gatilhos, a regra da cópia,
o histórico, a retenção e a tela — e não depende de nenhuma das perguntas abertas. O degrau 2 é a
segunda, e começa pelos dois spikes.

## Open Questions

### Q1 — O bookmark de pasta do iOS sobrevive a reboot?

A Apple afirma persistência entre reinícios em documentação de **macOS**; para iOS não há
afirmação equivalente, e a página de `withoutImplicitSecurityScope` diz que o escopo implícito vale
*"until reboot at the latest"*. As duas variantes de criação precisam ser medidas
(`[]`/`.minimalBookmark` e `.withSecurityScope`), porque a própria documentação da Apple se
contradiz sobre qual usar no iOS.

**Critério de aceitação**: escolher uma pasta, reiniciar o aparelho, resolver o bookmark e
escrever. Se falhar, o degrau 2 no iOS precisa de outro desenho — e o degrau 1 segue intacto.

### Q2 — Uma subpasta de `Download` é selecionável no Android 11+?

A documentação proíbe `ACTION_OPEN_DOCUMENT_TREE` sobre *"o diretório `Download`"*, e escreve *"e
todos os subdiretórios"* apenas para `Android/data` e `Android/obb`. A assimetria sugere que
subpastas passam, mas não está afirmado.

Interessa por um motivo lateral: há relato de julho/2026 de que o Android passou a subir documentos
de `Downloads` para uma pasta "Android backups" no Drive. Se as duas coisas se confirmarem, o
Android ganharia cobertura de nuvem sem esforço. **Duas incertezas empilhadas, uma de fonte
jornalística única** — não é plano, é um teste de dois minutos a mais no mesmo spike.

### Q3 — O Auto Backup da plataforma volta um dia, como opt-in?

D14 recusa por postura, não por técnica, e o argumento técnico que fechou a porta originalmente não
cobre o arquivo capturado. Fica registrado para que ninguém redescubra isto do zero: se a falta de
cobertura contra perda do aparelho no Android incomodar, a resposta é `allowBackup` com regras que
incluam **apenas** a pasta de backups do degrau 1 e continuem excluindo o banco vivo, oferecido
como escolha explícita do usuário. Teto de 25 MB por app, só a cópia mais recente, e reescrita de
`data_extraction_rules.xml`, de `PlatformBackupIsOffTest` e do requisito correspondente em
`local-backup`.
