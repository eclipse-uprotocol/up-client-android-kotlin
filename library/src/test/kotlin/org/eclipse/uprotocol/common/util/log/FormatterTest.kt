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

import org.eclipse.uprotocol.TestBase
import org.eclipse.uprotocol.common.util.STATUS_OK
import org.eclipse.uprotocol.common.util.buildStatus
import org.eclipse.uprotocol.common.util.log.Formatter.SEPARATOR_PAIR
import org.eclipse.uprotocol.common.util.log.Formatter.SEPARATOR_PAIRS
import org.eclipse.uprotocol.common.util.log.Formatter.group
import org.eclipse.uprotocol.common.util.log.Formatter.join
import org.eclipse.uprotocol.common.util.log.Formatter.joinAndAppend
import org.eclipse.uprotocol.common.util.log.Formatter.joinGrouped
import org.eclipse.uprotocol.common.util.log.Formatter.quote
import org.eclipse.uprotocol.common.util.log.Formatter.removeQuote
import org.eclipse.uprotocol.common.util.log.Formatter.status
import org.eclipse.uprotocol.common.util.log.Formatter.stringify
import org.eclipse.uprotocol.common.util.log.Formatter.tag
import org.eclipse.uprotocol.common.util.log.Formatter.toPrettyMemory
import org.eclipse.uprotocol.uuid.serializer.LongUuidSerializer
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.copy
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatterTest : TestBase() {
    @Test
    fun testTag() {
        assertEquals("$NAME:$GROUP", tag(NAME, GROUP))
    }

    @Test
    fun testConstructorWithEmptyGroup() {
        assertEquals(NAME, tag(NAME))
        assertEquals(NAME, tag(NAME, null))
        assertEquals(NAME, tag(NAME, ""))
    }

    @Test
    fun testQuote() {
        assertEquals("\"test\"", quote("test"))
    }

    @Test
    fun testQuoteEscaped() {
        assertEquals("\"with \\\" inside\"", quote("with \" inside"))
    }

    @Test
    fun testRemoveQuotes() {
        assertEquals("with inside", removeQuote("with \"inside"))
    }

    @Test
    fun testGroup() {
        assertEquals("[test]", group("test"))
    }

    @Test
    fun testGroupEmpty() {
        assertEquals("[]", group(""))
    }

    @Test
    fun testJoinGrouped() {
        assertEquals("[$MESSAGE1]", joinGrouped(KEY1, VALUE1))
    }

    @Test
    fun testJoin() {
        assertEquals(MESSAGE2, join(KEY1, VALUE1, KEY2, VALUE2))
    }

    @Test
    fun testJoinEmptyKey() {
        assertEquals(MESSAGE1, join(KEY1, VALUE1, null, VALUE2))
        assertEquals(MESSAGE1, join(KEY1, VALUE1, "", VALUE2))
    }

    @Test
    fun testJoinNullValue() {
        assertEquals("key1: ", join(KEY1, null))
    }

    @Test
    fun testJoinAndAppend() {
        val builder = StringBuilder()
        joinAndAppend(builder, KEY1, VALUE1)
        joinAndAppend(builder, KEY2, VALUE2)
        assertEquals(MESSAGE2, builder.toString())
    }

    @Test
    fun testJoinAndAppendWithNull() {
        val builder = StringBuilder()
        joinAndAppend(builder, null, null)
        assertEquals("", builder.toString())
    }

    @Test
    fun testJoinAndAppendAutoQuotes() {
        val builder = StringBuilder()
        joinAndAppend(builder, KEY1, "Value with spaces")
        joinAndAppend(builder, KEY2, "[\"Quotes in group\"]")
        assertEquals(
            "key1: \"Value with spaces\", key2: [\"Quotes in group\"]",
            builder.toString()
        )
    }

    @Test
    fun testStatus() {
        assertEquals(MESSAGE_STATUS_OK, status(METHOD, STATUS_OK, KEY1, VALUE1))
    }

    @Test
    fun testStringifyUUID() {
        assertEquals(testIDString, testId.stringify())
    }

    @Test
    fun testStringifyUEntity() {
        assertEquals("test.client/1", testClient.stringify())
    }

    @Test
    fun testStringifyUEntityWithoutVersionMajor() {
        assertEquals(
            "test.client",
            testClient.copy { clearVersionMajor() }.stringify()
        )
    }

    @Test
    fun testStringifyUResource() {
        assertEquals("resource.main#State", testResource.stringify())
    }

    @Test
    fun testStringifyUResourceWithoutInstance() {
        assertEquals(
            "resource#State",
            testResource.copy { clearInstance() }.stringify()
        )
    }

    @Test
    fun testStringifyUResourceWithoutMessage() {
        assertEquals(
            "resource.main",
            testResource.copy { clearMessage() }.stringify()
        )
    }

    @Test
    fun testStringifyUUri() {
        assertEquals(
            "/test.service/1/resource.main#State",
            testResourceUri.stringify()
        )
    }

    @Test
    fun testStringifyUStatus() {
        val status: UStatus = UCode.UNKNOWN.buildStatus("Unknown failure")
        assertEquals("[code: UNKNOWN, message: \"Unknown failure\"]", status.stringify())
    }

    @Test
    fun testStringifyUStatusWithoutMessage() {
        val status: UStatus = UCode.OK.buildStatus()
        assertEquals("[code: OK]", status.stringify())
    }

    @Test
    fun testStringifyUMessage() {
        val message: UMessage = buildMessage(testPayload, testAttributes)
        assertEquals(
            "[id: $testIDString, " +
                "source: /test.service/1/rpc.method, sink: /test.client/1/rpc.response, " +
                "type: UMESSAGE_TYPE_RESPONSE]",
            message.stringify()
        )
    }

    @Test
    fun testStringifyUMessageWithoutSink() {
        val message: UMessage =
            buildMessage(testPayload, testAttributes.copy { clearSink() })
        assertEquals(
            "[id: $testIDString, source: /test.service/1/rpc.method, type: UMESSAGE_TYPE_RESPONSE]",
            message.stringify()
        )
    }

    @Test
    fun testToPrettyMemory() {
        assertEquals("17 B", toPrettyMemory(17))
        assertEquals("1.0 KB", toPrettyMemory(1024))
        assertEquals("1.0 MB", toPrettyMemory(1048576))
        assertEquals("1.0 GB", toPrettyMemory(1073741824))
    }

    private val testIDString: String = LongUuidSerializer.INSTANCE.serialize(testId)

    companion object {
        private const val NAME = "name"
        private const val GROUP = "group"
        private const val KEY1 = "key1"
        private const val KEY2 = "key2"
        private const val VALUE1 = "value1"
        private const val VALUE2 = "value2"
        private const val MESSAGE1 = KEY1 + SEPARATOR_PAIR + VALUE1
        private const val MESSAGE2 = MESSAGE1 + SEPARATOR_PAIRS + KEY2 + SEPARATOR_PAIR + VALUE2
        private const val METHOD = "method"
        private val MESSAGE_STATUS_OK = "status.${METHOD}${SEPARATOR_PAIR}[" +
            "${Key.CODE}${SEPARATOR_PAIR}${STATUS_OK.code}]" +
            "${SEPARATOR_PAIRS}$MESSAGE1"
    }
}
