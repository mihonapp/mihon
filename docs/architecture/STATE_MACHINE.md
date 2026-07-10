# Matching State Machine

## States

- `UNSEARCHED`: no matching attempt has started.
- `SEARCHING`: an active visible operation is resolving the entry.
- `AUTO_MATCHED`: accepted automatically under current thresholds.
- `USER_CONFIRMED`: explicitly selected by the user and protected from automatic replacement.
- `AMBIGUOUS`: candidates exist but certainty or margin is insufficient.
- `UNRESOLVED`: no acceptable candidate is selected.
- `SOURCE_UNAVAILABLE`: the selected extension or remote source cannot currently be used.
- `CHAPTER_REMOVED`: the mapped remote series remains available but the chapter target is missing.
- `NEEDS_REMATCH`: retained metadata requires a new resolution attempt.

## Protection rule

Automatic operations may report a problem with `USER_CONFIRMED` data but may not silently transition it to a different remote target.
