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
import org.eclipse.uprotocol.rpc.CallOptions
import org.eclipse.uprotocol.rpc.CallOptions.Companion.callOptions
import org.eclipse.uprotocol.uuid.factory.UUIDV8
import org.eclipse.uprotocol.v1.UAttributes
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UMessageType
import org.eclipse.uprotocol.v1.UPayload
import org.eclipse.uprotocol.v1.UPriority
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUID
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.forNotification
import org.eclipse.uprotocol.v1.forPublication
import org.eclipse.uprotocol.v1.forRequest
import org.eclipse.uprotocol.v1.forResponse
import org.eclipse.uprotocol.v1.forRpcRequest
import org.eclipse.uprotocol.v1.forRpcResponse
import org.eclipse.uprotocol.v1.packToAny
import org.eclipse.uprotocol.v1.uAttributes
import org.eclipse.uprotocol.v1.uAuthority
import org.eclipse.uprotocol.v1.uEntity
import org.eclipse.uprotocol.v1.uMessage
import org.eclipse.uprotocol.v1.uPayload
import org.eclipse.uprotocol.v1.uResource
import org.eclipse.uprotocol.v1.uUri
import org.junit.Assert.assertEquals

open class TestBase {
    protected val authorityRemote = uAuthority {
        name = "cloud"
    }
    protected val testService = uEntity {
        name = "test.service"
        versionMajor = 1
    }

    protected val testClient = uEntity {
        name = "test.client"
        versionMajor = 1
    }

    protected val testResource = uResource {
        name = "resource"
        instance = "main"
        message = "State"
    }
    protected val testResource2 = uResource {
        name = "resource2"
        instance = "main2"
        message = "State2"
    }

    protected val testResourceUri = uUri {
        entity = testService
        resource = testResource
    }
    protected val testResource2Uri = uUri {
        entity = testService
        resource = testResource2
    }
    protected val testMethodUri: UUri = uUri {
        entity = testService
        resource = uResource {
            forRpcRequest("method")
        }
    }
    protected val testMethod2Uri: UUri = uUri {
        entity = testService
        resource = uResource {
            forRpcRequest("method2")
        }
    }
    protected val testResponseUri: UUri = uUri {
        entity = testClient
        resource = uResource {
            forRpcResponse()
        }
    }
    protected val testResourceUriRemote = uUri {
        authority = authorityRemote
        entity = testService
        resource = testResource
    }
    protected val testClientUri = uUri {
        entity = testClient
    }
    protected val testServiceUri = uUri {
        entity = testService
    }
    protected val testId = createId()
    protected val testId2 = createId()

    protected val testCallOptions: CallOptions = callOptions {
        timeout = TTL
        token = TOKEN
    }

    protected val testAttributes = uAttributes {
        id = testId
        type = UMessageType.UMESSAGE_TYPE_RESPONSE
        source = testMethodUri
        sink = testResponseUri
        priority = UPriority.UPRIORITY_CS4
        ttl = TTL
        permissionLevel = 5
        commstatus = UCode.DEADLINE_EXCEEDED_VALUE
        reqid = testId2
        token = TOKEN
    }

    protected val testPayload: UPayload = uPayload {
        packToAny(Empty.getDefaultInstance())
    }

    protected fun createId(): UUID {
        return UUIDV8()
    }

    protected fun buildPublishAttributes(source: UUri): UAttributes {
        return uAttributes {
            forPublication(source, UPriority.UPRIORITY_CS0)
        }
    }

    protected fun buildRequestAttributes(responseUri: UUri, methodUri: UUri): UAttributes {
        return uAttributes {
            forRequest(responseUri, methodUri, UPriority.UPRIORITY_CS4, TTL)
        }
    }

    protected fun newNotificationAttributes(source: UUri, sink: UUri): UAttributes {
        return uAttributes {
            forNotification(source, sink, UPriority.UPRIORITY_CS0)
        }
    }

    protected fun newPublishAttributes(source: UUri): UAttributes {
        return uAttributes {
            forPublication(source, UPriority.UPRIORITY_CS0)
        }
    }

    protected fun buildResponseAttributes(
        methodUri: UUri,
        responseUri: UUri,
        requestId: UUID
    ): UAttributes {
        return uAttributes {
            forResponse(
                methodUri,
                responseUri,
                UPriority.UPRIORITY_CS4,
                requestId
            )
        }
    }

    protected fun buildMessage(payload: UPayload?, attributes: UAttributes?): UMessage {
        return uMessage {
            payload?.let { this.payload = it }
            attributes?.let { this.attributes = it }
        }
    }

    protected fun assertStatus(code: UCode, status: UStatus) {
        assertEquals(code, status.code)
    }

    companion object {
        const val TTL = 1000
        const val DELAY_MS: Long = 100
        const val TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG" +
                "4gU21pdGgiLCJpYXQiOjE1MTYyMzkwMjJ9.Q_w2AVguPRU2KskCXwR7ZHl09TQXEntfEA8Jj2_Jyew"
    }
}
