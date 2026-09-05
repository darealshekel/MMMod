package com.mmm.release;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.junit.jupiter.api.Test;

class ReleaseMetadataTest
{
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();

    @Test
    void compiledMixinPackageContainsOnlyMixins() throws Exception
    {
        // Runtime helpers here are rejected by Mixin, even when Java compilation succeeds.
        Path classes = PROJECT_ROOT.resolve("build/classes/java/main/com/mmm/mixin");
        assertTrue(Files.isDirectory(classes), "Compiled mixin classes must exist");
        try (var paths = Files.walk(classes))
        {
            for (Path path : paths.filter(file -> file.toString().endsWith(".class")).toList())
            {
                ClassNode node = new ClassNode(Opcodes.ASM9);
                new ClassReader(Files.readAllBytes(path)).accept(node, ClassReader.SKIP_CODE);
                boolean isMixin = node.invisibleAnnotations != null && node.invisibleAnnotations.stream()
                        .anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"));
                assertTrue(isMixin, "Move runtime helper outside the restricted mixin package: " + node.name);
            }
        }
    }

    @Test
    void releaseDocumentationMatchesConfiguredVersion() throws Exception
    {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(PROJECT_ROOT.resolve("gradle.properties")))
        {
            properties.load(reader);
        }

        String version = properties.getProperty("mod_version", "").trim();
        String releaseVersion = version.replaceFirst("\\+.*$", "");
        String readme = Files.readString(PROJECT_ROOT.resolve("README.md"));
        String changelog = Files.readString(PROJECT_ROOT.resolve("CHANGELOG.md"));

        assertTrue(version.isBlank() == false, "mod_version must be set");
        assertTrue(readme.contains("`mmm-" + version + ".jar`"), "README must list the configured release jar");
        assertTrue(changelog.contains("## " + releaseVersion + " "), "CHANGELOG must describe the configured release");
    }

    @Test
    void everyConfiguredClientMixinHasSource() throws Exception
    {
        Path mixinConfig = PROJECT_ROOT.resolve("src/main/resources/mmm.mixins.json");
        JsonObject root = JsonParser.parseString(Files.readString(mixinConfig)).getAsJsonObject();
        JsonArray clientMixins = root.getAsJsonArray("client");

        for (var element : clientMixins)
        {
            String className = element.getAsString().replace('.', '/');
            Path source = PROJECT_ROOT.resolve("src/main/java/com/mmm/mixin/" + className + ".java");
            assertTrue(Files.isRegularFile(source), "Missing source for configured client mixin " + className);
        }
    }
}
