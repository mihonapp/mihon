package exh.util

inline fun <T> ignore(expr: () -> T): T? {
    return try { expr() } catch (t: Throwable) { null }
}


