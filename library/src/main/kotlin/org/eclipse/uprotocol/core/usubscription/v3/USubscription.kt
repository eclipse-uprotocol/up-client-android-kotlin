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
package org.eclipse.uprotocol.core.usubscription.v3

import kotlinx.coroutines.flow.Flow
import org.eclipse.uprotocol.rpc.CallOptions
import org.eclipse.uprotocol.rpc.RpcClient
import org.eclipse.uprotocol.rpc.toResponse
import org.eclipse.uprotocol.v1.UAuthority
import org.eclipse.uprotocol.v1.UEntity
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.forRpcRequest
import org.eclipse.uprotocol.v1.packToAny
import org.eclipse.uprotocol.v1.uEntity
import org.eclipse.uprotocol.v1.uPayload
import org.eclipse.uprotocol.v1.uResource
import org.eclipse.uprotocol.v1.uUri

object USubscription {
    val SERVICE: UEntity = uEntity {
        name = "core.usubscription"
        versionMajor = 3
    }
    const val METHOD_SUBSCRIBE = "Subscribe"
    const val METHOD_UNSUBSCRIBE = "Unsubscribe"
    const val METHOD_FETCH_SUBSCRIPTIONS = "FetchSubscriptions"
    const val METHOD_CREATE_TOPIC = "CreateTopic"
    const val METHOD_DEPRECATE_TOPIC = "DeprecateTopic"
    const val METHOD_REGISTER_FOR_NOTIFICATIONS = "RegisterForNotifications"
    const val METHOD_UNREGISTER_FOR_NOTIFICATIONS = "UnregisterForNotifications"
    const val METHOD_FETCH_SUBSCRIBERS = "FetchSubscribers"
    const val METHOD_RESET = "Reset"

    fun newStub(
        proxy: RpcClient,
        authority: UAuthority? = null,
        options: CallOptions = CallOptions.DEFAULT
    ): Stub {
        return Stub(proxy, authority, options)
    }

    class Stub internal constructor(
        private val proxy: RpcClient,
        val authority: UAuthority?,
        val options: CallOptions
    ) {
        private fun buildUri(method: String): UUri {
            return uUri {
                entity = SERVICE
                resource = uResource {
                    forRpcRequest(method)
                }
                this@Stub.authority?.let { authority = it }
            }
        }

        fun subscribe(request: SubscriptionRequest): Flow<SubscriptionResponse> {
            return proxy.invokeMethod(
                buildUri(METHOD_SUBSCRIBE),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun unsubscribe(request: UnsubscribeRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_UNSUBSCRIBE),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun fetchSubscriptions(request: FetchSubscriptionsRequest): Flow<FetchSubscriptionsResponse> {
            return proxy.invokeMethod(
                buildUri(METHOD_FETCH_SUBSCRIPTIONS),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun createTopic(request: CreateTopicRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_CREATE_TOPIC),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun deprecateTopic(request: DeprecateTopicRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_DEPRECATE_TOPIC),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun registerForNotifications(request: NotificationsRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_REGISTER_FOR_NOTIFICATIONS),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun unregisterForNotifications(request: NotificationsRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_UNREGISTER_FOR_NOTIFICATIONS),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun fetchSubscribers(request: FetchSubscribersRequest): Flow<FetchSubscribersResponse> {
            return proxy.invokeMethod(
                buildUri(METHOD_FETCH_SUBSCRIBERS),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun reset(request: ResetRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_RESET),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }
    }
}
