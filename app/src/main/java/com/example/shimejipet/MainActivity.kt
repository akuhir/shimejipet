package com.example.shimejipet

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(PetService.PREFS, Context.MODE_PRIVATE) }
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private val pickPhoto = 41
    private val overlayRequest = 42
    private val cream = Color.rgb(250,246,239)
    private val brown = Color.rgb(91,58,49)
    private val dark = Color.rgb(57,37,33)
    private val muted = Color.rgb(130,108,98)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cream
        window.navigationBarColor = cream
        window.decorView.systemUiVisibility = 0x00002000 or 0x00000010
        buildUi()
        refreshPreview()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(cream)
        }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(TextView(this).apply {
            text = "Habibty"
            textSize = 32f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(dark)
        })
        page.addView(TextView(this).apply {
            text = "Your little companion"
            textSize = 14f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(20))
        })

        val previewCard = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.rgb(239,231,220), 22)
        }
        preview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(180))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_heart)
        }
        previewCard.addView(preview)
        page.addView(previewCard)

        button(page, "Choose photo", true) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(i, pickPhoto)
        }

        section(page, "Appearance")
        val round = Switch(this).apply {
            text = "Round photo crop"
            setTextColor(dark)
            isChecked = prefs.getBoolean("round", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("round", checked).apply()
                prefs.getString("photo_uri", null)?.let {
                    try {
                        PetImage.import(this@MainActivity, Uri.parse(it), checked)
                        refreshPreview()
                    } catch (e: Exception) { status.text = "Photo update failed: ${e.message}" }
                }
            }
        }
        page.addView(round)
        addSlider(page, "Pet size", 50, 260, prefs.getInt("size",150), "dp") {
            prefs.edit().putInt("size",it).apply()
        }

        section(page, "Movement")
        val movement = Switch(this).apply {
            text = "Allow Habibty to move"
            setTextColor(dark)
            isChecked = prefs.getBoolean("movement_enabled",true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("movement_enabled",checked).apply()
            }
        }
        page.addView(movement)
        addSlider(page, "Movement speed", 50, 200, prefs.getInt("speed",100), "%") {
            prefs.edit().putInt("speed",it).apply()
        }
        addSlider(page, "Lift from bottom", 0, 120, prefs.getInt("lift",0), "dp") {
            prefs.edit().putInt("lift",it).apply()
        }

        section(page, "Speech")
        val messages = EditText(this).apply {
            minLines = 3
            setText(prefs.getString("messages","Hello, my love ♡|I missed you!|You're doing great!"))
            hint = "Separate messages with |"
            setTextColor(dark)
            setPadding(dp(14),dp(12),dp(14),dp(12))
            background = rounded(Color.WHITE,14)
        }
        page.addView(messages)
        button(page,"Save messages",false) {
            prefs.edit().putString("messages",messages.text.toString()).apply()
            status.text = "Messages saved."
        }

        section(page,"Control")
        button(page,"Start Habibty",true) { startPet() }
        button(page,"Stop Habibty",false) {
            stopService(Intent(this,PetService::class.java))
            status.text = "Habibty stopped."
        }
        status = TextView(this).apply {
            text = "Choose a photo to get started."
            textSize = 13f
            setTextColor(muted)
            setPadding(0,dp(18),0,dp(8))
        }
        page.addView(status)
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(dark)
            setPadding(0,dp(22),0,dp(10))
        })
    }

    private fun button(parent: LinearLayout, title: String, primary: Boolean, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 15f
            setTextColor(if (primary) Color.WHITE else dark)
            background = rounded(if (primary) brown else Color.rgb(235,226,215),16)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin = dp(8) })
    }

    private fun addSlider(parent: LinearLayout, title: String, min: Int, max: Int,
                          initial: Int, unit: String, save: (Int)->Unit) {
        val label = TextView(this).apply { textSize=14f; setTextColor(dark) }
        val seek = SeekBar(this).apply { this.max=max-min; progress=(initial-min).coerceIn(0,max-min) }
        fun update(v:Int) { label.text="$title: $v$unit"; save(v) }
        update(initial)
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean) { update(p+min) }
            override fun onStartTrackingTouch(s:SeekBar?) {}
            override fun onStopTrackingTouch(s:SeekBar?) {}
        })
        parent.addView(label); parent.addView(seek)
    }

    private fun rounded(color:Int, radius:Int) = GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat()
    }

    private fun startPet() {
        if (!PetImage.exists(this)) { status.text="Choose a photo first."; return }
        if (!Settings.canDrawOverlays(this)) {
            status.text="Enable display over other apps."
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")),overlayRequest)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),43)
        }
        val i=Intent(this,PetService::class.java)
        if (Build.VERSION.SDK_INT>=26) startForegroundService(i) else startService(i)
        status.text="Habibty is active."
        moveTaskToBack(true)
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode==pickPhoto && resultCode==RESULT_OK) {
            val uri=data?.data ?: return
            try {
                val flags=data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 && flags != 0)
                    contentResolver.takePersistableUriPermission(uri,flags)
                prefs.edit().putString("photo_uri",uri.toString()).apply()
                PetImage.import(this,uri,prefs.getBoolean("round",true))
                refreshPreview()
                status.text="Photo imported successfully."
            } catch(e:Exception) { status.text="Photo import failed: ${e.message}" }
        } else if (requestCode==overlayRequest && Settings.canDrawOverlays(this)) startPet()
    }

    private fun refreshPreview() {
        if (PetImage.exists(this)) preview.setImageBitmap(BitmapFactory.decodeFile(PetImage.path(this)))
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
