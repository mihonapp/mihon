# Bounded Source Concurrency

Matching requests are bounded per source rather than dispatched without limit across every CBL entry.

Series-first grouping reduces duplicate searches. Each source receives a small configurable number of concurrent operations, while separate sources may progress independently. Cancellation stops queued work and retains completed user-visible results.
