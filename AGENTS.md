# Mihon

## Code

- Android app in Kotlin and Jetpack Compose, split into Gradle modules (`app`, `domain`, `data`,
  `presentation-core`, `source-api`, ...).
- Comment only a non-obvious why: a constraint, a workaround or a decision the code can't show, as short as it can
  be. Short KDoc is fine on any declaration whose purpose its name doesn't make clear. No comments that restate the
  code, no section banners, no TODOs without an issue.
- No speculative abstractions, settings or "just in case" error handling.
- Every file holding substantial code copied from elsewhere names its source, copyright holders and licence in
  its header, wherever the code lands: `// From <repo url>` for a verbatim copy or `// Adapted from <repo url>` for
  code that was modified, split or taken partially, followed by `// Copyright <holders>, <licence>`. A source file
  that already has such a header keeps it. Licence texts reach users through the app's licences screen, not the
  repo.

## Scope

- Stay on the task and match the surrounding code. Report unrelated issues instead of fixing them.
- Don't touch `gradle/` unless asked.

## Verification

Run `./gradlew check` before committing and fix what fails; never skip or disable a check. If you can't run it,
stop and tell the user.

## Commits

- One concern per commit; each compiles on its own.
- Imperative title in the style of the history, e.g. `Restore the window size on launch`. Body only for context
  the title can't carry.

## Pull requests

When asked to open a PR, end its description with a collapsed summary of the prompt: what the user asked for and
the decisions they made along the way.

```markdown
<details>
<summary>Prompt summary</summary>

...
</details>
```

## AI disclosure

Attribute every commit by where its ideas came from, not who typed or reviewed it. A change has four elements:

- **Cause**: what is wrong, or the concrete behavior needed. Restating the request doesn't count.
- **Approach**: how the change solves it.
- **Design**: structure and where code lives.
- **Correctness**: bugs, edge cases or constraints caught before the change was final.

The user supplied an element only if they gave its content or the reason that decides it, e.g. "put it in the
cache, not the provider". Requests, approvals, rejections without a reason, and naming or style nits don't count.

| Tier | When | Author | Trailer |
| --- | --- | --- | --- |
| 1 | User supplied the cause or the approach | User | none |
| 2 | Agent supplied both; user supplied another element | Agent | `Co-authored-by: <user> <email>` |
| 3 | User supplied no element | Agent | none |

- Agent author: `git commit --author="<agent name> <agent noreply email>"`, with your own name and no-reply
  address. Never use the user's identity as author for tiers 2 and 3.
- Decide the tier yourself and apply it; don't announce it or ask the user to confirm it. If unsure, use the lower
  one. Re-check it when amending. Never amend pushed commits.
