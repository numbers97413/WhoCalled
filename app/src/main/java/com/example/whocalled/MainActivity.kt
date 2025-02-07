package com.example.whocalledme

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
    }

    // For devices below Android Q, we require WRITE_EXTERNAL_STORAGE;
    // For Android Q and above, only READ_CALL_LOG and READ_CONTACTS are needed.
    private val requiredPermissions: Array<String> by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS
            )
        }
    }

    // Activity Result Launcher for creating a CSV file.
    private lateinit var createDocumentLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the ActivityResultLauncher for creating a CSV file.
        createDocumentLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
                if (uri != null) {
                    writeCSVToUri(uri)
                } else {
                    Toast.makeText(this, "File not created", Toast.LENGTH_SHORT).show()
                }
            }

        val buttonExtract = findViewById<Button>(R.id.buttonExtract)
        buttonExtract.setOnClickListener {
            if (hasPermissions()) {
                createCSVFile()
            } else {
                ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSIONS_REQUEST_CODE)
            }
        }
    }

    private fun hasPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                createCSVFile()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launches the document creation flow.
    private fun createCSVFile() {
        createDocumentLauncher.launch("call_log.csv")
    }

    // Writes the CSV data to the provided URI.
    private fun writeCSVToUri(uri: Uri) {
        val csvContent = generateCSV()
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csvContent.toByteArray())
            }
            Toast.makeText(this, "Call log extracted and saved.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error writing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Queries the call log and generates CSV-formatted text.
    private fun generateCSV(): String {
        val csvHeader = "Type,PhoneNumber,ContactName,Date,Duration"
        val stringBuilder = StringBuilder().apply { append(csvHeader).append("\n") }

        val projection = arrayOf(
            CallLog.Calls.TYPE,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val cachedNameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                val callType = cursor.getInt(typeIndex)
                val phoneNumber = cursor.getString(numberIndex)
                val contactName = cursor.getString(cachedNameIndex) ?: ""
                val callDate = cursor.getLong(dateIndex)
                val duration = cursor.getString(durationIndex)

                val callTypeString = when (callType) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    else -> "Other"
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = dateFormat.format(Date(callDate))

                stringBuilder.append("$callTypeString,$phoneNumber,$contactName,$date,$duration\n")
            } while (cursor.moveToNext())
            cursor.close()
        } else {
            Toast.makeText(this, "No call logs found.", Toast.LENGTH_SHORT).show()
        }
        return stringBuilder.toString()
    }
}
