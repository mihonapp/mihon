package eu.kanade.tachiyomi.ui.setting

import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.f2prateek.rx.preferences.Preference
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.util.toast
import exh.favorites.FavoritesIntroDialog
import exh.favorites.LocalFavoritesStorage
import exh.uconfig.ConfiguringDialogController
import exh.ui.login.LoginController
import exh.util.trans
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * EH Settings fragment
 */

class SettingsEhController : SettingsController() {
    private fun Preference<*>.reconfigureOnChange() {
        asObservable()
                .skip(1) //Skip first as it is emitted immediately
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeUntilDestroy {
            ConfiguringDialogController().showDialog(router)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        title = "E-Hentai"

        switchPreference {
            title = "Enable ExHentai"
            summaryOff = "Requires login"
            key = PreferenceKeys.eh_enableExHentai
            isPersistent = false
            defaultValue = false
            preferences.enableExhentai()
                    .asObservable()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeUntilDestroy {
                        isChecked = it
                    }

            onChange { newVal ->
                newVal as Boolean
                if(!newVal) {
                    preferences.enableExhentai().set(false)
                    true
                } else {
                    router.pushController(RouterTransaction.with(LoginController())
                            .pushChangeHandler(FadeChangeHandler())
                            .popChangeHandler(FadeChangeHandler()))
                    false
                }
            }
        }

        switchPreference {
            title = "Use Hentai@Home Network"
            summary = "Do you wish to load images through the Hentai@Home Network? Disabling this option will reduce the amount of pages you are able to view"
            key = "enable_hah"
            defaultValue = true

            preferences.useHentaiAtHome().reconfigureOnChange()
        }.dependency = PreferenceKeys.eh_enableExHentai

        switchPreference {
            title = "Show Japanese titles in search results"
            summaryOn = "Currently showing Japanese titles in search results. Clear the chapter cache after changing this (in the Advanced section)"
            summaryOff = "Currently showing English/Romanized titles in search results. Clear the chapter cache after changing this (in the Advanced section)"
            key = "use_jp_title"
            defaultValue = false

            preferences.useJapaneseTitle().reconfigureOnChange()
        }.dependency = PreferenceKeys.eh_enableExHentai

        switchPreference {
            title = "Use original images"
            summaryOn = "Currently using original images"
            summaryOff = "Currently using resampled images"
            key = PreferenceKeys.eh_useOrigImages
            defaultValue = false

            preferences.eh_useOriginalImages().reconfigureOnChange()
        }.dependency = PreferenceKeys.eh_enableExHentai

        switchPreference {
            defaultValue = true
            key = "secure_exh"
            title = "Secure ExHentai/E-Hentai"
            summary = "Use the HTTPS version of ExHentai/E-Hentai."
        }

        listPreference {
            defaultValue = "auto"
            key = "ehentai_quality"
            summary = "The quality of the downloaded images"
            title = "Image quality"
            entries = arrayOf(
                    "Auto",
                    "2400x",
                    "1600x",
                    "1280x",
                    "980x",
                    "780x"
            )
            entryValues = arrayOf(
                    "auto",
                    "ovrs_2400",
                    "ovrs_1600",
                    "high",
                    "med",
                    "low"
            )

            preferences.imageQuality().reconfigureOnChange()
        }.dependency = PreferenceKeys.eh_enableExHentai

        preferenceCategory {
            title = "Favorites sync"

            switchPreference {
                title = "Disable favorites uploading"
                summary = "Favorites are only downloaded from ExHentai. Any changes to favorites in the app will not be uploaded. Prevents accidental loss of favorites on ExHentai. Note that removals will still be downloaded (if you remove a favorites on ExHentai, it will be removed in the app as well)."
                key = PreferenceKeys.eh_readOnlySync
            }

            preference {
                title = "Show favorites sync notes"
                summary = "Show some information regarding the favorites sync feature"

                onClick {
                    activity?.let {
                        FavoritesIntroDialog().show(it)
                    }
                }
            }

            preference {
                title = "Force sync state reset"
                summary = "Performs a full resynchronization on the next sync. Removals will not be synced. All favorites in the app will be re-uploaded to ExHentai and all favorites on ExHentai will be redownloaded into the app. Useful for repairing sync after sync has been interrupted."

                onClick {
                    activity?.let {
                        MaterialDialog.Builder(it)
                                .title("Are you sure?")
                                .content("Resetting the sync state can cause your next sync to be extremely slow.")
                                .positiveText("Yes")
                                .onPositive { _, _ ->
                                    LocalFavoritesStorage().apply {
                                        getRealm().use {
                                            it.trans {
                                                clearSnapshots(it)
                                            }
                                        }
                                    }
                                    it.toast("Sync state reset", Toast.LENGTH_LONG)
                                }
                                .negativeText("No")
                                .cancelable(false)
                                .show()
                    }
                }
            }
        }
    }
}
