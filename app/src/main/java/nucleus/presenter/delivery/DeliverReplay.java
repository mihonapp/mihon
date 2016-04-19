package nucleus.presenter.delivery;

import rx.Notification;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

public class DeliverReplay<View, T> implements Observable.Transformer<T, Delivery<View, T>> {

    private final Observable<View> view;

    public DeliverReplay(Observable<View> view) {
        this.view = view;
    }

    @Override
    public Observable<Delivery<View, T>> call(Observable<T> observable) {
        final ReplaySubject<Notification<T>> subject = ReplaySubject.create();
        final Subscription subscription = observable
            .materialize()
            .filter(new Func1<Notification<T>, Boolean>() {
                @Override
                public Boolean call(Notification<T> notification) {
                    return !notification.isOnCompleted();
                }
            })
            .subscribe(subject);
        return view
            .switchMap(new Func1<View, Observable<Delivery<View, T>>>() {
                @Override
                public Observable<Delivery<View, T>> call(final View view) {
                    return view == null ? Observable.<Delivery<View, T>>never() : subject
                        .map(new Func1<Notification<T>, Delivery<View, T>>() {
                            @Override
                            public Delivery<View, T> call(Notification<T> notification) {
                                return new Delivery<>(view, notification);
                            }
                        });
                }
            })
            .doOnUnsubscribe(new Action0() {
                @Override
                public void call() {
                    subscription.unsubscribe();
                }
            });
    }
}
