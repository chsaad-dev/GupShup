package com.example.gupshup.ui.chat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.gupshup.databinding.ActivityStatusFullScreenBinding

class StatusFullScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatusFullScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatusFullScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userName = intent.getStringExtra("userName")
        val text = intent.getStringExtra("text")

        binding.fullScreenUser.text = userName
        binding.fullScreenText.text = text

        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
}
