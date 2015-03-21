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
package ninja.leaping.permissionsex;

import org.slf4j.Logger;

import javax.sql.DataSource;
import java.io.File;

/**
 * Methods that are specific to a certain implementation of PermissionsEx (Sponge, Forge, etc)
 */
public interface ImplementationInterface {

    /**
     * Return the base directory to store any additional configuration files in.
     *
     * @return The base directory
     */
    public File getBaseDirectory();
    /**
     * Gets the appropriate logger
     * @return
     */
    public Logger getLogger();

    /**
     * Returns an appropriate data source for the implementation-dependent specificer {@code url}.
     *
     * @param url The specifier to get a data source for
     * @return The appropriate data source, or null if not supported
     */
    public DataSource getDataSourceForURL(String url);

    /**
     * Schedules a task to be executed asynchronously through an appropriate method
     * @param run The task to be run
     */
    public void executeAsyncronously(Runnable run);
}
