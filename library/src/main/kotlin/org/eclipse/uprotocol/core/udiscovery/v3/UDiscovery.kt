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

object UDiscovery {
    val SERVICE: UEntity = uEntity {
        name = "core.udiscovery"
        versionMajor = 3
    }
    const val METHOD_LOOKUP_URI = "LookupUri"
    const val METHOD_UPDATE_NODE = "UpdateNode"
    const val METHOD_FIND_NODES = "FindNodes"
    const val METHOD_FIND_NODE_PROPERTIES = "FindNodeProperties"
    const val METHOD_DELETE_NODES = "DeleteNodes"
    const val METHOD_ADD_NODES = "AddNodes"
    const val METHOD_UPDATE_PROPERTY = "UpdateProperty"
    const val METHOD_REGISTER_FOR_NOTIFICATIONS = "RegisterForNotifications"
    const val METHOD_UNREGISTER_FOR_NOTIFICATIONS = "UnregisterForNotifications"
    const val METHOD_RESOLVE_URI = "ResolveUri"

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

        fun lookupUri(request: UUri): Flow<LookupUriResponse> {
            return proxy.invokeMethod(
                buildUri(METHOD_LOOKUP_URI),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun updateNode(request: UpdateNodeRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_UPDATE_NODE),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun findNodes(request: FindNodesRequest): Flow<FindNodesResponse> {
            return proxy.invokeMethod(
                buildUri(METHOD_FIND_NODES),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun findNodeProperties(request: FindNodePropertiesRequest): Flow<FindNodePropertiesResponse> {
            return proxy.invokeMethod(
                buildUri(METHOD_FIND_NODE_PROPERTIES),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun deleteNodes(request: DeleteNodesRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_DELETE_NODES),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun addNodes(request: AddNodesRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_ADD_NODES),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun updateProperty(request: UpdatePropertyRequest): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_UPDATE_PROPERTY),
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

        fun resolveUri(request: ResolveUriRequest): Flow<ResolveUriResponse> {
            return proxy.invokeMethod(
                buildUri(METHOD_RESOLVE_URI),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }
    }
}
