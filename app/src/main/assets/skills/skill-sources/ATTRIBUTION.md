# Skill sources and licenses

These skills are third-party open-source works, vendored locally without
modification. Local additions are marked below.

| Pack | Upstream | Commit | License |
|---|---|---|---|
| Anthropic skills (14) | https://github.com/anthropics/skills | `dbd4588` | Apache-2.0 (per-skill `LICENSE.txt`) |
| Superpowers (15) | https://github.com/obra/superpowers | `2ce8aa1` | MIT |
| Vercel agent-skills (9) | https://github.com/vercel-labs/agent-skills | `063bee9` | MIT (stated in README) |
| Local skills (2) | this device, `~/tmp/opencode-skills/stage` | — | same as this setup |

Anthropic proprietary skills (`docx`, `pdf`, `pptx`, `xlsx`) and the
unlicensed `doc-coauthoring` were **not** installed. Their functionality is
covered by the local `office-files` skill and open tools.

## Installed ids

- **Anthropic:** academy-guide, algorithmic-art, brand-guidelines,
  canvas-design, claude-api, discernment-nudge, frontend-design,
  internal-comms, mcp-builder, skill-creator, slack-gif-creator,
  theme-factory, web-artifacts-builder, webapp-testing
- **Superpowers:** brainstorming, diagnosing-superpowers,
  dispatching-parallel-agents, executing-plans,
  finishing-a-development-branch, receiving-code-review,
  requesting-code-review, subagent-driven-development,
  systematic-debugging, test-driven-development, using-git-worktrees,
  using-superpowers, verification-before-completion, writing-plans,
  writing-skills
- **Vercel:** composition-patterns, deploy-to-vercel, react-best-practices,
  react-native-skills, react-view-transitions, vercel-cli-with-tokens,
  vercel-optimize, web-design-guidelines, writing-guidelines
- **Local:** phone-debian, office-files

## Licenses

Full texts: `licenses/Apache-2.0.txt`, `licenses/Superpowers-MIT.txt`.
Vercel agent-skills declares MIT in its README; no separate LICENSE file is
included upstream at the pinned commit.

This directory has no `SKILL.md`, so OpenCode does not advertise it as a skill.
