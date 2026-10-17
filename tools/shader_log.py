"""Strict render-error scan with one explicit third-party integration diagnostic.
Oculus injects into Embeddium by design; Embeddium logs its support disclaimer at
ERROR level. Retain that exact diagnostic, but do not waive any other render error.
"""
import re

def scan(text):
    candidates=re.findall(r'^.*(?:\[Render thread/ERROR\]|GL renderer disabled|OutOfMemoryError|EXCEPTION_ACCESS_VIOLATION|GL_INVALID|OpenGL Error).*$' ,text,re.M)
    diagnostic=[];fatal=[]
    for line in candidates:
        if '[Embeddium-MixinTaintDetector/]' in line and 'Mod mixin into Embeddium internals detected.' in line:
            diagnostic.append(line)
        else:fatal.append(line)
    return fatal,diagnostic
