#version 330

#moj_import <mine2dengine:shadow.glsl>

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
    vec4 CornerRadiiHorizontal;
    vec4 CornerRadiiVertical;
};

in vec2 shadowUv;

out vec4 fragColor;

vec2 cornerRadii(vec2 point) {
    if (point.y < 0.0) {
        return point.x < 0.0
            ? vec2(CornerRadiiHorizontal.x, CornerRadiiVertical.x)
            : vec2(CornerRadiiHorizontal.y, CornerRadiiVertical.y);
    }
    return point.x < 0.0
        ? vec2(CornerRadiiHorizontal.w, CornerRadiiVertical.w)
        : vec2(CornerRadiiHorizontal.z, CornerRadiiVertical.z);
}

float boxDistance(vec2 point, vec2 halfSize) {
    vec2 distanceToEdge = abs(point) - halfSize;
    return length(max(distanceToEdge, vec2(0.0)))
        + min(max(distanceToEdge.x, distanceToEdge.y), 0.0);
}

float ellipseDistance(vec2 point, vec2 radii) {
    float angle = atan(point.y * radii.x, point.x * radii.y);
    const float HALF_PI = 1.5707963267948966;
    for (int iteration = 0; iteration < 5; ++iteration) {
        float sine = sin(angle);
        float cosine = cos(angle);
        vec2 delta = vec2(radii.x * cosine, radii.y * sine) - point;
        vec2 tangent = vec2(-radii.x * sine, radii.y * cosine);
        float functionValue = dot(delta, tangent);
        float derivative = dot(tangent, tangent)
            + dot(delta, vec2(-radii.x * cosine, -radii.y * sine));
        if (abs(derivative) <= 0.00001) {
            break;
        }
        angle = clamp(angle - functionValue / derivative, 0.0, HALF_PI);
    }

    vec2 nearest = vec2(radii.x * cos(angle), radii.y * sin(angle));
    float signToEdge = dot(point / radii, point / radii) < 1.0 ? -1.0 : 1.0;
    return length(point - nearest) * signToEdge;
}

float roundedBoxDistance(vec2 point, vec2 halfSize) {
    vec2 radii = cornerRadii(point);
    if (radii.x <= 0.0 || radii.y <= 0.0) {
        return boxDistance(point, halfSize);
    }

    vec2 absolutePoint = abs(point);
    vec2 cornerPoint = absolutePoint - halfSize + radii;
    if (cornerPoint.x > 0.0 && cornerPoint.y > 0.0) {
        return ellipseDistance(cornerPoint, radii);
    }
    if (cornerPoint.x > 0.0) {
        return cornerPoint.x - radii.x;
    }
    if (cornerPoint.y > 0.0) {
        return cornerPoint.y - radii.y;
    }
    return max(absolutePoint.x - halfSize.x, absolutePoint.y - halfSize.y);
}

void main() {
    vec2 halfSize = ShadowSize * 0.5;
    vec2 drawSize = ShadowSize + vec2(BlurRadius * 2.0);
    vec2 point = shadowUv * drawSize - halfSize - vec2(BlurRadius);
    float distanceToBox = roundedBoxDistance(point, halfSize);
    float coverage = shadowCoverage(distanceToBox, BlurRadius);

    vec4 color = ShadowColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
