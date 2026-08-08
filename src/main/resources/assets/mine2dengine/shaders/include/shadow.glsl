// CSS shadow blur radii map to a Gaussian standard deviation of half the radius.
float shadowStandardDeviation(float blurRadius) {
    return max(blurRadius * 0.5, 0.0001);
}

float shadowGaussianWeight(float squaredDistance, float blurRadius) {
    float deviation = shadowStandardDeviation(blurRadius);
    return exp(-squaredDistance / (2.0 * deviation * deviation));
}

float shadowErrorFunction(float value) {
    float signValue = value < 0.0 ? -1.0 : 1.0;
    float absoluteValue = abs(value);
    float t = 1.0 / (1.0 + 0.3275911 * absoluteValue);
    float polynomial = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
        - 0.284496736) * t + 0.254829592) * t;
    return signValue * (1.0 - polynomial * exp(-absoluteValue * absoluteValue));
}

float shadowCoverage(float signedDistance, float blurRadius) {
    if (blurRadius <= 0.0) {
        return signedDistance <= 0.0 ? 1.0 : 0.0;
    }
    float denominator = 1.41421356237 * shadowStandardDeviation(blurRadius);
    return 0.5 * (1.0 - shadowErrorFunction(signedDistance / denominator));
}
