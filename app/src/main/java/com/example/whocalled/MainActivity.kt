import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
        private const val CREATE_FILE_REQUEST_CODE = 101
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonExtract = findViewById<Button>(R.id.buttonExtract)
        buttonExtract.setOnClickListener {
            if (hasPermissions()) {
                createCSVFile()
            } else {
                ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSIONS_REQUEST_CODE)
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                createCSVFile()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launch an intent to let the user choose where to save the CSV file.
    private fun createCSVFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "call_log.csv")
        }
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                writeCSVToUri(uri)
            } else {
                Toast.makeText(this, "File not created", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Write the CSV content to the URI provided by the SAF.
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

    // Query the call log and generate a CSV-formatted String including contact names.
    private fun generateCSV(): String {
        // Updated CSV header to include ContactName.
        val csvHeader = "Type,PhoneNumber,ContactName,Date,Duration"
        val stringBuilder = StringBuilder().apply { append(csvHeader).append("\n") }

        // Add CACHED_NAME to the projection.
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
                val callType = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE))
                val phoneNumber = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER))
                // Retrieve the cached contact name; if null, use an empty string.
                val contactName = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)) ?: ""
                val callDate = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE))
                val duration = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION))

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
