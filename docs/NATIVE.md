**English** | [Bahasa Indonesia](id/NATIVE.md)

# Rust in KomiKita

The `native/` module calculates missing chapters in batches using primitive arrays. `core:native` provides the JNI bridge; `domain` uses the native implementation for lists of 512 chapters or more. Smaller lists, or a failure to load the library, use the Kotlin implementation.

WebView also uses Brave's [adblock-rust](https://github.com/brave/adblock-rust) through `core:native` to apply network filter rules. Builds without Rust use the Kotlin domain and hosts filters. Manage the filters in **More → Settings → Advanced → Ad blocker**.

The algorithm ignores negative numbers, NaN, infinity, and values outside the Int range. Fractional chapters share their base chapter number. Totals use 64-bit arithmetic to prevent overflow.

## Toolchain

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
cargo install cargo-ndk --version 4.1.2 --locked
cargo test --manifest-path native/Cargo.toml --locked
cargo clippy --manifest-path native/Cargo.toml --all-targets --locked
cargo bench --manifest-path native/Cargo.toml --locked
```

To use another installed NDK, add `-Prust-ndk-path=/path/to/ndk` to the Gradle command.

`BuildRustTask` runs cargo-ndk for four ABIs with a minimum Android API level of 26. JNI libraries are included as generated sources in the Android library. To build without Rust, use `-Penable-rust=false`.

When updating Cargo dependencies, update the license metadata for Android runtime crates in `app/rust-licenses`.

JNI buffers are copied during a call. The Kotlin bridge skips that copy for lists above 1,000,000 chapters. A failed native buffer allocation triggers the Kotlin fallback. Panics are caught at the FFI boundary.

Run `./gradlew :core:native:connectedDebugAndroidTest -Penable-rust=true` on an emulator or device to verify the JNI results.

To measure performance, run `./gradlew :app:assembleBenchmark -Pdist=ci -Penable-rust=true` and install the matching ABI APK on a physical ARM64 device. Launch `adb shell am start -n app.komikita.benchmark/app.komikita.benchmark.NativeBenchmarkActivity`, then pull `/sdcard/Android/data/app.komikita.benchmark/files/native-boundary.csv`. The CSV compares the production Kotlin fallback and the full JNI path for ascending, descending, and mixed chapter numbers. Use these results to evaluate the 512-chapter threshold.

References: [cargo-ndk](https://github.com/bbqsrc/cargo-ndk), [JNI crate 0.21.1](https://docs.rs/jni/0.21.1/jni/).
