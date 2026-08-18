package com.mvnsh.citizenship

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.mvnsh.citizenship.databinding.ActivityMainBinding

/**
 * The app is single-Activity: this hosts the nav graph and the bottom navigation.
 * Task 5 wires the NavController, the onboarding gate, theming and the snackbar host.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
