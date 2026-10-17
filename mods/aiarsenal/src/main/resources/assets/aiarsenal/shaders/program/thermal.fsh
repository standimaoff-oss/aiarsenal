#version 150
// r21 thermal imager, white-hot: the world is drawn in dim, low-contrast greys; bodies / running engines were
// re-rendered in a magenta heat signature (red = blue = heat, green = 0) and come out white

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float heat(vec2 uv) {
    vec3 c = texture(DiffuseSampler, uv).rgb;
    return clamp((min(c.r, c.b) - c.g * 1.4) * 1.5, 0.0, 1.0);
}

void main() {
    vec2 px = 1.0 / InSize;
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    // slight blur of the cold scene (microbolometer resolution)
    vec3 s = texture(DiffuseSampler, texCoord + vec2(px.x, 0.0)).rgb + texture(DiffuseSampler, texCoord - vec2(px.x, 0.0)).rgb
           + texture(DiffuseSampler, texCoord + vec2(0.0, px.y)).rgb + texture(DiffuseSampler, texCoord - vec2(0.0, px.y)).rgb;
    l = mix(l, dot(s * 0.25, vec3(0.299, 0.587, 0.114)), 0.5);
    float cold = 0.10 + 0.38 * pow(l, 1.1);
    // heat with a soft halo
    float h = heat(texCoord);
    float hh = max(max(heat(texCoord + vec2(px.x * 2.0, 0.0)), heat(texCoord - vec2(px.x * 2.0, 0.0))),
                   max(heat(texCoord + vec2(0.0, px.y * 2.0)), heat(texCoord - vec2(0.0, px.y * 2.0))));
    float hot = max(h, hh * 0.6);
    float v = max(cold, 0.3 + 0.7 * hot) * step(0.02, hot) + cold * (1.0 - step(0.02, hot));
    v += (hash(floor(texCoord * InSize * 0.5) + vec2(Time * 53.0, Time * 29.0)) - 0.5) * 0.04;
    vec2 d = texCoord - 0.5;
    v *= clamp(1.1 - dot(d, d) * 1.2, 0.0, 1.0);
    fragColor = vec4(vec3(v), 1.0);
}
