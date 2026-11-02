import sys
import platform
import datetime

def main():
    print("=" * 40)
    print("Mobile Code: Python Development Environment")
    print("=" * 40)
    print(f"Python Version: {platform.python_version()}")
    print(f"Operating System: {platform.system()} ({platform.machine()})")
    print(f"Current Timestamp: {datetime.datetime.now()}")
    print("Environment ready for programming on mobile!")

if __name__ == "__main__":
    main()

