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

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import java.util.Objects

/**
 * A parcelable wrapper base for protobuf messages.
 */
abstract class ParcelableMessage<T : Message> : Parcelable {
    private val mMessage: T

    protected constructor(parcel: Parcel) {
        mMessage = readFromParcel(parcel)
    }

    protected constructor(message: T) {
        mMessage = message
    }

    override fun writeToParcel(out: Parcel, flags: Int) {
        val data = mMessage.toByteArray()
        out.writeInt(data.size)
        out.writeByteArray(data)
    }

    private fun readFromParcel(parcel: Parcel): T {
        return try {
            val size = parcel.readInt()
            val data = ByteArray(size)
            parcel.readByteArray(data)
            parse(data)
        } catch (e: Exception) {
            throw BadParcelableException(e.message)
        }
    }

    @Throws(InvalidProtocolBufferException::class)
    protected abstract fun parse(data: ByteArray): T

    override fun describeContents(): Int {
        return 0
    }

    fun getWrapped(): T {
        return mMessage
    }

    override fun hashCode(): Int {
        return Objects.hashCode(mMessage)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return if (other !is ParcelableMessage<*>) {
            false
        } else {
            mMessage == other.mMessage
        }
    }
}
