#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Zoom;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

void main() {
    vec2 resolution = 1.0 / oneTexel;
    vec2 uv = texCoord;
    float aspectRatio = resolution.y / resolution.x;
    uv.y = (uv.y - 0.5) * aspectRatio + 0.5;

    vec2 center = vec2(0.5);
    vec2 delta = (uv - center) * Zoom;
    float radius = sqrt(dot(delta, delta));
    float bind = sqrt(dot(center, center));

    // Fisheye effect
    uv = center + normalize(delta) * tan(radius * Intensity) * bind / tan(bind * Intensity);
    vec3 color = texture(DiffuseSampler, uv).rgb;

    // Vignette
    uv = texCoord;
    uv.y = (uv.y - 0.5) * aspectRatio + 0.5;
    radius = distance(uv, center);
    float vignette = radius / 0.3333;
    vignette = 1.0 - vignette * vignette * vignette * vignette;

    fragColor = vec4(color * vignette, 1.0);
}
