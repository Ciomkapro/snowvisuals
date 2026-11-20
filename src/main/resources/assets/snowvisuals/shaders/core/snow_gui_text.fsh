#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Sample a screen pixel's footprint when glyphs are minified, with a one-texel
    // limit so the filter cannot fetch the neighbouring glyph in the atlas.
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    vec2 dx = dFdx(texCoord0);
    vec2 dy = dFdy(texCoord0);
    float footprint = max(length(dx * atlasSize), length(dy * atlasSize));

    float coverage = texture(Sampler0, texCoord0).r;
    if (footprint > 1.0) {
        float radius = min(0.23, 0.88 / footprint);
        vec2 x = dx * radius;
        vec2 y = dy * radius;
        float filtered = 0.25 * (
                texture(Sampler0, texCoord0 - x - y).r +
                texture(Sampler0, texCoord0 + x - y).r +
                texture(Sampler0, texCoord0 - x + y).r +
                texture(Sampler0, texCoord0 + x + y).r);
        coverage = mix(coverage, filtered, smoothstep(1.0, 2.8, footprint));
    }

    // The intensity atlas encodes coverage; keep the requested RGB at softened edges.
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 1.0 / 255.0) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}