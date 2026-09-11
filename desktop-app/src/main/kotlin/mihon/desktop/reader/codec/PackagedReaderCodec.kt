package mihon.desktop.reader.codec

import java.nio.file.Path

object PackagedReaderCodec {
    const val CODEC_PROPERTY = "mihon.reader.codec"

    fun executablePath(): Path {
        System.getProperty(CODEC_PROPERTY)?.takeIf { it.isNotBlank() }?.let {
            return Path.of(it).toAbsolutePath().normalize()
        }
        System.getProperty("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let {
            return Path.of(it, "codec", "magick.exe").toAbsolutePath().normalize()
        }
        System.getenv("APPDIR")?.takeIf { it.isNotBlank() }?.let {
            return Path.of(it, "resources", "codec", "magick.exe").toAbsolutePath().normalize()
        }
        return Path.of(
            System.getProperty("user.dir"),
            "desktop-app",
            "build",
            "reader-codec",
            "app-resources",
            "windows",
            "codec",
            "magick.exe",
        ).toAbsolutePath().normalize()
    }
}
