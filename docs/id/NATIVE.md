[English](../NATIVE.md) | **Bahasa Indonesia**

# Rust pada KomiKita

Modul `native/` menghitung chapter yang hilang secara batch menggunakan array primitif. `core:native` menyediakan bridge JNI; `domain` memakai native mulai 512 chapter. Daftar kecil atau kegagalan memuat library memakai implementasi Kotlin.

WebView memakai [adblock-rust](https://github.com/brave/adblock-rust) dari Brave melalui `core:native` untuk menyaring permintaan jaringan. Build tanpa Rust memakai filter domain dan hosts dari implementasi Kotlin. Kelola filter lewat **Lainnya → Pengaturan → Lanjutan → Pemblokir iklan**.

Algoritma mengabaikan nomor negatif, NaN, infinity, dan angka di luar rentang Int. Pecahan memiliki nomor dasar yang sama. Penjumlahan memakai 64 bit untuk mencegah overflow.

## Toolchain

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
cargo install cargo-ndk --version 4.1.2 --locked
cargo test --manifest-path native/Cargo.toml --locked
cargo clippy --manifest-path native/Cargo.toml --all-targets --locked
cargo bench --manifest-path native/Cargo.toml --locked
```

Jika memakai NDK lain yang sudah terpasang, tambahkan `-Prust-ndk-path=/path/to/ndk` pada perintah Gradle.

`BuildRustTask` menjalankan cargo-ndk untuk empat ABI dan minimum Android API 26. JNI libraries dimasukkan sebagai generated source pada Android library. Untuk build tanpa Rust, gunakan `-Penable-rust=false`.

Saat memperbarui dependensi Cargo, sesuaikan metadata lisensi crate runtime Android di `app/rust-licenses`.

Buffer JNI disalin selama pemanggilan. Bridge Kotlin melewati penyalinan untuk daftar di atas 1.000.000 chapter. Jika alokasi buffer native gagal, penghitungan beralih ke Kotlin. Panic ditahan pada batas FFI.

Jalankan `./gradlew :core:native:connectedDebugAndroidTest -Penable-rust=true` di emulator atau perangkat untuk memverifikasi hasil JNI.

Untuk mengukur performa, jalankan `./gradlew :app:assembleBenchmark -Pdist=ci -Penable-rust=true` dan pasang APK dengan ABI yang sesuai di perangkat fisik ARM64. Jalankan `adb shell am start -n app.komikita.benchmark/app.komikita.benchmark.NativeBenchmarkActivity`, lalu ambil `/sdcard/Android/data/app.komikita.benchmark/files/native-boundary.csv`. CSV ini membandingkan fallback Kotlin produksi dan seluruh jalur JNI dengan data chapter terurut naik, terurut turun, dan campuran. Gunakan hasilnya untuk mengevaluasi ambang 512 chapter.

Referensi: [cargo-ndk](https://github.com/bbqsrc/cargo-ndk), [JNI crate 0.21.1](https://docs.rs/jni/0.21.1/jni/).
