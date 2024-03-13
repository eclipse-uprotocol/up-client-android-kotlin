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
package org.eclipse.uprotocol.core.udiscovery.v3

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
import org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.newStub
import org.eclipse.uprotocol.rpc.CallOptions
import org.eclipse.uprotocol.rpc.RpcClient
import org.eclipse.uprotocol.v1.UAttributes
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.packToAny
import org.eclipse.uprotocol.v1.uPayload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UDiscoveryTest : TestBase() {
    private lateinit var mClient: RpcClient
    private lateinit var mStub: UDiscovery.Stub

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
        val options = UDiscoveryProto.getDescriptor().findServiceByName("uDiscovery").options
        assertEquals(options.getExtension(UprotocolOptions.name), UDiscovery.SERVICE.name)
        assertEquals(
            (options.getExtension(UprotocolOptions.versionMajor) as Int).toLong(),
            UDiscovery.SERVICE.versionMajor.toLong()
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
    fun testLookupUri() = runTest {
        val request = UUri.getDefaultInstance()
        val response = LookupUriResponse.getDefaultInstance()
        simulateResponse(response)
        assertEquals(response, mStub.lookupUri(request).first())
    }

    @Test
    fun testUpdateNode() = runTest {
        val request = UpdateNodeRequest.getDefaultInstance()
        val response = UStatus.getDefaultInstance()
        simulateResponse(response)
        assertEquals(response, mStub.updateNode(request).first())
    }

    @Test
    fun testFindNodes() = runTest {
        val request = FindNodesRequest.getDefaultInstance()
        val response = FindNodesResponse.getDefaultInstance()
        simulateResponse(response)
        assertEquals(response, mStub.findNodes(request).first())
    }

    @Test
    fun testFindNodeProperties() = runTest {
        val request = FindNodePropertiesRequest.getDefaultInstance()
        val response = FindNodePropertiesResponse.getDefaultInstance()
        simulateResponse(response)
        assertEquals(
            response,
            mStub.findNodeProperties(request).first()
        )
    }

    @Test
    fun testDeleteNodes() = runTest {
        val request = DeleteNodesRequest.getDefaultInstance()
        val response = UStatus.getDefaultInstance()
        simulateResponse(response)
        assertEquals(response, mStub.deleteNodes(request).first())
    }

    @Test
    fun testAddNodes() = runTest {
        val request = AddNodesRequest.getDefaultInstance()
        val response = UStatus.getDefaultInstance()
        simulateResponse(response)
        assertEquals(response, mStub.addNodes(request).first())
    }

    @Test
    fun testUnregisterForNotifications() = runTest {
        val request = NotificationsRequest.getDefaultInstance()
        val response = UStatus.getDefaultInstance()
        simulateResponse(response)
        assertEquals(
            response,
            mStub.unregisterForNotifications(request).first()
        )
    }

    @Test
    fun testResolveUri() = runTest {
        val request = ResolveUriRequest.getDefaultInstance()
        val response = ResolveUriResponse.getDefaultInstance()
        simulateResponse(response)
        assertEquals(response, mStub.resolveUri(request).first())
    }

    @Test
    fun testRegisterForNotifications() = runTest {
        val request = NotificationsRequest.getDefaultInstance()
        val response = UStatus.getDefaultInstance()
        simulateResponse(response)
        assertEquals(
            response,
            mStub.registerForNotifications(request).first()
        )
    }

    @Test
    fun testUpdateProperty() = runTest {
        val request = UpdatePropertyRequest.getDefaultInstance()
        val response = UStatus.getDefaultInstance()
        simulateResponse(response)
        assertEquals(response, mStub.updateProperty(request).first())
    }

    @Test
    fun tesCallWithAuthority() = runTest {
        val uriSlot = slot<UUri>()
        val response = LookupUriResponse.getDefaultInstance()
        mStub = newStub(mClient, authorityRemote, testCallOptions)
        every { mClient.invokeMethod(capture(uriSlot), any(), any()) } returns flow {
            val methodUri: UUri = uriSlot.captured
            val responseAttributes: UAttributes =
                buildResponseAttributes(methodUri, testResponseUri, testId)
            emit(buildMessage(uPayload { packToAny(response) }, responseAttributes))
        }
        mStub.lookupUri(UUri.getDefaultInstance()).first()
        assertEquals(authorityRemote, uriSlot.captured.authority)
        assertEquals(UDiscovery.SERVICE, uriSlot.captured.entity)
        assertEquals(UDiscovery.METHOD_LOOKUP_URI, uriSlot.captured.resource.instance)
    }
}
