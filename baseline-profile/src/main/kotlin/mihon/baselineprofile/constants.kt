package mihon.baselineprofile

import androidx.test.platform.app.InstrumentationRegistry

internal val TARGET_PACKAGE_NAME: String
    inline get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: throw Exception("targetAppId not passed as instrumentation runner arg")
