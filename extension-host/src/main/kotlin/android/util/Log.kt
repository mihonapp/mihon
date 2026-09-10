package android.util

object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    @JvmStatic fun v(tag: String, msg: String): Int {
        println("[$tag] $msg")
        return 0
    }

    @JvmStatic fun d(tag: String, msg: String): Int {
        println("[$tag] $msg")
        return 0
    }

    @JvmStatic fun i(tag: String, msg: String): Int {
        println("[$tag] $msg")
        return 0
    }

    @JvmStatic fun w(tag: String, msg: String): Int {
        System.err.println("[$tag] $msg")
        return 0
    }

    @JvmStatic fun w(tag: String, tr: Throwable): Int {
        tr.printStackTrace()
        return 0
    }

    @JvmStatic fun e(tag: String, msg: String): Int {
        System.err.println("[$tag] $msg")
        return 0
    }

    @JvmStatic fun e(
        tag: String,
        msg: String,
        tr: Throwable?,
    ): Int {
        System.err.println("[$tag] $msg")
        tr?.printStackTrace()
        return 0
    }

    @JvmStatic fun isLoggable(tag: String, level: Int): Boolean = true
}
