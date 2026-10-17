#version 150
in vec2 Position;
out vec2 vUv;
void main() {
    vUv = Position;
    gl_Position = vec4(Position * 2.0 - 1.0, 0.0, 1.0);
}
