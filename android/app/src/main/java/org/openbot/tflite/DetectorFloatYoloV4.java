package org.openbot.tflite;

import android.app.Activity;
import android.graphics.RectF;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DetectorFloatYoloV4 extends Detector {

  /** Additional normalization of the used input. */
  private static final float IMAGE_MEAN = 0.0f;

  private static final float IMAGE_STD = 255.0f;

  // Only return this many results.
  private static final int NUM_DETECTIONS = 2535;

  // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
  // contains the location of detected boxes
  private float[][][] outputLocations;
  // outputScores: array of shape [Batchsize, NUM_DETECTIONS,labels.size()]
  // contains the scores of detected boxes
  private float[][][] outputScores;

  //  //config yolov4
  //  private static final int INPUT_SIZE = 416;
  //  private static final int[] OUTPUT_WIDTH = new int[]{52, 26, 13};
  //
  //  private static final int[][] MASKS = new int[][]{{0, 1, 2}, {3, 4, 5}, {6, 7, 8}};
  //  private static final int[] ANCHORS = new int[]{
  //          12, 16, 19, 36, 40, 28, 36, 75, 76, 55, 72, 146, 142, 110, 192, 243, 459, 401
  //  };
  //  private static final float[] XYSCALE = new float[]{1.2f, 1.1f, 1.05f};
  //
  //  private static final int NUM_BOXES_PER_BLOCK = 3;
  //
  //  // config yolov4 tiny
  //  private static final int[] OUTPUT_WIDTH_TINY = new int[]{2535, 2535};
  //  private static final int[] OUTPUT_WIDTH_FULL = new int[]{10647, 10647};
  //  private static final int[][] MASKS_TINY = new int[][]{{3, 4, 5}, {1, 2, 3}};
  //  private static final int[] ANCHORS_TINY = new int[]{
  //          23, 27, 37, 58, 81, 82, 81, 82, 135, 169, 344, 319};
  //  private static final float[] XYSCALE_TINY = new float[]{1.05f, 1.05f};

  /**
   * An array to hold inference results, to be feed into Tensorflow Lite as outputs. This isn't part
   * of the super class, because we need a primitive array here.
   */
  private byte[][] labelProbArray = null;

  /**
   * Initializes a {@code ClassifierQuantizedMobileNet}.
   *
   * @param activity
   */
  public DetectorFloatYoloV4(Activity activity, Model model, Device device, int numThreads)
      throws IOException {
    super(activity, model, device, numThreads);
    labelProbArray = new byte[1][getNumLabels()];
  }

  @Override
  public boolean getMaintainAspect() {
    return false;
  }

  @Override
  public RectF getCropRect() {
    return new RectF(0.0f, 0.0f, 0.0f, 0.0f);
  }

  @Override
  public int getImageSizeX() {
    return 416;
  }

  @Override
  public int getImageSizeY() {
    return 416;
  }

  @Override
  protected String getModelPath() {
    // you can download this file from
    // see build.gradle for where to obtain this file. It should be auto
    // downloaded into assets.
    return "networks/yolo_v4_tiny_float_coco.tflite";
  }

  @Override
  protected String getLabelPath() {
    return "networks/coco.txt";
  }

  @Override
  protected int getNumBytesPerChannel() {
    // the float model uses a four bytes
    return 4;
  }

  @Override
  protected final int getNumDetections() {
    return NUM_DETECTIONS;
  }

  @Override
  protected void addPixelValue(int pixelValue) {
    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
  }

  @Override
  protected void runInference() {
    // tflite.run(imgData, labelProbArray);
    Object[] inputArray = {imgData};
    tflite.runForMultipleInputsOutputs(inputArray, outputMap);
  }

  @Override
  protected void feedData() {
    outputLocations = new float[1][getNumDetections()][4];
    outputScores = new float[1][getNumDetections()][labels.size()];

    outputMap.put(0, outputLocations);
    outputMap.put(1, outputScores);
  }

  @Override
  protected List<Recognition> getRecognitions() {
    // Show the best detections.
    // after scaling them back to the input size.
    final ArrayList<Recognition> recognitions = new ArrayList<>(getNumDetections());
    for (int i = 0; i < getNumDetections(); ++i) {
      float maxClass = 0;
      int detectedClass = -1;
      final float[] classes = new float[labels.size()];
      System.arraycopy(outputScores[0][i], 0, classes, 0, labels.size());
      for (int c = 0; c < labels.size(); ++c) {
        if (classes[c] > maxClass) {
          detectedClass = c;
          maxClass = classes[c];
        }
      }
      final float score = maxClass;
      if (detectedClass == 0) { // only consider persons
        final float xPos = outputLocations[0][i][0];
        final float yPos = outputLocations[0][i][1];
        final float w = outputLocations[0][i][2];
        final float h = outputLocations[0][i][3];
        final RectF detection =
            new RectF(
                Math.max(0, xPos - w / 2),
                Math.max(0, yPos - h / 2),
                Math.min(getImageSizeX() - 1, xPos + w / 2),
                Math.min(getImageSizeY() - 1, yPos + h / 2));
        recognitions.add(
            new Recognition("" + i, labels.get(detectedClass), score, detection, detectedClass));
      }
    }
    return nms(recognitions);
  }
}
