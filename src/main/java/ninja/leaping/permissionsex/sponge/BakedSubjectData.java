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

import com.google.common.base.Preconditions;
import org.spongepowered.api.service.permission.NodeTree;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.context.Context;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents subject data that has had its hierarchy and contexts flattened
 */
public class BakedSubjectData {
    private final Set<Context> applicableContexts;
    private final NodeTree permissions;
    private final List<Subject> parents;
    private final Map<String, String> options;

    public BakedSubjectData(Set<Context> applicableContexts, NodeTree permissions, List<Subject> parents, Map<String, String> options) {
        Preconditions.checkNotNull(applicableContexts, "applicableContexts");
        Preconditions.checkNotNull(permissions, "permissions");
        Preconditions.checkNotNull(parents, "parents");
        Preconditions.checkNotNull(options, "options");
        this.applicableContexts = applicableContexts;
        this.permissions = permissions;
        this.parents = parents;
        this.options = options;
    }

    public NodeTree getPermissions() {
        return permissions;
    }

    public List<Subject> getParents() {
        return parents;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BakedSubjectData that = (BakedSubjectData) o;

        if (!applicableContexts.equals(that.applicableContexts)) return false;
        if (!options.equals(that.options)) return false;
        if (!parents.equals(that.parents)) return false;
        if (!permissions.equals(that.permissions)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = applicableContexts.hashCode();
        result = 31 * result + permissions.hashCode();
        result = 31 * result + parents.hashCode();
        result = 31 * result + options.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "BakedSubjectData{" +
                "applicableContexts=" + applicableContexts +
                ", permissions=" + permissions +
                ", parents=" + parents +
                ", options=" + options +
                '}';
    }
}
