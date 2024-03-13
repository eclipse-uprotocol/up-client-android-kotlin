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
package org.eclipse.uprotocol.core.utwin.v1

import com.google.protobuf.Message
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.eclipse.uprotocol.TestBase
import org.eclipse.uprotocol.UprotocolOptions
import org.eclipse.uprotocol.common.util.STATUS_OK
import org.eclipse.uprotocol.core.utwin.v1.UTwin.newStub
import org.eclipse.uprotocol.rpc.CallOptions
import org.eclipse.uprotocol.rpc.RpcClient
import org.eclipse.uprotocol.v1.UAttributes
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.UUriBatch
import org.eclipse.uprotocol.v1.packToAny
import org.eclipse.uprotocol.v1.uPayload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UTwinTest : TestBase() {
    private lateinit var mClient: RpcClient
    private lateinit var mStub: UTwin.Stub

    @Before
    fun setUp() {
        mClient = mockk<RpcClient>(relaxed = true)
        mStub = newStub(mClient)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun simulateResponse(response: Message) {
        val uriSlot = slot<UUri>()
        every { mClient.invokeMethod(capture(uriSlot), any(), any()) } returns flow {
            val methodUri: UUri = uriSlot.captured
            val responseAttributes: UAttributes =
                buildResponseAttributes(methodUri, testResponseUri, testId)
            emit(buildMessage(uPayload { packToAny(response) }, responseAttributes))
        }
    }

    @Test
    fun testEntity() {
        val options = UTwinProto.getDescriptor().findServiceByName("uTwin").options
        assertEquals(options.getExtension(UprotocolOptions.name), UTwin.SERVICE.name)
        assertEquals(
            (options.getExtension(UprotocolOptions.versionMajor) as Int).toLong(),
            UTwin.SERVICE.versionMajor.toLong()
        )
    }

    @Test
    fun testNewStub() {
        assertNull(mStub.authority)
        assertEquals(CallOptions.DEFAULT, mStub.options)
    }

    @Test
    fun testNewStubWithCallOptions() {
        mStub = newStub(mClient, options = testCallOptions)
        assertNull(mStub.authority)
        assertEquals(testCallOptions, mStub.options)
    }

    @Test
    fun testNewStubWithAuthorityAndCallOptions() {
        mStub = newStub(mClient, authorityRemote, testCallOptions)
        assertEquals(authorityRemote, mStub.authority)
        assertEquals(testCallOptions, mStub.options)
    }

    @Test
    fun testGetLastMessages() = runTest {
        val request = UUriBatch.getDefaultInstance()
        val response = GetLastMessagesResponse.getDefaultInstance()
        simulateResponse(response)
        assertEquals(response, mStub.getLastMessages(request).first())
    }

    @Test
    fun testSetLastMessage() = runTest {
        val request = UMessage.getDefaultInstance()
        val response = UStatus.getDefaultInstance()
        simulateResponse(response)
        assertEquals(response, mStub.setLastMessage(request).first())
    }

    @Test
    fun tesCallWithAuthority() = runTest {
        testNewStubWithAuthorityAndCallOptions()
        val response = STATUS_OK
        val uriSlot = slot<UUri>()
        every { mClient.invokeMethod(capture(uriSlot), any(), any()) } returns flow {
            val methodUri: UUri = uriSlot.captured
            val responseAttributes: UAttributes =
                buildResponseAttributes(methodUri, testResponseUri, testId)
            emit(buildMessage(uPayload { packToAny(response) }, responseAttributes))
        }
        mStub.setLastMessage(UMessage.getDefaultInstance()).first()
        assertEquals(authorityRemote, uriSlot.captured.authority)
        assertEquals(UTwin.SERVICE, uriSlot.captured.entity)
        assertEquals(UTwin.METHOD_SET_LAST_MESSAGE, uriSlot.captured.resource.instance)
    }
}
