package com.synctap

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var countText: TextView
    private lateinit var logText: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Logger.init(this)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "SyncTap\n\n1. Mở Cài đặt trợ năng, bật dịch vụ SyncTap.\n" +
                "2. Mở các game clone ở cửa sổ nổi (hoặc split-screen), sắp xếp vị trí.\n" +
                "3. Chọn số cửa sổ bên dưới.\n" +
                "4. Bấm nút nổi VÙNG để sửa khung: giữ vào một CẠNH để di chuyển cả vùng, kéo một GÓC để đổi cỡ. " +
                "Dùng + VÙNG / XOÁ VÙNG để thêm bớt, LƯU VÙNG để lưu, HUỶ SỬA để thoát không lưu.\n" +
                "5. Bấm nút SYNC để bật. Chạm ở cửa sổ nào, các cửa sổ còn lại chạm theo cùng vị trí tương đối.\n" +
                "Tắt SYNC để dùng máy bình thường."
            textSize = 15f
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
        val modeBtn = Button(this)
        fun refreshMode() {
            modeBtn.text = if (prefs.getInt("mode", 1) == 2) "Chế độ: HỆ THỐNG (thử nghiệm, Android 11+)"
                           else "Chế độ: GHI RỒI PHÁT (ổn định, trễ)"
        }
        modeBtn.setOnClickListener {
            prefs.edit().putInt("mode", if (prefs.getInt("mode", 1) == 2) 1 else 2).apply(); refreshMode()
        }
        refreshMode()
        root.addView(modeBtn)

        // ----- Khu vực log -----
        val logRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        logRow.addView(Button(this).apply {
            text = "Xem log"
            setOnClickListener { showLog() }
        })
        logRow.addView(Button(this).apply {
            text = "Sao chép"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("synctap-log", Logger.dump()))
                Toast.makeText(this@MainActivity, "Đã sao chép log", Toast.LENGTH_SHORT).show()
            }
        })
        logRow.addView(Button(this).apply {
            text = "Xoá log"
            setOnClickListener { Logger.clear(); showLog() }
        })
        root.addView(logRow)
        logText = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.DKGRAY)
            setTextIsSelectable(true)
        }
        root.addView(logText)

        setContentView(ScrollView(this).apply { addView(root) })
        showLog()
    }

    override fun onResume() { super.onResume(); showLog() }

    private fun showLog() { logText.text = Logger.dump().takeLast(12000) }
}
