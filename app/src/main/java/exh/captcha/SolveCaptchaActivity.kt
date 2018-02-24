package exh.captcha

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

class SolveCaptchaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceId = intent.getIntExtra(SOURCE_ID_EXTRA, -1)
        val source = sourc

        if(sourceId == -1) {
            finish()
            return
        }
    }

    companion object {
        const val SOURCE_ID_EXTRA = "source_id_extra"
    }
}

