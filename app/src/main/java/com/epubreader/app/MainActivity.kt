package com.epubreader.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.view.Gravity

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "EpubReader\n\nBiblioteca EPUB"
            textSize = 26f
            gravity = Gravity.CENTER
        }

        setContentView(textView)
    }
}
