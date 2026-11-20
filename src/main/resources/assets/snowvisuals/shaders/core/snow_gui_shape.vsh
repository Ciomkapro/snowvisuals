#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 localPosition;
out vec4 vertexColor;
flat out vec4 shape;

void main() {
    // The quad's UVs carry the two signed extents, then radius and stroke in their fractional bits.
    vec2 extent = round(UV0);
    vec2 style = (UV0 - extent) * 128.0;
    localPosition = extent * 0.5;
    shape = vec4(abs(localPosition) - vec2(4.0), style);
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
