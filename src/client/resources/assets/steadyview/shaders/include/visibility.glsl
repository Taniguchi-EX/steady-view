#ifndef STEADYVIEW_VISIBILITY_GLSL
#define STEADYVIEW_VISIBILITY_GLSL

// Steady View: hide what cannot be seen from the original camera position in third-person view.
// When the camera is kept behind an obstacle, a terrain face is drawn only if the cell in front of it can be seen from
// the original camera position (where vanilla would move the camera) or from the player's eye.
// The visibility of each cell in a STEADYVIEW_GRID_SIZE^3 grid is computed on the CPU (VisibilityGrid.java) and passed
// in Globals (SteadyViewGridOrigin, SteadyViewViewpoint, SteadyViewEye, SteadyViewGrid).
// Requires <minecraft:globals.glsl>.
// Keep the grid size and the cell order in sync with VisibilityGrid.java.

const int STEADYVIEW_GRID_SIZE = 48;

bool steadyViewCellVisible(ivec3 cell) {
    int index = (cell.y * STEADYVIEW_GRID_SIZE + cell.z) * STEADYVIEW_GRID_SIZE + cell.x;
    int word = index >> 5;
    int bits = SteadyViewGrid[word >> 2][word & 3];
    return ((bits >> (index & 31)) & 1) != 0;
}

float steadyViewExitT(float origin, float delta) {
    if (delta > 1.0e-6) {
        return (float(STEADYVIEW_GRID_SIZE) - 0.001 - origin) / delta;
    }
    if (delta < -1.0e-6) {
        return (0.001 - origin) / delta;
    }
    return 1.0e9;
}

// Whether the cell where the line from origin to point leaves the grid is visible
bool steadyViewExitCellVisible(vec3 origin, vec3 point) {
    vec3 delta = point - origin;
    float t = min(min(steadyViewExitT(origin.x, delta.x), steadyViewExitT(origin.y, delta.y)), steadyViewExitT(origin.z, delta.z));
    ivec3 cell = clamp(ivec3(floor(origin + delta * t)), ivec3(0), ivec3(STEADYVIEW_GRID_SIZE - 1));
    return steadyViewCellVisible(cell);
}

// point: position relative to the grid origin (blocks).
// Outside the grid, use the cell where the line from the original camera position (or the eye) leaves the grid.
bool steadyViewPointVisible(vec3 point) {
    if (all(greaterThanEqual(point, vec3(0.0))) && all(lessThan(point, vec3(float(STEADYVIEW_GRID_SIZE))))) {
        return steadyViewCellVisible(ivec3(floor(point)));
    }
    if (steadyViewExitCellVisible(SteadyViewViewpoint.xyz, point)) {
        return true;
    }
    return SteadyViewEye.w > 0.5 && steadyViewExitCellVisible(SteadyViewEye.xyz, point);
}

// gridPos: fragment position relative to the grid origin.
// normal: normal of the face (any length or sign, e.g. from derivatives).
// Returns true if the face must not be drawn.
bool steadyViewHiddenFace(vec3 gridPos, vec3 normal) {
    if (SteadyViewGridOrigin.w == 0) {
        return false;
    }
    // Snap to the main axis, pointing to the camera side (back faces are culled, so the camera is in front of the face)
    vec3 a = abs(normal);
    vec3 axis = a.x >= a.y && a.x >= a.z ? vec3(1.0, 0.0, 0.0) : (a.y >= a.z ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0));
    vec3 cameraPos = vec3(CameraBlockPos - SteadyViewGridOrigin.xyz) - CameraOffset;
    if (dot(axis, cameraPos - gridPos) < 0.0) {
        axis = -axis;
    }
    return !steadyViewPointVisible(gridPos + axis * 0.02);
}

#endif
