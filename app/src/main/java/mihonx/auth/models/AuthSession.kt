/*
 * Copyright (C) 2025 AntsyLich and MihonX contributors.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package mihonx.auth.models

import kotlinx.serialization.Serializable

@Serializable
public data class AuthSession(
    val user: User,
    val authExpired: Boolean,
    val authRevoked: Boolean,
    val memo: Map<String, String>,
)
