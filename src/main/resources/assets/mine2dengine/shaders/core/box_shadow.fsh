#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Mine2DBoxShadow {
    vec4 ShadowColor;
    vec2 ShadowSize;
    float BlurRadius;
    float CornerRadius;
};

in vec2 shadowUv;

out vec4 fragColor;

float roundedBoxDistance(vec2 point, vec2 halfSize, float radius) {
    vec2 distanceToEdge = abs(point) - halfSize + vec2(radius);
    return length(max(distanceToEdge, vec2(0.0)))
        + min(max(distanceToEdge.x, distanceToEdge.y), 0.0)
        - radius;
}

void main() {
    vec2 halfSize = ShadowSize * 0.5;
    vec2 drawSize = ShadowSize + vec2(BlurRadius * 2.0);
    vec2 point = shadowUv * drawSize - halfSize - vec2(BlurRadius);
    float radius = clamp(CornerRadius, 0.0, min(halfSize.x, halfSize.y));
    float distanceToBox = roundedBoxDistance(point, halfSize, radius);
    float coverage = BlurRadius > 0.0
        ? 1.0 - smoothstep(-BlurRadius, BlurRadius, distanceToBox)
        : (distanceToBox <= 0.0 ? 1.0 : 0.0);

    vec4 color = ShadowColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
