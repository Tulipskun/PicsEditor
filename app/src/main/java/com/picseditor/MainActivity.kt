package com.picseditor

import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.view.View
import android.widget.Toast
import android.widget.ImageView

class MainActivity : AppCompatActivity() {

    private lateinit var canvas: ImageView
    private lateinit var zoomPan: ZoomPanHandler

    // Back callback สำหรับ Main → Start
    // mode อื่น (crop, adjust ฯลฯ) ให้ addCallback() เพิ่มเองตอน enter mode
    // Android จะเรียก callback ล่าสุดก่อนเสมอ (LIFO)
    private val mainBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showStart()
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = android.graphics.BitmapFactory.decodeStream(contentResolver.openInputStream(it))
            showMain()
            canvas.setImageBitmap(bitmap)
            zoomPan.init()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(R.layout.activity_main)

        val resourceId = resources.getIdentifier(
            "navigation_bar_height", "dimen", "android")
        val navbarHeight = if (resourceId > 0)
            resources.getDimensionPixelSize(resourceId) else 0

        findViewById<View>(R.id.Main).setPadding(0, 0, 0, navbarHeight)

        canvas = findViewById(R.id.canvas)
        zoomPan = ZoomPanHandler(this, canvas)

        onBackPressedDispatcher.addCallback(this, mainBackCallback)

        findViewById<View>(R.id.display).setOnTouchListener { _, event ->
            zoomPan.onTouch(event)
        }

        findViewById<View>(R.id.btnImport).setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<View>(R.id.Crop).setOnClickListener {
            Toast.makeText(this, "Crop", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMain() {
        findViewById<View>(R.id.Start).visibility = View.GONE
        findViewById<View>(R.id.Main).visibility = View.VISIBLE
        mainBackCallback.isEnabled = true
    }

    private fun showStart() {
        findViewById<View>(R.id.Main).visibility = View.GONE
        findViewById<View>(R.id.Start).visibility = View.VISIBLE
        mainBackCallback.isEnabled = false
    }
}
