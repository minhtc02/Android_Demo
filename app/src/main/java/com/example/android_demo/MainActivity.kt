package com.example.android_demo

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.android_demo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var glView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Bạn có thể đổi R.drawable.draw thành id của tệp texture quần áo bạn tải lên res/drawable
        val textureResId = R.drawable.frog
        
        // Hiển thị ảnh 2D
        binding.imgTexture.setImageResource(textureResId)

        // Cấu hình 3D OpenGL Surface
        glView = binding.glSurfaceView
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(CharacterRenderer(this, textureResId))
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }
}
