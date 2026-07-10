# Confidence Scoring Rules

The matching engine returns an integer score from 0 to 100 plus individual evidence components.

## Principles

- Scores are deterministic for the same inputs and evidence version.
- Missing optional metadata is neutral rather than automatically contradictory.
- Contradictory metadata reduces confidence.
- A high score alone is insufficient when another candidate is close.
- A sole candidate may still require review.
- Source preference may break otherwise equal candidates but must not conceal weak title or issue evidence.
- Confirmed mappings are authority, not merely another weighted signal.

## Defaults

- Auto accept: 88
- Review: 65–87
- Unresolved: below 65
- Minimum lead over runner-up: 10

Thresholds are user-configurable. Weight customization is deferred until scoring behavior is validated with real fixtures.
