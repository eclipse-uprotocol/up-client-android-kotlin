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
import android.content.pm.PackageInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.uprotocol.client.BuildConfig
import org.eclipse.uprotocol.common.UStatusException
import org.eclipse.uprotocol.common.util.STATUS_OK
import org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument
import org.eclipse.uprotocol.common.util.UStatusUtils.checkArgumentPositive
import org.eclipse.uprotocol.common.util.isOk
import org.eclipse.uprotocol.common.util.log.Formatter.join
import org.eclipse.uprotocol.common.util.log.Formatter.stringify
import org.eclipse.uprotocol.common.util.log.Formatter.tag
import org.eclipse.uprotocol.common.util.log.Key.EVENT
import org.eclipse.uprotocol.common.util.log.Key.MESSAGE
import org.eclipse.uprotocol.common.util.log.Key.PACKAGE
import org.eclipse.uprotocol.common.util.log.Key.REASON
import org.eclipse.uprotocol.common.util.log.Key.VERSION
import org.eclipse.uprotocol.common.util.log.logV
import org.eclipse.uprotocol.common.util.toUCode
import org.eclipse.uprotocol.common.util.toUStatus
import org.eclipse.uprotocol.core.ubus.ConnectionCallback
import org.eclipse.uprotocol.core.ubus.UBusManager
import org.eclipse.uprotocol.rpc.CallOptions
import org.eclipse.uprotocol.rpc.RpcClient
import org.eclipse.uprotocol.rpc.RpcServer
import org.eclipse.uprotocol.rpc.URpcListener
import org.eclipse.uprotocol.transport.UListener
import org.eclipse.uprotocol.transport.UTransport
import org.eclipse.uprotocol.transport.validate.UAttributesValidator
import org.eclipse.uprotocol.transport.validate.UAttributesValidator.Companion.getValidator
import org.eclipse.uprotocol.uri.validator.isNotEmpty
import org.eclipse.uprotocol.uri.validator.isRpcMethod
import org.eclipse.uprotocol.v1.UAttributes
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UEntity
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UMessageType
import org.eclipse.uprotocol.v1.UPayload
import org.eclipse.uprotocol.v1.UPriority
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUID
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.forRequest
import org.eclipse.uprotocol.v1.forResponse
import org.eclipse.uprotocol.v1.forRpcResponse
import org.eclipse.uprotocol.v1.uAttributes
import org.eclipse.uprotocol.v1.uEntity
import org.eclipse.uprotocol.v1.uMessage
import org.eclipse.uprotocol.v1.uResource
import org.eclipse.uprotocol.v1.uUri
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * The uProtocol client API layer which enables communication over the uBus,
 * offering basic functionalities for establishing connections, sending and
 * receiving messages, and invoking RPC methods.
 * @param entity The [UEntity] associated with this instance.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class UPClient private constructor(
    context: Context,
    val entity: UEntity,
    private val dispatcher: CoroutineDispatcher,
    private val serviceLifecycleListener: ServiceLifecycleListener
) : UTransport, RpcServer, RpcClient {

    /**
     * The [UUri] associated with this instance.
     */
    val uri: UUri = uUri {
        entity = this@UPClient.entity
    }
    private val mResponseUri: UUri = uUri {
        entity = this@UPClient.entity
        resource = uResource {
            forRpcResponse()
        }
    }
    private val mTag: String = tag(entity.name, TAG_GROUP)
    private val mVerboseLoggable = Log.isLoggable(mTag, Log.VERBOSE).also {
        logV(it, mTag, join(PACKAGE, BuildConfig.LIBRARY_PACKAGE_NAME, VERSION, BuildConfig.VERSION_NAME))
    }

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val mConnectionCallback: ConnectionCallback = object : ConnectionCallback {
        override fun onConnected() {
            scope.launch {
                renewRegistration()
                serviceLifecycleListener.onLifecycleChanged(this@UPClient, true)
            }
        }

        override fun onDisconnected() {
            scope.launch {
                release()
                serviceLifecycleListener.onLifecycleChanged(this@UPClient, false)
            }
        }

        override fun onConnectionInterrupted() {
            scope.launch {
                setRegistrationExpired()
                serviceLifecycleListener.onLifecycleChanged(this@UPClient, false)
            }
        }
    }

    private val mListener: UListener = object : UListener {
        override fun onReceive(message: UMessage) {
            handleMessage(message)
        }
    }

    private val mUBusManager: UBusManager =
        UBusManager.create(context, entity, dispatcher, mConnectionCallback, mListener)

    private var mRegistrationExpired = false

    private val registrationMutex = Mutex()

    // Request from UClients which make Rpc calls to the server
    private val mRequestsMap = ConcurrentHashMap<UUID, UResponseListener>()

    // RpcRequestListeners from UServers
    private val mRequestListenersMap = ConcurrentHashMap<UUri, URpcListener>()

    // Topic Listeners from UClients
    private val mListenersMap = ConcurrentHashMap<UUri, Set<UListener>>()

    /**
     * Asynchronously invoke a method (send an RPC request) and receive a response.
     *
     * @param methodUri      A [UUri] associated with a method.
     * @param requestPayload A [UPayload] to be supplied with a request.
     * @param options        [CallOptions] containing various invocation parameters.
     * @return A [Flow]<[UMessage]> used by a caller to receive a response.
     */
    override fun invokeMethod(
        methodUri: UUri,
        requestPayload: UPayload,
        options: CallOptions
    ): Flow<UMessage> {
        try {
            checkArgument(methodUri.isNotEmpty(), errorMessage = "Method URI is empty")
            val timeout =
                checkArgumentPositive(options.timeout, errorMessage = "Timeout is not positive")
            val requestMessage = uMessage {
                payload = requestPayload
                attributes = uAttributes {
                    forRequest(
                        mResponseUri,
                        methodUri,
                        UPriority.UPRIORITY_CS4,
                        timeout
                    )
                    token = options.token
                }
            }
            return callbackFlow {
                mRequestsMap.compute(requestMessage.attributes.id) { _, currentRequest ->
                    checkArgument(currentRequest == null, UCode.ABORTED, "Duplicated request found")
                    val status = this@UPClient.send(requestMessage)
                    if (status.isOk()) {
                        object : UResponseListener {
                            override fun onReceive(message: UMessage) {
                                trySend(message)
                            }
                            override fun onClose(exception: Exception?) {
                                close(exception)
                            }
                        }
                    } else {
                        throw UStatusException(status)
                    }
                }
                awaitClose {
                    // on close action is handled in onCompletion as timeout would cancel the awaitClose
                }
            }.onCompletion {
                mRequestsMap.remove(requestMessage.attributes.id)
            }.timeout(requestMessage.attributes.ttl.milliseconds).flowOn(dispatcher)
            // timeout operator cannot be called directly after callbackflow, otherwise close exception will be swallowed
            // https://github.com/Kotlin/kotlinx.coroutines/issues/4071
        } catch (e: Exception) {
            return flow { throw e }
        }
    }

    /**
     * Register a listener for a particular method URI to be notified when requests are sent against said method.
     *
     * <p>Note: Only one listener is allowed to be registered per method URI.
     *
     * @param method A [UUri] associated with a method.
     * @param listener  A [URpcListener] which needs to be registered.
     * @return A {@code Status} which contains a result code and other details.
     */
    override fun registerRpcListener(method: UUri, listener: URpcListener): UStatus {
        return try {
            checkArgument(method.isRpcMethod(), errorMessage = "URI doesn't match the RPC format")
            mRequestListenersMap.compute(method) { _, currentListener ->
                if (currentListener === listener) {
                    currentListener
                } else if (currentListener != null) {
                    throw UStatusException(UCode.ALREADY_EXISTS, "Listener is already registered")
                } else {
                    val status = mUBusManager.enableDispatching(method)
                    if (status.isOk()) {
                        listener
                    } else {
                        throw UStatusException(status)
                    }
                }
            }
            STATUS_OK
        } catch (e: Exception) {
            e.toUStatus()
        }
    }

    /**
     * Unregister a listener for a particular method URI.
     *
     * @param method A [UUri] associated with a method.
     * @param listener  A [URpcListener] used when registration.
     * @return A {@code Status} which contains a result code and other details.
     */
    override fun unregisterRpcListener(method: UUri, listener: URpcListener): UStatus {
        return try {
            checkArgument(method.isRpcMethod(), errorMessage = "URI doesn't match the RPC format")
            if (mRequestListenersMap.remove(method, listener)) {
                mUBusManager.disableDispatchingQuietly(method)
            }
            STATUS_OK
        } catch (e: Exception) {
            e.toUStatus()
        }
    }

    /**
     * Unregister a listener from all method URIs.
     *
     * If this listener wasn't registered, nothing will happen.
     *
     * @param listener A [URpcListener] which needs to be unregistered.
     * @return A [UStatus] which contains a result code and other details.
     */
    fun unregisterRpcListener(listener: URpcListener): UStatus {
        return try {
            mRequestListenersMap.keys.removeIf { methodUri: UUri ->
                if (mRequestListenersMap[methodUri] !== listener) {
                    false
                } else {
                    mUBusManager.disableDispatchingQuietly(methodUri)
                    true
                }
            }
            STATUS_OK
        } catch (e: Exception) {
            e.toUStatus()
        }
    }

    /**
     * Register a listener for a particular topic to be notified when a message with that topic is received.
     *
     * <p>In order to start receiving published data a client needs to subscribe to the topic.
     *
     * @param topic    A {@link UUri} associated with a topic.
     * @param listener A {@link UListener} which needs to be registered.
     * @return A {@link UStatus} which contains a result code and other details.
     */
    override fun registerListener(topic: UUri, listener: UListener): UStatus {
        return try {
            checkArgument(topic.isNotEmpty(), errorMessage = "Topic is empty")
            checkArgument(!topic.isRpcMethod(), errorMessage = "Topic matches the RPC format")
            mListenersMap.compute(topic) { _, listeners ->
                val newListeners = (listeners ?: HashSet()).toMutableSet()
                if (newListeners.isEmpty()) {
                    val status = mUBusManager.enableDispatching(topic)
                    if (!status.isOk()) {
                        throw UStatusException(status)
                    }
                }
                if (newListeners.add(listener) && newListeners.size > 1) {
                    scope.launch {
                        val event = mUBusManager.getLastMessage(topic)
                        if (event != null) {
                            listener.onReceive(event)
                        }
                    }
                }
                newListeners
            }
            STATUS_OK
        } catch (e: Exception) {
            return e.toUStatus()
        }
    }

    /**
     * Unregister a listener from a particular topic.
     *
     * <p>If this listener wasn't registered, nothing will happen.
     *
     * @param topic    A {@link UUri} associated with a topic.
     * @param listener A {@link UListener} which needs to be unregistered.
     * @return A {@link UStatus} which contains a result code and other details.
     */
    override fun unregisterListener(topic: UUri, listener: UListener): UStatus {
        return try {
            checkArgument(topic.isNotEmpty(), errorMessage = "Topic is empty")
            checkArgument(!topic.isRpcMethod(), errorMessage = "Topic matches the RPC format")
            if (unregisterListenerIsLast(topic, listener)) {
                mListenersMap.remove(topic)
            }
            STATUS_OK
        } catch (e: java.lang.Exception) {
            e.toUStatus()
        }
    }

    /**
     * Unregister a listener from all topics.
     *
     *
     * If this listener wasn't registered, nothing will happen.
     *
     * @param listener A [UListener] which needs to be unregistered.
     * @return A [UStatus] which contains a result code and other details.
     */
    fun unregisterListener(listener: UListener): UStatus {
        return try {
            mListenersMap.keys.removeIf { topic: UUri ->
                unregisterListenerIsLast(topic, listener)
            }
            STATUS_OK
        } catch (e: java.lang.Exception) {
            e.toUStatus()
        }
    }

    private fun unregisterListenerIsLast(topic: UUri, listener: UListener): Boolean {
        var isLast = false
        mListenersMap.compute(topic) { _, listeners ->
            listeners?.toMutableSet()?.let { newListeners ->
                if (newListeners.contains(listener)) {
                    newListeners.remove(listener)
                }
                if (newListeners.isEmpty()) {
                    mUBusManager.disableDispatchingQuietly(topic)
                    isLast = true
                }
                newListeners
            }
        }
        return isLast
    }

    /**
     * Transmit a message.
     *
     * @param message A [UMessage] to be sent.
     * @return A [UStatus] which contains a result code and other details.
     */
    override fun send(message: UMessage): UStatus {
        return mUBusManager.send(message)
    }

    /**
     * Connect to the uBus.
     *
     * <p>Requires [#PERMISSION_ACCESS_UBUS] permission to access this API.
     *
     * <p>An instance connected with this method should be disconnected from the uBus by calling
     * [#disconnect()] before the passed {@link Context} is released.
     *
     * @return The [UStatus] of the connection status.
     */
    suspend fun connect(): UStatus {
        return mUBusManager.connect()
    }

    /**
     * Disconnect from the uBus.
     *
     * <p>All previously registered listeners will be automatically unregistered.
     *
     * @return The [UStatus] of disconnection status.
     */
    suspend fun disconnect(): UStatus {
        return mUBusManager.disconnect()
    }

    /**
     * Check whether this instance is disconnected from the uBus or not.
     *
     * @return <code>true</code> if it is disconnected.
     */
    fun isDisconnected(): Boolean {
        return mUBusManager.isDisconnected()
    }

    /**
     * Check whether this instance is already connecting to the uBus or not.
     *
     * @return <code>true</code> if it is connecting.
     */
    fun isConnecting(): Boolean {
        return mUBusManager.isConnecting()
    }

    /**
     * Check whether the uBus is connected or not. This will return <code>false</code> if it
     * is still connecting.
     *
     * @return <code>true</code> if is is connected.
     */
    fun isConnected(): Boolean {
        return mUBusManager.isConnected()
    }

    private suspend fun setRegistrationExpired() {
        registrationMutex.withLock {
            mRegistrationExpired = true
        }
    }

    private suspend fun renewRegistration() {
        registrationMutex.withLock {
            if (mRegistrationExpired) {
                mRequestListenersMap.keys.forEach { uri ->
                    mUBusManager.enableDispatching(uri)
                }
                mListenersMap.keys.forEach { uri ->
                    mUBusManager.enableDispatching(uri)
                }
                mRegistrationExpired = false
            }
        }
    }

    private suspend fun release() {
        registrationMutex.withLock {
            mRequestsMap.values.forEach { listener ->
                listener.onClose(UStatusException(UCode.CANCELLED, "Service is disconnected"))
            }
            mRequestsMap.clear()
            mRequestListenersMap.clear()
            mListenersMap.clear()
            mRegistrationExpired = false
        }
    }

    private fun UMessage.logForInvalidMessage(reason: String) {
        Log.w(mTag, join(EVENT, MESSAGE_DROPPED, MESSAGE, stringify(), REASON, reason))
    }

    private fun handleMessage(message: UMessage) {
        logV(mVerboseLoggable, mTag, join(EVENT, MESSAGE_RECEIVED, MESSAGE, message.stringify()))
        val attributes = message.attributes
        val validator: UAttributesValidator = getValidator(attributes)
        val result = validator.validate(attributes)
        if (result.isFailure()) {
            message.logForInvalidMessage(result.getMessage())
            return
        }
        if (validator.isExpired(attributes)) { // Do we need to check expiration? Should be done by the service...
            message.logForInvalidMessage("Expired")
            return
        }
        when (attributes.type) {
            UMessageType.UMESSAGE_TYPE_PUBLISH -> handleGenericMessage(message)
            UMessageType.UMESSAGE_TYPE_REQUEST -> handleRequestMessage(message)
            UMessageType.UMESSAGE_TYPE_RESPONSE -> handleResponseMessage(message)
            else -> message.logForInvalidMessage("Unknown type")
        }
    }

    private fun handleGenericMessage(message: UMessage) {
        if (message.attributes.hasSink() && message.attributes.sink.entity != uri.entity) {
            message.logForInvalidMessage("Wrong sink")
            return
        }
        scope.launch {
            val topic = message.attributes.source
            mListenersMap[topic]?.let { listeners ->
                if (listeners.isEmpty()) {
                    message.logForInvalidMessage("No listener")
                } else {
                    listeners.forEach { listener ->
                        listener.onReceive(message)
                    }
                }
            } ?: run {
                message.logForInvalidMessage("No listener")
            }
        }
    }

    private fun handleRequestMessage(requestMessage: UMessage) {
        scope.launch {
            val methodUri = requestMessage.attributes.sink
            mRequestListenersMap[methodUri]?.onReceive(
                requestMessage,
                buildServerResponseDeferred(requestMessage)
            ) ?: run {
                requestMessage.logForInvalidMessage("No listener")
            }
        }
    }

    private fun buildServerResponseDeferred(requestMessage: UMessage): CompletableDeferred<UPayload> {
        val deferred = CompletableDeferred<UPayload>()
        deferred.invokeOnCompletion { exception ->
            val requestAttributes = requestMessage.attributes
            val responseMessage = uMessage {
                exception ?: run {
                    payload = deferred.getCompleted()
                }
                attributes = uAttributes {
                    forResponse(
                        requestAttributes.sink,
                        requestAttributes.source,
                        requestAttributes.priority,
                        requestAttributes.id
                    )
                    exception?.let {
                        commstatus = exception.toUStatus().codeValue
                    }
                }
            }
            send(responseMessage)
        }
        return deferred
    }

    private fun handleResponseMessage(responseMessage: UMessage) {
        val responseAttributes: UAttributes = responseMessage.attributes
        val responseListener = mRequestsMap.remove(responseAttributes.reqid) ?: return
        if (responseAttributes.hasCommstatus()) {
            val code = responseAttributes.commstatus.toUCode()
            if (code != UCode.OK) {
                responseListener.onClose(UStatusException(code, "Communication error [$code]"))
                return
            }
        }
        responseListener.onReceive(responseMessage)
        responseListener.onClose()
    }

    /**
     * The callback to notify the lifecycle of the uBus.
     *
     *
     * Access to the uBus should happen
     * after [ServiceLifecycleListener.onLifecycleChanged] call with
     * `ready` set `true`.
     */
    interface ServiceLifecycleListener {
        /**
         * The uBus has gone through status change.
         *
         * @param client A [UPClient] object that was originally associated with this
         * listener from [.create] call.
         * @param ready  When `true`, the uBus is ready and all accesses are ok.
         * Otherwise it has crashed or killed and will be restarted.
         */
        fun onLifecycleChanged(client: UPClient, ready: Boolean)
    }

    private interface UResponseListener : UListener {
        fun onClose(exception: Exception? = null)
    }

    companion object {
        /**
         * The logging group tag used by this class and all sub-components.
         */
        const val TAG_GROUP = "uPClient"

        /**
         * The permission necessary to connect to the uBus.
         */
        const val PERMISSION_ACCESS_UBUS = "uprotocol.permission.ACCESS_UBUS"

        /**
         * The name of the `meta-data` element that must be present on an
         * `application` or `service` element in a manifest to specify
         * the name of a client (uEntity).
         */
        const val META_DATA_ENTITY_NAME = "uprotocol.entity.name"

        /**
         * The name of the `meta-data` element that must be present on an
         * `application` or `service` element in a manifest to specify
         * the major version of a client (uEntity).
         */
        const val META_DATA_ENTITY_VERSION = "uprotocol.entity.version"

        /**
         * The name of the `meta-data` element that may be present on an
         * `application` or `service` element in a manifest to specify
         * the id of a client (uEntity).
         */
        const val META_DATA_ENTITY_ID = "uprotocol.entity.id"

        private const val MESSAGE_RECEIVED = "Message received"
        private const val MESSAGE_DROPPED = "Message dropped"

        /**
         * Create an instance for a specified uEntity.
         *
         * @param context  An application [Context]. This should not be `null`. If you are passing
         * [ContextWrapper], make sure that its base Context is non-null as well.
         * Otherwise it will throw [NullPointerException].
         * @param entity   A [UEntity] containing its name and major version, or `null` to use the
         * first found declaration under `application` or `service` element
         * in a manifest.
         * @param dispatcher  A [CoroutineDispatcher] on which callbacks should execute.
         * @param listener A [ServiceLifecycleListener] for monitoring the uBus lifecycle.
         * @return A [UPClient] instance.
         * @throws SecurityException If the caller does not have [.META_DATA_ENTITY_NAME] and
         * [.META_DATA_ENTITY_VERSION] `meta-data` elements declared in the manifest.
         */
        fun create(
            context: Context,
            entity: UEntity? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Main,
            listener: ServiceLifecycleListener
        ): UPClient {
            checkNonNullContext(context)
            return UPClient(
                context,
                checkContainsEntity(getPackageInfo(context), entity),
                dispatcher,
                listener
            )
        }

        private fun checkNonNullContext(context: Context) {
            if (context is ContextWrapper && context.baseContext == null) {
                throw NullPointerException("ContextWrapper with null base passed as Context")
            }
        }

        private fun getPackageInfo(context: Context): PackageInfo {
            return try {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SERVICES or PackageManager.GET_META_DATA
                )
            } catch (e: PackageManager.NameNotFoundException) {
                throw SecurityException(e.message, e)
            }
        }

        private fun checkContainsEntity(packageInfo: PackageInfo, entity: UEntity?): UEntity {
            return mutableListOf<PackageItemInfo?>().apply {
                add(packageInfo.applicationInfo)
                addAll(packageInfo.services.orEmpty())
            }.filterNotNull().firstNotNullOfOrNull { info ->
                val foundEntity = getEntity(info)
                if (entity != null) {
                    if (entity == foundEntity) entity else null
                } else {
                    foundEntity
                }
            } ?: throw SecurityException(
                "Missing or not matching '$META_DATA_ENTITY_NAME', '$META_DATA_ENTITY_VERSION' or '$META_DATA_ENTITY_ID' meta-data in manifest"
            )
        }

        private fun getEntity(info: PackageItemInfo): UEntity? {
            return info.metaData?.let { data ->
                val entityName = data.getString(META_DATA_ENTITY_NAME)
                val version = data.getInt(META_DATA_ENTITY_VERSION)
                val entityId = data.getInt(META_DATA_ENTITY_ID)
                if (!entityName.isNullOrEmpty() && version > 0) {
                    uEntity {
                        name = entityName
                        versionMajor = version
                        if (entityId > 0) {
                            id = entityId
                        }
                    }
                } else {
                    null
                }
            }
        }
    }
}
