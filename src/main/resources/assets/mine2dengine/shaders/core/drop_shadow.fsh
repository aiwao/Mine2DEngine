#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <mine2dengine:shadow.glsl>

uniform sampler2D DropShadowSampler;
layout(std140) uniform Mine2DDropShadow {
    vec4 ShadowColor;
    vec4 OffsetAndViewport;
    vec4 BlurAxes;
    vec4 ShadowParameters;
};

in vec4 vertexColor;

out vec4 fragColor;

float sourceAlpha(vec2 guiPosition) {
    vec2 viewport = OffsetAndViewport.zw;
    vec2 uv = vec2(guiPosition.x / viewport.x, 1.0 - guiPosition.y / viewport.y);
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) {
        return 0.0;
    }
    return texture(DropShadowSampler, uv).a;
}

float blurredAlpha(vec2 sourcePosition) {
    float blurRadius = ShadowParameters.x;
    if (blurRadius <= 0.0) {
        return sourceAlpha(sourcePosition);
    }

    const int SAMPLE_RADIUS = 3;
    vec2 blurAxisX = BlurAxes.xy;
    vec2 blurAxisY = BlurAxes.zw;
    float alphaSum = 0.0;
    float weightSum = 0.0;
    for (int y = -SAMPLE_RADIUS; y <= SAMPLE_RADIUS; ++y) {
        for (int x = -SAMPLE_RADIUS; x <= SAMPLE_RADIUS; ++x) {
            vec2 localOffset = vec2(x, y) * (blurRadius / float(SAMPLE_RADIUS));
            vec2 screenOffset =
                blurAxisX * (float(x) / float(SAMPLE_RADIUS)) +
                blurAxisY * (float(y) / float(SAMPLE_RADIUS));
            float weight = shadowGaussianWeight(dot(localOffset, localOffset), blurRadius);
            alphaSum += sourceAlpha(sourcePosition + screenOffset) * weight;
            weightSum += weight;
        }
    }
    return alphaSum / weightSum;
}

void main() {
    vec2 viewport = OffsetAndViewport.zw;
    vec2 fragmentPosition = vec2(
        gl_FragCoord.x * viewport.x / float(textureSize(DropShadowSampler, 0).x),
        (1.0 - gl_FragCoord.y / float(textureSize(DropShadowSampler, 0).y)) * viewport.y
    );
    float alpha = blurredAlpha(fragmentPosition - OffsetAndViewport.xy);

    vec4 color = ShadowColor * ColorModulator * vertexColor;
    color.a *= alpha;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
