import React, { useRef, useState } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  Text,
  Dimensions,
  Alert, // Import Alert for native alerts
} from 'react-native';
import Svg, { Path } from 'react-native-svg';
import Slider from '@react-native-community/slider';
// Import react-native-view-shot to capture the drawing as an image
import ViewShot from 'react-native-view-shot';

const { width } = Dimensions.get('window');
const CANVAS_SIZE = width - 40;
// Define a white background color. ML models work best with non-transparent images.
const CANVAS_BACKGROUND_COLOR = '#FFFFFF';

/**
 * DrawingCanvas Component
 * Allows users to draw digits on a canvas with adjustable brush size
 * Captures the drawing as a base64 image for recognition.
 */
const DrawingCanvas = ({ onDrawingComplete }) => {
  const [paths, setPaths] = useState([]);
  const [currentPath, setCurrentPath] = useState('');
  const [isDrawing, setIsDrawing] = useState(false);
  const [brushSize, setBrushSize] = useState(15);

  // Create a ref to attach to the ViewShot component
  const viewShotRef = useRef(null);

  const handleTouchStart = (event) => {
    const { locationX, locationY } = event.nativeEvent;
    setCurrentPath(`M${locationX},${locationY}`);
    setIsDrawing(true);
  };

  const handleTouchMove = (event) => {
    if (!isDrawing) return;

    const { locationX, locationY } = event.nativeEvent;
    setCurrentPath((prevPath) => `${prevPath} L${locationX},${locationY}`);
  };

  const handleTouchEnd = () => {
    if (currentPath) {
      setPaths([...paths, { path: currentPath, strokeWidth: brushSize }]);
      setCurrentPath('');
    }
    setIsDrawing(false);
  };

  const clearCanvas = () => {
    setPaths([]);
    setCurrentPath('');
    setIsDrawing(false);
  };

  /**
   * Capture the drawing as a base64 encoded image
   */
  const captureDrawing = async () => {
    if (paths.length === 0 && !currentPath) {
      // Use Alert.alert for cross-platform alerts
      Alert.alert('Empty Canvas', 'Please draw something first!');
      return;
    }

    try {
      // Use the ref to capture the component
      // This returns a promise that resolves with the base64 string
      const base64Image = await viewShotRef.current.capture({
        format: 'png', // Output format
        quality: 0.9, // Image quality
        result: 'base64', // Return a base64 string
        // Note: Your ML model might expect a specific size (e.g., 28x28 for MNIST).
        // The backend service might handle resizing. If not, you may need to
        // resize the image here or on the backend.
        // We pass the full-size canvas capture for now.
      });

      // Pass the base64 string directly to the parent's handler
      onDrawingComplete(base64Image);
    } catch (error) {
      console.error('Failed to capture drawing:', error);
      Alert.alert('Error', 'Could not capture the drawing. Please try again.');
    }
  };

  return (
    <View style={styles.container}>
      {/* Wrap the Svg component in ViewShot.
          This ViewShot component is what we will capture.
       */}
      <ViewShot
        ref={viewShotRef}
        style={styles.canvasContainer}
        options={{ format: 'png', quality: 1.0, result: 'base64' }}
      >
        <Svg
          height={CANVAS_SIZE}
          width={CANVAS_SIZE}
          style={styles.canvas} // The background color is now on the container
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          // **FIX:** Add responder props here. This makes the Svg component
          // the primary touch handler, preventing parent ScrollViews
          // from intercepting the touch gesture and scrolling.
          onStartShouldSetResponder={() => true}
          onMoveShouldSetResponder={() => true}
        >
          {/* Add a white background rectangle.
              This is crucial so the captured PNG is not transparent.
           */}
          <Path
            d={`M0,0 H${CANVAS_SIZE} V${CANVAS_SIZE} H0 Z`}
            fill={CANVAS_BACKGROUND_COLOR}
          />

          {/* Render all completed paths */}
          {paths.map((pathObj, index) => (
            <Path
              key={`path-${index}`}
              d={pathObj.path}
              stroke="#000"
              strokeWidth={pathObj.strokeWidth}
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="none"
            />
          ))}

          {/* Render the current path being drawn */}
          {currentPath && (
            <Path
              d={currentPath}
              stroke="#000"
              strokeWidth={brushSize}
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="none"
            />
          )}
        </Svg>
      </ViewShot>

      {/* Brush Size Slider */}
      <View style={styles.brushSizeContainer}>
        <Text style={styles.brushSizeLabel}>刷子大小:</Text>
        <Slider
          style={styles.slider}
          minimumValue={5}
          maximumValue={30}
          value={brushSize}
          onValueChange={setBrushSize}
          minimumTrackTintColor="#2563eb"
          maximumTrackTintColor="#e2e8f0"
          thumbTintColor="#2563eb"
        />
        <Text style={styles.brushSizeValue}>{Math.round(brushSize)}px</Text>
      </View>

      {/* Action Buttons */}
      <View style={styles.buttonContainer}>
        <TouchableOpacity style={styles.clearButton} onPress={clearCanvas}>
          <Text style={styles.clearButtonText}>重置</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.recognizeButton}
          onPress={captureDrawing}
        >
          <Text style={styles.buttonText}>识别</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    marginBottom: 20,
  },
  canvasContainer: {
    backgroundColor: CANVAS_BACKGROUND_COLOR, // Set background color here
    borderWidth: 2,
    borderColor: '#e2e8f0',
    borderRadius: 20,
    overflow: 'hidden', // Ensures the Svg corners are rounded
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 4,
  },
  canvas: {
    // Background color is inherited from the container
  },
  brushSizeContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 20,
    paddingHorizontal: 10,
    width: '100%',
  },
  brushSizeLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#334155',
    marginRight: 12,
    marginLeft: 10,
  },
  slider: {
    flex: 1,
    height: 40,
  },
  brushSizeValue: {
    fontSize: 15,
    fontWeight: '600',
    color: '#2563eb',
    marginLeft: 12,
    minWidth: 50,
  },
  buttonContainer: {
    flexDirection: 'row',
    marginTop: 24,
    gap: 12,
    width: '100%',
    paddingHorizontal: 10,
  },
  clearButton: {
    flex: 1,
    backgroundColor: '#64748b',
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#64748b',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 5,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
  },
  recognizeButton: {
    flex: 1,
    backgroundColor: '#2563eb',
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#2563eb',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 5,
  },
  buttonText: {
    color: '#fff',
    fontSize: 17,
    fontWeight: '700',
    textShadowColor: 'rgba(0, 0, 0, 0.2)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 2,
  },
  clearButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '700',
    textShadowColor: 'rgba(0, 0, 0, 0.4)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
    letterSpacing: 1,
  },
});

export default DrawingCanvas;