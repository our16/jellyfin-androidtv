---
name: skill-creator
description: Guide for creating standardized OpenCode skills with proper SKILL.md format
license: MIT
compatibility: opencode
metadata:
  audience: developers
  workflow: skill-development
---

## What I do

Help create standardized OpenCode skills following the official SKILL.md format.

## Skill Structure

```
.opencode/skills/<skill-name>/SKILL.md
```

## SKILL.md Template

```markdown
---
name: <skill-name>
description: <1-1024 chars, specific enough for agent to select correctly>
license: MIT
compatibility: opencode
metadata:
  audience: <target users>
  workflow: <workflow type>
---

## What I do

- Bullet points of capabilities

## When to use me

- Trigger conditions

## Workflow

### Step 1: Description
Action details

### Step 2: Next
Next action
```

## Naming Rules

- `name`: 1-64 chars, lowercase letters/numbers, single hyphen separator
- Regex: `^[a-z0-9]+(-[a-z0-9]+)*$`
- Must match directory name
- No leading/trailing hyphens, no double hyphens

## File Locations (Priority)

1. `.opencode/skills/<name>/SKILL.md` (project)
2. `~/.config/opencode/skills/<name>/SKILL.md` (global)
3. `.claude/skills/<name>/SKILL.md` (Claude compat)
4. `~/.claude/skills/<name>/SKILL.md` (global Claude)
5. `.agents/skills/<name>/SKILL.md` (Agents compat)
6. `~/.agents/skills/<name>/SKILL.md` (global Agents)

## Example

See `.opencode/skills/build-package/SKILL.md`
