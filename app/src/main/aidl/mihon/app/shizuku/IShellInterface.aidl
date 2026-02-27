package mihon.app.shizuku;

import android.content.res.AssetFileDescriptor;

interface IShellInterface {
    void install(in AssetFileDescriptor apk) = 1;
    void uninstall(String packageName) = 2;

    void destroy() = 16777114;
}