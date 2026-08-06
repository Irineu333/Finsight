# Testes E2E (Maestro)

Fluxos ponta a ponta que dirigem o app de verdade num aparelho de verdade. São o anel mais externo da
pirâmide de testes: a suíte unitária (`./gradlew allTests`) é dona do comportamento, estes são donos
*da jornada* — de que telas, navegação, modais e persistência se sustentam quando uma pessoa
realmente toca por eles.

Mantenha-os poucos e portantes. Um fluxo que duplica o que um teste de ViewModel já prova custa um
minuto de emulador para não lhe contar nada de novo.

## Layout

```
.maestro/
├── config.yaml            # o workspace: quais fluxos rodam, onde vai o relatório
├── flows/                 # tudo aqui roda como teste, uma pasta por área
│   ├── accounts/
│   ├── budgets/
│   ├── creditcards/
│   ├── dashboard/
│   ├── installments/
│   ├── ledger/
│   ├── recurring/
│   └── smoke/
└── subflows/              # peças reutilizáveis; só rodam quando um fluxo as chama
    ├── launch_fresh.yaml
    ├── open_section.yaml
    ├── record_categorized_expense.yaml
    └── record_transaction.yaml
```

`subflows/` fica fora do glob `flows/**` de propósito — é isso que impede um bloco compartilhado de
ser executado como teste próprio.

Quase toda área tem um único `lifecycle.yaml`, e isso é deliberado: `launch_fresh` limpa o banco,
então uma história partida em duas gastaria a primeira metade recriando o que a segunda precisa. Cada
uma leva seu assunto da criação à aposentadoria.

| Fluxo | A afirmação pela qual ele existe |
|---|---|
| `smoke/launch` | o app sobe e publica seu chrome |
| `dashboard/initial_state` | o que o dia zero mostra e — tanto quanto — o que ele retém |
| `accounts/default_account` | um app de primeira execução nunca fica sem conta |
| `ledger/fund_spend_report_delete` | duas escritas de naturezas diferentes, somadas e lidas de volta |
| `accounts/lifecycle` | uma transferência move dinheiro sem criar nenhum; uma conta zerada pode ser arquivada e reencontrada |
| `creditcards/lifecycle` | um cartão cria dívida, não gasto de caixa, até a fatura ser fechada e paga |
| `installments/lifecycle` | uma parcela devida por fatura, a compra inteira comprometida contra o limite |
| `recurring/lifecycle` | um recorrente não é dinheiro até ser confirmado, e pular liquida um ciclo, não a ordem |
| `budgets/lifecycle` | uma despesa categorizada chega ao orçamento que a vigia, e passado o limite a leitura muda |

Três deles movem o relógio (`clockOffsetDays`, só em debug): `creditcards` para alcançar uma fatura
depois da data de fechamento; `installments` e `recurring` para provar que as folhas leem o relógio
que o app recebeu. Qualquer coisa que leia o relógio do sistema passa a discordar do resto do app no
momento em que esse argumento é passado — o que é um bug, e os dois lugares onde ele existia foram
corrigidos, não contornados.

## Rodando

```bash
scripts/e2e.sh                        # compila, instala, roda tudo
scripts/e2e.sh --skip-build           # reaproveita o APK instalado — o ciclo rápido ao escrever fluxos
scripts/e2e.sh --tags smoke           # só os fluxos com a tag `smoke`
scripts/e2e.sh .maestro/flows/smoke   # só uma pasta, ou um único .yaml
```

Precisa da CLI do Maestro (`curl -Ls https://get.maestro.mobile.dev | bash`) e de um emulador ligado
ou aparelho conectado. `maestro studio` abre um inspetor sobre o app em execução, e
`maestro hierarchy` despeja a árvore de acessibilidade — o jeito mais rápido de descobrir o que uma
tela de fato expõe.

No CI a suíte roda por `.github/workflows/e2e-android.yml`: manualmente, ou num pull request no
instante em que ele recebe o label `e2e`.

Se todos os fluxos morrerem instantaneamente com `UNAVAILABLE: io exception`, o app não é o suspeito.
O Maestro registra a sessão ativa do aparelho em `~/.maestro/sessions`, e enquanto houver uma entrada
lá a CLI assume que outra coisa já preparou o driver e pula esse passo — e então falha no primeiro
comando com um erro que não nomeia nada disso
([#3065](https://github.com/mobile-dev-inc/maestro/issues/3065)). O Maestro Studio mantém uma entrada
dessas, e a deixa para trás ao sair. Feche o Studio e rode `rm ~/.maestro/sessions`.

## O aparelho fixado

A suíte roda num aparelho só: **API 36, perfil `pixel_6`** (1080x2400, densidade 420). O
`scripts/e2e.sh` sobe o emulador `finsight_e2e` quando não há nada conectado, e se recusa a rodar
contra um aparelho de outra API. O `.github/workflows/e2e-android.yml` fixa o mesmo par.

Isso não é frescura. Os fluxos rolam a tela para alcançar o que está abaixo da dobra, então densidade
e altura decidem se o `scrollUntilVisible` acha um campo ou se o run fica vermelho — a folha de
adicionar transação colocou o botão de enviar abaixo da dobra num perfil e acima dela em outro. A
tela faz parte do contrato.

É um telefone com teclado de telefone: o comum, de tela, e nenhuma das facilidades de teclado físico.
Deixado por conta própria, o emulador escorrega para o segundo caso — o Gboard troca o teclado por
uma pequena barra flutuante, que se sobrepõe a qualquer folha aberta, e o texto digitado num campo
por baixo dela se perde. Duas coisas o mantêm no lugar, e as duas são necessárias: a AVD não pode
anunciar tampa de teclado (`hw.keyboard.lid = no`, lido no boot, que o `scripts/e2e.sh` ajusta antes
de iniciá-la), e `show_ime_with_hard_keyboard` precisa continuar em `0`. Ligar essa configuração
*parece* pedir um teclado e faz o oposto: é ela que convida a barra a aparecer.

Ainda assim texto se perde quando se digita num campo antes de ele ter o foco, e é por isso que o
`record_transaction` relê cada campo depois de digitá-lo — uma tecla perdida falha ali, e não telas
adiante, na forma de um botão que não envia.

Crie a AVD uma vez:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
    -n finsight_e2e -d pixel_6 -k "system-images;android-36;google_apis_playstore;arm64-v8a"
```

O `scripts/e2e.sh` fecha a tampa do teclado na configuração dela na primeira vez que a inicia.

## O aparelho fala inglês

Todo fluxo roda contra um aparelho configurado em inglês. O `scripts/e2e.sh` verifica
`persist.sys.locale` e se recusa a rodar de outro jeito, porque a alternativa é toda asserção de
texto ficar vermelha por um motivo que nenhuma mensagem de falha nomearia.

Ele é verificado e nunca ajustado: a propriedade exige root e reinício do framework, e o `pm clear` —
que cada fluxo executa ao iniciar — apaga qualquer idioma por app. Então o idioma do aparelho é
pré-condição da suíte, não algo que um run possa providenciar sozinho.

É isso que torna legítimo asserir uma figura renderizada. `$457.10` é uma afirmação real sobre o
razão: prova que duas escritas foram persistidas, somadas e lidas de volta. Prefira asserir o número
sem o símbolo de moeda (`457.10`), para que a verificação sobreviva a uma troca de símbolo, mas não a
uma troca de valor.

## Alcançando elementos: test tags, não rótulos

Fixar o idioma resolve o que um fluxo pode *asserir*. Não torna rótulos bons *seletores* — um rótulo
é texto de interface, é reescrito, e um botão renomeado não deveria quebrar um teste que nunca se
importou com a redação dele. Então os fluxos endereçam elementos por id:

```yaml
- tapOn:
    id: "add_transaction_save"
```

Esse id é um `Modifier.testTag` no Compose. Ele só chega ao Maestro porque a raiz de composição
publica as tags na árvore de acessibilidade, via `Modifier.exposeTestTags()`
(`core/designsystem` — `ui/util/ExposeTestTags`). **Uma raiz precisa aderir explicitamente, e uma
folha modal, um diálogo ou um popup são raízes próprias** — é por isso que o modifier é aplicado duas
vezes: no `Surface` do `App`, para a janela do app, e no `ModalBottomSheet`, para toda folha. Um novo
tipo de janela precisa da sua própria chamada, ou suas tags serão invisíveis sem nenhum erro que
explique o porquê.

Texto é o seletor certo para duas coisas: conteúdo que o próprio fluxo digitou (o título de uma
transação) e um valor sendo verificado (um montante, um saldo). Ambos são o *assunto* da asserção, e
não uma forma incidental de encontrar um widget.

### Nomenclatura

`snake_case`, descrevendo o elemento e não sua posição na tela: `add_transaction_save`,
`bottom_navigation_bar`. Itens de navegação derivam a sua da rota — `NavDestination.name` transforma
`DashboardRoute` em `dashboard`, dando `nav_item_dashboard` — de modo que uma tag nunca pode se
descolar do destino que nomeia.

Marque o que um fluxo precisa tocar, quando precisa. Uma tag sem fluxo por trás é peso morto que
ainda assim tem de ser mantido correto.

Um fluxo que já passa por dois estados deve asserir os dois. O que a UI *oferece* é tanto uma regra
quanto o que ela calcula — uma conta sem histórico oferece Excluir e uma com histórico oferece
Arquivar — e um fluxo que já está dos dois lados dessa mudança ganha a asserção quase de graça.
Procure por essas antes de escrever um segundo fluxo para chegar ao mesmo lugar.

## Escrevendo um fluxo

Comece de um estado conhecido. O `subflows/launch_fresh.yaml` limpa os dados do app e espera o
shell, para que nenhum fluxo herde as sobras de qualquer um que tenha rodado antes:

```yaml
appId: com.neoutils.finsight
name: smoke_launch
tags:
  - smoke
---
- runFlow: ../../subflows/launch_fresh.yaml
```

Uma instalação nova não é um app vazio: a conta padrão (*Carteira* / *Wallet*) é semeada, e o
dashboard já carrega seu layout padrão completo — saldos, contas, cartões, gastos por categoria,
orçamentos, recentes, ações rápidas (`GetDashboardPreferencesUseCase`).

Também não é um app *mobiliado*, e a linha cai num lugar que vale conhecer: a conta é semeada, **as
categorias não**. O `CreateDefaultCategoriesUseCase` roda a partir da oferta da própria tela de
categorias, nunca no boot — então um fluxo que precise de categoria cria uma, e um nome que ele mesmo
digitou é um seletor melhor do que catorze nomeados por recursos.

Boa parte desse layout começa abaixo da dobra. O dashboard é uma `LazyColumn`, então um componente
até o qual não se rolou **não está composto**, e nem o `maestro hierarchy` nem um `assertNotVisible`
conseguem distinguir isso de um componente que não está configurado. Recorra ao `scrollUntilVisible`
antes de concluir que algo está faltando, e consulte o `GetDashboardPreferencesUseCase` antes de
acreditar nisso.

Prefira `extendedWaitUntil` a um `assertVisible` seco logo depois de uma ação que anima ou carrega; e
prefira uma asserção que enuncie a intenção a uma que apenas por acaso seja verdadeira.

Dois gestos a conhecer. A tela de contas é um pager horizontal, e as figuras nela sempre pertencem à
conta em vista — mas **nunca deslize para a DIREITA para voltar de página**: a partir da borda
esquerda isso é o gesto de voltar do sistema, e ele sai da tela em vez de virar a página. Reentre na
seção, que abre na primeira conta. E no dashboard o `back` devolve você onde a última rolagem parou,
então role antes de asserir qualquer coisa perto do topo.

Esse último morde numa forma que vale nomear: o `scrollUntilVisible` só viaja na direção que recebe,
então voltar a um dashboard que ficou rolado *além* do componente desejado e rolar para BAIXO procura
para longe dele para sempre. Suba ao topo primeiro, depois desça.

## Dois jeitos de uma asserção mentir

**Um campo de dinheiro anexa.** O input coloca o separador sozinho reformatando todos os dígitos do
buffer, então digitar num campo que já contém uma figura concatena as duas — `$249.50` digitado por
cima com `0` lê `$2,495.00`. Todo campo que abre preenchido (um ajuste, a confirmação de um
recorrente, uma edição) precisa de `eraseText` antes do `inputText`. Campos que abrem vazios não
precisam, e reler o campo depois de digitar pega os dois erros no próprio campo, em vez de três telas
adiante, na forma de um botão que não envia.

**Uma asserção de texto casa o nó inteiro e vale a tela inteira.** O texto é comparado com a string
completa do nó — `assertVisible: "E2E Sofa"` falha numa linha renderizada como `E2E Sofa • 1/3` e,
pior, `assertNotVisible: "E2E Sofa"` passaria nela pelo mesmo motivo. E não fica restrita ao
componente até o qual você acabou de rolar: depois de confirmar um recorrente, o título dele está no
dashboard duas vezes — uma na linha de pendentes que ele deveria ter deixado, outra em Recentes, onde
está sua nova transação. Assira ausência por `id` (com a figura, quando vários nós compartilham o
mesmo id), nunca por uma string solta que outra coisa na página também possa estar renderizando.
