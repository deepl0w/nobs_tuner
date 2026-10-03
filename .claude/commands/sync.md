---
description: Bring this worktree up to date with main
allowed-tools: Bash(.claude/scripts/fleet.sh:*), Bash(git:*)
---

```bash
.claude/scripts/fleet.sh sync
```

It refuses on a dirty tree — commit first with
`.claude/scripts/fleet.sh save "<message>"`, or revert the changes if they were
scratch.

On a conflict, resolve towards what the code should be rather than towards your
own side, `git add` the files and `git commit --no-edit`. Then run
`./gradlew :app:testDebugUnitTest` — a merge that compiles is not a merge that
works — and report what moved and anything the merge changed about your own work.
