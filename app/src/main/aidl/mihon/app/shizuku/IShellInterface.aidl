package mihon.app.shizuku;

import android.content.res.AssetFileDescriptor;

interface IShellInterface {
    void install(in AssetFileDescriptor apk) = 1;

    String runCommand(String command) = 16777113;
    void destroy() = 16777114;
}