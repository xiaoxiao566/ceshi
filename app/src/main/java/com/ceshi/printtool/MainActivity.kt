package com.ceshi.printtool

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ceshi.printtool.document.DocType
import com.ceshi.printtool.document.DocumentSourceFactory
import com.ceshi.printtool.document.FileClassifier
import com.ceshi.printtool.document.PrintFile
import com.ceshi.printtool.print.OtgPrintManager
import com.ceshi.printtool.print.WirelessPrintHelper
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val files = mutableListOf<PrintFile>()
    private var selectedIndex = -1

    private lateinit var listView: ListView
    private lateinit var emptyHint: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var otgPrint: OtgPrintManager
    private lateinit var wifiPrint: WirelessPrintHelper

    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult
            for (u in uris) {
                addUri(u, silent = true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.fileList)
        emptyHint = findViewById(R.id.emptyHint)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            selectedIndex = position
        }

        otgPrint = OtgPrintManager(this)
        wifiPrint = WirelessPrintHelper(this)

        findViewById<MaterialButton>(R.id.btnAddFiles).setOnClickListener {
            pickFiles.launch(arrayOf("*/*"))
        }
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            files.clear()
            selectedIndex = -1
            refreshList()
        }
        findViewById<MaterialButton>(R.id.btnPrintWifi).setOnClickListener { printWifi() }
        findViewById<MaterialButton>(R.id.btnPrintOtg).setOnClickListener { printOtg() }

        refreshList()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val uri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                        addTextShare(text)
                    }
                    null
                } else {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        if (uri != null) addUri(uri, silent = false)
    }

    private fun addTextShare(text: String) {
        // 分享的纯文本拆成临时文件过于繁琐，这里提示改用文件方式
        Toast.makeText(this, "请以文件方式分享文本（.txt）后再打印", Toast.LENGTH_LONG).show()
    }

    private fun addUri(uri: Uri, silent: Boolean) {
        val f = FileClassifier.fromUri(contentResolver, uri)
        if (f.type == DocType.UNSUPPORTED || f.type == DocType.DOC) {
            if (!silent) {
                Toast.makeText(this, getString(R.string.msg_unsupported), Toast.LENGTH_SHORT).show()
            }
            return
        }
        // 去重
        if (files.any { it.uri == uri }) return
        files.add(f)
        selectedIndex = files.size - 1
        refreshList()
    }

    private fun refreshList() {
        val names = files.map { "${it.name}  [${typeLabel(it.type)}]" }
        adapter.clear()
        adapter.addAll(names)
        adapter.notifyDataSetChanged()
        emptyHint.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        if (selectedIndex in files.indices) {
            listView.setItemChecked(selectedIndex, true)
        }
    }

    private fun selectedFile(): PrintFile? {
        if (files.isEmpty()) {
            Toast.makeText(this, getString(R.string.hint_empty), Toast.LENGTH_SHORT).show()
            return null
        }
        if (selectedIndex !in files.indices) selectedIndex = 0
        return files[selectedIndex]
    }

    private fun printWifi() {
        val f = selectedFile() ?: return
        val source = DocumentSourceFactory.open(this, f)
            ?: run { Toast.makeText(this, R.string.msg_pick_failed, Toast.LENGTH_SHORT).show(); return }
        wifiPrint.print(source, f.name)
    }

    private fun printOtg() {
        val f = selectedFile() ?: return
        if (!otgPrint.isUsbHostSupported()) {
            Toast.makeText(this, "当前设备不支持 USB OTG Host 功能", Toast.LENGTH_LONG).show()
            return
        }
        val source = DocumentSourceFactory.open(this, f)
            ?: run { Toast.makeText(this, R.string.msg_pick_failed, Toast.LENGTH_SHORT).show(); return }
        otgPrint.print(source, f.name) { result ->
            val text = when (result) {
                is com.ceshi.printtool.print.PrintResult.Success -> result.message
                is com.ceshi.printtool.print.PrintResult.Failure -> "失败：${result.message}"
                is com.ceshi.printtool.print.PrintResult.Info -> result.message
            }
            runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
        }
    }

    private fun typeLabel(type: DocType): String = when (type) {
        DocType.PDF -> getString(R.string.format_pdf)
        DocType.IMAGE -> getString(R.string.format_image)
        DocType.TEXT -> getString(R.string.format_text)
        DocType.DOCX -> getString(R.string.format_docx)
        DocType.DOC -> getString(R.string.format_docx)
        DocType.UNSUPPORTED -> getString(R.string.format_unsupported)
    }
}