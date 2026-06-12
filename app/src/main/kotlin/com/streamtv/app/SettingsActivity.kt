package com.streamtv.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.streamtv.app.data.SettingsManager

/** TV-friendly settings: edit the single endpoint URL and player options. */
class SettingsActivity : Activity() {

    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        settings = SettingsManager.from(this)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0D0D1A"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(40), dp(32), dp(32))
        }

        root.addView(TextView(this).apply {
            text = "⚙️ Settings"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, 0, 0, dp(24))
        })

        // Endpoint URL
        root.addView(label("URL Endpoint Streaming", dp(8)))
        val endpointInput = field(settings.endpointUrl, dp(16))
        root.addView(endpointInput)

        // Auto-sniff toggle
        val sniffRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        sniffRow.addView(TextView(this).apply {
            text = "Auto-deteksi stream (player native)"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val sniffSwitch = Switch(this).apply {
            isChecked = settings.autoSniff
            isFocusable = true
        }
        sniffRow.addView(sniffSwitch)
        root.addView(sniffRow)

        // User-Agent (advanced)
        root.addView(label("User-Agent (lanjutan)", dp(8)))
        val uaInput = field(settings.userAgent, dp(16))
        root.addView(uaInput)

        // Buttons
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        buttonRow.addView(button("Simpan", "#7C4DFF") {
            val ok = settings.setEndpoint(endpointInput.text.toString())
            if (!ok) {
                Toast.makeText(this, "URL harus diawali http:// atau https://", Toast.LENGTH_LONG).show()
                return@button
            }
            settings.autoSniff = sniffSwitch.isChecked
            val ua = uaInput.text.toString().trim()
            if (ua.isNotEmpty()) settings.userAgent = ua
            Toast.makeText(this, "✅ Tersimpan", Toast.LENGTH_SHORT).show()
            finish()
        })
        buttonRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), 1)
        })
        buttonRow.addView(button("Reset Default", "#444466") {
            settings.resetToDefaults()
            endpointInput.setText(settings.endpointUrl)
            uaInput.setText(settings.userAgent)
            sniffSwitch.isChecked = settings.autoSniff
            Toast.makeText(this, "Direset ke default", Toast.LENGTH_SHORT).show()
        })
        root.addView(buttonRow)

        scroll.addView(root)
        setContentView(scroll)
        endpointInput.requestFocus()
    }

    private fun label(text: String, bottom: Int) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#AAFFFFFF"))
        setPadding(0, 0, 0, bottom)
    }

    private fun field(value: String, bottom: Int) = EditText(this).apply {
        setText(value)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#66FFFFFF"))
        setBackgroundColor(Color.parseColor("#22FFFFFF"))
        setSingleLine(true)
        isFocusable = true
        isFocusableInTouchMode = true
        val density = resources.displayMetrics.density
        setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        (layoutParams as? ViewGroup.MarginLayoutParams)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = bottom }
    }

    private fun button(text: String, color: String, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isFocusable = true
            isClickable = true
            setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 14f
                setColor(Color.parseColor(color))
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, has ->
                v.scaleX = if (has) 1.05f else 1f
                v.scaleY = if (has) 1.05f else 1f
            }
        }
    }
}
