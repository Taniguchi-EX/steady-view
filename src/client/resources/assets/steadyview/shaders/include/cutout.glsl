#ifndef STEADYVIEW_CUTOUT_GLSL
#define STEADYVIEW_CUTOUT_GLSL

// Steady View: see-through cutout for third-person view.
// Skip fragments near the line from the camera to the player's eye and in front of the player.
// Back faces are already culled, so skipping the camera-facing face makes a block see-through.
// The region is a truncated cone (radius SteadyViewCutout.w at the camera, RADIUS_NEAR_EYE times it near the eye).
// Its edge is dithered so that it fades out gradually.
// Requires <minecraft:globals.glsl> (SteadyViewCutout).
// Keep the constants in sync with SeeThrough.java.

const float STEADYVIEW_KEEP_BEFORE_EYE = 0.7;
const float STEADYVIEW_RADIUS_NEAR_EYE = 0.5;
const float STEADYVIEW_EDGE = 0.4;

float steadyViewDither() {
    // 4x4 Bayer matrix (0 to 1)
    int x = int(mod(gl_FragCoord.x, 4.0));
    int y = int(mod(gl_FragCoord.y, 4.0));
    const float bayer[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    return (bayer[x + y * 4] + 0.5) / 16.0;
}

// cameraRelativePos: position relative to the camera, in world axes (blocks)
bool steadyViewCutout(vec3 cameraRelativePos) {
    float radius = SteadyViewCutout.w;
    if (radius <= 0.0) {
        return false;
    }

    vec3 toEye = SteadyViewCutout.xyz;
    float length2 = dot(toEye, toEye);
    float eyeDistance = sqrt(length2);
    // Position along the line (0: camera, 1: eye). Stop a little before the eye
    float t = dot(cameraRelativePos, toEye) / length2;
    float stop = 1.0 - STEADYVIEW_KEEP_BEFORE_EYE / eyeDistance;
    if (t <= 0.0 || t >= stop) {
        return false;
    }

    float distanceFromLine = length(cameraRelativePos - toEye * t);
    float localRadius = radius * mix(1.0, STEADYVIEW_RADIUS_NEAR_EYE, t / stop);
    if (distanceFromLine < localRadius) {
        return true;
    }

    float edge = (distanceFromLine - localRadius) / STEADYVIEW_EDGE;
    return edge < 1.0 && edge < steadyViewDither();
}

#endif
