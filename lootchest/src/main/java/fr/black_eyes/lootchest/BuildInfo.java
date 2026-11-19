package fr.black_eyes.lootchest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/** Immutable provenance embedded into each packaged LootChest artifact. */
public record BuildInfo(
		String pluginVersion,
		String buildNumber,
		String sourceCommit,
		boolean sourceDirty,
		String paperTarget,
		String paperBuild,
		String paperChannel,
		String paperApi,
		String javaTarget,
		String cmiApiVersion,
		String cmiLibApiVersion,
		List<String> cmiTestedPairs,
		String artifactName
) {
	private static final String RESOURCE = "lootchest-build.properties";
	private static final String UNKNOWN = "unknown";

	public BuildInfo {
		cmiTestedPairs = List.copyOf(cmiTestedPairs);
	}

	public static BuildInfo load(Logger logger) {
		Properties properties = new Properties();
		try (InputStream stream = BuildInfo.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				logger.warning("Build metadata is missing from this LootChest artifact.");
				return unknown();
			}
			properties.load(stream);
			return new BuildInfo(
					value(properties, "plugin.version"),
					value(properties, "build.number"),
					value(properties, "source.commit"),
					Boolean.parseBoolean(value(properties, "source.dirty")),
					value(properties, "paper.target"),
					value(properties, "paper.build"),
					value(properties, "paper.channel"),
					value(properties, "paper.api"),
					value(properties, "java.target"),
					value(properties, "cmi.api"),
					value(properties, "cmilib.api"),
					loadCmiTestedPairs(properties),
					value(properties, "artifact.name"));
		} catch (IOException | IllegalArgumentException exception) {
			logger.warning("Build metadata could not be read: " + exception.getMessage());
			return unknown();
		}
	}

	public static BuildInfo unknown() {
		return new BuildInfo(
				UNKNOWN, UNKNOWN, UNKNOWN, false, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN,
				UNKNOWN, UNKNOWN, List.of(), UNKNOWN);
	}

	public boolean isTestedCmiPair(String cmiVersion, String cmiLibVersion) {
		return cmiTestedPairs.contains(cmiVersion + "/" + cmiLibVersion);
	}

	public String sourceDisplay() {
		return sourceCommit + (sourceDirty ? "-dirty" : "");
	}

	private static String value(Properties properties, String key) {
		return properties.getProperty(key, UNKNOWN).trim();
	}

	private static List<String> loadCmiTestedPairs(Properties properties) {
		List<String> pairs = new ArrayList<>();
		String cmi = value(properties, "cmi.tested");
		String cmiLib = value(properties, "cmilib.tested");
		if (!UNKNOWN.equals(cmi) && !UNKNOWN.equals(cmiLib)) {
			pairs.add(cmi + "/" + cmiLib);
		}
		for (String pair : properties.getProperty("cmi.tested.additional", "").split(",")) {
			if (!pair.isBlank()) {
				pairs.add(pair.strip());
			}
		}
		return pairs;
	}
}
