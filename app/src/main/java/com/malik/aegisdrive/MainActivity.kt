package com.malik.aegisdrive

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Step 1: Find the bottom navigation bar from our layout
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Step 2: Find the NavHostFragment — this is the container
        // that swaps screens when tabs are tapped
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        // Step 3: Get the NavController — this is the brain
        // that knows which screen to show
        val navController = navHostFragment.navController

        // Step 4: Connect the bottom nav bar to the NavController
        // Now tapping a tab automatically switches the screen!
        bottomNavigationView.setupWithNavController(navController)
    }
}