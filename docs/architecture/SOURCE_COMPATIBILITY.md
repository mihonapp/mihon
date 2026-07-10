# Source and Extension Compatibility

Yomori supports user-installed Mihon/Tachiyomi-compatible extensions without shipping extensions or source implementations itself.

## Stable compatibility boundary

The extension-facing API packages are compatibility contracts, not product branding. In particular, do not rename `eu.kanade.tachiyomi.source` or its public models merely to replace Mihon/Tachiyomi names in the user interface.

## Extension discovery

Retain support for:

- Shared extension APKs usable by compatible variants
- Private extensions stored in application data
- Extension feature and metadata detection
- Supported source-library version checks
- Signature verification and trusted-signature handling
- Child-first extension class loading
- `Source` and `SourceFactory` entry points

## User control

- Repositories are added by the user.
- Extensions are installed by the user.
- A CBL import searches only extensions selected by the user for that list.
- No extension is automatically trusted.
- Source order and overrides remain editable.

## Update review

When syncing Mihon upstream, explicitly inspect changes to extension metadata, supported library versions, signature checks, package scanning, class loading, and source models. Record any behavior change in `PROJECT_CONTEXT.md` before merging.
