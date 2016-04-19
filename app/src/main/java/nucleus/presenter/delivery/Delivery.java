package nucleus.presenter.delivery;

import android.support.annotation.Nullable;

import rx.Notification;
import rx.functions.Action2;

/**
 * A class that represents a couple of View and Data.
 *
 * @param <View>
 * @param <T>
 */
public final class Delivery<View, T> {

    private final View view;
    private final Notification<T> notification;

    public Delivery(View view, Notification<T> notification) {
        this.view = view;
        this.notification = notification;
    }

    public void split(Action2<View, T> onNext, @Nullable Action2<View, Throwable> onError) {
        if (notification.getKind() == Notification.Kind.OnNext)
            onNext.call(view, notification.getValue());
        else if (onError != null && notification.getKind() == Notification.Kind.OnError)
            onError.call(view, notification.getThrowable());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Delivery<?, ?> delivery = (Delivery<?, ?>)o;

        if (view != null ? !view.equals(delivery.view) : delivery.view != null) return false;
        return !(notification != null ? !notification.equals(delivery.notification) : delivery.notification != null);
    }

    @Override
    public int hashCode() {
        int result = view != null ? view.hashCode() : 0;
        result = 31 * result + (notification != null ? notification.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Delivery{" +
            "view=" + view +
            ", notification=" + notification +
            '}';
    }
}
