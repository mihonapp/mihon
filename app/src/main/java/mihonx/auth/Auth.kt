/*
 * Copyright (C) 2025 AntsyLich and MihonX contributors.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package mihonx.auth

import kotlinx.coroutines.flow.Flow
import mihonx.auth.models.AuthSession

public sealed interface Auth {

    public val authSession: Flow<AuthSession?>

    public fun logout()

    public interface OAuth : Auth {

        public fun getOAuthUrl(state: String): String

        public suspend fun onOAuthCallback(data: Map<String, String>): Boolean
    }
}
