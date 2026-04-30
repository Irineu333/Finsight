# Maestro E2E Tests

Suíte black-box de testes end-to-end do Finsight em Android e iOS, executada com [Maestro](https://maestro.mobile.dev/).

## Pré-requisitos

- **Maestro CLI**: versão mínima **2.2.0**.
  ```bash
  curl -fsSL "https://get.maestro.mobile.dev" | bash
  maestro --version
  ```
- **Android**: SDK + emulador (ou device físico) com API ≥ `minSdk` do projeto.
- **iOS**: Xcode + Simulador iOS 18.2+ (apenas para execução local; CI cobre só Android no início).

## Build do flavor `e2e`

O flavor `e2e` substitui Auth, Firestore, Crashlytics e Analytics por implementações fake locais. Não faz nenhuma chamada de rede.

### Android

```bash
./gradlew :composeApp:assembleE2eDebug
adb install -r composeApp/build/outputs/apk/e2e/debug/composeApp-e2e-debug.apk
```

App ID: `com.neoutils.finsight.e2e` (suffix `.e2e` permite coexistir com a versão de produção no mesmo device).

### iOS

```bash
cd iosApp
./generate-project.sh
xcodebuild -project iosApp.xcodeproj \
  -scheme Finsight \
  -configuration E2E \
  -destination 'platform=iOS Simulator,name=iPhone 15'
```

> A configuração `E2E` está cadastrada no `iosApp/project.yml`. A integração runtime completa (swap dos bindings Koin para fakes em iOS) será fechada em uma fase posterior.

## Rodando flows

Após instalar o APK no emulador/device:

```bash
# Suíte completa
maestro test .maestro/flows/

# Apenas um diretório (área)
maestro test .maestro/flows/smoke/

# Um único flow
maestro test .maestro/flows/smoke/01-app-launch.yaml
```

Maestro detecta o device alvo automaticamente. Para forçar um device específico, exporte `MAESTRO_DEVICE_ID`.

### Modo desenvolvimento

```bash
maestro studio   # interface gráfica para inspecionar a árvore de UI
maestro hierarchy  # dump da árvore atual no terminal
```

## Estrutura

```
.maestro/
├── config.yaml          # appId default
├── flows/
│   ├── smoke/           # boot, navegação
│   ├── transactions/    # CRUD de transação
│   ├── transfers/       # transferência entre contas, ajuste de saldo
│   ├── invoices/        # cartão de crédito, fatura
│   ├── installments/    # parcelamento
│   └── recurring/       # transações recorrentes
├── helpers/
│   └── reset-app.yaml   # pré-requisito universal de todo flow
└── README.md
```

Cada flow é independente e **deve** começar chamando `helpers/reset-app.yaml` em `onFlowStart`. Pré-condições adicionais (conta, categoria, cartão) são montadas via outros helpers em `helpers/seed-*.yaml`.

## Convenção de testTag

- Formato: `<área>-<elemento>` ou `<área>-<elemento>-<id>` para itens de lista.
  - Exemplo: `transactions-fab`, `account-form-name`, `transactions-item-{transactionId}`.
- Constantes vivem em `object <Área>TestTags` na pasta da feature (ex.: `ui/screen/transactions/TransactionsTestTags.kt`).
- **Nunca** usar string mágica em call site.
- Apenas pontos que algum flow precisa interagir ou assertar recebem testTag — não decorar tudo.

No Android, `Modifier.semantics { testTagsAsResourceId = true }` é aplicado no root composable em `MainActivity`, fazendo testTags virarem `resource-id` consultáveis pelo Maestro via `id:`. No iOS, testTag vira `accessibilityIdentifier` automaticamente.

## Ritual: tela mudou ⇒ revise o testTag e o flow

Sempre que você alterar:

- Estrutura de uma tela ou modal coberto por flow,
- IDs ou nomes de componentes interativos,
- Hierarquia que afete o caminho até um botão tagged,

…faça **três passos** antes de abrir PR:

1. Confira os `*TestTags.kt` da área e atualize-os se algum identificador mudou.
2. Rode os flows da área localmente:
   ```bash
   maestro test .maestro/flows/<área>/
   ```
3. Se algum flow falhou, ajuste antes de commitar — ou abra issue se a quebra for intencional e o flow precisa ser repensado.

Tela nova adicionada a um flow existente? Acrescente o testTag de root e pelo menos os pontos exercitados.

## CI

Workflow GitHub Actions: `.github/workflows/e2e-android.yml`. Rodar via aba **Actions → e2e-android → Run workflow** (`workflow_dispatch`). Ainda **não** é gate de PR — promoção depende de critério: ≥ 2 semanas sem flaky e suíte completa abaixo de 10 min.

## Troubleshooting

- **`Element not found by id`**: rode `maestro hierarchy` e confirme que o testTag aparece como `resource-id`. Se não aparecer no Android, verifique se `testTagsAsResourceId = true` ainda envolve a tela ativa.
- **Flow trava em modal**: confirme que o conteúdo do `ModalBottomSheet` recebeu seus testTags via `Modifier.testTag(...)` aplicado no Composable interno, não no wrapper do sheet.
- **Estado vazado entre flows**: nunca reuse `appId` instalado por outra branch. Reinstale o APK do flavor `e2e` antes de rodar.
- **Timeout em assert**: prefira `extendedWaitUntil` baseado em `assertVisible: id:` em vez de `wait` fixo.

## Lista de flows cobertos

> Atualizada conforme novas fases entram. Fase 2 — Smoke entrega os dois primeiros flows.

| Área         | Flow                              | Status |
|--------------|-----------------------------------|--------|
| smoke        | 01-app-launch.yaml                | Fase 2 |
| smoke        | 02-bottom-nav.yaml                | Fase 2 |
| transactions | 01-create-expense.yaml            | TODO (Fase 3) |
| transactions | 02-create-income.yaml             | TODO (Fase 3) |
| transactions | 03-edit-transaction.yaml          | TODO (Fase 3) |
| transactions | 04-delete-transaction.yaml        | TODO (Fase 3) |
