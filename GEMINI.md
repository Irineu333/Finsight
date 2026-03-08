# Project Agent Instructions

## Contexto do projeto

Antes de qualquer planejamento ou edição de código, leia `./CLAUDE.md` — ele é a
referência principal de arquitetura, convenções e comandos deste projeto.

## Skills locais

Use as skills abaixo diretamente dos caminhos locais:

| Nome | Caminho |
|---|---|
| `commit` | `./.claude/skills/commit/SKILL.md` |
| `bump-version` | `./.claude/skills/bump-version/SKILL.md` |
| `finsight-ux` | `./.claude/skills/finsight-ux/SKILL.md` |
| `jetpack-compose-expert` | `./.claude/skills/jetpack-compose-expert/SKILL.md` |
| `kmp-architecture` | `./.claude/skills/kmp-architecture/SKILL.md` |
| `kmp-unit-testing` | `./.claude/skills/kmp-unit-testing/SKILL.md` |
| `room-database` | `./.claude/skills/room-database/SKILL.md` |

## Regras de acionamento

- Se o usuário citar uma skill pelo nome, carregue o respectivo `SKILL.md` e siga as instruções.
- Se a tarefa corresponder claramente ao domínio de uma skill, acione-a mesmo sem citação explícita.
- Se mais de uma skill se aplicar, use apenas o conjunto mínimo necessário.
