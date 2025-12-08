#!/usr/bin/env python3
"""
OpenCV Camera Stream - Automated Error Pattern Detection
Analyzes logs in real-time and detects critical error patterns
"""

import os
import re
import json
import sys
import argparse
from datetime import datetime
from pathlib import Path
from collections import defaultdict, Counter

class AutomatedErrorDetector:
    """Automated error detection system for CI/CD integration"""
    
    def __init__(self, logs_dir, alert_threshold='medium'):
        self.logs_dir = Path(logs_dir)
        self.alert_threshold = alert_threshold
        self.critical_errors = []
        self.high_priority_errors = []
        self.medium_priority_errors = []
        self.warnings = []
        
        # Define critical error patterns that should fail the build
        self.critical_patterns = {
            'native_library_missing': [
                r'UnsatisfiedLinkError.*libc\+\+_shared\.so',
                r'UnsatisfiedLinkError.*libopencv_java4\.so',
                r'dlopen failed.*libc\+\+_shared\.so',
                r'dlopen failed.*libopencv_java4\.so',
                r'library.*not found',
                r'is too small to be an ELF executable'
            ],
            'opencv_initialization_failure': [
                r'OpenCV initialization failed',
                r'OpenCV.*not loaded',
                r'OpenCV Manager.*not found',
                r'cv::.*exception',
                r'Mat.*allocation failed'
            ],
            'camera_critical_failure': [
                r'Camera.*FATAL',
                r'CameraService.*died',
                r'Camera device.*permanently failed',
                r'Camera permission.*permanently denied'
            ],
            'memory_critical': [
                r'OutOfMemoryError',
                r'Native heap.*exceeded',
                r'Failed to allocate.*bytes'
            ],
            'app_crash': [
                r'FATAL EXCEPTION',
                r'Process.*crashed',
                r'AndroidRuntime.*FATAL'
            ]
        }
        
        # High priority patterns
        self.high_priority_patterns = {
            'camera_errors': [
                r'Camera.*error',
                r'CameraService.*failed',
                r'Camera2.*exception',
                r'Camera device.*disconnected'
            ],
            'processing_errors': [
                r'Error processing.*image',
                r'Frame processing.*failed',
                r'Processing pipeline.*error'
            ],
            'permission_errors': [
                r'Permission denied',
                r'SecurityException',
                r'permission.*not granted'
            ]
        }
        
        # Medium priority patterns
        self.medium_priority_patterns = {
            'performance_issues': [
                r'Skipped.*frames',
                r'Choreographer.*skipped',
                r'GC.*blocked.*ms',
                r'ANR in.*'
            ],
            'warnings': [
                r'WARNING',
                r'WARN',
                r'deprecated'
            ]
        }
    
    def analyze_logs(self):
        """Analyze all log files and detect error patterns"""
        print(f"Analyzing logs in: {self.logs_dir}")
        
        # Find all log files
        log_files = list(self.logs_dir.glob("*.txt")) + list(self.logs_dir.glob("*.log"))
        
        if not log_files:
            print("No log files found!")
            return False
        
        print(f"Found {len(log_files)} log files to analyze")
        
        # Analyze each file
        for log_file in log_files:
            print(f"Analyzing: {log_file.name}")
            self._analyze_file(log_file)
        
        # Generate report
        return self._generate_report()
    
    def _analyze_file(self, log_file):
        """Analyze individual log file for error patterns"""
        try:
            with open(log_file, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()
                lines = content.split('\n')
            
            # Check critical patterns
            for category, patterns in self.critical_patterns.items():
                for pattern in patterns:
                    matches = re.finditer(pattern, content, re.IGNORECASE | re.MULTILINE)
                    for match in matches:
                        # Find line number
                        line_num = content[:match.start()].count('\n') + 1
                        error_line = lines[line_num - 1] if line_num <= len(lines) else match.group(0)
                        
                        self.critical_errors.append({
                            'category': category,
                            'pattern': pattern,
                            'file': log_file.name,
                            'line': line_num,
                            'text': error_line.strip(),
                            'severity': 'CRITICAL'
                        })
            
            # Check high priority patterns
            for category, patterns in self.high_priority_patterns.items():
                for pattern in patterns:
                    matches = re.finditer(pattern, content, re.IGNORECASE | re.MULTILINE)
                    for match in matches:
                        line_num = content[:match.start()].count('\n') + 1
                        error_line = lines[line_num - 1] if line_num <= len(lines) else match.group(0)
                        
                        self.high_priority_errors.append({
                            'category': category,
                            'pattern': pattern,
                            'file': log_file.name,
                            'line': line_num,
                            'text': error_line.strip(),
                            'severity': 'HIGH'
                        })
            
            # Check medium priority patterns
            for category, patterns in self.medium_priority_patterns.items():
                for pattern in patterns:
                    matches = re.finditer(pattern, content, re.IGNORECASE | re.MULTILINE)
                    for match in matches:
                        line_num = content[:match.start()].count('\n') + 1
                        error_line = lines[line_num - 1] if line_num <= len(lines) else match.group(0)
                        
                        self.medium_priority_errors.append({
                            'category': category,
                            'pattern': pattern,
                            'file': log_file.name,
                            'line': line_num,
                            'text': error_line.strip(),
                            'severity': 'MEDIUM'
                        })
        
        except Exception as e:
            print(f"Error analyzing {log_file}: {e}")
    
    def _generate_report(self):
        """Generate error detection report and determine if build should fail"""
        print("\n" + "="*80)
        print("AUTOMATED ERROR DETECTION REPORT")
        print("="*80)
        
        # Count errors by severity
        critical_count = len(self.critical_errors)
        high_count = len(self.high_priority_errors)
        medium_count = len(self.medium_priority_errors)
        
        print(f"\nError Summary:")
        print(f"  CRITICAL: {critical_count}")
        print(f"  HIGH:     {high_count}")
        print(f"  MEDIUM:   {medium_count}")
        
        # Display critical errors
        if self.critical_errors:
            print("\n" + "="*80)
            print("CRITICAL ERRORS (Build should fail)")
            print("="*80)
            
            # Group by category
            by_category = defaultdict(list)
            for error in self.critical_errors:
                by_category[error['category']].append(error)
            
            for category, errors in by_category.items():
                print(f"\n{category.upper().replace('_', ' ')} ({len(errors)} occurrences):")
                # Show first 5 unique errors
                unique_texts = list(set(e['text'] for e in errors))[:5]
                for text in unique_texts:
                    print(f"  • {text[:120]}")
                if len(errors) > 5:
                    print(f"  ... and {len(errors) - 5} more")
        
        # Display high priority errors
        if self.high_priority_errors:
            print("\n" + "="*80)
            print("HIGH PRIORITY ERRORS")
            print("="*80)
            
            by_category = defaultdict(list)
            for error in self.high_priority_errors:
                by_category[error['category']].append(error)
            
            for category, errors in by_category.items():
                print(f"\n{category.upper().replace('_', ' ')} ({len(errors)} occurrences):")
                unique_texts = list(set(e['text'] for e in errors))[:3]
                for text in unique_texts:
                    print(f"  • {text[:120]}")
                if len(errors) > 3:
                    print(f"  ... and {len(errors) - 3} more")
        
        # Display medium priority errors (summary only)
        if self.medium_priority_errors:
            print("\n" + "="*80)
            print("MEDIUM PRIORITY ISSUES (Summary)")
            print("="*80)
            
            by_category = defaultdict(list)
            for error in self.medium_priority_errors:
                by_category[error['category']].append(error)
            
            for category, errors in by_category.items():
                print(f"  {category.replace('_', ' ').title()}: {len(errors)} occurrences")
        
        # Save detailed report to JSON
        report_file = self.logs_dir / f"error_detection_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        report_data = {
            'timestamp': datetime.now().isoformat(),
            'summary': {
                'critical': critical_count,
                'high': high_count,
                'medium': medium_count
            },
            'critical_errors': self.critical_errors,
            'high_priority_errors': self.high_priority_errors,
            'medium_priority_errors': self.medium_priority_errors
        }
        
        with open(report_file, 'w', encoding='utf-8') as f:
            json.dump(report_data, f, indent=2, ensure_ascii=False)
        
        print(f"\nDetailed report saved to: {report_file}")
        
        # Determine if build should fail based on threshold
        should_fail = False
        failure_reason = []
        
        if critical_count > 0:
            should_fail = True
            failure_reason.append(f"{critical_count} critical error(s) detected")
        
        if self.alert_threshold in ['high', 'medium'] and high_count > 0:
            should_fail = True
            failure_reason.append(f"{high_count} high priority error(s) detected")
        
        if self.alert_threshold == 'medium' and medium_count > 10:
            should_fail = True
            failure_reason.append(f"{medium_count} medium priority issues detected (threshold: 10)")
        
        # Print final verdict
        print("\n" + "="*80)
        if should_fail:
            print("BUILD SHOULD FAIL")
            print("Reasons:")
            for reason in failure_reason:
                print(f"  • {reason}")
        else:
            print("BUILD CAN PROCEED")
            print("No critical issues detected above threshold")
        print("="*80)
        
        return not should_fail

def main():
    parser = argparse.ArgumentParser(description='Automated error pattern detection for CI/CD')
    parser.add_argument('logs_dir', nargs='?', default='logs',
                       help='Directory containing log files (default: logs)')
    parser.add_argument('--threshold', choices=['critical', 'high', 'medium'], default='high',
                       help='Alert threshold level (default: high)')
    parser.add_argument('--fail-on-errors', action='store_true',
                       help='Exit with error code if issues detected')
    
    args = parser.parse_args()
    
    if not os.path.exists(args.logs_dir):
        print(f"Error: Logs directory '{args.logs_dir}' not found")
        return 1
    
    detector = AutomatedErrorDetector(args.logs_dir, args.threshold)
    success = detector.analyze_logs()
    
    if args.fail_on_errors and not success:
        return 1
    
    return 0

if __name__ == '__main__':
    sys.exit(main())
