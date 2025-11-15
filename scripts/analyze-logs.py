#!/usr/bin/env python3
"""
OpenCV Camera Stream - Log Analysis Tool
Analyzes collected application logs and generates error reports
"""

import os
import re
import json
import argparse
from datetime import datetime
from collections import defaultdict, Counter
from pathlib import Path

class LogAnalyzer:
    def __init__(self, logs_dir):
        self.logs_dir = Path(logs_dir)
        self.analysis_results = {
            'timestamp': datetime.now().isoformat(),
            'errors': [],
            'warnings': [],
            'crashes': [],
            'performance_issues': [],
            'opencv_issues': [],
            'camera_issues': [],
            'permission_issues': [],
            'memory_issues': [],
            'summary': {}
        }
        
        # Error patterns for different categories
        self.error_patterns = {
            'crashes': [
                r'FATAL EXCEPTION',
                r'AndroidRuntime.*FATAL',
                r'Process.*crashed',
                r'java\.lang\..*Exception',
                r'java\.lang\..*Error'
            ],
            'opencv_issues': [
                r'OpenCV.*error',
                r'cv::.*exception',
                r'libopencv.*not found',
                r'OpenCV initialization failed',
                r'Mat.*allocation failed'
            ],
            'camera_issues': [
                r'Camera.*error',
                r'CameraService.*failed',
                r'Camera2.*exception',
                r'Camera permission denied',
                r'Camera device.*disconnected'
            ],
            'permission_issues': [
                r'Permission denied',
                r'SecurityException',
                r'permission.*not granted',
                r'CAMERA.*permission'
            ],
            'memory_issues': [
                r'OutOfMemoryError',
                r'GC_.*freed.*MB',
                r'Low memory killer',
                r'Native heap.*exceeded'
            ],
            'performance_issues': [
                r'ANR in.*',
                r'Skipped.*frames',
                r'Choreographer.*skipped',
                r'GC.*blocked.*ms'
            ]
        }

    def analyze_logs(self):
        """Main analysis function"""
        print(f"Analyzing logs in: {self.logs_dir}")
        
        # Find all log files
        log_files = list(self.logs_dir.glob("*.txt"))
        if not log_files:
            print("No log files found!")
            return
            
        print(f"Found {len(log_files)} log files")
        
        # Analyze each log file
        for log_file in log_files:
            print(f"Analyzing: {log_file.name}")
            self._analyze_file(log_file)
        
        # Generate summary
        self._generate_summary()
        
        # Save analysis results
        self._save_results()
        
        # Print summary
        self._print_summary()

    def _analyze_file(self, log_file):
        """Analyze individual log file"""
        try:
            with open(log_file, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()
                
            # Analyze based on file type
            if 'crash' in log_file.name:
                self._analyze_crashes(content, log_file.name)
            elif 'camera' in log_file.name:
                self._analyze_camera_logs(content, log_file.name)
            elif 'opencv' in log_file.name:
                self._analyze_opencv_logs(content, log_file.name)
            elif 'memory' in log_file.name:
                self._analyze_memory_logs(content, log_file.name)
            elif 'app' in log_file.name:
                self._analyze_app_logs(content, log_file.name)
            else:
                # General analysis for other files
                self._analyze_general(content, log_file.name)
                
        except Exception as e:
            print(f"Error analyzing {log_file}: {e}")

    def _analyze_crashes(self, content, filename):
        """Analyze crash logs"""
        lines = content.split('\n')
        current_crash = None
        
        for i, line in enumerate(lines):
            if re.search(r'FATAL EXCEPTION|AndroidRuntime.*FATAL', line):
                if current_crash:
                    self.analysis_results['crashes'].append(current_crash)
                
                current_crash = {
                    'type': 'crash',
                    'file': filename,
                    'line_number': i + 1,
                    'message': line.strip(),
                    'stack_trace': [],
                    'severity': 'critical'
                }
            elif current_crash and (line.strip().startswith('at ') or 'Exception' in line):
                current_crash['stack_trace'].append(line.strip())
        
        if current_crash:
            self.analysis_results['crashes'].append(current_crash)

    def _analyze_camera_logs(self, content, filename):
        """Analyze camera-specific logs"""
        for category, patterns in self.error_patterns.items():
            if category == 'camera_issues':
                self._find_pattern_matches(content, patterns, category, filename)

    def _analyze_opencv_logs(self, content, filename):
        """Analyze OpenCV-specific logs"""
        for category, patterns in self.error_patterns.items():
            if category == 'opencv_issues':
                self._find_pattern_matches(content, patterns, category, filename)

    def _analyze_memory_logs(self, content, filename):
        """Analyze memory information"""
        # Extract memory usage statistics
        memory_stats = {}
        
        # Parse memory info
        if 'TOTAL' in content:
            for line in content.split('\n'):
                if 'TOTAL' in line and 'PSS' in line:
                    parts = line.split()
                    if len(parts) >= 2:
                        try:
                            memory_stats['total_pss'] = int(parts[1])
                        except ValueError:
                            pass
        
        if memory_stats:
            self.analysis_results['memory_issues'].append({
                'type': 'memory_usage',
                'file': filename,
                'stats': memory_stats,
                'severity': 'info'
            })

    def _analyze_app_logs(self, content, filename):
        """Analyze application logs"""
        for category, patterns in self.error_patterns.items():
            self._find_pattern_matches(content, patterns, category, filename)

    def _analyze_general(self, content, filename):
        """General log analysis"""
        for category, patterns in self.error_patterns.items():
            self._find_pattern_matches(content, patterns, category, filename)

    def _find_pattern_matches(self, content, patterns, category, filename):
        """Find matches for error patterns"""
        lines = content.split('\n')
        
        for i, line in enumerate(lines):
            for pattern in patterns:
                if re.search(pattern, line, re.IGNORECASE):
                    issue = {
                        'type': category,
                        'file': filename,
                        'line_number': i + 1,
                        'message': line.strip(),
                        'pattern': pattern,
                        'severity': self._determine_severity(pattern, line)
                    }
                    
                    # Add to appropriate category
                    if category in self.analysis_results:
                        self.analysis_results[category].append(issue)

    def _determine_severity(self, pattern, line):
        """Determine severity based on pattern and content"""
        if any(word in pattern.lower() for word in ['fatal', 'crash', 'exception']):
            return 'critical'
        elif any(word in pattern.lower() for word in ['error', 'failed']):
            return 'high'
        elif any(word in pattern.lower() for word in ['warning', 'skipped']):
            return 'medium'
        else:
            return 'low'

    def _generate_summary(self):
        """Generate analysis summary"""
        summary = {
            'total_issues': 0,
            'by_severity': Counter(),
            'by_category': Counter(),
            'top_issues': []
        }
        
        # Count issues by category and severity
        for category, issues in self.analysis_results.items():
            if isinstance(issues, list) and category != 'summary':
                summary['by_category'][category] = len(issues)
                summary['total_issues'] += len(issues)
                
                for issue in issues:
                    if isinstance(issue, dict) and 'severity' in issue:
                        summary['by_severity'][issue['severity']] += 1
        
        # Find most common issues
        all_messages = []
        for category, issues in self.analysis_results.items():
            if isinstance(issues, list) and category != 'summary':
                for issue in issues:
                    if isinstance(issue, dict) and 'message' in issue:
                        all_messages.append(issue['message'])
        
        message_counts = Counter(all_messages)
        summary['top_issues'] = message_counts.most_common(10)
        
        self.analysis_results['summary'] = summary

    def _save_results(self):
        """Save analysis results to JSON file"""
        output_file = self.logs_dir / f"analysis_results_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(self.analysis_results, f, indent=2, ensure_ascii=False)
        
        print(f"Analysis results saved to: {output_file}")

    def _print_summary(self):
        """Print analysis summary"""
        summary = self.analysis_results['summary']
        
        print("\n" + "="*60)
        print("LOG ANALYSIS SUMMARY")
        print("="*60)
        
        print(f"Total Issues Found: {summary['total_issues']}")
        
        if summary['by_severity']:
            print("\nBy Severity:")
            for severity, count in summary['by_severity'].most_common():
                print(f"  {severity.upper()}: {count}")
        
        if summary['by_category']:
            print("\nBy Category:")
            for category, count in summary['by_category'].items():
                if count > 0:
                    print(f"  {category.replace('_', ' ').title()}: {count}")
        
        if summary['top_issues']:
            print("\nMost Common Issues:")
            for i, (message, count) in enumerate(summary['top_issues'][:5], 1):
                print(f"  {i}. ({count}x) {message[:80]}...")
        
        # Specific recommendations
        print("\nRECOMMENDATIONS:")
        self._print_recommendations()

    def _print_recommendations(self):
        """Print specific recommendations based on found issues"""
        recommendations = []
        
        if self.analysis_results['crashes']:
            recommendations.append("• Fix critical crashes - check crash_logs for stack traces")
        
        if self.analysis_results['camera_issues']:
            recommendations.append("• Review camera permissions and Camera2 API usage")
        
        if self.analysis_results['opencv_issues']:
            recommendations.append("• Check OpenCV library loading and initialization")
        
        if self.analysis_results['memory_issues']:
            recommendations.append("• Investigate memory leaks and optimize memory usage")
        
        if self.analysis_results['permission_issues']:
            recommendations.append("• Update Android 10 permission handling implementation")
        
        if self.analysis_results['performance_issues']:
            recommendations.append("• Optimize UI thread usage and frame processing")
        
        if not recommendations:
            recommendations.append("• No critical issues found - monitor performance metrics")
        
        for rec in recommendations:
            print(rec)

def main():
    parser = argparse.ArgumentParser(description='Analyze OpenCV Camera Stream application logs')
    parser.add_argument('logs_dir', nargs='?', default='logs', 
                       help='Directory containing log files (default: logs)')
    parser.add_argument('--output', '-o', help='Output file for detailed results')
    
    args = parser.parse_args()
    
    if not os.path.exists(args.logs_dir):
        print(f"Error: Logs directory '{args.logs_dir}' not found")
        return 1
    
    analyzer = LogAnalyzer(args.logs_dir)
    analyzer.analyze_logs()
    
    return 0

if __name__ == '__main__':
    exit(main())