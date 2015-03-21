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
package ninja.leaping.permissionsex.sponge;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import ninja.leaping.permissionsex.data.Caching;
import ninja.leaping.permissionsex.data.ImmutableOptionSubjectData;
import ninja.leaping.permissionsex.sponge.option.OptionSubject;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.context.Context;
import org.spongepowered.api.service.permission.context.ContextCalculator;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.util.command.CommandSource;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Permissions subject implementation
 */
public class PEXSubject implements OptionSubject, Caching {
    private final PEXSubjectCollection collection;
    private final PEXOptionSubjectData data;
    private final PEXOptionSubjectData transientData;
    private final String identifier;
    private final LoadingCache<Set<Context>, BakedSubjectData> dataCache = CacheBuilder.newBuilder().maximumSize(5)
            .build(new CacheLoader<Set<Context>, BakedSubjectData>() {
                @Override
                public BakedSubjectData load(Set<Context> key) throws Exception {
                    return new SubjectDataBaker(PEXSubject.this, key).bake();
                }
            });

    public PEXSubject(String identifier, PEXOptionSubjectData data, PEXOptionSubjectData transientData, PEXSubjectCollection collection) {
        this.identifier = identifier;
        this.data = data;
        this.transientData = transientData;
        data.addListener(this);
        transientData.addListener(this);
        this.collection = collection;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    private String identifyUser() {
        final Optional<CommandSource> source = getCommandSource();
        return getIdentifier() + (source.isPresent() ? "/" + source.get().getName() : "");
    }

    @Override
    public Optional<CommandSource> getCommandSource() {
        return getContainingCollection().getCommandSource(this.identifier);
    }

    @Override
    public PEXSubjectCollection getContainingCollection() {
        return this.collection;
    }

    @Override
    public PEXOptionSubjectData getData() {
        return data;
    }

    @Override
    public PEXOptionSubjectData getTransientData() {
        return transientData;
    }

    @Override
    public Optional<String> getOption(Set<Context> contexts, String key) {
        Preconditions.checkNotNull(contexts, "contexts");
        Preconditions.checkNotNull(key, "key");
        try {
            final String val = dataCache.get(contexts).getOptions().get(key);
            if (collection.getPlugin().getManager().hasDebugMode()) {
                collection.getPlugin().getLogger().info("Option " + key + " checked in " + contexts + " for user " + identifyUser() + ": " + val);
            }
            return Optional.fromNullable(val);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<String> getOption(String key) {
        return getOption(getActiveContexts(), key);
    }

    @Override
    public boolean hasPermission(Set<Context> contexts, String permission) {
        return getPermissionValue(contexts, permission).asBoolean();
    }

    @Override
    public boolean hasPermission(String permission) {
        return hasPermission(getActiveContexts(), permission);
    }

    @Override
    public Tristate getPermissionValue(Set<Context> contexts, String permission) {
        Preconditions.checkNotNull(contexts, "contexts");
        Preconditions.checkNotNull(permission, "permission");

        try {
            Tristate ret = dataCache.get(contexts).getPermissions().get(permission);
            if (collection.getPlugin().getManager().hasDebugMode()) {
                collection.getPlugin().getLogger().info("Permission " + permission + " checked in " + contexts + " for user " + identifyUser() + ": " + ret);
            }
            return ret;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public boolean isChildOf(Subject parent) {
        return isChildOf(getActiveContexts(), parent);
    }

    @Override
    public boolean isChildOf(Set<Context> contexts, Subject parent) {
        Preconditions.checkNotNull(contexts, "contexts");
        Preconditions.checkNotNull(parent, "parent");
        return getParents(contexts).contains(parent);
    }

    @Override
    public Set<Context> getActiveContexts() {
        Set<Context> set = new HashSet<>();
        for (ContextCalculator calc : this.collection.getPlugin().getContextCalculators()) {
            calc.accumulateContexts(this, set);
        }
        return Collections.unmodifiableSet(set);
    }

    @Override
    public List<Subject> getParents() {
        return getParents(getActiveContexts());
    }

    @Override
    public List<Subject> getParents(Set<Context> contexts) {
        Preconditions.checkNotNull(contexts, "contexts");
        try {
            final List<Subject> parents = dataCache.get(contexts).getParents();
            if (collection.getPlugin().getManager().hasDebugMode()) {
                collection.getPlugin().getLogger().info("Parents checked in " + contexts + " for user " + identifyUser() + ": " + parents);
            }
            return parents;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void clearCache(ImmutableOptionSubjectData newData) {
        dataCache.invalidateAll();
        for (PEXSubject subj : collection.getPlugin().getAllActiveSubjects()) {
            if (subj.isChildOf(this)) {
                subj.dataCache.invalidateAll(); // (or something that clears cache without requiring a param)
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PEXSubject)) {
            return false;
        }

        PEXSubject otherSubj = (PEXSubject) other;

        return this.identifier.equals(otherSubj.identifier)
                && this.data.equals(otherSubj.data);
    }
}
