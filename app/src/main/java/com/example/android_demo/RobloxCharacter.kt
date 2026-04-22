package com.example.android_demo

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class LimbPart(
    val startVertex: Int,
    val vertexCount: Int,
    val pivotX: Float,
    val pivotY: Float,
    val pivotZ: Float,
    val animType: Int // 0=None, 1=Swing Forward, 2=Swing Backward
)

class RobloxCharacter {

    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec2 aTexCoordinate;
        varying vec2 vTexCoordinate;
        uniform mat4 uMVPMatrix;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            vTexCoordinate = aTexCoordinate;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTexCoordinate;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoordinate);
        }
    """.trimIndent()

    private val vertexBuffer: FloatBuffer
    private val uvBuffer: FloatBuffer
    private val mProgram: Int
    private var textureId: Int = 0

    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private val coordsPerVertex = 3
    private val vertexStride = coordsPerVertex * 4
    private val uvStride = 2 * 4

    private val vertices = mutableListOf<Float>()
    private val uvs = mutableListOf<Float>()
    private val parts = mutableListOf<LimbPart>()
    
    private var animationTime = 0f

    init {
        // Hàm hỗ trợ để nhóm các vertices vào Part tương ứng
        fun addPart(pivotX: Float, pivotY: Float, pivotZ: Float, animType: Int, buildBlock: () -> Unit) {
            val start = vertices.size / 3
            buildBlock()
            val count = (vertices.size / 3) - start
            parts.add(LimbPart(start, count, pivotX, pivotY, pivotZ, animType))
        }

        // Xây dựng các khối cơ thể mô phỏng R6 character
        // Torso: Center(0, 0, 0), Width=2, Height=2, Depth=1
        addPart(0f, 0f, 0f, 0) { // Không vung
            addBox(-1.0f, 1.0f, -0.5f, 1.0f, -1.0f, 0.5f, 
                   232, 74, 128, 128, 64, 64) // Torso UV
        }

        // Left Arm: Center(1.5, 0, 0), Pivot ngay tại vai (y=1.0)
        addPart(1.5f, 1.0f, 0f, 1) { // Lắc tới
            addLimb(1.0f, 1.0f, -0.5f, 2.0f, -1.0f, 0.5f, isRightLimb = true)
        }
               
        // Right Arm: Center(-1.5, 0, 0), Pivot ngay tại vai (y=1.0)
        addPart(-1.5f, 1.0f, 0f, 2) { // Lắc lùi
            addLimb(-2.0f, 1.0f, -0.5f, -1.0f, -1.0f, 0.5f, isRightLimb = false)
        }
               
        // Left Leg: Center(0.5, -2, 0), Pivot ngay tại hông (y=-1.0)
        addPart(0.5f, -1.0f, 0f, 2) { // Đi ngược với Left Arm
            addLimb(0.0f, -1.0f, -0.5f, 1.0f, -3.0f, 0.5f, isRightLimb = true)
        }
               
        // Right Leg: Center(-0.5, -2, 0), Pivot ngay tại hông (y=-1.0)
        addPart(-0.5f, -1.0f, 0f, 1) { // Đi ngược với Right Arm
            addLimb(-1.0f, -1.0f, -0.5f, 0.0f, -3.0f, 0.5f, isRightLimb = false)
        }

        // Head: Center(0, 1.5, 0), Trục cổ (y=1.0)
        addPart(0f, 1.0f, 0f, 0) { // Không vung
            addCylinder(0.0f, 1.5f, 0.0f, 0.6f, 1.0f, 24, 0, 0, 10, 10) 
        }
        
        val vertexArray = vertices.toFloatArray()
        vertexBuffer = ByteBuffer.allocateDirect(vertexArray.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(vertexArray)
                position(0)
            }
        }

        val uvArray = uvs.toFloatArray()
        uvBuffer = ByteBuffer.allocateDirect(uvArray.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(uvArray)
                position(0)
            }
        }

        val vertexShader: Int = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    private fun addCylinder(cx: Float, cy: Float, cz: Float, r: Float, h: Float, segments: Int, 
                            uX: Int, uY: Int, wPix: Int, hPix: Int) {
        val tW = 585f
        val tH = 559f
        val uL = uX / tW
        val vT = uY / tH
        val uR = (uX + wPix) / tW
        val vB = (uY + hPix) / tH
        
        val halfH = h / 2f
        val topY = cy + halfH
        val bottomY = cy - halfH

        // Tạo mảng toạ độ x, z cho hình tròn
        val circleX = FloatArray(segments + 1)
        val circleZ = FloatArray(segments + 1)
        for (i in 0..segments) {
            val angle = 2.0 * Math.PI * i / segments
            circleX[i] = cx + (r * Math.cos(angle)).toFloat()
            circleZ[i] = cz + (r * Math.sin(angle)).toFloat()
        }

        for (i in 0 until segments) {
            val x1 = circleX[i]
            val z1 = circleZ[i]
            val x2 = circleX[i + 1]
            val z2 = circleZ[i + 1]
            
            val u1 = uL + (uR - uL) * (i.toFloat() / segments)
            val u2 = uL + (uR - uL) * ((i + 1).toFloat() / segments)

            // Side face (2 triangles)
            // Triangle 1: (x1, topY, z1) -> (x1, bottomY, z1) -> (x2, topY, z2)
            vertices.addAll(listOf(x1, topY, z1, x1, bottomY, z1, x2, topY, z2))
            uvs.addAll(listOf(u1, vT, u1, vB, u2, vT))
            // Triangle 2: (x2, topY, z2) -> (x1, bottomY, z1) -> (x2, bottomY, z2)
            vertices.addAll(listOf(x2, topY, z2, x1, bottomY, z1, x2, bottomY, z2))
            uvs.addAll(listOf(u2, vT, u1, vB, u2, vB))

            // Top Cap (Triangle: center -> circle1 -> circle2)
            vertices.addAll(listOf(cx, topY, cz, x1, topY, z1, x2, topY, z2))
            uvs.addAll(listOf((uL+uR)/2, (vT+vB)/2, u1, vT, u2, vT))

            // Bottom Cap (Triangle: center -> circle2 -> circle1) (đảo ngược để culling đúng)
            vertices.addAll(listOf(cx, bottomY, cz, x2, bottomY, z2, x1, bottomY, z1))
            uvs.addAll(listOf((uL+uR)/2, (vT+vB)/2, u2, vB, u1, vB))
        }
    }

    private fun addBox(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
                        uX: Int, uY: Int, wFont: Int, hFront: Int, wSide: Int, dTop: Int) {
        // Helpers for 6 faces
        // uX, uY là top-left của phần pixel trong file 585x559 của mặt trước
        // Chiều rộng tổng file là 585, cao là 559.
        val tW = 585f
        val tH = 559f
        
        fun convertU(pixelX: Int): Float = pixelX / tW
        fun convertV(pixelY: Int): Float = pixelY / tH 
        // Luv, Ruv, Tuv, Buv cho từng mặt (Mặt Front)
        
        // FRONT
        addFace(x1, y1, z2, x2, y1, z2, x1, y2, z2, x2, y2, z2, // Pos
                convertU(uX), convertV(uY), convertU(uX + wFont), convertV(uY + hFront)) // UV
        
        // BACK
        addFace(x2, y1, z1, x1, y1, z1, x2, y2, z1, x1, y2, z1,
                convertU(uX + wFont + wSide), convertV(uY), convertU(uX + wFont + wSide + wFont), convertV(uY + hFront))
        
        // LEFT (Nhìn từ phía nhân vật)
        addFace(x2, y1, z2, x2, y1, z1, x2, y2, z2, x2, y2, z1,
                convertU(uX + wFont), convertV(uY), convertU(uX + wFont + wSide), convertV(uY + hFront))
        
        // RIGHT
        addFace(x1, y1, z1, x1, y1, z2, x1, y2, z1, x1, y2, z2,
                convertU(uX - wSide), convertV(uY), convertU(uX), convertV(uY + hFront))
                
        // TOP
        addFace(x1, y1, z1, x2, y1, z1, x1, y1, z2, x2, y1, z2,
                convertU(uX), convertV(uY - dTop), convertU(uX + wFont), convertV(uY))
                
        // BOTTOM
        addFace(x1, y2, z2, x2, y2, z2, x1, y2, z1, x2, y2, z1,
                convertU(uX), convertV(uY + hFront), convertU(uX + wFont), convertV(uY + hFront + dTop))
    }

    private fun addLimb(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, isRightLimb: Boolean) {
        val tW = 585f
        val tH = 559f
        fun convertU(pixelX: Int): Float = pixelX / tW
        fun convertV(pixelY: Int): Float = pixelY / tH 
        
        val wSide = 64
        val hFront = 128
        val dTop = 64
        
        val yBase = 355
        
        val uF: Int; val uL: Int; val uR: Int; val uB: Int; val uxTop: Int; val uxBot: Int
        if (isRightLimb) {
            uL = 19; uB = 85; uR = 151; uF = 217
            uxTop = 217; uxBot = 217
        } else {
            uF = 308; uL = 374; uB = 440; uR = 506
            uxTop = 308; uxBot = 308
        }
        
        val vTop = yBase - dTop
        val vBot = yBase + hFront

        // FRONT
        addFace(x1, y1, z2, x2, y1, z2, x1, y2, z2, x2, y2, z2,
                convertU(uF), convertV(yBase), convertU(uF + wSide), convertV(yBase + hFront))
        
        // BACK
        addFace(x2, y1, z1, x1, y1, z1, x2, y2, z1, x1, y2, z1,
                convertU(uB), convertV(yBase), convertU(uB + wSide), convertV(yBase + hFront))
        
        // LEFT (Nhìn từ phía nhân vật)
        addFace(x2, y1, z2, x2, y1, z1, x2, y2, z2, x2, y2, z1,
                convertU(uL), convertV(yBase), convertU(uL + wSide), convertV(yBase + hFront))
        
        // RIGHT
        addFace(x1, y1, z1, x1, y1, z2, x1, y2, z1, x1, y2, z2,
                convertU(uR), convertV(yBase), convertU(uR + wSide), convertV(yBase + hFront))
                
        // TOP
        addFace(x1, y1, z1, x2, y1, z1, x1, y1, z2, x2, y1, z2,
                convertU(uxTop), convertV(vTop), convertU(uxTop + wSide), convertV(yBase))
                
        // BOTTOM
        addFace(x1, y2, z2, x2, y2, z2, x1, y2, z1, x2, y2, z1,
                convertU(uxBot), convertV(vBot), convertU(uxBot + wSide), convertV(vBot + dTop))
    }

    private fun addFace(xTL: Float, yTL: Float, zTL: Float, 
                        xTR: Float, yTR: Float, zTR: Float,
                        xBL: Float, yBL: Float, zBL: Float, 
                        xBR: Float, yBR: Float, zBR: Float,
                        uL: Float, vT: Float, uR: Float, vB: Float) {
        // Triangle 1 (TL, BL, TR)
        vertices.addAll(listOf(xTL, yTL, zTL, xBL, yBL, zBL, xTR, yTR, zTR))
        uvs.addAll(listOf(uL, vT, uL, vB, uR, vT))
        
        // Triangle 2 (TR, BL, BR)
        vertices.addAll(listOf(xTR, yTR, zTR, xBL, yBL, zBL, xBR, yBR, zBR))
        uvs.addAll(listOf(uR, vT, uL, vB, uR, vB))
    }

    fun loadTexture(context: Context, resourceId: Int) {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        
        if (textureIds[0] == 0) return
        
        val options = BitmapFactory.Options()
        options.inScaled = false
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options) ?: return
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        
        textureId = textureIds[0]
    }

    fun draw(mvpMatrix: FloatArray) {
        GLES20.glUseProgram(mProgram)
        
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(it, coordsPerVertex, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        }
        
        texCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoordinate").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(it, 2, GLES20.GL_FLOAT, false, uvStride, uvBuffer)
        }
        
        mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        val texUniform = GLES20.glGetUniformLocation(mProgram, "uTexture")
        GLES20.glUniform1i(texUniform, 0)
        
        animationTime += 0.05f
        val swingMagnitude = 45f // Góc đi bộ 45 độ
        val swingAngle = (Math.sin(animationTime.toDouble()) * swingMagnitude).toFloat()

        for (part in parts) {
            val modelMatrix = FloatArray(16)
            android.opengl.Matrix.setIdentityM(modelMatrix, 0)

            var rotX = 0f
            if (part.animType == 1) rotX = swingAngle
            else if (part.animType == 2) rotX = -swingAngle

            if (rotX != 0f) {
                // Di chuyển pivot về origin, xoay, rồi đẩy lại vị trí cũ
                android.opengl.Matrix.translateM(modelMatrix, 0, part.pivotX, part.pivotY, part.pivotZ)
                android.opengl.Matrix.rotateM(modelMatrix, 0, rotX, 1f, 0f, 0f)
                android.opengl.Matrix.translateM(modelMatrix, 0, -part.pivotX, -part.pivotY, -part.pivotZ)
            }

            // Gộp matrix Animation local với MVP matrix global từ camera
            val finalMVP = FloatArray(16)
            android.opengl.Matrix.multiplyMM(finalMVP, 0, mvpMatrix, 0, modelMatrix, 0)

            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, finalMVP, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, part.startVertex, part.vertexCount)
        }
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
