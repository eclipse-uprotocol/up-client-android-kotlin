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

import org.eclipse.uprotocol.TestBase
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.uStatus
import org.junit.Assert
import org.junit.Test

class UStatusExceptionTest : TestBase() {
    @Test
    fun testConstructorWithStatus() {
        val exception = UStatusException(STATUS)
        Assert.assertEquals(STATUS, exception.status)
        Assert.assertEquals(CODE, exception.code)
        Assert.assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testConstructorWithStatusAndCause() {
        val exception = UStatusException(STATUS, CAUSE)
        Assert.assertEquals(STATUS, exception.status)
        Assert.assertEquals(CODE, exception.code)
        Assert.assertEquals(MESSAGE, exception.message)
        Assert.assertEquals(CAUSE, exception.cause)
    }

    @Test
    fun testConstructorWithCodeAndMessage() {
        val exception = UStatusException(CODE, MESSAGE)
        Assert.assertEquals(STATUS, exception.status)
        Assert.assertEquals(CODE, exception.code)
        Assert.assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testConstructorWithCodeMessageAndCause() {
        val exception = UStatusException(CODE, MESSAGE, CAUSE)
        Assert.assertEquals(STATUS, exception.status)
        Assert.assertEquals(CODE, exception.code)
        Assert.assertEquals(MESSAGE, exception.message)
        Assert.assertEquals(CAUSE, exception.cause)
    }

    @Test
    fun testGetStatus() {
        val exception = UStatusException(STATUS)
        Assert.assertEquals(STATUS, exception.status)
    }

    @Test
    fun testGetCode() {
        val exception = UStatusException(STATUS)
        Assert.assertEquals(CODE, exception.code)
    }

    @Test
    fun testGetMessage() {
        val exception = UStatusException(STATUS)
        Assert.assertEquals(MESSAGE, exception.message)
    }

    companion object {
        private val CODE = UCode.OK
        private const val MESSAGE = "Test message"
        private val STATUS = uStatus {
            code = CODE
            message = MESSAGE
        }
        private val CAUSE = Throwable(MESSAGE)
    }
}
