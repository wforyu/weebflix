package com.weebflix.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.weebflix.app.R
import com.weebflix.app.data.config.ProviderConfig

class SettingsFragment : Fragment() {

    private lateinit var etBaseUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var tvCurrentUrl: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etBaseUrl = view.findViewById(R.id.etBaseUrl)
        btnSave = view.findViewById(R.id.btnSave)
        btnReset = view.findViewById(R.id.btnReset)
        tvCurrentUrl = view.findViewById(R.id.tvCurrentUrl)

        etBaseUrl.setText(ProviderConfig.baseUrl)
        tvCurrentUrl.text = ProviderConfig.baseUrl

        btnSave.setOnClickListener {
            val newUrl = etBaseUrl.text.toString().trim()
            if (newUrl.isEmpty()) {
                etBaseUrl.error = "URL tidak boleh kosong"
                return@setOnClickListener
            }
            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                etBaseUrl.error = "URL harus diawali http:// atau https://"
                return@setOnClickListener
            }
            ProviderConfig.baseUrl = newUrl
            tvCurrentUrl.text = ProviderConfig.baseUrl
            Toast.makeText(requireContext(), "Domain berhasil diperbarui", Toast.LENGTH_SHORT).show()
        }

        btnReset.setOnClickListener {
            ProviderConfig.reset()
            etBaseUrl.setText(ProviderConfig.baseUrl)
            tvCurrentUrl.text = ProviderConfig.baseUrl
            Toast.makeText(requireContext(), "Domain dikembalikan ke default", Toast.LENGTH_SHORT).show()
        }
    }
}
