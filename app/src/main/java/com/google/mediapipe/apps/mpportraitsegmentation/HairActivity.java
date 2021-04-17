package com.google.mediapipe.apps.mpportraitsegmentation;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.Toolbar;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;

import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.glutil.EglManager;
import com.google.mediapipe.framework.Packet;

/**
 * Main activity of MediaPipe example apps.
 */
public class HairActivity extends AppCompatActivity {
    private static final String TAG = "HairActivity";

    /** 设置调色板所需的变量 **/
    private ColorPickerView colorDisk = null;

    private static final String BINARY_GRAPH_NAME = "hair_segmentation.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String RED_INPUT_STREAM ="red";
    private static final String GREEN_INPUT_STREAM ="green";
    private static final String BLUE_INPUT_STREAM ="blue";

    private static CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    /** 初始化的颜色为黑色头发 **/
    protected int red_progress = 0;
    protected int blue_progress = 0;
    protected int green_progress = 0;

    public Packet red_packet;
    public Packet green_packet;
    public Packet blue_packet;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;

    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    private FrameProcessor emptyProcessor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;

    // Handles camera access via the {@link CameraX} Jetpack support library.
    private CameraXPreviewHelper cameraHelper;

    private RGBHandler rgbHandler;

    private ToggleButton toggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hair);
        this.setTitle("发色替换");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        /** 设置调色板 **/
        colorDisk=(ColorPickerView)findViewById(R.id.colorDisk);
        colorDisk.setOnColorBackListener(new ColorPickerView.OnColorBackListener() {
            @Override
            public void onColorBack(int a, int r, int g, int b) {
                red_progress = r;
                green_progress = g;
                blue_progress = b;
            }
        });

        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();
        // Initilize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);

        rgbHandler = new RGBHandler();
        processor.setOnWillAddFrameListener(rgbHandler);

        processor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);
        PermissionHelper.checkAndRequestCameraPermissions(this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        converter = new ExternalTextureConverter(eglManager.getContext(), 2);
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();
        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    previewFrameTexture = surfaceTexture;
                    // Make the display view visible to start showing the preview. This triggers the
                    // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
                    previewDisplayView.setVisibility(View.VISIBLE);
                });
        cameraHelper.startCamera(this, CAMERA_FACING, /*surfaceTexture=*/ null);
    }

    private class RGBHandler implements FrameProcessor.OnWillAddFrameListener
    {
        @Override
        public void onWillAddFrame(long timestamp){
            red_packet = processor.getPacketCreator().createInt32(red_progress);
            green_packet = processor.getPacketCreator().createInt32(green_progress);
            blue_packet = processor.getPacketCreator().createInt32(blue_progress);
            processor.getGraph().addConsumablePacketToInputStream(RED_INPUT_STREAM,
                    red_packet,  timestamp);
            processor.getGraph().addConsumablePacketToInputStream(GREEN_INPUT_STREAM,
                    green_packet, timestamp);
            processor.getGraph().addConsumablePacketToInputStream(BLUE_INPUT_STREAM,
                    blue_packet,  timestamp);
            red_packet.release();
            green_packet.release();
            blue_packet.release();

        }
    }

    /** 点击切换相机**/
    public void changeCamera(View view) {

        String camer_info;

        if(CAMERA_FACING == CameraHelper.CameraFacing.BACK) {
            CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
            camer_info = "前置摄像";
        } else {
            CAMERA_FACING = CameraHelper.CameraFacing.BACK;
            camer_info = "后置摄像";
        }

        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();
        onPause();
        onResume();
        Toast.makeText(HairActivity.this, "已切换至" + camer_info,
                Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                this.finish();
                return true;
            default:
                Toast.makeText(getApplicationContext(),"Back button clicked", Toast.LENGTH_SHORT).show();
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode== KeyEvent.KEYCODE_BACK ){
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            this.finish();
        }
        return super.onKeyDown(keyCode, event);
    }
}
