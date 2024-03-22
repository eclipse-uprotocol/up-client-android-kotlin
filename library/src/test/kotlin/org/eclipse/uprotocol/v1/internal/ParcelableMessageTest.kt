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
import android.os.Parcelable.Creator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.protobuf.BoolValue
import com.google.protobuf.Int32Value
import com.google.protobuf.InvalidProtocolBufferException
import org.eclipse.uprotocol.TestBase
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.Array
import kotlin.ByteArray
import kotlin.Int
import kotlin.Throws
import kotlin.arrayOfNulls
import kotlin.byteArrayOf

@RunWith(AndroidJUnit4::class)
class ParcelableMessageTest : TestBase() {
    private lateinit var mParcel: Parcel

    class ParcelableBoolValue : ParcelableMessage<BoolValue> {
        internal constructor(`in`: Parcel) : super(`in`)
        constructor(value: BoolValue) : super(value)

        @Throws(InvalidProtocolBufferException::class)
        override fun parse(data: ByteArray): BoolValue {
            return BoolValue.parseFrom(data)
        }

        companion object {
            val CREATOR: Creator<ParcelableBoolValue> = object : Creator<ParcelableBoolValue> {
                override fun createFromParcel(`in`: Parcel): ParcelableBoolValue {
                    return ParcelableBoolValue(`in`)
                }

                override fun newArray(size: Int): Array<ParcelableBoolValue?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    class ParcelableInt32Value : ParcelableMessage<Int32Value> {
        internal constructor(`in`: Parcel) : super(`in`)
        constructor(value: Int32Value) : super(value)

        @Throws(InvalidProtocolBufferException::class)
        override fun parse(data: ByteArray): Int32Value {
            return Int32Value.parseFrom(data)
        }

        companion object {
            val CREATOR: Creator<ParcelableInt32Value> = object : Creator<ParcelableInt32Value> {
                override fun createFromParcel(`in`: Parcel): ParcelableInt32Value {
                    return ParcelableInt32Value(`in`)
                }

                override fun newArray(size: Int): Array<ParcelableInt32Value?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    @Before
    fun setUp() {
        mParcel = Parcel.obtain()
    }

    @After
    fun tearDown() {
        mParcel.recycle()
    }

    @Test
    fun testConstructorParcel() {
        PARCELABLE_VALUE.writeToParcel(mParcel, 0)
        mParcel.setDataPosition(0)
        assertEquals(
            VALUE,
            ParcelableBoolValue(
                mParcel
            ).getWrapped()
        )
        assertEndPosition(mParcel)
    }

    @Test
    fun testConstructorParcelSequence() {
        ParcelableBoolValue(VALUE).writeToParcel(mParcel, 0)
        ParcelableInt32Value(VALUE2).writeToParcel(mParcel, 0)
        mParcel.setDataPosition(0)
        assertEquals(
            VALUE,
            ParcelableBoolValue(
                mParcel
            ).getWrapped()
        )
        assertEquals(
            VALUE2,
            ParcelableInt32Value(
                mParcel
            ).getWrapped()
        )
        assertEndPosition(mParcel)
    }

    @Test
    fun testConstructorParcelWrongSize() {
        mParcel.writeInt(-1) // Wrong size
        mParcel.writeByteArray(VALUE.toByteArray())
        mParcel.setDataPosition(0)
        Assert.assertThrows(
            BadParcelableException::class.java
        ) { ParcelableBoolValue(mParcel) }
    }

    @Test
    fun testConstructorParcelWrongData() {
        mParcel.writeInt(3)
        mParcel.writeByteArray(byteArrayOf(1, 2, 3))
        mParcel.setDataPosition(0)
        Assert.assertThrows(
            BadParcelableException::class.java
        ) { ParcelableBoolValue(mParcel) }
    }

    @Test
    fun testConstructorMessage() {
        assertEquals(VALUE, ParcelableBoolValue(VALUE).getWrapped())
    }

    @Test
    fun testWriteToParcel() {
        val data = VALUE.toByteArray()
        val size = data.size
        PARCELABLE_VALUE.writeToParcel(mParcel, 0)
        mParcel.setDataPosition(0)
        assertEquals(size.toLong(), mParcel.readInt().toLong())
        val actualData = ByteArray(size)
        mParcel.readByteArray(actualData)
        Assert.assertArrayEquals(data, actualData)
        assertEndPosition(mParcel)
    }

    @Test
    fun testGetWrapped() {
        assertEquals(VALUE, PARCELABLE_VALUE.getWrapped())
    }

    @Test
    fun testDescribeContents() {
        assertEquals(0, PARCELABLE_VALUE.describeContents().toLong())
    }

    @Test
    fun testHashCode() {
        val value1 = PARCELABLE_VALUE
        val value2 = ParcelableBoolValue(VALUE)
        val value3 = ParcelableBoolValue(BoolValue.newBuilder().setValue(false).build())
        assertEquals(value1.hashCode().toLong(), value2.hashCode().toLong())
        assertNotEquals(value1.hashCode().toLong(), value3.hashCode().toLong())
    }

    @Test
    fun testEquals() {
        val value1 = PARCELABLE_VALUE
        val value2 = ParcelableBoolValue(VALUE)
        val value3 = ParcelableBoolValue(BoolValue.newBuilder().setValue(false).build())
        assertEquals(value1, value1)
        assertEquals(value1, value2)
        assertNotEquals(value1, value3)
        assertNotNull(value1)
        assertNotEquals(true, value1)
    }

    companion object {
        private val VALUE = BoolValue.newBuilder().setValue(true).build()
        private val VALUE2 = Int32Value.newBuilder().setValue(1).build()
        private val PARCELABLE_VALUE = ParcelableBoolValue(VALUE)
        private fun assertEndPosition(parcel: Parcel) {
            assertEquals(parcel.dataPosition().toLong(), parcel.dataSize().toLong())
        }
    }
}
