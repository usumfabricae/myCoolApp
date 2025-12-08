# Log Analysis and Monitoring Integration

## Overview

This document describes the log analysis and monitoring integration system for the OpenCV Camera Stream application. The system provides automated error detection, real-time monitoring, and performance metrics tracking.

## Components

### 1. Log Collection Scripts

#### Local Development
- **collect-app-logs.sh** / **collect-app-logs.ps1**: Collect comprehensive application logs from connected devices
- **analyze-logs.py**: Analyze collected logs and generate error reports

#### CI/CD Integration
- **cicd-log-collection.sh**: Automated log collection during CI/CD builds
- **automated-error-detection.py**: Real-time error pattern detection with build failure triggers

### 2. Real-Time Monitoring

#### RealTimeMonitor
Java class that provides:
- Real-time error tracking and alerting
- Performance metrics monitoring
- Critical issue detection
- Automated log export for analysis
- Integration with ErrorHandler and PerformanceMonitor

#### PerformanceDashboard
UI component that displays:
- Real-time FPS metrics
- Memory usage statistics
- Processing time tracking
- Performance level indicators
- Error count display

## Usage

### Local Log Collection

```bash
# Collect logs for 30 seconds (default)
./scripts/collect-app-logs.sh

# Collect logs for 60 seconds with system logs
./scripts/collect-app-logs.sh --duration 60 --include-system

# Clear existing logs before collection
./scripts/collect-app-logs.sh --clear-first
```

### Log Analysis

```bash
# Analyze logs in default directory
python3 scripts/analyze-logs.py

# Analyze logs in custom directory
python3 scripts/analyze-logs.py logs/custom

# Save detailed results to file
python3 scripts/analyze-logs.py --output analysis_report.json
```

### Automated Error Detection

```bash
# Run automated error detection
python3 scripts/automated-error-detection.py logs

# Set alert threshold
python3 scripts/automated-error-detection.py logs --threshold critical

# Fail build on errors
python3 scripts/automated-error-detection.py logs --fail-on-errors
```

### CI/CD Integration

The CI/CD pipeline automatically:
1. Collects logs during build and test execution
2. Runs automated error detection
3. Fails the build if critical errors are detected
4. Exports logs as build artifacts

## Error Detection Patterns

### Critical Errors (Build Fails)
- Native library loading failures
- OpenCV initialization failures
- Camera critical failures
- Out of memory errors
- Application crashes

### High Priority Errors (Warnings)
- Camera errors
- Processing errors
- Permission errors

### Medium Priority Issues (Informational)
- Performance issues
- Frame drops
- General warnings

## Real-Time Monitoring Integration

### Setup in MainActivity

```java
// Initialize monitoring components
ErrorHandler errorHandler = new ErrorHandler(this);
PerformanceMonitor performanceMonitor = new PerformanceMonitor(this);
PerformanceMetricsCollector metricsCollector = new PerformanceMetricsCollector(performanceMonitor);
RealTimeMonitor realTimeMonitor = new RealTimeMonitor(this, errorHandler, performanceMonitor, metricsCollector);

// Setup alert callback
realTimeMonitor.setAlertCallback(new RealTimeMonitor.AlertCallback() {
    @Override
    public void onAlert(@NonNull RealTimeMonitor.MonitoringEvent event) {
        // Handle alert
        Log.w(TAG, "Alert: " + event.message);
    }
    
    @Override
    public void onCriticalAlert(@NonNull String message, @NonNull List<RealTimeMonitor.MonitoringEvent> recentEvents) {
        // Handle critical alert
        Log.e(TAG, "Critical Alert: " + message);
        // Show user notification or dialog
    }
});

// Start monitoring
realTimeMonitor.startMonitoring();
metricsCollector.startMonitoring();

// Enable auto-export of logs
realTimeMonitor.setAutoExportEnabled(true);
```

### Performance Dashboard Integration

```java
// Add dashboard to layout
PerformanceDashboard dashboard = findViewById(R.id.performance_dashboard);
dashboard.setMetricsSources(performanceMonitor, metricsCollector, realTimeMonitor);

// Update dashboard periodically
Handler handler = new Handler(Looper.getMainLooper());
Runnable updateRunnable = new Runnable() {
    @Override
    public void run() {
        dashboard.updateMetrics();
        handler.postDelayed(this, 500); // Update every 500ms
    }
};
handler.post(updateRunnable);

// Toggle dashboard visibility
dashboard.toggle();
```

## Exported Log Format

Monitoring logs are exported to: `/data/data/com.example.opencvcamerastream/files/monitoring_logs/`

Format:
```
OpenCV Camera Stream - Monitoring Log Export
============================================
Export Time: [timestamp]
Total Events: [count]
Critical Errors: [count]
High Errors: [count]

Performance Metrics:
  Memory Usage: [percentage]
  Avg Processing Time: [ms]
  Performance Level: [level]

Frame Rate Metrics:
  Current FPS: [fps]
  Average FPS: [fps]
  Dropped Frames: [count]/[total]

Events:
========
[timestamp] [level] - [category]: [message]
  Details: [details]
...
```

## Alert Thresholds

### Critical Alert Triggers
- 5 or more critical errors
- 10 or more high priority errors
- Memory usage > 90%
- FPS < 10 (below minimum threshold)
- Processing time > 100ms consistently

### Warning Alert Triggers
- Memory usage > 80%
- FPS < 20
- Processing time > 50ms
- Performance level degradation

## CI/CD Configuration

The monitoring system is integrated into the Codemagic CI/CD pipeline:

1. **Pre-build**: Setup monitoring environment
2. **Build**: Collect build logs
3. **Test**: Collect test execution logs and device logs
4. **Post-test**: Run automated error detection
5. **Artifacts**: Export logs and analysis reports

## Troubleshooting

### No logs collected
- Ensure device is connected: `adb devices`
- Check app is running: `adb shell ps | grep opencvcamerastream`
- Verify permissions: `adb shell dumpsys package com.example.opencvcamerastream | grep permission`

### Error detection not working
- Verify Python 3 is installed: `python3 --version`
- Check log files exist in specified directory
- Ensure log files are not empty

### Real-time monitoring not updating
- Verify monitoring is started: `realTimeMonitor.startMonitoring()`
- Check metrics sources are set: `dashboard.setMetricsSources(...)`
- Ensure dashboard is visible: `dashboard.show()`

## Performance Impact

The monitoring system is designed to have minimal performance impact:
- Real-time monitoring: < 1% CPU overhead
- Log export: Async operation, no UI blocking
- Dashboard updates: 500ms interval, minimal rendering cost
- Memory usage: < 5MB for event queue (1000 events max)

## Requirements Addressed

- **Req-11**: Comprehensive error logging and recovery
  - Real-time error tracking
  - Automated recovery attempt logging
  - Critical issue detection

- **Req-12**: Automated testing and validation
  - CI/CD log collection integration
  - Automated error pattern detection
  - Build failure on critical errors
  - Performance metrics validation
