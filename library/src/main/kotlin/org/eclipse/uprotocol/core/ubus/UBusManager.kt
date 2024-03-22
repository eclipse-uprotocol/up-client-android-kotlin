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
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.uprotocol.UPClient.Companion.TAG_GROUP
import org.eclipse.uprotocol.client.R
import org.eclipse.uprotocol.common.UStatusException
import org.eclipse.uprotocol.common.util.STATUS_OK
import org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull
import org.eclipse.uprotocol.common.util.UStatusUtils.checkStatusOk
import org.eclipse.uprotocol.common.util.buildStatus
import org.eclipse.uprotocol.common.util.isOk
import org.eclipse.uprotocol.common.util.log.Formatter.join
import org.eclipse.uprotocol.common.util.log.Formatter.status
import org.eclipse.uprotocol.common.util.log.Formatter.stringify
import org.eclipse.uprotocol.common.util.log.Formatter.tag
import org.eclipse.uprotocol.common.util.log.Key
import org.eclipse.uprotocol.common.util.log.logD
import org.eclipse.uprotocol.common.util.log.logV
import org.eclipse.uprotocol.common.util.toUStatus
import org.eclipse.uprotocol.transport.UListener
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UEntity
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.internal.ParcelableUEntity
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage
import org.eclipse.uprotocol.v1.internal.ParcelableUUri
import kotlin.math.pow

@ExperimentalCoroutinesApi
class UBusManager internal constructor(
    private val context: Context,
    private val entity: UEntity,
    private val dispatcher: CoroutineDispatcher,
    private val connectionCallback: ConnectionCallback,
    private val listener: UListener
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val mClientToken: IBinder = Binder()
    private val mServiceConfig = context.getString(R.string.config_UBusService)
    private val mTag: String = tag(entity.name, TAG_GROUP)
    private var mDebugLoggable = Log.isLoggable(mTag, Log.DEBUG)
    private var mVerboseLoggable = Log.isLoggable(mTag, Log.VERBOSE)

    private var mServiceBound = false
    private var mService: IUBus? = null

    private var mRebindBackoffExponent = 0

    private val mConnectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.DISCONNECTED())

    private val connectionMutex = Mutex()

    private val mServiceConnectionCallback: ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                scope.launch {
                    try {
                        val newService: IUBus = IUBus.Stub.asInterface(service)
                        connectionMutex.withLock {
                            if (!mServiceBound) {
                                logD(mDebugLoggable, mTag, join(Key.MESSAGE, "Service connection was cancelled"))
                                return@launch
                            } else if (mService != null && mService?.asBinder()?.equals(newService.asBinder()) == true) {
                                logD(mDebugLoggable, mTag, join(Key.MESSAGE, "Service is already connected"))
                                return@launch
                            } else {
                                checkStatusOk(registerClient(newService))
                                mService = newService
                                mConnectionState.value = ConnectionStatus.CONNECTED
                            }
                        }
                        connectionCallback.onConnected()
                    } catch (e: Exception) {
                        Log.e(mTag, join(Key.EVENT, "Service connection failed", Key.REASON, e.message))
                        connectionMutex.withLock {
                            unbindService()
                            handleServiceDisconnect()
                            mConnectionState.value = ConnectionStatus.DISCONNECTED(e.toUStatus())
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                scope.launch {
                    connectionMutex.withLock {
                        if (mConnectionState.value is ConnectionStatus.DISCONNECTED) {
                            logD(mDebugLoggable, mTag, join(Key.MESSAGE, "Service is already disconnected"))
                            return@launch
                        } else {
                            handleServiceDisconnect()
                        }
                    }
                    connectionCallback.onConnectionInterrupted()
                }
            }

            override fun onBindingDied(name: ComponentName) {
                scope.launch {
                    val delayTime = calculateRebindDelaySeconds() * 1000
                    Log.w(mTag, join(Key.EVENT, "Service binder died", Key.MESSAGE, "Rebind in $delayTime second(s)..."))
                    delay(delayTime)
                    connectionMutex.withLock {
                        if (!mServiceBound) {
                            logD(mDebugLoggable, mTag, join(Key.MESSAGE, "Service connection was cancelled"))
                        } else if (mConnectionState.value == ConnectionStatus.CONNECTED) {
                            logD(mDebugLoggable, mTag, join(Key.MESSAGE, "Service is already connected"))
                        } else {
                            unbindService()
                            val status: UStatus = bindService()
                            if (!status.isOk()) {
                                handleServiceDisconnect(status)
                            }
                        }
                    }
                }
            }

            override fun onNullBinding(name: ComponentName) {
                scope.launch {
                    connectionMutex.withLock {
                        Log.e(mTag, join(Key.EVENT, "Null binding to service", Key.NAME, name.className))
                        unbindService()
                        handleServiceDisconnect(UCode.NOT_FOUND.buildStatus("Service is disabled"))
                    }
                    connectionCallback.onDisconnected()
                }
            }
        }

    private val mServiceListener: IUListener.Stub =
        object : IUListener.Stub() {
            override fun onReceive(data: ParcelableUMessage) {
                listener.onReceive(data.getWrapped())
            }
        }

    init {
        scope.launch {
            var preState = mConnectionState.value
            mConnectionState.drop(1).collect { state ->
                logV(mVerboseLoggable, mTag, join(Key.MESSAGE, "Connection state: ${preState.javaClass.simpleName} -> ${state.javaClass.simpleName}"))
                preState = state
            }
        }
    }

    suspend fun connect(): UStatus {
        mConnectionState.value.let {
            if (it is ConnectionStatus.DISCONNECTED && it.reason != null) {
                mConnectionState.value = ConnectionStatus.DISCONNECTED()
            }
        }
        return mConnectionState.flatMapLatest { state ->
            flow {
                when (state) {
                    is ConnectionStatus.DISCONNECTED -> {
                        state.reason?.let { emit(it) } ?: let {
                            val status = connectionMutex.withLock {
                                bindService()
                            }
                            // In case connect calls are made simultaneously
                            if (!currentCoroutineContext().isActive) {
                                return@flow
                            }
                            if (status.isOk()) {
                                mConnectionState.value = ConnectionStatus.CONNECTING
                            } else {
                                handleServiceDisconnect(status)
                            }
                        }
                    }

                    ConnectionStatus.CONNECTED -> {
                        emit(STATUS_OK)
                    }

                    ConnectionStatus.CONNECTING -> {
                    }
                }
            }
        }.flowOn(dispatcher).first()
    }

    suspend fun disconnect(): UStatus {
        return withContext(dispatcher) {
            val oldState = mConnectionState.value
            val service = mService
            connectionMutex.withLock {
                unbindService()
            }
            val status = UCode.CANCELLED.buildStatus("Service connection is cancelled")
            handleServiceDisconnect(status)
            service?.let { unregisterClient(it) }
            if (oldState !is ConnectionStatus.DISCONNECTED) {
                connectionCallback.onDisconnected()
            }
            STATUS_OK
        }
    }

    fun isDisconnected(): Boolean = mConnectionState.value is ConnectionStatus.DISCONNECTED

    fun isConnecting(): Boolean = mConnectionState.value == ConnectionStatus.CONNECTING

    fun isConnected(): Boolean = mConnectionState.value == ConnectionStatus.CONNECTED

    @Throws(UStatusException::class)
    private fun getServiceOrThrow(): IUBus {
        return checkNotNull(mService, UCode.UNAVAILABLE, "Service is not connected")
    }

    private fun handleServiceDisconnect(cancelReason: UStatus? = null) {
        mConnectionState.value = ConnectionStatus.DISCONNECTED(cancelReason)
        mService = null
        mRebindBackoffExponent = 0
    }

    private fun buildServiceIntent(): Intent {
        return Intent(ACTION_BIND_UBUS).apply {
            val component = ComponentName.unflattenFromString(mServiceConfig)
            if (component != null) {
                setComponent(component)
            } else if (mServiceConfig.isNotEmpty()) {
                setPackage(mServiceConfig)
            }
        }
    }

    private fun bindService(): UStatus {
        return try {
            if (mConnectionState.value !is ConnectionStatus.DISCONNECTED) {
                STATUS_OK
            } else {
                mServiceBound =
                    context.bindService(buildServiceIntent(), mServiceConnectionCallback, Context.BIND_AUTO_CREATE)
                if (mServiceBound) {
                    STATUS_OK
                } else {
                    UCode.NOT_FOUND.buildStatus("Service is not found")
                }
            }
        } catch (e: Exception) {
            mServiceBound = false
            e.toUStatus()
        }.also {
            if (it.isVerboseLoggable()) {
                Log.println(it.verboseOrError(), mTag, status("bindService", it))
            }
        }
    }

    private fun unbindService() {
        if (mServiceBound) {
            context.unbindService(mServiceConnectionCallback)
            mServiceBound = false
            logV(mVerboseLoggable, mTag, join(Key.MESSAGE, "unbindService", STATUS_OK))
        }
    }

    private fun calculateRebindDelaySeconds(): Long {
        val exponent = mRebindBackoffExponent
        if (mRebindBackoffExponent < REBIND_BACKOFF_EXPONENT_MAX) {
            mRebindBackoffExponent++
        }
        return ((REBIND_BACKOFF_BASE.toDouble().pow(exponent.toDouble()))).toLong()
    }

    private fun registerClient(service: IUBus): UStatus {
        return try {
            service.registerClient(
                context.packageName,
                ParcelableUEntity(entity),
                mClientToken,
                0,
                mServiceListener
            ).getWrapped()
        } catch (e: Exception) {
            e.toUStatus()
        }.also { status ->
            if (status.isDebugLoggable()) {
                Log.println(status.debugOrError(), mTag, status("registerClient", status))
            }
        }
    }

    private fun unregisterClient(service: IUBus): UStatus {
        return try {
            service.unregisterClient(mClientToken).getWrapped()
        } catch (e: Exception) {
            e.toUStatus()
        }.also { status ->
            if (status.isDebugLoggable()) {
                Log.println(status.debugOrError(), mTag, status("unregisterClient", status))
            }
        }
    }

    fun send(message: UMessage): UStatus {
        return try {
            getServiceOrThrow().send(ParcelableUMessage(message), mClientToken).getWrapped()
        } catch (e: Exception) {
            e.toUStatus()
        }.also { status ->
            if (status.isVerboseLoggable()) {
                Log.println(status.verboseOrError(), mTag, status("send", status, Key.MESSAGE, message.stringify()))
            }
        }
    }

    fun enableDispatching(uri: UUri): UStatus {
        return try {
            getServiceOrThrow().enableDispatching(ParcelableUUri(uri), 0, mClientToken).getWrapped()
        } catch (e: Exception) {
            e.toUStatus()
        }.also { status ->
            if (status.isDebugLoggable()) {
                Log.println(status.debugOrError(), mTag, status("enableDispatching", status, Key.URI, uri.stringify()))
            }
        }
    }

    fun disableDispatching(uri: UUri): UStatus {
        return try {
            getServiceOrThrow().disableDispatching(ParcelableUUri(uri), 0, mClientToken).getWrapped()
        } catch (e: java.lang.Exception) {
            e.toUStatus()
        }.also { status ->
            if (status.isDebugLoggable()) {
                Log.println(status.debugOrError(), mTag, status("disableDispatching", status, Key.URI, uri.stringify()))
            }
        }
    }

    fun disableDispatchingQuietly(uri: UUri) {
        disableDispatching(uri)
    }

    fun getLastMessage(topic: UUri): UMessage? {
        return try {
            val bundle = getServiceOrThrow()
                .pull(ParcelableUUri(topic), 1, 0, mClientToken)
            if (bundle != null && bundle.isNotEmpty()) bundle[0].getWrapped() else null
        } catch (e: java.lang.Exception) {
            Log.e(mTag, status("getLastMessage", e.toUStatus(), Key.URI, topic.stringify()))
            null
        }
    }

    private fun UStatus.isDebugLoggable(): Boolean {
        return mDebugLoggable || !this.isOk()
    }

    private fun UStatus.isVerboseLoggable(): Boolean {
        return mVerboseLoggable || !this.isOk()
    }

    fun setLoggable(level: Int) {
        mDebugLoggable = level <= Log.DEBUG
        mVerboseLoggable = level <= Log.VERBOSE
    }

    private fun UStatus.debugOrError(): Int {
        return if (this.isOk()) Log.DEBUG else Log.ERROR
    }

    private fun UStatus.verboseOrError(): Int {
        return if (this.isOk()) Log.VERBOSE else Log.ERROR
    }

    sealed class ConnectionStatus {
        data object CONNECTING : ConnectionStatus()
        data object CONNECTED : ConnectionStatus()
        data class DISCONNECTED(var reason: UStatus? = null) : ConnectionStatus()
    }

    companion object {
        const val ACTION_BIND_UBUS = "uprotocol.action.BIND_UBUS"
        private const val REBIND_BACKOFF_EXPONENT_MAX = 5
        private const val REBIND_BACKOFF_BASE = 2

        fun create(
            context: Context,
            entity: UEntity,
            dispatcher: CoroutineDispatcher,
            connectionCallback: ConnectionCallback,
            listener: UListener
        ): UBusManager {
            return UBusManager(context, entity, dispatcher, connectionCallback, listener)
        }
    }
}
