# Spec: Crashlytics

> A spec descreve *o que* o sistema deve fazer. Não inclui como implementar.
> Código só é válido aqui para padrões específicos desta feature.

---

## Contexto

O app não possui nenhuma forma de rastreamento de erros ou crashes hoje. Quando uma exceção não tratada derruba o app, ou quando uma operação lança uma exceção tratada pelo código, nenhuma informação chega ao desenvolvedor.

Firebase já está integrado via `dev.gitlive` v2.1.0 (Firestore, Auth, Analytics). Os arquivos `google-services.json` (Android) e `GoogleService-Info.plist` (iOS) já estão configurados. Desktop/JVM não tem suporte Firebase.

A feature de Analytics estabeleceu o padrão arquitetural que Crashlytics deve seguir: interface no domínio, implementações por plataforma (`androidMain`, `iosMain`), no-op em `jvmMain`, injeção via Koin com módulo por plataforma.

Exceções de domínio já existem no projeto como wrappers de erros operacionais (ex: `XxxException(val error: XxxError)`). Erros de validação retornam via `Either` e não lançam exceções.

---

## Objetivo

Capturar e reportar automaticamente crashes e exceções ao Firebase Crashlytics, para que falhas em produção sejam visíveis e investigáveis.

---

## Comportamentos

### Caminho principal

**Crash fatal capturado automaticamente**
```
Dado que o app está rodando em Android ou iOS
Quando uma exceção não tratada derruba o app
Então o Crashlytics captura o crash automaticamente
     e o relatório é enviado na próxima inicialização do app
     e o relatório inclui o stack trace completo
     e o relatório inclui o user ID do Firebase Auth
```

**Exceção tratada reportada manualmente**
```
Dado que o app está rodando em Android ou iOS
Quando o código captura uma exceção e chama recordException
Então o Crashlytics registra o evento como erro não-fatal
     e o relatório inclui o stack trace da exceção
     e o relatório inclui o user ID do Firebase Auth
     e o app continua funcionando normalmente
```

**User ID associado aos relatórios**
```
Dado que o app é iniciado
Quando o Crashlytics é inicializado
Então o user ID do Firebase Auth é associado à sessão
     para que relatórios de crash sejam vinculados ao mesmo usuário rastreado pelo Analytics
```

### Casos de borda

**Desktop sem Crashlytics**
```
Dado que o app está rodando em Desktop/JVM
Quando qualquer chamada de Crashlytics é feita
Então a chamada é ignorada silenciosamente (no-op)
     e nenhum erro é lançado
     e o app funciona normalmente
```

**App iniciado antes de autenticação**
```
Dado que o Crashlytics é inicializado antes do usuário estar autenticado
Quando um crash ocorre antes do user ID ser definido
Então o crash é capturado sem user ID associado
```

---

## Regras de negócio

- Crashes fatais são capturados automaticamente — não requerem nenhuma chamada explícita no código do app.
- Exceções tratadas devem ser reportadas explicitamente via `recordException` — apenas exceções, não erros de validação (`Either.Left`).
- O user ID associado aos relatórios é o mesmo do Firebase Auth (`currentUser?.uid`) — o mesmo usado pelo Analytics.
- A persistência e recuperação do user ID é responsabilidade do Firebase Auth, não do app.
- Desktop/JVM: todas as chamadas são no-op — sem exceções, sem logs.
- O crash reporting é sempre ativo — não há opt-out.
- Nenhum dado pessoal ou financeiro é incluído manualmente nos relatórios.

---

## Padrões

- **Abstração via interface no domínio:** o domínio define uma interface `Crashlytics` (ou equivalente). Implementações concretas ficam em `androidMain` e `iosMain`. No-op em `jvmMain`. Segue o mesmo padrão da interface `Analytics`.

- **Injeção via Koin:** `Crashlytics` registrado como `single {}` com implementação por plataforma, seguindo o padrão do `analyticsModule`.

---

## Fora do escopo

- Monitoramento de performance (Firebase Performance)
- Custom keys ou logs customizados nos relatórios de crash
- Breadcrumbs manuais (registro de ações antes do crash)
- Relatórios de erros de validação (`Either.Left`)
- Dashboard in-app de crashes
- Opt-out de crash reporting
- Crashlytics em Desktop (no-op — sem crash reporting real)

---

## Critério de aceite

### Validação manual

Usando o Firebase Console (Crashlytics dashboard) com o app em modo debug no Android:

1. Forçar um crash no app (ex: botão de teste `throw RuntimeException("test crash")`) → confirmar que o crash aparece no painel do Crashlytics após reiniciar o app.
2. Acionar um fluxo que chama `recordException` com uma exceção tratada → confirmar que o evento não-fatal aparece no painel.
3. Abrir o relatório de crash → confirmar que o `user_id` está associado e corresponde ao mesmo ID do Firebase Auth visto no Analytics.
4. Rodar o app no Desktop → confirmar que nenhum erro é lançado e todas as funcionalidades operam normalmente.

### Revisão de código

- [x] `Crashlytics` é uma interface em camada independente de plataforma
- [x] Implementação Firebase está em `androidMain` e `iosMain` (não em `commonMain`)
- [x] Implementação no-op está em `jvmMain`
- [x] `user_id` obtido do Firebase Auth (`currentUser?.uid`) — não gerado manualmente
- [x] `recordException` chamado apenas em blocos que capturam exceções, não em retornos `Either.Left`
- [x] Nenhum dado pessoal ou financeiro incluído manualmente nos relatórios
- [x] `Crashlytics` registrado como `single {}` no Koin em módulo separado por plataforma
