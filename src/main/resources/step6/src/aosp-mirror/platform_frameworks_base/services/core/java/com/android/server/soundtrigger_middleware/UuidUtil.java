/* (rank 11) copied from https://github.com/aosp-mirror/platform_frameworks_base/blob/c5d02da0f6553a00da6b0d833b67d3bbe87341e0/services/core/java/com/android/server/soundtrigger_middleware/UuidUtil.java
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.soundtrigger_middleware;

import java.util.regex.Pattern;

/**
 * Utilities for representing UUIDs as strings.
 *
 * @hide
 */
public class UuidUtil {
    /**
     * Regex pattern that can be used to validate / extract the various fields of a string-formatted
     * UUID.
     */
    static final Pattern PATTERN = Pattern.compile("^([a-fA-F0-9]{8})-" +
            "([a-fA-F0-9]{4})-" +
            "([a-fA-F0-9]{4})-" +
            "([a-fA-F0-9]{4})-" +
            "([a-fA-F0-9]{2})" +
            "([a-fA-F0-9]{2})" +
            "([a-fA-F0-9]{2})" +
            "([a-fA-F0-9]{2})" +
            "([a-fA-F0-9]{2})" +
            "([a-fA-F0-9]{2})$");

    /** Printf-style pattern for formatting the various fields of a UUID as a string. */
    static final String FORMAT = "%08x-%04x-%04x-%04x-%02x%02x%02x%02x%02x%02x";
}