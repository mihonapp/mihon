/*
 * Copyright (C) 2015 Piotr Wittchen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.pwittchen.reactivenetwork.library;

import rx.functions.Func1;

public enum ConnectivityStatus {
  UNKNOWN("unknown"),
  WIFI_CONNECTED("connected to WiFi"),
  WIFI_CONNECTED_HAS_INTERNET("connected to WiFi (Internet available)"),
  WIFI_CONNECTED_HAS_NO_INTERNET("connected to WiFi (Internet not available)"),
  MOBILE_CONNECTED("connected to mobile network"),
  OFFLINE("offline");

  public final String description;

  ConnectivityStatus(final String description) {
    this.description = description;
  }

  /**
   * Creates a function, which checks
   * if single connectivity status or many statuses
   * are equal to current status. It can be used inside filter(...)
   * method from RxJava
   *
   * @param statuses many connectivity statuses or single status
   * @return Func1<ConnectivityStatus, Boolean> from RxJava
   */
  public static Func1<ConnectivityStatus, Boolean> isEqualTo(final ConnectivityStatus... statuses) {
    return new Func1<ConnectivityStatus, Boolean>() {
      @Override public Boolean call(ConnectivityStatus connectivityStatus) {
        boolean statuesAreEqual = false;

        for (ConnectivityStatus singleStatus : statuses) {
          statuesAreEqual = singleStatus == connectivityStatus;
        }

        return statuesAreEqual;
      }
    };
  }

  /**
   * Creates a function, which checks
   * if single connectivity status or many statuses
   * are not equal to current status. It can be used inside filter(...)
   * method from RxJava
   *
   * @param statuses many connectivity statuses or single status
   * @return Func1<ConnectivityStatus, Boolean> from RxJava
   */
  public static Func1<ConnectivityStatus, Boolean> isNotEqualTo(
      final ConnectivityStatus... statuses) {
    return new Func1<ConnectivityStatus, Boolean>() {
      @Override public Boolean call(ConnectivityStatus connectivityStatus) {
        boolean statuesAreNotEqual = false;

        for (ConnectivityStatus singleStatus : statuses) {
          statuesAreNotEqual = singleStatus != connectivityStatus;
        }

        return statuesAreNotEqual;
      }
    };
  }

  @Override public String toString() {
    return "ConnectivityStatus{" + "description='" + description + '\'' + '}';
  }
}
