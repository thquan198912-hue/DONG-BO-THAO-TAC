package com.synctap

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var countText: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "SyncTap\n\n1. Mở Cài đặt trợ năng, bật dịch vụ SyncTap.\n" +
                "2. Mở các game clone ở cửa sổ nổi (hoặc split-screen), sắp xếp vị trí.\n" +
                "3. Chọn số cửa sổ bên dưới.\n" +
                "4. Bấm nút nổi VÙNG, kéo một khung quanh vùng game của từng cửa sổ (lần lượt 1, 2, ...).\n" +
                "5. Bấm nút SYNC để bật. Chạm ở cửa sổ nào, các cửa sổ còn lại chạm theo cùng vị trí tương đối.\n" +
                "Di chuyển hoặc đổi cỡ cửa sổ thì cài lại VÙNG. Tắt SYNC để dùng máy bình thường."
            textSize = 16f
            setTextColor(Color.BLACK)
        })
        root.addView(Button(this).apply {
            text = "Mở cài đặt trợ năng"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        countText = TextView(this).apply { textSize = 22f; setPadding(40, 0, 40, 0) }
        fun refresh() { countText.text = "Số cửa sổ: " + prefs.getInt("panes", 2) }
        row.addView(Button(this).apply {
            text = "-"
            setOnClickListener {
                prefs.edit().putInt("panes", (prefs.getInt("panes", 2) - 1).coerceAtLeast(1)).apply(); refresh()
            }
        })
        row.addView(countText)
        row.addView(Button(this).apply {
            text = "+"
            setOnClickListener {
                prefs.edit().putInt("panes", (prefs.getInt("panes", 2) + 1).coerceAtMost(8)).apply(); refresh()
            }
        })
        root.addView(row)
        refresh()
        setContentView(root)
    }
}
