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
package org.eclipse.uprotocol.common.util.log

/**
 * The common keys to be used for logging key-value pairs.
 */
@Suppress("unused")
object Key {
    /**
     * Format a status key for a given method.
     *
     * @param method A name of a method.
     * @return A formatted status key, like "status.method".
     */
    fun forStatus(method: String): String = "$STATUS.$method"

    const val ACCESS = "access"
    const val ACTION = "action"
    const val ATTRIBUTES = "attributes"
    const val AUTHORITY = "authority"
    const val CLASS = "class"
    const val CLIENT = "client"
    const val CODE = "code"
    const val COMPONENT = "component"
    const val CONNECTION = "connection"
    const val COUNT = "count"
    const val DATA = "data"
    const val DEFAULT_LEVEL = "defaultLevel"
    const val DELAY = "delay"
    const val DUMP = "dump"
    const val DURATION = "duration"
    const val ENTITY = "entity"
    const val EVENT = "event"
    const val FAILURE = "failure"
    const val FILENAME = "filename"
    const val FORMAT = "format"
    const val ID = "id"
    const val INSTANCE = "instance"
    const val INTENT = "intent"
    const val IP = "ip"
    const val LATENCY = "latency"
    const val LEVEL = "level"
    const val LEVELS = "levels"
    const val MAJOR = "major"
    const val MESSAGE = "message"
    const val METHOD = "method"
    const val MINOR = "minor"
    const val MODE = "mode"
    const val NAME = "name"
    const val PACKAGE = "package"
    const val PATH = "path"
    const val PAYLOAD = "payload"
    const val PERCENTAGE = "percentage"
    const val PERMISSIONS = "permissions"
    const val PID = "pid"
    const val PRIORITY = "priority"
    const val REASON = "reason"
    const val REFERENCE = "reference"
    const val REQUEST = "request"
    const val REQUEST_ID = "requestId"
    const val RESOURCE = "resource"
    const val RESPONSE = "response"
    const val SCOPE = "scope"
    const val SERVER = "server"
    const val SERVICE = "service"
    const val SINK = "sink"
    const val SIZE = "size"
    const val SOURCE = "source"
    const val STATE = "state"
    const val STATUS = "status"
    const val SUBSCRIBER = "subscriber"
    const val SUBSCRIPTION = "subscription"
    const val TIME = "time"
    const val TIMEOUT = "timeout"
    const val TOKEN = "token"
    const val TOPIC = "topic"
    const val TRIGGER = "trigger"
    const val TTL = "ttl"
    const val TYPE = "type"
    const val UID = "uid"
    const val URI = "uri"
    const val USER = "user"
    const val VALUE = "value"
    const val VERSION = "version"
}
