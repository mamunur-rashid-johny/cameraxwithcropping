package com.johny.cameraxwithcropping

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import coil3.load
import com.google.common.util.concurrent.ListenableFuture
import com.johny.cameraxwithcropping.databinding.FragmentCameraAndCroppingBinding
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraAndCropperFragment : Fragment(R.layout.fragment_camera_and_cropping) {
    private var _binding: FragmentCameraAndCroppingBinding? = null
    private val binding get() = _binding!!

    //camerax related
    private var camera: Camera? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraProvider: ProcessCameraProvider
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode: Int = ImageCapture.FLASH_MODE_OFF


    //cropper related
    private var croppedImageUri: Uri? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraAndCroppingBinding.inflate(inflater, container, false)

        setupPreview()
        initListener()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }




        return binding.root
    }

    //app related
    private fun setupPreview() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProvider = cameraProviderFuture.get()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun initListener() {

        //camera layout
        binding.layoutCamera.btnSwitchCamera.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_BACK == lensFacing) {

                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            flashMode = ImageCapture.FLASH_MODE_OFF
            startCamera()
        }

        binding.layoutCamera.btnTakePicture.setOnClickListener {
            takePhoto()
        }

        binding.layoutCamera.btnChaneFlashMode.setOnClickListener {
            changeFlashMode()
        }

        //cropper layout
        binding.layoutCropper.cancelBtn.setOnClickListener {
            binding.layoutCropper.root.visibility = View.GONE
            binding.layoutCamera.root.visibility = View.VISIBLE
            startCamera()
        }

        binding.layoutCropper.okBtn.setOnClickListener {
            croppedImage()
            binding.layoutCropper.root.visibility = View.GONE
            binding.layoutImagePreview.root.visibility = View.VISIBLE
            croppedImageUri?.let {
                binding.layoutImagePreview.croppedImage.load(it)
            }
        }

        //image preview layout
        binding.layoutImagePreview.cancelBtn.setOnClickListener {
            binding.layoutImagePreview.root.visibility = View.GONE
            binding.layoutCamera.root.visibility = View.VISIBLE
            startCamera()
        }


        binding.layoutImagePreview.submitBtn.setOnClickListener {
            //take action depend on your requirement with cropped image
            croppedImageUri?.let {

            }
        }

        //back press


    }

    //permission related
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(requireContext(), "Permission request denied", Toast.LENGTH_SHORT)
                    .show()
            } else {
                startCamera()
            }
        }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }


    //camera related
    private fun changeFlashIcon() {
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            binding.layoutCamera.btnChaneFlashMode.visibility = View.VISIBLE
            val icon = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> {
                    R.drawable.ic_flash_off
                }

                ImageCapture.FLASH_MODE_ON -> {
                    R.drawable.ic_flash_on
                }

                ImageCapture.FLASH_MODE_AUTO -> {
                    R.drawable.ic_flash_auto
                }

                else -> {
                    null
                }
            }

            icon?.let {
                binding.layoutCamera.btnChaneFlashMode.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        it
                    )
                )
            }
        } else {
            binding.layoutCamera.btnChaneFlashMode.visibility = View.GONE
        }

    }

    private fun changeFlashMode() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> {
                ImageCapture.FLASH_MODE_AUTO
            }

            ImageCapture.FLASH_MODE_AUTO -> {
                ImageCapture.FLASH_MODE_ON
            }

            else -> {
                ImageCapture.FLASH_MODE_OFF
            }
        }
        startCamera()
    }

    private fun startCamera() {
        binding.layoutCamera.root.visibility = View.VISIBLE
        cameraProviderFuture.addListener({
            val viewFinder = binding.layoutCamera.preview
            preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = viewFinder.surfaceProvider
                }
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d(TAG, "Average luminosity: $luma")
                    })
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
                changeFlashIcon()
            } catch (ex: Exception) {
                Log.e(TAG, "Use case binding failed", ex)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val name =
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Foodi-Raider")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .setMetadata(getMetadata())
            .build()


        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Log.d(TAG, msg)
                    cameraProvider.unbind(preview)
                    output.savedUri?.let {
                        openImageCropper(it)
                    }

                }
            }
        )

    }


    private fun getMetadata(): ImageCapture.Metadata {
        val metadata = ImageCapture.Metadata()
        metadata.isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
        return metadata
    }


    //image cropper related

    fun openImageCropper(imageUri: Uri) {
        binding.layoutCamera.root.visibility = View.GONE
        binding.layoutCropper.root.visibility = View.VISIBLE
        flashMode = ImageCapture.FLASH_MODE_OFF
        val degree = rectifyImage(requireContext(), imageUri)
        val bitmap = makeBitmapFromUri(imageUri)
        bitmap?.let {
            binding.layoutCropper.cropView.initialize(it, degree)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun rectifyImage(context: Context, uri: Uri): Int {
        return try {
            val input: InputStream? = context.contentResolver.openInputStream(uri)
            val ei: ExifInterface? =
                if (Build.VERSION.SDK_INT > 23) input?.let { ExifInterface(it) } else uri.path?.let {
                    ExifInterface(it)
                }
            input?.close()
            when (ei?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

        } catch (e: java.lang.Exception) {
            0
        }
    }

    private fun makeBitmapFromUri(uri: Uri): Bitmap? {
        var bitmap: Bitmap? = null
        try {
            val ins = requireContext().contentResolver.openInputStream(uri)
            bitmap = BitmapFactory.decodeStream(ins)
            ins?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Load Bitmap(ImageCropper Section): %s", e)
        }
        return bitmap
    }

    private fun croppedImage() {
        val bitmap = binding.layoutCropper.cropView.crop()
        bitmap?.let {
            croppedImageUri = saveToCache(it)
        }
    }

    private fun saveToCache(bitmap: Bitmap): Uri {
        val timeMills = System.currentTimeMillis()
        val fileName = "$timeMills.jpg"
        val cacheFile = File(requireContext().cacheDir, fileName)
        val out: OutputStream = FileOutputStream(cacheFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        out.flush()
        out.close()
        return Uri.fromFile(cacheFile)
    }

    //image preview related


    companion object {
        private const val TAG = "CAMERAX"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
