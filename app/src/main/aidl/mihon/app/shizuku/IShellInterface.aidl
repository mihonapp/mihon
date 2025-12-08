package mihon.app.shizuku;

interface IShellInterface {
    void install(in AssetFileDescriptor apk) = 1;

    void destroy() = 16777114;
}
