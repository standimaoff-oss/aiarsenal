#version 150
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uVolumeTex;
uniform sampler2D uDepthTex;
uniform vec2 uFullTexel;
uniform float uDepthSharpness;

void main() {
    ivec2 sizeLow = textureSize(uVolumeTex, 0);
    vec2 lowPixel = vUv * vec2(sizeLow) - 0.5;
    ivec2 base = ivec2(floor(lowPixel));
    vec2 fracPart = fract(lowPixel);
    float centerDepth = texture(uDepthTex, vUv).r;
    vec4 colorSum = vec4(0.0);
    float weightSum = 0.0;
    for (int y=0; y<2; ++y) {
        for (int x=0; x<2; ++x) {
            ivec2 coord = clamp(base + ivec2(x,y), ivec2(0), sizeLow-1);
            vec2 uv = (vec2(coord)+0.5)/vec2(sizeLow);
            float sampleDepth = texture(uDepthTex, uv).r;
            vec2 bilinear = mix(vec2(1.0)-fracPart, fracPart, vec2(x,y));
            float spatial = max(bilinear.x*bilinear.y, 1e-4);
            float depthWeight = exp(-abs(sampleDepth-centerDepth)*uDepthSharpness);
            float w = spatial*depthWeight;
            colorSum += texture(uVolumeTex, uv)*w;
            weightSum += w;
        }
    }
    fragColor = colorSum/max(weightSum,1e-4);
}
