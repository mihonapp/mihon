# Clean release defaults, 2026-09-15

Target version: 0.2.4.

## Requirement and changes

Published installers and portable archives must contain application binaries and runtimes only, without configured content sources, extension repositories, installed extensions, accounts, or user profiles. Existing local user data must be preserved during upgrades.

The 0.2.3 portable archive contained no profile files, but `ExtensionStoreService.getRepositories()` substituted a hardcoded extension repository whenever the saved list was absent or blank. It also restored that repository after the last entry was removed. The fallback is now removed. Explicitly saved repository entries are still read and persisted.

All Windows installer and portable packaging tasks now depend on `verifyCleanDistribution`. It runs `scripts/verify-release-clean.ps1` against the finished application image before packaging. Unexpected root entries, profile directories, saved preference/cookie/database files, and installed `.mext` or `.apk` packages cause a failure. Validation does not delete user data.

## Verification

- Before the fix, fresh/blank repository assertions failed with the hardcoded repository present. A separate first-refresh test also failed because it attempted repository requests.
- After the fix, `ExtensionStoreServiceTest` reported 17 tests, zero failures/errors, and one existing disabled live-repository test: 16 executed tests passed.
- Tests cover fresh profiles making zero repository requests, saved whitespace remaining empty, add/remove surviving a new preference-store instance, and explicitly saved repositories being preserved.
- The packaging guard accepted the existing application image and a clean structural fixture. It rejected separate fixtures containing a `data` directory, an installed `.mext` package, or a saved `preferences.properties` file.
- Delivery, fresh-profile runtime checks, installation, and GitHub asset verification are recorded under ignored `desktop-app/build/deliveries/0.2.4/` after packaging; personal profiles are not committed or published.
