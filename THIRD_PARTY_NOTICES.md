# Third-party notices

## VolPyro volumetric renderer — MIT

The VolPyro 1.5.6 Forge metadata names **VolPyro Contributors** as authors and declares the **MIT** license. The original JAR and decompiled reference source are not redistributed in this repository. The four upstream shader files under `assets/aiarsenal/shaders/volpyro/` are retained byte-for-byte; their SHA-256 digests are pinned by `tools/check_volumetric.py`.

The port changes the namespace and Forge integration, adds resource-lifecycle and OpenGL-state handling, bounds caches and queues, and routes effects through this mod's visual-event system. Donor items, blocks, recipes, entities, and server-side damage logic are not imported. `arsenal_smoke.glsl` is an additional, original smoke field and material palette.

The supplied archive declared MIT in `META-INF/mods.toml` but did not include a separate license file or copyright year. The standard MIT license text is reproduced below; no ownership of VolPyro work is claimed.

### MIT License

Copyright (c) VolPyro Contributors (year not specified upstream)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
