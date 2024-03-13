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
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.uStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

/**
 * Checks whether a status is successful.
 *
 * @return `true` if it contains [UCode.OK].
 */
fun UStatus.isOk(): Boolean = codeValue == UCode.OK_VALUE

/**
 * Checks whether a status contains a given code.
 *
 * @param code   An `int` value of a [UCode] to check.
 * @return `true` if it contains the same code.
 */
fun UStatus.hasCode(code: Int): Boolean = codeValue == code

/**
 * Checks whether a status contains a given code.
 *
 * @param code   A [UCode] to check.
 * @return `true` if it contains the same code.
 */
fun UStatus.hasCode(code: UCode): Boolean = codeValue == code.number

/**
 * Build a status.
 *
 * @param message A message to set.
 * @return A [UStatus] with the given `code` and `message`.
 */
fun UCode.buildStatus(message: String? = null): UStatus {
    return uStatus {
        code = this@buildStatus
        message?.let { this.message = it }
    }
}

/**
 * Convert an `int` value to an UCode.
 *
 * @return A [UCode] matching the given `value`, otherwise [UCode.UNKNOWN].
 */
fun Int.toUCode(): UCode = UCode.forNumber(this) ?: UCode.UNKNOWN

/**
 * Create a status with [UCode.OK].
 */
val STATUS_OK = UCode.OK.buildStatus()

/**
 * Convert an exception to a status.
 *
 * @return A [UStatus] that includes a mapped code and a message derived from that `exception`.
 */
fun Throwable.toUStatus(): UStatus {
    if (this is UStatusException) {
        return status
    } else if (this is CompletionException || this is ExecutionException) {
        val cause = this.cause
        if (cause is UStatusException) {
            return cause.status
        } else if (cause != null) {
            return cause.toUCode().buildStatus(cause.message)
        }
    }
    return this.toUCode().buildStatus(message)
}

private fun Throwable.toUCode(): UCode {
    return exceptionToCodeMap[this::class.java] ?: UCode.UNKNOWN
}

private val exceptionToCodeMap = hashMapOf<Class<out Throwable>, UCode>(
    SecurityException::class.java to UCode.PERMISSION_DENIED,
    InvalidProtocolBufferException::class.java to UCode.INVALID_ARGUMENT,
    IllegalArgumentException::class.java to UCode.INVALID_ARGUMENT,
    NullPointerException::class.java to UCode.INVALID_ARGUMENT,
    CancellationException::class.java to UCode.CANCELLED,
    IllegalStateException::class.java to UCode.UNAVAILABLE,
    RemoteException::class.java to UCode.UNAVAILABLE,
    UnsupportedOperationException::class.java to UCode.UNIMPLEMENTED,
    InterruptedException::class.java to UCode.CANCELLED,
    TimeoutException::class.java to UCode.DEADLINE_EXCEEDED
)

/**
 * Utility methods for building, converting to uProtocol error model, and validating arguments and states.
 */
object UStatusUtils {
    /**
     * Ensure that a status is successful.
     *
     * @param status A [UStatus] to check.
     * @throws UStatusException containing `status` if it contains a code different from [UCode.OK].
     */
    fun checkStatusOk(status: UStatus) {
        if (!status.isOk()) {
            throw UStatusException(status)
        }
    }

    /**
     * Ensure the truth of an expression involving one or more parameters.
     *
     * @param expression   A boolean expression to check.
     * @param errorCode    A [UCode] to use if the check fails, Default as [UCode.INVALID_ARGUMENT].
     * @param errorMessage A message to use if the check fails.
     * @throws UStatusException containing `errorCode` if `expression` is false.
     */
    fun checkArgument(expression: Boolean, errorCode: UCode = UCode.INVALID_ARGUMENT, errorMessage: String?) {
        if (!expression) {
            throw UStatusException(errorCode, errorMessage)
        }
    }

    /**
     * Ensure that an argument numeric value is positive.
     *
     * @param value        A numeric `int` value to check.
     * @param errorCode    A [UCode] to use if the check fails, Default as [UCode.INVALID_ARGUMENT].
     * @param errorMessage A message to use if the check fails.
     * @return A validated `value`.
     * @throws UStatusException containing `errorCode` if `value` is not positive.
     */
    fun checkArgumentPositive(value: Int, errorCode: UCode = UCode.INVALID_ARGUMENT, errorMessage: String?): Int {
        if (value <= 0) {
            throw UStatusException(errorCode, errorMessage)
        }
        return value
    }

    /**
     * Ensure that an argument numeric value is non-negative.
     *
     * @param value        A numeric `int` value to check.
     * @param errorCode    A [UCode] to use if the check fails, Default as [UCode.INVALID_ARGUMENT].
     * @param errorMessage A message to use if the check fails.
     * @return A validated `value`.
     * @throws UStatusException containing `errorCode` if `value` is negative.
     */
    fun checkArgumentNonNegative(value: Int, errorCode: UCode = UCode.INVALID_ARGUMENT, errorMessage: String?): Int {
        if (value < 0) {
            throw UStatusException(errorCode, errorMessage)
        }
        return value
    }

    /**
     * Ensure that a string is not empty.
     *
     * @param string       A string to check.
     * @param errorCode    A [UCode] to use if the check fails, Default as [UCode.INVALID_ARGUMENT].
     * @param errorMessage A message to use if the check fails.
     * @return A validated `string`.
     * @throws UStatusException containing `errorCode` if `string` is empty or null.
     */
    fun <T : CharSequence> checkStringNotEmpty(
        string: T,
        errorCode: UCode = UCode.INVALID_ARGUMENT,
        errorMessage: String?
    ): T {
        if (string.isEmpty()) {
            throw UStatusException(errorCode, errorMessage)
        }
        return string
    }

    /**
     * Ensure that strings are equal.
     *
     * @param string1      A string.
     * @param string2      A string to be compared with `string1` for equality.
     * @param errorCode    A [UCode] to use if the check fails, Default as [UCode.INVALID_ARGUMENT].
     * @param errorMessage A message to use if the check fails.
     * @return A validated `string1`.
     * @throws UStatusException containing `errorCode` if strings are not equal.
     */
    fun <T : CharSequence> checkStringEquals(
        string1: T,
        string2: T,
        errorCode: UCode = UCode.INVALID_ARGUMENT,
        errorMessage: String?
    ): T {
        if (string1 != string2) {
            throw UStatusException(errorCode, errorMessage)
        }
        return string1
    }

    /**
     * Ensure that an object reference is not null.
     *
     * @param reference    An object reference to check.
     * @param errorCode    A [UCode] to use if the check fails, Default as [UCode.INVALID_ARGUMENT].
     * @param errorMessage A message to use if the check fails.
     * @return A validated `reference`.
     * @throws UStatusException containing `errorCode` if `reference` is null.
     */
    fun <T> checkNotNull(
        reference: T?,
        errorCode: UCode = UCode.INVALID_ARGUMENT,
        errorMessage: String?
    ): T {
        if (reference == null) {
            throw UStatusException(errorCode, errorMessage)
        }
        return reference
    }

    /**
     * Ensure the truth of an expression involving a state.
     *
     * @param expression   A boolean expression to check.
     * @param errorCode    A [UCode] to use if the check fails, Default as [UCode.FAILED_PRECONDITION]
     * @param errorMessage A message to use if the check fails.
     * @throws UStatusException containing `errorCode` if `expression` is false.
     */
    fun checkState(expression: Boolean, errorCode: UCode = UCode.FAILED_PRECONDITION, errorMessage: String?) {
        if (!expression) {
            throw UStatusException(errorCode, errorMessage)
        }
    }
}
