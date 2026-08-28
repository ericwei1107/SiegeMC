package woo.siegePlugin.map;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

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
            Files.walkFileTree(template, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    Path relative = template.relativize(directory);
                    Files.createDirectories(destination.resolve(relative));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    String name = file.getFileName().toString();
                    if (!name.equals("session.lock") && !name.equals("uid.dat")) {
                        Files.copy(file, destination.resolve(template.relativize(file)));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return destination;
        } catch (IOException | RuntimeException failure) {
            deleteActiveCopy(activeRoot, destination);
            throw failure;
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
        Files.walkFileTree(normalizedCopy, new SimpleFileVisitor<>() {
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
