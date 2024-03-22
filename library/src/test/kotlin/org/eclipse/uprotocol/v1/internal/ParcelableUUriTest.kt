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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.eclipse.uprotocol.TestBase
import org.eclipse.uprotocol.v1.UUri
import org.eclipse.uprotocol.v1.copy
import org.eclipse.uprotocol.v1.uUri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParcelableUUriTest : TestBase() {
    private lateinit var mParcel: Parcel

    @Before
    fun setUp() {
        mParcel = Parcel.obtain()
    }

    @After
    fun tearDown() {
        mParcel.recycle()
    }

    private fun checkWriteAndRead(message: UUri) {
        val start = mParcel.dataPosition()
        ParcelableUUri(message).writeToParcel(mParcel, 0)
        mParcel.setDataPosition(start)
        assertEquals(message, ParcelableUUri.CREATOR.createFromParcel(mParcel).getWrapped())
    }

    @Test
    fun testConstructor() {
        assertEquals(testResourceUriRemote, ParcelableUUri(testResourceUriRemote).getWrapped())
    }

    @Test
    fun testNewArray() {
        val array = ParcelableUUri.CREATOR.newArray(2)
        assertEquals(2, array.size.toLong())
    }

    @Test
    fun testCreateFromParcel() {
        checkWriteAndRead(testResourceUriRemote)
    }

    @Test
    fun testCreateFromParcelWithoutAuthority() {
        checkWriteAndRead(
            testResourceUriRemote.copy {
                clearAuthority()
            })
    }

    @Test
    fun testCreateFromParcelWithoutEntity() {
        checkWriteAndRead(testResourceUriRemote.copy {
            clearEntity()
        })
    }

    @Test
    fun testCreateFromParcelWithoutResource() {
        checkWriteAndRead(testResourceUriRemote.copy {
            clearResource()
        })
    }

    @Test
    fun testCreateFromParcelEmpty() {
        checkWriteAndRead(uUri {  })
    }
}
