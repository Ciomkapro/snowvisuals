#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // Draw the same mask for square art and center-cropped rectangular art.
    vec2 sourceSize = vec2(textureSize(Sampler0, 0));
    vec2 crop = vec2(min(sourceSize.x, sourceSize.y)) / sourceSize;
    vec2 local = (texCoord0 - vec2(0.5)) / crop;
    float radius = 0.17;
    vec2 corner = abs(local) - vec2(0.5 - radius);
    float distance = length(max(corner, 0.0)) + min(max(corner.x, corner.y), 0.0) - radius;
    float feather = max(fwidth(distance) * 0.65, 0.002);
    float mask = 1.0 - smoothstep(-feather, feather, distance);
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.a *= mask;
    if (color.a <= 1.0 / 255.0) discard;
    fragColor = color;
}