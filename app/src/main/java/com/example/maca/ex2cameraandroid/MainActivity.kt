package com.example.maca.ex2cameraandroid

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.GuardedBy
import android.support.annotation.Nullable
import android.support.annotation.RequiresApi
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import com.example.maca.ex2cameraandroid.helper.CameraPermissionHelper
import com.example.maca.ex2cameraandroid.helper.DisplayRotationHelper
import com.example.maca.ex2cameraandroid.helper.FullScreenHelper
import com.example.maca.ex2cameraandroid.helper.SnackbarHelper
import com.example.maca.ex2cameraandroid.rendering.BackgroundRender
import com.example.maca.ex2cameraandroid.rendering.ObjectRenderer
import com.example.maca.ex2cameraandroid.rendering.PlaneRenderer
import com.example.maca.ex2cameraandroid.rendering.PointCloudRenderer
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    lateinit var displayRotationHelper: DisplayRotationHelper
    lateinit var  gestureDetector: GestureDetector

    // bloqueio necessario para sinc
    private val singleTapAnchorLock = Any()

    @GuardedBy("singleTapAnchorLock")
    private var queueSingleTap: MotionEvent? = null

    //Componentes ARCORE
    @Nullable
    @GuardedBy("singleTapAnchorLock")
    private var anchor: Anchor? = null

    private var installRequested: Boolean = false
    private var session: Session? = null

    //Interface do usuário e manuseio
    private val snackbarHelper = SnackbarHelper()

    //Os Renderers são criados e inicializados quando o GL surface é criada.
    private val backGroundRender = BackgroundRender()
    private val pointCloudRenderer = PointCloudRenderer()
    private val planeRenderer = PlaneRenderer()
    private val virtualObject = ObjectRenderer()
    private val virtualObjectShadow = ObjectRenderer()
    lateinit var surfaceView: GLSurfaceView

    private val anchorMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val colorCorrectionRgba = FloatArray(4)

    @GuardedBy("singleTapAnchorLock")
    private var appAnchorState = AppAnchorState.NONE

    private enum class AppAnchorState {
        NONE,
        HOSTING,
        HOSTED
    }

    override fun onDrawFrame(gl: GL10?) {
        //Limpar tela para notificar o driver para não carregar nenhum pixel do quadro anterior
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if(session == null) return

        //Ajuste do fundo do vídeo
        displayRotationHelper.updateSessionIfNeeded(session)

        try {
            session!!.setCameraTextureName(backGroundRender.getTextureId())

            //Obtenha o quadro atual do ARSession
            val frame = session!!.update() as Frame
            val camera = frame.getCamera() as Camera
            val cameraTrackingState = camera.getTrackingState() as TrackingState
            checkUpdatedAnchor()

            // Handle taps.
            handleTapOnDraw(cameraTrackingState, frame)

            backGroundRender.draw(frame)

            //Se não estiver rastreando, não desenhe objetos 3d.
            if(cameraTrackingState == TrackingState.PAUSED) return

            //Pegar metrizes do projeto e camera
            camera.getProjectionMatrix(projectionMatrix,0,0.1f,0f)
            camera.getViewMatrix(viewMatrix, 0)

            //Vizualizar pontos rasteadros
            val pointCloud = frame.acquirePointCloud()
            pointCloudRenderer.update(pointCloud)
            pointCloudRenderer.draw(viewMatrix, projectionMatrix)

            pointCloud.release()

            // Visualizar planos.
            planeRenderer.drawPlanes(
                session!!.getAllTrackables(Plane::class.java),
                camera.getDisplayOrientedPose(),
                projectionMatrix)


            // Visualizar anchor.
            var shouldDrawAnchor: Boolean = false
            synchronized(singleTapAnchorLock) {
                if (anchor != null && anchor!!.getTrackingState() == TrackingState.TRACKING) {
                    frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0)

                    // Get the current pose of an Anchor in world space. The Anchor pose is updated
                    // during calls to session.update() as ARCore refines its estimate of the world.
                    anchor!!.getPose().toMatrix(anchorMatrix, 0)
                    shouldDrawAnchor = true
                }
            }

            if (shouldDrawAnchor) {
                val scaleFactor = 1.0f.toFloat()
                frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0)

                // Atualiza e desenha o modelo e sua sombra.
                virtualObject.updateModelMatrix(anchorMatrix, scaleFactor)
                virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor)
                virtualObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba)
                virtualObjectShadow.draw(viewMatrix, projectionMatrix, colorCorrectionRgba)
            }

        }catch (t:Throwable){
            //Evita que o aplicativo quebre
            Log.e("TAG","Exception na thread do OpenGL", t);
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        //Prepara os objetos de renderização. Isso envolve a leitura de shaders, portanto, pode lançar uma IOException.
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backGroundRender.createOnGlThread(/*context=*/ this)
            planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png")
            pointCloudRenderer.createOnGlThread(/*context=*/ this)

            virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png")
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f)

            virtualObjectShadow.createOnGlThread(
                /*context=*/ this, "models/andy_shadow.obj", "models/andy_shadow.png")
            virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow)
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f)
        } catch (ex: IOException) {
            Log.e("TAG", "Failed to read an asset file", ex)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceview)

        displayRotationHelper = DisplayRotationHelper(this)

        gestureDetector = GestureDetector(this,object:GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                synchronized(singleTapAnchorLock) {
                    queueSingleTap = e
                }
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        })

        surfaceView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true)
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used for plane blending.
        surfaceView.setRenderer(this)
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
        installRequested = false

        // Initialize the "Clear" button. Clicking it will clear the current anchor, if it exists.
        clear_button.setOnClickListener {
            synchronized(singleTapAnchorLock) {
                setNewAnchor(null)
            }
            Toast.makeText(this, "FOI", Toast.LENGTH_SHORT).show()
        }
    }

    @GuardedBy("singleTapAnchorLock")
    private fun setNewAnchor(newAnchor: Anchor?) {
        anchor?.detach()
        anchor = newAnchor
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onResume() {
        super.onResume()

        if (session == null) {
            var exception: Exception? = null
            var messageId = -1
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {
                    }
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this)
                    return
                }
                session = Session(this)
            } catch (e: UnavailableArcoreNotInstalledException) {
                messageId = R.string.snackbar_arcore_unavailable
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                messageId = R.string.snackbar_arcore_too_old
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                messageId = R.string.snackbar_arcore_sdk_too_old
                exception = e
            } catch (e: Exception) {
                messageId = R.string.snackbar_arcore_exception
                exception = e
            }

            if (exception != null) {
                snackbarHelper.showError(this, getString(messageId))
                Log.e("TAG", "Exception creating session", exception)
                return
            }

            // Create default config and check if supported.
            val config = Config(session)
            config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED)
            session!!.configure(config)
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session!!.resume()
        } catch (e: CameraNotAvailableException) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            snackbarHelper.showError(this, getString(R.string.snackbar_camera_unavailable))
            session = null
            return
        }

        surfaceView.onResume()
        displayRotationHelper.onResume()

    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onPause() {
        super.onPause()

        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session!!.pause();
        }
    }

    private fun handleTapOnDraw(currentTrackingState: TrackingState, currentFrame: Frame) {
        synchronized(singleTapAnchorLock){
            if(anchor == null
                && queueSingleTap != null
                && currentTrackingState == TrackingState.TRACKING
                && appAnchorState == AppAnchorState.NONE){
                for(hit: HitResult in currentFrame.hitTest(queueSingleTap)){
                    if(shouldCreateAnchorWithHit(hit)){
                        val newAnchor = session!!.hostCloudAnchor(hit.createAnchor())
                        setNewAnchor(newAnchor)

                        appAnchorState = AppAnchorState.HOSTING
                        snackbarHelper.showMessage(this,"agora hospedando Anchor...")
                        break
                    }
                }
            }
            queueSingleTap = null
        }
    }

    fun shouldCreateAnchorWithHit(hit: HitResult): Boolean {
        val trackable = hit.getTrackable()
        if (trackable is Plane) {
            // Check if any plane was hit, and if it was hit inside the plane polygon
            return ((trackable).isPoseInPolygon(hit.getHitPose() as Pose))
        } else if (trackable is Point) {
            // Check if an oriented point was hit.
            return (trackable).getOrientationMode() == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
        }
        return false
    }

    private fun checkUpdatedAnchor() {
        synchronized (singleTapAnchorLock) {
            if (appAnchorState != AppAnchorState.HOSTING) {
                return;
            }
            val cloudState = anchor!!.getCloudAnchorState() as Anchor.CloudAnchorState
            if (cloudState.isError()) {
                snackbarHelper.showMessageWithDismiss(this, "Erro ao hospedar anchor: " + cloudState);
                appAnchorState = AppAnchorState.NONE;
            } else if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
                snackbarHelper.showMessageWithDismiss(
                    this, "Anchor hospedado com successo! Cloud ID: " + anchor!!.getCloudAnchorId());
                appAnchorState = AppAnchorState.HOSTED;
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Permissão da camera é necessária", Toast.LENGTH_LONG)
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }

}
