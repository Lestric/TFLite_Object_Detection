/*
 * 
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This File is changes by Lestric
 */

package org.tensorflow.lite.examples.detection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;



/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 *
 */


public class DetectorActivity extends CameraAudioActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged SSD model.
  private static final int TF_OD_API_INPUT_SIZE = 300;
  private static final boolean TF_OD_API_IS_QUANTIZED = true;
  private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
  private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;
  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
  private static final boolean MAINTAIN_ASPECT = false;
  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;
  private Integer sensorOrientation;

  private Detector detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private BorderedText borderedText;

  private static List<Detector.Recognition> resultsForAudio = new ArrayList<Detector.Recognition>();



  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;

    try {
      detector =
          TFLiteObjectDetectionAPIModel.create(
              this,
              TF_OD_API_MODEL_FILE,
              TF_OD_API_LABELS_FILE,
              TF_OD_API_INPUT_SIZE,
              TF_OD_API_IS_QUANTIZED);
      cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      e.printStackTrace();
      LOGGER.e(e, "Exception initializing Detector!");
      Toast toast =
          Toast.makeText(
              getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
        ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            tracker.draw(canvas);
            if (isDebug()) {
              tracker.drawDebug(canvas);
            }
          }
        });

    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
  }

  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

    runInBackground(
        new Runnable() {
          @Override
          public void run() {
            LOGGER.i("Running detection on image " + currTimestamp);
            final long startTime = SystemClock.uptimeMillis();
            //Here call for the Obj detection Model to process the Image!! ~Lestric
            final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2.0f);

            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
            switch (MODE) {
              case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
            }

            //List with the Recognition results
            final List<Detector.Recognition> mappedRecognitions =
                new ArrayList<Detector.Recognition>();


            resultsForAudio.clear();
            int i = 0;

            for (final Detector.Recognition result : results) {
              final RectF location = result.getLocation();
              if (location != null && result.getConfidence() >= minimumConfidence) {
                canvas.drawRect(location, paint);

                cropToFrameTransform.mapRect(location);

                result.setLocation(location);
                mappedRecognitions.add(result);
                resultsForAudio.add( i++, result);
              }
            }

            tracker.trackResults(mappedRecognitions, currTimestamp);
            trackingOverlay.postInvalidate();

            computingDetection = false;

            runOnUiThread(
                new Runnable() {
                  @Override
                  public void run() {
                    showFrameInfo(previewWidth + "x" + previewHeight);
                    showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                    showInference(lastProcessingTimeMs + "ms");
                  }
                });
          }
        });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.tfe_od_camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.
  private enum DetectorMode {
    TF_OD_API;
  }

  @Override
  protected void setUseNNAPI(final boolean isChecked) {
    runInBackground(
        () -> {
          try {
            detector.setUseNNAPI(isChecked);
          } catch (UnsupportedOperationException e) {
            LOGGER.e(e, "Failed to set \"Use NNAPI\".");
            runOnUiThread(
                () -> {
                  Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
          }
        });
  }

  @Override
  protected void setNumThreads(final int numThreads) {
    runInBackground(
        () -> {
          try {
            detector.setNumThreads(numThreads);
          } catch (IllegalArgumentException e) {
            LOGGER.e(e, "Failed to set multithreads.");
            runOnUiThread(
                () -> {
                  Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
          }
        });
  }


  // From here on new Code by Lestric for the Audio Return function
  // The results of the object detection are ordered by their position in the image.
  // The audiooutput then uses this ordered sequnece.

  protected static HashMap<Integer, String> getTextForAudio(){

    int objectCounter = 0;

    //Depth information from google ARcore Frame... is only possible for new Android Smartphones which support the google ARcore...
    //Image depthImage = getDepthImage();

    //Bubble Sort of Recognition Results by the criteria: "From the Bottom to the Top of the Image"
    resultsForAudio = sort((ArrayList<Detector.Recognition>) resultsForAudio);


    //Location of RectF for Rectangle of Bounding Box is defined in Class: TFLiteObjectDetectionAPIModel in Line 228ff.
    //The Values are in Order: left, top, right, bottom
    //The point at the bottom (right) is used to estimate the distance and order the output sequence

    String detectedObjectsStringDistance1 = " ";
    String detectedObjectsStringDistance2 = " ";
    String detectedObjectsStringDistance3 = " ";

    for( Detector.Recognition result : resultsForAudio){

      objectCounter++;

      System.out.println(result.getTitle() + ", right:" + result.getLocation().right);
      System.out.println(result.getTitle() + ", centerX:" + result.getLocation().centerX() + ", centerY:" + result.getLocation().centerY());


      //Distance < 50cm
      if(result.getLocation().right > 530) {
        detectedObjectsStringDistance1 = detectedObjectsStringDistance1 + ". " + String.format(result.getTitle()) + ", ";
        continue;
      }


      //50cm < Distance < 100cm
      if(result.getLocation().right > 380) {
        detectedObjectsStringDistance2 = detectedObjectsStringDistance2 + ". " + String.format(result.getTitle()) + ", ";
        continue;
      }

      //Distance > 100cm
      if(result.getLocation().right < 380) {
        detectedObjectsStringDistance3 = detectedObjectsStringDistance3 + ". " + String.format(result.getTitle()) + ", ";
        continue;
      }



    }




    // The output strings are ordered by the estimated distances 1 (<100cm), 2(<200cm) and 3 (>200cm) and here the output strings are put into the hashmap
    // for audio out...

    HashMap h = new HashMap<Integer, String>();
    h.put(1 , detectedObjectsStringDistance1);
    h.put(2 , detectedObjectsStringDistance2);
    h.put(3 , detectedObjectsStringDistance3);


    return h;
  }

  public static ArrayList<Detector.Recognition> sort(ArrayList<Detector.Recognition> arrayList) {


    /*
    Sort Result List by the bounding box locations...

    The incoming videostream must have been turned to the left to process the image or else the variables left, right, top, bottom are reversed...
    Thats because the variable of RectF.right describes the bottom location of the detected objects in my pictures... Thats why I changed the sort criteria to "location.right"
    I want to sort the nearer Objects before the ones more far away. So I want to have Objects which are deeper in the image (more at the bottom of the image) before the objects more at the top (probably more far away)

    Anyways:
    the variables of RectF are like that in the "real" incoming Camera Image:

    RectF Variable |   real position...
          bottom   =     left
          top      =     right
          right    =    bottom
          left     =     top
     */

    Collections.sort(arrayList, new Comparator<Detector.Recognition>() {
      @Override
      public int compare(Detector.Recognition o1, Detector.Recognition o2) {

        if(o1.getLocation().right < o2.getLocation().right){
          return 1;
        } else {
          if(o1.getLocation().right == o2.getLocation().right){
            return 0;
          } else{
            return -1;
          }
        }
      }
    });

    return arrayList;
  }



  /*
  Method to get distance Values from a depth map for a pixel at position x,y
  Only works with google ARcore...
  // Frame Z-Value only works for special new Android Smartphones which support the google ARcore and depth information in images
  This would have been a great method for depth estimation but it doesnt work here.

  public int getMillimetersDepth(Image depthImage, int x, int y) {
    // The depth image has a single plane, which stores depth for each
    // pixel as 16-bit unsigned integers.
    Image.Plane plane = depthImage.getPlanes()[0];
    int byteIndex = x * plane.getPixelStride() + y * plane.getRowStride();
    ByteBuffer buffer = plane.getBuffer().order(ByteOrder.nativeOrder());
    short depthSample = buffer.getShort(byteIndex);
    return depthSample;
  }


  // Frame Z-Value only works for special new Android Smartphones which support the google ARcore and depth information in images
  protected Image getDepthImage() {

    Frame frame = new Frame(new Session(this));

    return Frame.acquireCameraImage();
  }

   */

}
