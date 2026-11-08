"""Normalize Windows AAPT2 ZIP entry separators before signing the APK."""
from copy import copy
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as source, zipfile.ZipFile(sys.argv[2], 'w') as target:
    for info in source.infolist():
        data = source.read(info)
        normalized = copy(info)
        normalized.filename = info.filename.replace('\\', '/')
        target.writestr(normalized, data)
