# Development APKs

Yomori development APKs are built by GitHub Actions and can be downloaded directly on a phone.

## Package and signing

- Package: `io.github.kamui2040.yomori.debug`
- Signing: dedicated public development certificate
- Telemetry: disabled
- Purpose: testing only

The public development certificate allows every Yomori development APK to update an earlier development APK. It is intentionally unsuitable for a production release because anyone can reproduce the public test key. A future production build will use a different package/signing plan and a protected key.

## Filename format

```text
Yomori-v<version>-build<workflow-run>-<short-sha>-<abi>.apk
```

Example:

```text
Yomori-v0.1.0-alpha01-build12-a1b2c3d-arm64-v8a.apk
```

For most current Android phones, use the `arm64-v8a` APK. The `universal` APK is larger but works across supported architectures.

## First installation

Earlier Yomori CI artifacts used the base package `io.github.kamui2040.yomori` and a temporary GitHub-runner signature. The new development APK uses the separate `.debug` package, so it installs beside the earlier build instead of replacing it.

After the first `.debug` APK is installed, later development APKs can be installed over it directly without uninstalling. Transfer any wanted data from the earlier build using backup and restore before removing that older installation.
