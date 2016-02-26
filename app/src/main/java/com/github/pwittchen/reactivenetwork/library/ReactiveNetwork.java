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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

/**
 * ReactiveNetwork is an Android library
 * listening network connection state and change of the WiFi signal strength
 * with RxJava Observables. It can be easily used with RxAndroid.
 */
public class ReactiveNetwork {
  private boolean checkInternet = false;
  private ConnectivityStatus status = ConnectivityStatus.UNKNOWN;

  /**
   * Enables Internet connection check.
   * When it's called WIFI_CONNECTED_HAS_INTERNET and WIFI_CONNECTED_HAS_NO_INTERNET statuses
   * can be emitted by observeConnectivity(context) method. When it isn't called
   * only WIFI_CONNECTED can by emitted by observeConnectivity(context) method.
   *
   * @return ReactiveNetwork object
   */
  public ReactiveNetwork enableInternetCheck() {
    checkInternet = true;
    return this;
  }

  /**
   * Observes ConnectivityStatus,
   * which can be WIFI_CONNECTED, MOBILE_CONNECTED or OFFLINE
   *
   * @param context Context of the activity or an application
   * @return RxJava Observable with ConnectivityStatus
   */
  public Observable<ConnectivityStatus> observeConnectivity(final Context context) {
    final IntentFilter filter = new IntentFilter();
    filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

    return Observable.create(new Observable.OnSubscribe<ConnectivityStatus>() {
      @Override public void call(final Subscriber<? super ConnectivityStatus> subscriber) {
        final BroadcastReceiver receiver = new BroadcastReceiver() {
          @Override public void onReceive(Context context, Intent intent) {
            final ConnectivityStatus newStatus = getConnectivityStatus(context, checkInternet);

            // we need to perform check below,
            // because after going off-line, onReceive() is called twice
            if (newStatus != status) {
              status = newStatus;
              subscriber.onNext(newStatus);
            }
          }
        };

        context.registerReceiver(receiver, filter);

        subscriber.add(unsubscribeInUiThread(new Action0() {
          @Override public void call() {
            context.unregisterReceiver(receiver);
          }
        }));
      }
    }).defaultIfEmpty(ConnectivityStatus.OFFLINE);
  }

  public ConnectivityStatus getConnectivityStatus(final Context context,
      final boolean checkInternet) {
    final String service = Context.CONNECTIVITY_SERVICE;
    final ConnectivityManager manager = (ConnectivityManager) context.getSystemService(service);
    final NetworkInfo networkInfo = manager.getActiveNetworkInfo();

    if (networkInfo == null) {
      return ConnectivityStatus.OFFLINE;
    }

    if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
      if (checkInternet) {
        return getWifiInternetStatus(networkInfo);
      } else {
        return ConnectivityStatus.WIFI_CONNECTED;
      }
    } else if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
      return ConnectivityStatus.MOBILE_CONNECTED;
    }

    return ConnectivityStatus.OFFLINE;
  }

  private ConnectivityStatus getWifiInternetStatus(final NetworkInfo networkInfo) {
    if (networkInfo.isConnected()) {
      return ConnectivityStatus.WIFI_CONNECTED_HAS_INTERNET;
    } else {
      return ConnectivityStatus.WIFI_CONNECTED_HAS_NO_INTERNET;
    }
  }

  private Subscription unsubscribeInUiThread(final Action0 unsubscribe) {
    return Subscriptions.create(new Action0() {

      @Override public void call() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
          unsubscribe.call();
        } else {
          final Scheduler.Worker inner = AndroidSchedulers.mainThread().createWorker();
          inner.schedule(new Action0() {
            @Override public void call() {
              unsubscribe.call();
              inner.unsubscribe();
            }
          });
        }
      }
    });
  }
}
