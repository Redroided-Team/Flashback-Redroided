package com.whaltermc.mixin;

import com.moulberry.flashback.exporting.ExportSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mixin(
        targets = "com.moulberry.flashback.exporting.ExportJob",
        remap = false
)
public abstract class ExportJobMixin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android/export");

    /**
     * Writes the export directly to the selected output instead of a
     * CWD-relative temp file: on launchers the CWD is not the game dir, so
     * the final move fails and the video is stranded. Only relative temp
     * names are touched; absolute paths pass through. require=0: inert if
     * Flashback changes writer construction.
     */
    @ModifyArgs(
            method = "createVideoWriter",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/AsyncFFmpegVideoWriter;" +
                            "<init>(Lcom/moulberry/flashback/exporting/ExportSettings;" +
                            "Ljava/lang/String;)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$writeDirectlyToOutput(Args args) {
        try {
            Object settingsArg = args.get(0);
            Object nameArg = args.get(1);
            if (!(settingsArg instanceof ExportSettings settings)
                    || !(nameArg instanceof String name)
                    || Paths.get(name).isAbsolute()) {
                return;
            }
            Path output = settings.output();
            if (output == null) {
                return;
            }
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            args.set(1, output.toString());
            LOGGER.info("FBA: writing export directly to {} (cwd={})",
                    output, System.getProperty("user.dir"));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Companion to the direct write above: with no temp file there is nothing
     * to move. If the output already exists, report success; otherwise rethrow
     * so Flashback's own error fallback still applies.
     */
    @Redirect(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/nio/file/Files;" +
                            "move(Ljava/nio/file/Path;Ljava/nio/file/Path;" +
                            "[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static Path flashbackRedroided$moveExportTemp(
            Path source,
            Path target,
            CopyOption[] options
    ) throws IOException {
        try {
            return Files.move(source, target, options);
        } catch (java.nio.file.NoSuchFileException e) {
            try {
                if (!Files.exists(source) && Files.exists(target)) {
                    LOGGER.info("FBA: export already at output, skipping move (target={})",
                            target);
                    return target;
                }
            } catch (Throwable ignored) {
            }
            throw e;
        }
    }
}
