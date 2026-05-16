package com.picseditor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import android.view.View
import android.widget.Toast

class MainActivity : AppCompatActivity() {
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

        val main = findViewById<View>(R.id.Main)
        main.setPadding(0, 0, 0, navbarHeight)
		
		val btnCrop = findViewById<View>(R.id.Crop)
val btnAdjust = findViewById<View>(R.id.Adjust)
val btnEffect = findViewById<View>(R.id.Effect)

btnCrop.setOnClickListener {
    Toast.makeText(this, "Crop", Toast.LENGTH_SHORT).show()
}
btnAdjust.setOnClickListener { /* โค้ด */ }
btnEffect.setOnClickListener { /* โค้ด */ }
    }
}