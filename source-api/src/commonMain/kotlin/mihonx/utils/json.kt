@file:Suppress("UNUSED")

package mihonx.utils

import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * App provided default [Json] instance. Configured as
 * ```
 * Json {
 *     ignoreUnknownKeys = true
 *     explicitNulls = false
 * }
 * ```
 *
 * @since extensions-lib 1.6
 */
val defaultJson: Json = Injekt.get<Json>()
