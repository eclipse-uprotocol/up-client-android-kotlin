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
import android.os.Parcelable.Creator
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.eclipse.uprotocol.TestBase
import org.eclipse.uprotocol.common.util.buildStatus
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.packToAny
import org.eclipse.uprotocol.v1.uPayload
import org.junit.Test
import org.junit.runner.RunWith
import java.util.function.Consumer

@RunWith(AndroidJUnit4::class)
class PerformanceTest : TestBase() {

    private val testMessage: UMessage = buildMessage(
        uPayload {
            packToAny(STATUS)
        },
        testAttributes
    )

    private fun writeParcelable(
        parcel: Parcel,
        parcelable: ParcelableMessage<*>,
        count: Int
    ): Float {
        val start = System.nanoTime()
        for (i in 0 until count) {
            parcel.setDataPosition(0)
            parcelable.writeToParcel(parcel, 0)
        }
        val end = System.nanoTime()
        return (end - start).toFloat() / count.toFloat()
    }

    private fun readParcelable(parcel: Parcel, creator: Creator<*>, count: Int): Float {
        val start = System.nanoTime()
        for (i in 0 until count) {
            parcel.setDataPosition(0)
            creator.createFromParcel(parcel)
        }
        val end = System.nanoTime()
        return (end - start).toFloat() / count.toFloat()
    }

    private fun runPerformanceTestParcelableUEntity(count: Int) {
        val parcelable = ParcelableUEntity(testClient)
        val parcel = Parcel.obtain()
        val writeAverage = writeParcelable(parcel, parcelable, count)
        val readAverage = readParcelable(parcel, ParcelableUEntity.CREATOR, count)
        parcel.recycle()
        printTableRow(count, writeAverage, readAverage, PROTOBUF)
    }

    private fun runPerformanceTestParcelableUUri(count: Int) {
        val parcelable = ParcelableUUri(testResourceUri)
        val parcel = Parcel.obtain()
        val writeAverage = writeParcelable(parcel, parcelable, count)
        val readAverage = readParcelable(parcel, ParcelableUUri.CREATOR, count)
        parcel.recycle()
        printTableRow(count, writeAverage, readAverage, PROTOBUF)
    }

    private fun runPerformanceTestParcelableUStatus(count: Int) {
        val parcelable = ParcelableUStatus(STATUS)
        val parcel = Parcel.obtain()
        val writeAverage = writeParcelable(parcel, parcelable, count)
        val readAverage = readParcelable(parcel, ParcelableUStatus.CREATOR, count)
        parcel.recycle()
        printTableRow(count, writeAverage, readAverage, PROTOBUF)
    }

    private fun runPerformanceTestParcelableUMessage(count: Int) {
        val parcelable = ParcelableUMessage(testMessage)
        val parcel = Parcel.obtain()
        val writeAverage = writeParcelable(parcel, parcelable, count)
        val readAverage = readParcelable(parcel, ParcelableUMessage.CREATOR, count)
        parcel.recycle()
        printTableRow(count, writeAverage, readAverage, PROTOBUF)
    }

    private fun runPerformanceTestUMessage(count: Int) {
        runPerformanceTestParcelableUMessage(count)
    }

    @Test
    fun testPerformanceUEntity() {
        printTableHeader("UEntity")
        COUNTS.forEach(
            Consumer { count: Int ->
                runPerformanceTestParcelableUEntity(
                    count
                )
            }
        )
    }

    @Test
    fun testPerformanceUUri() {
        printTableHeader("UUri")
        COUNTS.forEach(
            Consumer { count: Int ->
                runPerformanceTestParcelableUUri(
                    count
                )
            }
        )
    }

    @Test
    fun testPerformanceUStatus() {
        printTableHeader("UStatus")
        COUNTS.forEach(
            Consumer { count: Int ->
                runPerformanceTestParcelableUStatus(
                    count
                )
            }
        )
    }

    @Test
    fun testPerformanceUMessage() {
        printTableHeader("UMessage")
        COUNTS.forEach(
            Consumer { count: Int ->
                runPerformanceTestUMessage(
                    count
                )
            }
        )
    }

    companion object {

        private val STATUS: UStatus = UCode.UNKNOWN.buildStatus("Unknown error")
        private const val PROTOBUF = "Protobuf"
        private val COUNTS = listOf(1000, 100, 10, 5, 1)
        private fun printTableHeader(title: String) {
            println("$title:")
            println("   Loops  Write(ns)   Read(ns)     Method")
            println("-----------------------------------------")
        }

        private fun printTableRow(
            count: Int,
            writeAverage: Float,
            readAverage: Float,
            method: String
        ) {
            println("%8d %10.0f %10.0f   %s%n".format(count, writeAverage, readAverage, method))
        }
    }
}
