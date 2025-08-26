#!/usr/bin/env python3
"""
Simple validation script for codemagic.yaml
Checks for common YAML structure issues that cause Codemagic validation errors
"""

import yaml
import sys
import os

def validate_codemagic_yaml(file_path):
    """Validate the codemagic.yaml file structure"""
    
    try:
        with open(file_path, 'r', encoding='utf-8') as file:
            config = yaml.safe_load(file)
        
        print("✅ YAML syntax is valid")
        
        # Check for required top-level sections
        if 'workflows' not in config:
            print("❌ Missing 'workflows' section")
            return False
        
        print("✅ Found 'workflows' section")
        
        # Validate each workflow
        for workflow_name, workflow_config in config['workflows'].items():
            print(f"\n🔍 Validating workflow: {workflow_name}")
            
            # Check required workflow fields
            required_fields = ['name', 'instance_type', 'environment', 'scripts']
            for field in required_fields:
                if field not in workflow_config:
                    print(f"❌ Missing required field '{field}' in workflow '{workflow_name}'")
                    return False
                else:
                    print(f"✅ Found required field '{field}'")
            
            # Validate environment section
            env_config = workflow_config.get('environment', {})
            
            # Check if vars section exists and is properly formatted
            if 'vars' in env_config:
                vars_config = env_config['vars']
                if vars_config is None:
                    print(f"❌ 'vars' section is None in workflow '{workflow_name}'")
                    return False
                elif not isinstance(vars_config, dict):
                    print(f"❌ 'vars' section must be a dictionary in workflow '{workflow_name}'")
                    return False
                else:
                    print(f"✅ 'vars' section is properly formatted")
            
            # Check if groups section exists and is properly formatted
            if 'groups' in env_config:
                groups_config = env_config['groups']
                if groups_config is None:
                    print(f"❌ 'groups' section is None in workflow '{workflow_name}'")
                    return False
                elif not isinstance(groups_config, list):
                    print(f"❌ 'groups' section must be a list in workflow '{workflow_name}'")
                    return False
                else:
                    print(f"✅ 'groups' section is properly formatted")
        
        print("\n🎉 All validations passed!")
        return True
        
    except yaml.YAMLError as e:
        print(f"❌ YAML syntax error: {e}")
        return False
    except FileNotFoundError:
        print(f"❌ File not found: {file_path}")
        return False
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        return False

if __name__ == "__main__":
    # Get the script directory and find codemagic.yaml
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    codemagic_file = os.path.join(project_root, 'codemagic.yaml')
    
    print(f"Validating: {codemagic_file}")
    
    if validate_codemagic_yaml(codemagic_file):
        sys.exit(0)
    else:
        sys.exit(1)