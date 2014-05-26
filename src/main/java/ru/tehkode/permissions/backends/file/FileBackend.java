/*
 * PermissionsEx - Permissions plugin for Bukkit
 * Copyright (C) 2011 t3hk0d3 http://www.tehkode.ru
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package ru.tehkode.permissions.backends.file;

import org.bukkit.configuration.ConfigurationSection;
import ru.tehkode.permissions.PermissionsGroupData;
import ru.tehkode.permissions.PermissionsUserData;
import ru.tehkode.permissions.backends.PermissionBackend;
import ru.tehkode.permissions.PermissionManager;
import ru.tehkode.permissions.backends.caching.CachingGroupData;
import ru.tehkode.permissions.backends.caching.CachingUserData;
import ru.tehkode.permissions.exceptions.PermissionBackendException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author code
 */
public class FileBackend extends PermissionBackend {
	public final static char PATH_SEPARATOR = '/';
	public FileConfig permissions;
	public File permissionsFile;
	private final Map<String, List<String>> worldInheritanceCache = new ConcurrentHashMap<>();
	private final Object lock = new Object();

	public FileBackend(PermissionManager manager, ConfigurationSection config) throws PermissionBackendException {
		super(manager, config);
		String permissionFilename = getConfig().getString("file");

		// Default settings
		if (permissionFilename == null) {
			permissionFilename = "permissions.yml";
			getConfig().set("file", "permissions.yml");
		}

		String baseDir = manager.getPlugin().getConfig().getString("permissions.basedir", manager.getPlugin().getDataFolder().getPath());

		if (baseDir.contains("\\") && !"\\".equals(File.separator)) {
			baseDir = baseDir.replace("\\", File.separator);
		}

		File baseDirectory = new File(baseDir);
		if (!baseDirectory.exists()) {
			baseDirectory.mkdirs();
		}

		this.permissionsFile = new File(baseDir, permissionFilename);
		reload();
	}

	@Override
	public List<String> getWorldInheritance(String world) {
		if (world != null && !world.isEmpty()) {
			List<String> parentWorlds = worldInheritanceCache.get(world);
			if (parentWorlds == null) {
				synchronized (lock) {
					parentWorlds = this.permissions.getStringList(buildPath("worlds", world, "inheritance"));
					if (parentWorlds != null) {
						parentWorlds = Collections.unmodifiableList(parentWorlds);
						worldInheritanceCache.put(world, parentWorlds);
						return parentWorlds;
					}
				}
			} else {
				return parentWorlds;
			}
		}

		return Collections.emptyList();
	}

	@Override
	public Map<String, List<String>> getAllWorldInheritance() {
		synchronized (lock) {
			ConfigurationSection worldsSection = this.permissions.getConfigurationSection("worlds");
			if (worldsSection == null) {
				return Collections.emptyMap();
			}

			Map<String, List<String>> ret = new HashMap<>();
			for (String world : worldsSection.getKeys(false)) {
				ret.put(world, getWorldInheritance(world));
			}
			return Collections.unmodifiableMap(ret);
		}
	}

	@Override
	public void setWorldInheritance(final String world, List<String> rawParentWorlds) {
		if (world == null || world.isEmpty()) {
			return;
		}
		final List<String> parentWorlds = new ArrayList<>(rawParentWorlds);
		worldInheritanceCache.put(world, parentWorlds);

		getExecutor().execute(new Runnable() {
			@Override
			public void run() {
				synchronized (lock) {
					permissions.set(buildPath("worlds", world, "inheritance"), parentWorlds);
					save();
				}
			}
		});
	}

	@Override
	public PermissionsUserData getUserData(String userName) {
		synchronized (lock) {
			final CachingUserData data = new CachingUserData(new FileData("users", userName, this.permissions, "group"), getExecutor(), lock);
			data.load();
			return data;
		}
	}

	@Override
	public PermissionsGroupData getGroupData(String groupName) {
		synchronized (lock) {
			final CachingGroupData data = new CachingGroupData(new FileData("groups", groupName, this.permissions, "inheritance"), getExecutor(), lock);
			data.load();
			return data;
		}
	}

	@Override
	public boolean hasUser(String userName) {
		synchronized (lock) {
			if (this.permissions.isConfigurationSection(buildPath("users", userName))) {
				return true;
			}

			ConfigurationSection userSection = this.permissions.getConfigurationSection("users");
			if (userSection != null) {
				for (String name : userSection.getKeys(false)) {
					if (userName.equalsIgnoreCase(name)) {
						return true;
					}
				}

			}
			return false;
		}
	}

	@Override
	public boolean hasGroup(String group) {
		if (this.permissions.isConfigurationSection(buildPath("groups", group))) {
			return true;
		}

		ConfigurationSection userSection = this.permissions.getConfigurationSection("groups");
		if (userSection != null) {
			for (String name : userSection.getKeys(false)) {
				if (group.equalsIgnoreCase(name)) {
					return true;
				}
			}

		}
		return false;
	}

	@Override
	public Collection<String> getUserIdentifiers() {
		synchronized (lock) {
			ConfigurationSection users = this.permissions.getConfigurationSection("users");
			return users != null ? users.getKeys(false) : Collections.<String>emptyList();
		}
	}

	@Override
	public Collection<String> getUserNames() {
		synchronized (lock) {
			ConfigurationSection users = this.permissions.getConfigurationSection("users");

			if (users == null) {
				return Collections.emptySet();
			}

			Set<String> userNames = new HashSet<>();

			for (Map.Entry<String, Object> entry : users.getValues(false).entrySet()) {
				if (entry.getValue() instanceof ConfigurationSection) {
					ConfigurationSection userSection = (ConfigurationSection) entry.getValue();

					String name = userSection.getString(buildPath("options", "name"));
					if (name != null) {
						userNames.add(name);
					}
				}
			}
			return Collections.unmodifiableSet(userNames);
		}
	}

	@Override
	public Collection<String> getGroupNames() {
		synchronized (lock) {
			ConfigurationSection groups = this.permissions.getConfigurationSection("groups");
			return groups != null ? groups.getKeys(false) : Collections.<String>emptySet();
		}
	}

	public static String buildPath(String... path) {
		StringBuilder builder = new StringBuilder();

		boolean first = true;
		char separator = PATH_SEPARATOR; //permissions.options().pathSeparator();

		for (String node : path) {
			if (node.isEmpty()) {
				continue;
			}

			if (!first) {
				builder.append(separator);
			}

			builder.append(node);

			first = false;
		}

		return builder.toString();
	}

	@Override
	public void reload() throws PermissionBackendException {
		FileConfig newPermissions = new FileConfig(permissionsFile);
		newPermissions.options().pathSeparator(PATH_SEPARATOR);
		try {
			newPermissions.load();
			getLogger().info("Permissions file successfully reloaded");
			worldInheritanceCache.clear();
			this.permissions = newPermissions;
		} catch (FileNotFoundException e) {
			if (this.permissions == null) {
				// First load, load even if the file doesn't exist
				worldInheritanceCache.clear();
				this.permissions = newPermissions;
				initNewConfiguration();
			}
		} catch (Throwable e) {
			throw new PermissionBackendException("Error loading permissions file!", e);
		}
	}

	/**
	 * This method is called when the file the permissions config is supposed to save to
	 * does not exist yet,This adds default permissions & stuff
	 */
	private void initNewConfiguration() throws PermissionBackendException {
		if (!permissionsFile.exists()) {
			try {
				permissionsFile.createNewFile();

				// Load default permissions
				permissions.set("groups/default/default", true);


				List<String> defaultPermissions = new LinkedList<>();
				// Specify here default permissions
				defaultPermissions.add("modifyworld.*");

				permissions.set("groups/default/permissions", defaultPermissions);

				this.save();
			} catch (IOException e) {
				throw new PermissionBackendException(e);
			}
		}
	}

	@Override
	public void loadFrom(PermissionBackend backend) {
		this.setPersistent(false);
		try {
			super.loadFrom(backend);
		} finally {
			this.setPersistent(true);
		}
		save();
	}

	@Override
	public void setPersistent(boolean persistent) {
		super.setPersistent(persistent);
		this.permissions.setSaveSuppressed(!persistent);
		if (persistent) {
			this.save();
		}
	}

	public void save() {
		try {
			this.permissions.save();
		} catch (IOException e) {
			getManager().getLogger().severe("Error while saving permissions file: " + e.getMessage());
		}
	}
}
