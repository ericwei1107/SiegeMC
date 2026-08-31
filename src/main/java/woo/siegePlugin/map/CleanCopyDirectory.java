package woo.siegePlugin.map;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.UUID;

/** Filesystem-only half of map rotation: copies templates and deletes only verified generated folders. */
public final class CleanCopyDirectory {

    private static final String ACTIVE_PREFIX = "siege-active-";

    private CleanCopyDirectory() {
    }

    public static Path copyTemplate(Path template, Path activeRoot, String activeFolderName) throws IOException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(activeRoot, "activeRoot");
        if (!Files.isDirectory(template)) {
            throw new IOException("Map template is not a directory: " + template);
        }
        if (!activeFolderName.startsWith(ACTIVE_PREFIX) || activeFolderName.contains("/") || activeFolderName.contains("\\")) {
            throw new IllegalArgumentException("Active world folder must start with " + ACTIVE_PREFIX);
        }

        Files.createDirectories(activeRoot);
        Path normalizedRoot = activeRoot.toAbsolutePath().normalize();
        Path destination = normalizedRoot.resolve(activeFolderName).normalize();
        if (!destination.getParent().equals(normalizedRoot)) {
            throw new IllegalArgumentException("Active world must be an immediate child of the active root");
        }
        if (Files.exists(destination)) {
            throw new IOException("Generated active world already exists: " + destination);
        }

        try {
            copyWorldDirectory(template, destination);
            return destination;
        } catch (IOException | RuntimeException failure) {
            deleteActiveCopy(activeRoot, destination);
            throw failure;
        }
    }

    /**
     * Replaces one clean template with a saved calibration copy. The previous
     * template is retained beside it, so promotion is recoverable.
     */
    public static Path promoteActiveCopy(
            Path activeRoot,
            Path activeCopy,
            Path templateRoot,
            String templateFolder
    ) throws IOException {
        Objects.requireNonNull(activeRoot, "activeRoot");
        Objects.requireNonNull(activeCopy, "activeCopy");
        Objects.requireNonNull(templateRoot, "templateRoot");
        Objects.requireNonNull(templateFolder, "templateFolder");
        Path normalizedActiveRoot = activeRoot.toAbsolutePath().normalize();
        Path normalizedActive = activeCopy.toAbsolutePath().normalize();
        if (!normalizedActive.getParent().equals(normalizedActiveRoot)
                || !normalizedActive.getFileName().toString().startsWith(ACTIVE_PREFIX)) {
            throw new IllegalArgumentException("Calibration source is not a generated active copy");
        }
        if (templateFolder.contains("/") || templateFolder.contains("\\") || templateFolder.contains("..")) {
            throw new IllegalArgumentException("Template folder must be one safe path segment");
        }
        Path normalizedTemplateRoot = templateRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedTemplateRoot);
        Path template = normalizedTemplateRoot.resolve(templateFolder).normalize();
        if (!template.getParent().equals(normalizedTemplateRoot)) {
            throw new IllegalArgumentException("Template escapes its template root");
        }
        Path staging = normalizedTemplateRoot.resolve("." + templateFolder + ".promoting-" + UUID.randomUUID());
        Path backup = normalizedTemplateRoot.resolve(templateFolder + ".pre-calibration-" + System.currentTimeMillis());
        try {
            copyWorldDirectory(normalizedActive, staging);
            if (!Files.isRegularFile(staging.resolve("level.dat"))) {
                throw new IOException("Promoted calibration copy has no level.dat");
            }
            boolean backedUp = Files.exists(template);
            if (backedUp) moveWithinTemplateRoot(template, backup);
            try {
                moveWithinTemplateRoot(staging, template);
            } catch (IOException promotionFailure) {
                if (backedUp && !Files.exists(template)) moveWithinTemplateRoot(backup, template);
                throw promotionFailure;
            }
            deleteActiveCopy(normalizedActiveRoot, normalizedActive);
            return backedUp ? backup : null;
        } catch (IOException | RuntimeException failure) {
            if (Files.exists(staging)) deleteTree(staging);
            throw failure;
        }
    }

    private static void copyWorldDirectory(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    Path relative = source.relativize(directory);
                    Files.createDirectories(destination.resolve(relative));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    String name = file.getFileName().toString();
                    if (!name.equals("session.lock") && !name.equals("uid.dat")) {
                        Files.copy(file, destination.resolve(source.relativize(file)));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
    }

    private static void moveWithinTemplateRoot(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination);
        }
    }

    public static void deleteActiveCopy(Path activeRoot, Path activeCopy) throws IOException {
        Objects.requireNonNull(activeRoot, "activeRoot");
        Objects.requireNonNull(activeCopy, "activeCopy");
        Path normalizedRoot = activeRoot.toAbsolutePath().normalize();
        Path normalizedCopy = activeCopy.toAbsolutePath().normalize();
        if (!normalizedCopy.getParent().equals(normalizedRoot)
                || !normalizedCopy.getFileName().toString().startsWith(ACTIVE_PREFIX)) {
            throw new IllegalArgumentException("Refusing to delete a path outside the generated active-world root: " + activeCopy);
        }
        if (!Files.exists(normalizedCopy)) {
            return;
        }
        deleteTree(normalizedCopy);
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
