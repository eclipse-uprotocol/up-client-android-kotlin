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
package org.eclipse.uprotocol.common.util.log

import org.eclipse.uprotocol.common.util.log.Key.forStatus
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer
import org.eclipse.uprotocol.uuid.serializer.LongUuidSerializer
import org.eclipse.uprotocol.v1.UEntity
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UResource
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUID
import org.eclipse.uprotocol.v1.UUri
import kotlin.math.ln
import kotlin.math.pow

/**
 * The formatter utility to be used for logging key-value pairs.
 */
object Formatter {
    /** The separator between a key and a value.  */
    const val SEPARATOR_PAIR = ": "

    /** The separator between key-value pairs.  */
    const val SEPARATOR_PAIRS = ", "

    /**
     * Format a tag with a given name.
     *
     * @param name A name of a tag.
     * @return A formatted tag.
     */
    fun tag(
        name: String,
        group: String? = null
    ): String {
        return if (group.isNullOrEmpty()) name else "$name:$group"
    }

    private fun escapeQuotes(value: String): String = value.replace("\"", "\\\"")

    private fun quoteIfNeeded(value: String): String {
        if (value.isEmpty() || value[0] == '"' || value[0] == '[') {
            return value
        }
        return if (value.indexOf(' ') >= 0) quote(value) else value
    }

    /**
     * Apply quotes and any necessary character escaping.
     *
     * @param value A string to modify.
     * @return A quoted string.
     */
    fun quote(value: String): String = """"${escapeQuotes(value)}""""

    /**
     * Remove all quotes.
     *
     * @param value A string to modify.
     * @return A string without quotes.
     */
    fun removeQuote(value: String): String = value.replace("\"", "")

    /**
     * Apply square brackets as a group.
     *
     * @param value A string to modify.
     * @return A string enclosed in square brackets.
     */
    fun group(value: String): String = "[$value]"

    /**
     * Format grouped key-value pairs.
     *
     * @param args A variable argument list of key-value pairs, like "key1, value1, key2, value2, ...".
     * @return A formatted string containing grouped key-value pairs.
     */
    fun joinGrouped(vararg args: Any?): String = "[${join(*args)}]"

    /**
     * Format key-value pairs.
     *
     * @param args A variable argument list of key-value pairs, like "key1, value1, key2, value2, ...".
     * @return A formatted string containing key-value pairs.
     */
    fun join(vararg args: Any?): String = joinAndAppend(StringBuilder(), *args).toString()

    /**
     * Format key-value pairs and append the result to a given [StringBuilder].
     *
     * @param builder A [StringBuilder] to append the result.
     * @param args    A variable argument list of key-value pairs, like "key1, value1, key2, value2, ...".
     * @return A [StringBuilder] containing formatted key-value pairs.
     */
    fun joinAndAppend(
        builder: StringBuilder,
        vararg args: Any?
    ): StringBuilder {
        var isKey = true
        var skipValue = false
        for (arg in args) {
            val string: String = arg?.toString() ?: ""
            if (isKey && string.isEmpty() || skipValue) {
                isKey = !isKey
                skipValue = !skipValue
                continue
            }
            if (isKey) {
                appendPairsSeparator(builder)
                builder.append(string)
            } else {
                builder.append(SEPARATOR_PAIR)
                builder.append(quoteIfNeeded(string))
            }
            isKey = !isKey
        }
        return builder
    }

    private fun appendPairsSeparator(builder: StringBuilder) {
        if (builder.length > 1) {
            builder.append(SEPARATOR_PAIRS)
        }
    }

    /**
     * Format a status of a method with optional arguments.
     *
     * @param method A name of a method.
     * @param status A [UStatus] to format.
     * @param args   A variable argument list of key-value pairs, like "key1, value1, key2, value2, ...".
     * @return A formatted string containing a `method/status` pair and other given key-value pairs.
     */
    fun status(
        method: String,
        status: UStatus,
        vararg args: Any
    ): String {
        return buildString {
            joinAndAppend(this, forStatus(method), status.stringify(), *args)
        }
    }

    /**
     * Convert a [UUID] into a string.
     *
     * @return A formatted string.
     */
    fun UUID.stringify(): String = LongUuidSerializer.INSTANCE.serialize(this)

    /**
     * Convert a [UEntity] into a string containing arbitrary fields.
     *
     * @return A formatted string.
     */
    fun UEntity.stringify(): String {
        return buildString {
            append(name)
            if (hasVersionMajor()) {
                append('/').append(versionMajor)
            }
        }
    }

    /**
     * Convert a [UResource] into a string containing arbitrary fields.
     *
     * @return A formatted string.
     */
    fun UResource.stringify(): String {
        return buildString {
            append(name)
            if (hasInstance()) {
                append('.').append(instance)
            }
            if (hasMessage()) {
                append('#').append(message)
            }
        }
    }

    /**
     * Convert a [UUri] into a string.
     *
     * @return A formatted string.
     */
    fun UUri.stringify(): String = LongUriSerializer.INSTANCE.serialize(this)

    /**
     * Convert a [UStatus] into a string.
     *
     * @return A formatted string.
     */
    fun UStatus.stringify(): String {
        val hasMessage = hasMessage()
        return joinGrouped(
            Key.CODE,
            code,
            if (hasMessage) Key.MESSAGE else null,
            if (hasMessage) quote(message) else null
        )
    }

    /**
     * Convert a [UMessage] into a string containing arbitrary fields.
     *
     * @return A formatted string.
     */
    fun UMessage.stringify(): String {
        val hasSink = attributes.hasSink()
        return joinGrouped(
            Key.ID,
            attributes.id.stringify(),
            Key.SOURCE,
            attributes.source.stringify(),
            if (hasSink) Key.SINK else null,
            if (hasSink) attributes.sink.stringify() else null,
            Key.TYPE,
            attributes.type
        )
    }

    /**
     * Convert a byte count to a human readable string.
     *
     * @param bytes A byte count.
     * @return A formatted string such as "5.0 MB".
     */
    fun toPrettyMemory(bytes: Long): String {
        val unit: Long = 1024
        if (bytes < unit) {
            return "$bytes B"
        }
        val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
        return String.format(
            "%.1f %sB",
            bytes / unit.toDouble().pow(exp.toDouble()),
            "KMGTPE"[exp - 1]
        )
    }
}
