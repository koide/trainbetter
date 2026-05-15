package com.ditrain.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ditrain.app.ui.home.HomeViewController

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(HomeViewController(this).buildView())
    }
}
