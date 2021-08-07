package eu.kanade.tachiyomi.ui.more.licenses

import eu.davidea.flexibleadapter.FlexibleAdapter

class LicensesAdapter(controller: LicensesController) :
    FlexibleAdapter<LicensesItem>(null, controller, true)
