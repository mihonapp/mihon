# Test plan: batch 1 (direct open, queue and cost, reading modes)

## Open translated chapter directly

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 1 | Open | Translate a chapter, wait for "Translation finished", tap the Open icon | The translated chapter opens directly in the reader (no stop at the library or series screen). The first time may take a moment while the translated series is registered |
| 2 | Open twice | Back out, tap Open again | Opens at once, no duplicate series or chapters in the Local source |
| 3 | Missing copy | Delete the translated folder outside the app, tap Open before the list refreshes | "Couldn't open" message, no crash |

## Queue and cost

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 4 | Estimate line | Tap Translate in Overlay mode, then in Redraw mode | Overlay shows "About N requests..."; Redraw shows "N images will be generated... billed to your API key". No prices are shown |
| 5 | Next chapters offered | Download chapters 1-6, tap Translate on chapter 2 | Chips "This chapter only", "+1 more", "+3 more"... up to the number of later downloaded chapters (max 9). Earlier chapters and chapters already translated are not offered |
| 6 | Page total | Select "+3 more" | The "N pages will be sent" line becomes the total of the four chapters |
| 7 | Queue order | Confirm with "+3 more" | Snackbar "Translation queued (4 chapters)". One chapter translates at a time, in chapter order, each finishing with its own notification |
| 8 | Charger and Wi-Fi | Turn on "Only while charging and on Wi-Fi" with the phone unplugged and on mobile data, confirm | Snackbar says it starts when charging and on Wi-Fi. Nothing runs until both are true, then the queue starts. The switch stays on the next time the dialog opens |
| 9 | Cancel one | Cancel the notification of the running chapter | Only that chapter stops; the next queued chapter starts |
| 10 | No later chapters | Tap Translate on the newest downloaded chapter | No chip row, only the switch |
| 11 | Chapters without numbers | A series whose chapters have no number | No chip row (nothing is guessed), single-chapter translate works as before |

## Reading modes (Overlay mode only)

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 12 | Settings | Overlay mode: open Chapter translation settings | Font, Text size (60-100%) and "Where the translation goes" are listed, plus a note that they apply when a chapter is translated. In Redraw mode they are hidden |
| 13 | Defaults | Translate with the defaults | Same look as before this change (bold sans, largest size that fits, text replaces the original) |
| 14 | Fonts | Translate the same chapter with Bold, Regular, Serif and Comic (delete the translated copy between runs) | Each looks different. Check Arabic/other non-Latin text still renders (Comic may fall back to the system font for them) |
| 15 | Size | Text size 60% | Translated text is visibly smaller inside the same bubbles, never larger than at 100% |
| 16 | Caption mode | Placement "caption box": translate a page with a hard layout | The original page is untouched; each translation sits in a rounded white box right below its original text (above it near the page bottom), readable, not outside the page |
| 17 | Stacked captions | Several bubbles close together in caption mode | Caption boxes do not cover each other (they move down or up) |
| 18 | Changing the setting later | Change the font, tap Translate on an already translated chapter | Nothing happens/"already translated": delete the translated copy (bin icon) first, then translate again |

## Overlay mode: protect faces and artwork

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 19 | Face preservation | Overlay-translate the same sample chapter with dialogue near faces (especially pages 004-006 and 011) | Faces, eyes, hair, expressions, clothing, and linework remain unchanged; an uncertain bubble is left untranslated rather than painted over a face |
| 20 | Caption/sign preservation | Overlay-translate pages with narration/signs over artwork (especially page 003) | Only the detected glyphs are erased; no flat gray/white rectangle covers the sign, hand, or other artwork |
| 21 | Text placement and cleanup | Inspect pages with multiple bubbles or small text (especially pages 004-005 and 009) | Each translation stays with its source text; boxes from separate/open regions are not merged; source text is either fully replaced or left intact, never partially erased |
| 22 | Tall-page close-ups | Overlay-translate a page at least 3,000 px high and with an aspect ratio of at least 4.5:1 | The page is sent as overlapping vertical crops, one request per crop; translated text is still rendered on the original full-size page |
| 23 | No unnecessary crop | Overlay-translate a page below the tall-page threshold (for example, 700 × 2,332 px) | The page is sent once as before; its coordinates and rendering are unchanged |
| 24 | Crop-boundary bubble | Use a tall page with a bubble crossing the overlap between adjacent crops | The bubble is not omitted; duplicate detections from the overlap render only once, with coordinates aligned to the original page |
| 25 | Request estimate | Open the Overlay translation confirmation for a chapter containing tall pages | The estimate says tall pages may need extra close-up requests, so the one-request-per-page count is clearly identified as approximate |
| 26 | Crop-edge text coverage | Use tall pages with readable dialogue near the top and bottom of each crop | The model translates visible words at crop edges and does not invent text outside the crop |
| 27 | Truncated response | Parse a valid-looking partial box list with `finishReason=MAX_TOKENS` | The page is reported as failed/incomplete instead of being silently accepted as fully translated |
| 28 | Source-grounded detection | Overlay-translate the supplied 12-page surgery chapter, including its thought bubbles and system/screen labels | Each entry includes source transcription and a translation; compare original/output and verify previously missed bubbles such as page 009 are handled |
| 29 | Outlined lettering cleanup | Translate stylised multicolour title/SFX text over artwork, especially page 003 | The old outlined lettering is removed without a flat mask, face damage, or Arabic laid on top of visible English |

## Library refresh scope

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 30 | Pull-to-refresh | Select any library category and pull down to refresh | Checks all eligible manga across all categories, not just the selected category |
| 31 | Explicit category update | Select a category and choose "Update category" from the library menu | Checks only manga assigned to that category |
