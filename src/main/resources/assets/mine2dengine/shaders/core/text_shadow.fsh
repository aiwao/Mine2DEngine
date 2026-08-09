#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <mine2dengine:shadow.glsl>

uniform sampler2D Sampler0;
layout(std140) uniform Mine2DTextShadow {
    vec4 UvBounds;
    vec4 UvPerGuiUnit;
    float BlurRadius;
    int Grayscale;
};

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float glyphAlpha(vec2 uv) {
    if (
        uv.x < UvBounds.x || uv.y < UvBounds.y ||
        uv.x > UvBounds.z || uv.y > UvBounds.w
    ) {
        return 0.0;
    }
    vec4 texel = texture(Sampler0, uv);
    return Grayscale != 0 ? texel.r : texel.a;
}

float blurredGlyphAlpha() {
    if (BlurRadius <= 0.0) {
        return glyphAlpha(texCoord0);
    }

    const int SAMPLE_RADIUS = 3;
    float alphaSum = 0.0;
    float weightSum = 0.0;
    for (int y = -SAMPLE_RADIUS; y <= SAMPLE_RADIUS; ++y) {
        for (int x = -SAMPLE_RADIUS; x <= SAMPLE_RADIUS; ++x) {
            vec2 guiOffset = vec2(x, y) * (BlurRadius / float(SAMPLE_RADIUS));
            float weight = shadowGaussianWeight(dot(guiOffset, guiOffset), BlurRadius);
            vec2 uvOffset =
                guiOffset.x * UvPerGuiUnit.xy + guiOffset.y * UvPerGuiUnit.zw;
            alphaSum += glyphAlpha(texCoord0 + uvOffset) * weight;
            weightSum += weight;
        }
    }
    return alphaSum / weightSum;
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    color.a *= blurredGlyphAlpha();
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
