#!/usr/bin/env python3
"""
Mappls SDK Integration Checker
Verifies if the Mappls SDK is properly configured in an Android project.
"""

import os
import sys
import json
import requests
from pathlib import Path
from typing import Dict, List, Tuple, Optional
import re
from datetime import datetime


class MapplsIntegrationChecker:
    def __init__(self, project_path: str = "."):
        self.project_path = Path(project_path)
        self.results = []
        self.errors = []
        self.warnings = []
        
    def log(self, message: str, level: str = "INFO"):
        """Log a message with timestamp"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        prefix = {"INFO": "[OK]", "ERROR": "[ERR]", "WARNING": "[WARN]", "CHECK": "[-->]"}
        print(f"[{timestamp}] {prefix.get(level, '→')} {message}")
        
    def check_build_gradle(self) -> bool:
        """Check if build.gradle.kts has Mappls SDK dependency"""
        self.log("Checking build.gradle.kts...", "CHECK")
        
        build_file = self.project_path / "app" / "build.gradle.kts"
        if not build_file.exists():
            self.errors.append("app/build.gradle.kts not found")
            self.log("app/build.gradle.kts not found", "ERROR")
            return False
            
        content = build_file.read_text(encoding='utf-8')
        
        # Check for Mappls SDK dependency
        mappls_dep = "com.mappls.sdk:mappls-android-sdk"
        if mappls_dep not in content:
            self.errors.append(f"Mappls SDK dependency not found: {mappls_dep}")
            self.log(f"Mappls SDK dependency not found", "ERROR")
            return False
            
        # Extract version
        version_match = re.search(
            rf'{re.escape(mappls_dep)}:(\d+\.\d+\.\d+)', 
            content
        )
        version = version_match.group(1) if version_match else "unknown"
        
        # Check for required plugins
        required_plugins = [
            "com.android.application",
            "org.jetbrains.kotlin.android",
            "org.jetbrains.kotlin.plugin.compose"
        ]
        
        missing_plugins = []
        for plugin in required_plugins:
            if plugin not in content:
                missing_plugins.append(plugin)
                
        if missing_plugins:
            self.warnings.append(f"Missing plugins: {', '.join(missing_plugins)}")
            self.log(f"Missing plugins: {', '.join(missing_plugins)}", "WARNING")
        else:
            self.log("All required plugins found", "INFO")
            
        self.results.append({
            "check": "build.gradle.kts",
            "status": "PASS",
            "details": {
                "mappls_dependency": f"{mappls_dep}:{version}",
                "version": version,
                "plugins_present": len(required_plugins) - len(missing_plugins)
            }
        })
        
        self.log(f"Mappls SDK {version} found in build.gradle.kts", "INFO")
        return True
        
    def check_main_application(self) -> bool:
        """Check if MainApplication.kt has proper Mappls initialization"""
        self.log("Checking MainApplication.kt...", "CHECK")
        
        # Search for MainApplication.kt
        kotlin_files = list(self.project_path.rglob("*.kt"))
        main_app_files = [f for f in kotlin_files if "MainApplication" in f.name]
        
        if not main_app_files:
            self.errors.append("MainApplication.kt not found")
            self.log("MainApplication.kt not found", "ERROR")
            return False
            
        main_app_file = main_app_files[0]
        content = main_app_file.read_text(encoding='utf-8')
        
        # Check for required Mappls initialization
        required_checks = {
            "MapplsAccountManager.getInstance().restAPIKey": "REST API Key",
            "MapplsAccountManager.getInstance().mapSDKKey": "Map SDK Key",
            "MapplsAccountManager.getInstance().atlasClientId": "Atlas Client ID",
            "MapplsAccountManager.getInstance().atlasClientSecret": "Atlas Client Secret",
            "Mappls.getInstance(applicationContext)": "Mappls initialization"
        }
        
        found_checks = []
        missing_checks = []
        
        for check, name in required_checks.items():
            if check in content:
                found_checks.append(name)
            else:
                missing_checks.append(name)
                
        if missing_checks:
            self.errors.append(f"Missing Mappls initialization: {', '.join(missing_checks)}")
            self.log(f"Missing: {', '.join(missing_checks)}", "ERROR")
            return False
            
        # Check for placeholder values
        placeholder_patterns = [
            "YOUR_CLIENT_ID",
            "YOUR_CLIENT_SECRET",
            "YOUR_API_KEY",
            "YOUR_REST_API_KEY",
            "YOUR_MAP_SDK_KEY"
        ]
        
        placeholders_found = []
        for pattern in placeholder_patterns:
            if pattern in content:
                placeholders_found.append(pattern)
                
        if placeholders_found:
            self.warnings.append(f"Placeholder values found: {', '.join(placeholders_found)}")
            self.log(f"Placeholder values: {', '.join(placeholders_found)}", "WARNING")
            
        # Check for Application class inheritance
        if "Application()" in content or ": Application()" in content:
            self.log("Application class inheritance found", "INFO")
        else:
            self.warnings.append("Application class inheritance not found")
            self.log("Application class inheritance not found", "WARNING")
            
        self.results.append({
            "check": "MainApplication.kt",
            "status": "PASS",
            "details": {
                "file": str(main_app_file),
                "initialization_checks": len(found_checks),
                "missing_checks": len(missing_checks),
                "placeholders": placeholders_found
            }
        })
        
        self.log(f"All Mappls initialization found in {main_app_file.name}", "INFO")
        return True
        
    def check_api_keys(self, rest_api_key: str, map_sdk_key: str) -> bool:
        """Check if API keys are valid by making a test request with CORS headers"""
        self.log("Checking API keys validity (with CORS)...", "CHECK")
        
        # Mappls uses access_token as query parameter
        test_url = "https://search.mappls.com/search/address/geocode"
        
        rest_key_valid = False
        map_key_valid = False
        
        # CORS headers to simulate browser request
        cors_headers = {
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "GET",
            "Access-Control-Request-Headers": "Content-Type"
        }
        
        # Test REST API Key with CORS preflight
        self.log("Testing REST API Key with CORS preflight...", "CHECK")
        try:
            # First: CORS preflight (OPTIONS)
            preflight_response = requests.options(
                test_url,
                headers=cors_headers,
                timeout=10
            )
            self.log(f"  CORS Preflight Status: {preflight_response.status_code}", "INFO")
            
            # Check CORS headers in preflight response
            allowed_origin = preflight_response.headers.get("Access-Control-Allow-Origin", "NOT SET")
            allowed_methods = preflight_response.headers.get("Access-Control-Allow-Methods", "NOT SET")
            allowed_headers = preflight_response.headers.get("Access-Control-Allow-Headers", "NOT SET")
            
            self.log(f"  Allow-Origin: {allowed_origin}", "INFO")
            self.log(f"  Allow-Methods: {allowed_methods}", "INFO")
            self.log(f"  Allow-Headers: {allowed_headers}", "INFO")
            
            # Second: Actual GET request with Origin header
            response = requests.get(
                test_url,
                params={
                    "address": "Connaught Place, New Delhi",
                    "access_token": rest_api_key
                },
                headers={"Origin": "http://localhost:3000"},
                timeout=10
            )
            
            # Check CORS headers in actual response
            response_origin = response.headers.get("Access-Control-Allow-Origin", "NOT SET")
            self.log(f"  Response Allow-Origin: {response_origin}", "INFO")
            
            data = response.json()
            
            if response.status_code == 200:
                results_count = len(data.get("copResults", []))
                self.log(f"  REST API Key VALID - returned {results_count} results", "INFO")
                rest_key_valid = True
            elif response.status_code == 400 and "Invalid Token" in str(data.get("error", "")):
                self.log(f"  REST API Key INVALID - {data.get('error_description', 'Token not recognized')}", "ERROR")
            elif response.status_code == 401:
                self.log("  REST API Key INVALID (401 Unauthorized)", "ERROR")
            elif response.status_code == 403:
                self.log("  REST API Key exceeded limit (403 Forbidden)", "ERROR")
            else:
                self.log(f"  REST API Key returned status {response.status_code}", "WARNING")
                
        except requests.RequestException as e:
            self.log(f"  REST API Key test failed: {e}", "ERROR")
            
        # Test Map SDK Key with CORS
        self.log("Testing Map SDK Key with CORS preflight...", "CHECK")
        try:
            # CORS preflight
            preflight_response = requests.options(
                test_url,
                headers=cors_headers,
                timeout=10
            )
            
            # Actual GET request
            response = requests.get(
                test_url,
                params={
                    "address": "Marine Drive, Mumbai",
                    "access_token": map_sdk_key
                },
                headers={"Origin": "http://localhost:3000"},
                timeout=10
            )
            
            response_origin = response.headers.get("Access-Control-Allow-Origin", "NOT SET")
            self.log(f"  Response Allow-Origin: {response_origin}", "INFO")
            
            data = response.json()
            
            if response.status_code == 200:
                results_count = len(data.get("copResults", []))
                self.log(f"  Map SDK Key VALID - returned {results_count} results", "INFO")
                map_key_valid = True
            elif response.status_code == 400 and "Invalid Token" in str(data.get("error", "")):
                self.log(f"  Map SDK Key INVALID - {data.get('error_description', 'Token not recognized')}", "ERROR")
            elif response.status_code == 401:
                self.log("  Map SDK Key INVALID (401 Unauthorized)", "ERROR")
            elif response.status_code == 403:
                self.log("  Map SDK Key exceeded limit (403 Forbidden)", "ERROR")
            else:
                self.log(f"  Map SDK Key returned status {response.status_code}", "WARNING")
                
        except requests.RequestException as e:
            self.log(f"  Map SDK Key test failed: {e}", "ERROR")
            
        self.results.append({
            "check": "API Keys (CORS)",
            "status": "PASS" if rest_key_valid and map_key_valid else "WARNING",
            "details": {
                "rest_api_key_valid": rest_key_valid,
                "map_sdk_key_valid": map_key_valid,
                "cors_preflight_status": preflight_response.status_code if preflight_response else "N/A"
            }
        })
        
        return rest_key_valid and map_key_valid
        
    def check_atlas_credentials(self, client_id: str, client_secret: str) -> bool:
        """Check if Atlas credentials are properly formatted"""
        self.log("Checking Atlas credentials format...", "CHECK")
        
        # Check client ID format (should be a long string)
        client_id_valid = len(client_id) > 20 and not client_id.startswith("YOUR_")
        
        # Check client secret format (should be a long string)
        client_secret_valid = len(client_secret) > 20 and not client_secret.startswith("YOUR_")
        
        if client_id_valid and client_secret_valid:
            self.log("Atlas credentials format looks valid", "INFO")
        else:
            issues = []
            if not client_id_valid:
                issues.append("Client ID appears to be placeholder or too short")
            if not client_secret_valid:
                issues.append("Client Secret appears to be placeholder or too short")
            self.warnings.extend(issues)
            self.log(f"Atlas credential issues: {'; '.join(issues)}", "WARNING")
            
        self.results.append({
            "check": "Atlas Credentials",
            "status": "PASS" if client_id_valid and client_secret_valid else "WARNING",
            "details": {
                "client_id_valid": client_id_valid,
                "client_secret_valid": client_secret_valid,
                "client_id_length": len(client_id),
                "client_secret_length": len(client_secret)
            }
        })
        
        return client_id_valid and client_secret_valid
        
    def check_project_structure(self) -> bool:
        """Check if the project structure is correct"""
        self.log("Checking project structure...", "CHECK")
        
        required_paths = [
            "app/build.gradle.kts",
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties"
        ]
        
        missing_paths = []
        for path in required_paths:
            if not (self.project_path / path).exists():
                missing_paths.append(path)
                
        if missing_paths:
            self.warnings.append(f"Missing project files: {', '.join(missing_paths)}")
            self.log(f"Missing: {', '.join(missing_paths)}", "WARNING")
        else:
            self.log("Project structure looks correct", "INFO")
            
        self.results.append({
            "check": "Project Structure",
            "status": "PASS" if not missing_paths else "WARNING",
            "details": {
                "required_files": len(required_paths),
                "found_files": len(required_paths) - len(missing_paths),
                "missing_files": missing_paths
            }
        })
        
        return len(missing_paths) == 0
        
    def run_checks(self, credentials: Dict[str, str] = None) -> Dict:
        """Run all checks and return results"""
        self.log("=" * 60, "INFO")
        self.log("Starting Mappls SDK Integration Check", "INFO")
        self.log("=" * 60, "INFO")
        
        # Run structural checks
        self.check_project_structure()
        build_gradle_ok = self.check_build_gradle()
        main_app_ok = self.check_main_application()
        
        # Run credential checks if provided
        if credentials:
            rest_api_key = credentials.get("rest_api_key", "")
            map_sdk_key = credentials.get("map_sdk_key", "")
            client_id = credentials.get("client_id", "")
            client_secret = credentials.get("client_secret", "")
            
            self.check_api_keys(rest_api_key, map_sdk_key)
            self.check_atlas_credentials(client_id, client_secret)
        
        # Generate summary
        self.log("=" * 60, "INFO")
        self.log("SUMMARY", "INFO")
        self.log("=" * 60, "INFO")
        
        total_checks = len(self.results)
        passed_checks = sum(1 for r in self.results if r["status"] == "PASS")
        warnings_count = len(self.warnings)
        errors_count = len(self.errors)
        
        self.log(f"Total checks: {total_checks}", "INFO")
        self.log(f"Passed: {passed_checks}", "INFO")
        self.log(f"Warnings: {warnings_count}", "INFO")
        self.log(f"Errors: {errors_count}", "INFO")
        
        # Overall status
        if errors_count == 0 and warnings_count == 0:
            overall_status = "EXCELLENT - Integration looks perfect!"
        elif errors_count == 0:
            overall_status = "GOOD - Integration has minor warnings"
        else:
            overall_status = "ISSUES FOUND - Integration needs fixes"
            
        self.log(f"\nOverall Status: {overall_status}", "INFO")
        
        # Print warnings
        if warnings_count > 0:
            self.log("\nWarnings:", "INFO")
            for warning in self.warnings:
                self.log(f"  - {warning}", "WARNING")
                
        # Print errors
        if errors_count > 0:
            self.log("\nErrors:", "ERROR")
            for error in self.errors:
                self.log(f"  - {error}", "ERROR")
                
        # Print recommendations
        self.log("\nRecommendations:", "INFO")
        if not build_gradle_ok:
            self.log("  1. Add Mappls SDK dependency to app/build.gradle.kts", "INFO")
        if not main_app_ok:
            self.log("  2. Create/update MainApplication.kt with proper initialization", "INFO")
        if warnings_count > 0:
            self.log("  3. Review warnings above and update credentials if needed", "INFO")
            
        return {
            "status": overall_status,
            "total_checks": total_checks,
            "passed": passed_checks,
            "warnings": warnings_count,
            "errors": errors_count,
            "details": self.results
        }


def main():
    """Main function to run the checker"""
    print("\nMappls SDK Integration Checker")
    print("=" * 60)
    
    # Your credentials
    credentials = {
        "rest_api_key": "6028f7f20d0f77db48c8de02",
        "map_sdk_key": "6028f7f20d0f77db48c8de02",
        "client_id": "96dHZVzsAusVK6FU4fGDDFrrFjNKTicKf9s5uxqepvMUkgNbN7ohQiwG2msyhfGCtBgjyjEtJ6hz-AKhDpnGQA==",
        "client_secret": "lrFxI-iSEg9iM76CbYdAoW35RGGg8KRx1Hy8H2__HLHPn_iKQWf_Cf_hp727UXiHUAKVYJ0D-19AgLDvs0K89THliUOw0ksA"
    }
    
    # Run checker
    checker = MapplsIntegrationChecker(project_path=".")
    results = checker.run_checks(credentials)
    
    # Save results to JSON
    output_file = "mappls_integration_report.json"
    with open(output_file, "w") as f:
        json.dump(results, f, indent=2)
        
    print(f"\nDetailed report saved to: {output_file}")
    print("=" * 60)
    
    # Return exit code based on results
    if results["errors"] > 0:
        sys.exit(1)
    else:
        sys.exit(0)


if __name__ == "__main__":
    main()
