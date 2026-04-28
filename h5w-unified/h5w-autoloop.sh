#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# H5W AUTO-LOOP — Autonomous Claude Code session manager
# ═══════════════════════════════════════════════════════════════
#
# Usage:
#   ./h5w-autoloop.sh "Improve my app autonomously"
#   ./h5w-autoloop.sh --resume
#
# Requires: Claude Code CLI (claude) installed and authenticated
# ═══════════════════════════════════════════════════════════════

set -e

# ─── Configuration ────────────────────────────────────────────
MAX_LOOPS=30               # Max auto-continue iterations (FULL/GUIDED)
MAX_LOOPS_UNCHAINED=60     # UNCHAINED mode runs longer
MAX_LOOPS_BRAINSTORM=200   # BRAINSTORM mode runs much longer (closed sandbox use)
MAX_TURNS=15               # Turns per iteration (prevents timeout)
COOLDOWN=3                 # Seconds between iterations
LOG="h5w-autoloop.log"
ACTIVATION_PHRASE="run H5W full autonomous mode"
UNCHAINED_PHRASE="run H5W unchained autonomous mode"
UNCHAINED_CONFIRM="i accept full responsibility"
BRAINSTORM_FLAG=":brainstorm"   # Append to UNCHAINED phrase to enable
BRAINSTORM_CONFIRM="this is my sandbox"

# Default: GUIDED mode (permission prompts active). FULL or UNCHAINED only
# when the user's prompt contains the corresponding literal activation phrase.
PERMISSION_MODE="default"  # overridden below if FULL/UNCHAINED is opted into
MODE="GUIDED"
BRAINSTORM=false           # Set true when UNCHAINED + :brainstorm flag triggers

# UNCHAINED stop patterns are looser — only true terminations stop the loop.
STOP_PATTERNS="^RUNWAY LIMIT|^SESSION END|H5W-REPORT\.md (written|complete)|^STUCK.*cannot proceed"
STOP_PATTERNS_GUARDED="$STOP_PATTERNS|^All remaining.*T3|^## What Needs Your Decision"

# ─── Colors ───────────────────────────────────────────────────
G='\033[0;32m'; Y='\033[1;33m'; R='\033[0;31m'; C='\033[0;36m'; N='\033[0m'

# ─── Prerequisite check ───────────────────────────────────
if ! command -v claude &>/dev/null; then
    echo -e "${R}Error: Claude Code CLI not found. Install: npm install -g @anthropic-ai/claude-code${N}"
    exit 1
fi

# ─── Argument handling ────────────────────────────────────────
[ $# -eq 0 ] && echo -e "${R}Usage:${N} ./h5w-autoloop.sh \"your prompt\"" && exit 1

PROMPT="$1"
CONTINUE_MODE=false

if [ "$PROMPT" = "--resume" ]; then
    CONTINUE_MODE=true
    PROMPT="Resume H5W autonomous session. Read COMPACT-RESUME.md and H5W-QUEUE.md. Continue working per §AUTO. End every response with NEXT: [action]."
fi

# ─── §AUTO mode resolution ────────────────────────────────────
# FULL mode requires the literal activation phrase as a top-level instruction
# OR a --full flag for --resume after a previously-confirmed FULL session.
# The gate (Risk Acknowledgment + typed `proceed`) is enforced HERE, at the
# script level, BEFORE claude is invoked. This prevents the contradiction
# between "NEVER use AskUserQuestion" (in the AUTO_RULES injection) and the
# gate's confirmation requirement.

# Detect the literal phrase as a top-level instruction (start of prompt or
# its own line) — not embedded in metalinguistic mentions like "should I run
# H5W full autonomous mode?". This narrows the trigger surface.
phrase_at_start() {
    local p="$1" needle="$2"
    local pl="${p,,}" nl="${needle,,}"
    [[ "$pl" == "$nl"* ]] && return 0
    [[ "$pl" == *$'\n'"$nl"* ]] && return 0
    return 1
}

# UNCHAINED check FIRST — if both phrases somehow match, UNCHAINED is the
# more deliberate signal. UNCHAINED also requires a second confirmation
# phrase ("i accept full responsibility") that's distinct from FULL's
# "proceed" so muscle memory can't escalate accidentally.
if phrase_at_start "$PROMPT" "$UNCHAINED_PHRASE"; then
    echo -e "${R}═══ §AUTO-UNCHAINED — Activation Gate ═══${N}" >&2
    cat <<'UNCHAINED_ACK' >&2
═════════════════════════════════════════════════════════════════════
              §AUTO-UNCHAINED — UNRESTRICTED MODE
═════════════════════════════════════════════════════════════════════

  ⚠⚠⚠  THIS MODE REMOVES PROTECTIONS THAT MAKE LONG SESSIONS  ⚠⚠⚠
  ⚠⚠⚠  RECOVERABLE. READ THIS BEFORE TYPING THE CONFIRMATION.  ⚠⚠⚠

WHAT UNCHAINED DOES THAT FULL DOES NOT:

  ⚠ T3 actions execute without queueing. This includes:
    - Deleting features, files, branches, user data
    - Schema migrations and breaking API changes
    - Force-push and git history rewrites (Δ from FULL)
    - Modifying auth / payment / PII handling code
    - Publishing to app stores or production hosting if configured
    - Spending money on paid APIs / cloud services
    Some of these cannot be undone by 'git checkout'. Some lose work
    that isn't yet in your reflog. You will not be asked first.

  ⚠ Iron Laws 6, 7, 9 are demoted to advisories.
    - Law 6 (Feature Preservation): no longer blocks. Working features
      can be replaced or removed if §META/§OBSTACLE judges it warranted.
    - Law 7 (Identity Preservation): no longer blocks. Visual identity
      and architectural conventions can be overwritten.
    - Law 9 (Reversibility Before Action): the tier system still
      classifies actions for the log, but T3 no longer gates execution.
    Laws 1-5, 8, 10-12 still apply (specificity, read-before-act,
    source integrity, bugs-before-refactors, minimum footprint,
    expansion boundaries, honesty, verify-or-don't-claim).

  ⚠ §META can write directly to SKILL.md and references/*.md.
    Proposals are still also written to skill-improvements/SF-NNN.md
    for review, but the active rules CAN change mid-session. The rules
    at hour 3 are not necessarily the rules you read at hour 0.

  ⚠ §OBSTACLE permissions extended to T3.
    Tool-building authorization now covers force-push, history
    rewrites, irreversible API calls, and external services that
    aren't free-tier.

  ⚠ Loop runs longer. MAX_LOOPS=60 (vs 30 in FULL). Roughly
    4-6 hours of wall-clock work before the runway report.

WHAT UNCHAINED DOES NOT CHANGE (still bails like FULL on these):

  • [STUCK] from §OBSTACLE's 3-attempt cap still terminates the
    loop. UNCHAINED has slightly more runway than FULL but behaves
    the same on STUCK. To push past the give-up thresholds, see
    §AUTO-UNCHAINED + §BRAINSTORM (separate gate).
  • Self-correction protocol still allows 3 attempts per finding
    before logging [STUCK]. BRAINSTORM raises this to 20.
  • The 5-failures-and-stop session runway limit still applies in
    UNCHAINED. BRAINSTORM removes it.

WHAT UNCHAINED STILL DOES (these are floor, not removable here):

  • Activation gate (this prompt + the two-phrase confirmation)
  • Full COMPACT-RESUME / H5W-LOG / H5W-QUEUE writeup of every action
  • Resolved git policy (.h5w/git-policy) is still respected for
    branch behavior — set it to 'main' if you want UNCHAINED to
    work directly on main without a working branch
  • Iron Laws 1, 2, 3, 4, 5, 8, 10, 11, 12 (the laws about being
    accurate, specific, honest, and not fabricating findings).
    Removing these wouldn't be "more autonomous"; it would just be
    making Claude lie. They stay.
  • H5W-REPORT.md at session end with full audit trail
  • Claude Code session quotas / rate limits (Anthropic-side, not
    overridable from this side regardless)

WHEN TO USE UNCHAINED:

  ✓ Personal / scratch projects you can recreate
  ✓ Repos with full git history pushed to a remote you trust
  ✓ When you've already used FULL and found T3 friction more
    obstructive than protective on this specific project
  ✓ When you accept that "broken" is a possible outcome and you
    have time/energy to fix it

WHEN NEVER TO USE UNCHAINED:

  ✗ Production systems with real users
  ✗ Repos with secrets / credentials in scope
  ✗ Multi-author projects where someone else hasn't consented
  ✗ Anything irreplaceable (writing drafts, photo metadata, configs
    you don't have backups of)
  ✗ When you're tired or rushed — the two-phrase gate exists
    specifically because tired people make bad activation calls

═════════════════════════════════════════════════════════════════════
UNCHAINED_ACK
    echo -en "${R}Type EXACTLY: i accept full responsibility${N}\n${R}(anything else drops to FULL or GUIDED):${N} " >&2
    read -r CONFIRM
    if [ "${CONFIRM,,}" = "$UNCHAINED_CONFIRM" ]; then
        MODE="UNCHAINED"
        PERMISSION_MODE="auto"
        MAX_LOOPS="$MAX_LOOPS_UNCHAINED"
        echo -e "${R}═══ §AUTO-UNCHAINED ACTIVATED — MAX_LOOPS=$MAX_LOOPS ═══${N}" >&2
        echo "[$(date)] §AUTO-UNCHAINED activated. Confirmation phrase received. T3 gate disabled. Iron Laws 6/7/9 demoted. §META can edit skill files." >> "$LOG"

        # Check for :brainstorm flag in original prompt — UNCHAINED + BRAINSTORM
        # is for closed-sandbox deep-work where the goal is "burn turns until
        # you actually solve it" rather than "fail gracefully and stop."
        # SF-018: require whitespace boundary or end-of-string around the
        # flag — substring match would catch :brainstormy or test:brainstormstuff.
        if echo "$PROMPT" | grep -qiE "(^|[[:space:]])${BRAINSTORM_FLAG}([[:space:]]|$)"; then
            echo "" >&2
            cat <<'BRAINSTORM_ACK' >&2
─────────────────────────────────────────────────────────────────────
                §BRAINSTORM — closed-sandbox deep-work modifier
─────────────────────────────────────────────────────────────────────

DETECTED: ':brainstorm' flag in prompt.

§BRAINSTORM is a behavioral modifier on top of UNCHAINED. It changes how
Claude responds to obstacles — from "try 3 times then give up" to "try
many angles, pivot, research, decompose, retry from a different shape."

WHAT IT CHANGES (effort knobs):

  • Self-correction attempts: 3 → 20 per finding
  • §OBSTACLE attempts: 3 → 10 per obstacle, REQUIRED to be from
    different approach classes (regex / parser / AST / LLM / ML /
    manual rule engine — not "tweak the regex 3 ways")
  • Session runway: 5-failures-and-stop limit REMOVED. The only
    runway is context-full or wall-clock cap (MAX_LOOPS=200).
  • [STUCK] is no longer a queue entry — it's a routing signal to
    §BRAINSTORM-PIVOT (research → decompose → reframe → retry).
  • Premature-completion pressure removed. "I think I'm done"
    triggers §SIM.6 (50 Questions) THEN §SIM.7 (research) THEN
    §SIM.8 (BRAINSTORM-PIVOT) before any "done" state is allowed.

WHAT IT DOES NOT CHANGE:

  • The 12 Iron Laws still apply at UNCHAINED's relaxation level.
  • Honesty still applies — Claude reports what it actually tried,
    including dead ends. Logs do not get "tidied" to look productive.
  • Genuine walls (auth, captcha, requires-credit-card) still get
    flagged honestly. §BRAINSTORM doesn't turn impossible into
    possible — it raises the bar for what counts as impossible.
  • Cost-bearing actions (paid APIs) are still gated by .h5w/git-policy
    and the user's environment. §BRAINSTORM doesn't override account
    creation or payment.

WHEN TO USE:

  ✓ Closed local sandbox you control end-to-end
  ✓ Problem you genuinely want Claude to push through, not bail on
  ✓ You have hours of runway and real interest in seeing how far it gets
  ✓ You're comfortable reading 200+ iterations of log to see the path

WHEN NOT TO USE:

  ✗ You need a bounded session (use UNCHAINED without :brainstorm)
  ✗ You're rate-limited and 200 iterations would burn your quota
  ✗ The problem is genuinely impossible and you'd rather know fast

─────────────────────────────────────────────────────────────────────
BRAINSTORM_ACK
            echo -en "${R}Type EXACTLY: this is my sandbox${N}\n${R}(anything else stays in plain UNCHAINED):${N} " >&2
            read -r BS_CONFIRM
            if [ "${BS_CONFIRM,,}" = "$BRAINSTORM_CONFIRM" ]; then
                BRAINSTORM=true
                MAX_LOOPS="$MAX_LOOPS_BRAINSTORM"
                echo -e "${R}═══ §BRAINSTORM ENABLED — MAX_LOOPS=$MAX_LOOPS ═══${N}" >&2
                echo "[$(date)] §BRAINSTORM modifier enabled. Effort caps raised. STUCK becomes routing signal." >> "$LOG"
            else
                echo -e "${Y}═══ §BRAINSTORM not confirmed — staying in plain UNCHAINED ═══${N}" >&2
                echo "[$(date)] :brainstorm flag detected but sandbox confirmation not matched — staying in plain UNCHAINED" >> "$LOG"
            fi
        fi
    else
        echo -e "${C}═══ Dropped to §AUTO-GUIDED (confirmation phrase not matched) ═══${N}" >&2
        echo "[$(date)] UNCHAINED phrase detected but confirmation not matched — running GUIDED" >> "$LOG"
    fi
elif phrase_at_start "$PROMPT" "$ACTIVATION_PHRASE"; then
    echo -e "${Y}═══ §AUTO FULL — Activation Gate ═══${N}" >&2
    cat <<'RISK_ACK' >&2
═════════════════════════════════════════════════════════════════════
                    §AUTO FULL — RISK ACKNOWLEDGMENT
═════════════════════════════════════════════════════════════════════

WHAT YOU LOSE BY ACTIVATING:

  ⚠ Interactive permission prompts. Claude Code is invoked with
    --permission-mode auto. If a tool action would normally prompt
    you, it now executes silently.

  ⚠ "I can't" as a stopping signal. §OBSTACLE forbids surrender.
    Claude will research, build tools, and chain workarounds rather
    than ask for help.

  ⚠ Conservative defaults. Default mode is GUIDED (permission prompts
    active). FULL is opt-in only via this gate.

WHAT CLAUDE WILL DO WITHOUT ASKING:
  • Install npm / pip / gradle / cargo / go packages (T1)
  • Clone open-source repos into the project (T1)
  • Build custom scripts, parsers, fetchers, converters (T1)
  • Modify package.json / build.gradle / tsconfig / webpack config (T2)
  • Create new directories, CI/CD pipelines, Dockerfiles (T2)
  • Write to AndroidManifest, add permissions, modify build types (T2)
  • Make git commits per resolved git policy (.h5w/git-policy)
  • Run §META: propose edits to skill files (proposals only — no merge)

WHAT CLAUDE WILL NOT DO (T3 — queued for your decision):
  • Delete existing features or user data
  • Change fundamental architecture decisions you've made
  • Spend money (paid APIs, cloud services beyond free tier)
  • Publish to app stores or production hosting
  • Modify auth, payment, or PII handling without explicit approval
  • Force-push, rewrite git history, or delete branches
  • Auto-merge §META skill edits to the active SKILL.md

For a higher-autonomy mode that removes these T3 gates, see
§AUTO-UNCHAINED (different activation phrase + two-step confirmation).

═════════════════════════════════════════════════════════════════════
RISK_ACK
    echo -en "${Y}Type 'proceed' to activate §AUTO FULL, or anything else to drop to GUIDED:${N} " >&2
    read -r CONFIRM
    case "${CONFIRM,,}" in
        proceed)
            MODE="FULL"
            PERMISSION_MODE="auto"
            echo -e "${Y}═══ §AUTO FULL ACTIVATED ═══${N}" >&2
            echo "[$(date)] §AUTO FULL activated by user typed 'proceed'" >> "$LOG"
            ;;
        *)
            echo -e "${C}═══ Dropped to §AUTO-GUIDED ═══${N}" >&2
            echo "[$(date)] Activation phrase detected but user did not confirm with 'proceed' — running GUIDED" >> "$LOG"
            ;;
    esac
elif [ "$2" = "--unchained" ] && [ "$3" = "--brainstorm" ] && [ "$CONTINUE_MODE" = true ]; then
    MODE="UNCHAINED"
    PERMISSION_MODE="auto"
    BRAINSTORM=true
    MAX_LOOPS="$MAX_LOOPS_BRAINSTORM"
    echo -e "${R}═══ §AUTO-UNCHAINED + §BRAINSTORM resume (--unchained --brainstorm) ═══${N}" >&2
    echo "[$(date)] §AUTO-UNCHAINED + §BRAINSTORM resumed via flags" >> "$LOG"
elif [ "$2" = "--unchained" ] && [ "$CONTINUE_MODE" = true ]; then
    MODE="UNCHAINED"
    PERMISSION_MODE="auto"
    MAX_LOOPS="$MAX_LOOPS_UNCHAINED"
    echo -e "${R}═══ §AUTO-UNCHAINED resume (--unchained flag) ═══${N}" >&2
    echo "[$(date)] §AUTO-UNCHAINED resumed via --unchained flag" >> "$LOG"
elif [ "$2" = "--full" ] && [ "$CONTINUE_MODE" = true ]; then
    MODE="FULL"
    PERMISSION_MODE="auto"
    echo -e "${Y}═══ §AUTO FULL resume (--full flag) ═══${N}" >&2
    echo "[$(date)] §AUTO FULL resumed via --full flag" >> "$LOG"
else
    echo -e "${C}═══ §AUTO-GUIDED mode (default) ═══${N}" >&2
    echo -e "${C}Permission prompts active.${N}" >&2
    echo -e "${C}To activate FULL:       type '$ACTIVATION_PHRASE' at start of prompt → 'proceed'${N}" >&2
    echo -e "${C}To activate UNCHAINED:  type '$UNCHAINED_PHRASE' at start of prompt → '$UNCHAINED_CONFIRM'${N}" >&2
    echo -e "${C}To add :brainstorm:     append '$BRAINSTORM_FLAG' to UNCHAINED prompt → '$BRAINSTORM_CONFIRM'${N}" >&2
fi

# ─── §AUTO injection (concise pointer; full rules in references/auto-mode.md) ─
if [ "$MODE" = "UNCHAINED" ]; then
AUTO_RULES="

=== §AUTO-UNCHAINED — runtime context ===
You are in §AUTO-UNCHAINED mode. The user typed the activation phrase AND
the explicit confirmation phrase 'i accept full responsibility' at the
script-level gate. They have read and accepted the unchained risk
acknowledgment.

Binding rules — RELAXATIONS from §AUTO FULL:
1. T3 actions execute without queueing. Force-push, history rewrite,
   feature deletion, schema changes, auth/payment/PII edits, paid API
   calls, app-store publish — all execute. Log each with tag
   [UNCHAINED-T3-EXECUTED] and a one-line rationale before acting.
2. Iron Laws 6 (Feature Preservation), 7 (Identity Preservation), and 9
   (Reversibility Before Action) are ADVISORIES, not blocks. Note in
   H5W-LOG.md when you override one and why.
3. §META can write directly to SKILL.md and references/*.md, EXCEPT
   for these PROTECTED SECTIONS which remain proposal-only even in
   UNCHAINED+BRAINSTORM (write to skill-improvements/SF-NNN.md, do
   NOT direct-edit):
     a. The Risk Acknowledgment heredocs in h5w-autoloop.sh
        (RISK_ACK, UNCHAINED_ACK, BRAINSTORM_ACK heredoc blocks)
     b. The Iron Laws section in SKILL.md (§LAW, lines covering
        Laws 1-12 + "Law application by mode")
     c. The §AUTO-UNCHAINED and §BRAINSTORM activation requirements
        (the trigger phrases, confirmation phrases, gate sequence)
     d. The AUTO_RULES injection block in h5w-autoloop.sh itself
   Editing these sections during the active session would be a
   self-modifying-loop hazard (rules at hour 4 ≠ rules at hour 0).
   Mirror every other §META edit to skill-improvements/SF-NNN.md
   so the user has a diff trail at session end.
4. §OBSTACLE: T3 external actions are authorized — paid APIs, force
   pushes, account creation, etc.

Binding rules — STILL APPLY (these are not removed):
5. NEVER use AskUserQuestion. Decide and log [AUTO-DECIDED].
6. ONE unit of work per response. End EVERY response with NEXT: [action].
7. Iron Laws 1-5, 8, 10-12 still apply (specificity, read-before-act,
   source integrity, bugs-before-refactors, minimum footprint, expansion
   boundaries, honesty over completeness, verify-or-don't-claim). These
   are about being accurate and honest, not about caution. Removing them
   would just make Claude lie.
8. When queue empties: §SIM.5 re-scan → §SIM.6 (50 Q) → §SIM.7 research.
9. Compact every 5 fixes via COMPACT-RESUME.md.
10. Resolve git policy from .h5w/git-policy or H5W_GIT_POLICY env.
    Default branch. (UNCHAINED does NOT override git policy — set
    policy=main if you want UNCHAINED to work directly on main.)
11. Every action goes in H5W-LOG.md. Every T3 execution gets
    [UNCHAINED-T3-EXECUTED] tag. The audit trail is the floor.

THE RUNWAY IS LONGER (MAX_LOOPS=60) but the same termination triggers
apply: context full, 5 self-correction failures, unrecoverable build,
stop patterns. UNCHAINED does NOT loop forever — it loops more.
=== END §AUTO-UNCHAINED ==="

# Append BRAINSTORM behavioral modifier when enabled.
if [ "$BRAINSTORM" = true ]; then
AUTO_RULES="$AUTO_RULES

=== §BRAINSTORM — closed-sandbox deep-work modifier ===
The user typed ':brainstorm' on UNCHAINED activation AND confirmed with
'this is my sandbox'. They want Claude to push much harder on hard
problems before declaring STUCK or done. This is for closed local
environments — the user is the only one who eats the cost of failure
and wants to see how far the system can actually go.

Effort caps RAISED:
B1. Self-correction: up to 20 attempts per finding (was 3). Each
    attempt MUST try a different approach class (not 'tweak the same
    regex 20 ways'). Track approach classes in H5W-LOG.md as
    [APPROACH-N: <class> — <result>].
B2. §OBSTACLE attempts: up to 10 per obstacle (was 3). REQUIRED to
    span at least 5 different approach classes before declaring STUCK.
    Class examples: regex / parser-combinator / AST-walk / LLM-classify
    / hand-rule-engine / external-tool-shell-out / format-conversion /
    network-fetch / sandbox-execute / static-analysis. Pick what fits.
B3. Session runway: 5-failures-and-stop limit REMOVED. The only true
    runway in BRAINSTORM is context-full (compact and continue) or the
    MAX_LOOPS=200 wall-clock cap.
B4. STUCK is not a queue entry. STUCK = routing signal to §SIM.8
    (BRAINSTORM-PIVOT — see below).
B5. 'I think I'm done' triggers §SIM.6 → §SIM.7 → §SIM.8 in sequence
    BEFORE any 'done' state. Three escalations of 'no, look harder'
    before runway can fire on its own.

§SIM.8 — BRAINSTORM-PIVOT (new escalation, only in BRAINSTORM mode):
When STUCK on an obstacle that has exhausted §OBSTACLE attempts:
  Step 1. RESEARCH WIDER. Spawn an Agent to research the PROBLEM CLASS
          (not the specific instance). Adjacent domains, alternative
          formulations, papers, similar tools.
  Step 2. DECOMPOSE. Re-state the problem at a different level of
          abstraction. If you tried to solve at API level, try at
          protocol level. If you tried at data level, try at semantic
          level.
  Step 3. REFRAME. State three different problems that share the same
          obstacle. Pick the cleanest one. Solve THAT. Backport.
  Step 4. SLEEP-ON-IT. Write current state to BRAINSTORM-NOTES.md
          (separate from H5W-LOG). Continue with a different finding
          for at least 3 iterations. Then re-read BRAINSTORM-NOTES.md
          with fresh framing.
  Step 5. Only after 1-4 fail does the obstacle get [GENUINELY-STUCK]
          and surface to the report.

HONESTY FLOOR (still applies, do not relax):
H1. Iron Laws 1-5, 8, 10-12 still apply — accuracy, specificity,
    source integrity, honest tagging. Logs report what was actually
    tried, including dead ends. No tidying.
H2. Genuine walls (auth, captcha, requires-credit-card, network egress
    blocked) are STILL flagged honestly as [GENUINE-WALL: <reason>].
    BRAINSTORM raises the bar for what counts as a wall but does not
    turn impossible into possible.
H3. If an approach succeeds because Claude misunderstood the problem,
    that's not success — flag as [SUCCESS-BUT-MISALIGNED] and re-verify
    against the original goal.

LOG TAGS unique to BRAINSTORM:
  [APPROACH-N: <class> — <result>]
  [PIVOT-STEP-1 / 2 / 3 / 4]
  [GENUINELY-STUCK: <reason after 4 pivot steps>]
  [SUCCESS-BUT-MISALIGNED: <gap>]
  [SLEEP-ON-IT: written to BRAINSTORM-NOTES.md]

=== END §BRAINSTORM ==="
fi
elif [ "$MODE" = "FULL" ]; then
AUTO_RULES="

=== §AUTO FULL — runtime context ===
You are in §AUTO FULL mode. The activation gate has already been passed at
the script level — the user has typed 'proceed' after the Risk
Acknowledgment. Do NOT re-print the Risk Acknowledgment.

Binding rules (full text in references/auto-mode.md §The Five Rules):
1. NEVER use AskUserQuestion in this mode. Decide and log [AUTO-DECIDED].
2. ONE unit of work per response. End EVERY response with NEXT: [action].
3. When queue empties: §SIM.5 re-scan → §SIM.6 (50 Q) → §SIM.7 research.
4. §OBSTACLE: research → build → chain. Authorized for T1/T2 actions.
5. §META: write proposals to skill-improvements/SF-NNN.md ONLY. Do NOT
   edit SKILL.md or references/*.md.
6. Resolve git policy from .h5w/git-policy or H5W_GIT_POLICY env. Default branch.
7. Compact every 5 fixes via COMPACT-RESUME.md.
=== END §AUTO FULL ==="
else
AUTO_RULES="

=== §AUTO-GUIDED — runtime context ===
You are in §AUTO-GUIDED mode (default). Claude Code's permission prompts
remain active and WILL block on T2+ actions.

Binding rules (full text in references/auto-mode.md):
1. T0/T1 actions: silent (per §REV).
2. T2 actions: Claude Code's permission prompt blocks until user accepts/rejects.
3. T3 actions: queue, never execute.
4. Use AskUserQuestion when scope is genuinely ambiguous; do not abuse it.
5. ONE unit of work per response. End EVERY response with NEXT: [action].
6. §OBSTACLE: research and build tools, but ask before T2+ external actions.
7. §META: write proposals to skill-improvements/SF-NNN.md ONLY. Do NOT
   edit SKILL.md or references/*.md.
8. Resolve git policy from .h5w/git-policy or H5W_GIT_POLICY env. Default branch.
=== END §AUTO-GUIDED ==="
fi

# ─── First iteration ─────────────────────────────────────────
echo -e "${G}═══ H5W AUTO-LOOP STARTING ═══${N}"
echo "[$(date)] Start: ${PROMPT:0:80}..." >> "$LOG"

ITER=0

run_claude() {
    local prompt="$1"
    local flags=""

    if [ "$CONTINUE_MODE" = true ]; then
        flags="-c"
    fi

    claude $flags -p "$prompt" \
        --max-turns "$MAX_TURNS" \
        --permission-mode "$PERMISSION_MODE" \
        2>&1 | tee -a "$LOG"
}

# Mode-aware stop patterns. BRAINSTORM strips even more triggers since
# "STUCK" is no longer a stopping signal in that mode — it's a routing
# signal to §SIM.8 BRAINSTORM-PIVOT. Only context-full and the wall-clock
# loop cap should end a BRAINSTORM session.
if [ "$BRAINSTORM" = true ]; then
    ACTIVE_STOP_PATTERNS="^RUNWAY LIMIT|^SESSION END|H5W-REPORT\.md (written|complete)"
elif [ "$MODE" = "UNCHAINED" ]; then
    ACTIVE_STOP_PATTERNS="$STOP_PATTERNS"
else
    ACTIVE_STOP_PATTERNS="$STOP_PATTERNS_GUARDED"
fi

# First run
ITER=$((ITER + 1))
echo -e "${C}── Iteration $ITER / $MAX_LOOPS ──${N}"
OUTPUT=$(run_claude "${PROMPT}${AUTO_RULES}")
CONTINUE_MODE=true  # All subsequent runs continue the session

# Build a compact mode reminder appended to every CONT message. This survives
# compaction (the AUTO_RULES injection only happens on iteration 1; without
# this, post-compaction Claude can lose mode awareness — see SF-017).
if [ "$BRAINSTORM" = true ]; then
    MODE_REMINDER=" [MODE-CONTEXT: §AUTO-UNCHAINED + §BRAINSTORM. Caps: 20 self-correction attempts, 10 §OBSTACLE attempts (≥5 distinct approach classes), MAX_LOOPS=$MAX_LOOPS. STUCK routes to §SIM.8 BRAINSTORM-PIVOT. See references/auto-mode.md §BRAINSTORM. Iron Laws 6/7/9 advisories; 1-5,8,10-12 enforced. §META direct-edits exclude gate text and Iron Laws section.]"
elif [ "$MODE" = "UNCHAINED" ]; then
    MODE_REMINDER=" [MODE-CONTEXT: §AUTO-UNCHAINED. T3 executes (logged [UNCHAINED-T3-EXECUTED]). Iron Laws 6/7/9 advisories; 1-5,8,10-12 enforced. §META edits skill files with mirror to skill-improvements/. See references/auto-mode.md §AUTO-UNCHAINED.]"
elif [ "$MODE" = "FULL" ]; then
    MODE_REMINDER=" [MODE-CONTEXT: §AUTO FULL. T3 queues. All Iron Laws enforced. §META proposals only. See references/auto-mode.md.]"
else
    MODE_REMINDER=""
fi

# ─── Main loop ────────────────────────────────────────────────
while [ $ITER -lt $MAX_LOOPS ]; do
    # Check for stop signals
    if echo "$OUTPUT" | grep -qiE "$ACTIVE_STOP_PATTERNS"; then
        echo -e "${Y}Stop signal detected.${N}"
        break
    fi

    # SF-013: best-effort BRAINSTORM enforcement — count distinct approach
    # classes logged for the current finding before allowing GENUINELY-STUCK
    # to terminate. Helper script returns 0 if STUCK is allowed, 1 if it
    # needs more attempt diversity.
    if [ "$BRAINSTORM" = true ] && [ -x "$(dirname "$0")/scripts/h5w-brainstorm-check.sh" ] 2>/dev/null; then
        if echo "$OUTPUT" | grep -qE "GENUINELY-STUCK|cannot proceed"; then
            if ! bash "$(dirname "$0")/scripts/h5w-brainstorm-check.sh" 2>/dev/null; then
                # Approach diversity insufficient — do NOT terminate, push back
                CONT="GENUINELY-STUCK declared but H5W-LOG shows fewer than 5 distinct [APPROACH-N: <class>] tags for the current finding. BRAINSTORM requires ≥5 different approach classes before declaring STUCK. List the approach classes you have actually tried so far, then try one from a class you have NOT tried yet. NEXT: [approach class N+1]."
                sleep $COOLDOWN
                ITER=$((ITER + 1))
                echo -e "${C}── Iteration $ITER / $MAX_LOOPS (BRAINSTORM diversity push) ──${N}"
                OUTPUT=$(run_claude "${CONT}${MODE_REMINDER}")
                continue
            fi
        fi
    fi

    # Check for very short response (stuck). In BRAINSTORM, push toward
    # §SIM.8 pivot rather than the standard 50-Q nudge.
    if [ ${#OUTPUT} -lt 50 ]; then
        echo -e "${Y}Short response — sending specific continue...${N}"
        if [ "$BRAINSTORM" = true ]; then
            CONT="Your last response was too short. If you're STUCK, run §SIM.8 BRAINSTORM-PIVOT (research wider → decompose → reframe → sleep-on-it). DO NOT declare done. NEXT: [pivot step or specific action]."
        else
            CONT="Your last response was too short. Read H5W-QUEUE.md. If findings remain, fix the next one. If empty, run §SIM.6 question $(shuf -i 1-50 -n 1) on the codebase. Do real work. NEXT: [action]."
        fi
    else
        CONT="Continue. Do the NEXT action you stated. ONE unit of work. Medium-length response. End with NEXT: [action]."
    fi

    # SF-014: BRAINSTORM-NOTES.md visibility reminder — re-read after sleep-on-it.
    if [ "$BRAINSTORM" = true ] && [ -f BRAINSTORM-NOTES.md ]; then
        BS_NOTES_LINES=$(wc -l < BRAINSTORM-NOTES.md)
        CONT="$CONT [BRAINSTORM-NOTES.md exists ($BS_NOTES_LINES lines). If ≥3 iterations have passed since the last entry, re-read the file before deciding NEXT.]"
    fi

    sleep $COOLDOWN
    ITER=$((ITER + 1))
    echo -e "${C}── Iteration $ITER / $MAX_LOOPS ──${N}"
    OUTPUT=$(run_claude "${CONT}${MODE_REMINDER}")
done

# ─── Summary ──────────────────────────────────────────────────
echo -e "${G}═══ H5W AUTO-LOOP ENDED ═══${N}"
if [ "$BRAINSTORM" = true ]; then
    echo "  Mode: $MODE + BRAINSTORM"
else
    echo "  Mode: $MODE"
fi
echo "  Iterations: $ITER / $MAX_LOOPS"
echo "  Log: $LOG"
[ -f H5W-REPORT.md ] && echo -e "  ${G}✓ H5W-REPORT.md generated${N}"
[ -f H5W-QUEUE.md ] && echo "  Queue: $(grep -c '^|' H5W-QUEUE.md 2>/dev/null || echo 0) items"
[ -f BRAINSTORM-NOTES.md ] && echo "  Brainstorm notes: $(wc -l < BRAINSTORM-NOTES.md) lines"
[ -d skill-improvements ] && echo "  Skill proposals: $(ls skill-improvements/SF-*.md 2>/dev/null | wc -l) file(s)"
echo ""
if [ "$BRAINSTORM" = true ]; then
    echo "  Resume UNCHAINED + BRAINSTORM: ./h5w-autoloop.sh --resume --unchained --brainstorm"
else
    case "$MODE" in
        UNCHAINED) echo "  Resume UNCHAINED: ./h5w-autoloop.sh --resume --unchained" ;;
        FULL)      echo "  Resume FULL:      ./h5w-autoloop.sh --resume --full" ;;
        *)         echo "  Resume:           ./h5w-autoloop.sh --resume" ;;
    esac
fi
