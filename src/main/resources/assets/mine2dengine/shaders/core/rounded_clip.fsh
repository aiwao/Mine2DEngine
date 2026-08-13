#version 330

uniform sampler2D ClipSampler;
layout(std140) uniform Mine2DRoundedClip {
    vec4 ClipBounds;
    vec4 CornerRadiiHorizontal;
    vec4 CornerRadiiVertical;
    vec4 ScreenToLocalX;
    vec4 ScreenToLocalY;
    vec4 ClipViewport;
};

out vec4 fragColor;

float ellipseDistance(vec2 point, vec2 radius) {
    vec2 normalized = point / radius;
    float k0 = length(normalized);
    float k1 = length(point / (radius * radius));
    return k1 > 0.0 ? k0 * (k0 - 1.0) / k1 : -min(radius.x, radius.y);
}

float roundedClipDistance(vec2 point) {
    vec2 minimum = ClipBounds.xy;
    vec2 maximum = minimum + ClipBounds.zw;

    vec2 radius = vec2(CornerRadiiHorizontal.x, CornerRadiiVertical.x);
    vec2 center = minimum + radius;
    if (radius.x > 0.0 && radius.y > 0.0 && point.x < center.x && point.y < center.y) {
        return ellipseDistance(point - center, radius);
    }

    radius = vec2(CornerRadiiHorizontal.y, CornerRadiiVertical.y);
    center = vec2(maximum.x - radius.x, minimum.y + radius.y);
    if (radius.x > 0.0 && radius.y > 0.0 && point.x > center.x && point.y < center.y) {
        return ellipseDistance(point - center, radius);
    }

    radius = vec2(CornerRadiiHorizontal.z, CornerRadiiVertical.z);
    center = maximum - radius;
    if (radius.x > 0.0 && radius.y > 0.0 && point.x > center.x && point.y > center.y) {
        return ellipseDistance(point - center, radius);
    }

    radius = vec2(CornerRadiiHorizontal.w, CornerRadiiVertical.w);
    center = vec2(minimum.x + radius.x, maximum.y - radius.y);
    if (radius.x > 0.0 && radius.y > 0.0 && point.x < center.x && point.y > center.y) {
        return ellipseDistance(point - center, radius);
    }

    return -1.0;
}

void main() {
    vec2 viewport = ClipViewport.xy;
    ivec2 textureSizePixels = textureSize(ClipSampler, 0);
    vec2 guiPosition = vec2(
        gl_FragCoord.x * viewport.x / float(textureSizePixels.x),
        (1.0 - gl_FragCoord.y / float(textureSizePixels.y)) * viewport.y
    );
    vec2 localPosition = vec2(
        dot(ScreenToLocalX.xyz, vec3(guiPosition, 1.0)),
        dot(ScreenToLocalY.xyz, vec3(guiPosition, 1.0))
    );

    float distanceToClip = roundedClipDistance(localPosition);
    float antialiasWidth = max(fwidth(distanceToClip), 0.0001);
    float coverage = clamp(0.5 - distanceToClip / antialiasWidth, 0.0, 1.0);
    if (coverage <= 0.0) {
        discard;
    }

    vec2 sourceUv = vec2(guiPosition.x / viewport.x, 1.0 - guiPosition.y / viewport.y);
    vec4 source = texture(ClipSampler, sourceUv);
    fragColor = source * coverage;
    if (fragColor.a <= 0.0) {
        discard;
    }
}
