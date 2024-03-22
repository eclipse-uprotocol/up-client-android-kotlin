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

import java.lang.reflect.Field

inline fun <reified T> T.callPrivateMethod(name: String, vararg args: Any?): Any? =
    T::class.java
        .getDeclaredMethod(name)
        .apply { isAccessible = true }
        .invoke(this, *args)

inline fun <reified T, R> T.getPrivateObject(name: String): R =
    T::class.java
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .get(this) as R

inline fun <reified T> T.getPrivateField(name: String): Field =
    T::class.java
        .getDeclaredField(name)
        .apply { isAccessible = true }