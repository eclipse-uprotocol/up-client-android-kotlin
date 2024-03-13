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

import com.google.protobuf.Empty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withTimeout
import org.eclipse.uprotocol.common.UStatusException
import org.eclipse.uprotocol.common.util.toUStatus
import org.eclipse.uprotocol.v1.UAttributes
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UPayload
import org.eclipse.uprotocol.v1.UPriority
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.forNotification
import org.eclipse.uprotocol.v1.forPublication
import org.eclipse.uprotocol.v1.forRpcRequest
import org.eclipse.uprotocol.v1.forRpcResponse
import org.eclipse.uprotocol.v1.packToAny
import org.eclipse.uprotocol.v1.uAttributes
import org.eclipse.uprotocol.v1.uEntity
import org.eclipse.uprotocol.v1.uMessage
import org.eclipse.uprotocol.v1.uPayload
import org.eclipse.uprotocol.v1.uResource
import org.eclipse.uprotocol.v1.uUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@ExperimentalCoroutinesApi
@FlowPreview
open class TestBase {

    protected val testPayload: UPayload = uPayload {
        packToAny(Empty.getDefaultInstance())
    }

    protected fun buildPublishAttributes(source: UUri): UAttributes {
        return uAttributes {
            forPublication(source, UPriority.UPRIORITY_CS0)
        }
    }

    protected fun buildNotificationAttributes(source: UUri, sink: UUri): UAttributes {
        return uAttributes {
            forNotification(source, sink, UPriority.UPRIORITY_CS0)
        }
    }

    protected fun buildMessage(payload: UPayload?, attributes: UAttributes?): UMessage {
        return uMessage {
            payload?.let { this.payload = it }
            attributes?.let { this.attributes = it }
        }
    }

    companion object {
        const val TTL = 5000
        const val CONNECTION_TIMEOUT_MS: Long = 3000
        const val DELAY_MS: Long = 100

        suspend fun connect(client: UPClient) {
            assertStatus(
                UCode.OK,
                getOrThrow(CONNECTION_TIMEOUT_MS) {
                    client.connect()
                }
            )
            assertTrue(client.isConnected())
        }

        suspend fun disconnect(client: UPClient) {
            assertStatus(
                UCode.OK,
                getOrThrow {
                    client.disconnect()
                }
            )
            assertTrue(client.isDisconnected())
        }

        fun assertStatus(code: UCode, status: UStatus) {
            assertEquals(code, status.code)
        }

        suspend fun <T> getOrThrow(timeout: Long = DELAY_MS, action: suspend () -> T): T {
            return try {
                withTimeout(timeout) {
                    action.invoke()
                }
            } catch (e: Exception) {
                throw UStatusException(e.toUStatus())
            }
        }

        suspend fun <T> Flow<T>.getOrThrow(timeout: Long = DELAY_MS): T {
            return try {
                timeout(timeout.milliseconds).first()
            } catch (e: Exception) {
                throw UStatusException(e.toUStatus())
            }
        }

        suspend fun <T> Flow<T>.getOrThrow(timeout: Int): T {
            return getOrThrow(timeout.toLong())
        }

        suspend fun <T> getOrThrow(timeout: Int, action: suspend () -> T): T {
            return getOrThrow(timeout.toLong(), action)
        }

        val SERVICE = uEntity {
            name = "client.test"
            versionMajor = 1
        }

        val RESOURCE = uResource {
            name = "resource"
            instance = "main"
            message = "State"
        }
        val RESOURCE_URI = uUri {
            entity = SERVICE
            resource = RESOURCE
        }
        val CLIENT = uEntity {
            name = "client.test"
            versionMajor = 1
        }

        val RESOURCE2 = uResource {
            name = "resource2"
            instance = "main2"
            message = "State2"
        }

        val RESOURCE2_URI = uUri {
            entity = SERVICE
            resource = RESOURCE2
        }
        val METHOD_URI: UUri = uUri {
            entity = SERVICE
            resource = uResource {
                forRpcRequest("method")
            }
        }
        val METHOD2_URI: UUri = uUri {
            entity = SERVICE
            resource = uResource {
                forRpcRequest("method2")
            }
        }
        val RESPONSE_URI: UUri = uUri {
            entity = CLIENT
            resource = uResource {
                forRpcResponse()
            }
        }
        val CLIENT_URI = uUri {
            entity = CLIENT
        }
    }
}
