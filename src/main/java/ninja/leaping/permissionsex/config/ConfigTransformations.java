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
package ninja.leaping.permissionsex.config;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.transformation.ConfigurationTransformation;
import ninja.leaping.configurate.transformation.MoveStrategy;
import ninja.leaping.configurate.transformation.TransformAction;

import static ninja.leaping.configurate.transformation.ConfigurationTransformation.builder;

public class ConfigTransformations {
    private static final TransformAction DELETE_ITEM = new TransformAction() {
        @Override
        public Object[] visitPath(ConfigurationTransformation.NodePath inputPath, ConfigurationNode valueAtPath) {
            valueAtPath.setValue(null);
            return null;
        }
    };

    /**
     * Creat a configuration transformation that converts the Bukkit global configuration structure to the new Sponge configuration structure.
     * @return A transformation that converts a Bukkit-style configuration to a Sponge-style configuration
     */
    public static ConfigurationTransformation fromBukkit() {
        return builder()
                        .setMoveStrategy(MoveStrategy.MERGE)
                        .addAction(p("permissions"), new TransformAction() {
                            @Override
                            public Object[] visitPath(ConfigurationTransformation.NodePath inputPath, ConfigurationNode valueAtPath) {
                                return new Object[0];
                            }
                        })
                        .addAction(p("permissions", "backend"), new TransformAction() {
                            @Override
                            public Object[] visitPath(ConfigurationTransformation.NodePath inputPath, ConfigurationNode valueAtPath) {
                                return p("default-backend");
                            }
                        })
                        .addAction(p("permissions", "allowOps"), DELETE_ITEM)
                        .addAction(p("permissions", "basedir"), DELETE_ITEM)
                        .addAction(p("updater"), new TransformAction() {
                            @Override
                            public Object[] visitPath(ConfigurationTransformation.NodePath inputPath, ConfigurationNode valueAtPath) {
                                valueAtPath.getNode("enabled").setValue(valueAtPath.getValue());
                                valueAtPath.getNode("always-update").setValue(valueAtPath.getParent().getNode("alwaysUpdate"));
                                valueAtPath.getParent().getNode("alwaysUpdate").setValue(null);
                                return null;
                            }
                        })
                        .build();
    }

    private static Object[] p(Object... path) {
        return path;
    }
}
