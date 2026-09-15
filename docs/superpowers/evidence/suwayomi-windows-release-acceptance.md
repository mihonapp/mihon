# 0.2.0 Windows release engineering

## Implemented

- `desktop-version.txt` is the single version source. Gradle emits the runtime version resource; package MSI/EXE/portable and update service use it.
- `stageDistributionResources` stages codec, independent Java 21/JCEF browser runtime and host, third-party license index, and installation maintenance script. Main runtime remains Java 17.
- `assembleWindowsRelease` collects same-version app-image/MSI/EXE/ZIP in `desktop-app/build/releases/0.2.0` and writes recursive SHA256SUMS.txt. This task builds artifacts; it does not install them.
- Local isolated-directory verification accepts an explicit ZIP, reads current version and uses the shared Gradle wrapper; it no longer calls itself a clean Windows machine acceptance.
- `--remove-background-tasks` invokes the current profile scheduler, emits status and propagates errors. Script cleanup matches both MihonW task names and the exact installed executable action. Neither deletes user data.
- JDK17 main.wxs template has a deferred, impersonated cleanup action before RemoveFiles, only for uninstall (not a major upgrade). MSI/EXE task actions invoke JDK17 jpackage directly against the completed app-image, with an independent resource-dir override.

## Verification status

PowerShell parser checks and WiX XML parsing passed. Gradle stageDistributionResources and DesktopAppUpdateServiceTest passed (37s). CLI first run: 13/14 passed; obsolete 0.1.3 assertion was corrected; subsequent BackgroundCommandTest + DesktopCommandTest passed all 14 tests. PowerShell tests passed for checksum rejection, executable-failure rollback, data preservation and installation-scoped task removal. Full MSI hook execution, app-image launch, Win10/Win11 clean machines, previous 0.1.3 package upgrades, successful old-version upgrade and UI acceptance are not yet verified here. Do not mark T11 complete from this engineering report.

T4 native browser verification is separately recorded in suwayomi-webview-host.md. Chromium credits export and final packaged file hashes must be refreshed for the final integrated build.

## Sources

- https://docs.oracle.com/en/java/javase/21/jpackage/override-jpackage-resources.html
- WiX template extracted from installed Temurin 17.0.18 jdk.jpackage.jmod; original required properties retained.


Portable update now requires ExpectedSha256. The complete new directory is staged on the same volume before switching; validation failure restores the prior directory. Successful updates retain the previous directory as an explicit rollback copy. GUI update installation wiring is outside this script test; download success is not treated as install success.

## Installer validation rejection and reproducible scope

The attempted exec command wrote a proposed `scripts/tests/uninstall-msi.tests.ps1` with a PowerShell here-string, then invoked `& scripts/tests/uninstall-msi.tests.ps1`. Its intended sequence was WiX candle/light of a uniquely generated ProductCode, `Start-Process msiexec.exe -ArgumentList '/i "<fixture>/test.msi" /qn INSTALLDIR="<fixture>/installed" /l*v "<fixture>/install.log"'`, creation of one `MihonW-smoke-<guid>` task, and `/x "<fixture>/test.msi" /qn` followed by assertions.

The intended fixture was `scripts/tests/uninstall-fixture-<new-guid>` beneath this worktree. The entire tool command was rejected before execution, so no GUID was evaluated, no test script/fixture/MSI was created, and no installation or task registration occurred. Exact tool rejection: `rejected: blocked by policy`. No more specific reason was supplied. Root explicitly instructed not to retry this installation through another route. Actual MSI install/uninstall hook execution remains **not verified**.

Read-only verification is implemented in `scripts/verify-msi-package.ps1`. The first MSI build succeeded (2m59s) but its CustomAction table did not contain MihonRemoveBackgroundTasks: Compose erased its private resource directory during task execution. The read-only script correctly rejects that artifact. Installer task implementation now packages the completed app-image directly with JDK17 jpackage and the independent override directory; the replacement artifact passed the read-only database checks below.

Chromium credits were exported from the exact native browser's chrome://credits using its source visitor (not copied from a different Chromium version). The UTF-8 file SHA256 is 007AD8F6FD12CD5205613B999111F1788CA25D9EA25255D567A0BD325D4A9B67. Host stdout is explicitly UTF-8, fixing the Windows native-codepage issue found by this non-ASCII content export.


First app-image created successfully, but root GUI smoke caught LifecycleRegistry rejecting the EDT because extension-host's Android dispatcher factory was selected by the desktop process. Root corrected desktop fallback to the Swing dispatcher and verified DesktopMainThreadTest/RuntimeFactory tests; the corrected app-image was rebuilt successfully; root owns the subsequent GUI acceptance. The independent three-process packaged-reader headless validation of that first image passed, as reported separately by the reader acceptance agent. These are distinct from GUI startup validation.


## Verified engineering artifact (not the final integrated release)

- Shared Gradle wrapper `:desktop-app:createDistributable :desktop-app:packageMsi` passed in **2m33s** after replacing Compose installer actions with direct jpackage against the completed app-image.
- `scripts/verify-msi-package.ps1` passed against the real MSI: ProductVersion 0.2.0; libcef.dll, Chromium credits and cleanup script present; MihonRemoveBackgroundTasks is deferred and scheduled before RemoveFiles with uninstall-only condition.
- Artifact: `desktop-app/build/compose/binaries/main/msi/MihonW-0.2.0.msi`, **485024844 bytes**, SHA256 **7079B1274F047FB8C8C1623AA760B2C68778437D90CFABE94822AAB2AA570818**.
- This artifact is an intermediate uncommitted integration snapshot. Final EXE/MSI/ZIP and recursive release manifest remain root-owned after final integration. The subsequently added Rhino 1.8.1 upstream license/index update will enter that final build, so do not publish this intermediate MSI.
- Complete upstream Rhino 1.8.1 LICENSE.txt (including its MPL-2.0 text and additional notices) was obtained from `https://raw.githubusercontent.com/mozilla/rhino/Rhino1_8_1_Release/LICENSE.txt` and staged under packaging/licenses. Browser legal, JCEF/CEF license files and native Chromium credits are retained.
- `generateDesktopVersion` also writes revision/dirty state to mihon-build-info.properties; `assembleWindowsRelease` collects this plus the complete file SHA-256 manifest.

No actual installation/upgrade was performed by this agent. The blocked isolated install/uninstall test was not retried. Win10/Win11 clean-machine acceptance, final installation, real OAuth/account trackers, and full live WebView source reading remain explicitly separate gates.
