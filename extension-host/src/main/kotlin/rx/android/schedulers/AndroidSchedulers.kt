package rx.android.schedulers

import android.os.Handler
import android.os.Looper
import rx.Scheduler
import rx.schedulers.Schedulers

object AndroidSchedulers {
    private val main = Schedulers.from { action -> Handler(Looper.getMainLooper()).post(action) }

    @JvmStatic fun mainThread(): Scheduler = main

    @JvmStatic fun from(looper: Looper): Scheduler = Schedulers.from { action -> Handler(looper).post(action) }
}
