/*
 * Copyright (c) 2024 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.uprotocol.common

import org.eclipse.uprotocol.common.util.buildStatus
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UStatus

/**
 * The unchecked exception which carries uProtocol error model.
 * @param status An error [UStatus].
 * @param cause  An exception that caused this one.
 */
class UStatusException @JvmOverloads constructor(val status: UStatus, cause: Throwable? = null) :
    RuntimeException(status.message, cause) {

    /**
     * Constructs an instance.
     *
     * @param code    An error [UCode].
     * @param message An error message.
     */
    constructor(code: UCode, message: String?) : this(code.buildStatus(message), null)

    /**
     * Constructs an instance.
     *
     * @param code    An error [UCode].
     * @param message An error message.
     * @param cause   An exception that caused this one.
     */
    constructor(code: UCode, message: String?, cause: Throwable) : this(code.buildStatus(message), cause)

    val code: UCode
        /**
         * Get the error code.
         * @return The error [UCode].
         */
        get() = status.code
    override val message: String
        /**
         * Get the error message.
         * @return The error message.
         */
        get() = status.message
}
