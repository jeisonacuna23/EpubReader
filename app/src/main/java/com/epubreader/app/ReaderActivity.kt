package com.epubreader.app

import android.os.Bundle
import kotlinx.coroutines.flow.collect
import org.readium.r2.shared.publication.Locator
import org.json.JSONObject
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment

class ReaderActivity : FragmentActivity() {

    private val containerId = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory =
                EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            finish()
            return
        }

        super.onCreate(null)

        val container = FrameLayout(this).apply {
            id = containerId
            setBackgroundColor(Color.WHITE)
        }

        val status = TextView(this).apply {
            text = "Abriendo libro…"
            textSize = 18f
            setTextColor(Color.DKGRAY)
            gravity = android.view.Gravity.CENTER
        }

        val progress = ProgressBar(this)

        val loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            addView(
                progress,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                status,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        container.addView(
            loading,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(container)

        val uriString = intent.getStringExtra("book_uri")
        if (uriString.isNullOrBlank()) {
            showError(container, "No se recibió la ubicación del libro.")
            return
        }

        lifecycleScope.launch {
            try {
                val uri = Uri.parse(uriString)
                val url = uri.toAbsoluteUrl()

                if (url == null) {
                    showError(container, "No se pudo reconocer el archivo EPUB.")
                    return@launch
                }

                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(
                    contentResolver,
                    httpClient
                )

                val asset = assetRetriever.retrieve(url).getOrElse {
                    showError(
                        container,
                        "No se pudo leer el EPUB: ${it}"
                    )
                    return@launch
                }

                val parser = DefaultPublicationParser(
                    this@ReaderActivity,
                    httpClient = httpClient,
                    assetRetriever = assetRetriever,
                    pdfFactory = null
                )

                val opener = PublicationOpener(
                    publicationParser = parser
                )

                val publication = opener.open(
                    asset,
                    allowUserInteraction = false
                ).getOrElse {
                    showError(
                        container,
                        "No se pudo abrir el libro. Puede que el EPUB esté dañado o no sea compatible."
                    )
                    return@launch
                }

                val navigatorFactory = EpubNavigatorFactory(publication)

                val progressPreferences = getSharedPreferences(
                    "reading_progress",
                    MODE_PRIVATE
                )

                val savedPosition = progressPreferences.getString(
                    uriString,
                    null
                )

                val initialLocator = try {
                    savedPosition?.let {
                        Locator.fromJSON(JSONObject(it))
                    }
                } catch (e: Exception) {
                    null
                }

                supportFragmentManager.fragmentFactory =
                    navigatorFactory.createFragmentFactory(
                        initialLocator = initialLocator
                    )

                container.removeAllViews()

                supportFragmentManager.beginTransaction()
                    .replace(
                        containerId,
                        EpubNavigatorFragment::class.java,
                        Bundle(),
                        "epub_navigator"
                    )
                    .commitNow()

                val navigator = supportFragmentManager
                    .findFragmentByTag("epub_navigator")
                    as EpubNavigatorFragment

                lifecycleScope.launch {
                    navigator.currentLocator.collect { locator ->
                        progressPreferences.edit()
                            .putString(
                                uriString,
                                locator.toJSON().toString()
                            )
                            .apply()
                    }
                }

            } catch (e: Exception) {
                showError(
                    container,
                    "Error al abrir el libro:\n${e.message ?: "Error desconocido"}"
                )
            }
        }
    }

    private fun showError(container: FrameLayout, message: String) {
        container.removeAllViews()

        val error = TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(32, 32, 32, 32)
            gravity = android.view.Gravity.CENTER
        }

        container.addView(
            error,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
}
