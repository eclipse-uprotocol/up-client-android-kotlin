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

import kotlinx.coroutines.flow.Flow
import org.eclipse.uprotocol.rpc.CallOptions
import org.eclipse.uprotocol.rpc.RpcClient
import org.eclipse.uprotocol.rpc.toResponse
import org.eclipse.uprotocol.v1.UAuthority
import org.eclipse.uprotocol.v1.UEntity
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.UUriBatch
import org.eclipse.uprotocol.v1.forRpcRequest
import org.eclipse.uprotocol.v1.packToAny
import org.eclipse.uprotocol.v1.uEntity
import org.eclipse.uprotocol.v1.uPayload
import org.eclipse.uprotocol.v1.uResource
import org.eclipse.uprotocol.v1.uUri

object UTwin {
    val SERVICE: UEntity = uEntity {
        name = "core.utwin"
        versionMajor = 1
    }
    const val METHOD_GET_LAST_MESSAGES = "GetLastMessages"
    const val METHOD_SET_LAST_MESSAGE = "SetLastMessage"

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

        fun getLastMessages(request: UUriBatch): Flow<GetLastMessagesResponse> {
            return proxy.invokeMethod(
                buildUri(METHOD_GET_LAST_MESSAGES),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }

        fun setLastMessage(request: UMessage): Flow<UStatus> {
            return proxy.invokeMethod(
                buildUri(METHOD_SET_LAST_MESSAGE),
                uPayload {
                    packToAny(request)
                },
                options
            ).toResponse()
        }
    }
}
