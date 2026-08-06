# Testes E2E (Maestro)

```bash
scripts/e2e.sh                                   # compila, instala e roda tudo (~16 min)
scripts/e2e.sh --skip-build                      # reaproveita o APK instalado — o ciclo rápido
scripts/e2e.sh .maestro/flows/budgets            # uma pasta, ou um único .yaml
```

Precisa de um emulador **API 36, perfil `pixel_6`, em inglês, com teclado de tela**. O
`scripts/e2e.sh` sobe o `finsight_e2e` quando não há nada conectado e recusa qualquer outro perfil.
Artefatos de falha (log, captura, hierarquia) vão para `.maestro/report/` e `~/.maestro/tests/`.

---

## 1. O que esta suíte é dona (e o que não é)

O E2E dirige o app de verdade num aparelho de verdade. Ele é dono **da travessia** — de que telas,
navegação, modais e persistência se sustentam quando uma pessoa realmente toca por eles. Não é dono
das regras.

**É dono de:** jornadas de negócio de ponta a ponta; contratos entre telas; persistência real
observada pela UI; o que o app **oferece** em cada estado (botão habilitado, comando que aparece e
some); e o que só existe no aparelho — relógio do sistema, teclado, gesto de voltar.

**Não é dono de:** correção de regra de negócio; formatação; validação de campo; combinatória de
estados; layout e pixels; performance.

Na pirâmide, é o anel mais externo: a suíte unitária (`./gradlew allTests`) é dona do comportamento,
esta é dona da jornada. São os únicos testes que rodam o sistema montado — logo, os únicos que podem
falhar por integração, e é só por isso que valem o que custam.

**O custo, com números.** A suíte inteira leva **~16 minutos** para 9 fluxos. Os três de fumaça
somam 20 segundos; os seis de jornada custam de 1m40 a 3m51 **cada um**. Isso é o orçamento (§6), e é
o que torna "adicionar um fluxo" uma decisão, não uma adição livre. Um fluxo que duplica o que um
teste de ViewModel já prova custa dois minutos de emulador para não contar nada de novo.

## 2. Rodar a suíte

```bash
scripts/e2e.sh                        # compila, instala, roda tudo
scripts/e2e.sh --skip-build           # reaproveita o APK instalado
scripts/e2e.sh --tags smoke           # só os fluxos com a tag `smoke`
scripts/e2e.sh .maestro/flows/smoke   # uma pasta, ou um único .yaml
```

Precisa da CLI do Maestro (`curl -Ls https://get.maestro.mobile.dev | bash`) e de um emulador ligado
ou aparelho conectado. A suíte instala o APK de debug — o de release não serve, porque o relógio
móvel de que dois fluxos dependem (§5.2, padrão 9) só existe em debug.

No CI, o `.github/workflows/e2e-android.yml` roda **o mesmo comando**, manualmente ou quando um pull
request recebe o label `e2e`.

Duas ferramentas de inspeção: `maestro studio` abre um inspetor sobre o app em execução, e
`maestro hierarchy` despeja a árvore de acessibilidade — o jeito mais rápido de descobrir o que uma
tela de fato expõe.

### 2.1 O dispositivo de referência

A suíte roda num aparelho só, e isso não é frescura: cada linha abaixo é uma **entrada do teste**.
Deixá-la livre é aceitar entrada aleatória.

| Fixado | Valor | Por que é fixo |
|---|---|---|
| API | 36 | Diálogos de permissão, animações e gestos de sistema mudam entre versões |
| Perfil | `pixel_6` (1080x2400, densidade 420) | Os fluxos rolam para alcançar o que está abaixo da dobra; densidade e altura decidem se o `scrollUntilVisible` acha o campo. A folha de adicionar transação põe o botão de enviar abaixo da dobra num perfil e acima em outro |
| Idioma | inglês (`persist.sys.locale`) | As asserções leem figuras e rótulos renderizados; o locale muda separador decimal, ordem de data e as palavras assertadas |
| Teclado | o de tela, sem facilidades de teclado físico | Sem IME, digitar e fechar o teclado divergem do usuário real e o campo pode não receber foco |
| `hw.keyboard.lid` | `no` (lido no boot) | Sem isso o Gboard troca o teclado por uma barra flutuante que se sobrepõe à folha aberta, e o texto digitado por baixo se perde |
| `show_ime_with_hard_keyboard` | `0` | Ligar *parece* pedir um teclado e faz o oposto: é o que convida a barra a aparecer |
| Animações | desligadas (`config.yaml`) | Transição em curso é a causa nº 1 de toque perdido |

**Um perfil divergente não é "meu ambiente", é um resultado inválido** — vermelho ou verde nele não
conta. O `scripts/e2e.sh` verifica a API e o idioma e recusa rodar fora deles.

O idioma é verificado e **nunca ajustado**: a propriedade exige root e reinício do framework, e o
`pm clear` que cada fluxo executa apaga qualquer idioma por app. É pré-condição da suíte, não algo
que um run providencie.

Crie a AVD uma vez — o `scripts/e2e.sh` fecha a tampa do teclado na primeira vez que a inicia:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
    -n finsight_e2e -d pixel_6 -k "system-images;android-36;google_apis_playstore;arm64-v8a"
```

### 2.2 Quando nada roda

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Todo fluxo morre em `UNAVAILABLE: io exception` | Sessão órfã do Maestro Studio ([#3065](https://github.com/mobile-dev-inc/maestro/issues/3065)) | Feche o Studio e `rm ~/.maestro/sessions` |
| O script recusa antes de começar | Aparelho em outra API ou outro idioma | Ajuste o aparelho; a suíte não o ajusta por você (§2.1) |
| Fluxos falham no primeiro toque | Emulador sem `hw.keyboard.lid = no` | Recrie a AVD; a configuração é lida no boot |
| Texto digitado some | Campo recebeu digitação antes do foco | Não é ambiente: é o fluxo. Veja §5.2, padrão 4 |
| Tudo vermelho depois de mexer no app | APK velho instalado | Rode sem `--skip-build` |

## 3. Mapa da suíte

```
.maestro/
├── config.yaml            # o workspace: quais fluxos rodam, onde vai o relatório
├── flows/                 # tudo aqui roda como teste, uma pasta por área
└── subflows/              # peças reutilizáveis; só rodam quando um fluxo as chama
```

`subflows/` fica fora do glob `flows/**` de propósito — é isso que impede um bloco compartilhado de
ser executado como teste próprio. Hoje são sete: `launch_fresh` (estado inicial), `open_section`
(chegar a uma seção pela grade de ações rápidas), `record_transaction`, `record_categorized_expense`
e `record_card_expense` (lançar), `create_account` e `create_credit_card` (abrir uma conta, abrir um
cartão).

Um subflow carrega o **arranjo**, nunca a afirmação: quem chama é que diz o que o estado criado
deve mostrar. É por isso que `create_credit_card` não assere o limite disponível do cartão que
acabou de criar — dois fluxos criam cartões com limites diferentes, e a afirmação é de cada um.

Quase toda área tem um único `lifecycle.yaml`, e isso é deliberado: `launch_fresh` limpa o banco,
então uma história partida em duas gastaria a primeira metade recriando o que a segunda precisa.

| Fluxo | A afirmação pela qual ele existe |
|---|---|
| `smoke/launch` | o app sobe e publica seu chrome |
| `dashboard/initial_state` | o que o dia zero mostra e — tanto quanto — o que ele retém |
| `accounts/default_account` | um app de primeira execução nunca fica sem conta |
| `ledger/fund_spend_correct_delete` | duas escritas de naturezas diferentes, somadas e lidas de volta; e corrigir uma reescreve as duas pernas, não acrescenta uma terceira |
| `report/lifecycle` | o relatório *escopa*: a mesma escrita lida por contas diferentes, e por perspectivas diferentes, diz coisas diferentes |
| `accounts/lifecycle` | uma transferência move dinheiro sem criar nenhum; uma conta zerada pode ser arquivada e reencontrada |
| `creditcards/lifecycle` | um cartão cria dívida, não gasto de caixa, até a fatura ser fechada e paga |
| `installments/lifecycle` | uma parcela devida por fatura, a compra inteira comprometida contra o limite |
| `recurring/lifecycle` | um recorrente não é dinheiro até ser confirmado, e pular liquida um ciclo, não a ordem |
| `budgets/lifecycle` | uma despesa categorizada chega ao orçamento que a vigia, e passado o limite a leitura muda |

**Identificadores.** `snake_case`, descrevendo o elemento e não sua posição: `add_transaction_save`,
`bottom_navigation_bar`. Itens de navegação derivam o seu da rota — `NavDestination.name` transforma
`DashboardRoute` em `dashboard`, dando `nav_item_dashboard` — então a tag não pode se descolar do
destino que nomeia.

Um id é um `Modifier.testTag` do Compose, e ele só chega ao Maestro porque a raiz de composição
publica as tags na árvore de acessibilidade, via `Modifier.exposeTestTags()` (`core/designsystem` —
`ui/util/ExposeTestTags`). **Uma raiz precisa aderir explicitamente, e uma folha modal, um diálogo ou
um popup são raízes próprias.** Hoje aderem: o `Surface` do `App`, o `ModalBottomSheet`, o painel de
detalhe e dois `DropdownMenu`. Uma janela nova precisa da sua própria chamada, ou suas tags serão
invisíveis sem nenhum erro que explique o porquê.

## 4. Quando um teste fica vermelho

Perguntas em ordem fixa, da mais barata para a mais cara:

1. **O aparelho bate com a §2.1?** Perfil divergente é resultado inválido, não pista.
2. **O fluxo falha sozinho?** `scripts/e2e.sh --skip-build .maestro/flows/<área>` responde em dois
   minutos. Falha sozinho e passa na suíte (ou o contrário) é dependência de ordem — que nesta suíte
   não deveria existir, porque todo fluxo começa de `launch_fresh`.
3. **É determinístico?** Rode duas vezes. Intermitente é quase sempre sincronização (§5.2, padrão 3).
4. **O passo que falhou é o assunto do fluxo ou um passo de preparo?** Falha no preparo raramente é
   bug do app; é a UI que mudou de forma debaixo do fluxo.
5. **Reproduz no CI e localmente?** Se só no CI, volte à pergunta 1.

**"Elemento não encontrado" quase nunca significa que o elemento não existe.** Significa: fora da
viewport; ainda não composto (o dashboard é uma `LazyColumn` — o que não foi rolado **não existe** na
árvore); atrás do teclado; ou numa raiz de composição que não publicou suas tags (§3).

Onde olhar: o log de passos impresso pelo run, e a pasta que ele imprime no fim
(`~/.maestro/tests/<timestamp>/`) com captura e hierarquia no momento da falha.

**Fluxo vermelho não vira comentado nem ignorado.** Ou conserta, ou reverte a mudança que o
quebrou — a suíte só protege enquanto for verde por inteiro.

## 5. Escrever um fluxo novo

Decidir se cabe (§5.1) → escrever seguindo os padrões (§5.2) → revisar contra os antipadrões (§5.3)
e o checklist (§5.4).

Todo fluxo começa de um estado conhecido:

```yaml
appId: com.neoutils.finsight
name: smoke_launch
tags:
  - smoke
---
- runFlow: ../../subflows/launch_fresh.yaml
```

Uma instalação nova **não é um app vazio**: a conta padrão (*Carteira* / *Wallet*) é semeada e o
dashboard já carrega seu layout padrão completo (`GetDashboardPreferencesUseCase`). Também **não é um
app mobiliado**: a conta é semeada, **as categorias não** — o `CreateDefaultCategoriesUseCase` roda a
partir da oferta da tela de categorias, nunca no boot. Um fluxo que precise de categoria cria uma.

Daí em diante todo trecho tem a mesma forma — esperar, agir, asserir a figura no nó que a renderiza:

```yaml
- runFlow:                                  # o meio, reutilizado
    file: ../../subflows/record_transaction.yaml
    env:
      TYPE: income
      TITLE: ${INCOME_TITLE}
      AMOUNT: "50000"                       # dígitos: o campo põe o separador sozinho
- scrollUntilVisible:                       # o que não foi rolado não está composto
    element:
      id: "dashboard_total_balance_amount"
    direction: UP
    timeout: 25000
- assertVisible:
    id: "dashboard_total_balance_amount"    # o nó que renderiza a figura...
    text: "[$]500[.,]00"                    # ...e a figura, tolerante a símbolo e separador
```

**Leia `flows/ledger/fund_spend_report_delete.yaml` antes de escrever o seu.** É o mais curto dos
fluxos de jornada e o que melhor mostra a forma inteira: uma história só, figuras que não
compartilham dígitos, e cada asserção comentada com a claim que ela sustenta.

### 5.1 Cabe em E2E?

> **Cabe se, e somente se, a pergunta for "as peças reais, montadas, entregam este resultado?" — e
> não puder ser respondida com o sistema desmontado.**

Três perguntas resolvem qualquer caso fora da tabela:

1. **Desmonta?** Se uma camada só responde, o teste é dessa camada.
2. **É combinatória?** De N variações da mesma regra, no máximo **uma** é E2E.
3. **Depende do aparelho real?** Relógio, teclado, gesto de voltar, ciclo de vida do processo — aí é
   E2E mesmo que pareça pequeno, porque nenhuma outra camada alcança.

**Na dúvida, desça uma camada.** Um teste bom na camada de baixo custa uma fração e falha com mais
precisão.

| Cabe em E2E | Não cabe — cabe em |
|---|---|
| Lançar receita e despesa e o saldo na tela ser a soma delas | O cálculo do saldo a partir dos lançamentos — **unitário** do repositório de lançamentos |
| Transferir entre contas e o patrimônio total não se mover | A regra de que transferência é neutra — **unitário** do caso de uso |
| Gastar no cartão e o caixa não se mover, mas a fatura sim | Como um lançamento vira lançamentos contábeis — **unitário** do escritor do razão |
| Uma conta zerada poder ser arquivada, e uma com saldo não | A mensagem de erro de cada motivo de recusa — **unitário** do caso de uso |
| O rótulo virar "Excluir" ou "Arquivar" conforme o histórico | A matriz completa de estados que produz cada rótulo — **unitário** do mapeador |
| Passado o limite, o orçamento trocar "Restante" por "Excedido em" | O cálculo do percentual e do restante — **unitário** do progresso de orçamento |
| Confirmar um recorrente depois de virar o mês e a ocorrência cair no mês certo | A idempotência de confirmar duas vezes — **unitário** do caso de uso |
| Uma compra em 3x aparecer em três faturas | A divisão do valor e o arredondamento — **unitário** |
| O dashboard esconder a linha de contas enquanto só há uma | Quais componentes o layout padrão declara — **unitário** das preferências |
| Fechar uma fatura e a seguinte abrir com o limite de volta | O cálculo do limite disponível — **unitário** |
| Um valor gravado continuar lá depois de reiniciar o app | Uma consulta devolver as linhas certas — **integração** do DAO |
| Migração de esquema não quebrar as telas | A migração em si, versão a versão — **integração** do banco |
| Um formulário válido concluir a jornada | Cada mensagem de campo inválido — **unitário** da validação |
| A aparência de um estado vazio | — **screenshot test**, não esta suíte |

### 5.2 Padrões

1. **Alcance elementos por `id`, nunca pelo texto da interface.** Rótulo é copy: muda numa revisão
   de UX e quebra um teste que não tinha nada com o assunto.
2. **Asserte a figura renderizada, não a existência do elemento.** `assertVisible: id=balance` passa
   com o saldo errado. `457.10` no nó que o renderiza prova que duas escritas foram persistidas,
   somadas e lidas de volta — a única coisa que o E2E prova melhor que qualquer camada. Prefira o
   número sem o símbolo (`457.10`), para sobreviver a uma troca de símbolo mas não de valor.
3. **Sincronize por condição, nunca por tempo.** `extendedWaitUntil` depois de qualquer ação que
   anime ou carregue; nunca uma espera fixa.
4. **Releia cada campo depois de digitar.** Uma tecla perdida falha ali, e não três telas adiante,
   como um botão que não envia. E `hideKeyboard` entre campos: o que o aparelho põe na tela se
   sobrepõe à folha, e o próximo campo pode ficar por baixo.
5. **Escreva o valor esperado literal; nunca o calcule no fluxo.** Um valor calculado reimplementa a
   regra do app, e quando os dois erram juntos o teste fica verde para sempre.
6. **Escolha figuras que não compartilhem dígitos.** `500.00` e `42.90` deixando `457.10`: nenhuma
   asserção pode passar lendo a figura do vizinho.
7. **Asserte o que o app oferece, nos dois estados.** O que a UI *oferece* é tanto uma regra quanto o
   que ela calcula, e um fluxo que já passa pelos dois lados ganha a asserção quase de graça.
8. **Uma jornada, um assunto.** Se o nome precisa de dois "e", são dois fluxos — dentro do limite do
   que `launch_fresh` permite (§3).
9. **Use o relógio móvel para alcançar o que a data esconde.** `clockOffsetDays` (só em debug) é o
   que permite fechar uma fatura ou virar o mês. Toda tela lê o `Clock` injetado; qualquer código que
   leia `Clock.System` direto discorda do resto do app no instante em que esse argumento é passado —
   isso é bug, e se conserta, não se contorna.
10. **Reuse o meio, mantenha inline o assunto.** Subflow para preparo; asserção compartilhada
    esconde o que o fluxo afirma e torna a falha ilegível.
11. **Marque só o que um fluxo precisa tocar.** Uma tag sem fluxo por trás é peso morto que ainda
    assim tem de ser mantido correto.
12. **Comente a claim, não o comando.** `tapOn: save` não precisa de comentário; *por que aquele
    número prova alguma coisa* precisa.

### 5.3 Antipadrões

| Antipadrão | Como você percebe que caiu nele | Em vez disso |
|---|---|---|
| **Espera mágica** | O fluxo passa localmente e falha no CI; alguém aumenta o sleep; a suíte engorda e segue instável | `extendedWaitUntil` na condição |
| **Seletor por copy** | Um PR que só reescreve textos deixa dez fluxos vermelhos | `id` para alcançar; texto só para asserir |
| **Asserção tautológica** | O fluxo nunca falhou na vida — e afirma "a tela existe" | Quebre a funcionalidade de propósito uma vez; se não fica vermelho, a asserção é enfeite |
| **Ausência sem premissa** | `assertNotVisible` passa porque o componente está fora da viewport, não porque sumiu | Assere a ausência com um componente *posterior* visível, provando que a região foi composta |
| **Ausência por texto solto** | `assertNotVisible: "E2E Salary"` passa (ou falha) por causa de outro lugar da tela que renderiza o mesmo nome | Ausência por `id` — e com a figura, quando vários nós compartilham o id |
| **Oráculo espelhado** | A regra muda, a implementação erra, o fluxo continua verde porque errou igual | Valor literal, conferido por gente |
| **A grande turnê** | Um fluxo de 120 passos; quando falha no 90, ninguém sabe se o app quebrou ou se a navegação mudou | Uma jornada por fluxo |
| **Retry analgésico** | A suíte "passa" em três vezes o tempo, e ninguém abre as tentativas descartadas | Instabilidade é bug — do app ou do fluxo |
| **Cemitério de ignorados** | N fluxos comentados com "flaky, ver depois"; a suíte é verde e não protege nada | Consertar ou apagar |
| **Espelhar a pirâmide** | Um fluxo para cada regra de validação; a suíte passa de meia hora e ninguém lê o relatório | Combinatória desce de camada (§5.1) |
| **Ramificação defensiva** | `if` no fluxo ("se aparecer o modal, feche"); ele passa em cenários que ninguém projetou | Estado inicial determinístico |
| **Screenshot como asserção** | Toda troca de tema gera dezenas de diffs e o time aprova baselines em massa | Captura é artefato de diagnóstico; comparação visual é outra suíte |

### 5.4 Antes de abrir o PR

- [ ] O fluxo passa sozinho **e** dentro da suíte inteira.
- [ ] Passou duas vezes seguidas.
- [ ] Toda figura assertada está no nó que a renderiza (`id` + `text` no mesmo seletor).
- [ ] Nenhuma figura do fluxo compartilha dígitos com outra.
- [ ] Toda `assertNotVisible` tem premissa de que a região foi composta.
- [ ] Nenhuma tag nova ficou sem fluxo por trás.
- [ ] O cabeçalho do arquivo diz, em duas linhas, qual é a claim do fluxo.

## 6. Saúde da suíte

**Orçamento: ~19 minutos e 10 fluxos.** Ao estourar, corta-se ou funde-se — o teto não sobe por
reflexo. Cada fluxo novo compete com os existentes pelo tempo de CI; ao propor um, diga qual sai ou
por que o teto muda.

O teto subiu uma vez, de ~16 para ~19, e a justificativa fica registrada porque é ela que autoriza
a próxima recusa. Duas coisas entraram: a **edição de transação**, que era o único comando
corretivo do app sem nenhuma travessia — o botão sequer tinha `testTag`, então nenhum fluxo
conseguia alcançá-lo; e **`report/lifecycle`**, que recolheu as pernas de relatório espalhadas por
três outras histórias. Essa segunda quase se paga: cinco gerações de relatório viraram três, e o
que sobrou em `creditcards/lifecycle` é a única que aquela história ganha e nenhuma outra
consegue — a que soma uma fatura liquidada e uma aberta.

**Instabilidade é bug.** Um fluxo que fica vermelho sem mudança de código entra em investigação no
mesmo dia. Não existe fluxo em quarentena permanente nesta pasta, e a ausência disso é o que a
mantém confiável.

**A suíte é opt-in no CI hoje** — roda por label `e2e` ou manualmente. Os três fluxos `smoke` somam
20 segundos, o que torna barata a opção de rodá-los em todo PR; a decisão está em aberto.

---

**Referência da ferramenta:** [docs.maestro.dev](https://docs.maestro.dev). Este documento não a
repete — descreve o que é nosso.
