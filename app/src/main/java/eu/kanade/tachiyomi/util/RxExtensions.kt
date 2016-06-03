package eu.kanade.tachiyomi.util

import rx.Subscription
import rx.subscriptions.CompositeSubscription

fun Subscription?.isNullOrUnsubscribed() = this == null || isUnsubscribed

operator fun CompositeSubscription.plusAssign(subscription: Subscription) = add(subscription)