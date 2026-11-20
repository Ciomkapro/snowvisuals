#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

vec3 hue(float value) {
    vec3 spectrum = abs(fract(value + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return clamp(spectrum - 1.0, 0.0, 1.0);
}

void main() {
    vec3 color = texCoord0.y >= 2.0
            ? hue(texCoord0.y - 2.0)
            : mix(vec3(1.0), vertexColor.rgb, texCoord0.x) * (1.0 - texCoord0.y);
    fragColor = vec4(color, texture(Sampler0, vec2(0.5)).a);
}
