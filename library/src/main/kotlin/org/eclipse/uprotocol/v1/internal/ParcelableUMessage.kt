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
package org.eclipse.uprotocol.v1.internal

import android.os.Parcel
import com.google.protobuf.InvalidProtocolBufferException
import org.eclipse.uprotocol.v1.UMessage

/**
 * A parcelable wrapper for [UMessage].
 */
class ParcelableUMessage : ParcelableMessage<UMessage> {
    @Suppress("unused")
    internal constructor(parcel: Parcel) : super(parcel)
    constructor(message: UMessage) : super(message)

    @Throws(InvalidProtocolBufferException::class)
    override fun parse(data: ByteArray): UMessage {
        return UMessage.parseFrom(data)
    }

    companion object {
        @JvmField
        val CREATOR = parcelableCreatorOf<ParcelableUMessage>()
    }
}
