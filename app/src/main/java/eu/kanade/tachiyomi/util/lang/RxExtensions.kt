package eu.kanade.tachiyomi.util.lang

import rx.Subscription
import rx.subscriptions.CompositeSubscription

operator fun CompositeSubscription.plusAssign(subscription: Subscription) = add(subscription)
