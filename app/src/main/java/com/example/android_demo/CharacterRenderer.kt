package com.example.android_demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CharacterRenderer(private val context: Context, private val textureResId: Int) : GLSurfaceView.Renderer {

    private lateinit var character: RobloxCharacter
    
    // VP Matrix
    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    
    private var angle = 0f

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.2f, 0.2f, 0.3f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        character = RobloxCharacter()
        character.loadTexture(context, textureResId)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Cấu hình View (Camera) lùi xa hơn để nhân vật trông nhỏ lại
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 14f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)
        
        // Quay mô hình
        val scratch = FloatArray(16)
        Matrix.setRotateM(rotationMatrix, 0, angle, 0f, 1f, 0f)
        Matrix.multiplyMM(scratch, 0, viewMatrix, 0, rotationMatrix, 0)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, scratch, 0)
        
        character.draw(vPMatrix)
        
        angle += 0.5f // tự động quay
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 30f)
    }
}
