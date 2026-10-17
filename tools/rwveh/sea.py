# Build a raised 12-deep "sea" basin on the flat test world via RCON (fill <= 32768 blocks per command).
import subprocess, sys
def rc(c):
    for _ in range(3):
        r = subprocess.run(["python3", "tools/rcon.py", "--password", "bm21test", c], capture_output=True, text=True)
        if r.returncode == 0: return r.stdout.strip()
    return "ERR " + r.stderr
CX, X0, X1, Z0, Z1, Y0, Y1 = 300, 240, 360, -200, 200, -60, -49
print(rc(f"forceload add {X0-2} {Z0-2} {X1+2} {Z1+2}"))
print(rc(f"fill {X0-1} {Y0} {Z0-1} {X1+1} {Y1} {Z0-1} stone"))
print(rc(f"fill {X0-1} {Y0} {Z1+1} {X1+1} {Y1} {Z1+1} stone"))
print(rc(f"fill {X0-1} {Y0} {Z0-1} {X0-1} {Y1} {Z1+1} stone"))
print(rc(f"fill {X1+1} {Y0} {Z0-1} {X1+1} {Y1} {Z1+1} stone"))
z = Z0
while z <= Z1:
    z2 = min(Z1, z + 19)
    out = rc(f"fill {X0} {Y0} {z} {X1} {Y1} {z2} water")
    z = z2 + 1
print("last", out)
