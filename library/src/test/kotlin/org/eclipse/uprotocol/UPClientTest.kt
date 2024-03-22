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
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.util.Log
import com.google.protobuf.Int32Value
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.eclipse.uprotocol.UPClient.Companion.META_DATA_ENTITY_ID
import org.eclipse.uprotocol.UPClient.Companion.META_DATA_ENTITY_NAME
import org.eclipse.uprotocol.UPClient.Companion.META_DATA_ENTITY_VERSION
import org.eclipse.uprotocol.UPClient.Companion.PERMISSION_ACCESS_UBUS
import org.eclipse.uprotocol.UPClient.ServiceLifecycleListener
import org.eclipse.uprotocol.client.BuildConfig
import org.eclipse.uprotocol.common.UStatusException
import org.eclipse.uprotocol.common.util.STATUS_OK
import org.eclipse.uprotocol.common.util.buildStatus
import org.eclipse.uprotocol.common.util.toUStatus
import org.eclipse.uprotocol.core.ubus.ConnectionCallback
import org.eclipse.uprotocol.core.ubus.UBusManager
import org.eclipse.uprotocol.rpc.URpcListener
import org.eclipse.uprotocol.transport.UListener
import org.eclipse.uprotocol.transport.validate.UAttributesValidator
import org.eclipse.uprotocol.uuid.factory.UUIDV8
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UEntity
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UMessageType
import org.eclipse.uprotocol.v1.UPayload
import org.eclipse.uprotocol.v1.UUID
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.copy
import org.eclipse.uprotocol.v1.packToAny
import org.eclipse.uprotocol.v1.uEntity
import org.eclipse.uprotocol.v1.uPayload
import org.eclipse.uprotocol.validation.ValidationResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

@ExperimentalCoroutinesApi
class UPClientTest : TestBase() {
    private val mContextWrapper: ContextWrapper = mockk(relaxed = true)
    private val mContext: Context = mockk(relaxed = true)
    private val mPackageName: String = "org.eclipse.uprotocol.testPackage"
    private val mPackageInfo: PackageInfo = mockk(relaxed = true)
    private val mServiceLifecycleListener: ServiceLifecycleListener = mockk(relaxed = true)
    private var mListener: UListener = mockk(relaxed = true)
    private var mListener2: UListener = mockk(relaxed = true)
    private var mRequestListener: URpcListener = mockk(relaxed = true)
    private var mRequestListener2: URpcListener = mockk(relaxed = true)
    private var mManager: UBusManager = mockk(relaxed = true)
    private lateinit var mClient: UPClient
    private val testMessage: UMessage = buildMessage(testPayload, buildPublishAttributes(testResourceUri))
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        mockLogger()
        mockkObject(UBusManager)
        every { UBusManager.create(any(), any(), any(), any(), any()) } returns mManager
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockLogger() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.isLoggable(any(), any()) } returns true
    }

    @Test
    fun testConstants() {
        assertEquals("uprotocol.permission.ACCESS_UBUS", PERMISSION_ACCESS_UBUS)
        assertEquals("uprotocol.entity.name", META_DATA_ENTITY_NAME)
        assertEquals("uprotocol.entity.version", META_DATA_ENTITY_VERSION)
    }

    private fun mockPackageInfo(entity: UEntity?, context: Context = mContext) {
        every { context.packageName } returns mPackageName
        every { context.packageManager.getPackageInfo(any<String>(), any()) } returns mPackageInfo
        val applicationInfo: ApplicationInfo = mockk(relaxed = true)
        mPackageInfo.applicationInfo = applicationInfo
        mPackageInfo.services = emptyArray<ServiceInfo>()
        val bundle = mockk<Bundle>()
        applicationInfo.metaData = bundle
        every { bundle.getString(META_DATA_ENTITY_NAME) } returns entity?.name
        every { bundle.getInt(META_DATA_ENTITY_VERSION) } returns (entity?.versionMajor ?: 0)
        every { bundle.getInt(META_DATA_ENTITY_ID) } returns (entity?.id ?: 0)
    }

    @Test
    fun testCreate() {
        mockPackageInfo(testService)
        assertNotNull(UPClient.create(mContext, testService, testDispatcher, mServiceLifecycleListener))
    }

    @Test
    fun testCreateWithoutEntity() {
        mockPackageInfo(testService)
        assertNotNull(UPClient.create(mContext, dispatcher = testDispatcher, listener = mServiceLifecycleListener))
    }

    @Test
    fun testCreateWithEntityId() {
        val entity = testClient.copy {
            id = 100
        }
        mockPackageInfo(entity)
        assertNotNull(UPClient.create(mContext, entity, testDispatcher, mServiceLifecycleListener))
    }

    @Test
    fun testCreateWithDispatcher() {
        mockPackageInfo(testService)
        val client = UPClient.create(mContext, listener = mServiceLifecycleListener)
        assertNotNull(client)
        assertNotNull(client.getPrivateObject("dispatcher"))
    }

    @Test
    fun testCreateWithBadContextWrapper() {
        every { mContextWrapper.baseContext } returns null
        assertThrows(NullPointerException::class.java) {
            UPClient.create(mContextWrapper, testService, testDispatcher, mServiceLifecycleListener)
        }
    }

    @Test
    fun testCreateWithContextWrapper() {
        mockPackageInfo(testService, mContextWrapper)
        every { mContextWrapper.baseContext } returns mockk()
        assertNotNull(UPClient.create(mContextWrapper, testService, testDispatcher, mServiceLifecycleListener))
    }

    @Test
    fun testCreatePackageManagerNotAvailable() {
        assertThrows(SecurityException::class.java) {
            UPClient.create(mContext, dispatcher = testDispatcher, listener = mServiceLifecycleListener)
        }
    }

    @Test
    fun testCreatePackageNotFound() {
        every { mContext.packageManager.getPackageInfo(any<String>(), any()) } throws PackageManager.NameNotFoundException()
        assertThrows(SecurityException::class.java) {
            UPClient.create(mContext, dispatcher = testDispatcher, listener = mServiceLifecycleListener)
        }
    }

    @Test
    fun testCreateEntityNotDeclared() {
        mockPackageInfo(null)
        assertThrows(SecurityException::class.java) {
            UPClient.create(mContext, dispatcher = testDispatcher, listener = mServiceLifecycleListener)
        }
    }

    @Test
    fun testCreateEntityNameNotDeclared() {
        mockPackageInfo(
            uEntity {
                versionMajor = 1
            }
        )
        assertThrows(SecurityException::class.java) {
            UPClient.create(mContext, dispatcher = testDispatcher, listener = mServiceLifecycleListener)
        }
    }

    @Test
    fun testCreateEntityVersionNotDeclared() {
        mockPackageInfo(
            uEntity {
                name = testClient.name
            }
        )
        assertThrows(SecurityException::class.java) {
            UPClient.create(mContext, dispatcher = testDispatcher, listener = mServiceLifecycleListener)
        }
    }

    @Test
    fun testCreateVerboseVersionLogged() {
        every { Log.isLoggable(any(), Log.VERBOSE) } returns true
        mockPackageInfo(testService)
        val client = UPClient.create(mContext, testService, testDispatcher, mServiceLifecycleListener)
        assertNotNull(client)
        val tag: String = client.getLogTag()
        verify {
            Log.v(
                tag,
                match { log ->
                    log.contains(BuildConfig.LIBRARY_PACKAGE_NAME) && log.contains(BuildConfig.VERSION_NAME)
                }
            )
        }
    }

    @Test
    fun testConnect() = testScope.runTest {
        coEvery { mManager.connect() } returns STATUS_OK
        val client = createUPClient()
        assertEquals(STATUS_OK, client.connect())
    }

    @Test
    fun testDisconnect() = testScope.runTest {
        coEvery { mManager.disconnect() } returns STATUS_OK
        val client = createUPClient()
        assertEquals(STATUS_OK, client.disconnect())
    }

    @Test
    fun testIsDisconnected() {
        val client = createUPClient()
        every { mManager.isDisconnected() } returns false
        assertFalse(client.isDisconnected())
        every { mManager.isDisconnected() } returns true
        assertTrue(client.isDisconnected())
    }

    @Test
    fun testIsConnecting() {
        val client = createUPClient()
        every { mManager.isConnecting() } returns false
        assertFalse(client.isConnecting())
        every { mManager.isConnecting() } returns true
        assertTrue(client.isConnecting())
    }

    @Test
    fun testIsConnected() {
        val client = createUPClient()
        every { mManager.isConnected() } returns false
        assertFalse(client.isConnected())
        every { mManager.isConnected() } returns true
        assertTrue(client.isConnected())
    }

    @Test
    fun testOnConnected() {
        val client = createUPClient()
        val connectionCallback: ConnectionCallback = client.getPrivateObject("mConnectionCallback")
        every { mServiceLifecycleListener.onLifecycleChanged(client, any()) } returns Unit
        connectionCallback.onConnected()
        verify(exactly = 1) { mServiceLifecycleListener.onLifecycleChanged(client, true) }
    }

    @Test
    fun testOnDisconnected() {
        val client = createUPClient()
        val connectionCallback: ConnectionCallback = client.getPrivateObject("mConnectionCallback")
        every { mServiceLifecycleListener.onLifecycleChanged(client, any()) } returns Unit
        connectionCallback.onDisconnected()
        verify(exactly = 1) { mServiceLifecycleListener.onLifecycleChanged(client, false) }
    }

    @Test
    fun testOnConnectionInterrupted() {
        val client = createUPClient()
        val connectionCallback: ConnectionCallback = client.getPrivateObject("mConnectionCallback")
        every { mServiceLifecycleListener.onLifecycleChanged(client, any()) } returns Unit
        connectionCallback.onConnectionInterrupted()
        verify(exactly = 1) { mServiceLifecycleListener.onLifecycleChanged(client, false) }
    }

    @Test
    fun testGetEntity() {
        val client = createUPClient()
        assertEquals(testClient, client.entity)
    }

    @Test
    fun testGetUri() {
        val client = createUPClient()
        assertEquals(testClient, client.uri.entity)
    }

    @Test
    fun testSend() {
        val client = createUPClient()
        every { mManager.send(testMessage) } returns STATUS_OK
        assertStatus(UCode.OK, client.send(testMessage))
    }

    @Test
    fun testRegisterListener() = testScope.runTest {
        mClient = createUPClient()
        every { mManager.enableDispatching(testResourceUri) } returns STATUS_OK
        every { mManager.getLastMessage(testResourceUri) } returns null
        assertStatus(
            UCode.OK,
            mClient.registerListener(testResourceUri, mListener)
        )
        verify(exactly = 1) { mManager.enableDispatching(testResourceUri) }
        verify(exactly = 0) { mManager.getLastMessage(testResourceUri) }
    }

    @Test
    fun testRegisterListenerWithInvalidArgument() {
        mClient = createUPClient()
        assertStatus(
            UCode.INVALID_ARGUMENT,
            mClient.registerListener(UUri.getDefaultInstance(), mListener)
        )
        assertStatus(
            UCode.INVALID_ARGUMENT,
            mClient.registerListener(testMethodUri, mListener)
        )
        verify(exactly = 0) { mManager.enableDispatching(any()) }
        verify(exactly = 0) { mManager.getLastMessage(any()) }
    }

    @Test
    fun testRegisterListenerDifferentTopics() {
        mClient = createUPClient()
        every { mManager.enableDispatching(testResourceUri) } returns STATUS_OK
        every { mManager.enableDispatching(testResource2Uri) } returns STATUS_OK
        assertStatus(
            UCode.OK,
            mClient.registerListener(testResourceUri, mListener)
        )
        assertStatus(
            UCode.OK,
            mClient.registerListener(testResource2Uri, mListener)
        )
        verify(exactly = 1) { mManager.enableDispatching(testResourceUri) }
        verify(exactly = 1) { mManager.enableDispatching(testResource2Uri) }
    }

    @Test
    fun testRegisterListenerSame() {
        testRegisterListener()
        assertStatus(
            UCode.OK,
            mClient.registerListener(testResourceUri, mListener)
        )
        verify(exactly = 1) { mManager.enableDispatching(testResourceUri) }
        verify(exactly = 0) { mManager.getLastMessage(testResourceUri) }
    }

    @Test
    fun testRegisterListenerNotFirst() {
        testRegisterListener()
        assertStatus(
            UCode.OK,
            mClient.registerListener(testResourceUri, mListener2)
        )
        verify(exactly = 1) { mManager.enableDispatching(testResourceUri) }
        verify(exactly = 1) { mManager.getLastMessage(testResourceUri) }
    }

    @Test
    fun testRegisterListenerNotFirstLastMessageNotified() {
        testRegisterListener()
        every { mManager.getLastMessage(testResourceUri) } returns testMessage
        assertStatus(
            UCode.OK,
            mClient.registerListener(testResourceUri, mListener2)
        )
        verify(exactly = 1) { mManager.enableDispatching(testResourceUri) }
        verify(exactly = 1) { mManager.getLastMessage(testResourceUri) }
        verify(exactly = 1, timeout = DELAY_MS) { mListener2.onReceive(testMessage) }
    }

    @Test
    fun testRegisterListenerFailed() {
        mClient = createUPClient()
        every { mManager.enableDispatching(testResourceUri) } returns UCode.UNAUTHENTICATED.buildStatus()
        assertStatus(
            UCode.UNAUTHENTICATED,
            mClient.registerListener(testResourceUri, mListener)
        )
    }

    @Test
    fun testRegisterListenerWhenReconnected() {
        testRegisterListener()
        val connectionCallback: ConnectionCallback = mClient.getPrivateObject("mConnectionCallback")
        connectionCallback.onConnectionInterrupted()
        verify(exactly = 0, timeout = DELAY_MS) { mManager.disableDispatchingQuietly(testResourceUri) }
        connectionCallback.onConnected()
        // once at registration and once at reconnection
        verify(exactly = 2, timeout = DELAY_MS) { mManager.enableDispatching(testResourceUri) }
    }

    @Test
    fun testUnregisterListener() {
        testRegisterListener()
        every { mManager.disableDispatching(testResourceUri) } returns STATUS_OK
        assertStatus(UCode.OK, mClient.unregisterListener(testResourceUri, mListener))
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testResourceUri) }
    }

    @Test
    fun testUnregisterListenerWithException() {
        testRegisterListener()
        every { mManager.disableDispatchingQuietly(testResourceUri) } throws UStatusException(UCode.ABORTED, "test")
        assertStatus(UCode.ABORTED, mClient.unregisterListener(testResourceUri, mListener))
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testResourceUri) }
    }

    @Test
    fun testUnregisterListenerAllWithException() {
        testRegisterListener()
        every { mManager.disableDispatchingQuietly(testResourceUri) } throws UStatusException(UCode.ABORTED, "test")
        assertStatus(UCode.ABORTED, mClient.unregisterListener(mListener))
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testResourceUri) }
    }

    @Test
    fun testUnregisterListenerWithInvalidArgument() {
        mClient = createUPClient()
        assertStatus(
            UCode.INVALID_ARGUMENT,
            mClient.unregisterListener(
                UUri.getDefaultInstance(),
                mListener
            )
        )
        assertStatus(
            UCode.INVALID_ARGUMENT,
            mClient.unregisterListener(
                testMethodUri,
                mListener
            )
        )
        verify(exactly = 0) { mManager.disableDispatchingQuietly(any()) }
    }

    @Test
    fun testUnregisterListenerSame() {
        testUnregisterListener()
        assertStatus(
            UCode.OK,
            mClient.unregisterListener(
                testResourceUri,
                mListener
            )
        )
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testResourceUri) }
    }

    @Test
    fun testUnregisterListenerNotRegistered() {
        testRegisterListener()
        assertStatus(
            UCode.OK,
            mClient.unregisterListener(
                testResourceUri,
                mListener2
            )
        )
        verify(exactly = 0) { mManager.disableDispatchingQuietly(testResourceUri) }
    }

    @Test
    fun testUnregisterListenerNotLast() {
        testRegisterListenerNotFirst()
        assertStatus(
            UCode.OK,
            mClient.unregisterListener(
                testResourceUri,
                mListener
            )
        )
        verify(exactly = 0) { mManager.disableDispatchingQuietly(testResourceUri) }
    }

    @Test
    fun testUnregisterListenerLast() {
        testUnregisterListenerNotLast()
        assertStatus(
            UCode.OK,
            mClient.unregisterListener(
                testResourceUri,
                mListener2
            )
        )
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testResourceUri) }
    }

    @Test
    fun testUnregisterListenerWhenDisconnected() {
        testRegisterListener()
        val connectionCallback: ConnectionCallback = mClient.getPrivateObject("mConnectionCallback")
        connectionCallback.onDisconnected()
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(testMessage)
        verify(exactly = 0, timeout = DELAY_MS) { mListener.onReceive(testMessage) }
    }

    @Test
    fun testUnregisterListenerFromAllTopics() {
        testRegisterListenerDifferentTopics()
        assertStatus(UCode.OK, mClient.unregisterListener(mListener))
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testResourceUri) }
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testResource2Uri) }
    }

    @Test
    fun testOnReceiveGenericMessage() {
        testRegisterListenerNotFirst()
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(testMessage)
        verify(exactly = 1) { mListener.onReceive(testMessage) }
        verify(exactly = 1) { mListener2.onReceive(testMessage) }
    }

    @Test
    fun testOnReceiveGenericMessageNotRegistered() {
        testUnregisterListener()
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(testMessage)
        verify(exactly = 0) { mListener.onReceive(testMessage) }
    }

    @Test
    fun testOnReceiveGenericMessageRegisteredButNoListener() {
        mClient = createUPClient()
        val mListenersMap: ConcurrentHashMap<UUri, Set<UListener>> = mClient.getPrivateObject("mListenersMap")
        mListenersMap[testResourceUri] = emptySet()
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(testMessage)
        verify(exactly = 0) { mListener.onReceive(testMessage) }
    }

    @Test
    fun testOnReceiveNotificationMessage() {
        testRegisterListener()
        val message: UMessage = buildMessage(
            testPayload,
            newNotificationAttributes(testResourceUri, testClientUri)
        )
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(message)
        verify(exactly = 1, timeout = DELAY_MS) { mListener.onReceive(message) }
    }

    @Test
    fun testOnReceiveNotificationMessageWrongSink() {
        testRegisterListener()
        val message: UMessage = buildMessage(
            testPayload,
            newNotificationAttributes(testResourceUri, testServiceUri)
        )
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(message)
        verify(exactly = 0, timeout = DELAY_MS) { mListener.onReceive(message) }
        val tag: String = mClient.getLogTag()
        verify {
            Log.w(
                tag,
                match<String> {
                    it.contains("Wrong sink")
                }
            )
        }
    }

    @Test
    fun testOnReceiveMessageExpired() {
        testRegisterListener()
        mockkObject(UAttributesValidator)
        val mockValidator = mockk<UAttributesValidator>()
        every { UAttributesValidator.getValidator(any()) } returns mockValidator
        every { mockValidator.validate(any()) } returns ValidationResult.success()
        every { mockValidator.isExpired(any()) } returns true
        val message: UMessage = buildMessage(
            testPayload,
            newPublishAttributes(testResourceUri).copy { ttl = 1 }
        )
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(message)
        verify(timeout = DELAY_MS, exactly = 0) { mListener.onReceive(message) }
        val tag: String = mClient.getLogTag()
        verify {
            Log.w(
                tag,
                match<String> {
                    it.contains("Expired")
                }
            )
        }
    }

    @Test
    fun testOnReceiveMessageWithoutAttributes() {
        testRegisterListener()
        val message: UMessage = buildMessage(null, null)
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(message)
        verify(exactly = 0, timeout = DELAY_MS) { mListener.onReceive(message) }
    }

    @Test
    fun testOnReceiveMessageWithUnknownType() {
        testRegisterListener()
        mockkObject(UAttributesValidator)
        val mockValidator = mockk<UAttributesValidator>()
        every { UAttributesValidator.getValidator(any()) } returns mockValidator
        every { mockValidator.validate(any()) } returns ValidationResult.Success
        every { mockValidator.isExpired(any()) } returns false
        val message: UMessage = buildMessage(null, null)
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(message)
        verify(exactly = 0, timeout = DELAY_MS) { mListener.onReceive(message) }
        val tag: String = mClient.getLogTag()
        verify {
            Log.w(
                tag,
                match<String> {
                    it.contains("Unknown type")
                }
            )
        }
    }

    @Test
    fun testRegisterRpcListener() {
        mClient = createUPClient()
        every { mManager.enableDispatching(testMethodUri) } returns STATUS_OK
        assertStatus(
            UCode.OK,
            mClient.registerRpcListener(testMethodUri, mRequestListener)
        )
        verify(exactly = 1) { mManager.enableDispatching(testMethodUri) }
    }

    @Test
    fun testRegisterRpcListenerWithInvalidArgument() {
        mClient = createUPClient()
        assertStatus(
            UCode.INVALID_ARGUMENT,
            mClient.registerRpcListener(
                UUri.getDefaultInstance(),
                mRequestListener
            )
        )
        assertStatus(
            UCode.INVALID_ARGUMENT,
            mClient.registerRpcListener(
                testResourceUri,
                mRequestListener
            )
        )
        verify(exactly = 0) { mManager.enableDispatching(any()) }
    }

    @Test
    fun testRegisterRpcListenerDifferentMethods() {
        mClient = createUPClient()
        every { mManager.enableDispatching(testMethodUri) } returns STATUS_OK
        every { mManager.enableDispatching(testMethod2Uri) } returns STATUS_OK
        assertStatus(
            UCode.OK,
            mClient.registerRpcListener(
                testMethodUri,
                mRequestListener
            )
        )
        assertStatus(
            UCode.OK,
            mClient.registerRpcListener(
                testMethod2Uri,
                mRequestListener
            )
        )
        verify(exactly = 1) { mManager.enableDispatching(testMethodUri) }
        verify(exactly = 1) { mManager.enableDispatching(testMethod2Uri) }
    }

    @Test
    fun testRegisterRpcListenerSame() {
        testRegisterRpcListener()
        assertStatus(
            UCode.OK,
            mClient.registerRpcListener(
                testMethodUri,
                mRequestListener
            )
        )
        verify(exactly = 1) { mManager.enableDispatching(testMethodUri) }
    }

    @Test
    fun testRegisterRpcListenerNotFirst() {
        testRegisterRpcListener()
        assertStatus(
            UCode.ALREADY_EXISTS,
            mClient.registerRpcListener(
                testMethodUri,
                mRequestListener2
            )
        )
        verify(exactly = 1) { mManager.enableDispatching(testMethodUri) }
    }

    @Test
    fun testRegisterRpcListenerFailed() {
        mClient = createUPClient()
        every { mManager.enableDispatching(testMethodUri) } returns UCode.UNAUTHENTICATED.buildStatus()
        assertStatus(
            UCode.UNAUTHENTICATED,
            mClient.registerRpcListener(
                testMethodUri,
                mRequestListener
            )
        )
    }

    @Test
    fun testRegisterRpcListenerWhenReconnected() {
        testRegisterRpcListener()
        val connectionCallback: ConnectionCallback = mClient.getPrivateObject("mConnectionCallback")
        connectionCallback.onConnectionInterrupted()
        verify(exactly = 0) { mManager.disableDispatchingQuietly(testMethodUri) }
        connectionCallback.onConnected()
        verify(exactly = 2, timeout = DELAY_MS) { mManager.enableDispatching(testMethodUri) }
    }

    @Test
    fun testUnregisterRpcListener() {
        testRegisterRpcListener()
        every { mManager.disableDispatching(testMethodUri) } returns STATUS_OK
        assertStatus(
            UCode.OK,
            mClient.unregisterRpcListener(
                testMethodUri,
                mRequestListener
            )
        )
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testMethodUri) }
    }

    @Test
    fun testUnregisterRpcListenerWithException() {
        testRegisterRpcListener()
        every { mManager.disableDispatchingQuietly(testMethodUri) } throws UStatusException(UCode.ABORTED, "Aborted")
        assertStatus(
            UCode.ABORTED,
            mClient.unregisterRpcListener(
                testMethodUri,
                mRequestListener
            )
        )
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testMethodUri) }
    }

    @Test
    fun testUnregisterRpcListenerAllWithException() {
        testRegisterRpcListener()
        every { mManager.disableDispatchingQuietly(testMethodUri) } throws UStatusException(UCode.ABORTED, "Aborted")
        assertStatus(
            UCode.ABORTED,
            mClient.unregisterRpcListener(mRequestListener)
        )
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testMethodUri) }
    }

    @Test
    fun testUnregisterRpcListenerWithInvalidArgument() {
        mClient = createUPClient()
        assertStatus(
            UCode.INVALID_ARGUMENT,
            mClient.unregisterRpcListener(
                UUri.getDefaultInstance(),
                mRequestListener
            )
        )
        assertStatus(
            UCode.INVALID_ARGUMENT,
            mClient.unregisterRpcListener(
                testResourceUri,
                mRequestListener
            )
        )
        verify(exactly = 0) { mManager.disableDispatchingQuietly(any()) }
    }

    @Test
    fun testUnregisterRpcListenerSame() {
        testUnregisterRpcListener()
        assertStatus(
            UCode.OK,
            mClient.unregisterRpcListener(
                testMethodUri,
                mRequestListener
            )
        )
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testMethodUri) }
    }

    @Test
    fun testUnregisterRpcListenerNotRegistered() {
        testRegisterRpcListener()
        assertStatus(
            UCode.OK,
            mClient.unregisterRpcListener(
                testMethodUri,
                mRequestListener2
            )
        )
        verify(exactly = 0) { mManager.disableDispatchingQuietly(testMethodUri) }
    }

    @Test
    fun testUnregisterRpcListenerWhenDisconnected() {
        testRegisterRpcListener()
        val connectionCallback: ConnectionCallback = mClient.getPrivateObject("mConnectionCallback")
        connectionCallback.onDisconnected()
        val requestMessage: UMessage =
            buildMessage(testPayload, buildRequestAttributes(testResponseUri, testMethodUri))
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(requestMessage)
        verify(exactly = 0, timeout = DELAY_MS) { mRequestListener.onReceive(requestMessage, any()) }
    }

    @Test
    fun testUnregisterRpcListenerFromAllMethods() {
        testRegisterRpcListenerDifferentMethods()
        assertStatus(UCode.OK, mClient.unregisterRpcListener(mRequestListener))
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testMethodUri) }
        verify(exactly = 1) { mManager.disableDispatchingQuietly(testMethod2Uri) }
    }

    @Test
    fun testUnregisterRpcListenerFromAllMethodsNotRegistered() {
        testRegisterRpcListenerDifferentMethods()
        assertStatus(UCode.OK, mClient.unregisterRpcListener(mRequestListener2))
        verify(exactly = 0) { mManager.disableDispatchingQuietly(testMethodUri) }
        verify(exactly = 0) { mManager.disableDispatchingQuietly(testMethod2Uri) }
    }

    @Test
    fun testOnReceiveRequestMessage() {
        testRegisterRpcListener()
        val requestMessage: UMessage =
            buildMessage(testPayload, buildRequestAttributes(testResponseUri, testMethodUri))
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(requestMessage)
        verify(exactly = 1, timeout = DELAY_MS) { mRequestListener.onReceive(requestMessage, any()) }
    }

    @Test
    fun testOnReceiveRequestMessageNotRegistered() {
        testUnregisterRpcListener()
        val requestMessage: UMessage =
            buildMessage(testPayload, buildRequestAttributes(testResponseUri, testMethodUri))
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(requestMessage)
        verify(exactly = 0, timeout = DELAY_MS) { mRequestListener.onReceive(requestMessage, any()) }
    }

    @Test
    fun testSendResponseMessage() {
        testRegisterRpcListener()
        val requestMessage: UMessage = buildMessage(testPayload, buildRequestAttributes(testResponseUri, testMethodUri))
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(requestMessage)
        val completableSlot = slot<CompletableDeferred<UPayload>>()
        verify(exactly = 1, timeout = DELAY_MS) { mRequestListener.onReceive(requestMessage, capture(completableSlot)) }
        completableSlot.captured.complete(testPayload)
        verify(exactly = 1, timeout = DELAY_MS) {
            mManager.send(
                match {
                    it.payload == testPayload &&
                        it.attributes.source == testMethodUri &&
                        it.attributes.sink == testResponseUri
                }
            )
        }
    }

    @Test
    fun testSendResponseMessageWithCommStatus() {
        testRegisterRpcListener()
        val requestMessage: UMessage = buildMessage(testPayload, buildRequestAttributes(testResponseUri, testMethodUri))
        val listener: UListener = mClient.getPrivateObject("mListener")
        listener.onReceive(requestMessage)
        val completableSlot = slot<CompletableDeferred<UPayload>>()
        verify(exactly = 1, timeout = DELAY_MS) { mRequestListener.onReceive(requestMessage, capture(completableSlot)) }
        completableSlot.captured.completeExceptionally(UStatusException(UCode.ABORTED, "Aborted"))
        verify(exactly = 1, timeout = DELAY_MS) {
            mManager.send(
                match {
                    it.payload == UPayload.getDefaultInstance() &&
                        it.attributes.source == testMethodUri &&
                        it.attributes.sink == testResponseUri &&
                        it.attributes.commstatus == UCode.ABORTED_VALUE
                }
            )
        }
    }

    @Test
    fun testInvokeMethod() = testScope.runTest {
        testRegisterRpcListener()
        val sendSlot = slot<UMessage>()
        every { mManager.send(capture(sendSlot)) } returns STATUS_OK
        launch {
            val responseMessage = mClient.invokeMethod(testMethodUri, REQUEST_PAYLOAD, testCallOptions).first()
            assertEquals(REQUEST_PAYLOAD, responseMessage.payload)
            assertEquals(testResponseUri, responseMessage.attributes.source)
            assertEquals(testResponseUri, responseMessage.attributes.sink)
            assertEquals(UMessageType.UMESSAGE_TYPE_RESPONSE, responseMessage.attributes.type)
            assertEquals(responseMessage.attributes.id, responseMessage.attributes.reqid)
        }
        val listener: UListener = mClient.getPrivateObject("mListener")
        val resp = sendSlot.captured.copy {
            attributes = attributes.copy {
                sink = source
                reqid = id
                type = UMessageType.UMESSAGE_TYPE_RESPONSE
            }
        }
        listener.onReceive(resp)
    }

    @Test
    fun testInvokeMethodWithInvalidArgument() = testScope.runTest {
        mClient = createUPClient()
        try {
            mClient.invokeMethod(
                UUri.getDefaultInstance(),
                testPayload,
                testCallOptions
            ).first()
            fail()
        } catch (e: Exception) {
            if (e !is UStatusException) {
                fail()
            } else {
                assertStatus(UCode.INVALID_ARGUMENT, e.toUStatus())
            }
        }
    }

    @Test
    fun testInvokeMethodOtherResponseReceive() = testScope.runTest {
        testRegisterRpcListener()
        every { mManager.send(any()) } returns STATUS_OK
        launch {
            try {
                mClient.invokeMethod(testMethodUri, REQUEST_PAYLOAD, testCallOptions).first()
                fail()
            } catch (e: Exception) {
                if (e !is TimeoutCancellationException) {
                    fail()
                }
            }
        }
        val listener: UListener = mClient.getPrivateObject("mListener")
        val responseMessage: UMessage = buildMessage(
            testPayload,
            buildResponseAttributes(testMethodUri, testResponseUri, createId())
        )
        listener.onReceive(responseMessage)
    }

    @Test
    fun testInvokeMethodWhenDisconnected() = testScope.runTest {
        testRegisterRpcListener()
        every { mManager.send(any()) } returns STATUS_OK
        launch {
            try {
                mClient.invokeMethod(testMethodUri, REQUEST_PAYLOAD, testCallOptions).first()
                fail()
            } catch (e: Exception) {
                if (e !is UStatusException) {
                    fail()
                }
            }
        }
        val connectionCallback: ConnectionCallback = mClient.getPrivateObject("mConnectionCallback")
        every { mServiceLifecycleListener.onLifecycleChanged(mClient, any()) } returns Unit
        connectionCallback.onDisconnected()
    }

    @Test
    fun testInvokeMethodCompletedWithCommStatus() = testScope.runTest {
        testRegisterRpcListener()
        val sendSlot = slot<UMessage>()
        every { mManager.send(capture(sendSlot)) } returns STATUS_OK
        launch {
            try {
                mClient.invokeMethod(testMethodUri, REQUEST_PAYLOAD, testCallOptions).first()
                fail()
            } catch (e: Exception) {
                if (e !is UStatusException) {
                    fail()
                } else {
                    assertStatus(UCode.CANCELLED, e.toUStatus())
                }
            }
        }
        val listener: UListener = mClient.getPrivateObject("mListener")
        val resp = sendSlot.captured.copy {
            attributes = attributes.copy {
                sink = source
                reqid = id
                type = UMessageType.UMESSAGE_TYPE_RESPONSE
                commstatus = UCode.CANCELLED_VALUE
            }
        }
        listener.onReceive(resp)
    }

    @Test
    fun testInvokeMethodCompletedWithCommStatusOk() = testScope.runTest {
        testRegisterRpcListener()
        val sendSlot = slot<UMessage>()
        every { mManager.send(capture(sendSlot)) } returns STATUS_OK
        launch {
            val responseMessage = mClient.invokeMethod(testMethodUri, REQUEST_PAYLOAD, testCallOptions).first()
            assertEquals(REQUEST_PAYLOAD, responseMessage.payload)
            assertEquals(testResponseUri, responseMessage.attributes.source)
            assertEquals(testResponseUri, responseMessage.attributes.sink)
            assertEquals(UMessageType.UMESSAGE_TYPE_RESPONSE, responseMessage.attributes.type)
            assertEquals(responseMessage.attributes.id, responseMessage.attributes.reqid)
        }
        val listener: UListener = mClient.getPrivateObject("mListener")
        val resp = sendSlot.captured.copy {
            attributes = attributes.copy {
                sink = source
                reqid = id
                type = UMessageType.UMESSAGE_TYPE_RESPONSE
                commstatus = UCode.OK_VALUE
            }
        }
        listener.onReceive(resp)
    }

    @Test
    fun testInvokeMethodSameRequest() = testScope.runTest {
        testRegisterRpcListener()
        val sendSlot = slot<UMessage>()
        every { mManager.send(capture(sendSlot)) } returns STATUS_OK
        val testUUID = mockk<UUID>()
        mockkObject(UUIDV8)
        every { UUIDV8(any()) } answers {
            testUUID
        }
        every { testUUID.msb } returns 123456L
        every { testUUID.lsb } returns 789012L
        launch {
            val responseMessage = mClient.invokeMethod(testMethodUri, REQUEST_PAYLOAD, testCallOptions).first()
            assertEquals(REQUEST_PAYLOAD, responseMessage.payload)
            assertEquals(testResponseUri, responseMessage.attributes.source)
            assertEquals(testResponseUri, responseMessage.attributes.sink)
            assertEquals(UMessageType.UMESSAGE_TYPE_RESPONSE, responseMessage.attributes.type)
            assertEquals(responseMessage.attributes.id, responseMessage.attributes.reqid)
        }
        launch {
            delay((testCallOptions.timeout / 4).toLong())
            try {
                mClient.invokeMethod(testMethodUri, REQUEST_PAYLOAD, testCallOptions).first()
                fail()
            } catch (e: Exception) {
                if (e !is UStatusException) {
                    fail()
                } else {
                    assertStatus(UCode.ABORTED, e.toUStatus())
                }
            }
        }
        val listener: UListener = mClient.getPrivateObject("mListener")
        val resp = sendSlot.captured.copy {
            attributes = attributes.copy {
                sink = source
                reqid = id
                type = UMessageType.UMESSAGE_TYPE_RESPONSE
            }
        }
        delay((testCallOptions.timeout / 2).toLong())
        listener.onReceive(resp)
    }

    @Test
    fun testInvokeMethodSendFailure() = testScope.runTest {
        mClient = createUPClient()
        every { mManager.send(any()) } returns UCode.UNAVAILABLE.buildStatus()
        try {
            mClient.invokeMethod(
                testMethodUri,
                testPayload,
                testCallOptions
            ).first()
            fail()
        } catch (e: Exception) {
            if (e !is UStatusException) {
                fail()
            } else {
                assertStatus(UCode.UNAVAILABLE, e.toUStatus())
            }
        }
    }

    private fun UPClient.getLogTag(): String = getPrivateObject("mTag")
    private fun createUPClient() = UPClient::class.java.getDeclaredConstructor(
        Context::class.java,
        UEntity::class.java,
        CoroutineDispatcher::class.java,
        ServiceLifecycleListener::class.java
    ).apply { isAccessible = true }
        .newInstance(mContext, testClient, testDispatcher, mServiceLifecycleListener)

    companion object {
        private val REQUEST_PAYLOAD: UPayload = uPayload {
            packToAny(Int32Value.newBuilder().setValue(1).build())
        }
    }
}
