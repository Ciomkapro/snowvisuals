#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
uniform sampler2D Sampler0;

in vec2 localPosition;
in vec4 vertexColor;
flat in vec4 shape;

out vec4 fragColor;

void main() {
    vec2 edge = abs(localPosition) - shape.xy + shape.z;
    float distance = length(max(edge, 0.0)) + min(max(edge.x, edge.y), 0.0) - shape.z;
    float feather = max(fwidth(distance) * 0.58, 0.015625);
    float coverage = 1.0 - smoothstep(-feather, feather, distance);
    if (shape.w > 0.0) {
        coverage *= smoothstep(-shape.w - feather, -shape.w + feather, distance);
    }
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage * texture(Sampler0, vec2(0.5)).a;
    if (color.a <= 0.001) discard;
    fragColor = color;
}
