#version 300 es
// FBA: MobileGlues / GLES-compatible ImGui fragment shader.
// Original (desktop) uses `#version 330` + `GL_ARB_separate_shader_objects : require`
// which fails on MobileGlues/GLES. This variant is only used when MobileGlues is detected.
precision highp float;
precision highp int;

// Same reason as the vertex shader: no `layout(location=)` on the inputs.
in vec4 vertexColor;
in vec2 texCoord;

uniform sampler2D Sampler0;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vertexColor * texture(Sampler0, texCoord);
}
