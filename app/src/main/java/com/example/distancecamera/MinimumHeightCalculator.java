package com.example.distancecamera;

public class MinimumHeightCalculator {
    
    /**
     * Calculate minimum detectable altitude for given parameters
     * @param speedKmh Airplane speed in km/h
     * @param intervalSeconds Time between captures in seconds
     * @param minPixelDisplacement Minimum detectable pixel movement
     * @param focalLengthMm Camera focal length in mm
     * @param sensorWidthMm Camera sensor width in mm
     * @param imageWidthPixels Image width in pixels
     * @return Minimum altitude in meters
     */
    public static double calculateMinimumHeight(double speedKmh, double intervalSeconds, 
                                              double minPixelDisplacement, double focalLengthMm, 
                                              double sensorWidthMm, int imageWidthPixels) {
        
        // Convert speed to m/s
        double speedMs = speedKmh / 3.6;
        
        // Calculate baseline (ground distance)
        double baseline = speedMs * intervalSeconds;
        
        // Convert focal length to pixels
        double focalLengthPixels = (focalLengthMm * imageWidthPixels) / sensorWidthMm;
        
        // Minimum altitude = (focal_length_pixels * baseline) / min_pixel_displacement
        return (focalLengthPixels * baseline) / minPixelDisplacement;
    }
    
    /**
     * Calculate for your specific case: 40 km/h, 0.1s intervals
     */
    public static void calculateForYourCase() {
        double speedKmh = 40.0;
        double intervalSeconds = 0.1;
        double minPixelDisplacement = 1.0; // 1 pixel minimum
        double focalLengthMm = 3.67; // Typical smartphone
        double sensorWidthMm = 5.76; // Typical smartphone
        int imageWidthPixels = 1920; // Full HD
        
        double minHeight = calculateMinimumHeight(speedKmh, intervalSeconds, minPixelDisplacement, 
                                                focalLengthMm, sensorWidthMm, imageWidthPixels);
        
        System.out.println("=== MINIMUM DETECTABLE HEIGHT CALCULATION ===");
        System.out.println("Speed: " + speedKmh + " km/h (" + (speedKmh/3.6) + " m/s)");
        System.out.println("Capture interval: " + intervalSeconds + " seconds");
        System.out.println("Ground distance per interval: " + (speedKmh/3.6 * intervalSeconds) + " meters");
        System.out.println("Minimum pixel displacement: " + minPixelDisplacement + " pixel");
        System.out.println("MINIMUM DETECTABLE HEIGHT: " + Math.round(minHeight) + " meters");
        
        // Calculate for different pixel sensitivities
        System.out.println("\n=== SENSITIVITY ANALYSIS ===");
        for (double pixelSensitivity : new double[]{0.5, 1.0, 2.0, 5.0}) {
            double height = calculateMinimumHeight(speedKmh, intervalSeconds, pixelSensitivity, 
                                                 focalLengthMm, sensorWidthMm, imageWidthPixels);
            System.out.println(pixelSensitivity + " pixel sensitivity: " + Math.round(height) + "m minimum height");
        }
    }
}