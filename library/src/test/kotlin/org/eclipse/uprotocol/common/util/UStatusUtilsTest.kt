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
package org.eclipse.uprotocol.common.util

import android.os.RemoteException
import com.google.protobuf.InvalidProtocolBufferException
import org.eclipse.uprotocol.common.UStatusException
import org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument
import org.eclipse.uprotocol.common.util.UStatusUtils.checkArgumentNonNegative
import org.eclipse.uprotocol.common.util.UStatusUtils.checkArgumentPositive
import org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull
import org.eclipse.uprotocol.common.util.UStatusUtils.checkState
import org.eclipse.uprotocol.common.util.UStatusUtils.checkStatusOk
import org.eclipse.uprotocol.common.util.UStatusUtils.checkStringEquals
import org.eclipse.uprotocol.common.util.UStatusUtils.checkStringNotEmpty
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class UStatusUtilsTest {
    @Test
    fun testIsOk() {
        assertTrue(STATUS_OK.isOk())
        assertFalse(STATUS_UNKNOWN.isOk())
    }

    @Test
    fun testHasCodeInt() {
        assertTrue(STATUS_OK.hasCode(UCode.OK_VALUE))
        assertFalse(STATUS_OK.hasCode(UCode.UNKNOWN_VALUE))
    }

    @Test
    fun testHasCode() {
        assertTrue(STATUS_OK.hasCode(UCode.OK))
        assertFalse(STATUS_OK.hasCode(UCode.UNKNOWN))
    }

    @Test
    fun testToCode() {
        assertEquals(UCode.OK, UCode.OK_VALUE.toUCode())
        assertEquals(UCode.DATA_LOSS, UCode.DATA_LOSS_VALUE.toUCode())
    }

    @Test
    fun testToCodeNegative() {
        assertEquals(UCode.UNKNOWN, (-1).toUCode())
        assertEquals(UCode.UNKNOWN, 100.toUCode())
    }

    @Test
    fun testBuildStatusCode() {
        val status: UStatus = UCode.INVALID_ARGUMENT.buildStatus()
        assertEquals(UCode.INVALID_ARGUMENT, status.code)
    }

    @Test
    fun testBuildStatusCodeAndMessage() {
        val status: UStatus = UCode.INVALID_ARGUMENT.buildStatus(MESSAGE)
        assertEquals(UCode.INVALID_ARGUMENT, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusUStatusException() {
        val status: UStatus = UStatusException(UCode.UNIMPLEMENTED, MESSAGE).toUStatus()
        assertEquals(UCode.UNIMPLEMENTED, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusCompletionException() {
        val cause: Throwable = IllegalArgumentException(MESSAGE)
        val status = CompletionException(cause).toUStatus()
        assertEquals(UCode.INVALID_ARGUMENT, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusCompletionExceptionWithStatus() {
        val cause: Throwable = UStatusException(UCode.NOT_FOUND, MESSAGE)
        val status = CompletionException(cause).toUStatus()
        assertEquals(UCode.NOT_FOUND, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusCompletionExceptionWithoutCause() {
        val status = CompletionException(null).toUStatus()
        assertEquals(UCode.UNKNOWN, status.code)
        assertEquals("", status.message)
    }

    @Test
    fun testToStatusExecutionException() {
        val cause: Throwable = IllegalArgumentException(MESSAGE)
        val status = ExecutionException(cause).toUStatus()
        assertEquals(UCode.INVALID_ARGUMENT, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusExecutionExceptionWithStatus() {
        val cause: Throwable = UStatusException(UCode.NOT_FOUND, MESSAGE)
        val status = ExecutionException(cause).toUStatus()
        assertEquals(UCode.NOT_FOUND, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusExecutionExceptionWithoutCause() {
        val status = ExecutionException(null).toUStatus()
        assertEquals(UCode.UNKNOWN, status.code)
        assertEquals("", status.message)
    }

    @Test
    fun testToStatusSecurityException() {
        val status = SecurityException(MESSAGE).toUStatus()
        assertEquals(UCode.PERMISSION_DENIED, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusInvalidProtocolBufferException() {
        val status = InvalidProtocolBufferException(MESSAGE).toUStatus()
        assertEquals(UCode.INVALID_ARGUMENT, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusIllegalArgumentException() {
        val status = IllegalArgumentException(MESSAGE).toUStatus()
        assertEquals(UCode.INVALID_ARGUMENT, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusNullPointerException() {
        val status = NullPointerException(MESSAGE).toUStatus()
        assertEquals(UCode.INVALID_ARGUMENT, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusCancellationException() {
        val status = CancellationException(MESSAGE).toUStatus()
        assertEquals(UCode.CANCELLED, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusIllegalStateException() {
        val status = IllegalStateException(MESSAGE).toUStatus()
        assertEquals(UCode.UNAVAILABLE, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusRemoteException() {
        val status = RemoteException(MESSAGE).toUStatus()
        assertEquals(UCode.UNAVAILABLE, status.code)
        // assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusUnsupportedOperationException() {
        val status = UnsupportedOperationException(MESSAGE).toUStatus()
        assertEquals(UCode.UNIMPLEMENTED, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusCancelledException() {
        val status = InterruptedException(MESSAGE).toUStatus()
        assertEquals(UCode.CANCELLED, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusDeadlineException() {
        val status = TimeoutException(MESSAGE).toUStatus()
        assertEquals(UCode.DEADLINE_EXCEEDED, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testToStatusUnknownException() {
        val status = RuntimeException(MESSAGE).toUStatus()
        assertEquals(UCode.UNKNOWN, status.code)
        assertEquals(MESSAGE, status.message)
    }

    @Test
    fun testCheckStatusOk() {
        checkStatusOk(STATUS_OK)
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) { checkStatusOk(STATUS_UNKNOWN) }
        assertEquals(STATUS_UNKNOWN, exception.status)
    }

    @Test
    fun testCheckArgument() {
        checkArgument(true, errorMessage = MESSAGE)
    }

    @Test
    fun testCheckArgumentNegative() {
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkArgument(false, errorMessage = MESSAGE)
        }
        assertEquals(UCode.INVALID_ARGUMENT, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckArgumentWithCode() {
        checkArgument(true, UCode.OK, MESSAGE)
    }

    @Test
    fun testCheckArgumentWithCodeNegative() {
        val code = UCode.INVALID_ARGUMENT
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkArgument(false, code, MESSAGE)
        }
        assertEquals(code, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckArgumentPositive() {
        val value = 1
        assertEquals(value.toLong(), checkArgumentPositive(value, errorMessage = MESSAGE).toLong())
    }

    @Test
    fun testCheckArgumentPositiveNegative() {
        val value = 0
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkArgumentPositive(
                value,
                errorMessage = MESSAGE
            )
        }
        assertEquals(UCode.INVALID_ARGUMENT, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckArgumentPositiveWithCode() {
        val value = 1
        assertEquals(
            value.toLong(),
            checkArgumentPositive(value, UCode.UNKNOWN, MESSAGE).toLong()
        )
    }

    @Test
    fun testCheckArgumentPositiveExceptionWithCode() {
        val value = 0
        val code = UCode.CANCELLED
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkArgumentPositive(value, code, MESSAGE)
        }
        assertEquals(code, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckArgumentNonNegative() {
        val value = 1
        assertEquals(value.toLong(), checkArgumentNonNegative(value, errorMessage = MESSAGE).toLong())
    }

    @Test
    fun testCheckArgumentNonNegativeNegative() {
        val value = -1
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkArgumentNonNegative(value, errorMessage = MESSAGE)
        }
        assertEquals(UCode.INVALID_ARGUMENT, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckArgumentNonNegativeWithCode() {
        val value = 1
        assertEquals(
            value.toLong(),
            checkArgumentNonNegative(value, UCode.UNKNOWN, MESSAGE).toLong()
        )
    }

    @Test
    fun testCheckArgumentNonNegativeWithCodeNegative() {
        val value = -1
        val code = UCode.CANCELLED
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkArgumentNonNegative(value, code, MESSAGE)
        }
        assertEquals(code, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckStringNotEmpty() {
        val string = "test"
        assertEquals(string, checkStringNotEmpty(string, errorMessage = MESSAGE))
    }

    @Test
    fun testCheckStringNotEmptyNegative() {
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkStringNotEmpty("", errorMessage = MESSAGE)
        }
        assertEquals(UCode.INVALID_ARGUMENT, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckStringNotEmptyWithCode() {
        val string = "test"
        assertEquals(string, checkStringNotEmpty(string, UCode.UNKNOWN, MESSAGE))
    }

    @Test
    fun testCheckStringNotEmptyWithCodeNegative() {
        val code = UCode.INTERNAL
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkStringNotEmpty("", code, MESSAGE)
        }
        assertEquals(code, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckStringEquals() {
        val string1 = "test"
        val string2 = "test"
        assertEquals(string1, checkStringEquals(string1, string2, errorMessage = MESSAGE))
    }

    @Test
    fun testCheckStringEqualsNegative() {
        val string1 = "test1"
        val string2 = "test2"
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkStringEquals(string1, string2, errorMessage = MESSAGE)
        }
        assertEquals(UCode.INVALID_ARGUMENT, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckStringEqualsWithCode() {
        val string1 = "test"
        val string2 = "test"
        assertEquals(string1, checkStringEquals(string1, string2, UCode.UNKNOWN, MESSAGE))
    }

    @Test
    fun testCheckStringEqualsWithCodeNegative() {
        val string1 = "test1"
        val string2 = "test2"
        val code = UCode.UNKNOWN
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkStringEquals(
                string1,
                string2,
                code,
                MESSAGE
            )
        }
        assertEquals(code, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckNotNull() {
        val reference = Any()
        assertEquals(reference, checkNotNull(reference, errorMessage = MESSAGE))
    }

    @Test
    fun testCheckNotNullNegative() {
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkNotNull(null, errorMessage = MESSAGE)
        }
        assertEquals(UCode.INVALID_ARGUMENT, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckNotNullWithCode() {
        val reference = Any()
        assertEquals(reference, checkNotNull(reference, UCode.UNKNOWN, MESSAGE))
    }

    @Test
    fun testCheckNotNullWithCodeNegative() {
        val code = UCode.UNKNOWN
        val testException: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkNotNull<Any>(null, code, MESSAGE)
        }
        assertEquals(code, testException.code)
        assertEquals(MESSAGE, testException.message)
    }

    @Test
    fun testCheckState() {
        checkState(true, errorMessage = MESSAGE)
    }

    @Test
    fun testCheckStateNegative() {
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkState(false, errorMessage = MESSAGE)
        }
        assertEquals(UCode.FAILED_PRECONDITION, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    @Test
    fun testCheckStateWithCode() {
        checkState(true, UCode.UNKNOWN, MESSAGE)
    }

    @Test
    fun testCheckStateWithCodeNegative() {
        val code = UCode.UNKNOWN
        val exception: UStatusException = assertThrows(
            UStatusException::class.java
        ) {
            checkState(false, code, MESSAGE)
        }
        assertEquals(code, exception.code)
        assertEquals(MESSAGE, exception.message)
    }

    companion object {
        private val STATUS_UNKNOWN: UStatus = UCode.UNKNOWN.buildStatus("Unknown")
        private const val MESSAGE = "Test message"
    }
}
