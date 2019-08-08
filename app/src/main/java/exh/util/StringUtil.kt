package exh.util

fun List<String>.trimAll() = map { it.trim() }
fun List<String>.dropBlank() = filter { it.isNotBlank() }
fun List<String>.dropEmpty() = filter { it.isNotEmpty() }
