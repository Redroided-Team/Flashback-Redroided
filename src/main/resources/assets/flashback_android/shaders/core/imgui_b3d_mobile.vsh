#version 300 es
// FBA: MobileGlues / GLES-compatible ImGui vertex shader.
// Original (desktop) uses `#version 330` + `GL_ARB_separate_shader_objects : require`
// which fails on MobileGlues/GLES. This variant is only used when MobileGlues is detected.
precision highp float;
precision highp int;

layout(location = 0) in vec2 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec4 Color;

// No `layout(location=)` on the outputs. MobileGlues rejects it with
// "Qualifier 'location' is not allowed on vertex output variable" because
// GLSL ES derives output locations from the fragment input declarations.
// Declaring them here made the program fail to link, so ImGui rendered
// nothing at all.
out vec4 vertexColor;
out vec2 texCoord;

layout(std140) uniform UBO {
    mat4 mvp;
};

void main() {
    gl_Position = mvp * vec4(Position, 0.0, 1.0);
    vertexColor = Color;
    texCoord = UV;
}
