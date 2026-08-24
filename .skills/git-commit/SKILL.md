---
name: "git-commit"
description: "Generates standardized Git commit messages following conventional commit rules. Invoke when user asks to write a commit message, create a commit, or needs help formatting a git commit."
---

# Git Commit

This skill generates clear, standardized Git commit messages that prioritize **human readability** and **debugging efficiency**.

**Core Principle:** The subject line answers *"What does the user get?"* and is written from the **end user's** perspective. The body explains *"How did we implement it?"* (Solution X) for developers.

> **Why the subject must be user-facing:** `script/release.sh` builds `CHANGELOG.md` from commit **subjects only** (`git log --pretty=format:"%s"`); the body is never published. Users read those subjects to decide whether to upgrade, so the subject must describe a user-visible change, not internal architecture. See §0 below.

## When to Use

Invoke this skill when:
- User asks to "write a commit message" or "create a commit"
- User wants to commit staged changes and needs a proper message
- User asks for help formatting a git commit
- User mentions "commit", "commit message", or "git commit" in the context of creating one

## 0. Release-Note Contract (Critical)

`script/release.sh` builds `CHANGELOG.md` from commit **subjects only** — it runs `git log --pretty=format:"%s"` since the last `vX.Y.Z` tag and groups results by type. **The body is never shown to users.** This makes the subject the single user-facing artifact of a commit, so it must be written for a plugin user, not for a contributor.

### How subjects become changelog entries

| Commit type | Changelog section | Prefix handling in `release.sh` |
| :--- | :--- | :--- |
| `feat:` / `feat(scope):` | **Added** | `feat:` (no scope) is stripped; `feat(scope):` is kept **verbatim** |
| `fix:` / `fix(scope):` | **Fixed** | same stripping rule |
| `refactor:` / `refactor(scope):` | **Changed** | same stripping rule |
| anything else (`chore`, `docs`, `style`, `enhance`, `perf`, `test`, `build`, `amend`) | **Improved** | kept verbatim |

Because scoped subjects appear with the `type(scope):` prefix intact, `feat(settings): apply toggles live` renders as the bullet `- feat(settings): apply toggles live`. Make sure the whole string reads naturally as a user-facing bullet.

### Subject must be user-facing

Write the subject as **what the user will observe or be able to do** after this change — not the internal mechanism.

| Developer-facing (internal) | User-facing (what the user gets) |
| :--- | :--- |
| `Unify feature lifecycle controls` | `allow turning off most features` |
| `Introduce project feature registry` | `toggle features without restarting the IDE` |
| `Replace startup-only scan controller` | `API scanning responds to settings without restart` |
| `Serialize lifecycle with generation-scoped sessions` | `discard stale API results, keep last good snapshot` |

Reserve internal terms (registry, lifecycle controller, dependency resolution, generation-scoped, extension point, serialized lifecycle) for the **body**, where developers read the "how".

## Workflow

### Step 1: Analyze the Changes

Before writing the commit message, understand what changed:

```bash
# View staged changes
git diff --cached --stat
git diff --cached

# View recent commit history for style reference
git log --oneline -10
```

### Step 2: Determine the Commit Type

Identify the type of change from the mandatory type list (see Section 3).

### Step 3: Draft the Subject Line

Follow the Subject Line Rules (see Section 1).

### Step 4: Draft the Body

Follow the Body Rules (see Section 2). When fixing a bug, the body **MUST** answer the four questions:
1. The Problem
2. The Root Cause
3. The Solution
4. The Impact

### Step 5: Append Ticket Link

If a ticket/issue exists, append `Fixes: #<issue>` or `Refs: #<issue>` at the bottom.

### Step 6: Create the Commit

Use a HEREDOC to preserve formatting:

```bash
git commit -m "$(cat <<'EOF'
<type>(<scope>): <subject>

<body>

Fixes: #<ticket>
EOF
)"
```

---

## 1. Subject Line Rules (The "Headline")

- **Format:** `<type>(<scope>): <subject>` or `<type>!: <subject>` for breaking changes, or `release v?x.y.z` for releases
- **Tense:** Must use **imperative, present tense** (e.g., `Add`, `Fix`, `Update`, `Remove`). **Never** use past tense (e.g., `Added`, `Fixed`, `Updated`).
- **Length:** Strictly **≤ 50 characters**.
- **Capitalization:** Capitalize the first letter of the subject.
- **Punctuation:** **No period** at the end.
- **Content Focus (CRITICAL):**
    - If the commit is a **`fix:`** (bug fix), the subject **MUST** describe the **problem (Y)** (e.g., `Fix login redirect loop on Safari`). **DO NOT** put the implementation (X) in the subject.
    - If the commit is a **`feat:`** (new feature), the subject **MUST** describe the **feature itself (X)** (e.g., `Add dark mode toggle`).
    - **User perspective (mandatory):** The subject is published verbatim into `CHANGELOG.md` by `script/release.sh`, so it must describe what the **plugin user** observes or can now do — never internal architecture (e.g., `allow turning off most features`, not `Unify feature lifecycle controls`).

---

## 2. Body Rules (The "Context")

- **Separation:** Leave exactly **one blank line** between the subject and the body.
- **Line Wrap:** Wrap text at **72 characters**.
- **Content Structure:** When fixing an issue (Y), the body **MUST** explicitly answer these four questions:
    1. **The Problem:** What was broken or lacking?
    2. **The Root Cause:** Why did it happen?
    3. **The Solution (X):** How does this code change fix it?
    4. **The Impact:** Does this affect other systems or APIs?

---

## 3. Conventional Commit Types (Mandatory)

You **MUST** prefix the subject with one of these exact types:

| Type | When to use | Auto-label |
| :--- | :--- | :--- |
| **feat** | A new feature for the user (X) | `type: new feature` |
| **fix** | A bug fix for the user (Y) | `type: bug` |
| **enhance** | Enhancement to existing features | `type: enhancement` |
| **refactor** | Code restructuring - no feature change and no bug fix | `type: enhancement` |
| **perf** | Performance improvement | `type: enhancement` |
| **docs** | Documentation changes only | `type: doc` |
| **test** | Adding or fixing tests | `type: test` |
| **style** | Code style (formatting, semicolons, etc.) - no logic change | `type: chore` |
| **build** | Build system changes (dependencies, build config) | `type: chore` |
| **chore** | Maintenance tasks (configs, tooling) | `type: chore` |
| **amend** | Small amendments or corrections | `type: amend` |
| **release** | Release version (format: `release x.y.z`) | `release` |

---

## 4. Ticket Linking

If a ticket/issue exists, **MUST** append one of these to the very bottom of the body:
- `Fixes: #<issue-number>` (if it completely closes the issue)
- `Refs: #<issue-number>` (if it partially addresses it)

---

## 5. Strict Forbidden Patterns (Never Do These)

- ❌ **No emojis** (e.g., 🐛, ✨) in the subject line.
- ❌ **No vague subjects** like `Fix stuff`, `Update code`, or `WIP`.
- ❌ **No passive voice** (e.g., `Error was fixed` → use `Fix error`).
- ❌ **No past tense** (e.g., `Updated` → use `Update`).
- ❌ **Do not put the implementation details (X) in the subject line** when fixing a bug—reserve X for the body.
- ❌ **No internal architecture jargon in the subject** (e.g., `registry`, `lifecycle controller`, `generation-scoped`, `dependency resolution`, `extension point`). The subject is published to user-facing release notes; describe user-visible behavior and put internals in the body.

---

## 6. Correct Example

```text
fix(payment): resolve checkout timeout during 3D Secure redirects

The checkout process was failing for users with banks requiring
3D Secure authentication due to a strict 5-second timeout.

Root cause: The API client did not account for external redirects,
which take 7-10 seconds to return to our callback URL.

Solution: Increased the global timeout to 30 seconds and added
exponential backoff retry logic specifically for the payment endpoint.
We also added a loading state to prevent duplicate submissions.

This change does not affect the regular non-3DS flow.

Fixes: #4421
```

---

### Feat example (user-facing subject)

```text
feat(settings): allow turning off most features

Most plugin features can now be turned off from the Features tab. API
scanning and editor integration are now toggleable alongside the export
channels, field formats, and frameworks, all in one place.

Turning a feature off takes effect immediately without restarting the
IDE, and a manual rescan stays available even when continuous scanning
is turned off.
```

The subject above is what `script/release.sh` publishes verbatim into
`CHANGELOG.md` under **Added**, so it is phrased as a user-visible
benefit. The body (developer detail) is not published.

---

## 7. Copy-Paste Template

When generating the commit, strictly follow this structural template:

```
<type>(<scope>): <Imperative summary of Y if fix, or X if feat>

<Problem summary>
Root cause: <Explanation>
Solution: <Implementation details X>
Impact: <Side effects>

Fixes: #<ticket>
```

---

## 8. Enforcement (Optional for CI/CD)

If you want to enforce this automatically, install `commitlint` and use this `commitlint.config.js`:

```javascript
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'subject-case': [2, 'always', 'sentence-case'],
    'subject-max-length': [2, 'always', 50],
    'body-max-line-length': [2, 'always', 72],
    'type-enum': [2, 'always', [
      'feat', 'fix', 'enhance', 'refactor', 'perf', 'docs', 'test',
      'style', 'build', 'chore', 'amend', 'release'
    ]]
  }
};
```
