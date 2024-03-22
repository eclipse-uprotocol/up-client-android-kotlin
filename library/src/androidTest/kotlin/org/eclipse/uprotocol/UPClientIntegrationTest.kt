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
package org.eclipse.uprotocol

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.protobuf.Int32Value
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.uprotocol.common.UStatusException
import org.eclipse.uprotocol.common.util.STATUS_OK
import org.eclipse.uprotocol.common.util.toUStatus
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse
import org.eclipse.uprotocol.core.usubscription.v3.USubscription
import org.eclipse.uprotocol.core.usubscription.v3.createTopicRequest
import org.eclipse.uprotocol.core.usubscription.v3.subscriberInfo
import org.eclipse.uprotocol.core.usubscription.v3.subscriptionRequest
import org.eclipse.uprotocol.core.usubscription.v3.unsubscribeRequest
import org.eclipse.uprotocol.rpc.CallOptions
import org.eclipse.uprotocol.rpc.CallOptions.Companion.callOptions
import org.eclipse.uprotocol.rpc.URpcListener
import org.eclipse.uprotocol.transport.UListener
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UMessageType
import org.eclipse.uprotocol.v1.UPayload
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.packToAny
import org.eclipse.uprotocol.v1.uPayload
import org.eclipse.uprotocol.v1.uUri
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
@FlowPreview
class UPClientIntegrationTest : TestBase() {

    private val testMessage: UMessage = buildMessage(testPayload, buildPublishAttributes(RESOURCE_URI))
    private val testNotificationMessage: UMessage =
        buildMessage(testPayload, buildNotificationAttributes(RESOURCE_URI, CLIENT_URI))

    private val testReqPayload: UPayload = uPayload { packToAny(Int32Value.newBuilder().setValue(1).build()) }
    private val testResPayload: UPayload = uPayload { packToAny(STATUS_OK) }

    private val sListener: UListener = mockk(relaxed = true)
    private val sListener2: UListener = mockk(relaxed = true)
    private val sRequestListener: URpcListener = mockk(relaxed = true)
    private val sRequestListener2: URpcListener = mockk(relaxed = true)

    @After
    fun tearDownTest() {
        sClient.unregisterListener(sListener)
        sClient.unregisterListener(sListener2)
        sClient.unregisterRpcListener(sRequestListener)
        sClient.unregisterRpcListener(sRequestListener2)
        unmockkAll()
    }

    @Test
    fun testConnect() {
        verify(timeout = DELAY_MS, exactly = 1) { sServiceLifecycleListener.onLifecycleChanged(sClient, true) }
    }

    @Test
    fun testConnectDuplicated() {
        runBlocking {
            val client = UPClient.create(sContext, dispatcher = TEST_DISPATCHER, listener = sServiceLifecycleListener)
            assertStatus(UCode.OK, getOrThrow(CONNECTION_TIMEOUT_MS) { client.connect() })
            assertStatus(UCode.OK, getOrThrow(CONNECTION_TIMEOUT_MS) { client.connect() })
            verify(exactly = 1, timeout = DELAY_MS) { sServiceLifecycleListener.onLifecycleChanged(client, true) }
            assertTrue(client.isConnected())
            client.disconnect()
        }
    }

    @Test
    fun testConnectInDifferentThreads() {
        runBlocking {
            val client = UPClient.create(sContext, dispatcher = TEST_DISPATCHER, listener = sServiceLifecycleListener)
            launch {
                assertStatus(UCode.OK, getOrThrow(CONNECTION_TIMEOUT_MS) { client.connect() })
            }
            launch {
                assertStatus(UCode.OK, getOrThrow(CONNECTION_TIMEOUT_MS) { client.connect() })
            }
            delay(DELAY_MS)
            verify(exactly = 1, timeout = DELAY_MS) { sServiceLifecycleListener.onLifecycleChanged(client, true) }
        }
    }

    @Test
    fun testDisconnect() = runBlocking {
        val client = UPClient.create(sContext, dispatcher = TEST_DISPATCHER, listener = sServiceLifecycleListener)
        connect(client)
        assertStatus(UCode.OK, getOrThrow { client.disconnect() })
        verify(exactly = 1, timeout = DELAY_MS) { sServiceLifecycleListener.onLifecycleChanged(client, false) }
        assertTrue(client.isDisconnected())
    }

    @Test
    fun testDisconnectNotConnected() = runBlocking {
        val client = UPClient.create(sContext, dispatcher = TEST_DISPATCHER, listener = sServiceLifecycleListener)
        assertStatus(UCode.OK, getOrThrow { client.disconnect() })
        verify(exactly = 0, timeout = DELAY_MS) { sServiceLifecycleListener.onLifecycleChanged(client, false) }
        assertTrue(client.isDisconnected())
    }

    @Test
    fun testDisconnectWhileConnecting() {
        runBlocking {
            val client = UPClient.create(sContext, dispatcher = TEST_DISPATCHER, listener = sServiceLifecycleListener)
            val result = async { client.connect() }
            launch {
                delay(5) // delay for wait for connection
                assertStatus(UCode.OK, getOrThrow { client.disconnect() })
                assertTrue(client.isDisconnected())
            }
            assertTrue(
                setOf(UCode.OK, UCode.CANCELLED)
                    .contains(result.await().code)
            )
        }
    }

    @Test
    fun testGetEntity() {
        assertEquals(CLIENT, sClient.entity)
    }

    @Test
    fun testSubscription() = runBlocking {
        createTopic(RESOURCE2_URI)
        subscribe(RESOURCE2_URI)
        unsubscribe(RESOURCE2_URI)
    }

    @Test
    fun testSend() = runBlocking {
        createTopic(RESOURCE_URI)
        assertStatus(UCode.OK, sClient.send(testMessage))
    }

    @Test
    fun testSendNotificationMessage() {
        assertStatus(UCode.OK, sClient.send(testNotificationMessage))
    }

    @Test
    fun testRegisterListener() {
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE_URI, sListener))
    }

    @Test
    fun testRegisterListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerListener(UUri.getDefaultInstance(), sListener))
    }

    @Test
    fun testRegisterListenerDifferentTopics() {
        testRegisterListener()
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE2_URI, sListener))
    }

    @Test
    fun testRegisterListenerSame() {
        testRegisterListener()
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE_URI, sListener))
    }

    @Test
    fun testRegisterListenerNotFirst() {
        testRegisterListener()
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE_URI, sListener2))
    }

    @Test
    fun testUnregisterListener() {
        testRegisterListener()
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener))
    }

    @Test
    fun testUnregisterListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(UUri.getDefaultInstance(), sListener))
    }

    @Test
    fun testUnregisterListenerSame() {
        testUnregisterListener()
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener))
    }

    @Test
    fun testUnregisterListenerNotRegistered() {
        testRegisterListener()
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener2))
    }

    @Test
    fun testUnregisterListenerNotLast() {
        testRegisterListenerNotFirst()
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener))
    }

    @Test
    fun testUnregisterListenerLast() {
        testUnregisterListenerNotLast()
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener2))
    }

    @Test
    fun testUnregisterListenerFromAllTopics() {
        testRegisterListenerDifferentTopics()
        assertStatus(UCode.OK, sClient.unregisterListener(sListener))
    }

    @Test
    fun testOnReceiveGenericMessage() = runBlocking {
        testSend()
        subscribe(RESOURCE_URI)
        testRegisterListenerNotFirst()
        verify(exactly = 1, timeout = DELAY_MS) { sListener.onReceive(testMessage) }
        verify(atLeast = 1, timeout = DELAY_MS) { sListener2.onReceive(testMessage) }
    }

    @Test
    fun testOnReceiveGenericMessageNotRegistered() = runBlocking {
        testSend()
        subscribe(RESOURCE_URI)
        testRegisterListener()
        verify(exactly = 1, timeout = DELAY_MS) { sListener.onReceive(testMessage) }
        verify(exactly = 0, timeout = DELAY_MS) { sListener2.onReceive(testMessage) }
    }

    @Test
    fun testOnReceiveNotificationMessage() {
        testRegisterListener()
        testSendNotificationMessage()
        verify(exactly = 1, timeout = DELAY_MS) { sListener.onReceive(testNotificationMessage) }
    }

    @Test
    fun testRegisterRpcListener() {
        assertEquals(STATUS_OK, sClient.registerRpcListener(METHOD_URI, sRequestListener))
    }

    @Test
    fun testRegisterRpcListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerRpcListener(UUri.getDefaultInstance(), sRequestListener))
    }

    @Test
    fun testRegisterRpcListenerDifferentMethods() {
        assertStatus(UCode.OK, sClient.registerRpcListener(METHOD_URI, sRequestListener))
        assertStatus(UCode.OK, sClient.registerRpcListener(METHOD2_URI, sRequestListener))
    }

    @Test
    fun testRegisterRpcListenerSame() {
        testRegisterRpcListener()
        assertStatus(UCode.OK, sClient.registerRpcListener(METHOD_URI, sRequestListener))
    }

    @Test
    fun testRegisterRpcListenerNotFirst() {
        testRegisterRpcListener()
        assertStatus(UCode.ALREADY_EXISTS, sClient.registerRpcListener(METHOD_URI, sRequestListener2))
    }

    @Test
    fun testUnregisterRpcListener() {
        testRegisterRpcListener()
        assertStatus(UCode.OK, sClient.unregisterRpcListener(METHOD_URI, sRequestListener))
    }

    @Test
    fun testUnregisterRpcListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterRpcListener(UUri.getDefaultInstance(), sRequestListener))
    }

    @Test
    fun testUnregisterRpcListenerSame() {
        testUnregisterRpcListener()
        assertStatus(UCode.OK, sClient.unregisterRpcListener(METHOD_URI, sRequestListener))
    }

    @Test
    fun testUnregisterRpcListenerNotRegistered() {
        testRegisterRpcListener()
        assertStatus(UCode.OK, sClient.unregisterRpcListener(METHOD_URI, sRequestListener2))
    }

    @Test
    fun testUnregisterRpcListenerFromAllMethods() {
        testRegisterRpcListenerDifferentMethods()
        assertStatus(UCode.OK, sClient.unregisterRpcListener(sRequestListener))
    }

    @Test
    fun testUnregisterRpcListenerFromAllMethodsNotRegistered() {
        testRegisterRpcListenerDifferentMethods()
        assertStatus(UCode.OK, sClient.unregisterRpcListener(sRequestListener2))
    }

    @Test
    fun testInvokeMethod() = runBlocking {
        val requestSlot = slot<UMessage>()
        val responseDeferredSlot = slot<CompletableDeferred<UPayload>>()
        every {
            sRequestListener.onReceive(capture(requestSlot), capture(responseDeferredSlot))
        } just runs
        sClient.registerRpcListener(METHOD_URI, sRequestListener)
        val responseMessageJob = async {
            sClient.invokeMethod(METHOD_URI, testReqPayload, CALL_OPTIONS).first()
        }
        delay(DELAY_MS)
        val requestMessage: UMessage = requestSlot.captured
        assertEquals(RESPONSE_URI.entity, requestMessage.attributes.source.entity)
        assertEquals(testReqPayload, requestMessage.payload)
        assertEquals(METHOD_URI, requestMessage.attributes.sink)
        assertEquals(CALL_OPTIONS.timeout, requestMessage.attributes.ttl)
        assertEquals(UMessageType.UMESSAGE_TYPE_REQUEST, requestMessage.attributes.type)
        responseDeferredSlot.captured.complete(testResPayload)
        val responseMessage = responseMessageJob.await()
        assertEquals(METHOD_URI, responseMessage.attributes.source)
        assertEquals(testResPayload, responseMessage.payload)
        assertEquals(RESPONSE_URI, responseMessage.attributes.sink)
        assertEquals(UMessageType.UMESSAGE_TYPE_RESPONSE, responseMessage.attributes.type)
        assertEquals(requestMessage.attributes.id, responseMessage.attributes.reqid)
    }

    @Test
    fun testInvokeMethodWithInvalidArgument() {
        assertStatus(
            UCode.INVALID_ARGUMENT,
            assertThrows(
                UStatusException::class.java
            ) {
                runBlocking {
                    sClient.invokeMethod(
                        UUri.getDefaultInstance(),
                        testPayload,
                        CALL_OPTIONS
                    ).first()
                }
            }.toUStatus()

        )
    }

    @Test
    fun testInvokeMethodCompletedWithCommStatus() = runBlocking {
        val responseDeferredSlot = slot<CompletableDeferred<UPayload>>()
        every {
            sRequestListener.onReceive(any(), capture(responseDeferredSlot))
        } just runs
        sClient.registerRpcListener(METHOD_URI, sRequestListener)
        val responseMessageJob = launch {
            try {
                sClient.invokeMethod(METHOD_URI, testReqPayload, CALL_OPTIONS).first()
                fail("Expected UStatusException")
            } catch (e: UStatusException) {
                assertStatus(UCode.CANCELLED, e.toUStatus())
            }
        }
        delay(DELAY_MS)
        responseDeferredSlot.captured.completeExceptionally(UStatusException(UCode.CANCELLED, "Cancelled"))
        responseMessageJob.join()
    }

    @Test
    fun testInvokeMethodNoServer() {
        val status = assertThrows(UStatusException::class.java) {
            runBlocking {
                sClient.invokeMethod(
                    METHOD_URI,
                    testPayload,
                    CALL_OPTIONS
                ).first()
            }
        }.toUStatus()
        assertStatus(UCode.UNAVAILABLE, status)
    }

    companion object {
        private lateinit var sContext: Context
        private lateinit var sClient: UPClient
        private lateinit var sSubscriptionStub: USubscription.Stub
        private val sServiceLifecycleListener: UPClient.ServiceLifecycleListener = mockk(relaxed = true)
        private val TEST_DISPATCHER = Dispatchers.IO
        private val CALL_OPTIONS: CallOptions = callOptions { timeout = TTL }

        @BeforeClass
        @JvmStatic
        fun setUp() {
            sContext = InstrumentationRegistry.getInstrumentation().targetContext
            sClient = UPClient.create(sContext, dispatcher = TEST_DISPATCHER, listener = sServiceLifecycleListener)
            sSubscriptionStub = USubscription.newStub(sClient, options = CALL_OPTIONS)
            runBlocking { connect(sClient) }
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            runBlocking {
                unsubscribe(RESOURCE_URI)
                disconnect(sClient)
            }
        }

        private suspend fun createTopic(topicUri: UUri) {
            val flow: Flow<UStatus> = sSubscriptionStub.createTopic(
                createTopicRequest {
                    topic = topicUri
                }
            )
            assertStatus(UCode.OK, flow.getOrThrow(CALL_OPTIONS.timeout))
        }

        private suspend fun subscribe(topicUri: UUri) {
            val flow: Flow<SubscriptionResponse> = sSubscriptionStub.subscribe(
                subscriptionRequest {
                    topic = topicUri
                    subscriber = subscriberInfo {
                        uri = uUri {
                            entity = sClient.entity
                        }
                    }
                }
            )
            assertEquals(UCode.OK, flow.getOrThrow(CALL_OPTIONS.timeout).status.code)
        }

        private suspend fun unsubscribe(topicUri: UUri) {
            val flow: Flow<UStatus> = sSubscriptionStub.unsubscribe(
                unsubscribeRequest {
                    topic = topicUri
                    subscriber = subscriberInfo {
                        uri = uUri {
                            entity = sClient.entity
                        }
                    }
                }
            )
            assertStatus(UCode.OK, flow.getOrThrow(CALL_OPTIONS.timeout))
        }
    }
}
