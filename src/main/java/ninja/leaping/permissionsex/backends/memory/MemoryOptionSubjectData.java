/**
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ninja.leaping.permissionsex.backends.memory;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ninja.leaping.configurate.objectmapping.ObjectMapper;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import ninja.leaping.permissionsex.data.ImmutableOptionSubjectData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Map.Entry;

public class MemoryOptionSubjectData implements ImmutableOptionSubjectData {
    protected static final ObjectMapper<DataEntry> MAPPER;
    static {
        try {
            MAPPER = ObjectMapper.forClass(DataEntry.class);
        } catch (ObjectMappingException e) {
            throw new ExceptionInInitializerError(e); // This error indicates a programming issue
        }
    }

    protected static <K, V> Map<K, V> updateImmutable(Map<K, V> input, K newKey, V newVal) {
        if (input == null) {
            return ImmutableMap.of(newKey, newVal);
        }
        Map<K, V> ret = new HashMap<>(input);
        ret.put(newKey, newVal);
        return Collections.unmodifiableMap(ret);
    }
    @ConfigSerializable
    protected static class DataEntry {
        @Setting private Map<String, Integer> permissions;
        @Setting private Map<String, String> options;
        @Setting private List<String> parents;
        @Setting("permissions-default") private int defaultValue;

        private DataEntry(Map<String, Integer> permissions, Map<String, String> options, List<String> parents, int defaultValue) {
            this.permissions = permissions;
            this.options = options;
            this.parents = parents;
            this.defaultValue = defaultValue;
        }

        private DataEntry() { // Objectmapper constructor
        }

        public DataEntry withOption(String key, String value) {
            return new DataEntry(permissions, updateImmutable(options, key, value), parents, defaultValue);
        }

        public DataEntry withoutOption(String key) {
            if (!options.containsKey(key)) {
                return this;
            }

            Map<String, String> newOptions = new HashMap<>(options);
            newOptions.remove(key);
            return new DataEntry(permissions, newOptions, parents, defaultValue);

        }

        public DataEntry withOptions(Map<String, String> values) {
            return new DataEntry(permissions, ImmutableMap.copyOf(values), parents, defaultValue);
        }

        public DataEntry withoutOptions() {
            return new DataEntry(permissions, null, parents, defaultValue);
        }

        public DataEntry withPermission(String permission, int value) {
            return new DataEntry(updateImmutable(permissions, permission, value), options, parents, defaultValue);

        }

        public DataEntry withoutPermission(String permission) {
            if (!permissions.containsKey(permission)) {
                return this;
            }

            Map<String, Integer> newPermissions = new HashMap<>(permissions);
            newPermissions.remove(permission);
            return new DataEntry(newPermissions, options, parents, defaultValue);
        }

        public DataEntry withPermissions(Map<String, Integer> values) {
            return new DataEntry(ImmutableMap.copyOf(values), options, parents, defaultValue);
        }

        public DataEntry withoutPermissions() {
            return new DataEntry(null, options, parents, defaultValue);
        }

        public DataEntry withDefaultValue(int defaultValue) {
            return new DataEntry(permissions, options, parents, defaultValue);
        }

        public DataEntry withAddedParent(String parent) {
                return new DataEntry(permissions, options, ImmutableList.<String>builder().add(parent).addAll(parents).build(), defaultValue);
        }

        public DataEntry withRemovedParent(String parent) {
            final List<String> newParents = new ArrayList<>(parents);
            newParents.remove(parent);
            return new DataEntry(permissions, options, newParents, defaultValue);
        }

        public DataEntry withParents(List<String> transform) {
            return new DataEntry(permissions, options, ImmutableList.copyOf(transform), defaultValue);
        }

        public DataEntry withoutParents() {
            return new DataEntry(permissions, options, null, defaultValue);
        }

        @Override
        public String toString() {
            return "DataEntry{" +
                    "permissions=" + permissions +
                    ", options=" + options +
                    ", parents=" + parents +
                    ", defaultValue=" + defaultValue +
                    '}';
        }

    }

    protected static DataEntry newEntry() {
        return new DataEntry();
    }

    protected final MemoryOptionSubjectData newWithUpdated(Set<Entry<String, String>> key, DataEntry val) {
        return newData(updateImmutable(contexts, immutSet(key), val));
    }

    protected MemoryOptionSubjectData newData(Map<Set<Entry<String, String>>, DataEntry> contexts) {
        return new MemoryOptionSubjectData(contexts);
    }

    protected final Map<Set<Entry<String, String>>, DataEntry> contexts;

    protected MemoryOptionSubjectData() {
        this.contexts = ImmutableMap.of();
    }

    protected MemoryOptionSubjectData(Map<Set<Entry<String, String>>, DataEntry> contexts) {
        this.contexts = contexts;
    }

    private DataEntry getDataEntryOrNew(Set<Entry<String, String>> contexts) {
        DataEntry res = this.contexts.get(contexts);
        if (res == null) {
            res = new DataEntry();
        }
        return res;
    }

    private <E> ImmutableSet<E> immutSet(Set<E> set) {
        return ImmutableSet.copyOf(set);
    }

    @Override
    public Map<Set<Entry<String, String>>, Map<String, String>> getAllOptions() {
        return Maps.transformValues(contexts, new Function<DataEntry, Map<String, String>>() {
            @Nullable
            @Override
            public Map<String, String> apply(@Nullable DataEntry dataEntry) {
                return dataEntry.options;
            }
        });
    }

    @Override
    public Map<String, String> getOptions(Set<Entry<String, String>> contexts) {
        final DataEntry entry = this.contexts.get(contexts);
        return entry == null || entry.options == null ? Collections.<String, String>emptyMap() : entry.options;
    }

    @Override
    public ImmutableOptionSubjectData setOption(Set<Entry<String, String>> contexts, String key, String value) {
        if (value == null) {
            return newWithUpdated(contexts, getDataEntryOrNew(contexts).withoutOption(key));
        } else {
            return newWithUpdated(contexts, getDataEntryOrNew(contexts).withOption(key, value));
        }
    }

    @Override
    public ImmutableOptionSubjectData setOptions(Set<Entry<String, String>> contexts, Map<String, String> values) {
        return newWithUpdated(contexts, getDataEntryOrNew(contexts).withOptions(values));
    }

    @Override
    public ImmutableOptionSubjectData clearOptions(Set<Entry<String, String>> contexts) {
        if (!this.contexts.containsKey(contexts)) {
            return this;
        }
        return newWithUpdated(contexts, getDataEntryOrNew(contexts).withoutOptions());
    }

    @Override
    public ImmutableOptionSubjectData clearOptions() {
        if (this.contexts.isEmpty()) {
            return this;
        }

        Map<Set<Entry<String, String>>, DataEntry> newValue = Maps.transformValues(this.contexts, new Function<DataEntry, DataEntry>() {
            @Nullable
            @Override
            public DataEntry apply(@Nullable DataEntry dataEntry) {
                return dataEntry.withoutOptions();
            }
        });
        return newData(newValue);
    }

    @Override
    public Map<Set<Entry<String, String>>, Map<String, Integer>> getAllPermissions() {
        return Maps.filterValues(Maps.transformValues(contexts, new Function<DataEntry, Map<String, Integer>>() {
            @Nullable
            @Override
            public Map<String, Integer> apply(@Nullable DataEntry dataEntry) {
                return dataEntry.permissions;
            }
        }), Predicates.notNull());
    }

    @Override
    public Map<String, Integer> getPermissions(Set<Entry<String, String>> set) {
        final DataEntry entry = this.contexts.get(set);
        return entry == null || entry.permissions == null ? Collections.<String, Integer>emptyMap() : entry.permissions;
    }

    @Override
    public ImmutableOptionSubjectData setPermission(Set<Entry<String, String>> contexts, String permission, int value) {
        if (value == 0) {
            return newWithUpdated(contexts, getDataEntryOrNew(contexts).withoutPermission(permission));
        } else {
            return newWithUpdated(contexts, getDataEntryOrNew(contexts).withPermission(permission, value));
        }
    }

    @Override
    public ImmutableOptionSubjectData setPermissions(Set<Entry<String, String>> contexts, Map<String, Integer> values) {
        return newWithUpdated(contexts, getDataEntryOrNew(contexts).withPermissions(values));
    }

    @Override
    public ImmutableOptionSubjectData clearPermissions() {
        if (this.contexts.isEmpty()) {
            return this;
        }

        Map<Set<Entry<String, String>>, DataEntry> newValue = Maps.transformValues(this.contexts, new Function<DataEntry, DataEntry>() {
            @Nullable
            @Override
            public DataEntry apply(@Nullable DataEntry dataEntry) {
                return dataEntry.withoutPermissions();
            }
        });
        return newData(newValue);
    }

    @Override
    public ImmutableOptionSubjectData clearPermissions(Set<Entry<String, String>> contexts) {
        if (!this.contexts.containsKey(contexts)) {
            return this;
        }
        return newWithUpdated(contexts, getDataEntryOrNew(contexts).withoutPermissions());

    }

    private static final Function<String, Map.Entry<String, String>> PARENT_TRANSFORM_FUNC = new Function<String, Map.Entry<String, String>>() {
                    @Nullable
                    @Override
                    public Map.Entry<String, String> apply(String input) {
                        String[] split = input.split(":", 2);
                        return Maps.immutableEntry(split.length > 1 ? split[0] : "group", split.length > 1 ? split[1]: split[0]);
                    }
                };

    @Override
    public Map<Set<Entry<String, String>>, List<Entry<String, String>>> getAllParents() {
        return Maps.filterValues(Maps.transformValues(contexts, new Function<DataEntry, List<Map.Entry<String, String>>>() {
            @Nullable
            @Override
            public List<Map.Entry<String, String>> apply(@Nullable DataEntry dataEntry) {
                return dataEntry.parents == null ? null : Lists.transform(dataEntry.parents, PARENT_TRANSFORM_FUNC);
            }
        }), Predicates.notNull());
    }

    @Override
    public List<Map.Entry<String, String>> getParents(Set<Entry<String, String>> contexts) {
        DataEntry ent = this.contexts.get(contexts);
        return ent == null || ent.parents == null ? Collections.<Map.Entry<String, String>>emptyList() : Lists.transform(ent.parents, PARENT_TRANSFORM_FUNC);
    }

    @Override
    public ImmutableOptionSubjectData addParent(Set<Entry<String, String>> contexts, String type, String ident) {
        DataEntry entry = getDataEntryOrNew(contexts);
        return newWithUpdated(contexts, entry.withAddedParent(type + ":" + ident));
    }

    @Override
    public ImmutableOptionSubjectData removeParent(Set<Entry<String, String>> contexts, String type, String identifier) {
        DataEntry ent = this.contexts.get(contexts);
        if (ent == null) {
            return this;
        }

        final String combined = type + ":" + identifier;
        if (!ent.parents.contains(combined)) {
            return this;
        }
        return newWithUpdated(contexts, ent.withRemovedParent(combined));
    }

    @Override
    public ImmutableOptionSubjectData setParents(Set<Entry<String, String>> contexts, List<Entry<String, String>> parents) {
        DataEntry entry = getDataEntryOrNew(contexts);
        return newWithUpdated(contexts, entry.withParents(Lists.transform(parents, new Function<Entry<String,String>, String>() {
            @Nullable
            @Override
            public String apply(@Nullable Entry<String, String> input) {
                return input.getKey() + ":" + input.getValue();
            }
        })));
    }

    @Override
    public ImmutableOptionSubjectData clearParents() {
        if (this.contexts.isEmpty()) {
            return this;
        }

        Map<Set<Entry<String, String>>, DataEntry> newValue = Maps.transformValues(this.contexts, new Function<DataEntry, DataEntry>() {
            @Nullable
            @Override
            public DataEntry apply(@Nullable DataEntry dataEntry) {
                return dataEntry.withoutParents();
            }
        });
        return newData(newValue);
    }

    @Override
    public ImmutableOptionSubjectData clearParents(Set<Entry<String, String>> contexts) {
        if (!this.contexts.containsKey(contexts)) {
            return this;
        }
        return newWithUpdated(contexts, getDataEntryOrNew(contexts).withoutParents());
    }

    public int getDefaultValue(Set<Entry<String, String>> contexts) {
        DataEntry ent = this.contexts.get(contexts);
        return ent == null ? 0 : ent.defaultValue;
    }

    public ImmutableOptionSubjectData setDefaultValue(Set<Entry<String, String>> contexts, int defaultValue) {
        return newWithUpdated(contexts, getDataEntryOrNew(contexts).withDefaultValue(defaultValue));
    }

    @Override
    public Iterable<Set<Entry<String, String>>> getActiveContexts() {
        return contexts.keySet();
    }

    @Override
    public Map<Set<Entry<String, String>>, Integer> getAllDefaultValues() {
        return Maps.filterValues(Maps.transformValues(contexts, new Function<DataEntry, Integer>() {
            @Nullable
            @Override
            public Integer apply(@Nullable DataEntry dataEntry) {
                return dataEntry.defaultValue;
            }
        }), Predicates.notNull());
    }

    @Override
    public String toString() {
        return "MemoryOptionSubjectData{" +
                "contexts=" + contexts +
                '}';
    }
}
