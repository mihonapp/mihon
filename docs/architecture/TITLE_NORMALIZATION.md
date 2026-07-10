# Title Normalization

Title normalization creates comparison forms without changing displayed source or CBL metadata.

Initial processing includes Unicode normalization, case folding, punctuation normalization, repeated whitespace removal, bracketed-year extraction, and cautious handling of volume markers and leading articles.

Potentially meaningful subtitles are retained. When a token may be either noise or identity, generate multiple comparison forms rather than destructively removing it.

Confirmed user aliases may supplement algorithmic normalization and must retain the original series tuple they apply to.
