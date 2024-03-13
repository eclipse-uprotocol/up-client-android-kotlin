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
package org.eclipse.uprotocol.core.ubus

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.eclipse.uprotocol.TestBase
import org.eclipse.uprotocol.callPrivateMethod
import org.eclipse.uprotocol.client.R
import org.eclipse.uprotocol.common.UStatusException
import org.eclipse.uprotocol.common.util.STATUS_OK
import org.eclipse.uprotocol.common.util.buildStatus
import org.eclipse.uprotocol.getPrivateObject
import org.eclipse.uprotocol.transport.UListener
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage
import org.eclipse.uprotocol.v1.internal.ParcelableUStatus
import org.eclipse.uprotocol.v1.internal.ParcelableUUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class UBusManagerTest : TestBase() {
    private val mContext: Context = mockk(relaxed = true)
    private val mConnectionCallback: ConnectionCallback = mockk(relaxed = true)
    private val mListener: UListener = mockk(relaxed = true)
    private lateinit var mManager: UBusManager
    private lateinit var mServiceConnection: ServiceConnection
    private val mServiceBinder: IBinder = mockk(relaxed = true)
    private val mService: IUBus = mockk(relaxed = true)
    private val testMessage: UMessage = buildMessage(testPayload, buildPublishAttributes(testResourceUri))
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        mockLogger()
        setServiceConfig(SERVICE_PACKAGE)
        mManager = UBusManager(mContext, testClient, testDispatcher, mConnectionCallback, mListener)
        mManager.setLoggable(Log.INFO)
        prepareService()
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

    private fun setServiceConfig(config: String) {
        every { mContext.getString(R.string.config_UBusService) } returns config
    }

    private fun prepareService() {
        mockkStatic(IUBus.Stub::class)
        every { IUBus.Stub.asInterface(any()) } returns mService
        every { mService.asBinder() } returns mServiceBinder
        every { mService.registerClient(any(), any(), any(), any(), any()) } returns ParcelableUStatus(STATUS_OK)
        every { mService.unregisterClient(any()) } returns ParcelableUStatus(STATUS_OK)
        every { mService.enableDispatching(any(), any(), any()) } returns ParcelableUStatus(STATUS_OK)
        every { mService.disableDispatching(any(), any(), any()) } returns ParcelableUStatus(STATUS_OK)
        every { mService.send(any(), any()) } returns ParcelableUStatus(STATUS_OK)
        every { mService.pull(any(), any(), any(), any()) } returns arrayOf(ParcelableUMessage(testMessage))
        prepareService(true) { connection: ServiceConnection ->
            mServiceConnection = connection
            mServiceConnection.onServiceConnected(COMPONENT_NAME_SERVICE, mServiceBinder)
        }
    }

    private fun prepareService(available: Boolean, onBindCallback: (ServiceConnection) -> Unit = {}) {
        every { mContext.bindService(any(), any(), any()) } answers {
            onBindCallback.invoke(secondArg())
            available
        }
    }

    private fun assertDisconnected() {
        verify { mConnectionCallback.onDisconnected() }
        assertTrue(mManager.isDisconnected())
    }

    private fun assertDisconnected(disconnectionResult: UStatus) {
        assertStatus(UCode.OK, disconnectionResult)
        assertDisconnected()
    }

    private fun assertConnecting() {
        verify(atLeast = 1) { mContext.bindService(any(), any(), any()) }
        assertTrue(mManager.isConnecting())
    }

    private fun assertConnected(onConnectedTime: Int = 1) {
        verify(exactly = onConnectedTime) { mConnectionCallback.onConnected() }
        assertTrue(mManager.isConnected())
    }

    private fun assertConnected(connectionResult: UStatus) {
        assertStatus(UCode.OK, connectionResult)
        assertConnected()
    }
    private fun assertConnectionFailed(uStatus: UStatus, code: UCode) {
        assertStatus(code, uStatus)
        verify(exactly = 0) { mConnectionCallback.onConnected() }
        assertTrue(mManager.isDisconnected())
    }

    private fun assertConnectionInterrupted() {
        verify { mConnectionCallback.onConnectionInterrupted() }
        assertTrue(mManager.isDisconnected())
    }

    @Test
    fun createUBusManager() {
        val manager = UBusManager.create(mContext, testClient, testDispatcher, mConnectionCallback, mListener)
        assertEquals(mContext, manager.getPrivateObject("context"))
        assertEquals(testClient, manager.getPrivateObject("entity"))
        assertEquals(mConnectionCallback, manager.getPrivateObject("connectionCallback"))
        assertEquals(mListener, manager.getPrivateObject("listener"))
    }

    @Test
    fun testConnect() = testScope.runTest {
        assertConnected(mManager.connect())
        verify {
            mContext.bindService(
                match { intent ->
                    intent.action == UBusManager.ACTION_BIND_UBUS && intent.`package` == SERVICE_PACKAGE
                },
                any(),
                any()
            )
        }
    }

    @Test
    fun testConnectWithConfiguredPackage() = testScope.runTest {
        val packageName = "some.package"
        setServiceConfig(packageName)
        mManager = UBusManager(mContext, testClient, testDispatcher, mConnectionCallback, mListener)
        assertConnected(mManager.connect())
        verify {
            mContext.bindService(
                match { intent ->
                    intent.action == UBusManager.ACTION_BIND_UBUS && intent.`package` == packageName
                },
                any(),
                any()
            )
        }
    }

    @Test
    fun testConnectWithConfiguredComponent() = testScope.runTest {
        setServiceConfig(COMPONENT_NAME_SERVICE.flattenToShortString())
        mManager = UBusManager(mContext, testClient, testDispatcher, mConnectionCallback, mListener)
        assertConnected(mManager.connect())
        verify {
            mContext.bindService(
                match { intent ->
                    intent.action == UBusManager.ACTION_BIND_UBUS && intent.component == COMPONENT_NAME_SERVICE
                },
                any(),
                any()
            )
        }
    }

    @Test
    fun testConnectWithEmptyConfig() = testScope.runTest {
        setServiceConfig("")
        mManager = UBusManager(mContext, testClient, testDispatcher, mConnectionCallback, mListener)
        assertConnected(mManager.connect())
        verify {
            mContext.bindService(
                match { intent ->
                    intent.action == UBusManager.ACTION_BIND_UBUS && intent.`package` == null
                },
                any(),
                any()
            )
        }
    }

    @Test
    fun testConnectNoService() = testScope.runTest {
        prepareService(false)
        assertConnectionFailed(mManager.connect(), UCode.NOT_FOUND)
    }

    @Test
    fun testConnectFailedThenReconnect() = testScope.runTest {
        prepareService(false)
        assertConnectionFailed(mManager.connect(), UCode.NOT_FOUND)
        prepareService(true) { connection: ServiceConnection ->
            mServiceConnection = connection
            mServiceConnection.onServiceConnected(COMPONENT_NAME_SERVICE, mServiceBinder)
        }
        assertConnected(mManager.connect())
    }

    @Test
    fun testConnectServiceDisabled() = testScope.runTest {
        prepareService(false) {
            mServiceConnection = it
            mServiceConnection.onNullBinding(COMPONENT_NAME_SERVICE)
        }
        assertConnectionFailed(mManager.connect(), UCode.NOT_FOUND)
    }

    @Test
    fun testConnectPermissionDenied() = testScope.runTest {
        prepareService(true) { throw SecurityException("Permission denied") }
        assertConnectionFailed(mManager.connect(), UCode.PERMISSION_DENIED)
    }

    @Test
    fun testConnectUnauthenticated() = testScope.runTest {
        every { mService.registerClient(any(), any(), any(), any(), any()) } returns
            ParcelableUStatus(UCode.UNAUTHENTICATED.buildStatus())
        assertConnectionFailed(mManager.connect(), UCode.UNAUTHENTICATED)
    }

    @Test
    fun testConnectExceptionally() = testScope.runTest {
        every { mService.registerClient(any(), any(), any(), any(), any()) } throws UStatusException(UCode.INTERNAL, "Failure")
        assertConnectionFailed(mManager.connect(), UCode.INTERNAL)
    }

    @Test
    fun testConnectAlreadyConnected() = testScope.runTest {
        mManager.setLoggable(Log.DEBUG)
        mManager.connect()
        assertConnected(mManager.connect())
    }

    @Test
    fun testConnectFromMultipleThreads() {
        runBlocking {
            every { mContext.bindService(any(), any(), any()) } coAnswers {
                delay(10)
                mServiceConnection = secondArg()
                mServiceConnection.onServiceConnected(COMPONENT_NAME_SERVICE, mServiceBinder)
                true
            }

            launch { assertConnected(mManager.connect()) }
            launch { assertConnected(mManager.connect()) }
        }
    }

    @Test
    fun testConnectAlreadyConnecting() = testScope.runTest {
        prepareService(true) { mServiceConnection = it }
        launch {
            mManager.connect()
        }
        launch {
            delay(10) // connectFlow1 to run first
            assertConnecting()
            mServiceConnection.onServiceConnected(COMPONENT_NAME_SERVICE, mServiceBinder)
            assertConnected(mManager.connect())
        }
    }

    @Test
    fun testConnectOnServiceConnectedWithSameInstance() = testScope.runTest {
        assertConnected(mManager.connect())
        mServiceConnection.onServiceConnected(COMPONENT_NAME_SERVICE, mServiceBinder)
        assertConnected()
    }

    @Test
    fun testConnectOnServiceConnectedWithSameInstanceDebug() {
        mManager.setLoggable(Log.DEBUG)
        testConnectOnServiceConnectedWithSameInstance()
    }

    @Test
    fun testConnectOnServiceConnectedWithOtherInstance() = testScope.runTest {
        assertConnected(mManager.connect())
        delay(DELAY_MS)
        prepareService()
        mServiceConnection.onServiceConnected(COMPONENT_NAME_SERVICE, mServiceBinder)
        assertConnected()
    }

    @Test
    fun testDisconnect() = testScope.runTest {
        mManager.connect()
        assertDisconnected(mManager.disconnect())
    }

    @Test
    fun testDisconnectAlreadyDisconnected() = testScope.runTest {
        mManager.connect()
        mManager.disconnect()
        assertDisconnected(mManager.disconnect())
    }

    @Test
    fun testDisconnectWhileConnecting() = testScope.runTest {
        prepareService(true) { mServiceConnection = it }
        val connectDeferred = async { mManager.connect() }
        delay(10) // connectDeferred to run first
        assertConnecting()
        assertDisconnected(mManager.disconnect())
        mServiceConnection.onServiceConnected(COMPONENT_NAME_SERVICE, mServiceBinder)
        assertConnectionFailed(connectDeferred.await(), UCode.CANCELLED)
    }

    @Test
    fun testDisconnectWhileConnectingVerbose() {
        mManager.setLoggable(Log.VERBOSE)
        testDisconnectWhileConnecting()
    }

    @Test
    fun testDisconnectExceptionally() = testScope.runTest {
        mManager.connect()
        every { mService.unregisterClient(any()) } throws UStatusException(UCode.INTERNAL, "Failure")
        assertDisconnected(mManager.disconnect())
    }

    @Test
    fun testOnServiceDisconnected() {
        testConnect()
        mServiceConnection.onServiceDisconnected(COMPONENT_NAME_SERVICE)
        assertConnectionInterrupted()
    }

    @Test
    fun testOnServiceDisconnectedAlreadyDisconnected() {
        testDisconnect()
        mServiceConnection.onServiceDisconnected(COMPONENT_NAME_SERVICE)
        verify(exactly = 0) { mConnectionCallback.onConnectionInterrupted() }
    }

    @Test
    fun testOnServiceDisconnectedAlreadyDisconnectedDebug() {
        mManager.setLoggable(Log.DEBUG)
        testOnServiceDisconnectedAlreadyDisconnected()
    }

    @Test
    fun testOnBindingDied() = testScope.runTest {
        mManager.connect()
        mServiceConnection.onServiceDisconnected(COMPONENT_NAME_SERVICE)
        assertConnectionInterrupted()
        mServiceConnection.onBindingDied(COMPONENT_NAME_SERVICE)
        val rebindDelayBase: Long = mManager.getPrivateObject("REBIND_BACKOFF_BASE")
        delay((rebindDelayBase.toDouble().pow(0) * 1000).toLong())
        verify { mContext.unbindService(any()) }
        verify(exactly = 2) { mContext.bindService(any(), any(), any()) }
    }

    @Test
    fun testOnBindingDiedNotConnected() = testScope.runTest {
        mManager.connect()
        mManager.disconnect()
        mServiceConnection.onBindingDied(COMPONENT_NAME_SERVICE)
        val rebindDelayBase: Long = mManager.getPrivateObject("REBIND_BACKOFF_BASE")
        delay((rebindDelayBase.toDouble().pow(0) * 1000).toLong())
        verify { mContext.unbindService(any()) }
        verify(exactly = 1) { mContext.bindService(any(), any(), any()) }
    }

    @Test
    fun testOnBindingDiedNotConnectedDebug() = testScope.runTest {
        mManager.setLoggable(Log.DEBUG)
        mManager.connect()
        mManager.disconnect()
        mServiceConnection.onBindingDied(COMPONENT_NAME_SERVICE)
        val rebindDelayBase: Long = mManager.getPrivateObject("REBIND_BACKOFF_BASE")
        delay((rebindDelayBase.toDouble().pow(0) * 1000).toLong())
        verify { mContext.unbindService(any()) }
        verify(exactly = 1) { mContext.bindService(any(), any(), any()) }
    }

    @Test
    fun testOnBindingDiedAlreadyReconnected() = testScope.runTest {
        mManager.connect()
        mServiceConnection.onBindingDied(COMPONENT_NAME_SERVICE)
        val rebindDelayBase: Long = mManager.getPrivateObject("REBIND_BACKOFF_BASE")
        delay((rebindDelayBase.toDouble().pow(0) * 1000).toLong())
        verify(exactly = 0) { mContext.unbindService(any()) }
        verify(exactly = 1) { mContext.bindService(any(), any(), any()) }
        assertTrue(mManager.isConnected())
    }

    @Test
    fun testOnBindingDiedAlreadyReconnectedDebug() {
        mManager.setLoggable(Log.DEBUG)
        testOnBindingDiedAlreadyReconnected()
    }

    @Test
    fun testOnBindingDiedRebindFailed() = testScope.runTest {
        mManager.connect()
        mServiceConnection.onServiceDisconnected(COMPONENT_NAME_SERVICE)
        assertConnectionInterrupted()
        prepareService(false)
        mServiceConnection.onBindingDied(COMPONENT_NAME_SERVICE)
        val rebindDelayBase: Long = mManager.getPrivateObject("REBIND_BACKOFF_BASE")
        delay((rebindDelayBase.toDouble().pow(0) * 1000).toLong())
        verify { mContext.unbindService(any()) }
        verify(exactly = 2) { mContext.bindService(any(), any(), any()) }
        assertTrue(mManager.isDisconnected())
    }

    @Test
    fun testConnectionStates() = testScope.runTest {
        prepareService(true) { mServiceConnection = it }
        launch { mManager.connect() }
        delay(10) // connect job to run first
        assertConnecting()
        assertFalse(mManager.isDisconnected())
        assertFalse(mManager.isConnected())
        mServiceConnection.onServiceConnected(COMPONENT_NAME_SERVICE, mServiceBinder)
        assertConnected()
        assertFalse(mManager.isDisconnected())
        assertFalse(mManager.isConnecting())
        launch { mManager.disconnect() }
        delay(10) // disconnect job to run first
        assertDisconnected()
        assertFalse(mManager.isConnecting())
        assertFalse(mManager.isConnected())
    }

    @Test
    fun testCalculateRebindDelaySeconds() {
        listOf(1L, 2L, 4L, 8L, 16L, 32L, 32L, 32L).forEach {
            assertEquals(it, mManager.callPrivateMethod("calculateRebindDelaySeconds"))
        }
    }

    @Test
    fun testEnableDispatching() {
        testConnect()
        assertStatus(UCode.OK, mManager.enableDispatching(testResourceUri))
        verify { mService.enableDispatching(ParcelableUUri(testResourceUri), any(), any()) }
    }

    @Test
    fun testEnableDispatchingDisconnected() {
        assertStatus(UCode.UNAVAILABLE, mManager.enableDispatching(testResourceUri))
        verify(exactly = 0) { mService.enableDispatching(any(), any(), any()) }
    }

    @Test
    fun testDisableDispatching() {
        testConnect()
        assertStatus(UCode.OK, mManager.disableDispatching(testResourceUri))
        verify { mService.disableDispatching(ParcelableUUri(testResourceUri), any(), any()) }
    }

    @Test
    fun testDisableDispatchingDisconnected() {
        assertStatus(UCode.UNAVAILABLE, mManager.disableDispatching(testResourceUri))
        verify(exactly = 0) { mService.disableDispatching(any(), any(), any()) }
    }

    @Test
    fun testDisableDispatchingDisconnectedDebug() {
        mManager.setLoggable(Log.DEBUG)
        assertStatus(UCode.UNAVAILABLE, mManager.disableDispatching(testResourceUri))
        verify(exactly = 0) { mService.disableDispatching(any(), any(), any()) }
    }

    @Test
    fun testDisableDispatchingQuietly() {
        testConnect()
        mManager.disableDispatchingQuietly(testResourceUri)
        verify { mService.disableDispatching(ParcelableUUri(testResourceUri), any(), any()) }
    }

    @Test
    fun testGetLastMessage() {
        testConnect()
        assertEquals(testMessage, mManager.getLastMessage(testResourceUri))
        verify { mService.pull(ParcelableUUri(testResourceUri), 1, any(), any()) }
    }

    @Test
    fun testGetLastMessageNotAvailable() {
        testConnect()
        every { mService.pull(any(), any(), any(), any()) } returns arrayOfNulls<ParcelableUMessage>(0)
        assertNull(mManager.getLastMessage(testResourceUri))
        every { mService.pull(any(), any(), any(), any()) } returns null
        assertNull(mManager.getLastMessage(testResourceUri))
        verify(exactly = 2) { mService.pull(ParcelableUUri(testResourceUri), 1, any(), any()) }
    }

    @Test
    fun testGetLastMessageInvalidArgument() {
        testConnect()
        every { mService.pull(any(), any(), any(), any()) } throws NullPointerException()
        assertNull(mManager.getLastMessage(testResourceUri))
    }

    @Test
    fun testGetLastMessageDisconnected() {
        assertNull(mManager.getLastMessage(testResourceUri))
        verify(exactly = 0) { mService.pull(any(), any(), any(), any()) }
    }

    @Test
    fun testSend() {
        testConnect()
        assertStatus(UCode.OK, mManager.send(testMessage))
        verify(exactly = 1) { mService.send(ParcelableUMessage(testMessage), any()) }
    }

    @Test
    fun testSendDisconnected() {
        assertStatus(UCode.UNAVAILABLE, mManager.send(testMessage))
        verify(exactly = 0) { mService.send(any(), any()) }
    }

    @Test
    fun testOnReceive() {
        val listenerSlot = slot<IUListener>()
        every { mService.registerClient(any(), any(), any(), any(), capture(listenerSlot)) } returns ParcelableUStatus(STATUS_OK)
        testConnect()
        val serviceListener: IUListener = listenerSlot.captured
        serviceListener.onReceive(ParcelableUMessage(testMessage))
        verify(exactly = 1) { mListener.onReceive(testMessage) }
    }
    companion object {
        private const val SERVICE_PACKAGE = "org.eclipse.uprotocol.core.ubus"
        private val COMPONENT_NAME_SERVICE = ComponentName(SERVICE_PACKAGE, "$SERVICE_PACKAGE.UBusService")
    }
}
