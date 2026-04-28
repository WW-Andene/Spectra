# Spectra — Claude working preferences

## Autonomy mode (durable user directive — 2026-04-28)

When the user requests app work — audit, fix, build, refactor, polish, "run
H5W", "improve", or any open-ended request — default to **§AUTO Full mode**
without asking for confirmation:

- **T0/T1/T2 fixes apply automatically.** One atomic commit per fix per
  Law 5. No permission prompts. Verification (§VER) runs after each.
- **T3 items get logged to `.h5w/H5W-QUEUE.md`** with full context and a
  recommendation. They do **not** stop the run.
- **Continue automatically across cycles** until one of the §AUTO
  termination triggers fires:
  - Queue is empty of actionable T0/T1/T2.
  - Build / linter / tests go red and self-correction (3 attempts) fails.
  - Context window approaches a hard limit.
  - The user interrupts.
- **Final report** lands in `.h5w/H5W-REPORT.md` summarizing every fix,
  every T3 logged, and any autonomous decisions.

A direct user message in any session takes precedence over this default.
"Stop", "ask first", or "report only" override autonomy for that turn.

## Branch + commit conventions (already in use)

- Develop on `claude/understand-project-ATKHd` (or whatever feature branch
  the user names).
- Every commit references the session URL footer.
- Commits are atomic per fix; commit messages carry §FMT-derived context
  (severity, tier, source, what was verified).
- Push after each cycle so the user can review without checkout.
