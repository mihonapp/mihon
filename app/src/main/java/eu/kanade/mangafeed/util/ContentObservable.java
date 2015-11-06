/**
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
package eu.kanade.mangafeed.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

public final class ContentObservable {
    private ContentObservable() {
        throw new AssertionError("No instances");
    }

    /**
     * Create Observable that wraps BroadcastReceiver and emits received intents.
     *
     * @param filter Selects the Intent broadcasts to be received.
     */
    public static Observable<Intent> fromBroadcast(Context context, IntentFilter filter){
        return Observable.create(new OnSubscribeBroadcastRegister(context, filter, null, null));
    }

    /**
     * Create Observable that wraps BroadcastReceiver and emits received intents.
     *
     * @param filter Selects the Intent broadcasts to be received.
     * @param broadcastPermission String naming a permissions that a
     *      broadcaster must hold in order to send an Intent to you.  If null,
     *      no permission is required.
     * @param schedulerHandler Handler identifying the thread that will receive
     *      the Intent.  If null, the main thread of the process will be used.
     */
    public static Observable<Intent> fromBroadcast(Context context, IntentFilter filter, String broadcastPermission, Handler schedulerHandler){
        return Observable.create(new OnSubscribeBroadcastRegister(context, filter, broadcastPermission, schedulerHandler));
    }


    static class OnSubscribeBroadcastRegister implements Observable.OnSubscribe<Intent> {

        private final Context context;
        private final IntentFilter intentFilter;
        private final String broadcastPermission;
        private final Handler schedulerHandler;

        public OnSubscribeBroadcastRegister(Context context, IntentFilter intentFilter, String broadcastPermission, Handler schedulerHandler) {
            this.context = context;
            this.intentFilter = intentFilter;
            this.broadcastPermission = broadcastPermission;
            this.schedulerHandler = schedulerHandler;
        }

        @Override
        public void call(final Subscriber<? super Intent> subscriber) {
            final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    subscriber.onNext(intent);
                }
            };

            final Subscription subscription = Subscriptions.create(new Action0() {
                @Override
                public void call() {
                    context.unregisterReceiver(broadcastReceiver);
                }
            });

            subscriber.add(subscription);
            context.registerReceiver(broadcastReceiver, intentFilter, broadcastPermission, schedulerHandler);

        }
    }

}
