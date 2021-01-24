package eu.kanade.tachiyomi.ui.setting.track

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.preference.LoginDialogPreference
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackLoginDialog(
    @StringRes usernameLabelRes: Int? = null,
    bundle: Bundle? = null
) : LoginDialogPreference(usernameLabelRes, bundle) {

    private val service = Injekt.get<TrackManager>().getService(args.getInt("serviceId"))!!

    constructor(service: TrackService) : this(service, null)

    constructor(service: TrackService, @StringRes usernameLabelRes: Int?) :
        this(usernameLabelRes, bundleOf("serviceId" to service.id))

    @StringRes
    override fun getTitleName(): Int = service.nameRes()

    override fun setCredentialsOnView(view: View) {
        binding?.username?.setText(service.getUsername())
        binding?.password?.setText(service.getPassword())
    }

    override fun checkLogin() {
        if (binding!!.username.text.isNullOrEmpty() || binding!!.password.text.isNullOrEmpty()) {
            return
        }

        binding!!.login.progress = 1
        val user = binding!!.username.text.toString()
        val pass = binding!!.password.text.toString()

        launchIO {
            try {
                service.login(user, pass)
                dialog?.dismiss()
                withUIContext { view?.context?.toast(R.string.login_success) }
            } catch (e: Throwable) {
                service.logout()
                binding?.login?.progress = -1
                binding?.login?.setText(R.string.unknown_error)
                withUIContext { e.message?.let { view?.context?.toast(it) } }
            }
        }
    }

    override fun onDialogClosed() {
        super.onDialogClosed()
        (targetController as? Listener)?.trackLoginDialogClosed(service)
    }

    interface Listener {
        fun trackLoginDialogClosed(service: TrackService)
    }
}
