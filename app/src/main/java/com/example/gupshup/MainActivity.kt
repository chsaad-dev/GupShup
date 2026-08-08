package com.example.gupshup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.gupshup.databinding.ActivityMainBinding
import com.example.gupshup.ui.auth.RegisterActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openRegisterButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            com.example.gupshup.util.ActivityTransitionUtil.applyFadeTransition(this)
        }
    }
}
