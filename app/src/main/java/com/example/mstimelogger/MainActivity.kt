package com.example.mstimelogger

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var addTimeButton: Button
    private lateinit var exportButton: Button
    private lateinit var manageButton: Button
    private lateinit var openExcelButton: Button
    private lateinit var setMinWorkButton: Button

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val fileName = "TimeLog.xlsx"

    private var inTime: String? = null
    private var outTime: String? = null
    private var minWorkMinutes: Int = 0

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logTextView = findViewById(R.id.logTextView)
        addTimeButton = findViewById(R.id.addTimeButton)
        exportButton = findViewById(R.id.exportButton)
        manageButton = findViewById(R.id.manageButton)
        openExcelButton = findViewById(R.id.openExcelButton)
        setMinWorkButton = findViewById(R.id.setMinWorkButton)

        checkPermissions()
        loadSavedTimes()
        loadMinWorkingHours()
        displayTodayLog()

        addTimeButton.setOnClickListener {
            val currentTime = timeFormat.format(Date())
            if (inTime == null) {
                inTime = currentTime
                saveTimesToPrefs()
                saveTodayLog()
                Toast.makeText(this, "In Time recorded: $inTime", Toast.LENGTH_SHORT).show()
            } else if (outTime == null) {
                outTime = currentTime
                saveTimesToPrefs()
                saveTodayLog()
                Toast.makeText(this, "Out Time recorded: $outTime", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Today's log already completed", Toast.LENGTH_SHORT).show()
            }
            displayTodayLog()
        }

        exportButton.setOnClickListener { exportToExcel() }
        manageButton.setOnClickListener { showManageDialog() }
        openExcelButton.setOnClickListener { openExcelFile() }
        setMinWorkButton.setOnClickListener { showMinWorkDialog() }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun loadSavedTimes() {
        val prefs = getSharedPreferences("TimePrefs", MODE_PRIVATE)
        val savedDate = prefs.getString("date", "")
        val today = dateFormat.format(Date())
        if (savedDate == today) {
            inTime = prefs.getString("inTime", null)
            outTime = prefs.getString("outTime", null)
        } else {
            inTime = null
            outTime = null
        }
    }

    private fun saveTimesToPrefs() {
        val prefs = getSharedPreferences("TimePrefs", MODE_PRIVATE).edit()
        prefs.putString("date", dateFormat.format(Date()))
        prefs.putString("inTime", inTime)
        prefs.putString("outTime", outTime)
        prefs.apply()
    }

    private fun loadMinWorkingHours() {
        val prefs = getSharedPreferences("TimePrefs", MODE_PRIVATE)
        minWorkMinutes = prefs.getInt("minWorkMinutes", 0)
    }

    private fun saveMinWorkingHours(totalMinutes: Int) {
        val prefs = getSharedPreferences("TimePrefs", MODE_PRIVATE).edit()
        prefs.putInt("minWorkMinutes", totalMinutes)
        prefs.apply()
        minWorkMinutes = totalMinutes
    }

    private fun displayTodayLog() {
        val today = dateFormat.format(Date())
        val duration = calculateDuration(inTime, outTime)
        val minLeaveTime = calculateMinimumLeaveTime(inTime, minWorkMinutes)

        val (weekMinutes, monthMinutes) = calculateWeekAndMonthTotals()

        logTextView.text = """
            Work Log:
            Date: $today
            In Time: ${inTime ?: "--"}
            Out Time: ${outTime ?: "--"}
            Duration: $duration
            Minimum Leave Time: $minLeaveTime

            Weekly Total: ${formatMinutes(weekMinutes)}
            Monthly Total: ${formatMinutes(monthMinutes)}
        """.trimIndent()
    }

    private fun calculateDuration(inTime: String?, outTime: String?): String {
        if (inTime == null || outTime == null) return "--"
        val diff = timeFormat.parse(outTime)!!.time - timeFormat.parse(inTime)!!.time
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "$h hr ${String.format("%02d", m)} min" else "$m min"
    }

    private fun calculateMinimumLeaveTime(inTime: String?, minWorkMinutes: Int): String {
        if (inTime == null || minWorkMinutes <= 0) return "--"
        val cal = Calendar.getInstance()
        cal.time = timeFormat.parse(inTime)!!
        cal.add(Calendar.MINUTE, minWorkMinutes)
        return timeFormat.format(cal.time)
    }

    private fun formatMinutes(totalMinutes: Long): String {
        if (totalMinutes <= 0) return "--"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "$h hr ${String.format("%02d", m)} min" else "$m min"
    }

    private fun calculateWeekAndMonthTotals(): Pair<Long, Long> {
        val file = File(getExternalFilesDir(null), fileName)
        if (!file.exists()) return 0L to 0L

        var week = 0L
        var month = 0L
        val today = Calendar.getInstance().time

        val weekStart = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.time

        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.time

        FileInputStream(file).use {
            val sheet = WorkbookFactory.create(it).getSheetAt(0)
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val date = dateFormat.parse(row.getCell(0).stringCellValue) ?: continue
                val minutes = parseDurationToMinutes(row.getCell(3).stringCellValue)
                if (!date.before(weekStart) && !date.after(today)) week += minutes
                if (!date.before(monthStart) && !date.after(today)) month += minutes
            }
        }
        return week to month
    }

    private fun parseDurationToMinutes(d: String?): Long {
        if (d.isNullOrBlank()) return 0
        return when {
            d.contains("hr") -> {
                val parts = d.replace("hr", "").replace("min", "").trim().split(" ")
                (parts[0].toLong() * 60) + (parts.getOrNull(1)?.toLong() ?: 0)
            }
            d.endsWith("min") -> d.replace("min", "").trim().toLong()
            else -> 0
        }
    }

    // ===================== UPDATED PART =====================

    private fun saveTodayLog() {
        val file = File(getExternalFilesDir(null), fileName)
        val workbook = if (file.exists()) {
            FileInputStream(file).use { WorkbookFactory.create(it) }
        } else {
            XSSFWorkbook().apply {
                createSheet("WorkLogs").createRow(0).apply {
                    createCell(0).setCellValue("Date")
                    createCell(1).setCellValue("In Time")
                    createCell(2).setCellValue("Out Time")
                    createCell(3).setCellValue("Duration")
                }
            }
        }

        val sheet = workbook.getSheetAt(0)
        val today = dateFormat.format(Date())
        val duration = calculateDuration(inTime, outTime)

        var rowFound = false
        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i)
            if (row?.getCell(0)?.stringCellValue == today) {
                row.getCell(1).setCellValue(inTime)
                row.getCell(2).setCellValue(outTime)
                row.getCell(3).setCellValue(duration)
                rowFound = true
                break
            }
        }

        if (!rowFound) {
            val row = sheet.createRow(sheet.lastRowNum + 1)
            row.createCell(0).setCellValue(today)
            row.createCell(1).setCellValue(inTime)
            row.createCell(2).setCellValue(outTime)
            row.createCell(3).setCellValue(duration)
        }

        // ✅ ADD WEEK & MONTH TOTALS INTO EXCEL
        if (isEndOfWeek()) {
            val (weekMinutes, _) = calculateWeekAndMonthTotals()
            appendTotalRowIfNeeded(sheet, "WEEK TOTAL", weekMinutes)
        }

        if (isEndOfMonth()) {
            val (_, monthMinutes) = calculateWeekAndMonthTotals()
            appendTotalRowIfNeeded(sheet, "MONTH TOTAL", monthMinutes)
        }

        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
    }

    private fun exportToExcel() {
        val file = File(getExternalFilesDir(null), fileName)
        Toast.makeText(this, file.absolutePath, Toast.LENGTH_LONG).show()
    }

    private fun openExcelFile() {
        val file = File(getExternalFilesDir(null), fileName)
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun showManageDialog() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Edit In Time", "Edit Out Time", "Reset Today's Log")) { _, i ->
                when (i) {
                    0 -> showTimePicker(true)
                    1 -> showTimePicker(false)
                    2 -> resetTodayLog()
                }
            }.show()
    }

    private fun showTimePicker(isInTime: Boolean) {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            val t = timeFormat.format(cal.time)
            if (isInTime) inTime = t else outTime = t
            saveTimesToPrefs()
            saveTodayLog()
            displayTodayLog()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }

    private fun resetTodayLog() {
        inTime = null
        outTime = null
        saveTimesToPrefs()
        displayTodayLog()
    }

    private fun showMinWorkDialog() {
        TimePickerDialog(this, { _, h, m ->
            saveMinWorkingHours(h * 60 + m)
            displayTodayLog()
        }, minWorkMinutes / 60, minWorkMinutes % 60, true).show()
    }

    private fun isEndOfWeek() =
        Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

    private fun isEndOfMonth(): Boolean {
        val c = Calendar.getInstance()
        return c.get(Calendar.DAY_OF_MONTH) == c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun appendTotalRowIfNeeded(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        label: String,
        totalMinutes: Long
    ) {
        for (i in 0..sheet.lastRowNum) {
            if (sheet.getRow(i)?.getCell(0)?.stringCellValue == label) return
        }
        val row = sheet.createRow(sheet.lastRowNum + 1)
        row.createCell(0).setCellValue(label)
        row.createCell(3).setCellValue(formatMinutes(totalMinutes))
    }
}
