# Artefatos visuais desta change

Duas páginas HTML autocontidas, guardadas aqui porque foram onde as decisões desta proposta
foram discutidas e revisadas. Nenhuma delas é normativa: **`proposal.md`, `design.md`, `specs/` e
`tasks.md` são a fonte da verdade**, e onde uma página divergir deles, quem está errado é a página.

| Arquivo | O que é | Publicado em |
|---|---|---|
| `proposta.html` | "Cofre do Acervo" — a escada de proteção, os três gatilhos, as quinze decisões, riscos e perguntas abertas | https://claude.ai/code/artifact/35e125fa-7033-44cd-9735-f5ecc207d436 |
| `telas.html` | "Telas do Cofre" — mockups da feature nos tokens reais de `core/designsystem` | https://claude.ai/code/artifact/b82a643f-a9ee-4d73-aa3a-70f43ca45c97 |

## Como abrir

Abra o arquivo direto no navegador. As duas páginas são um arquivo só, sem build e sem
dependência local; carregam apenas fontes do Google Fonts, e degradam para as fontes do sistema
sem elas. As duas seguem o tema claro/escuro do sistema de quem abre.

`telas.html` replica `core/designsystem/.../theme/Color.kt` e `Theme.kt` nos dois esquemas —
`background`, `surface`, `onSurfaceVariant`, `outline`, `primary` e os status. Se a paleta do app
mudar, os mockups passam a mentir: é a única parte deles que precisa acompanhar o código.

## Como atualizar

Editar o arquivo aqui e republicar **passando a URL da tabela acima**. Publicar sem a URL, ou a
partir de outro caminho, cria um artefato novo em vez de atualizar o existente — e o link que
circula deixa de apontar para a versão viva.
