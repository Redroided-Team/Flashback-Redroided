// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

// Thrown by a native file-dialog leg the user cancels, so it settles null
// directly instead of falling through to the next leg. Lives outside the
// mixin package on purpose: helper types inside a mixin package cannot be
// referenced and crash loading with IllegalClassLoadError.
public final class NfdCancelled extends Exception {
}
