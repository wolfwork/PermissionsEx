package ru.tehkode.permissions.backends.file;

import org.bukkit.configuration.ConfigurationSection;
import ru.tehkode.permissions.PermissionsGroupData;
import ru.tehkode.permissions.PermissionsUserData;
import ru.tehkode.permissions.bukkit.PermissionsEx;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class FileData implements PermissionsUserData, PermissionsGroupData {
	protected transient final FileConfig config;
	private String nodePath, entityName;
	private final String basePath;
	private ConfigurationSection node;
	protected boolean virtual = true;
	private final String parentPath;

	public FileData(String basePath, String name, FileConfig config, String parentPath) {
		this.config = config;
		this.basePath = basePath;
		this.node = findNode(name);
		this.parentPath = parentPath;
	}

	private ConfigurationSection findExistingNode(String entityName, boolean set) {
		String nodePath = FileBackend.buildPath(basePath, entityName);

		ConfigurationSection entityNode = this.config.getConfigurationSection(nodePath);

		if (entityNode != null) {
			this.virtual = false;
			if (set) {
				this.nodePath = nodePath;
				this.entityName = entityName;
			}
			return entityNode;
		}

		ConfigurationSection users = this.config.getConfigurationSection(basePath);

		if (users != null) {
			for (Map.Entry<String, Object> entry : users.getValues(false).entrySet()) {
				if (entry.getKey().equalsIgnoreCase(entityName)
						&& entry.getValue() instanceof ConfigurationSection) {
					if (set) {
						this.nodePath = FileBackend.buildPath(basePath, entry.getKey());
						this.entityName = entry.getKey();
					}
					return (ConfigurationSection) entry.getValue();
				}
			}
		}

		return null;
	}

	private ConfigurationSection findNode(String entityName) {
		ConfigurationSection section = findExistingNode(entityName, true);
		if (section != null) {
			return section;
		}

		// Silly workaround for empty nodes
		this.nodePath = FileBackend.buildPath(basePath, entityName);
		section = this.config.createSection(nodePath);
		this.entityName = entityName;
		this.config.set(nodePath, null);
		this.virtual = true; // Make sure

		return section;

	}

	@Override
	public String getIdentifier() {
		return entityName;
	}

	@Override
	public boolean setIdentifier(String identifier) {
		ConfigurationSection section = findExistingNode(identifier, false);
		if (section != null) {
			return false;
		}
		String oldNodePath = this.nodePath;
		this.nodePath = FileBackend.buildPath(basePath, identifier);
		this.entityName = identifier;
		if (!this.isVirtual()) {
			this.config.set(oldNodePath, null);
			this.config.set(nodePath, node);
			this.save();

		}
		return true;
	}

	/**
	 * Permissions
	 */
	@Override
	public List<String> getPermissions(String worldName) {
		List<String> result = this.node.getStringList(formatPath(worldName, "permissions"));

		return result == null ? Collections.<String>emptyList() : Collections.unmodifiableList(result);
	}

	@Override
	public void setPermissions(List<String> permissions, String worldName) {
		this.node.set(formatPath(worldName, "permissions"), permissions == null ? null : new ArrayList<>(permissions));
		save();
	}

	@Override
	public Map<String, List<String>> getPermissionsMap() {
		Map<String, List<String>> allPermissions = new HashMap<>();

		// Common permissions
		List<String> commonPermissions = this.node.getStringList("permissions");
		if (commonPermissions != null) {
			allPermissions.put(null, Collections.unmodifiableList(commonPermissions));
		}

		//World-specific permissions
		ConfigurationSection worldsSection = this.node.getConfigurationSection("worlds");
		if (worldsSection != null) {
			for (String world : worldsSection.getKeys(false)) {
				List<String> worldPermissions = this.node.getStringList(FileBackend.buildPath("worlds", world, "permissions"));
				if (commonPermissions != null) {
					allPermissions.put(world, Collections.unmodifiableList(worldPermissions));
				}
			}
		}

		return Collections.unmodifiableMap(allPermissions);
	}

	@Override
	public Set<String> getWorlds() {
		ConfigurationSection worldsSection = this.node.getConfigurationSection("worlds");

		if (worldsSection == null) {
			return Collections.emptySet();
		}

		return Collections.unmodifiableSet(worldsSection.getKeys(false));
	}

	@Override
	public String getOption(String option, String worldName) {
		String ret = this.node.getString(formatPath(worldName, "options", option));
		if (ret == null && (option.equals("prefix") || option.equals("suffix"))) {
			ret = this.node.getString(formatPath(worldName, option));
		}
		return ret;
	}

	@Override
	public void setOption(String option, String value, String worldName) {
		this.node.set(formatPath(worldName, "options", option), value);
		if (option.equals("prefix") || option.equals("suffix")) {
			this.node.set(formatPath(worldName, option), null); // Delete old-style prefix/suffix
		}
		save();
	}

	private <K, V> void putIfNotNull(Map<K, V> map, K key, V value) {
		if (value != null) {
			map.put(key, value);
		}
	}

	@Override
	public Map<String, String> getOptions(String worldName) {
		ConfigurationSection optionsSection = this.node.getConfigurationSection(formatPath(worldName, "options"));
		Map<String, String> worldOptions = new LinkedHashMap<>();

		putIfNotNull(worldOptions, "prefix", this.node.getString(formatPath(worldName, "prefix")));
		putIfNotNull(worldOptions, "suffix", this.node.getString(formatPath(worldName, "suffix")));

		if (optionsSection == null) {
			return Collections.unmodifiableMap(worldOptions);
		}

		return Collections.unmodifiableMap(collectOptions(worldOptions, optionsSection));

	}

	@Override
	public Map<String, Map<String, String>> getOptionsMap() {
		Map<String, Map<String, String>> allOptions = new HashMap<>();

		allOptions.put(null, this.getOptions(null));

		for (String worldName : this.getWorlds()) {
			allOptions.put(worldName, this.getOptions(worldName));
		}

		return Collections.unmodifiableMap(allOptions);
	}

	@Override
	public boolean isVirtual() {
		return virtual;
	}

	@Override
	public void save() {
		if (isVirtual()) {
			this.config.set(nodePath, node);
			virtual = false;
		}

		try {
			this.config.save();
		} catch (IOException e) {
			PermissionsEx.getPermissionManager().getLogger().log(Level.SEVERE, "Error saving data for  " + nodePath, e);
		}
	}

	@Override
	public void remove() {
		this.config.set(nodePath, null);
		this.save();
	}

	@Override
	public Map<String, List<String>> getParentsMap() {
		Map<String, List<String>> ret = new HashMap<>();
		ret.put(null, getParents(null));

		for (String world : getWorlds()) {
			ret.put(world, getParents(world));
		}
		return Collections.unmodifiableMap(ret);
	}


	@Override
	public List<String> getParents(String worldName) {
		List<String> parents = this.node.getStringList(formatPath(worldName, parentPath));
		for (Iterator<String> it = parents.iterator(); it.hasNext();) {
			final String test = it.next();
			if (test == null || test.isEmpty()) {
				it.remove();
			}
		}

		if (parents == null || parents.isEmpty()) {
			return Collections.emptyList();
		}

		return Collections.unmodifiableList(parents);
	}

	@Override
	public void setParents(List<String> parents, String worldName) {
		this.node.set(formatPath(worldName, parentPath), parents == null ? null : new ArrayList<>(parents));
		save();
	}

	@Override
	public void load() {
		// Already loaded bc file
	}

	@Override
	public boolean isDefault(String world) {
		return this.node.getBoolean(formatPath(world, "default"));
	}

	@Override
	public void setDefault(boolean def, String world) {
		this.node.set(formatPath(world, "default"), def);
		save();
	}

	private Map<String, String> collectOptions(Map<String, String> options, ConfigurationSection section) {
		for (String key : section.getKeys(true)) {
			if (section.isConfigurationSection(key)) {
				continue;
			}

			options.put(key.replace(section.getRoot().options().pathSeparator(), '.'), section.getString(key));
		}

		return options;
	}

	protected static String formatPath(String worldName, String node, String value) {
		String path = FileBackend.buildPath(node, value);

		if (worldName != null && !worldName.isEmpty()) {
			path = FileBackend.buildPath("worlds", worldName, path);
		}

		return path;
	}

	protected static String formatPath(String worldName, String node) {
		String path = node;

		if (worldName != null && !worldName.isEmpty()) {
			path = FileBackend.buildPath("worlds", worldName, path);
		}

		return path;
	}
}
