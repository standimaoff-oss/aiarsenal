#version 150
// r21 image intensifier (gen 3 style): green phosphor, gain, grain, slight bloom, vignette

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float lum(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 px = 1.0 / InSize;
    float l = lum(texture(DiffuseSampler, texCoord).rgb);
    float b = 0.0;
    b += lum(texture(DiffuseSampler, texCoord + vec2(px.x * 2.0, 0.0)).rgb);
    b += lum(texture(DiffuseSampler, texCoord - vec2(px.x * 2.0, 0.0)).rgb);
    b += lum(texture(DiffuseSampler, texCoord + vec2(0.0, px.y * 2.0)).rgb);
    b += lum(texture(DiffuseSampler, texCoord - vec2(0.0, px.y * 2.0)).rgb);
    l = mix(l, b * 0.25, 0.25);
    l = pow(clamp(l * 1.3 + 0.03, 0.0, 1.0), 0.95);   // the night vision light map already lifts the scene
    float n = hash(floor(texCoord * InSize * 0.5) + vec2(Time * 61.0, Time * 37.0));
    l += (n - 0.5) * 0.11;
    vec2 d = texCoord - 0.5;
    float vig = clamp(1.15 - dot(d, d) * 2.2, 0.0, 1.0);
    l *= vig;
    vec3 g = vec3(0.16, 1.0, 0.28) * l + vec3(0.0, 0.035, 0.005);
    fragColor = vec4(g, 1.0);
}
