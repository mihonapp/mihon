# mihondesk product rename, 2026-09-16

Version: 0.2.5.

## Scope

- The Windows program, installer ProductName, EXE filename, portable root, shortcuts, window title, localized app name/about headings, lock screen, notifications, and web verification window now use `mihondesk`.
- The portable updater is distributed as `mihondesk-updater.ps1` and supports the renamed archive root and executable. An explicit legacy executable name can still select a legacy archive root.
- Updates use the actual `1873412297-art/mihondesk` GitHub repository and support the new installer and portable asset names.
- Packaging, clean-image guards, MSI validation, and current verification scripts use the new paths.

## Compatibility boundaries

The MSI upgrade UUID remains `07E02BEA-9179-4E54-A1AF-CFC185C91398`. Installed application data remains in `%APPDATA%\MihonW`; credential identifiers, background task identifiers, and file association ProgIDs remain stable. Portable data remains beside the executable in `data`. The renamed packaged executable is recognized by the runtime so background scheduling remains available and enabled tasks can be rebound to its path.

The release keeps the empty source/repository defaults and clean-distribution checks from 0.2.4. The application icon is not changed in this release.

## Validation before packaging

- The new update test failed first because the request went to the old repository, then passed with the renamed repository and asset names.
- Six focused test classes reported 54 tests, zero failures/errors, and two environment-dependent skips: 52 executed tests passed.
- Portable updater checks passed for checksum rejection, invalid-executable rollback from a renamed archive to a legacy installation, and user data preservation.
- Background uninstall checks passed for exact executable ownership.
- Installation, shortcut, executable metadata, portable startup, profile preservation, and GitHub asset checks are recorded in ignored `desktop-app/build/deliveries/0.2.5/` after packaging.
