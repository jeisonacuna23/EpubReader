package com.epubreader.app

import android.os.Bundle
import android.app.AlertDialog
import android.content.SharedPreferences
import android.view.Gravity
import android.widget.Button
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
import androidx.core.view.WindowCompat
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
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
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
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val root = FrameLayout(this)

        val container = FrameLayout(this).apply {
            id = containerId
            setBackgroundColor(Color.WHITE)
        }

        root.addView(
            container,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val readerPreferences = getSharedPreferences(
            "reader_preferences",
            MODE_PRIVATE
        )
        var activeNavigator: EpubNavigatorFragment? = null

        val settingsButton = Button(this).apply {
            text = "Aa"
            textSize = 16f
            contentDescription = "Ajustes de lectura"
            setOnClickListener {
                activeNavigator?.let {
                    showReaderSettings(it, readerPreferences)
                }
            }
        }

        val buttonSize = (8 * resources.displayMetrics.density).toInt()
        root.addView(
            settingsButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = buttonSize
                marginEnd = buttonSize
            }
        )

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

        setContentView(root)

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
                        initialLocator = initialLocator,
                        initialPreferences = loadReaderPreferences(readerPreferences)
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

                activeNavigator = navigator

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

    private fun showReaderSettings(
        navigator: EpubNavigatorFragment,
        preferences: SharedPreferences
    ) {
        val options = arrayOf(
            "Tema: Claro",
            "Tema: Oscuro",
            "Tema: Sepia",
            "Tamaño de letra: 90%",
            "Tamaño de letra: 100%",
            "Tamaño de letra: 110%",
            "Tamaño de letra: 120%",
            "Tamaño de letra: 130%",
            "Tamaño de letra: 140%"
        )

        AlertDialog.Builder(this)
            .setTitle("Ajustes de lectura")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> preferences.edit().putString("theme", "light").apply()
                    1 -> preferences.edit().putString("theme", "dark").apply()
                    2 -> preferences.edit().putString("theme", "sepia").apply()
                    else -> {
                        val sizes = mapOf(
                            3 to 0.9f,
                            4 to 1.0f,
                            5 to 1.1f,
                            6 to 1.2f,
                            7 to 1.3f,
                            8 to 1.4f
                        )
                        sizes[which]?.let { size ->
                            preferences.edit().putFloat("font_size", size).apply()
                        }
                    }
                }

                navigator.submitPreferences(loadReaderPreferences(preferences))
            }
            .show()
    }

    private fun loadReaderPreferences(
        preferences: SharedPreferences
    ): EpubPreferences {
        val theme = when (preferences.getString("theme", "light")) {
            "dark" -> Theme.DARK
            "sepia" -> Theme.SEPIA
            else -> Theme.LIGHT
        }

        val fontSize = preferences.getFloat("font_size", 1.0f).toDouble()

        return EpubPreferences(
            theme = theme,
            fontSize = fontSize,
            fontFamily = FontFamily.SERIF,
            publisherStyles = false
        )
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
