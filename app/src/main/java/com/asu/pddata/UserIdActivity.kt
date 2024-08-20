package com.asu.pddata

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.asu.pddata.databinding.ActivityUserIdBinding

class UserIdActivity : Activity() {


    private lateinit var binding: ActivityUserIdBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserIdBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.submitBtn.setOnClickListener {
            val userId = binding.userIdEdtv.text.toString()
            if (userId.isNotEmpty()) {
                val sharedPref = getSharedPreferences("pddata", MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("userId", userId)
                    apply()
                }
                startActivity(Intent(this, MainActivity::class.java))
            }
        }
    }

}