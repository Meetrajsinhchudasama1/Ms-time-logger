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

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val fileName = "TimeLog.xlsx"

    private var inTime: String? = null
    private var outTime: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
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

        checkPermissions()
        loadSavedTimes()
        displayTodayLog()

        addTimeButton.setOnClickListener {
            val currentTime = timeFormat.format(Date())
            if (inTime == null) {
                inTime = currentTime
                saveTodayLog()
                saveTimesToPrefs()
                Toast.makeText(this, "In Time recorded: $inTime", Toast.LENGTH_SHORT).show()
            } else if (outTime == null) {
                outTime = currentTime
                saveTodayLog()
                saveTimesToPrefs()
                Toast.makeText(this, "Out Time recorded: $outTime", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Today's log already completed", Toast.LENGTH_SHORT).show()
            }
            displayTodayLog()
        }

        exportButton.setOnClickListener {
            exportToExcel()
        }

        manageButton.setOnClickListener {
            showManageDialog()
        }

        openExcelButton.setOnClickListener {
            openExcelFile()
        }
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

    private fun displayTodayLog() {
        val today = dateFormat.format(Date())
        val logText = """
            Work Log:
            Date: $today
            In Time: ${inTime ?: "--"}
            Out Time: ${outTime ?: "--"}
            Duration: ${calculateDuration(inTime, outTime)}
        """.trimIndent()
        logTextView.text = logText
    }

    private fun calculateDuration(inTime: String?, outTime: String?): String {
        if (inTime == null || outTime == null) return "--"
        return try {
            val inDate = timeFormat.parse(inTime)
            val outDate = timeFormat.parse(outTime)
            val diff = outDate.time - inDate.time
            val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60

            if (hours > 0) {
                String.format("%d hr %02d min", hours, minutes)
            } else {
                String.format("%d min", minutes)
            }
        } catch (e: Exception) {
            "--"
        }
    }


    private fun saveTodayLog() {
        val file = File(getExternalFilesDir(null), fileName)
        val workbook = if (file.exists()) {
            FileInputStream(file).use { fis -> WorkbookFactory.create(fis) }
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
            if (row.getCell(0).stringCellValue == today) {
                row.getCell(1).setCellValue(inTime)
                row.getCell(2).setCellValue(outTime)
                row.getCell(3).setCellValue(duration)
                rowFound = true
                break
            }
        }

        if (!rowFound) {
            val newRow = sheet.createRow(sheet.lastRowNum + 1)
            newRow.createCell(0).setCellValue(today)
            newRow.createCell(1).setCellValue(inTime)
            newRow.createCell(2).setCellValue(outTime)
            newRow.createCell(3).setCellValue(duration)
        }

        FileOutputStream(file).use { fos -> workbook.write(fos) }
        workbook.close()
    }

    private fun exportToExcel() {
        val file = File(getExternalFilesDir(null), fileName)
        if (file.exists()) {
            Toast.makeText(this, "Excel exported at:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No log file found yet!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExcelFile() {
        val file = File(getExternalFilesDir(null), fileName)
        if (!file.exists()) {
            Toast.makeText(this, "No Excel file found yet!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open Excel file", Toast.LENGTH_LONG).show()
        }
    }

    private fun showManageDialog() {
        val options = arrayOf("Edit In Time", "Edit Out Time", "Reset Today's Log")
        AlertDialog.Builder(this)
            .setTitle("Manage Today's Log")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTimePicker(isInTime = true)
                    1 -> showTimePicker(isInTime = false)
                    2 -> resetTodayLog()
                }
            }
            .show()
    }

    private fun showTimePicker(isInTime: Boolean) {
        val calendar = Calendar.getInstance()
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            val selectedTime = timeFormat.format(calendar.time)
            if (isInTime) inTime = selectedTime else outTime = selectedTime
            saveTimesToPrefs()
            saveTodayLog()
            displayTodayLog()
            Toast.makeText(this, "Time updated", Toast.LENGTH_SHORT).show()
        }

        TimePickerDialog(
            this, timeSetListener,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun resetTodayLog() {
        inTime = null
        outTime = null
        saveTimesToPrefs()
        displayTodayLog()
        Toast.makeText(this, "Today's log reset", Toast.LENGTH_SHORT).show()
    }
}
